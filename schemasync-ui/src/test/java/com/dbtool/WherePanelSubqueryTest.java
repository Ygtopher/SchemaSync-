package com.dbtool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WherePanelSubqueryTest {

    @BeforeAll
    public static void setupHeadless() {
        System.setProperty("java.awt.headless", "false");
    }

    @Test
    public void testSubquerySqlBuilderAndConditionClause() throws Exception {
        Main mainFrame = new Main();
        java.lang.reflect.Field dbmField = Main.class.getDeclaredField("dbManager");
        dbmField.setAccessible(true);
        DatabaseManager dbManager = (DatabaseManager) dbmField.get(mainFrame);
        dbManager.connectSandbox();

        List<Main.WherePanel> ownerList = new ArrayList<>();
        JPanel container = new JPanel();
        Main.WherePanel wp = mainFrame.new WherePanel(true, ownerList, container);
        ownerList.add(wp);

        wp.tableDropdown.setAllItems(List.of("employees", "departments"));
        wp.tableDropdown.setSelectedItem("employees");
        Thread.sleep(200);
        wp.columnDropdown.setSelectedItem("dept_id");
        wp.operatorDropdown.setSelectedItem("IN");
        wp.inTypeDropdown.setSelectedItem("Subquery (SELECT)");
        Thread.sleep(600);

        wp.inSubTableDropdown.setAllItems(List.of("departments", "projects"));
        wp.inSubTableDropdown.setSelectedItem("departments");
        Thread.sleep(300);
        wp.inSubColDropdown.setAllItems(List.of("id", "dept_name"));
        wp.inSubColDropdown.setSelectedItem("id");

        // Test basic subquery
        String basicSql = wp.buildSubquerySql(dbManager);
        assertNotNull(basicSql);
        assertTrue(basicSql.contains("SELECT"));
        assertTrue(basicSql.contains("id"));
        assertTrue(basicSql.contains("FROM"));
        assertTrue(basicSql.contains("departments"));

        String basicCondition = wp.buildConditionClause(dbManager);
        assertTrue(basicCondition.contains("dept_id"));
        assertTrue(basicCondition.contains("IN"));
        assertTrue(basicCondition.contains("(SELECT"));

        // Now test advanced clauses
        // 1. DISTINCT
        java.lang.reflect.Field distField = Main.WherePanel.class.getDeclaredField("subDistinct");
        distField.setAccessible(true);
        distField.set(wp, true);

        // 2. WHERE condition
        java.lang.reflect.Field condField = Main.WherePanel.class.getDeclaredField("subWhereConditions");
        condField.setAccessible(true);
        List<Object> conds = new ArrayList<>();
        Main.WherePanel.SubqueryWhereCondition cond1 = wp.new SubqueryWhereCondition("AND", "budget", ">", "50000");
        conds.add(cond1);
        condField.set(wp, conds);

        // 3. GROUP BY and HAVING
        java.lang.reflect.Field gbField = Main.WherePanel.class.getDeclaredField("subGroupByCol");
        gbField.setAccessible(true);
        gbField.set(wp, "name");

        java.lang.reflect.Field havField = Main.WherePanel.class.getDeclaredField("subHaving");
        havField.setAccessible(true);
        havField.set(wp, "COUNT(*) > 1");

        // 4. ORDER BY
        java.lang.reflect.Field obField = Main.WherePanel.class.getDeclaredField("subOrderByCol");
        obField.setAccessible(true);
        obField.set(wp, "budget");

        java.lang.reflect.Field dirField = Main.WherePanel.class.getDeclaredField("subOrderDir");
        dirField.setAccessible(true);
        dirField.set(wp, "DESC");

        // 5. LIMIT and OFFSET
        java.lang.reflect.Field limField = Main.WherePanel.class.getDeclaredField("subLimit");
        limField.setAccessible(true);
        limField.set(wp, "10");

        java.lang.reflect.Field offField = Main.WherePanel.class.getDeclaredField("subOffset");
        offField.setAccessible(true);
        offField.set(wp, "5");

        String fullSql = wp.buildSubquerySql(dbManager);
        System.out.println("Generated Subquery SQL: " + fullSql);

        assertTrue(fullSql.contains("SELECT DISTINCT"));
        assertTrue(fullSql.contains("budget"));
        assertTrue(fullSql.contains("WHERE"));
        assertTrue(fullSql.contains(">"));
        assertTrue(fullSql.contains("50000"));
        assertTrue(fullSql.contains("GROUP BY"));
        assertTrue(fullSql.contains("HAVING COUNT(*) > 1"));
        assertTrue(fullSql.contains("ORDER BY"));
        assertTrue(fullSql.contains("DESC"));
        assertTrue(fullSql.contains("LIMIT 10"));
        assertTrue(fullSql.contains("OFFSET 5"));

        String fullCondition = wp.buildConditionClause(dbManager);
        System.out.println("Generated Condition Clause: " + fullCondition);
        assertTrue(fullCondition.contains("IN (" + fullSql + ")"));

        // 6. Test Custom SQL Override
        java.lang.reflect.Field customOverrideField = Main.WherePanel.class.getDeclaredField("useCustomSubquery");
        customOverrideField.setAccessible(true);
        customOverrideField.set(wp, true);

        java.lang.reflect.Field customTextField = Main.WherePanel.class.getDeclaredField("customSubqueryText");
        customTextField.setAccessible(true);
        customTextField.set(wp, "SELECT target_id FROM lookup_table WHERE status = 'ACTIVE'");

        String overriddenSql = wp.buildSubquerySql(dbManager);
        assertEquals("SELECT target_id FROM lookup_table WHERE status = 'ACTIVE'", overriddenSql);

        String overriddenClause = wp.buildConditionClause(dbManager);
        assertTrue(overriddenClause.contains("(SELECT target_id FROM lookup_table WHERE status = 'ACTIVE')"));
        System.out.println("Override Test Passed: " + overriddenClause);
    }
}
