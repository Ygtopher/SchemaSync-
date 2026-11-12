package com.dbtool.core.export;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import java.io.*;
import java.util.List;

public class CsvExporter implements DataExporter {
    @Override
    public ExportFormat getFormat() { return ExportFormat.CSV; }

    @Override
    public ExportResult export(QueryResult queryResult, File destinationFile, ExportOptions options) throws IOException {
        long start = System.currentTimeMillis();
        char delim = options != null ? options.getDelimiter() : ',';

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destinationFile), "UTF-8"))) {
            if (options == null || options.isIncludeHeaders()) {
                writeLine(writer, queryResult.getColumnNames(), delim);
            }
            for (RowData row : queryResult.getRows()) {
                writeRow(writer, row.getValues(), delim);
            }
        }
        return new ExportResult(destinationFile, queryResult.getRowCount(), destinationFile.length(), System.currentTimeMillis() - start);
    }

    private void writeLine(BufferedWriter writer, List<String> items, char delim) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) writer.write(delim);
            writer.write(escapeCsv(items.get(i), delim));
        }
        writer.newLine();
    }

    private void writeRow(BufferedWriter writer, List<Object> items, char delim) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) writer.write(delim);
            Object obj = items.get(i);
            writer.write(escapeCsv(obj != null ? obj.toString() : "", delim));
        }
        writer.newLine();
    }

    private String escapeCsv(String val, char delim) {
        if (val == null) return "";
        if (val.contains(String.valueOf(delim)) || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
