package com.dbtool.core.exception;

public class ScriptExecutionException extends SchemaSyncException {
    private final int lineNumber;

    public ScriptExecutionException(String message, int lineNumber) {
        super("SCRIPT_ERROR", "Line " + lineNumber + ": " + message, null);
        this.lineNumber = lineNumber;
    }

    public ScriptExecutionException(String message, int lineNumber, Throwable cause) {
        super("SCRIPT_ERROR", "Line " + lineNumber + ": " + message, cause);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
