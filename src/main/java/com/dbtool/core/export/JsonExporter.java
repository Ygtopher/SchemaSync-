package com.dbtool.core.export;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import java.io.*;
import java.util.List;

public class JsonExporter implements DataExporter {
    @Override
    public ExportFormat getFormat() { return ExportFormat.JSON; }

    @Override
    public ExportResult export(QueryResult queryResult, File destinationFile, ExportOptions options) throws IOException {
        long start = System.currentTimeMillis();
        List<String> cols = queryResult.getColumnNames();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destinationFile), "UTF-8"))) {
            writer.write("[\n");
            for (int r = 0; r < queryResult.getRows().size(); r++) {
                RowData row = queryResult.getRows().get(r);
                writer.write("  {\n");
                for (int c = 0; c < cols.size(); c++) {
                    writer.write("    \"" + escapeJson(cols.get(c)) + "\": ");
                    Object v = row.getValue(c);
                    if (v == null) {
                        writer.write("null");
                    } else if (v instanceof Number || v instanceof Boolean) {
                        writer.write(v.toString());
                    } else {
                        writer.write("\"" + escapeJson(v.toString()) + "\"");
                    }
                    if (c < cols.size() - 1) writer.write(",");
                    writer.newLine();
                }
                writer.write("  }");
                if (r < queryResult.getRows().size() - 1) writer.write(",");
                writer.newLine();
            }
            writer.write("]\n");
        }
        return new ExportResult(destinationFile, queryResult.getRowCount(), destinationFile.length(), System.currentTimeMillis() - start);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
