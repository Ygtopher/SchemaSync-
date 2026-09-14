package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueryHistoryLimitTest {
    @Test
    void testLimit() {
        for (int i = 0; i < 60; i++) QueryHistory.add("SELECT " + i);
        assertTrue(QueryHistory.load().size() <= 50);
    }
}
