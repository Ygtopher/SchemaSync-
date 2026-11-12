package com.dbtool.core.script;

public class SqlCommentStripper {
    public static String stripComments(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("(?m)^\\s*--.*$", "").replaceAll("/\\*.*?\\*/", "").trim();
    }
}
