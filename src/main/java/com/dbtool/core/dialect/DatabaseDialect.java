package com.dbtool.core.dialect;

public interface DatabaseDialect {
    DialectType getDialectType();
    String quoteIdentifier(String identifier);
    String buildPagingQuery(String sql, int offset, int limit);
    boolean supportsFeature(SqlFeature feature);
    String getTableListQuery(String schemaName);
    String getColumnMetadataQuery(String tableName);
    String formatValue(Object value);
    default String buildCountQuery(String sql) {
        return "SELECT COUNT(*) FROM (" + sql + ") AS total_count_subquery";
    }
}
