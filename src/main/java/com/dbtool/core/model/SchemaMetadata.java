package com.dbtool.core.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class SchemaMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String schemaName;
    private Map<String, TableMetadata> tables = new HashMap<>();

    public SchemaMetadata() {}
    public SchemaMetadata(String schemaName) { this.schemaName = schemaName; }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public Map<String, TableMetadata> getTables() { return tables; }
    public void setTables(Map<String, TableMetadata> tables) { this.tables = tables; }

    public void addTable(TableMetadata table) {
        if (table != null && table.getTableName() != null) {
            tables.put(table.getTableName().toUpperCase(), table);
        }
    }

    public TableMetadata getTable(String tableName) {
        return tableName != null ? tables.get(tableName.toUpperCase()) : null;
    }
}
