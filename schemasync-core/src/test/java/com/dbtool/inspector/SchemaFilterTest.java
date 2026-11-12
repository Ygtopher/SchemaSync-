package com.dbtool.inspector;

import com.dbtool.core.inspector.SchemaFilter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaFilterTest {
    @Test
    void testFilterRegex() {
        SchemaFilter filter = new SchemaFilter("^app_.*", ".*_backup$");
        assertTrue(filter.matches("app_users"));
        assertTrue(filter.matches("app_orders"));
        assertFalse(filter.matches("app_orders_backup"));
        assertFalse(filter.matches("sys_config"));
    }
}
