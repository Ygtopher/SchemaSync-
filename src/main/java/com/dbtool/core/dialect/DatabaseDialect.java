package com.dbtool.core.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface DatabaseDialect {
    DialectType getDialectType();
    String quoteIdentifier(String identifier);
    String buildPagingQuery(String sql, int offset, int limit);
    boolean supportsFeature(SqlFeature feature);
    String getTableListQuery(String schemaName);
    String getColumnMetadataQuery(String tableName);
    String formatValue(Object value);
}
