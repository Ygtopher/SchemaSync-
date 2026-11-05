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
}
