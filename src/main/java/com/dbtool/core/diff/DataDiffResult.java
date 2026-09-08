package com.dbtool.core.diff;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DataDiffResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tableName;
    private List<String> columnNames = new ArrayList<>();
    private List<RowDiff> rowDiffs = new ArrayList<>();
    private long durationMs;

    public DataDiffResult(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() { return tableName; }
    public List<String> getColumnNames() { return columnNames; }
    public void setColumnNames(List<String> columnNames) { this.columnNames = columnNames; }
    public List<RowDiff> getRowDiffs() { return rowDiffs; }
    public void addRowDiff(RowDiff diff) { this.rowDiffs.add(diff); }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
