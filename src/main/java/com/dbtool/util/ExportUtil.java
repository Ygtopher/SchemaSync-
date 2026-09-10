package com.dbtool.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
        try (Workbook wb = new XSSFWorkbook()) {
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
            // Auto-size columns
            for (int c = 0; c < m.getColumnCount(); c++) sheet.autoSizeColumn(c);
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
            StringBuilder cols = new StringBuilder("(");
            for (int c = 0; c < m.getColumnCount(); c++) {
                cols.append(m.getColumnName(c));
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
                pw.println("INSERT INTO " + tableName + " " + cols + " VALUES " + vals + ";");
            }
            JOptionPane.showMessageDialog(parent, "Exported INSERT statements to " + fc.getSelectedFile().getName());
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
