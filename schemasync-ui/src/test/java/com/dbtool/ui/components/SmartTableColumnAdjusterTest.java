package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class SmartTableColumnAdjusterTest {

    @Test
    void testColumnWidthsMatchContentAndHeaderLength() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{
                        {1, "Short", "This is an extraordinarily long text content that needs substantial column width to be completely visible without ellipses"},
                        {2, "Medium name", "Another extensive paragraph detailing database operations in SchemaSync"}
                },
                new Object[]{"ID", "Name", "Long_Description_Header_Clause"}
        );

        JTable table = new JTable(model);
        SmartTableCellRenderer.install(table, col -> "ID".equals(col), col -> false);

        int idOptimal = SmartTableColumnAdjuster.calculateOptimalColumnWidth(table, 0, 80, 550);
        int nameOptimal = SmartTableColumnAdjuster.calculateOptimalColumnWidth(table, 1, 80, 550);
        int descOptimal = SmartTableColumnAdjuster.calculateOptimalColumnWidth(table, 2, 80, 550);

        // ID column should be near min-width (around 80-110px for key icon + header + "1")
        assertTrue(idOptimal >= 80, "ID column width must be at least min-width 80px");

        // Long description column must be substantially wider than short ID and Name column
        assertTrue(descOptimal > nameOptimal, "Long text column must be wider than medium column");
        assertTrue(descOptimal > idOptimal, "Long text column must be wider than ID column");
        assertTrue(descOptimal >= 300, "Long text column should have expanded to comfortably display data");

        // Test adjustColumnWidths
        SmartTableColumnAdjuster.adjustColumnWidths(table);
        assertEquals(JTable.AUTO_RESIZE_OFF, table.getAutoResizeMode(), "Table must use AUTO_RESIZE_OFF to prevent column squishing");
    }

    @Test
    void testLightModeDataGridContrastAndNullVisibility() {
        JButton dummyBtn = new JButton();
        ThemeManager.setPreset(ThemeManager.ThemePreset.SOLAR_LIGHT, dummyBtn);

        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{
                        {100, null, "Sample text"}
                },
                new Object[]{"ID", "NullableCol", "TextCol"}
        );

        JTable table = new JTable(model);
        SmartTableCellRenderer.install(table, col -> false, col -> false);

        // In Solar Light mode, table foreground must be deep dark slate (almost black) for maximum contrast
        Color textFg = table.getForeground();
        assertTrue(textFg.getRed() <= 30 && textFg.getGreen() <= 30 && textFg.getBlue() <= 50,
                "Text foreground in light mode must be high-contrast dark slate (Slate 900)");

        // Grid color in Solar Light mode must be crisp Slate 300 (not washed out)
        Color gridColor = table.getGridColor();
        assertTrue(gridColor.getRed() >= 180 && gridColor.getRed() <= 230,
                "Grid color must be clean, visible slate/gray");

        // NULL component renderer text must be crisp and readable
        Component nullComp = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        assertTrue(nullComp instanceof JLabel);
        JLabel nullLabel = (JLabel) nullComp;
        assertEquals("NULL", nullLabel.getText());

        // Reset back to Midnight Blue
        ThemeManager.setPreset(ThemeManager.ThemePreset.MIDNIGHT_BLUE, dummyBtn);
    }

    @Test
    void testAdCliColumnsWidthCalculation() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{
                        {209000100234L, null, 1, 1, "GITWA NGANZO", null, null, 1, 1, "250788123456", null, null, "philor@gmail.com", 75633, null, null, null, "2020-06-15", 0, 2, null, null}
                },
                new Object[]{"id_client", "anc_id_client", "statut_juridique", "qualite", "adresse", "code_postal", "ville", "pays", "pp_pm", "num_tel", "num_fax", "num_portable", "email", "id_cpte_base", "id_loc1", "id_loc2", "loc3", "date_adhesion", "nbre_parts", "etat", "date_radiation", "nb_imf"}
        );

        JTable table = new JTable(model);
        SmartTableCellRenderer.install(table, col -> "id_client".equals(col), col -> false);
        for (int c = 0; c < table.getColumnCount(); c++) {
            int w = SmartTableColumnAdjuster.calculateOptimalColumnWidth(table, c, 80, 550);
            System.out.println("COL " + c + " (" + table.getColumnName(c) + ") -> " + w + " px");
            assertTrue(w >= 80, "Every column should be at least 80px");
        }
    }
}
