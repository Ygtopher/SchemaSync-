package com.dbtool.core.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PrimaryKeyMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private List<String> columns = new ArrayList<>();

    public PrimaryKeyMetadata() {}

    public PrimaryKeyMetadata(String name, List<String> columns) {
        this.name = name;
        this.columns = columns != null ? columns : new ArrayList<>();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public void addColumn(String col) { this.columns.add(col); }
}
