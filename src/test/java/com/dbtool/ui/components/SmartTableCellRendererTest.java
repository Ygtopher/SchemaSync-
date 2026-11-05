package com.dbtool.ui.components;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
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
}
