package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

public class MultiTabCloseTest {

    @Test
    void testMultiTabContainerCanCloseLastTabAndReopen() {
        MultiTabContainer<JPanel> container = new MultiTabContainer<>("Browse", JPanel::new);
        assertEquals(1, container.getTabs().size());
        assertNotNull(container.getActiveTab());

        // Close the very last tab ("Browse 1")
        container.closeTabAt(0);
        assertEquals(0, container.getTabs().size(), "Should allow closing the last tab down to 0 tabs");
        assertNull(container.getActiveTab(), "Active tab should be null when all tabs are closed");

        // Reopen a new tab
        JPanel reopened = container.addNewTabAndGet();
        assertNotNull(reopened);
        assertEquals(1, container.getTabs().size());
        assertSame(reopened, container.getActiveTab());
    }

    @Test
    void testDualDatabaseComparePanelCanCloseLastTab() {
        DatabaseManager dbm = new DatabaseManager();
        DualDatabaseComparePanel panel = new DualDatabaseComparePanel(dbm);
        assertEquals(1, panel.getDataCompareTabCount());

        DataCompareTabPanel firstTab = panel.getDataCompareTabs().get(0);
        panel.closeDataCompareTab(firstTab);
        assertEquals(0, panel.getDataCompareTabCount(), "Should allow closing the last comparison tab");

        DataCompareTabPanel newTab = panel.addDataCompareTab("Compare Session 1");
        assertNotNull(newTab);
        assertEquals(1, panel.getDataCompareTabCount());
    }
}
