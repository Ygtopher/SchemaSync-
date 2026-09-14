package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SavedQueriesSearchTest {
    @Test
    void testSearch() {
        SavedQueries.save("GetUsers", "SELECT * FROM users");
        assertNotNull(SavedQueries.load().get("GetUsers"));
    }
}
