package com.dbtool.util;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import javax.swing.*;
import java.awt.Color;
import java.awt.Window;

public class ThemeManager {
    private static boolean isDarkMode = true;

    public static boolean isDarkMode() {
        return isDarkMode;
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
            
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
