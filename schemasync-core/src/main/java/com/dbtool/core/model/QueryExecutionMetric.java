package com.dbtool.core.model;

import java.io.Serializable;

/**
 * Immutable Java 21 record capturing query latency and affected row telemetry.
 */
public record QueryExecutionMetric(String sql, long executionTimeMs, int affectedRows, long timestamp) implements Serializable {
    private static final long serialVersionUID = 2L;

    public QueryExecutionMetric(String sql, long executionTimeMs, int affectedRows) {
        this(sql, executionTimeMs, affectedRows, System.currentTimeMillis());
    }

    public String getSql() { return sql; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public int getAffectedRows() { return affectedRows; }
    public long getTimestamp() { return timestamp; }
}
