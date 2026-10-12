package com.dbtool;

import com.dbtool.core.diff.*;
import com.dbtool.core.inspector.JdbcSchemaInspector;
import com.dbtool.core.model.TableMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.table.DefaultTableModel;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DualDatabaseCompareTest {

    private DatabaseManager dbManagerA;
    private DatabaseManager dbManagerB;

    @BeforeEach
    public void setUp() throws Exception {
        dbManagerA = new DatabaseManager();
        dbManagerB = new DatabaseManager();

        dbManagerA.connectSandbox("test_dual_sandbox_a");
        dbManagerB.connectSandbox("test_dual_sandbox_b");
    }

    @AfterEach
    public void tearDown() {
        if (dbManagerA != null && dbManagerA.connection != null) {
            try { dbManagerA.connection.close(); } catch (Exception ignored) {}
        }
        if (dbManagerB != null && dbManagerB.connection != null) {
            try { dbManagerB.connection.close(); } catch (Exception ignored) {}
        }
    }

    @Test
    public void testIndependentDualConnections() {
        assertTrue(dbManagerA.isConnected(), "Database A should be connected");
        assertTrue(dbManagerB.isConnected(), "Database B should be connected");
        assertNotSame(dbManagerA.connection, dbManagerB.connection, "Database connections should be distinct instances");

        List<String> tablesA = dbManagerA.getTableNames();
        List<String> tablesB = dbManagerB.getTableNames();
        assertFalse(tablesA.isEmpty(), "Database A should have tables");
        assertFalse(tablesB.isEmpty(), "Database B should have tables");
        assertTrue(tablesA.contains("employees"), "Database A should contain employees table");
        assertTrue(tablesB.contains("employees"), "Database B should contain employees table");
    }

    @Test
    public void testSchemaDiffAndMigrationScript() throws Exception {
        // Alter Database B to create structural drift
        try (Statement stmt = dbManagerB.connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS audit_log;");
            stmt.execute("ALTER TABLE employees ADD COLUMN IF NOT EXISTS bonus DECIMAL(10,2) DEFAULT 0;");
        }
        dbManagerB.clearCache();

        JdbcSchemaInspector inspector = new JdbcSchemaInspector();
        List<TableMetadata> tablesA = new ArrayList<>();
        List<TableMetadata> tablesB = new ArrayList<>();

        for (String tName : dbManagerA.getTableNames()) {
            tablesA.add(inspector.inspectTable(dbManagerA.connection, null, null, tName));
        }
        for (String tName : dbManagerB.getTableNames()) {
            tablesB.add(inspector.inspectTable(dbManagerB.connection, null, null, tName));
        }

        SchemaComparator comparator = new SchemaComparator();
        SchemaDiffResult diffResult = comparator.compareSchemas(tablesA, tablesB);

        assertNotNull(diffResult);
        assertTrue(diffResult.hasDifferences(), "Schema differences should be detected");

        // Verify audit_log is detected as missing in target (REMOVED from B perspective)
        boolean foundAuditLogDiff = false;
        boolean foundEmployeesDiff = false;

        for (TableDiff td : diffResult.getTableDiffs()) {
            if ("audit_log".equalsIgnoreCase(td.getTableName())) {
                foundAuditLogDiff = true;
                assertEquals(DifferenceType.REMOVED, td.getType(), "audit_log should be detected as missing in target");
            } else if ("employees".equalsIgnoreCase(td.getTableName())) {
                foundEmployeesDiff = true;
                assertEquals(DifferenceType.MODIFIED, td.getType(), "employees should be detected as modified");
                assertTrue(td.getAddedColumnsCount() > 0, "employees should have added column 'bonus' in target");
            }
        }

        assertTrue(foundAuditLogDiff, "Should have table diff for audit_log");
        assertTrue(foundEmployeesDiff, "Should have table diff for employees");

        // Verify Migration DDL Script Generation
        String migrationScript = DiffScriptGenerator.generateSchemaSyncScript(diffResult, "Test DB A", "Test DB B");
        assertNotNull(migrationScript);
        assertTrue(migrationScript.contains("CREATE TABLE"), "Migration script should contain CREATE TABLE for audit_log");
        assertTrue(migrationScript.contains("audit_log"), "Migration script should reference audit_log");
    }

    @Test
    public void testDataDiffRowMatching() throws Exception {
        // Modify rows in Database B
        try (Statement stmt = dbManagerB.connection.createStatement()) {
            // Update Alice's salary
            stmt.execute("UPDATE employees SET salary = 88888.00 WHERE email = 'alice@company.com';");
            // Insert a new employee into B
            stmt.execute("INSERT INTO employees (first_name, last_name, name, email, dept, department, dept_id, salary, hire_date, manager_id) " +
                         "VALUES ('Zoe', 'Taylor', 'Zoe Taylor', 'zoe@company.com', 'IT', 'IT', 1, 92000.00, '2024-02-01', NULL);");
        }

        DefaultTableModel modelA = dbManagerA.executeQuery("SELECT id, name, email, salary FROM employees ORDER BY id;");
        DefaultTableModel modelB = dbManagerB.executeQuery("SELECT id, name, email, salary FROM employees ORDER BY id;");

        assertNotNull(modelA);
        assertNotNull(modelB);

        int emailIdxA = -1;
        int salaryIdxA = -1;
        for (int i = 0; i < modelA.getColumnCount(); i++) {
            if ("email".equalsIgnoreCase(modelA.getColumnName(i))) emailIdxA = i;
            if ("salary".equalsIgnoreCase(modelA.getColumnName(i))) salaryIdxA = i;
        }

        int emailIdxB = -1;
        int salaryIdxB = -1;
        for (int i = 0; i < modelB.getColumnCount(); i++) {
            if ("email".equalsIgnoreCase(modelB.getColumnName(i))) emailIdxB = i;
            if ("salary".equalsIgnoreCase(modelB.getColumnName(i))) salaryIdxB = i;
        }

        assertTrue(emailIdxA >= 0 && salaryIdxA >= 0);
        assertTrue(emailIdxB >= 0 && salaryIdxB >= 0);

        // Check Alice's salary in A vs B
        Object aliceSalaryA = null;
        for (int r = 0; r < modelA.getRowCount(); r++) {
            if ("alice@company.com".equals(modelA.getValueAt(r, emailIdxA))) {
                aliceSalaryA = modelA.getValueAt(r, salaryIdxA);
            }
        }

        Object aliceSalaryB = null;
        for (int r = 0; r < modelB.getRowCount(); r++) {
            if ("alice@company.com".equals(modelB.getValueAt(r, emailIdxB))) {
                aliceSalaryB = modelB.getValueAt(r, salaryIdxB);
            }
        }

        assertNotNull(aliceSalaryA);
        assertNotNull(aliceSalaryB);
        assertNotEquals(aliceSalaryA.toString(), aliceSalaryB.toString(), "Alice's salary should differ between DB A and DB B");
        assertTrue(aliceSalaryB.toString().contains("88888"), "DB B should have updated salary");

        // Verify Zoe exists in B but not in A
        boolean zoeInA = false;
        for (int r = 0; r < modelA.getRowCount(); r++) {
            if ("zoe@company.com".equals(modelA.getValueAt(r, emailIdxA))) zoeInA = true;
        }

        boolean zoeInB = false;
        for (int r = 0; r < modelB.getRowCount(); r++) {
            if ("zoe@company.com".equals(modelB.getValueAt(r, emailIdxB))) zoeInB = true;
        }

        assertFalse(zoeInA, "Zoe should not exist in DB A");
        assertTrue(zoeInB, "Zoe should exist in DB B");
    }

    @Test
    public void testDataDiffWithClausesAndSearchableCombos() throws Exception {
        com.dbtool.panels.DualDatabaseComparePanel panel = new com.dbtool.panels.DualDatabaseComparePanel(dbManagerA);
        panel.getDbManagerA().useExistingConnection(dbManagerA);
        panel.getDbManagerB().useExistingConnection(dbManagerB);

        // Test Query Builder with All Clauses
        String queryA = panel.buildQueryForDatabase(panel.getDbManagerA(), "employees", "id");
        assertNotNull(queryA);
        assertTrue(queryA.contains("SELECT * FROM \"employees\"") || queryA.contains("SELECT * FROM employees"), "Base query should select from employees: " + queryA);

        // Execute queries with clauses on both databases
        String clauseSql = "SELECT DISTINCT e.id, e.name, e.salary, d.dept_name " +
                           "FROM employees e " +
                           "LEFT JOIN departments d ON e.dept_id = d.id " +
                           "WHERE e.salary > 50000 " +
                           "ORDER BY e.id ASC LIMIT 10 OFFSET 0;";

        DefaultTableModel resA = panel.getDbManagerA().executeQuery(clauseSql);
        DefaultTableModel resB = panel.getDbManagerB().executeQuery(clauseSql);

        assertNotNull(resA);
        assertNotNull(resB);
        assertTrue(resA.getRowCount() > 0, "Database A should return rows matching the clause");
        assertTrue(resB.getRowCount() > 0, "Database B should return rows matching the clause");
        assertEquals(resA.getColumnCount(), resB.getColumnCount(), "Both queries should have matching column counts");
    }

    @Test
    public void testMultiDatabaseAndMultiTabComparison() throws Exception {
        com.dbtool.panels.DualDatabaseComparePanel panel = new com.dbtool.panels.DualDatabaseComparePanel(dbManagerA);
        panel.getDbManagerA().useExistingConnection(dbManagerA);
        panel.getDbManagerB().useExistingConnection(dbManagerB);

        // 1. Test Dynamic 3+ Databases (Adding Database C)
        assertEquals(2, panel.getDatabaseEntries().size(), "Initially should have 2 database entries (A and B)");
        com.dbtool.panels.DualDatabaseComparePanel.DatabaseEntry entryC = panel.addDatabaseEntry();
        assertNotNull(entryC, "Database C entry should be created");
        assertEquals("Database C", entryC.getLabel());
        assertEquals(3, panel.getDatabaseEntries().size(), "Should now have 3 database entries");

        // Connect sandbox to DB C
        DatabaseManager dbManagerC = entryC.getDbManager();
        dbManagerC.connectSandbox("test_dual_sandbox_c");
        assertTrue(dbManagerC.isConnected(), "Database C should be connected to sandbox");
        assertTrue(dbManagerC.getTableNames().contains("employees"), "Database C should have employees table");

        // 2. Test Multi-Tab Data Comparison
        assertEquals(1, panel.getDataCompareTabCount(), "Initially should have 1 data compare tab");
        com.dbtool.panels.DataCompareTabPanel tab1 = panel.getDataCompareTabs().get(0);
        assertNotNull(tab1);

        // Set Tab 1 to compare employees
        tab1.getComboTableA().setSelectedItem("employees");
        tab1.getComboTableB().setSelectedItem("employees");
        String queryTab1 = tab1.buildQueryForDatabase(panel.getDbManagerA(), "employees", "id");
        assertTrue(queryTab1.contains("employees"), "Tab 1 query should target employees");

        // Open a 2nd comparison tab for departments
        com.dbtool.panels.DataCompareTabPanel tab2 = panel.addDataCompareTab("Departments Compare");
        assertEquals(2, panel.getDataCompareTabCount(), "Should now have 2 compare tabs");

        // Configure Tab 2 to compare Source A with Target C (cross-database compare!)
        tab2.getComboDbA().setSelectedItem(panel.getDatabaseEntries().get(0)); // DB A
        tab2.getComboDbB().setSelectedItem(entryC); // DB C
        assertEquals(entryC, tab2.getSelectedDatabaseB(), "Tab 2 target should be Database C");

        tab2.getComboTableA().setSelectedItem("departments");
        tab2.getComboTableB().setSelectedItem("departments");
        String queryTab2 = tab2.buildQueryForDatabase(dbManagerC, "departments", "id");
        assertTrue(queryTab2.contains("departments"), "Tab 2 query should target departments");

        // Ensure Tab 1 and Tab 2 remain isolated and maintain their own state
        assertEquals("employees", tab1.getComboTableA().getSelectedItem(), "Tab 1 should still have employees selected");
        assertEquals("departments", tab2.getComboTableA().getSelectedItem(), "Tab 2 should have departments selected");
        assertNotEquals(tab1.getComboTableA().getSelectedItem(), tab2.getComboTableA().getSelectedItem(),
                "Tab 1 and Tab 2 should maintain isolated table selections");

        // Clean up DB C connection
        if (dbManagerC.connection != null) {
            try { dbManagerC.connection.close(); } catch (Exception ignored) {}
        }
    }

    @Test
    public void testDarkModeAccessibleButtonColors() {
        // Verify ThemeManager provides high-contrast accessible colors
        java.awt.Color darkPrimary = com.dbtool.util.ThemeManager.getPrimaryActionColor();
        assertNotNull(darkPrimary);
        // Luminance should be high in dark mode for contrast against dark background (#3c3f41)
        double luma = (0.299 * darkPrimary.getRed() + 0.587 * darkPrimary.getGreen() + 0.114 * darkPrimary.getBlue());
        assertTrue(luma > 140, "Dark mode primary action button color should have high luminance for visibility: " + luma);

        java.awt.Color darkSuccess = com.dbtool.util.ThemeManager.getSuccessColor();
        assertNotNull(darkSuccess);
        double successLuma = (0.299 * darkSuccess.getRed() + 0.587 * darkSuccess.getGreen() + 0.114 * darkSuccess.getBlue());
        assertTrue(successLuma > 140, "Dark mode success button color should have high luminance for visibility: " + successLuma);

        // Verify DualDatabaseComparePanel initializes with accessible theme colors
        com.dbtool.panels.DualDatabaseComparePanel panel = new com.dbtool.panels.DualDatabaseComparePanel(dbManagerA);
        panel.updateThemeColors();

        // Check that theme listener is registered and triggers
        boolean[] listenerCalled = new boolean[]{false};
        com.dbtool.util.ThemeManager.ThemeChangeListener testListener = isDark -> listenerCalled[0] = true;
        com.dbtool.util.ThemeManager.addThemeChangeListener(testListener);

        com.dbtool.util.ThemeManager.toggleTheme(null);
        assertTrue(listenerCalled[0], "Theme change listener should have been notified");

        // Restore dark mode
        com.dbtool.util.ThemeManager.toggleTheme(null);
        com.dbtool.util.ThemeManager.removeThemeChangeListener(testListener);
    }
}
