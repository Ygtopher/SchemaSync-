package com.dbtool.core.export;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ExcelExporter implements DataExporter {
    @Override
    public ExportFormat getFormat() { return ExportFormat.EXCEL_XLSX; }

    @Override
    public ExportResult export(QueryResult queryResult, File destinationFile, ExportOptions options) throws IOException {
        long start = System.currentTimeMillis();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(options != null ? options.getTableName() : "Data");
            int rowIdx = 0;

            if (options == null || options.isIncludeHeaders()) {
                Row headerRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < queryResult.getColumnNames().size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(queryResult.getColumnNames().get(i));
                }
            }

            for (RowData r : queryResult.getRows()) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < r.size(); i++) {
                    Cell cell = row.createCell(i);
                    Object val = r.getValue(i);
                    if (val instanceof Number) {
                        cell.setCellValue(((Number) val).doubleValue());
                    } else if (val instanceof Boolean) {
                        cell.setCellValue((Boolean) val);
                    } else if (val != null) {
                        cell.setCellValue(val.toString());
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
                workbook.write(fos);
            }
        }
        return new ExportResult(destinationFile, queryResult.getRowCount(), destinationFile.length(), System.currentTimeMillis() - start);
    }
}
