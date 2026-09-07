package com.dbtool.core.diff;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TableDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tableName;
    private DifferenceType type;
    private List<ColumnDiff> columnDiffs = new ArrayList<>();

    public TableDiff(String tableName, DifferenceType type) {
        this.tableName = tableName;
        this.type = type;
    }

    public String getTableName() { return tableName; }
    public DifferenceType getType() { return type; }
    public List<ColumnDiff> getColumnDiffs() { return columnDiffs; }
    public void addColumnDiff(ColumnDiff diff) { this.columnDiffs.add(diff); }
}
