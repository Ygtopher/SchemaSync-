package com.dbtool.util;

import com.dbtool.DatabaseManager;
import com.dbtool.panels.DashboardPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AppUsageTrackerTest {

    @TempDir
    Path tempDir;

    private File testStatsFile;

    @BeforeEach
    void setUp() {
        testStatsFile = tempDir.resolve("test_app_usage_stats.json").toFile();
        AppUsageTracker.setFileForTesting(testStatsFile);
        AppUsageTracker.resetStats();
    }

    @AfterEach
    void tearDown() {
        AppUsageTracker.setFileForTesting(null);
    }

    @Test
    void testSessionQueryCategorizationPercentilesAndHotTables() {
        AppUsageTracker.recordSessionStart();
        AppUsageTracker.UsageStatsData stats = AppUsageTracker.getStats();
        assertEquals(2L, stats.totalSessions);

        // Internal ping should be ignored
        QueryLogger.log("SELECT 1", 2, true);
        assertEquals(0L, AppUsageTracker.getStats().totalQueriesExecuted);

        // Execute various queries with varying latencies and table references
        QueryLogger.log("SELECT e.name, d.dept_name FROM employees e JOIN departments d ON e.dept_id = d.id", 20, true);
        QueryLogger.log("-- comment\nINSERT INTO departments VALUES (9, 'Ops', 'LA', 0)", 30, true);
        QueryLogger.log("UPDATE employees SET salary = 90000 WHERE id = 1", 550, true); // slow (>=500ms)
        QueryLogger.log("CREATE INDEX idx_emp_dept ON employees(dept)", 40, true);
        QueryLogger.log("EXPLAIN SELECT * FROM orders", 10, true);
        QueryLogger.log("SELECT * FROM audit_log", 1200, false); // very slow (>=1000ms) & failed

        stats = AppUsageTracker.getStats();
        assertEquals(6L, stats.totalQueriesExecuted);
        assertEquals(5L, stats.successfulQueries);
        assertEquals(1L, stats.failedQueries);
        assertEquals(2L, stats.selectQueries);
        assertEquals(2L, stats.dmlQueries);
        assertEquals(1L, stats.ddlQueries);
        assertEquals(1L, stats.explainQueries);

        // Slow queries & percentiles
        assertEquals(2L, stats.slowQueriesCount);
        assertEquals(1L, stats.verySlowQueriesCount);
        assertEquals(1200L, stats.maxQueryDurationMs);
        assertTrue(stats.slowestQuerySql.contains("audit_log"));
        assertTrue(AppUsageTracker.getP50LatencyMs() >= 20L);
        assertTrue(AppUsageTracker.getP95LatencyMs() >= 550L);

        // Hot tables extracted automatically
        assertEquals(3L, stats.queriedTableCounts.get("employees"));
        assertEquals(2L, stats.queriedTableCounts.get("departments"));
        assertTrue(AppUsageTracker.getTopEntry(stats.queriedTableCounts, "-").startsWith("employees (3)"));

        // Daily activity & streak
        assertEquals(6L, AppUsageTracker.getTodayQueriesCount());
        assertEquals(6L, AppUsageTracker.getLast7DaysQueriesCount());
        assertTrue(AppUsageTracker.getActiveDaysCount() >= 1);
        assertTrue(AppUsageTracker.getCurrentDayStreak() >= 1);
    }

    @Test
    void testGovernanceDataMovementAndPersistence() {
        AppUsageTracker.recordConnection("PostgreSQL");
        AppUsageTracker.recordConnection("PostgreSQL");
        AppUsageTracker.recordConnection("H2 Sandbox");
        AppUsageTracker.recordFeatureUse("SQL Editor");
        AppUsageTracker.recordFeatureUse("SQL Editor");
        AppUsageTracker.recordFeatureUse("Visual Join Builder");
        AppUsageTracker.recordExport("CSV");

        AppUsageTracker.recordRowsFetched(250);
        AppUsageTracker.recordRowsMutated(15);
        AppUsageTracker.recordDestructiveQueryIntercepted();
        AppUsageTracker.recordLinterIssues(3);
        AppUsageTracker.recordLinterFixApplied();
        AppUsageTracker.recordPiiRowsMasked(500);
        AppUsageTracker.recordSchemaDriftAction();
        AppUsageTracker.recordRowsImported(1000);
        AppUsageTracker.recordMockRowsGenerated(200);
        AppUsageTracker.recordDualDbComparison();
        AppUsageTracker.recordOrmModelGenerated();

        AppUsageTracker.UsageStatsData stats = AppUsageTracker.getStats();
        assertEquals(3L, stats.totalConnectionsOpened);
        assertEquals(1L, stats.totalExportsPerformed);
        assertEquals(250L, stats.totalRowsFetched);
        assertEquals(1215L, stats.totalRowsMutated); // 15 + 1000 imported + 200 mock
        assertEquals(1L, stats.destructiveQueriesIntercepted);
        assertEquals(3L, stats.linterIssuesDetected);
        assertEquals(1L, stats.linterFixesApplied);
        assertEquals(500L, stats.piiRowsMasked);
        assertEquals(1L, stats.schemaSnapshotsAndMigrations);
        assertEquals(1000L, stats.rowsImported);
        assertEquals(200L, stats.mockRowsGenerated);
        assertEquals(1L, stats.dualDbComparisonsRun);
        assertEquals(1L, stats.ormModelsGenerated);

        // Reload from disk by re-pointing to same file
        AppUsageTracker.setFileForTesting(testStatsFile);
        AppUsageTracker.UsageStatsData reloaded = AppUsageTracker.getStats();
        assertEquals(3L, reloaded.totalConnectionsOpened);
        assertEquals(500L, reloaded.piiRowsMasked);
        assertEquals(1000L, reloaded.rowsImported);
        assertEquals(200L, reloaded.mockRowsGenerated);
        assertEquals(2L, reloaded.engineConnections.get("PostgreSQL"));
        assertEquals(2L, reloaded.featureUsageCounts.get("SQL Editor"));
    }

    @Test
    void testRuntimeMemoryAndDashboardRefresh() throws Exception {
        assertTrue(AppUsageTracker.getJvmUsedMemoryMb() > 0);
        assertTrue(AppUsageTracker.getJvmMaxMemoryMb() >= AppUsageTracker.getJvmTotalMemoryMb());
        assertTrue(AppUsageTracker.getJvmMemoryUsagePercent() >= 0 && AppUsageTracker.getJvmMemoryUsagePercent() <= 100);

        DatabaseManager dbm = new DatabaseManager();
        DashboardPanel panel = new DashboardPanel(dbm, null, null, null, null, null, null, null, null, null);
        assertDoesNotThrow(panel::refreshUsageStatistics);
        assertDoesNotThrow(panel::refresh);

        dbm.connectSandbox("dashboard_full_stats_test");
        assertDoesNotThrow(panel::refreshUsageStatistics);
        assertDoesNotThrow(panel::refresh);
    }
}
