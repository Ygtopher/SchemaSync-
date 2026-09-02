package com.dbtool.core.exception;

public class ExportException extends SchemaSyncException {
    public ExportException(String message) {
        super("EXPORT_FAILED", message, null);
    }
    public ExportException(String message, Throwable cause) {
        super("EXPORT_FAILED", message, cause);
    }
}
