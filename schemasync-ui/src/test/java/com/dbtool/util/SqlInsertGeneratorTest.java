package com.dbtool.util;

import com.dbtool.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

public class SqlInsertGeneratorTest {

    private DatabaseManager dbManager;

    @BeforeEach
    public void setUp() throws Exception {
        dbManager = new DatabaseManager();
        dbManager.connectSandbox("test_insert_gen_" + System.currentTimeMillis());
    }

    @Test
    public void testExcludePrimaryKeyFromInsert() {
        // Table: employees (id, first_name, last_name, dept, salary)
        // id is primary key
        String[] cols = new String[]{"id", "first_name", "last_name", "dept", "salary"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{1, "Alice", "Smith", "IT", 75000.00});

        JTable table = new JTable(model);
        int[] selectedRows = new int[]{0};

        String sql = SqlInsertGenerator.generateInsertStatements(table, selectedRows, "employees", dbManager, true);

        assertNotNull(sql);
        String colsPart = sql.substring(sql.indexOf("(") + 1, sql.indexOf(") VALUES"));
        java.util.List<String> colNames = java.util.Arrays.asList(colsPart.split(",\\s*"));
        assertFalse(colNames.contains("id"), "Primary key 'id' should be excluded from column list: " + sql);
        assertFalse(sql.contains("'1'"), "Primary key value '1' should not be present: " + sql);
        assertTrue(colNames.contains("first_name"), "Column 'first_name' must be present: " + sql);
        assertTrue(colNames.contains("last_name"), "Column 'last_name' must be present: " + sql);
        assertTrue(sql.contains("'Alice'"), "Value 'Alice' must be present: " + sql);
        assertTrue(sql.contains("'Smith'"), "Value 'Smith' must be present: " + sql);
        assertTrue(sql.startsWith("INSERT INTO "), "Should format valid INSERT: " + sql);
    }

    @Test
    public void testIncludePrimaryKeyWhenExplicitlyRequested() {
        String[] cols = new String[]{"id", "first_name", "last_name"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{42, "Bob", "Jones"});

        JTable table = new JTable(model);
        int[] selectedRows = new int[]{0};

        String sql = SqlInsertGenerator.generateInsertStatements(table, selectedRows, "employees", dbManager, false);

        assertNotNull(sql);
        String colsPart = sql.substring(sql.indexOf("(") + 1, sql.indexOf(") VALUES"));
        java.util.List<String> colNames = java.util.Arrays.asList(colsPart.split(",\\s*"));
        assertTrue(colNames.contains("id"), "Primary key 'id' should be included when excludePrimaryKey is false: " + sql);
        assertTrue(sql.contains("'42'"), "Value '42' should be included: " + sql);
        assertTrue(colNames.contains("first_name"));
    }

    @Test
    public void testMultiRowInsertWithoutPrimaryKey() {
        String[] cols = new String[]{"id", "dept_name", "location"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{10, "Engineering", "NYC"});
        model.addRow(new Object[]{20, "Marketing", "London"});

        JTable table = new JTable(model);
        int[] selectedRows = new int[]{0, 1};

        String sql = SqlInsertGenerator.generateInsertStatements(table, selectedRows, "departments", dbManager, true);

        assertNotNull(sql);
        String[] lines = sql.trim().split("\\r?\\n");
        assertEquals(2, lines.length, "Should generate exactly 2 insert statements");

        for (String line : lines) {
            assertFalse(line.matches("(?i).*\\b(id)\\b.*(?=VALUES).*"), "No line should contain 'id' in columns: " + line);
            assertTrue(line.startsWith("INSERT INTO "), "Each line must start with INSERT: " + line);
            assertTrue(line.endsWith(";"), "Each line must end with semicolon: " + line);
        }

        assertTrue(lines[0].contains("'Engineering'"));
        assertTrue(lines[1].contains("'Marketing'"));
    }

    @Test
    public void testNullAndQuoteEscaping() {
        String[] cols = new String[]{"id", "name", "notes"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{99, "O'Connor", null});

        JTable table = new JTable(model);
        int[] selectedRows = new int[]{0};

        String sql = SqlInsertGenerator.generateInsertStatements(table, selectedRows, "test_table", null, true);

        assertFalse(sql.matches("(?i).*\\b(id)\\b.*(?=VALUES).*"), "Should exclude 'id'");
        assertTrue(sql.contains("'O''Connor'"), "Single quotes must be escaped as double single-quotes: " + sql);
        assertTrue(sql.contains("NULL"), "Null value must be emitted as unquoted NULL keyword: " + sql);
    }

    @Test
    public void testJoinedTableColumnsFilteredOut() {
        // When results come from a JOIN query, e.g. employees.first_name, departments.dept_name
        String[] cols = new String[]{"employees.id", "employees.first_name", "departments.dept_name"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{1, "Diana", "Finance"});

        JTable table = new JTable(model);
        int[] selectedRows = new int[]{0};

        String sql = SqlInsertGenerator.generateInsertStatements(table, selectedRows, "employees", dbManager, true);

        assertFalse(sql.matches("(?i).*\\b(id)\\b.*(?=VALUES).*"), "Should exclude PK id");
        assertTrue(sql.contains("first_name"), "Should include employees.first_name as first_name");
        assertFalse(sql.contains("dept_name"), "Should filter out departments.dept_name from other table");
        assertTrue(sql.contains("'Diana'"));
    }

    @Test
    public void testFallbackWhenAllColumnsArePrimaryKey() {
        // Table where every column is part of PK
        String[] cols = new String[]{"id"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{101});

        JTable table = new JTable(model);
        int[] selectedRows = new int[]{0};

        String sql = SqlInsertGenerator.generateInsertStatements(table, selectedRows, "pure_pk_table", null, true);

        assertTrue(sql.contains("id"), "If all columns are PK, should fallback to including to avoid empty syntax: " + sql);
        assertTrue(sql.contains("'101'"));
    }

    @Test
    public void testGenerateUpdateStatements() {
        String[] cols = new String[]{"id", "first_name", "salary"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{7, "Elena", 95000});

        JTable table = new JTable(model);
        String updateSql = SqlInsertGenerator.generateUpdateStatements(table, new int[]{0}, "employees", dbManager);
        assertTrue(updateSql.startsWith("UPDATE employees SET "));
        assertTrue(updateSql.contains("first_name = 'Elena'"));
        assertTrue(updateSql.contains("salary = '95000'"));
        assertTrue(updateSql.contains("WHERE id = '7';"));
    }

    @Test
    public void testGenerateJsonArrayAndMarkdownAndStats() {
        String[] cols = new String[]{"id", "first_name", "salary"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{1, "Alice", 50000});
        model.addRow(new Object[]{2, "Bob", 70000});

        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 1);

        String json = SqlInsertGenerator.generateJsonArray(table, new int[]{0, 1});
        assertTrue(json.contains("\"first_name\": \"Alice\""));
        assertTrue(json.contains("\"salary\": 70000"));

        String md = SqlInsertGenerator.generateMarkdownTable(table, new int[]{0, 1});
        assertTrue(md.contains("| id | first_name | salary |"));
        assertTrue(md.contains("| 1 | Alice | 50000 |"));

        String stats = SqlInsertGenerator.calculateSelectionStats(table);
        assertTrue(stats.contains("Selected Rows: 2"));
        assertTrue(stats.contains("Sum:"));
    }
}
