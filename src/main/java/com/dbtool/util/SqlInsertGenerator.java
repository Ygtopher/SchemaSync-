package com.dbtool.util;

import com.dbtool.DatabaseManager;

import javax.swing.JTable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for generating clean, database-compatible SQL INSERT/UPDATE statements,
 * JSON payloads, Markdown tables, and live selection telemetry from JTable row data.
 * Supports:
 * - Skipping primary key / identity / auto-increment columns on INSERT (user requested)
 * - Generating targeted SQL UPDATE statements keyed on primary key columns
 * - Exporting selected rows as JSON Array or GitHub Markdown Table
 * - Live numeric/cell selection summary statistics (Count, Sum, Avg, Min, Max, Nulls)
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class SqlInsertGenerator {

    public static String generateInsertStatements(JTable table, int[] selectedRows, String tableName,
                                                 DatabaseManager dbManager, boolean excludePrimaryKey) {
        if (table == null || selectedRows == null || selectedRows.length == 0 || tableName == null || tableName.trim().isEmpty()) {
            return "";
        }

        List<String> pks = resolvePrimaryKeys(table, tableName, dbManager, excludePrimaryKey);
        String baseTableName = tableName.contains(".") ? tableName.substring(tableName.lastIndexOf(".") + 1) : tableName;

        List<Integer> targetColIndices = new ArrayList<>();
        List<String> targetColNames = new ArrayList<>();

        for (int c = 0; c < table.getColumnCount(); c++) {
            String rawColName = table.getColumnName(c);
            String actualCol = rawColName;
            if (rawColName.contains(".")) {
                String[] p = rawColName.split("\\.", 2);
                if (p.length == 2) {
                    if (!p[0].equalsIgnoreCase(tableName) && !p[0].equalsIgnoreCase(baseTableName)) {
                        continue; // Column belongs to another joined table
                    }
                    actualCol = p[1];
                }
            }

            if (excludePrimaryKey && isPrimaryKeyColumn(actualCol, pks)) {
                continue; // Do not add the primary key!
            }

            targetColIndices.add(c);
            targetColNames.add(actualCol);
        }

        // Safeguard fallback: If ALL columns were marked as primary keys (e.g. pure association/join table),
        // keep all columns to avoid generating invalid SQL syntax like "INSERT INTO table () VALUES ()"
        if (targetColIndices.isEmpty()) {
            for (int c = 0; c < table.getColumnCount(); c++) {
                String rawColName = table.getColumnName(c);
                String actualCol = rawColName;
                if (rawColName.contains(".")) {
                    String[] p = rawColName.split("\\.", 2);
                    if (p.length == 2) {
                        if (!p[0].equalsIgnoreCase(tableName) && !p[0].equalsIgnoreCase(baseTableName)) {
                            continue;
                        }
                        actualCol = p[1];
                    }
                }
                targetColIndices.add(c);
                targetColNames.add(actualCol);
            }
        }

        String quotedTable = dbManager != null ? dbManager.quoteTableName(tableName) : quoteIdentifier(tableName);
        StringBuilder sb = new StringBuilder();

        for (int row : selectedRows) {
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(quotedTable).append(" (");
            StringBuilder vals = new StringBuilder("VALUES (");

            for (int i = 0; i < targetColIndices.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                    vals.append(", ");
                }
                String col = targetColNames.get(i);
                sql.append(dbManager != null ? dbManager.quoteColumnName(col) : quoteIdentifier(col));

                int c = targetColIndices.get(i);
                Object val = table.getValueAt(row, c);
                if (val == null) {
                    vals.append("NULL");
                } else {
                    vals.append("'").append(val.toString().replace("'", "''")).append("'");
                }
            }
            sql.append(") ").append(vals).append(");\n");
            sb.append(sql);
        }

        return sb.toString();
    }

    /**
     * Generates SQL UPDATE statements for the selected rows of a table, using primary key columns
     * in the WHERE clause and non-primary-key columns in the SET clause.
     */
    public static String generateUpdateStatements(JTable table, int[] selectedRows, String tableName, DatabaseManager dbManager) {
        if (table == null || selectedRows == null || selectedRows.length == 0 || tableName == null || tableName.trim().isEmpty()) {
            return "";
        }

        List<String> pks = resolvePrimaryKeys(table, tableName, dbManager, true);
        String baseTableName = tableName.contains(".") ? tableName.substring(tableName.lastIndexOf(".") + 1) : tableName;

        List<Integer> setColIndices = new ArrayList<>();
        List<String> setColNames = new ArrayList<>();
        List<Integer> whereColIndices = new ArrayList<>();
        List<String> whereColNames = new ArrayList<>();

        for (int c = 0; c < table.getColumnCount(); c++) {
            String rawColName = table.getColumnName(c);
            String actualCol = rawColName;
            if (rawColName.contains(".")) {
                String[] p = rawColName.split("\\.", 2);
                if (p.length == 2) {
                    if (!p[0].equalsIgnoreCase(tableName) && !p[0].equalsIgnoreCase(baseTableName)) {
                        continue;
                    }
                    actualCol = p[1];
                }
            }

            if (isPrimaryKeyColumn(actualCol, pks)) {
                whereColIndices.add(c);
                whereColNames.add(actualCol);
            } else {
                setColIndices.add(c);
                setColNames.add(actualCol);
            }
        }

        if (whereColIndices.isEmpty() && table.getColumnCount() > 0) {
            whereColIndices.add(0);
            String firstCol = table.getColumnName(0);
            if (firstCol.contains(".")) firstCol = firstCol.substring(firstCol.lastIndexOf(".") + 1);
            whereColNames.add(firstCol);
            if (!setColIndices.isEmpty() && setColIndices.get(0) == 0) {
                setColIndices.remove(0);
                setColNames.remove(0);
            }
        }

        if (setColIndices.isEmpty()) {
            setColIndices.addAll(whereColIndices);
            setColNames.addAll(whereColNames);
        }

        String quotedTable = dbManager != null ? dbManager.quoteTableName(tableName) : quoteIdentifier(tableName);
        StringBuilder sb = new StringBuilder();

        for (int row : selectedRows) {
            StringBuilder sql = new StringBuilder("UPDATE ").append(quotedTable).append(" SET ");
            for (int i = 0; i < setColIndices.size(); i++) {
                if (i > 0) sql.append(", ");
                String col = setColNames.get(i);
                sql.append(dbManager != null ? dbManager.quoteColumnName(col) : quoteIdentifier(col)).append(" = ");
                Object val = table.getValueAt(row, setColIndices.get(i));
                if (val == null) {
                    sql.append("NULL");
                } else {
                    sql.append("'").append(val.toString().replace("'", "''")).append("'");
                }
            }
            sql.append(" WHERE ");
            for (int i = 0; i < whereColIndices.size(); i++) {
                if (i > 0) sql.append(" AND ");
                String col = whereColNames.get(i);
                sql.append(dbManager != null ? dbManager.quoteColumnName(col) : quoteIdentifier(col));
                Object val = table.getValueAt(row, whereColIndices.get(i));
                if (val == null) {
                    sql.append(" IS NULL");
                } else {
                    sql.append(" = '").append(val.toString().replace("'", "''")).append("'");
                }
            }
            sql.append(";\n");
            sb.append(sql);
        }

        return sb.toString();
    }

    /**
     * Generates a formatted JSON array representing the selected rows of a JTable.
     */
    public static String generateJsonArray(JTable table, int[] selectedRows) {
        if (table == null || selectedRows == null || selectedRows.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[\n");
        int colCount = table.getColumnCount();
        for (int rIdx = 0; rIdx < selectedRows.length; rIdx++) {
            int row = selectedRows[rIdx];
            sb.append("  {\n");
            for (int c = 0; c < colCount; c++) {
                String colName = table.getColumnName(c);
                Object val = table.getValueAt(row, c);
                sb.append("    \"").append(escapeJson(colName)).append("\": ");
                if (val == null) {
                    sb.append("null");
                } else if (val instanceof Number || val instanceof Boolean) {
                    sb.append(val.toString());
                } else {
                    sb.append("\"").append(escapeJson(val.toString())).append("\"");
                }
                if (c < colCount - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  }");
            if (rIdx < selectedRows.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Generates a GitHub-flavored Markdown table for the selected rows of a JTable.
     */
    public static String generateMarkdownTable(JTable table, int[] selectedRows) {
        if (table == null || selectedRows == null || selectedRows.length == 0) {
            return "";
        }
        int colCount = table.getColumnCount();
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < colCount; c++) {
            sb.append(" ").append(table.getColumnName(c).replace("|", "\\|")).append(" |");
        }
        sb.append("\n|");
        for (int c = 0; c < colCount; c++) {
            sb.append(" :--- |");
        }
        sb.append("\n");
        for (int row : selectedRows) {
            sb.append("|");
            for (int c = 0; c < colCount; c++) {
                Object val = table.getValueAt(row, c);
                String s = val == null ? "NULL" : val.toString().replace("|", "\\|").replace("\r", "").replace("\n", " ");
                sb.append(" ").append(s).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Computes live summary statistics (Selected Cells, Non-Null, Nulls, Numeric Count, Sum, Avg, Min, Max)
     * for the currently selected rows/columns of a JTable.
     */
    public static String calculateSelectionStats(JTable table) {
        if (table == null) return "";
        int[] rows = table.getSelectedRows();
        if (rows == null || rows.length == 0) {
            return "Total Rows: " + table.getRowCount() + " | Select cells or rows to view live summary stats";
        }
        int[] cols = table.getSelectedColumns();
        if (cols == null || cols.length == 0 || !table.getCellSelectionEnabled()) {
            cols = new int[table.getColumnCount()];
            for (int i = 0; i < cols.length; i++) cols[i] = i;
        }

        int totalCells = 0;
        int nullCount = 0;
        int numericCount = 0;
        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int r : rows) {
            if (r < 0 || r >= table.getRowCount()) continue;
            for (int c : cols) {
                if (c < 0 || c >= table.getColumnCount()) continue;
                totalCells++;
                Object val = table.getValueAt(r, c);
                if (val == null) {
                    nullCount++;
                } else {
                    Double num = parseNumeric(val);
                    if (num != null) {
                        numericCount++;
                        sum += num;
                        if (num < min) min = num;
                        if (num > max) max = num;
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Selected Rows: ").append(rows.length)
          .append(" | Cells: ").append(totalCells)
          .append(" | Nulls: ").append(nullCount);

        if (numericCount > 0) {
            DecimalFormat df = new DecimalFormat("#,##0.##");
            double avg = sum / numericCount;
            sb.append("  ||  Numeric: ").append(numericCount)
              .append(" | Sum: ").append(df.format(sum))
              .append(" | Avg: ").append(df.format(avg))
              .append(" | Min: ").append(df.format(min))
              .append(" | Max: ").append(df.format(max));
        }

        return sb.toString();
    }

    private static Double parseNumeric(Object val) {
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        String s = val.toString().trim();
        if (s.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static List<String> resolvePrimaryKeys(JTable table, String tableName, DatabaseManager dbManager, boolean checkHeuristic) {
        List<String> pks = new ArrayList<>();
        if (checkHeuristic && dbManager != null) {
            try {
                List<String> metaPks = dbManager.getPrimaryKeys(tableName);
                if (metaPks != null) {
                    for (String pk : metaPks) {
                        if (pk != null && !pks.contains(pk)) pks.add(pk);
                    }
                }
            } catch (Exception ignored) {}

            if (pks.isEmpty()) {
                try {
                    List<DatabaseManager.ColumnDefinition> detailed = dbManager.getDetailedColumns(tableName);
                    if (detailed != null) {
                        for (DatabaseManager.ColumnDefinition cd : detailed) {
                            if (cd != null && (cd.isPrimaryKey || cd.isAutoIncrement)) {
                                if (cd.name != null && !pks.contains(cd.name)) {
                                    pks.add(cd.name);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (checkHeuristic && pks.isEmpty()) {
            for (int c = 0; c < table.getColumnCount(); c++) {
                String col = table.getColumnName(c);
                if (col.contains(".")) {
                    col = col.substring(col.lastIndexOf(".") + 1);
                }
                if (col.equalsIgnoreCase("id")) {
                    pks.add(col);
                    break;
                }
            }
        }
        return pks;
    }

    private static boolean isPrimaryKeyColumn(String colName, List<String> pks) {
        if (colName == null || pks == null || pks.isEmpty()) return false;
        for (String pk : pks) {
            if (pk != null && pk.equalsIgnoreCase(colName)) {
                return true;
            }
        }
        return false;
    }

    private static String quoteIdentifier(String id) {
        if (id == null) return "";
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
