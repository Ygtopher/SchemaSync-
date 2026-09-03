package com.dbtool.core.dialect;

public class PostgreSqlDialect extends GenericSqlDialect {
    @Override
    public DialectType getDialectType() { return DialectType.POSTGRESQL; }

    @Override
    public boolean supportsFeature(SqlFeature feature) {
        switch (feature) {
            case OFFSET_LIMIT_PAGING:
            case RETURNING_CLAUSE:
            case DROP_CASCADE:
            case COMMON_TABLE_EXPRESSIONS:
            case WINDOW_FUNCTIONS:
            case DOLLAR_QUOTES:
            case TRANSACTION_SAVEPOINTS:
                return true;
            default:
                return false;
        }
    }

    @Override
    public String getTableListQuery(String schemaName) {
        String schema = (schemaName != null && !schemaName.isEmpty()) ? schemaName : "public";
        return "SELECT table_name FROM information_schema.tables WHERE table_schema = '" + schema + "' AND table_type = 'BASE TABLE' ORDER BY table_name";
    }
}
