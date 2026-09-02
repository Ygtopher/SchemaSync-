package com.dbtool.core.exception;

/**
 * Base runtime exception for all SchemaSync database and tooling operations.
 */
public class SchemaSyncException extends RuntimeException {
    private final String errorCode;

    public SchemaSyncException(String message) {
        super(message);
        this.errorCode = "GENERIC_ERROR";
    }

    public SchemaSyncException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "GENERIC_ERROR";
    }

    public SchemaSyncException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
