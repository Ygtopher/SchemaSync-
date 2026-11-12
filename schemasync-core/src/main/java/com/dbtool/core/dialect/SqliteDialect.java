package com.dbtool.core.dialect;

import java.util.Collections;
import java.util.List;

public class SqliteDialect extends GenericSqlDialect {

    @Override
    public DialectType getDialectType() {
        return DialectType.SQLITE;
    }

    @Override
    public boolean supportsFeature(SqlFeature feature) {
        return feature == SqlFeature.OFFSET_LIMIT_PAGING
                || feature == SqlFeature.COMMON_TABLE_EXPRESSIONS
                || feature == SqlFeature.WINDOW_FUNCTIONS
                || feature == SqlFeature.TRANSACTION_SAVEPOINTS;
    }

    @Override
    public String getTableListQuery(String schemaName) {
        return "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name";
    }

    @Override
    public List<String> getJdbcUrlPrefixes() {
        return Collections.singletonList("jdbc:sqlite:");
    }
}
