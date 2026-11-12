package com.dbtool.core.ai;

import com.dbtool.core.dialect.DatabaseDialect;
import com.dbtool.core.dialect.DialectRegistry;
import com.dbtool.core.inspector.JdbcSchemaInspector;
import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.SchemaMetadata;
import com.dbtool.core.model.TableMetadata;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Builds concise database schema context for LLM prompts with strict privacy separation:
 * <ul>
 *     <li><b>Schema-Only (default):</b> Shares table and column metadata only; zero table rows are read.</li>
 *     <li><b>Sample-Rows Opt-In:</b> Shares at most {@code maxSampleRows} sample rows per table when
 *     explicitly consented by the user.</li>
 * </ul>
 */
public final class SchemaContextBuilder {

    public static final int DEFAULT_MAX_SAMPLE_ROWS = 3;

    private SchemaContextBuilder() {}

    public static String buildContext(Connection conn, boolean includeSampleRows) {
        return buildContext(conn, null, includeSampleRows, DEFAULT_MAX_SAMPLE_ROWS);
    }

    public static String buildContext(Connection conn, String schemaPattern, boolean includeSampleRows, int maxSampleRows) {
        if (conn == null) {
            return "No database connected.";
        }
        try {
            if (conn.isClosed()) {
                return "Database connection is closed.";
            }
            JdbcSchemaInspector inspector = new JdbcSchemaInspector();
            SchemaMetadata schema = inspector.inspectSchema(conn, schemaPattern);
            StringBuilder sb = new StringBuilder();
            sb.append("Share Mode: ")
              .append(includeSampleRows ? "Schema + Sample Rows (Opt-In)" : "Schema Metadata Only (Zero Row Data)")
              .append("\n");

            if (schema.getTables().isEmpty()) {
                sb.append("No tables found.\n");
                return sb.toString();
            }

            String url = conn.getMetaData() != null ? conn.getMetaData().getURL() : null;
            DatabaseDialect dialect = DialectRegistry.getInstance().resolveFromJdbcUrl(url);

            for (TableMetadata tbl : schema.getTables().values()) {
                sb.append("Table: ").append(tbl.getTableName()).append(" (");
                List<ColumnMetadata> cols = tbl.getColumns();
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) sb.append(", ");
                    ColumnMetadata col = cols.get(i);
                    sb.append(col.getColumnName()).append(" ").append(col.getTypeName());
                    if (col.isPrimaryKey()) sb.append(" PK");
                }
                sb.append(")\n");

                if (includeSampleRows && maxSampleRows > 0) {
                    appendSampleRows(conn, dialect, tbl.getTableName(), maxSampleRows, sb);
                }
            }
            return sb.toString();
        } catch (SQLException e) {
            return "Error retrieving schema: " + e.getMessage();
        }
    }

    private static void appendSampleRows(
            Connection conn, DatabaseDialect dialect, String tableName, int limit, StringBuilder sb) {
        String sql = "SELECT * FROM " + dialect.quoteTableName(tableName);
        String paged = dialect.buildPagingQuery(sql, 0, limit);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(paged)) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            int rowNum = 0;
            while (rs.next() && rowNum < limit) {
                if (rowNum == 0) sb.append("  Sample rows:\n");
                sb.append("    {");
                for (int c = 1; c <= cols; c++) {
                    if (c > 1) sb.append(", ");
                    sb.append(md.getColumnLabel(c)).append("=");
                    Object val = rs.getObject(c);
                    String str = String.valueOf(val);
                    if (str.length() > 80) str = str.substring(0, 77) + "...";
                    sb.append(str);
                }
                sb.append("}\n");
                rowNum++;
            }
        } catch (SQLException ignored) {}
    }
}
