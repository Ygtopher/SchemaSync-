package com.dbtool.core.diff;

import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;

import java.util.Objects;

public class SchemaComparator {

    private final DiffOptions options;

    public SchemaComparator() {
        this(new DiffOptions());
    }

    public SchemaComparator(DiffOptions options) {
        this.options = options != null ? options : new DiffOptions();
    }

    public TableDiff compareTables(TableMetadata source, TableMetadata target) {
        String tableName = source != null ? source.getTableName() : (target != null ? target.getTableName() : "UNKNOWN");
        if (source == null && target == null) return new TableDiff(tableName, DifferenceType.IDENTICAL);
        if (source == null) {
            TableDiff td = new TableDiff(tableName, DifferenceType.ADDED);
            td.setTargetTable(target);
            for (ColumnMetadata col : target.getColumns()) {
                if (col != null && col.getColumnName() != null) {
                    td.addColumnDiff(new ColumnDiff(col.getColumnName(), DifferenceType.ADDED, null, col, "New column in target"));
                }
            }
            return td;
        }
        if (target == null) {
            TableDiff td = new TableDiff(tableName, DifferenceType.REMOVED);
            td.setSourceTable(source);
            for (ColumnMetadata col : source.getColumns()) {
                if (col != null && col.getColumnName() != null) {
                    td.addColumnDiff(new ColumnDiff(col.getColumnName(), DifferenceType.REMOVED, col, null, "Column missing in target"));
                }
            }
            return td;
        }

        TableDiff tableDiff = new TableDiff(tableName, DifferenceType.IDENTICAL);
        tableDiff.setSourceTable(source);
        tableDiff.setTargetTable(target);

        for (ColumnMetadata sCol : source.getColumns()) {
            if (sCol == null || sCol.getColumnName() == null) continue;
            ColumnMetadata tCol = target.findColumn(sCol.getColumnName());
            if (tCol == null) {
                tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.REMOVED, sCol, null, "Column missing in target"));
            } else {
                String sType = sCol.getTypeName() != null ? sCol.getTypeName() : "";
                String tType = tCol.getTypeName() != null ? tCol.getTypeName() : "";
                if (!sType.equalsIgnoreCase(tType)) {
                    if (!options.isSmartTypeEquivalence() || !areTypesEquivalent(sType, tType)) {
                        tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.MODIFIED, sCol, tCol, "Type mismatch: " + sType + " vs " + tType));
                    }
                }
                if (sCol.isNullable() != tCol.isNullable()) {
                    tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.MODIFIED, sCol, tCol, "Nullability changed: " + sCol.isNullable() + " -> " + tCol.isNullable()));
                }
                if (sCol.isPrimaryKey() != tCol.isPrimaryKey()) {
                    tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.MODIFIED, sCol, tCol, "Primary Key flag changed: " + sCol.isPrimaryKey() + " -> " + tCol.isPrimaryKey()));
                }
                if (!options.isIgnoreDefaultValues() && !Objects.equals(sCol.getDefaultValue(), tCol.getDefaultValue())) {
                    tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.MODIFIED, sCol, tCol, "Default value changed: " + sCol.getDefaultValue() + " -> " + tCol.getDefaultValue()));
                }
            }
        }
        for (ColumnMetadata tCol : target.getColumns()) {
            if (tCol == null || tCol.getColumnName() == null) continue;
            if (source.findColumn(tCol.getColumnName()) == null) {
                tableDiff.addColumnDiff(new ColumnDiff(tCol.getColumnName(), DifferenceType.ADDED, null, tCol, "New column in target"));
            }
        }
        return tableDiff;
    }

    public static boolean areTypesEquivalent(String typeA, String typeB) {
        if (typeA == null || typeB == null) return false;
        if (typeA.equalsIgnoreCase(typeB)) return true;
        String normA = normalizeType(typeA);
        String normB = normalizeType(typeB);
        return normA.equalsIgnoreCase(normB);
    }

    public static String normalizeType(String type) {
        if (type == null) return "";
        String t = type.trim().toUpperCase().replaceAll("\\s+", " ");
        String base = t.contains("(") ? t.substring(0, t.indexOf('(')).trim() : t;
        switch (base) {
            case "INT":
            case "INTEGER":
            case "INT4":
            case "MEDIUMINT":
                return "INTEGER";
            case "BIGINT":
            case "INT8":
                return "BIGINT";
            case "SMALLINT":
            case "INT2":
                return "SMALLINT";
            case "TINYINT":
            case "BOOL":
            case "BOOLEAN":
            case "BIT":
                return "BOOLEAN";
            case "VARCHAR":
            case "VARCHAR2":
            case "CHARACTER VARYING":
            case "CHAR VARYING":
            case "TEXT":
            case "STRING":
            case "LONGVARCHAR":
            case "CLOB":
                return "TEXT";
            case "CHAR":
            case "CHARACTER":
            case "BPCHAR":
                return "CHAR";
            case "FLOAT":
            case "FLOAT4":
            case "REAL":
                return "FLOAT";
            case "DOUBLE":
            case "DOUBLE PRECISION":
            case "FLOAT8":
                return "DOUBLE";
            case "NUMERIC":
            case "DECIMAL":
            case "DEC":
            case "MONEY":
                return "NUMERIC";
            case "TIMESTAMP":
            case "TIMESTAMPTZ":
            case "TIMESTAMP WITH TIME ZONE":
            case "TIMESTAMP WITHOUT TIME ZONE":
            case "DATETIME":
                return "TIMESTAMP";
            case "DATE":
                return "DATE";
            case "TIME":
            case "TIMETZ":
            case "TIME WITH TIME ZONE":
            case "TIME WITHOUT TIME ZONE":
                return "TIME";
            case "BLOB":
            case "BYTEA":
            case "VARBINARY":
            case "BINARY":
            case "RAW":
                return "BINARY";
            case "JSON":
            case "JSONB":
                return "JSON";
            case "UUID":
                return "UUID";
            default:
                return base;
        }
    }

    public SchemaDiffResult compareSchemas(java.util.List<TableMetadata> sourceTables, java.util.List<TableMetadata> targetTables) {
        long start = System.currentTimeMillis();
        SchemaDiffResult result = new SchemaDiffResult();
        java.util.Map<String, TableMetadata> targetMap = new java.util.LinkedHashMap<>();
        if (targetTables != null) {
            for (TableMetadata tm : targetTables) {
                if (tm != null && tm.getTableName() != null) {
                    targetMap.put(tm.getTableName().toLowerCase(), tm);
                }
            }
        }

        if (sourceTables != null) {
            for (TableMetadata sTable : sourceTables) {
                if (sTable == null || sTable.getTableName() == null) continue;
                TableMetadata tTable = targetMap.remove(sTable.getTableName().toLowerCase());
                TableDiff diff = compareTables(sTable, tTable);
                result.addTableDiff(diff);
            }
        }

        for (TableMetadata tTable : targetMap.values()) {
            TableDiff diff = compareTables(null, tTable);
            result.addTableDiff(diff);
        }

        result.setDurationMs(System.currentTimeMillis() - start);
        return result;
    }
}
