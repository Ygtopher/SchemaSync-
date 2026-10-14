package com.dbtool.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SavedQueriesTest {

    @TempDir
    Path tempDir;

    private File tempFile;

    @BeforeEach
    void setUp() {
        tempFile = tempDir.resolve("test_saved_queries.json").toFile();
        SavedQueries.setFileForTesting(tempFile);
    }

    @AfterEach
    void tearDown() {
        SavedQueries.setFileForTesting(null);
    }

    @Test
    void testSavedQueries() {
        SavedQueries.save("Find Active", "SELECT * FROM users WHERE active = 1");
        assertEquals("SELECT * FROM users WHERE active = 1", SavedQueries.load().get("Find Active"));
    }
}
