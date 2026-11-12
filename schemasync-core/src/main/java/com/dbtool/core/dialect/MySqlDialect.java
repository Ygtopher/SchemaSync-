package com.dbtool.core.dialect;

import java.util.Arrays;
import java.util.List;

public class MySqlDialect extends GenericSqlDialect {

    @Override
    public DialectType getDialectType() {
        return DialectType.MYSQL;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return "";
        if (identifier.matches("^[a-z_][a-z0-9_]*$") && !super.quoteIdentifier(identifier).startsWith("\"")) {
            return identifier;
        }
        return "`" + identifier.replace("`", "``") + "`";
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
        if (schemaName != null && !schemaName.trim().isEmpty()) {
            return "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema = '"
                    + schemaName.replace("'", "''")
                    + "' AND table_type = 'BASE TABLE' ORDER BY table_name";
        }
        return "SHOW TABLES";
    }

    @Override
    public List<String> getJdbcUrlPrefixes() {
        return Arrays.asList("jdbc:mysql:", "jdbc:mariadb:");
    }
}
