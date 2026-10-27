package com.dbtool.util;

import com.dbtool.DatabaseManager;

import javax.swing.JTable;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for generating clean, database-compatible SQL INSERT statements from table row data.
 * Supports:
 * - Skipping primary key / identity / auto-increment columns (user requested)
 * - Quoting identifiers according to database dialect
 * - Handling NULL values and escaping single quotes
 * - Multi-row batch generation
 * - Filtering out joined table columns
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class SqlInsertGenerator {

    /**
     * Generates SQL INSERT statements for the selected rows of a table.
     *
     * @param table JTable containing the data
     * @param selectedRows view row indices to export
     * @param tableName name of the target table
     * @param dbManager DatabaseManager for metadata and dialect quoting (optional)
     * @param excludePrimaryKey true to omit primary key / auto-increment columns
     * @return Formatted SQL INSERT statements string
     */
    public static String generateInsertStatements(JTable table, int[] selectedRows, String tableName,
                                                 DatabaseManager dbManager, boolean excludePrimaryKey) {
        if (table == null || selectedRows == null || selectedRows.length == 0 || tableName == null || tableName.trim().isEmpty()) {
            return "";
        }

        List<String> pks = new ArrayList<>();
        if (excludePrimaryKey && dbManager != null) {
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

        // Fallback: If excludePrimaryKey is requested and no PK metadata is found, check for standard "id" column
        if (excludePrimaryKey && pks.isEmpty()) {
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
}
