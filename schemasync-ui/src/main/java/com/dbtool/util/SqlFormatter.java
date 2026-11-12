package com.dbtool.util;

public final class SqlFormatter {
    private SqlFormatter() {}

    public static String format(String sql) {
        if (sql == null) return "";
        String normalized = sql.trim().replaceAll("[ \\t]+", " ");
        return normalized
                .replaceAll("(?i)\\bSELECT\\b", "SELECT")
                .replaceAll("(?i)\\s*\\bFROM\\b", "\nFROM")
                .replaceAll("(?i)\\s*\\b(LEFT\\s+(?:OUTER\\s+)?JOIN|RIGHT\\s+(?:OUTER\\s+)?JOIN|FULL\\s+(?:OUTER\\s+)?JOIN|INNER\\s+JOIN|CROSS\\s+JOIN|JOIN)\\b", "\n$1")
                .replaceAll("(?i)\\n(left\\s+outer\\s+join)\\b", "\nLEFT OUTER JOIN")
                .replaceAll("(?i)\\n(left\\s+join)\\b", "\nLEFT JOIN")
                .replaceAll("(?i)\\n(right\\s+outer\\s+join)\\b", "\nRIGHT OUTER JOIN")
                .replaceAll("(?i)\\n(right\\s+join)\\b", "\nRIGHT JOIN")
                .replaceAll("(?i)\\n(full\\s+outer\\s+join)\\b", "\nFULL OUTER JOIN")
                .replaceAll("(?i)\\n(full\\s+join)\\b", "\nFULL JOIN")
                .replaceAll("(?i)\\n(inner\\s+join)\\b", "\nINNER JOIN")
                .replaceAll("(?i)\\n(cross\\s+join)\\b", "\nCROSS JOIN")
                .replaceAll("(?i)\\n(join)\\b", "\nJOIN")
                .replaceAll("(?i)\\s*\\bWHERE\\b", "\nWHERE")
                .replaceAll("(?i)\\s*\\bGROUP\\s+BY\\b", "\nGROUP BY")
                .replaceAll("(?i)\\s*\\bHAVING\\b", "\nHAVING")
                .replaceAll("(?i)\\s*\\bORDER\\s+BY\\b", "\nORDER BY")
                .replaceAll("(?i)\\s*\\bLIMIT\\b", "\nLIMIT")
                .replaceAll("(?i)\\s*\\bUNION(\\s+ALL)?\\b", "\nUNION$1")
                .replaceAll("(?i)\\bINSERT\\s+INTO\\b", "INSERT INTO")
                .replaceAll("(?i)\\s*\\bVALUES\\b", "\nVALUES")
                .replaceAll("(?i)\\bUPDATE\\b", "UPDATE")
                .replaceAll("(?i)\\s*\\bSET\\b", "\nSET")
                .replaceAll("(?i)\\bDELETE\\s+FROM\\b", "DELETE FROM")
                .replaceAll("\\n+", "\n")
                .trim();
    }
}
