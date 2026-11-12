package com.dbtool.dialect;

import com.dbtool.core.dialect.H2Dialect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class H2DialectTest {
    private final H2Dialect dialect = new H2Dialect();

    @Test
    void testTableListQuery() {
        String sql = dialect.getTableListQuery("PUBLIC");
        assertTrue(sql.contains("INFORMATION_SCHEMA.TABLES"));
        assertTrue(sql.contains("PUBLIC"));
    }
}
