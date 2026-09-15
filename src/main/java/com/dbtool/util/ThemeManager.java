package com.dbtool.util;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.Color;
import java.awt.Window;

public class ThemeManager {
    private static boolean isDarkMode = false;

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
                UIManager.setLookAndFeel(new FlatDarkLaf());
                UIManager.put("ScrollBar.thumb", Color.WHITE);
                UIManager.put("ScrollBar.hoverThumb", new Color(200, 200, 200));
                UIManager.put("ScrollBar.pressedThumb", new Color(150, 150, 150));
                if (toggleButton != null) toggleButton.setText("💡 Light Mode");
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
                UIManager.put("ScrollBar.thumb", Color.BLACK);
                UIManager.put("ScrollBar.hoverThumb", new Color(50, 50, 50));
                UIManager.put("ScrollBar.pressedThumb", new Color(100, 100, 100));
                if (toggleButton != null) toggleButton.setText("🌙 Dark Mode");
            }
            
            // Make Scrollbars more visible
            UIManager.put("ScrollBar.width", 18);
            UIManager.put("ScrollBar.showButtons", true);
            UIManager.put("ScrollBar.thumbArc", 4);
            UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
            
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
