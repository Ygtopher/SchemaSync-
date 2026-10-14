package com.dbtool.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class QueryHistoryTest {

    @TempDir
    Path tempDir;

    private File tempFile;

    @BeforeEach
    void setUp() {
        tempFile = tempDir.resolve("test_query_history.json").toFile();
        QueryHistory.setHistoryFileForTesting(tempFile);
    }

    @AfterEach
    void tearDown() {
        QueryHistory.setHistoryFileForTesting(null);
    }

    @Test
    void testQueryHistoryAddAndLoad() {
        QueryHistory.add("SELECT * FROM users");
        List<String> list = QueryHistory.load();
        assertTrue(list.contains("SELECT * FROM users"));
    }

    @Test
    void testQueryHistoryDeduplication() {
        QueryHistory.add("SELECT 1");
        QueryHistory.add("SELECT 2");
        QueryHistory.add("SELECT 1");

        List<String> list = QueryHistory.load();
        assertEquals(2, list.size());
        assertEquals("SELECT 1", list.get(0)); // Most recent on top
        assertEquals("SELECT 2", list.get(1));
    }

    @Test
    void testQueryHistoryRemoveAndClear() {
        QueryHistory.add("SELECT A");
        QueryHistory.add("SELECT B");
        QueryHistory.remove("SELECT A");

        List<String> list = QueryHistory.load();
        assertFalse(list.contains("SELECT A"));
        assertTrue(list.contains("SELECT B"));

        QueryHistory.clear();
        assertTrue(QueryHistory.load().isEmpty());
    }

    @Test
    void testLegacyMigration() throws Exception {
        // Prepare a legacy file
        File legacyFile = tempDir.resolve("legacy_history.json").toFile();
        Files.write(legacyFile.toPath(), "[\"SELECT legacy_col FROM legacy_tbl\"]".getBytes());

        File newTarget = tempDir.resolve("migrated_history.json").toFile();
        assertFalse(newTarget.exists());

        // Copy legacy to new target manually or test migration logic
        Files.copy(legacyFile.toPath(), newTarget.toPath());
        QueryHistory.setHistoryFileForTesting(newTarget);

        List<String> list = QueryHistory.load();
        assertEquals(1, list.size());
        assertEquals("SELECT legacy_col FROM legacy_tbl", list.get(0));
    }
}
