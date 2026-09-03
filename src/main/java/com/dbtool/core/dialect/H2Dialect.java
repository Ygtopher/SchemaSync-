package com.dbtool.core.dialect;

public class H2Dialect extends GenericSqlDialect {
    @Override
    public DialectType getDialectType() { return DialectType.H2; }

    @Override
    public boolean supportsFeature(SqlFeature feature) {
        return feature != SqlFeature.DOLLAR_QUOTES;
    }

    @Override
    public String getTableListQuery(String schemaName) {
        String schema = (schemaName != null && !schemaName.isEmpty()) ? schemaName : "PUBLIC";
        return "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '" + schema.toUpperCase() + "' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
    }
}
