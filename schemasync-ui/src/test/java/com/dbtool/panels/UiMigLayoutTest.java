package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import net.miginfocom.swing.MigLayout;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

public class UiMigLayoutTest {

    @Test
    void testDualCompareAndAiAssistantPanelsUseMigLayoutWithSafeDefaults() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DatabaseManager db = new DatabaseManager();
            DualDatabaseComparePanel comparePanel = new DualDatabaseComparePanel(db);
            assertTrue(comparePanel.getComponentCount() > 0);

            DataCompareTabPanel tabPanel = new DataCompareTabPanel(comparePanel);
            assertTrue(tabPanel.getComponentCount() > 0);

            AiAssistantTab aiTab = new AiAssistantTab(db);
            assertTrue(aiTab.getLayout() instanceof MigLayout);
            assertFalse(aiTab.isIncludeSampleRowsSelected(),
                    "AI Assistant must default to Schema-Only (sample rows opt-in unchecked)");
            assertEquals(5, aiTab.getProviderCombo().getItemCount());
        });
    }
}
