package com.dbtool.core.diff;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DataDiffResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tableName;
    private List<String> columnNames = new ArrayList<>();
    private List<RowDiff> rowDiffs = new ArrayList<>();
    private long durationMs;
    private long totalSourceRows;
    private long totalTargetRows;
    private int chunksCompared;
    private int chunksMatchedSkipped;
    private int chunksDirtyResolved;

    public DataDiffResult(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() { return tableName; }
    public List<String> getColumnNames() { return columnNames; }
    public void setColumnNames(List<String> columnNames) { this.columnNames = columnNames; }
    public List<RowDiff> getRowDiffs() { return rowDiffs; }
    public void addRowDiff(RowDiff diff) { this.rowDiffs.add(diff); }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public long getTotalSourceRows() { return totalSourceRows; }
    public void setTotalSourceRows(long totalSourceRows) { this.totalSourceRows = totalSourceRows; }
    public long getTotalTargetRows() { return totalTargetRows; }
    public void setTotalTargetRows(long totalTargetRows) { this.totalTargetRows = totalTargetRows; }
    public int getChunksCompared() { return chunksCompared; }
    public void setChunksCompared(int chunksCompared) { this.chunksCompared = chunksCompared; }
    public int getChunksMatchedSkipped() { return chunksMatchedSkipped; }
    public void setChunksMatchedSkipped(int chunksMatchedSkipped) { this.chunksMatchedSkipped = chunksMatchedSkipped; }
    public int getChunksDirtyResolved() { return chunksDirtyResolved; }
    public void setChunksDirtyResolved(int chunksDirtyResolved) { this.chunksDirtyResolved = chunksDirtyResolved; }
}
