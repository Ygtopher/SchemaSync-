package com.dbtool.core.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class IndexMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private boolean unique;
    private List<String> columns = new ArrayList<>();

    public IndexMetadata() {}

    public IndexMetadata(String name, boolean unique) {
        this.name = name;
        this.unique = unique;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isUnique() { return unique; }
    public void setUnique(boolean unique) { this.unique = unique; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public void addColumn(String col) { this.columns.add(col); }
}
