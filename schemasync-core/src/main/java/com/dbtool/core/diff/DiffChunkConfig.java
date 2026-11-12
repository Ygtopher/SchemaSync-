package com.dbtool.core.diff;

/**
 * Tuning configuration for 2-phase chunked/streaming data diff comparisons.
 */
public class DiffChunkConfig {
    private int chunkSize = 5000;
    private int fetchSize = 1000;
    private boolean pushDownHashEnabled = true;
    private int maxDiffRowsInMemory = 100_000;

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = Math.max(1, chunkSize); }

    public int getFetchSize() { return fetchSize; }
    public void setFetchSize(int fetchSize) { this.fetchSize = Math.max(1, fetchSize); }

    public boolean isPushDownHashEnabled() { return pushDownHashEnabled; }
    public void setPushDownHashEnabled(boolean pushDownHashEnabled) { this.pushDownHashEnabled = pushDownHashEnabled; }

    public int getMaxDiffRowsInMemory() { return maxDiffRowsInMemory; }
    public void setMaxDiffRowsInMemory(int maxDiffRowsInMemory) { this.maxDiffRowsInMemory = Math.max(1, maxDiffRowsInMemory); }
}
