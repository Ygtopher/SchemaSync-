package com.dbtool.core.exception;

public class DataDiffException extends SchemaSyncException {
    public DataDiffException(String message) {
        super("DATA_DIFF_ERROR", message, null);
    }
    public DataDiffException(String message, Throwable cause) {
        super("DATA_DIFF_ERROR", message, cause);
    }
}
