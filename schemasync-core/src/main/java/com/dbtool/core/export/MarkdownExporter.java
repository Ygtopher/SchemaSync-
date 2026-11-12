package com.dbtool.core.export;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import java.io.*;
import java.util.List;

public class MarkdownExporter implements DataExporter {
    @Override
    public ExportFormat getFormat() { return ExportFormat.MARKDOWN; }

    @Override
    public ExportResult export(QueryResult queryResult, File destinationFile, ExportOptions options) throws IOException {
        long start = System.currentTimeMillis();
        List<String> cols = queryResult.getColumnNames();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destinationFile), "UTF-8"))) {
            writer.write("| " + String.join(" | ", cols) + " |\n");
            writer.write("|" + "---|".repeat(cols.size()) + "\n");
            for (RowData row : queryResult.getRows()) {
                writer.write("|");
                for (int i = 0; i < row.size(); i++) {
                    Object v = row.getValue(i);
                    writer.write(" " + (v != null ? v.toString().replace("|", "\\|") : "NULL") + " |");
                }
                writer.newLine();
            }
        }
        return new ExportResult(destinationFile, queryResult.getRowCount(), destinationFile.length(), System.currentTimeMillis() - start);
    }
}
