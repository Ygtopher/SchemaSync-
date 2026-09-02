package com.dbtool.core.exception;

public class SchemaDiffException extends SchemaSyncException {
    public SchemaDiffException(String message) {
        super("SCHEMA_DIFF_ERROR", message, null);
    }
    public SchemaDiffException(String message, Throwable cause) {
        super("SCHEMA_DIFF_ERROR", message, cause);
    }
}
