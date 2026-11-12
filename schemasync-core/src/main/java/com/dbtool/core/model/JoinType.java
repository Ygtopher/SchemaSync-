package com.dbtool.core.model;

public enum JoinType {
    INNER_JOIN("INNER JOIN"),
    LEFT_JOIN("LEFT JOIN"),
    RIGHT_JOIN("RIGHT JOIN"),
    FULL_OUTER_JOIN("FULL OUTER JOIN"),
    CROSS_JOIN("CROSS JOIN");

    private final String sqlKeyword;

    JoinType(String sqlKeyword) {
        this.sqlKeyword = sqlKeyword;
    }

    public String getSqlKeyword() {
        return sqlKeyword;
    }
}
