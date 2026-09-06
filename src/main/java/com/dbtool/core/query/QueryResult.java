package com.dbtool.core.query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class QueryResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<String> columnNames = new ArrayList<>();
    private List<RowData> rows = new ArrayList<>();
    private long executionTimeMs;
    private int affectedRows;

    public QueryResult() {}

    public List<String> getColumnNames() { return columnNames; }
    public void setColumnNames(List<String> columnNames) { this.columnNames = columnNames; }

    public List<RowData> getRows() { return rows; }
    public void setRows(List<RowData> rows) { this.rows = rows; }
    public void addRow(RowData row) { this.rows.add(row); }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public int getAffectedRows() { return affectedRows; }
    public void setAffectedRows(int affectedRows) { this.affectedRows = affectedRows; }
}
