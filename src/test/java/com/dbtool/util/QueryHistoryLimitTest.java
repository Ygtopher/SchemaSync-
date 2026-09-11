package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueryHistoryLimitTest {
    @Test
    void testLimit() {
        QueryHistory qh = new QueryHistory();
        for (int i = 0; i < 60; i++) qh.addQuery("SELECT " + i);
        assertTrue(qh.getHistory().size() <= 50);
    }
}
