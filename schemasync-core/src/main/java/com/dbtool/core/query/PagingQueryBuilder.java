package com.dbtool.core.query;

import com.dbtool.core.dialect.DatabaseDialect;

public class PagingQueryBuilder {
    private final DatabaseDialect dialect;

    public PagingQueryBuilder(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    public String build(String baseSql, int pageIndex, int pageSize) {
        int offset = Math.max(0, pageIndex * pageSize);
        return dialect.buildPagingQuery(baseSql, offset, pageSize);
    }
}
