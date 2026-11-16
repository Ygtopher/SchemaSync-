package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.TypedTableModel;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

public class SqlEditorPanelTest {

    @Test
    void testChunkedQueryResultAndInfiniteScroll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SqlEditorPanel panel = new SqlEditorPanel(new DatabaseManager());

            // Prepare 250 rows of dummy data
            Vector<String> colNames = new Vector<>();
            colNames.add("ID");
            colNames.add("NAME");
            String[] colTypes = new String[]{"INTEGER", "VARCHAR"};

            Vector<Vector<Object>> allRows = new Vector<>();
            for (int i = 0; i < 250; i++) {
                Vector<Object> row = new Vector<>();
                row.add(i + 1);
                row.add("User " + (i + 1));
                allRows.add(row);
            }

            // Initial model only gets first 100 rows
            Vector<Vector<Object>> initialData = new Vector<>();
            for (int i = 0; i < 100; i++) {
                initialData.add(allRows.get(i));
            }
            TypedTableModel model = new TypedTableModel(initialData, colNames, colTypes);

            JComponent resultTab = panel.buildInteractiveResultTab(model, allRows, "SELECT * FROM users", 12L);
            assertNotNull(resultTab);

            JTable table = (JTable) resultTab.getClientProperty("resultTable");
            assertNotNull(table);
            assertEquals(100, table.getRowCount(), "Initial render should be chunked to first 100 rows");
            assertEquals(250, allRows.size(), "Total pre-fetched rows should be preserved");

            // Verify Load All button can load all remaining rows
            JPanel filterBar = (JPanel) resultTab.getComponent(0);
            JPanel eastActions = (JPanel) filterBar.getComponent(2);
            JButton loadAllBtn = null;
            for (java.awt.Component c : eastActions.getComponents()) {
                if (c instanceof JButton && ((JButton) c).getText().contains("Load All")) {
                    loadAllBtn = (JButton) c;
                    break;
                }
            }
            assertNotNull(loadAllBtn, "Load All button should be present when total rows > 100");
            loadAllBtn.doClick();

            assertEquals(250, model.getRowCount(), "Clicking Load All should load all 250 rows");
            assertFalse(loadAllBtn.isVisible(), "Load All button should hide after loading all rows");
        });
    }

    @Test
    void testMaxRowsDropdownAndEffectiveLimits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SqlEditorPanel panel = new SqlEditorPanel(new DatabaseManager());

            // Default should be "No Limit (∞)" which translates to 0 (unlimited in JDBC)
            assertEquals(0, panel.getEffectiveMaxRows(), "Default should be unlimited (0)");

            // Preset values
            panel.setMaxRowsValue("50,000");
            assertEquals(50000, panel.getEffectiveMaxRows());

            panel.setMaxRowsValue("10,000");
            assertEquals(10000, panel.getEffectiveMaxRows());

            panel.setMaxRowsValue("1,000");
            assertEquals(1000, panel.getEffectiveMaxRows());

            panel.setMaxRowsValue("100,000");
            assertEquals(100000, panel.getEffectiveMaxRows());

            // Custom user typing
            panel.setMaxRowsValue("2500");
            assertEquals(2500, panel.getEffectiveMaxRows());

            // Unlimited keywords
            panel.setMaxRowsValue("No Limit (∞)");
            assertEquals(0, panel.getEffectiveMaxRows());

            panel.setMaxRowsValue("All");
            assertEquals(0, panel.getEffectiveMaxRows());

            panel.setMaxRowsValue("0");
            assertEquals(0, panel.getEffectiveMaxRows());
        });
    }
}
