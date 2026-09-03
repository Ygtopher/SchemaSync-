package com.dbtool.dialect;

import com.dbtool.core.dialect.PostgreSqlDialect;
import com.dbtool.core.dialect.SqlFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PostgreSqlDialectTest {
    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    @Test
    void testQuoting() {
        assertEquals("\"users\"", dialect.quoteIdentifier("users"));
        assertEquals("\"order\"\"item\"", dialect.quoteIdentifier("order\"item"));
    }

    @Test
    void testFeatures() {
        assertTrue(dialect.supportsFeature(SqlFeature.OFFSET_LIMIT_PAGING));
        assertTrue(dialect.supportsFeature(SqlFeature.RETURNING_CLAUSE));
        assertTrue(dialect.supportsFeature(SqlFeature.DOLLAR_QUOTES));
    }
}
