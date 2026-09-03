package com.dbtool.core.model;

import java.io.Serializable;

public class QueryExecutionMetric implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sql;
    private final long executionTimeMs;
    private final int affectedRows;
    private final long timestamp;

    public QueryExecutionMetric(String sql, long executionTimeMs, int affectedRows) {
        this.sql = sql;
        this.executionTimeMs = executionTimeMs;
        this.affectedRows = affectedRows;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSql() { return sql; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public int getAffectedRows() { return affectedRows; }
    public long getTimestamp() { return timestamp; }
}
