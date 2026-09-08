package com.dbtool.core.diff;

public class ChunkedDataComparator {
    private final DiffChunkConfig config;

    public ChunkedDataComparator(DiffChunkConfig config) {
        this.config = config != null ? config : new DiffChunkConfig();
    }

    public DiffChunkConfig getConfig() { return config; }
}
