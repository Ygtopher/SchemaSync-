package com.dbtool.core.query;

import com.dbtool.util.StringUtil;

public final class SqlSanitizer {
    private SqlSanitizer() {}

    public static String sanitize(String sql) {
        if (sql == null) return "";
        String cleaned = sql.trim();
        // Remove trailing semicolons
        cleaned = StringUtil.stripTrailingSemicolon(cleaned);
        return cleaned;
    }

    public static boolean isSelectQuery(String sql) {
        if (sql == null) return false;
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("SELECT") || trimmed.startsWith("WITH") || trimmed.startsWith("SHOW") || trimmed.startsWith("EXPLAIN");
    }
}
