package com.dbtool.core.diff;

import com.dbtool.core.dialect.DatabaseDialect;
import com.dbtool.core.dialect.DialectRegistry;
import com.dbtool.core.query.RowData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 2-Phase Chunked & Canonical Hash Data Diff Engine.
 *
 * <p><b>Phase 1 (Fingerprint Pass):</b> Streams both tables ordered by primary key(s) with JDBC
 * forward-only cursors and {@code setFetchSize}, computing per-chunk canonical SHA-256 digests
 * (via {@link RowHasher#hashRowSha256(RowData)} and {@link RowHasher#hashChunkSha256(List)}).
 * Matching chunks are discarded immediately without retaining full row payloads in memory.</p>
 *
 * <p><b>Phase 2 (Dirty-Range Resolution):</b> Fetches/resolves only key ranges whose chunk digest,
 * row count, or boundary keys diverged, and computes cell-level diffs using canonical equivalence
 * ({@link CanonicalValueNormalizer#areValuesEquivalent(Object, Object)}) and typed
 * {@link CompositeKey} keys.</p>
 */
public class ChunkedDataComparator {

    private final DiffChunkConfig config;

    public ChunkedDataComparator() {
        this(new DiffChunkConfig());
    }

    public ChunkedDataComparator(DiffChunkConfig config) {
        this.config = config != null ? config : new DiffChunkConfig();
    }

    public DiffChunkConfig getConfig() {
        return config;
    }

    private static final class ChunkDigest {
        final int chunkIndex;
        final CompositeKey firstKey;
        final CompositeKey lastKey;
        final int rowCount;
        final String sha256Digest;

        ChunkDigest(int chunkIndex, CompositeKey firstKey, CompositeKey lastKey, int rowCount, String sha256Digest) {
            this.chunkIndex = chunkIndex;
            this.firstKey = firstKey;
            this.lastKey = lastKey;
            this.rowCount = rowCount;
            this.sha256Digest = sha256Digest;
        }

        boolean matches(ChunkDigest other) {
            return other != null
                    && this.rowCount == other.rowCount
                    && Objects.equals(this.firstKey, other.firstKey)
                    && Objects.equals(this.lastKey, other.lastKey)
                    && Objects.equals(this.sha256Digest, other.sha256Digest);
        }
    }

    /**
     * Compares a table between two live JDBC {@link Connection}s using 2-phase chunk digests and
     * selective dirty-range fetches. Both connections must remain open for the duration of the call.
     */
    public DataDiffResult compareTables(
            Connection sourceConn,
            Connection targetConn,
            String tableName,
            List<String> pkColumns
    ) throws SQLException {
        return compareTables(sourceConn, targetConn, tableName, null, pkColumns);
    }

    /**
     * Compares a table across two live JDBC {@link Connection}s over explicit column projections.
     */
    public DataDiffResult compareTables(
            Connection sourceConn,
            Connection targetConn,
            String tableName,
            List<String> selectColumns,
            List<String> pkColumns
    ) throws SQLException {
        Objects.requireNonNull(sourceConn, "sourceConn must not be null");
        Objects.requireNonNull(targetConn, "targetConn must not be null");
        if (pkColumns == null || pkColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one primary-key column is required for ordered chunk diff.");
        }

        long startMs = System.currentTimeMillis();
        DataDiffResult result = new DataDiffResult(tableName);

        DatabaseDialect srcDialect = DialectRegistry.getInstance().resolveFromJdbcUrl(safeUrl(sourceConn));
        DatabaseDialect dstDialect = DialectRegistry.getInstance().resolveFromJdbcUrl(safeUrl(targetConn));

        List<String> cols = resolveColumnList(sourceConn, srcDialect, tableName, selectColumns, pkColumns);
        result.setColumnNames(cols);
        int[] pkIndices = resolvePkIndices(cols, pkColumns);

        // Phase 1: Stream key-ordered chunk digests without holding row payloads
        List<ChunkDigest> srcChunks = scanChunkDigests(sourceConn, srcDialect, tableName, cols, pkColumns, pkIndices);
        List<ChunkDigest> dstChunks = scanChunkDigests(targetConn, dstDialect, tableName, cols, pkColumns, pkIndices);

        long totalSrc = srcChunks.stream().mapToLong(c -> c.rowCount).sum();
        long totalDst = dstChunks.stream().mapToLong(c -> c.rowCount).sum();
        result.setTotalSourceRows(totalSrc);
        result.setTotalTargetRows(totalDst);

        int maxChunks = Math.max(srcChunks.size(), dstChunks.size());
        result.setChunksCompared(maxChunks);

        // Identify aligned matching prefix chunks vs dirty ranges
        int i = 0;
        int matchedSkipped = 0;
        int dirtyResolved = 0;

        while (i < maxChunks) {
            ChunkDigest sc = i < srcChunks.size() ? srcChunks.get(i) : null;
            ChunkDigest dc = i < dstChunks.size() ? dstChunks.get(i) : null;

            if (sc != null && sc.matches(dc)) {
                matchedSkipped++;
                i++;
                continue;
            }

            // Collect contiguous divergent chunk run until both sides resynchronize on a matching chunk key bound
            int runStart = i;
            CompositeKey minKey = minNonNullKey(sc != null ? sc.firstKey : null, dc != null ? dc.firstKey : null);
            CompositeKey maxKey = maxNonNullKey(sc != null ? sc.lastKey : null, dc != null ? dc.lastKey : null);
            i++;

            while (i < maxChunks) {
                ChunkDigest nextSrc = i < srcChunks.size() ? srcChunks.get(i) : null;
                ChunkDigest nextDst = i < dstChunks.size() ? dstChunks.get(i) : null;
                if (nextSrc != null && nextSrc.matches(nextDst)
                        && (maxKey == null || nextSrc.firstKey.compareTo(maxKey) > 0)) {
                    break;
                }
                if (nextSrc != null) {
                    minKey = minNonNullKey(minKey, nextSrc.firstKey);
                    maxKey = maxNonNullKey(maxKey, nextSrc.lastKey);
                }
                if (nextDst != null) {
                    minKey = minNonNullKey(minKey, nextDst.firstKey);
                    maxKey = maxNonNullKey(maxKey, nextDst.lastKey);
                }
                i++;
            }

            dirtyResolved += (i - runStart);

            // Phase 2: Stream & merge-join ONLY the dirty key range [minKey, maxKey]
            try (RowCursor srcCursor = openRangeCursor(sourceConn, srcDialect, tableName, cols, pkColumns, minKey, maxKey);
                 RowCursor dstCursor = openRangeCursor(targetConn, dstDialect, tableName, cols, pkColumns, minKey, maxKey)) {
                mergeSortedCursors(srcCursor, dstCursor, cols, pkIndices, result);
            }
        }

        result.setChunksMatchedSkipped(matchedSkipped);
        result.setChunksDirtyResolved(dirtyResolved);
        result.setDurationMs(System.currentTimeMillis() - startMs);
        return result;
    }

    /**
     * Compares two key-ordered streaming iterators in bounded {@link DiffChunkConfig#getChunkSize()}
     * chunk windows without loading either dataset wholly into memory.
     */
    public DataDiffResult compareOrderedStreams(
            String tableName,
            List<String> columnNames,
            Iterator<RowData> sourceStream,
            Iterator<RowData> targetStream,
            int[] pkIndices
    ) {
        long startMs = System.currentTimeMillis();
        DataDiffResult result = new DataDiffResult(tableName);
        result.setColumnNames(columnNames != null ? columnNames : new ArrayList<>());
        if (pkIndices == null || pkIndices.length == 0) {
            throw new IllegalArgumentException("At least one primary-key index is required.");
        }

        int chunkSize = config.getChunkSize();
        long srcTotal = 0;
        long dstTotal = 0;
        int chunksCompared = 0;
        int matchedSkipped = 0;
        int dirtyResolved = 0;

        KeyedRow nextSrc = nextKeyed(sourceStream, pkIndices);
        KeyedRow nextDst = nextKeyed(targetStream, pkIndices);

        // Process aligned streaming merge in chunk-sized key windows so peak heap stays O(chunkSize).
        while (nextSrc != null || nextDst != null) {
            chunksCompared++;
            List<String> srcChunkHashes = new ArrayList<>(Math.min(chunkSize, 4096));
            List<String> dstChunkHashes = new ArrayList<>(Math.min(chunkSize, 4096));
            List<RowDiff> pendingDiffs = new ArrayList<>();
            boolean chunkIdentical = true;

            int processedInChunk = 0;
            while (processedInChunk < chunkSize && (nextSrc != null || nextDst != null)) {
                int cmp;
                if (nextSrc == null) {
                    cmp = 1;
                } else if (nextDst == null) {
                    cmp = -1;
                } else {
                    cmp = nextSrc.key.compareTo(nextDst.key);
                }

                if (cmp < 0) {
                    chunkIdentical = false;
                    srcTotal++;
                    srcChunkHashes.add(RowHasher.hashRowSha256(nextSrc.row));
                    recordRowDiffBounded(result, pendingDiffs, new RowDiff(nextSrc.key.toDisplayValue(), DifferenceType.REMOVED, nextSrc.row, null));
                    nextSrc = nextKeyed(sourceStream, pkIndices);
                } else if (cmp > 0) {
                    chunkIdentical = false;
                    dstTotal++;
                    dstChunkHashes.add(RowHasher.hashRowSha256(nextDst.row));
                    recordRowDiffBounded(result, pendingDiffs, new RowDiff(nextDst.key.toDisplayValue(), DifferenceType.ADDED, null, nextDst.row));
                    nextDst = nextKeyed(targetStream, pkIndices);
                } else {
                    srcTotal++;
                    dstTotal++;
                    String hSrc = RowHasher.hashRowSha256(nextSrc.row);
                    String hDst = RowHasher.hashRowSha256(nextDst.row);
                    srcChunkHashes.add(hSrc);
                    dstChunkHashes.add(hDst);
                    if (!hSrc.equals(hDst)) {
                        RowDiff rowDiff = buildCellDiffs(nextSrc.key, nextSrc.row, nextDst.row, result.getColumnNames());
                        if (rowDiff != null) {
                            chunkIdentical = false;
                            recordRowDiffBounded(result, pendingDiffs, rowDiff);
                        }
                    }
                    nextSrc = nextKeyed(sourceStream, pkIndices);
                    nextDst = nextKeyed(targetStream, pkIndices);
                }
                processedInChunk++;
            }

            String chunkHashSrc = RowHasher.hashChunkSha256(srcChunkHashes);
            String chunkHashDst = RowHasher.hashChunkSha256(dstChunkHashes);
            if (chunkIdentical && chunkHashSrc.equals(chunkHashDst)) {
                matchedSkipped++;
            } else {
                dirtyResolved++;
                for (RowDiff rd : pendingDiffs) {
                    result.addRowDiff(rd);
                }
            }
        }

        result.setTotalSourceRows(srcTotal);
        result.setTotalTargetRows(dstTotal);
        result.setChunksCompared(chunksCompared);
        result.setChunksMatchedSkipped(matchedSkipped);
        result.setChunksDirtyResolved(dirtyResolved);
        result.setDurationMs(System.currentTimeMillis() - startMs);
        return result;
    }

    private void recordRowDiffBounded(DataDiffResult result, List<RowDiff> pending, RowDiff diff) {
        if (result.getRowDiffs().size() + pending.size() < config.getMaxDiffRowsInMemory()) {
            pending.add(diff);
        }
    }

    private List<ChunkDigest> scanChunkDigests(
            Connection conn,
            DatabaseDialect dialect,
            String tableName,
            List<String> cols,
            List<String> pkColumns,
            int[] pkIndices
    ) throws SQLException {
        List<ChunkDigest> digests = new ArrayList<>();
        String sql = buildOrderedSelectSql(dialect, tableName, cols, pkColumns);

        try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            configureStreamingFetch(stmt, config.getFetchSize());
            try (ResultSet rs = stmt.executeQuery(sql)) {
                int chunkSize = config.getChunkSize();
                List<String> rowHashes = new ArrayList<>(Math.min(chunkSize, 4096));
                CompositeKey firstKey = null;
                CompositeKey lastKey = null;
                int count = 0;
                int chunkIndex = 0;
                int colCount = cols.size();

                while (rs.next()) {
                    RowData row = readRow(rs, colCount);
                    CompositeKey key = extractCompositeKey(row, pkIndices);
                    if (firstKey == null) firstKey = key;
                    lastKey = key;
                    rowHashes.add(RowHasher.hashRowSha256(row));
                    count++;

                    if (count >= chunkSize) {
                        digests.add(new ChunkDigest(chunkIndex++, firstKey, lastKey, count, RowHasher.hashChunkSha256(rowHashes)));
                        rowHashes.clear();
                        firstKey = null;
                        lastKey = null;
                        count = 0;
                    }
                }
                if (count > 0) {
                    digests.add(new ChunkDigest(chunkIndex, firstKey, lastKey, count, RowHasher.hashChunkSha256(rowHashes)));
                }
            }
        }
        return digests;
    }

    private void mergeSortedCursors(
            RowCursor srcCursor,
            RowCursor dstCursor,
            List<String> cols,
            int[] pkIndices,
            DataDiffResult result
    ) throws SQLException {
        RowData sRow = srcCursor.nextRow();
        RowData tRow = dstCursor.nextRow();

        while (sRow != null || tRow != null) {
            if (result.getRowDiffs().size() >= config.getMaxDiffRowsInMemory()) {
                break;
            }
            CompositeKey sKey = sRow != null ? extractCompositeKey(sRow, pkIndices) : null;
            CompositeKey tKey = tRow != null ? extractCompositeKey(tRow, pkIndices) : null;

            int cmp;
            if (sKey == null) cmp = 1;
            else if (tKey == null) cmp = -1;
            else cmp = sKey.compareTo(tKey);

            if (cmp < 0) {
                result.addRowDiff(new RowDiff(sKey.toDisplayValue(), DifferenceType.REMOVED, sRow, null));
                sRow = srcCursor.nextRow();
            } else if (cmp > 0) {
                result.addRowDiff(new RowDiff(tKey.toDisplayValue(), DifferenceType.ADDED, null, tRow));
                tRow = dstCursor.nextRow();
            } else {
                String hSrc = RowHasher.hashRowSha256(sRow);
                String hDst = RowHasher.hashRowSha256(tRow);
                if (!hSrc.equals(hDst)) {
                    RowDiff diff = buildCellDiffs(sKey, sRow, tRow, cols);
                    if (diff != null) {
                        result.addRowDiff(diff);
                    }
                }
                sRow = srcCursor.nextRow();
                tRow = dstCursor.nextRow();
            }
        }
    }

    private RowDiff buildCellDiffs(CompositeKey key, RowData sRow, RowData tRow, List<String> colNames) {
        int len = Math.min(sRow.size(), tRow.size());
        List<CellDiff> diffs = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            Object sVal = sRow.getValue(i);
            Object tVal = tRow.getValue(i);
            if (!CanonicalValueNormalizer.areValuesEquivalent(sVal, tVal)) {
                String colName = (colNames != null && i < colNames.size()) ? colNames.get(i) : ("col_" + i);
                diffs.add(new CellDiff(colName, sVal, tVal));
            }
        }
        if (diffs.isEmpty()) {
            return null;
        }
        RowDiff rowDiff = new RowDiff(key.toDisplayValue(), DifferenceType.MODIFIED, sRow, tRow);
        for (CellDiff cd : diffs) {
            rowDiff.addCellDiff(cd);
        }
        return rowDiff;
    }

    private RowCursor openRangeCursor(
            Connection conn,
            DatabaseDialect dialect,
            String tableName,
            List<String> cols,
            List<String> pkColumns,
            CompositeKey minKey,
            CompositeKey maxKey
    ) throws SQLException {
        // For single-column PK we use a tight index-friendly WHERE pk BETWEEN ? AND ? (or >= / <=);
        // for composite PK we push down the leading PK component range and exact-filter in-stream.
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quoteQualified(dialect, cols.get(i)));
        }
        sql.append(" FROM ").append(quoteQualified(dialect, tableName));

        List<Object> params = new ArrayList<>();
        String leadPk = quoteQualified(dialect, pkColumns.get(0));
        if (minKey != null && maxKey != null) {
            sql.append(" WHERE ").append(leadPk).append(" >= ? AND ").append(leadPk).append(" <= ?");
            params.add(minKey.getComponent(0));
            params.add(maxKey.getComponent(0));
        } else if (minKey != null) {
            sql.append(" WHERE ").append(leadPk).append(" >= ?");
            params.add(minKey.getComponent(0));
        } else if (maxKey != null) {
            sql.append(" WHERE ").append(leadPk).append(" <= ?");
            params.add(maxKey.getComponent(0));
        }

        sql.append(" ORDER BY ");
        for (int i = 0; i < pkColumns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quoteQualified(dialect, pkColumns.get(i))).append(" ASC");
        }

        PreparedStatement ps = conn.prepareStatement(sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        configureStreamingFetch(ps, config.getFetchSize());
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        ResultSet rs = ps.executeQuery();
        int[] pkIndices = resolvePkIndices(cols, pkColumns);
        return new RowCursor(ps, rs, cols.size(), pkColumns.size() > 1 ? pkIndices : null,
                pkColumns.size() > 1 ? minKey : null, pkColumns.size() > 1 ? maxKey : null);
    }

    private static final class RowCursor implements AutoCloseable {
        final PreparedStatement stmt;
        final ResultSet rs;
        final int colCount;
        final int[] compositePkIndices;
        final CompositeKey minKey;
        final CompositeKey maxKey;

        RowCursor(PreparedStatement stmt, ResultSet rs, int colCount,
                  int[] compositePkIndices, CompositeKey minKey, CompositeKey maxKey) {
            this.stmt = stmt;
            this.rs = rs;
            this.colCount = colCount;
            this.compositePkIndices = compositePkIndices;
            this.minKey = minKey;
            this.maxKey = maxKey;
        }

        RowData nextRow() throws SQLException {
            while (rs.next()) {
                RowData row = readRow(rs, colCount);
                if (compositePkIndices != null) {
                    CompositeKey key = extractCompositeKey(row, compositePkIndices);
                    if (minKey != null && key.compareTo(minKey) < 0) continue;
                    if (maxKey != null && key.compareTo(maxKey) > 0) continue;
                }
                return row;
            }
            return null;
        }

        @Override
        public void close() {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (stmt != null) stmt.close(); } catch (SQLException ignored) {}
        }
    }

    private static final class KeyedRow {
        final CompositeKey key;
        final RowData row;

        KeyedRow(CompositeKey key, RowData row) {
            this.key = key;
            this.row = row;
        }
    }

    private static KeyedRow nextKeyed(Iterator<RowData> it, int[] pkIndices) {
        if (it == null || !it.hasNext()) return null;
        RowData row = it.next();
        return row == null ? null : new KeyedRow(extractCompositeKey(row, pkIndices), row);
    }

    private static CompositeKey extractCompositeKey(RowData row, int[] pkIndices) {
        Object[] comps = new Object[pkIndices.length];
        for (int i = 0; i < pkIndices.length; i++) {
            comps[i] = row.getValue(pkIndices[i]);
        }
        return new CompositeKey(comps);
    }

    private static RowData readRow(ResultSet rs, int colCount) throws SQLException {
        Object[] vals = new Object[colCount];
        for (int c = 1; c <= colCount; c++) {
            vals[c - 1] = rs.getObject(c);
        }
        return new RowData(vals);
    }

    private static String buildOrderedSelectSql(
            DatabaseDialect dialect, String tableName, List<String> cols, List<String> pkColumns) {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(quoteQualified(dialect, cols.get(i)));
        }
        sb.append(" FROM ").append(quoteQualified(dialect, tableName)).append(" ORDER BY ");
        for (int i = 0; i < pkColumns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(quoteQualified(dialect, pkColumns.get(i))).append(" ASC");
        }
        return sb.toString();
    }

    private static List<String> resolveColumnList(
            Connection conn, DatabaseDialect dialect, String tableName,
            List<String> requested, List<String> pkColumns) throws SQLException {
        if (requested != null && !requested.isEmpty()) {
            return new ArrayList<>(requested);
        }
        List<String> discovered = new ArrayList<>();
        String sql = "SELECT * FROM " + quoteQualified(dialect, tableName) + " WHERE 1=0";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            for (int c = 1; c <= md.getColumnCount(); c++) {
                discovered.add(md.getColumnLabel(c));
            }
        }
        if (discovered.isEmpty()) {
            discovered.addAll(pkColumns);
        }
        return discovered;
    }

    private static int[] resolvePkIndices(List<String> cols, List<String> pkColumns) {
        int[] indices = new int[pkColumns.size()];
        for (int p = 0; p < pkColumns.size(); p++) {
            String targetPk = pkColumns.get(p);
            int found = -1;
            for (int c = 0; c < cols.size(); c++) {
                if (cols.get(c).equalsIgnoreCase(targetPk)) {
                    found = c;
                    break;
                }
            }
            if (found < 0) {
                throw new IllegalArgumentException("Primary key column '" + targetPk + "' not present in selected columns " + cols);
            }
            indices[p] = found;
        }
        return indices;
    }

    private static void configureStreamingFetch(Statement stmt, int fetchSize) {
        try {
            stmt.setFetchSize(fetchSize);
        } catch (SQLException ignored) {
            // Some embedded drivers (e.g. older SQLite/UCanAccess) treat setFetchSize as optional hint.
        }
    }

    private static String safeUrl(Connection conn) {
        try {
            return conn.getMetaData() != null ? conn.getMetaData().getURL() : null;
        } catch (SQLException e) {
            return null;
        }
    }

    private static String quoteQualified(DatabaseDialect dialect, String name) {
        if (name == null) return "";
        if (!name.contains(".")) return dialect.quoteIdentifier(name);
        String[] parts = name.split("\\.", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(dialect.quoteIdentifier(parts[i]));
        }
        return sb.toString();
    }

    private static CompositeKey minNonNullKey(CompositeKey a, CompositeKey b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static CompositeKey maxNonNullKey(CompositeKey a, CompositeKey b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }
}
