package com.dbtool.util;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.Window;

public class ThemeManager {
    private static boolean isDarkMode = false;

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
                if (toggleButton != null) toggleButton.setText("☀️ Light Mode");
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
                if (toggleButton != null) toggleButton.setText("🌙 Dark Mode");
            }
            
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
