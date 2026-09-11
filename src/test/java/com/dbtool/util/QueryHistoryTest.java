package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueryHistoryTest {
    @Test
    void testQueryHistory() {
        QueryHistory qh = new QueryHistory();
        qh.addQuery("SELECT * FROM users");
        assertTrue(qh.getHistory().contains("SELECT * FROM users"));
    }
}
