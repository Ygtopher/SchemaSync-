package com.dbtool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerIntegrationTest {
    @Test
    void testManagerInit() {
        DatabaseManager dm = new DatabaseManager();
        assertNull(dm.connection);
    }
}
