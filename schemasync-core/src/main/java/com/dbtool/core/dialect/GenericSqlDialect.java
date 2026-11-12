package com.dbtool.core.dialect;

public class GenericSqlDialect implements DatabaseDialect {
    @Override
    public DialectType getDialectType() { return DialectType.GENERIC; }

    private static final java.util.Set<String> SQL_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "user", "order", "group", "select", "where", "from", "table", "update", "delete", "insert", "into", "values", "set", "limit", "offset", "all", "any", "as", "asc", "desc", "between", "case", "cast", "check", "column", "constraint", "create", "cross", "current_date", "current_time", "current_timestamp", "current_user", "default", "distinct", "drop", "else", "end", "except", "false", "for", "foreign", "full", "grant", "having", "in", "inner", "intersect", "is", "join", "left", "like", "natural", "not", "null", "on", "or", "outer", "primary", "references", "right", "table", "then", "to", "true", "union", "unique", "using", "when", "with"
    ));

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return "";
        if (identifier.matches("^[a-z_][a-z0-9_]*$") && !SQL_KEYWORDS.contains(identifier)) {
            return identifier;
        }
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
