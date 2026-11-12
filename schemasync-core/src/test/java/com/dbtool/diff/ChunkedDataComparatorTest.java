package com.dbtool.diff;

import com.dbtool.core.diff.ChunkedDataComparator;
import com.dbtool.core.diff.DataDiffResult;
import com.dbtool.core.diff.DiffChunkConfig;
import com.dbtool.core.diff.DifferenceType;
import com.dbtool.core.diff.RowDiff;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkedDataComparatorTest {

    @Test
    void testStreaming250kRowsWithSparseDiffsAndMatchedChunkSkipping() {
        final int totalRows = 250_000;
        DiffChunkConfig cfg = new DiffChunkConfig();
        cfg.setChunkSize(5000);
        ChunkedDataComparator comparator = new ChunkedDataComparator(cfg);

        // Source has IDs 1..250,000; Target has identical rows EXCEPT:
        // - ID 42,000 is removed
        // - ID 125,000 has modified amount (99.50 -> 199.50)
        // - ID 250,001 is added at end
        Iterator<RowData> src = new Iterator<RowData>() {
            int nextId = 1;
            @Override public boolean hasNext() { return nextId <= totalRows; }
            @Override public RowData next() {
                if (!hasNext()) throw new NoSuchElementException();
                int id = nextId++;
                return new RowData(new Object[]{id, "user_" + id, BigDecimal.valueOf(id, 2)});
            }
        };

        Iterator<RowData> dst = new Iterator<RowData>() {
            int nextId = 1;
            @Override public boolean hasNext() { return nextId <= totalRows + 1; }
            @Override public RowData next() {
                if (!hasNext()) throw new NoSuchElementException();
                if (nextId == 42_000) nextId++; // deleted in target
                int id = nextId++;
                if (id == 125_000) {
                    return new RowData(new Object[]{id, "user_" + id, new BigDecimal("9999.99")});
                }
                return new RowData(new Object[]{id, "user_" + id, BigDecimal.valueOf(id, 2)});
            }
        };

        DataDiffResult diff = comparator.compareOrderedStreams(
                "orders_250k",
                Arrays.asList("id", "username", "amount"),
                src,
                dst,
                new int[]{0}
        );

        assertEquals(totalRows, diff.getTotalSourceRows());
        assertEquals(totalRows, diff.getTotalTargetRows()); // 250,000 - 1 removed + 1 added
        assertEquals(3, diff.getRowDiffs().size(), "Expected exactly 3 diffs across 250k rows");

        assertTrue(diff.getChunksMatchedSkipped() >= 45,
                "Most clean 5k-row chunks should match & skip detailed diff allocation; got skipped=" + diff.getChunksMatchedSkipped());
        assertTrue(diff.getChunksDirtyResolved() >= 2 && diff.getChunksDirtyResolved() <= 5,
                "Only dirty chunks should resolve diffs; got dirty=" + diff.getChunksDirtyResolved());

        RowDiff removed = diff.getRowDiffs().get(0);
        assertEquals(DifferenceType.REMOVED, removed.getType());
        assertEquals(42_000, removed.getPrimaryKey());

        RowDiff modified = diff.getRowDiffs().get(1);
        assertEquals(DifferenceType.MODIFIED, modified.getType());
        assertEquals(125_000, modified.getPrimaryKey());
        assertEquals(1, modified.getCellDiffs().size());
        assertEquals("amount", modified.getCellDiffs().get(0).getColumnName());

        RowDiff added = diff.getRowDiffs().get(2);
        assertEquals(DifferenceType.ADDED, added.getType());
        assertEquals(250_001, added.getPrimaryKey());
    }

    @Test
    void testTwoPhaseJdbcChunkDiffAcrossLiveH2Connections() throws Exception {
        String urlA = "jdbc:h2:mem:chunk_src_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String urlB = "jdbc:h2:mem:chunk_dst_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";

        try (Connection cA = DriverManager.getConnection(urlA, "sa", "");
             Connection cB = DriverManager.getConnection(urlB, "sa", "")) {
            try (Statement sA = cA.createStatement(); Statement sB = cB.createStatement()) {
                sA.execute("CREATE TABLE accounts (id INT PRIMARY KEY, code VARCHAR(40), balance DECIMAL(12,2))");
                sB.execute("CREATE TABLE accounts (id INT PRIMARY KEY, code VARCHAR(40), balance DECIMAL(12,2))");
            }

            try (PreparedStatement pA = cA.prepareStatement("INSERT INTO accounts VALUES (?, ?, ?)");
                 PreparedStatement pB = cB.prepareStatement("INSERT INTO accounts VALUES (?, ?, ?)")) {
                for (int id = 1; id <= 2500; id++) {
                    pA.setInt(1, id);
                    pA.setString(2, "ACC-" + id);
                    pA.setBigDecimal(3, new BigDecimal(id + ".50"));
                    pA.addBatch();

                    if (id == 777) {
                        // modified in B
                        pB.setInt(1, id);
                        pB.setString(2, "ACC-777-EDITED");
                        pB.setBigDecimal(3, new BigDecimal("777.50"));
                        pB.addBatch();
                    } else if (id != 2100) { // 2100 deleted in B
                        pB.setInt(1, id);
                        pB.setString(2, "ACC-" + id);
                        pB.setBigDecimal(3, new BigDecimal(id + ".5")); // equivalent scale test!
                        pB.addBatch();
                    }
                }
                pA.executeBatch();
                pB.executeBatch();
            }

            try (Statement sB = cB.createStatement()) {
                sB.executeUpdate("INSERT INTO accounts VALUES (2501, 'ACC-2501', 2501.50)");
            }

            DiffChunkConfig cfg = new DiffChunkConfig();
            cfg.setChunkSize(500);
            cfg.setFetchSize(250);
            ChunkedDataComparator comparator = new ChunkedDataComparator(cfg);

            DataDiffResult diff = comparator.compareTables(cA, cB, "ACCOUNTS", Collections.singletonList("ID"));
            assertEquals(2500, diff.getTotalSourceRows());
            assertEquals(2500, diff.getTotalTargetRows());
            assertTrue(diff.getChunksMatchedSkipped() >= 2,
                    "Identical 500-row chunks must be skipped in Phase 2; skipped=" + diff.getChunksMatchedSkipped());
            assertEquals(3, diff.getRowDiffs().size(), "Expected modified 777, removed 2100, added 2501");

            assertEquals(DifferenceType.MODIFIED, diff.getRowDiffs().get(0).getType());
            assertEquals(777, diff.getRowDiffs().get(0).getPrimaryKey());
            assertEquals(DifferenceType.REMOVED, diff.getRowDiffs().get(1).getType());
            assertEquals(2100, diff.getRowDiffs().get(1).getPrimaryKey());
            assertEquals(DifferenceType.ADDED, diff.getRowDiffs().get(2).getType());
            assertEquals(2501, diff.getRowDiffs().get(2).getPrimaryKey());
        }
    }
}
