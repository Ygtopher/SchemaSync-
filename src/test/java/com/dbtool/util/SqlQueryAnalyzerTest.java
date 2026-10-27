package com.dbtool.util;

import com.dbtool.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SqlQueryAnalyzerTest {

    @Test
    void testUnfilteredDestructiveDmlDetection() {
        assertTrue(SqlQueryAnalyzer.isUnfilteredDestructiveDml("DELETE FROM employees;"));
        assertTrue(SqlQueryAnalyzer.isUnfilteredDestructiveDml("UPDATE employees SET salary = 0;"));
        assertTrue(SqlQueryAnalyzer.isUnfilteredDestructiveDml("DELETE FROM employees WHERE 1=1"));
        assertFalse(SqlQueryAnalyzer.isUnfilteredDestructiveDml("DELETE FROM employees WHERE id = 5;"));
        assertFalse(SqlQueryAnalyzer.isUnfilteredDestructiveDml("UPDATE employees SET salary = 90000 WHERE dept_id = 2;"));
        assertFalse(SqlQueryAnalyzer.isUnfilteredDestructiveDml("SELECT * FROM employees;"));
    }

    @Test
    void testBuildDryRunSelectSql() {
        assertEquals("SELECT * FROM employees WHERE dept_id = 3;",
                SqlQueryAnalyzer.buildDryRunSelectSql("DELETE FROM employees WHERE dept_id = 3;"));
        assertEquals("SELECT * FROM employees;",
                SqlQueryAnalyzer.buildDryRunSelectSql("DELETE FROM employees;"));
        assertEquals("SELECT * FROM employees WHERE id = 10;",
                SqlQueryAnalyzer.buildDryRunSelectSql("UPDATE employees SET salary = 5000 WHERE id = 10;"));
        assertEquals("SELECT * FROM accounts;",
                SqlQueryAnalyzer.buildDryRunSelectSql("UPDATE accounts SET active = false;"));
    }

    @Test
    void testExtractNamedParametersAndBind() {
        String sql = "SELECT * FROM orders WHERE status = :order_status AND total >= ${min_total} AND created_at::date > '2026-01-01'";
        List<String> params = SqlQueryAnalyzer.extractNamedParameters(sql);
        assertEquals(2, params.size());
        assertTrue(params.contains("order_status"));
        assertTrue(params.contains("min_total"));

        Map<String, String> values = new HashMap<>();
        values.put("order_status", "PAID");
        values.put("min_total", "250.50");

        String bound = SqlQueryAnalyzer.bindParameters(sql, values);
        assertTrue(bound.contains("status = 'PAID'"));
        assertTrue(bound.contains("total >= 250.50"));
        assertTrue(bound.contains("created_at::date")); // PostgreSQL cast preserved!
    }

    @Test
    void testStaticLintAntiPatterns() throws Exception {
        DatabaseManager db = new DatabaseManager();
        db.connectSandbox();

        // 1. SELECT * + Non-Sargable WHERE + Missing LIMIT
        List<SqlQueryAnalyzer.LintIssue> issues = SqlQueryAnalyzer.analyze(
                "SELECT * FROM employees WHERE UPPER(first_name) = 'ALICE'", db);
        assertTrue(issues.stream().anyMatch(i -> "SELECT_STAR".equals(i.code)));
        assertTrue(issues.stream().anyMatch(i -> "NON_SARGABLE_WHERE".equals(i.code)));
        assertTrue(issues.stream().anyMatch(i -> "MISSING_LIMIT".equals(i.code)));

        // Verify SELECT * expansion uses real columns from Sandbox DB
        String expanded = SqlQueryAnalyzer.expandSelectStar("SELECT * FROM employees WHERE id = 1", db);
        assertFalse(expanded.contains("SELECT *"));
        assertTrue(expanded.toLowerCase().contains("first_name"));
    }

    @Test
    void testContextAwareAliasAndAutocomplete() throws Exception {
        DatabaseManager db = new DatabaseManager();
        db.connectSandbox();

        String sql = "SELECT e. FROM employees e JOIN departments d ON ";
        int dotCaret = sql.indexOf("e.") + 2;
        List<String> colSuggestions = SqlQueryAnalyzer.suggestCompletions(sql, dotCaret, db);
        assertFalse(colSuggestions.isEmpty());
        assertTrue(colSuggestions.stream().anyMatch(s -> s.equalsIgnoreCase("e.first_name") || s.equalsIgnoreCase("e.salary")));
    }
}
