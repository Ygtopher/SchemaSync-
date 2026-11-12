package com.dbtool.util;

import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeManagerTest {

    @Test
    void testThemePresetsAndAccentColors() {
        JButton dummyBtn = new JButton();

        ThemeManager.setPreset(ThemeManager.ThemePreset.EMERALD_DARK, dummyBtn);
        assertTrue(ThemeManager.isDarkMode());
        assertEquals(ThemeManager.ThemePreset.EMERALD_DARK, ThemeManager.getCurrentPreset());
        assertEquals(ThemeManager.ThemePreset.EMERALD_DARK.getAccentColor(), ThemeManager.getPrimaryActionColor());
        assertNotNull(ThemeManager.getZebraAlternateColor());
        assertNotNull(ThemeManager.getRowHoverColor());

        ThemeManager.setPreset(ThemeManager.ThemePreset.SOLAR_LIGHT, dummyBtn);
        assertFalse(ThemeManager.isDarkMode());
        assertEquals(ThemeManager.ThemePreset.SOLAR_LIGHT, ThemeManager.getCurrentPreset());

        ThemeManager.setPreset(ThemeManager.ThemePreset.MIDNIGHT_BLUE, dummyBtn);
        assertTrue(ThemeManager.isDarkMode());
    }

    @Test
    void testUiScalingClamping() {
        JButton dummyBtn = new JButton();

        ThemeManager.setUiScaleFactor(1.1f, dummyBtn);
        assertEquals(1.1f, ThemeManager.getUiScaleFactor(), 0.01f);

        ThemeManager.setUiScaleFactor(2.0f, dummyBtn);
        assertEquals(1.35f, ThemeManager.getUiScaleFactor(), 0.01f);

        ThemeManager.setUiScaleFactor(0.5f, dummyBtn);
        assertEquals(0.85f, ThemeManager.getUiScaleFactor(), 0.01f);

        // Reset to 100%
        ThemeManager.setUiScaleFactor(1.0f, dummyBtn);
        assertEquals(1.0f, ThemeManager.getUiScaleFactor(), 0.01f);
    }

    @Test
    void testCompositeFontPreservesEmojiAndSymbolFallbacks() {
        JButton dummyBtn = new JButton("▶ Run Query 🔍 🏠 📐 🎨 ▾ ☰");
        ThemeManager.setPreset(ThemeManager.ThemePreset.MIDNIGHT_BLUE, dummyBtn);

        java.awt.Font buttonFont = UIManager.getFont("Button.font");
        assertNotNull(buttonFont);
        assertTrue(buttonFont.canDisplay('▶'), "Button.font must render play symbol ▶");
        assertTrue(buttonFont.canDisplay('▾'), "Button.font must render dropdown symbol ▾");
        assertTrue(buttonFont.canDisplay('☰'), "Button.font must render menu symbol ☰");
        assertTrue(buttonFont.canDisplay(0x1F50D), "Button.font must render magnifier emoji 🔍");
        assertTrue(buttonFont.canDisplay(0x1F3E0), "Button.font must render home emoji 🏠");

        java.awt.Font customUiFont = ThemeManager.getUiFont(java.awt.Font.BOLD, 12);
        assertNotNull(customUiFont);
        assertTrue(customUiFont.canDisplay('▶'), "ThemeManager.getUiFont must render play symbol ▶");
        assertTrue(customUiFont.canDisplay(0x1F50D), "ThemeManager.getUiFont must render magnifier emoji 🔍");
    }

    @Test
    void testTableGridSwitchesToLightBackgroundInSolarLightMode() {
        JButton dummyBtn = new JButton();
        ThemeManager.setPreset(ThemeManager.ThemePreset.MIDNIGHT_BLUE, dummyBtn);

        JTable table = new JTable(new Object[][]{{"Row1"}, {"Row2"}}, new String[]{"Col1"});
        com.dbtool.ui.components.SmartTableCellRenderer.install(table, c -> false, c -> false);
        JScrollPane scrollPane = new JScrollPane(table);
        TableRowUtilities.addRowNumbers(table, scrollPane);

        // Initially dark in Midnight Blue
        assertTrue(table.getBackground().getRed() < 50, "Table background should start dark in Midnight Blue");

        // Switch to Solar Daylight (Light Mode)
        ThemeManager.setPreset(ThemeManager.ThemePreset.SOLAR_LIGHT, dummyBtn);
        ThemeManager.syncComponentTreeThemeColors(scrollPane);

        assertEquals(255, table.getBackground().getRed(), "Table background must become white (255,255,255) in Light Mode");
        assertEquals(255, scrollPane.getViewport().getBackground().getRed(), "Viewport background must become white in Light Mode");

        java.awt.Component cell0 = table.getDefaultRenderer(Object.class)
                .getTableCellRendererComponent(table, "Row1", false, false, 0, 0);
        assertEquals(255, cell0.getBackground().getRed(), "Even row cell background must be white in Light Mode");

        java.awt.Component cell1 = table.getDefaultRenderer(Object.class)
                .getTableCellRendererComponent(table, "Row2", false, false, 1, 0);
        assertTrue(cell1.getBackground().getRed() >= 240, "Odd zebra row cell background must be light in Light Mode");

        // Restore default Midnight Blue
        ThemeManager.setPreset(ThemeManager.ThemePreset.MIDNIGHT_BLUE, dummyBtn);
    }
}
