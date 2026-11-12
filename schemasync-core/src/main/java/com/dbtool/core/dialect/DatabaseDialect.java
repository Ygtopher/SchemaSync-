package com.dbtool.core.dialect;

import java.util.Collections;
import java.util.List;

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

    default List<String> getJdbcUrlPrefixes() {
        return Collections.emptyList();
    }

    default String quoteTableName(String tableName) {
        if (tableName == null) return "";
        if (tableName.contains("(") || tableName.contains(")")) {
            return tableName;
        }
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            return quoteIdentifier(parts[0]) + "." + quoteIdentifier(parts[1]);
        }
        return quoteIdentifier(tableName);
    }

    default String quoteColumnName(String fullColumnName) {
        if (fullColumnName == null) return "";
        if (fullColumnName.equals("*") || fullColumnName.contains("(") || fullColumnName.contains(")")) {
            return fullColumnName;
        }
        String[] parts = fullColumnName.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append(quoteIdentifier(parts[i]));
            if (i < parts.length - 1) {
                sb.append('.');
            }
        }
        return sb.toString();
    }
}
