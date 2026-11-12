package com.dbtool.query;

import com.dbtool.core.query.SqlSanitizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SqlSanitizerTest {
    @Test
    void testSanitize() {
        assertEquals("SELECT 1", SqlSanitizer.sanitize("  SELECT 1;  "));
        assertTrue(SqlSanitizer.isSelectQuery("SELECT * FROM users"));
        assertTrue(SqlSanitizer.isSelectQuery("WITH cte AS (...) SELECT * FROM cte"));
        assertFalse(SqlSanitizer.isSelectQuery("UPDATE users SET active=1"));
    }
}
