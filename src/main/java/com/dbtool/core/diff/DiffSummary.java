package com.dbtool.core.diff;

public class DiffSummary {
    private int addedTables;
    private int removedTables;
    private int modifiedTables;
    private int addedColumns;
    private int removedColumns;
    private int modifiedColumns;

    public void incrementAddedTables() { addedTables++; }
    public void incrementRemovedTables() { removedTables++; }
    public void incrementModifiedTables() { modifiedTables++; }
    public void incrementAddedColumns() { addedColumns++; }
    public void incrementRemovedColumns() { removedColumns++; }
    public void incrementModifiedColumns() { modifiedColumns++; }

    public int getTotalDifferences() {
        return addedTables + removedTables + modifiedTables + addedColumns + removedColumns + modifiedColumns;
    }
}
