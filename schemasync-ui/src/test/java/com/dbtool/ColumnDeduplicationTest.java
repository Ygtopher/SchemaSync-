package com.dbtool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ColumnDeduplicationTest {

    private Connection conn;
    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT, email TEXT, created_at TEXT)");
        }
        dbManager = new DatabaseManager();
        dbManager.connection = conn;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    void testGetColumnNamesHasNoDuplicates() {
        List<String> cols = dbManager.getColumnNames("users");
        assertNotNull(cols);
        assertEquals(4, cols.size());
        assertEquals("id", cols.get(0).toLowerCase());
        assertEquals("username", cols.get(1).toLowerCase());
        assertEquals("email", cols.get(2).toLowerCase());
        assertEquals("created_at", cols.get(3).toLowerCase());

        // Ensure every column in the list is strictly unique
        Set<String> uniqueSet = new LinkedHashSet<>(cols);
        assertEquals(cols.size(), uniqueSet.size(), "Columns returned by getColumnNames must not contain duplicates");
    }

    @Test
    void testColumnCacheDeduplication() {
        // Pre-populate columnCache with duplicate entries as might happen from cross-schema metadata
        List<String> rawWithDuplicates = new ArrayList<>();
        rawWithDuplicates.add("id");
        rawWithDuplicates.add("username");
        rawWithDuplicates.add("id");
        rawWithDuplicates.add("username");

        dbManager.columnCachePutTest("users_dup", rawWithDuplicates);
        List<String> result = dbManager.getColumnNames("users_dup");

        assertEquals(2, result.size());
        assertEquals("id", result.get(0));
        assertEquals("username", result.get(1));
    }

    @Test
    void testDetailedColumnsDeduplication() {
        List<DatabaseManager.ColumnDefinition> list = dbManager.getDetailedColumns("users");
        assertNotNull(list);
        assertEquals(4, list.size());
        Set<String> names = new LinkedHashSet<>();
        for (DatabaseManager.ColumnDefinition cd : list) {
            names.add(cd.name.toLowerCase());
        }
        assertEquals(list.size(), names.size(), "Detailed columns must not have duplicates");
    }
}
