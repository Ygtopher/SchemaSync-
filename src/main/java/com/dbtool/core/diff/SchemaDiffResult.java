package com.dbtool.core.diff;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SchemaDiffResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<TableDiff> tableDiffs = new ArrayList<>();
    private long durationMs;

    public SchemaDiffResult() {}

    public List<TableDiff> getTableDiffs() { return tableDiffs; }
    public void addTableDiff(TableDiff diff) { this.tableDiffs.add(diff); }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean hasDifferences() {
        return tableDiffs.stream().anyMatch(t -> t.getType() != DifferenceType.IDENTICAL);
    }
}
