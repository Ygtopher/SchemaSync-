package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SavedQueriesTest {
    @Test
    void testSavedQueries() {
        SavedQueries.save("Find Active", "SELECT * FROM users WHERE active = 1");
        assertEquals("SELECT * FROM users WHERE active = 1", SavedQueries.load().get("Find Active"));
    }
}
