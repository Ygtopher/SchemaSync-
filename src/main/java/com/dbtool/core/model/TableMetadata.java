package com.dbtool.core.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TableMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String schemaName;
    private String tableName;
    private List<ColumnMetadata> columns = new ArrayList<>();

    public TableMetadata() {}

    public TableMetadata(String schemaName, String tableName) {
        this.schemaName = schemaName;
        this.tableName = tableName;
    }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public List<ColumnMetadata> getColumns() { return columns; }
    public void setColumns(List<ColumnMetadata> columns) { this.columns = columns; }
    public void addColumn(ColumnMetadata column) { this.columns.add(column); }
}
