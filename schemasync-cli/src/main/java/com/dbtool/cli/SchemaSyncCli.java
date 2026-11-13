package com.dbtool.cli;

import com.dbtool.core.connection.SessionPoolManager;
import com.dbtool.core.diff.ColumnDiff;
import com.dbtool.core.diff.DdlMigrationGenerator;
import com.dbtool.core.diff.DifferenceType;
import com.dbtool.core.diff.SchemaComparator;
import com.dbtool.core.diff.SchemaDiffResult;
import com.dbtool.core.diff.TableDiff;
import com.dbtool.core.export.CsvExporter;
import com.dbtool.core.export.DataExporter;
import com.dbtool.core.export.ExcelExporter;
import com.dbtool.core.export.ExportOptions;
import com.dbtool.core.export.ExportResult;
import com.dbtool.core.export.JsonExporter;
import com.dbtool.core.export.MarkdownExporter;
import com.dbtool.core.export.SqlInsertExporter;
import com.dbtool.core.inspector.JdbcSchemaInspector;
import com.dbtool.core.model.SchemaMetadata;
import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(
        name = "schemasync",
        mixinStandardHelpOptions = true,
        version = "SchemaSync CLI 2.3.0",
        description = "Headless CLI for SchemaSync schema diff, CI drift detection, and SQL export.",
        subcommands = {
                SchemaSyncCli.DiffCommand.class,
                SchemaSyncCli.DriftCheckCommand.class,
                SchemaSyncCli.ExportCommand.class
        }
)
public class SchemaSyncCli implements Runnable {

    public static final int EXIT_DRIFT_DETECTED = 2;

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        int code = execute(args);
        System.exit(code);
    }

    public static int execute(String... args) {
        return new CommandLine(new SchemaSyncCli()).execute(args);
    }

    @Command(name = "diff", description = "Compare schemas between two JDBC URLs and emit summary + DDL migration script.")
    public static class DiffCommand implements Callable<Integer> {
        @Option(names = {"--source"}, required = true, description = "Source JDBC URL")
        String sourceUrl;

        @Option(names = {"--target"}, required = true, description = "Target JDBC URL")
        String targetUrl;

        @Option(names = {"--source-user"}, defaultValue = "", description = "Source username")
        String sourceUser;

        @Option(names = {"--source-pass"}, defaultValue = "", description = "Source password")
        String sourcePass;

        @Option(names = {"--target-user"}, defaultValue = "", description = "Target username")
        String targetUser;

        @Option(names = {"--target-pass"}, defaultValue = "", description = "Target password")
        String targetPass;

        @Option(names = {"--schema"}, description = "Optional schema pattern filter (null inspects all schemas)")
        String schemaPattern;

        @Option(names = {"--ddl"}, defaultValue = "false", description = "Print generated ALTER TABLE DDL statements")
        boolean printDdl;

        @Option(names = {"--fail-on-drift"}, defaultValue = "false", description = "Exit with code 2 when differences are found")
        boolean failOnDrift;

        @Override
        public Integer call() throws Exception {
            SchemaDiffResult diffResult = runSchemaDiff(sourceUrl, sourceUser, sourcePass, targetUrl, targetUser, targetPass, schemaPattern);
            DdlMigrationGenerator ddlGen = new DdlMigrationGenerator();

            int changed = 0;
            for (TableDiff td : diffResult.getTableDiffs()) {
                if (td.getType() != DifferenceType.IDENTICAL) {
                    changed++;
                    System.out.println("[" + td.getType() + "] Table: " + td.getTableName());
                    for (ColumnDiff cd : td.getColumnDiffs()) {
                        System.out.println("  - " + cd.getType() + " column " + cd.getColumnName()
                                + (cd.getDescription() != null ? " (" + cd.getDescription() + ")" : ""));
                    }
                    if (printDdl) {
                        for (String sql : ddlGen.generateMigrationSql(td)) {
                            System.out.println("  DDL> " + sql);
                        }
                    }
                }
            }

            if (!diffResult.hasDifferences()) {
                System.out.println("Schemas match (" + diffResult.getTableDiffs().size() + " table(s) checked).");
                return 0;
            }
            System.out.println("Detected drift across " + changed + " table(s).");
            return failOnDrift ? EXIT_DRIFT_DETECTED : 0;
        }
    }

    @Command(name = "drift-check", description = "Verify zero schema drift between source and target DBs (returns exit code 2 on drift).")
    public static class DriftCheckCommand implements Callable<Integer> {
        @Option(names = {"--source"}, required = true, description = "Source JDBC URL")
        String sourceUrl;

        @Option(names = {"--target"}, required = true, description = "Target JDBC URL")
        String targetUrl;

        @Option(names = {"--source-user"}, defaultValue = "", description = "Source username")
        String sourceUser;

        @Option(names = {"--source-pass"}, defaultValue = "", description = "Source password")
        String sourcePass;

        @Option(names = {"--target-user"}, defaultValue = "", description = "Target username")
        String targetUser;

        @Option(names = {"--target-pass"}, defaultValue = "", description = "Target password")
        String targetPass;

        @Option(names = {"--schema"}, description = "Optional schema pattern filter")
        String schemaPattern;

        @Override
        public Integer call() throws Exception {
            SchemaDiffResult diffResult = runSchemaDiff(sourceUrl, sourceUser, sourcePass, targetUrl, targetUser, targetPass, schemaPattern);
            if (!diffResult.hasDifferences()) {
                System.out.println("DRIFT CHECK PASSED: 0 differences across " + diffResult.getTableDiffs().size() + " table(s).");
                return 0;
            }
            long driftCount = diffResult.getTableDiffs().stream()
                    .filter(td -> td.getType() != DifferenceType.IDENTICAL)
                    .count();
            System.err.println("DRIFT CHECK FAILED: " + driftCount + " table(s) differ.");
            return EXIT_DRIFT_DETECTED;
        }
    }

    @Command(name = "export", description = "Execute a SQL query and export results to csv, json, markdown, xlsx, or sql.")
    public static class ExportCommand implements Callable<Integer> {
        @Option(names = {"--url"}, required = true, description = "Database JDBC URL")
        String jdbcUrl;

        @Option(names = {"--user"}, defaultValue = "", description = "Database username")
        String user;

        @Option(names = {"--pass"}, defaultValue = "", description = "Database password")
        String pass;

        @Option(names = {"--query"}, required = true, description = "SQL query to execute")
        String query;

        @Option(names = {"--format"}, defaultValue = "csv", description = "Export format: csv, json, markdown, xlsx, sql")
        String format;

        @Option(names = {"--table"}, defaultValue = "exported_data", description = "Target table name (for sql inserts)")
        String tableName;

        @Option(names = {"--out"}, required = true, description = "Destination output file path")
        File outFile;

        @Override
        public Integer call() throws Exception {
            long start = System.currentTimeMillis();
            QueryResult qr = new QueryResult();

            try (SessionPoolManager pool = new SessionPoolManager(null, jdbcUrl, emptyToNull(user), emptyToNull(pass), 30);
                 Connection conn = pool.borrowConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                List<String> colNames = new ArrayList<>();
                for (int c = 1; c <= cols; c++) {
                    colNames.add(meta.getColumnLabel(c));
                }
                qr.setColumnNames(colNames);
                while (rs.next()) {
                    Object[] row = new Object[cols];
                    for (int c = 1; c <= cols; c++) {
                        row[c - 1] = rs.getObject(c);
                    }
                    qr.addRow(new RowData(row));
                }
            }
            qr.setExecutionTimeMs(System.currentTimeMillis() - start);

            ExportOptions options = new ExportOptions();
            options.setTableName(tableName);
            DataExporter exporter = resolveExporter(format);
            ExportResult result = exporter.export(qr, outFile, options);
            System.out.println("Exported " + result.getRowCount() + " row(s) as " + format + " -> " + outFile.getAbsolutePath());
            return 0;
        }
    }

    private static SchemaDiffResult runSchemaDiff(
            String sourceUrl, String sourceUser, String sourcePass,
            String targetUrl, String targetUser, String targetPass,
            String schemaPattern
    ) throws Exception {
        JdbcSchemaInspector inspector = new JdbcSchemaInspector();
        SchemaMetadata sourceSchema;
        SchemaMetadata targetSchema;

        try (SessionPoolManager srcPool = new SessionPoolManager(null, sourceUrl, emptyToNull(sourceUser), emptyToNull(sourcePass), 30);
             SessionPoolManager dstPool = new SessionPoolManager(null, targetUrl, emptyToNull(targetUser), emptyToNull(targetPass), 30);
             Connection srcConn = srcPool.borrowConnection();
             Connection dstConn = dstPool.borrowConnection()) {
            sourceSchema = inspector.inspectSchema(srcConn, schemaPattern);
            targetSchema = inspector.inspectSchema(dstConn, schemaPattern);
        }

        return new SchemaComparator().compareSchemas(
                new ArrayList<>(sourceSchema.getTables().values()),
                new ArrayList<>(targetSchema.getTables().values())
        );
    }

    private static DataExporter resolveExporter(String format) {
        String key = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "csv": return new CsvExporter();
            case "json": return new JsonExporter();
            case "markdown":
            case "md": return new MarkdownExporter();
            case "xlsx":
            case "excel": return new ExcelExporter();
            case "sql": return new SqlInsertExporter();
            default: throw new IllegalArgumentException("Unsupported export format: " + format);
        }
    }

    private static String emptyToNull(String val) {
        return (val == null || val.isEmpty()) ? null : val;
    }
}
