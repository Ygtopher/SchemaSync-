package com.dbtool.core.dialect;

public class AccessDialect implements DatabaseDialect {
    @Override
    public DialectType getDialectType() { return DialectType.ACCESS; }

    @Override
    public String quoteIdentifier(String identifier) {
        return "[" + identifier + "]";
    }

    @Override
    public String buildPagingQuery(String sql, int offset, int limit) {
        // Access dialect uses simulated paging or TOP limit
        return sql;
    }

    @Override
    public boolean supportsFeature(SqlFeature feature) {
        return feature == SqlFeature.TOP_PAGING;
    }

    @Override
    public String getTableListQuery(String schemaName) {
        return "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='TABLE'";
    }

    @Override
    public String getColumnMetadataQuery(String tableName) {
        return "SELECT * FROM " + quoteIdentifier(tableName) + " WHERE 1=0";
    }

    @Override
    public String formatValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Boolean) return ((Boolean) value) ? "TRUE" : "FALSE";
        if (value instanceof Number) return value.toString();
        return "'" + value.toString().replace("'", "''") + "'";
    }
}
