package com.dbtool.core.dialect;

public class GenericSqlDialect implements DatabaseDialect {
    @Override
    public DialectType getDialectType() { return DialectType.GENERIC; }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String buildPagingQuery(String sql, int offset, int limit) {
        return sql + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public boolean supportsFeature(SqlFeature feature) {
        return feature == SqlFeature.OFFSET_LIMIT_PAGING;
    }

    @Override
    public String getTableListQuery(String schemaName) {
        return "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'";
    }

    @Override
    public String getColumnMetadataQuery(String tableName) {
        return "SELECT * FROM " + quoteIdentifier(tableName) + " WHERE 1=0";
    }

    @Override
    public String formatValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return "'" + value.toString().replace("'", "''") + "'";
    }
}
