package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SqlEditorDraftTest {

    @TempDir
    Path tempDir;

    private File tempDraftFile;

    @BeforeEach
    void setUp() {
        tempDraftFile = tempDir.resolve("test_sql_editor_draft.sql").toFile();
        SqlEditorPanel.setDraftFileForTesting(tempDraftFile);
    }

    @AfterEach
    void tearDown() {
        SqlEditorPanel.setDraftFileForTesting(null);
    }

    @Test
    void testDefaultTemplateWhenNoDraftExists() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SqlEditorPanel panel = new SqlEditorPanel(new DatabaseManager());
            assertEquals("-- Write your SQL here\nSELECT 1;", panel.getEditorText().trim());
        });
    }

    @Test
    void testSaveAndRestoreDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SqlEditorPanel panel = new SqlEditorPanel(new DatabaseManager());
            String customSql = "SELECT id, full_name, salary FROM employees WHERE status = 'ACTIVE';";
            panel.setEditorText(customSql);
            panel.saveEditorDraft();
        });

        assertTrue(tempDraftFile.exists());
        String savedOnDisk = new String(Files.readAllBytes(tempDraftFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(savedOnDisk.contains("SELECT id, full_name, salary FROM employees"));

        // Creating a new panel should automatically restore this draft
        SwingUtilities.invokeAndWait(() -> {
            SqlEditorPanel restoredPanel = new SqlEditorPanel(new DatabaseManager());
            assertEquals("SELECT id, full_name, salary FROM employees WHERE status = 'ACTIVE';", restoredPanel.getEditorText());
        });
    }

    @Test
    void testEmptyDraftFallsBackToDefault() throws Exception {
        Files.write(tempDraftFile.toPath(), "   \n\t  ".getBytes(StandardCharsets.UTF_8));
        SwingUtilities.invokeAndWait(() -> {
            SqlEditorPanel panel = new SqlEditorPanel(new DatabaseManager());
            assertEquals("-- Write your SQL here\nSELECT 1;", panel.getEditorText().trim());
        });
    }
}
