package com.dbtool.core.exception;

public class DatabaseConnectionException extends SchemaSyncException {
    public DatabaseConnectionException(String message) {
        super("CONN_FAILED", message, null);
    }

    public DatabaseConnectionException(String message, Throwable cause) {
        super("CONN_FAILED", message, cause);
    }

    public DatabaseConnectionException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
