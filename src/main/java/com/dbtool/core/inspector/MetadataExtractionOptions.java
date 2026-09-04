package com.dbtool.core.inspector;

public class MetadataExtractionOptions {
    private boolean loadIndices = true;
    private boolean loadForeignKeys = true;
    private boolean loadPrimaryKeys = true;

    public boolean isLoadIndices() { return loadIndices; }
    public void setLoadIndices(boolean loadIndices) { this.loadIndices = loadIndices; }

    public boolean isLoadForeignKeys() { return loadForeignKeys; }
    public void setLoadForeignKeys(boolean loadForeignKeys) { this.loadForeignKeys = loadForeignKeys; }

    public boolean isLoadPrimaryKeys() { return loadPrimaryKeys; }
    public void setLoadPrimaryKeys(boolean loadPrimaryKeys) { this.loadPrimaryKeys = loadPrimaryKeys; }
}
