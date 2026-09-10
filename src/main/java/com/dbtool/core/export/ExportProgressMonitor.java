package com.dbtool.core.export;

public interface ExportProgressMonitor {
    void onProgress(int rowsWritten, int totalRows);
    boolean isCancelled();
}
