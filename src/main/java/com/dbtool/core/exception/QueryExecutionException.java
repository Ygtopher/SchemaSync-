package com.dbtool.core.exception;

public class QueryExecutionException extends SchemaSyncException {
    private final String sql;

    public QueryExecutionException(String message, String sql, Throwable cause) {
        super("QUERY_ERROR", message + " | SQL: " + sql, cause);
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }
}
