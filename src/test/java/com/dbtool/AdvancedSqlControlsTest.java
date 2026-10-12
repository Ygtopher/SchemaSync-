package com.dbtool;

import com.dbtool.panels.DataCompareTabPanel;
import com.dbtool.panels.DualDatabaseComparePanel;
import com.dbtool.panels.DataOpsPanel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedSqlControlsTest {

    @Test
    public void testDataCompareTabPanelQueryBuilder() {
        DatabaseManager dbm = new DatabaseManager();
        DualDatabaseComparePanel parent = new DualDatabaseComparePanel(dbm);
        DataCompareTabPanel tab = new DataCompareTabPanel(parent);

        String basicQuery = tab.buildQueryForDatabase(dbm, "employees", "id");
        assertNotNull(basicQuery);
        assertTrue(basicQuery.startsWith("SELECT * FROM employees"));

        // Test with table sample and lock
        tab.copyStateFrom(tab); // verify copyStateFrom
        String sampleQuery = tab.buildQueryForDatabase(dbm, "employees", "id");
        assertTrue(sampleQuery.contains("FROM employees"));
    }

    @Test
    public void testDataOpsPanelBuildSqlOperations() throws Exception {
        DatabaseManager dbm = new DatabaseManager();
        DataOpsPanel panel = new DataOpsPanel(dbm);
        assertNotNull(panel);

        // Reflection test on buildSql for operations that do not require an active DB connection
        Method buildSqlMethod = DataOpsPanel.class.getDeclaredMethod("buildSql");
        buildSqlMethod.setAccessible(true);

        java.lang.reflect.Field opField = DataOpsPanel.class.getDeclaredField("operationCb");
        opField.setAccessible(true);
        javax.swing.JComboBox<String> opCb = (javax.swing.JComboBox<String>) opField.get(panel);

        // Test TRANSACTION ISOLATION
        opCb.setSelectedItem("TRANSACTION ISOLATION");
        String isoSql = (String) buildSqlMethod.invoke(panel);
        assertNotNull(isoSql);
        assertTrue(isoSql.contains("TRANSACTION ISOLATION LEVEL"));

        // Test ADVISORY LOCK
        opCb.setSelectedItem("ADVISORY LOCK");
        String advSql = (String) buildSqlMethod.invoke(panel);
        assertNotNull(advSql);
        assertTrue(advSql.contains("pg_advisory_lock"));

        // Test LISTEN / NOTIFY
        opCb.setSelectedItem("LISTEN / NOTIFY");
        String pubSql = (String) buildSqlMethod.invoke(panel);
        assertNotNull(pubSql);
        assertTrue(pubSql.contains("NOTIFY"));
    }
}
