package com.dbtool.core.dialect;

public class PostgreSqlDialect extends GenericSqlDialect {
    @Override
    public DialectType getDialectType() { return DialectType.POSTGRESQL; }

    @Override
    public boolean supportsFeature(SqlFeature feature) {
        return true;
    }

    @Override
    public String getTableListQuery(String schemaName) {
        String schema = (schemaName != null && !schemaName.isEmpty()) ? schemaName : "public";
        return "SELECT table_name FROM information_schema.tables WHERE table_schema = '" + schema + "' AND table_type = 'BASE TABLE' ORDER BY table_name";
    }

    public String buildSetSearchPathQuery(String schema) {
        return "SET search_path TO " + quoteIdentifier(schema) + ", public;";
    }

    public boolean isDollarQuoting(String text, int index) {
        return text != null && index >= 0 && index < text.length() && text.charAt(index) == '$';
    }
}
