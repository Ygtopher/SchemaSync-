package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SavedQueriesTest {
    @Test
    void testSavedQueries() {
        SavedQueries sq = new SavedQueries();
        sq.saveQuery("Find Active", "SELECT * FROM users WHERE active = 1");
        assertEquals("SELECT * FROM users WHERE active = 1", sq.getQuery("Find Active"));
    }
}
