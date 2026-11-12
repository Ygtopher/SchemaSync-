package com.dbtool.core.script;

public interface ScriptProgressCallback {
    void onProgress(int lineNumber, String statement);
    void onMessage(String message);
    void onError(int lineNumber, Throwable t);
}
