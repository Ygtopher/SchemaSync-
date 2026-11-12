package com.dbtool.core.export;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import java.io.*;
import java.util.List;

public class SqlInsertExporter implements DataExporter {
    @Override
    public ExportFormat getFormat() { return ExportFormat.SQL_INSERTS; }

    @Override
    public ExportResult export(QueryResult queryResult, File destinationFile, ExportOptions options) throws IOException {
        long start = System.currentTimeMillis();
        String tbl = options != null && options.getTableName() != null ? options.getTableName() : "exported_table";
        List<String> cols = queryResult.getColumnNames();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destinationFile), "UTF-8"))) {
            for (RowData row : queryResult.getRows()) {
                StringBuilder sb = new StringBuilder("INSERT INTO ").append(tbl).append(" (");
                sb.append(String.join(", ", cols)).append(") VALUES (");
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) sb.append(", ");
                    Object v = row.getValue(i);
                    if (v == null) {
                        sb.append("NULL");
                    } else if (v instanceof Number || v instanceof Boolean) {
                        sb.append(v);
                    } else {
                        sb.append("'").append(v.toString().replace("'", "''")).append("'");
                    }
                }
                sb.append(");\n");
                writer.write(sb.toString());
            }
        }
        return new ExportResult(destinationFile, queryResult.getRowCount(), destinationFile.length(), System.currentTimeMillis() - start);
    }
}
