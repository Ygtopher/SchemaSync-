package com.dbtool;

import com.dbtool.model.SessionSnapshot;
import com.dbtool.model.VisualJoinState;
import com.dbtool.util.SessionRecoveryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionRecoveryTest {

    @TempDir
    Path tempRecoveryDir;

    @BeforeEach
    public void setUp() {
        SessionRecoveryManager.setRecoveryDirForTesting(tempRecoveryDir.toFile());
    }

    @AfterEach
    public void tearDown() {
        SessionRecoveryManager.stopAutoSaveDaemon();
        SessionRecoveryManager.setRecoveryDirForTesting(null);
    }

    @Test
    public void testSnapshotSerializationAndDeserialization() throws Exception {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.cleanExit = false;
        snapshot.activeTabTitle = "Visual Join Builder";
        snapshot.activeTabIndex = 2;
        snapshot.sqlEditor.queryText = "SELECT e.name, d.dept_name FROM employees e JOIN departments d ON e.dept_id = d.id;";
        snapshot.tableCompare.queryA = "SELECT * FROM employees ORDER BY id;";
        snapshot.tableCompare.queryB = "SELECT * FROM backup_employees ORDER BY id;";
        snapshot.tableCompare.keyCol = "id";

        // Connection Info
        snapshot.connection.type = "POSTGRES";
        snapshot.connection.host = "127.0.0.1";
        snapshot.connection.port = "5432";
        snapshot.connection.dbName = "corp_db";
        snapshot.connection.user = "postgres";

        // Browse Tab
        SessionSnapshot.BrowseState bTab = new SessionSnapshot.BrowseState();
        bTab.tableName = "employees";
        bTab.distinct = true;
        bTab.limit = "100";
        bTab.selectedColumns = List.of("id", "name", "salary", "dept_id");

        VisualJoinState.WhereState cond = new VisualJoinState.WhereState();
        cond.table = "employees";
        cond.column = "dept_id";
        cond.operator = "IN";
        cond.value = "";
        cond.inType = "Subquery (SELECT)";
        cond.subTable = "departments";
        cond.subCol = "id";
        cond.subDistinct = true;
        cond.subOrderByCol = "id";
        cond.subOrderDir = "ASC";
        cond.subLimit = "50";
        bTab.wheres.add(cond);

        VisualJoinState.OrderByState ord = new VisualJoinState.OrderByState();
        ord.table = "employees";
        ord.column = "salary";
        ord.direction = "DESC";
        bTab.orderBys.add(ord);
        snapshot.browseTabs.add(bTab);

        // Visual Join Tab
        VisualJoinState vState = new VisualJoinState();
        vState.baseTable = "employees";
        vState.distinct = false;
        vState.limit = "50";
        vState.offset = "10";

        VisualJoinState.JoinState join = new VisualJoinState.JoinState();
        join.type = "INNER JOIN";
        join.table = "departments";
        join.leftTable = "employees";
        join.leftCol = "dept_id";
        join.rightCol = "id";
        vState.joins.add(join);
        vState.selectedColumns.add("employees.id");
        vState.selectedColumns.add("employees.name");
        vState.selectedColumns.add("departments.dept_name");

        VisualJoinState.WhereState jCond = new VisualJoinState.WhereState();
        jCond.table = "employees";
        jCond.column = "salary";
        jCond.operator = ">";
        jCond.value = "60000";
        vState.wheres.add(jCond);
        snapshot.joinTabs.add(vState);

        // Save snapshot
        boolean saved = SessionRecoveryManager.saveSessionSnapshot(snapshot);
        assertTrue(saved, "Session snapshot should be saved successfully");

        // Load snapshot
        SessionSnapshot loaded = SessionRecoveryManager.loadAutosavedSession();
        assertNotNull(loaded, "Loaded snapshot should not be null");

        assertEquals(snapshot.activeTabTitle, loaded.activeTabTitle);
        assertEquals(snapshot.activeTabIndex, loaded.activeTabIndex);
        assertEquals(snapshot.sqlEditor.queryText, loaded.sqlEditor.queryText);
        assertEquals(snapshot.tableCompare.queryA, loaded.tableCompare.queryA);
        assertEquals(snapshot.tableCompare.queryB, loaded.tableCompare.queryB);
        assertEquals(snapshot.tableCompare.keyCol, loaded.tableCompare.keyCol);

        assertEquals("corp_db", loaded.connection.dbName);
        assertEquals("postgres", loaded.connection.user);
        assertEquals("5432", loaded.connection.port);
        assertEquals("POSTGRES", loaded.connection.type);

        assertEquals(1, loaded.browseTabs.size());
        SessionSnapshot.BrowseState loadedBTab = loaded.browseTabs.get(0);
        assertEquals("employees", loadedBTab.tableName);
        assertTrue(loadedBTab.distinct);
        assertEquals("100", loadedBTab.limit);
        assertEquals(1, loadedBTab.wheres.size());
        assertEquals("Subquery (SELECT)", loadedBTab.wheres.get(0).inType);
        assertEquals("departments", loadedBTab.wheres.get(0).subTable);
        assertEquals("id", loadedBTab.wheres.get(0).subCol);
        assertTrue(loadedBTab.wheres.get(0).subDistinct);

        assertEquals(1, loaded.joinTabs.size());
        VisualJoinState loadedVState = loaded.joinTabs.get(0);
        assertEquals("employees", loadedVState.baseTable);
        assertEquals(1, loadedVState.joins.size());
        assertEquals("INNER JOIN", loadedVState.joins.get(0).type);
        assertEquals(3, loadedVState.selectedColumns.size());
    }

    @Test
    public void testRecoveryManagerLockAndCrashDetection() {
        assertFalse(SessionRecoveryManager.hasCrashedSession(), "Should not detect crash when directory is empty");

        // Simulate crash: session lock created and unclosed snapshot written
        SessionRecoveryManager.initializeSessionLock();

        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.cleanExit = false;
        snapshot.sqlEditor.queryText = "SELECT * FROM orders;";
        SessionRecoveryManager.saveSessionSnapshot(snapshot);

        assertTrue(SessionRecoveryManager.hasCrashedSession(), "Should detect crashed session when lock file exists and cleanExit is false");

        // Simulate clean exit
        SessionRecoveryManager.markCleanExit();
        assertFalse(SessionRecoveryManager.hasCrashedSession(), "Should not detect crashed session after markCleanExit()");
    }

    @Test
    public void testRecoveryManagerDiscardCrashedSession() {
        SessionRecoveryManager.initializeSessionLock();
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.cleanExit = false;
        snapshot.sqlEditor.queryText = "SELECT * FROM temp_table;";
        SessionRecoveryManager.saveSessionSnapshot(snapshot);

        assertTrue(SessionRecoveryManager.hasCrashedSession(), "Should detect crashed session");

        // Discard
        SessionRecoveryManager.discardCrashedSession();
        assertFalse(SessionRecoveryManager.hasCrashedSession(), "Should no longer detect crashed session after discard");

        // Check archive
        File archiveDir = new File(tempRecoveryDir.toFile(), "archive");
        assertTrue(archiveDir.exists(), "Archive directory should exist");
        File[] archivedFiles = archiveDir.listFiles();
        assertNotNull(archivedFiles);
        assertTrue(archivedFiles.length > 0, "Archived discarded session file should exist");
        assertTrue(archivedFiles[0].getName().startsWith("discarded_"));
    }
}
