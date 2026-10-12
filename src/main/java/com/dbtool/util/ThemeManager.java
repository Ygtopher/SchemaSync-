package com.dbtool.util;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import javax.swing.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;

public class ThemeManager {
    private static boolean isDarkMode = true;

    public interface ThemeChangeListener {
        void onThemeChanged(boolean isDark);
    }

    private static final java.util.List<ThemeChangeListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addThemeChangeListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    public static boolean isDarkMode() {
        return isDarkMode;
    }

    public static boolean isDarkTheme(Component c) {
        if (c != null && c.getBackground() != null) {
            Color bg = c.getBackground();
            double luma = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue());
            return luma < 128;
        }
        return isDarkMode;
    }

    public static Color getPrimaryActionColor() {
        return isDarkMode ? new Color(100, 181, 246) : new Color(0, 102, 180);
    }

    public static Color getSuccessColor() {
        return isDarkMode ? new Color(129, 199, 132) : new Color(0, 128, 55);
    }

    public static Color getDangerColor() {
        return isDarkMode ? new Color(248, 110, 110) : new Color(180, 0, 0);
    }

    public static Color getMutedTextColor() {
        return isDarkMode ? new Color(175, 175, 175) : Color.GRAY;
    }

    public static void toggleTheme(JButton toggleButton) {
        isDarkMode = !isDarkMode;
        applyTheme(toggleButton);
    }
    
    public static void init(JButton toggleButton) {
        applyTheme(toggleButton);
    }

    private static void applyTheme(JButton toggleButton) {
        try {
            if (isDarkMode) {
                UIManager.setLookAndFeel(new FlatMacDarkLaf());
                UIManager.put("ScrollBar.thumb", new Color(180, 180, 180)); // Soft white/light gray
                UIManager.put("ScrollBar.hoverThumb", new Color(220, 220, 220));
                UIManager.put("ScrollBar.pressedThumb", new Color(255, 255, 255));
                if (toggleButton != null) toggleButton.setText("\u2600\uFE0F Light Mode"); // Sun emoji
            } else {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
                UIManager.put("ScrollBar.thumb", new Color(100, 100, 100)); // Soft dark gray instead of pure black
                UIManager.put("ScrollBar.hoverThumb", new Color(60, 60, 60));
                UIManager.put("ScrollBar.pressedThumb", new Color(20, 20, 20));
                if (toggleButton != null) toggleButton.setText("\uD83C\uDF19 Dark Mode"); // Crescent moon emoji
            }
            
            // Make Scrollbars more visible
            UIManager.put("ScrollBar.width", 18);
            UIManager.put("ScrollBar.showButtons", true);
            UIManager.put("ScrollBar.thumbArc", 4);
            UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
            
            UIManager.put("SplitPane.dividerSize", 18);
            UIManager.put("SplitPane.centerOneTouchButtons", true);
            UIManager.put("SplitPane.oneTouchButtonSize", 20);
            UIManager.put("SplitPane.oneTouchButtonOffset", 5);
            
            // Enhance table grid visibility on Mac themes
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", true);
            UIManager.put("Table.gridColor", isDarkMode ? new Color(55, 55, 55) : java.awt.Color.LIGHT_GRAY);
            
            for (ThemeChangeListener l : listeners) {
                try {
                    l.onThemeChanged(isDarkMode);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
