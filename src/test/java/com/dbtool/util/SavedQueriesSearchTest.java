package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SavedQueriesSearchTest {
    @Test
    void testSearch() {
        SavedQueries sq = new SavedQueries();
        sq.saveQuery("GetUsers", "SELECT * FROM users");
        assertNotNull(sq.getQuery("GetUsers"));
    }
}
