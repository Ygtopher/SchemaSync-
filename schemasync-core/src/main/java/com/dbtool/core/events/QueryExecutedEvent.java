package com.dbtool.core.events;

public record QueryExecutedEvent(String sql, long elapsedMs, int rowCount, boolean success, String errorMessage) {
    public String getSql() { return sql; }
    public long getElapsedMs() { return elapsedMs; }
    public int getRowCount() { return rowCount; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
