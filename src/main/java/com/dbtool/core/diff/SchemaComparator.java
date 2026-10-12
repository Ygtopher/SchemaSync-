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
                td.addColumnDiff(new ColumnDiff(col.getColumnName(), DifferenceType.ADDED, null, col, "New column in target"));
            }
            return td;
        }
        if (target == null) {
            TableDiff td = new TableDiff(tableName, DifferenceType.REMOVED);
            td.setSourceTable(source);
            for (ColumnMetadata col : source.getColumns()) {
                td.addColumnDiff(new ColumnDiff(col.getColumnName(), DifferenceType.REMOVED, col, null, "Column missing in target"));
            }
            return td;
        }

        TableDiff tableDiff = new TableDiff(tableName, DifferenceType.IDENTICAL);
        tableDiff.setSourceTable(source);
        tableDiff.setTargetTable(target);

        for (ColumnMetadata sCol : source.getColumns()) {
            ColumnMetadata tCol = target.findColumn(sCol.getColumnName());
            if (tCol == null) {
                tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.REMOVED, sCol, null, "Column missing in target"));
            } else {
                if (!sCol.getTypeName().equalsIgnoreCase(tCol.getTypeName())) {
                    tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.MODIFIED, sCol, tCol, "Type mismatch: " + sCol.getTypeName() + " vs " + tCol.getTypeName()));
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
            if (source.findColumn(tCol.getColumnName()) == null) {
                tableDiff.addColumnDiff(new ColumnDiff(tCol.getColumnName(), DifferenceType.ADDED, null, tCol, "New column in target"));
            }
        }
        return tableDiff;
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
