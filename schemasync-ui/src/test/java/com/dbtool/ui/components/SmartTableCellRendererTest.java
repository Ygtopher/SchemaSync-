package com.dbtool.ui.components;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class SmartTableCellRendererTest {

    @Test
    void testSemanticValueParsingAndRendering() {
        assertEquals(Boolean.TRUE, SmartTableCellRenderer.parseBoolean(true));
        assertEquals(Boolean.FALSE, SmartTableCellRenderer.parseBoolean("false"));
        assertNull(SmartTableCellRenderer.parseBoolean("hello"));

        assertTrue(SmartTableCellRenderer.isNumericValue(42));
        assertTrue(SmartTableCellRenderer.isNumericValue("12345.67"));
        // Phone numbers starting with 0 should not be treated as numeric
        assertFalse(SmartTableCellRenderer.isNumericValue("0788123456"));
        assertFalse(SmartTableCellRenderer.isNumericValue("abc"));

        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{
                        {101, null, true, "2026-11-05 18:00:00"},
                        {102, "Alice", false, "Kigali"}
                },
                new Object[]{"id_client", "nom", "active", "created_at"}
        );
        JTable table = new JTable(model);
        SmartTableCellRenderer.install(
                table,
                col -> "id_client".equals(col),
                col -> "dept_id".equals(col)
        );

        Component nullComp = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        assertTrue(nullComp instanceof JLabel);
        assertEquals("NULL", ((JLabel) nullComp).getText());

        Component boolComp = table.prepareRenderer(table.getCellRenderer(0, 2), 0, 2);
        assertTrue(((JLabel) boolComp).getText().contains("true"));

        Component numComp = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
        assertEquals(SwingConstants.RIGHT, ((JLabel) numComp).getHorizontalAlignment());
    }

    @Test
    void testRepeatedInstallAndColumnAdjustDoesNotZoomOrExpandHeaders() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{
                        {1, "Alice", 100.50},
                        {2, "Bob", 250.75}
                },
                new Object[]{"id", "username", "balance"}
        );
        JTable table = new JTable(model);

        // Run install 5 times consecutively as if user clicked "Run Query" 5 times
        for (int i = 0; i < 5; i++) {
            SmartTableCellRenderer.install(table, col -> "id".equals(col), col -> false);
        }

        // Header renderer must be unwrapped so only 1 SmartHeaderRenderer is at the top
        TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
        assertTrue(headerRenderer instanceof SmartTableCellRenderer.SmartHeaderRenderer);
        SmartTableCellRenderer.SmartHeaderRenderer smartHdr = (SmartTableCellRenderer.SmartHeaderRenderer) headerRenderer;
        assertFalse(smartHdr.getDelegate() instanceof SmartTableCellRenderer.SmartHeaderRenderer,
                "SmartHeaderRenderer delegate must not be another SmartHeaderRenderer");

        // Measure width before and after 50 renders
        int widthBefore = SmartTableColumnAdjuster.calculateOptimalColumnWidth(table, 0, 80, 550);
        int usernameWidthBefore = SmartTableColumnAdjuster.calculateOptimalColumnWidth(table, 1, 80, 550);

        // Simulate 50 repaint / header render cycles
        for (int i = 0; i < 50; i++) {
            for (int col = 0; col < table.getColumnCount(); col++) {
                Component comp = headerRenderer.getTableCellRendererComponent(
                        table, table.getColumnName(col), false, false, -1, col);
                assertNotNull(comp);
            }
        }

        int widthAfter = SmartTableColumnAdjuster.calculateOptimalColumnWidth(table, 0, 80, 550);
        int usernameWidthAfter = SmartTableColumnAdjuster.calculateOptimalColumnWidth(table, 1, 80, 550);

        assertEquals(widthBefore, widthAfter, "Column 0 width must stay completely stable and not zoom in on repeated runs");
        assertEquals(usernameWidthBefore, usernameWidthAfter, "Column 1 width must stay completely stable and not zoom in on repeated runs");
    }
}
