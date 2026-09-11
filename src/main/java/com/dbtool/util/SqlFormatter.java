package com.dbtool.util;

public final class SqlFormatter {
    private SqlFormatter() {}

    public static String format(String sql) {
        if (sql == null) return "";
        return sql.trim()
                .replaceAll("(?i)\\bSELECT\\b", "SELECT")
                .replaceAll("(?i)\\bFROM\\b", "\nFROM")
                .replaceAll("(?i)\\bWHERE\\b", "\nWHERE")
                .replaceAll("(?i)\\bORDER BY\\b", "\nORDER BY");
    }
}
