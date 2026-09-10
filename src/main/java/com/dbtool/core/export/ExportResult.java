package com.dbtool.core.export;

import java.io.File;
import java.io.Serializable;

public class ExportResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final File file;
    private final int rowCount;
    private final long fileSize;
    private final long durationMs;

    public ExportResult(File file, int rowCount, long fileSize, long durationMs) {
        this.file = file;
        this.rowCount = rowCount;
        this.fileSize = fileSize;
        this.durationMs = durationMs;
    }

    public File getFile() { return file; }
    public int getRowCount() { return rowCount; }
    public long getFileSize() { return fileSize; }
    public long getDurationMs() { return durationMs; }
}
