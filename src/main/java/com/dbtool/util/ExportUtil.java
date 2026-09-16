package com.dbtool.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ExportUtil {

    public static void exportCsv(JTable table, java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("export.csv"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            TableModel m = table.getModel();
            // Header
            for (int c = 0; c < m.getColumnCount(); c++) {
                pw.print(escapeCsv(m.getColumnName(c)));
                if (c < m.getColumnCount() - 1) pw.print(",");
            }
            pw.println();
            // Rows
            for (int r = 0; r < m.getRowCount(); r++) {
                for (int c = 0; c < m.getColumnCount(); c++) {
                    Object val = m.getValueAt(r, c);
                    pw.print(escapeCsv(val == null ? "" : val.toString()));
                    if (c < m.getColumnCount() - 1) pw.print(",");
                }
                pw.println();
            }
            JOptionPane.showMessageDialog(parent, "Exported to " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void exportExcel(JTable table, java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("export.xlsx"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try (Workbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("Data");
            TableModel m = table.getModel();
            // Header row
            Row header = sheet.createRow(0);
            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            for (int c = 0; c < m.getColumnCount(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(m.getColumnName(c));
                cell.setCellStyle(headerStyle);
            }
            // Data rows
            for (int r = 0; r < m.getRowCount(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < m.getColumnCount(); c++) {
                    Object val = m.getValueAt(r, c);
                    row.createCell(c).setCellValue(val == null ? "" : val.toString());
                }
            }
            // Auto-size columns removed for performance
            try (FileOutputStream fos = new FileOutputStream(fc.getSelectedFile())) {
                wb.write(fos);
            }
            JOptionPane.showMessageDialog(parent, "Exported to " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void copyToClipboard(JTable table) {
        TableModel m = table.getModel();
        StringBuilder sb = new StringBuilder();
        int[] rows = table.getSelectedRows();
        
        if (rows.length == 0) {
            // Rebuild for all rows
            for (int r = 0; r < m.getRowCount(); r++) {
                for (int c = 0; c < m.getColumnCount(); c++) {
                    Object val = m.getValueAt(r, c);
                    sb.append(val == null ? "" : val.toString());
                    if (c < m.getColumnCount() - 1) sb.append("\t");
                }
                sb.append("\n");
            }
        } else {
            for (int r : rows) {
                for (int c = 0; c < m.getColumnCount(); c++) {
                    Object val = m.getValueAt(table.convertRowIndexToModel(r), c);
                    sb.append(val == null ? "" : val.toString());
                    if (c < m.getColumnCount() - 1) sb.append("\t");
                }
                sb.append("\n");
            }
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(sb.toString()), null);
        JOptionPane.showMessageDialog(null, "Copied to clipboard!");
    }

    public static void exportSqlInserts(JTable table, String tableName, java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("inserts.sql"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            TableModel m = table.getModel();
            
            // Format table name
            String[] tParts = tableName.split("\\.");
            StringBuilder quotedTable = new StringBuilder();
            for (int i = 0; i < tParts.length; i++) {
                quotedTable.append("\"").append(tParts[i]).append("\"");
                if (i < tParts.length - 1) quotedTable.append(".");
            }
            
            StringBuilder cols = new StringBuilder("(");
            for (int c = 0; c < m.getColumnCount(); c++) {
                String colName = m.getColumnName(c);
                if (colName.contains(".")) {
                    colName = colName.substring(colName.lastIndexOf(".") + 1);
                }
                cols.append("\"").append(colName.replace("\"", "\"\"")).append("\"");
                if (c < m.getColumnCount() - 1) cols.append(", ");
            }
            cols.append(")");
            for (int r = 0; r < m.getRowCount(); r++) {
                StringBuilder vals = new StringBuilder("(");
                for (int c = 0; c < m.getColumnCount(); c++) {
                    Object val = m.getValueAt(r, c);
                    if (val == null) vals.append("NULL");
                    else vals.append("'").append(val.toString().replace("'", "''")).append("'");
                    if (c < m.getColumnCount() - 1) vals.append(", ");
                }
                vals.append(")");
                pw.println("INSERT INTO " + quotedTable.toString() + " " + cols + " VALUES " + vals + ";");
            }
            JOptionPane.showMessageDialog(parent, "Exported INSERT statements to " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void exportPdf(JTable table, java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("export.pdf"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try {
            com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4.rotate());
            com.lowagie.text.pdf.PdfWriter.getInstance(document, new java.io.FileOutputStream(fc.getSelectedFile()));
            document.open();

            TableModel m = table.getModel();
            com.lowagie.text.pdf.PdfPTable pdfTable = new com.lowagie.text.pdf.PdfPTable(m.getColumnCount());
            pdfTable.setWidthPercentage(100);

            float dataFontSize = Math.min(8f, Math.max(3f, 100f / m.getColumnCount()));
            float headerFontSize = dataFontSize + 1f;
            
            // Header row
            com.lowagie.text.Font headerFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, headerFontSize, java.awt.Color.WHITE);
            for (int c = 0; c < m.getColumnCount(); c++) {
                com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(m.getColumnName(c), headerFont));
                cell.setBackgroundColor(new java.awt.Color(50, 80, 130));
                cell.setPadding(3);
                pdfTable.addCell(cell);
            }

            // Data rows
            com.lowagie.text.Font dataFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, dataFontSize);
            for (int r = 0; r < m.getRowCount(); r++) {
                java.awt.Color rowBg = (r % 2 == 0) ? java.awt.Color.WHITE : new java.awt.Color(240, 240, 250);
                for (int c = 0; c < m.getColumnCount(); c++) {
                    Object val = m.getValueAt(r, c);
                    com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(val == null ? "" : val.toString(), dataFont));
                    cell.setBackgroundColor(rowBg);
                    cell.setPadding(4);
                    pdfTable.addCell(cell);
                }
            }

            document.add(pdfTable);
            document.close();
            JOptionPane.showMessageDialog(parent, "Exported to " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
