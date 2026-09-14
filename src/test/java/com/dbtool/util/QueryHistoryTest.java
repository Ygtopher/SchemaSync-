package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueryHistoryTest {
    @Test
    void testQueryHistory() {
        QueryHistory.add("SELECT * FROM users");
        assertTrue(QueryHistory.load().contains("SELECT * FROM users"));
    }
}
