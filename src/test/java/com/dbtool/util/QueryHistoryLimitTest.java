package com.dbtool.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class QueryHistoryLimitTest {

    @TempDir
    Path tempDir;

    private File tempFile;

    @BeforeEach
    void setUp() {
        tempFile = tempDir.resolve("test_limit_history.json").toFile();
        QueryHistory.setHistoryFileForTesting(tempFile);
    }

    @AfterEach
    void tearDown() {
        QueryHistory.setHistoryFileForTesting(null);
    }

    @Test
    void testLimit() {
        for (int i = 0; i < 120; i++) QueryHistory.add("SELECT " + i);
        assertTrue(QueryHistory.load().size() <= QueryHistory.MAX);
        assertEquals(QueryHistory.MAX, QueryHistory.load().size());
    }
}
