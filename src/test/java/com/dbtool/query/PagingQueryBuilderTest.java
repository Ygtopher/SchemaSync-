package com.dbtool.query;

import com.dbtool.core.dialect.GenericSqlDialect;
import com.dbtool.core.query.PagingQueryBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PagingQueryBuilderTest {
    @Test
    void testPaging() {
        PagingQueryBuilder builder = new PagingQueryBuilder(new GenericSqlDialect());
        String sql = builder.build("SELECT * FROM orders", 2, 50);
        assertTrue(sql.contains("LIMIT 50 OFFSET 100"));
    }
}
