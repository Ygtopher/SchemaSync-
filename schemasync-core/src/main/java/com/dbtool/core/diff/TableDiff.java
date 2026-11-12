package com.dbtool.core.diff;

import com.dbtool.core.model.TableMetadata;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TableDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tableName;
    private DifferenceType type;
    private TableMetadata sourceTable;
    private TableMetadata targetTable;
    private List<ColumnDiff> columnDiffs = new ArrayList<>();

    public TableDiff(String tableName, DifferenceType type) {
        this.tableName = tableName;
        this.type = type;
    }

    public String getTableName() { return tableName; }

    public DifferenceType getType() {
        if (type == DifferenceType.IDENTICAL && !columnDiffs.isEmpty()) {
            return DifferenceType.MODIFIED;
        }
        return type;
    }

    public void setType(DifferenceType type) { this.type = type; }

    public TableMetadata getSourceTable() { return sourceTable; }
    public void setSourceTable(TableMetadata sourceTable) { this.sourceTable = sourceTable; }

    public TableMetadata getTargetTable() { return targetTable; }
    public void setTargetTable(TableMetadata targetTable) { this.targetTable = targetTable; }

    public List<ColumnDiff> getColumnDiffs() { return columnDiffs; }
    public void addColumnDiff(ColumnDiff diff) { this.columnDiffs.add(diff); }

    public int getAddedColumnsCount() {
        return (int) columnDiffs.stream().filter(c -> c.getType() == DifferenceType.ADDED).count();
    }

    public int getRemovedColumnsCount() {
        return (int) columnDiffs.stream().filter(c -> c.getType() == DifferenceType.REMOVED).count();
    }

    public int getModifiedColumnsCount() {
        return (int) columnDiffs.stream().filter(c -> c.getType() == DifferenceType.MODIFIED).count();
    }
}
