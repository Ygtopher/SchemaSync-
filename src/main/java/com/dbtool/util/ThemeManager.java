package com.dbtool.util;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.formdev.flatlaf.util.FontUtils;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages global Look-and-Feel themes, accent presets, sleek scrollbar/splitter styling,
 * and dynamic UI font scaling across SchemaSync.
 */
public class ThemeManager {

    public enum ThemePreset {
        MIDNIGHT_BLUE("🌌 Midnight Blue (Dark)", true, new Color(56, 189, 248), new Color(18, 22, 31), new Color(24, 30, 42)),
        EMERALD_DARK("🌲 Emerald Studio (Dark)", true, new Color(52, 211, 153), new Color(17, 24, 21), new Color(23, 33, 29)),
        OBSIDIAN_SLATE("🌑 Obsidian Slate (Dark)", true, new Color(129, 140, 248), new Color(20, 20, 24), new Color(28, 28, 34)),
        CYBER_PURPLE("🔮 Cyber Amethyst (Dark)", true, new Color(192, 132, 252), new Color(22, 18, 30), new Color(30, 25, 42)),
        SOLAR_LIGHT("☀️ Solar Daylight (Light)", false, new Color(2, 132, 199), new Color(255, 255, 255), new Color(246, 248, 252));

        private final String displayName;
        private final boolean dark;
        private final Color accentColor;
        private final Color tableBg;
        private final Color tableAltRowBg;

        ThemePreset(String displayName, boolean dark, Color accentColor, Color tableBg, Color tableAltRowBg) {
            this.displayName = displayName;
            this.dark = dark;
            this.accentColor = accentColor;
            this.tableBg = tableBg;
            this.tableAltRowBg = tableAltRowBg;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isDark() {
            return dark;
        }

        public Color getAccentColor() {
            return accentColor;
        }

        public Color getTableBg() {
            return tableBg;
        }

        public Color getTableAltRowBg() {
            return tableAltRowBg;
        }
    }

    private static boolean isDarkMode = true;
    private static ThemePreset currentPreset = ThemePreset.MIDNIGHT_BLUE;
    private static float uiScaleFactor = 1.0f;
    private static int baseFontSize = 13;

    public interface ThemeChangeListener {
        void onThemeChanged(boolean isDark);
    }

    private static final List<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

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

    public static ThemePreset getCurrentPreset() {
        return currentPreset;
    }

    public static float getUiScaleFactor() {
        return uiScaleFactor;
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
        return currentPreset != null ? currentPreset.getAccentColor()
                : (isDarkMode ? new Color(100, 181, 246) : new Color(0, 102, 180));
    }

    public static Color getSuccessColor() {
        return isDarkMode ? new Color(74, 222, 128) : new Color(22, 163, 74);
    }

    public static Color getDangerColor() {
        return isDarkMode ? new Color(248, 113, 113) : new Color(220, 38, 38);
    }

    public static Color getMutedTextColor() {
        return isDarkMode ? new Color(156, 163, 175) : new Color(107, 114, 128);
    }

    public static Color getTableBackgroundColor() {
        return currentPreset != null ? currentPreset.getTableBg()
                : (isDarkMode ? new Color(18, 22, 31) : Color.WHITE);
    }

    public static Color getZebraAlternateColor() {
        return currentPreset != null ? currentPreset.getTableAltRowBg()
                : (isDarkMode ? new Color(28, 32, 42) : new Color(246, 248, 252));
    }

    public static Color getRowHoverColor() {
        return isDarkMode ? new Color(45, 58, 78) : new Color(226, 238, 255);
    }

    public static void toggleTheme(JButton toggleButton) {
        if (isDarkMode) {
            setPreset(ThemePreset.SOLAR_LIGHT, toggleButton);
        } else {
            setPreset(ThemePreset.MIDNIGHT_BLUE, toggleButton);
        }
    }

    public static void setPreset(ThemePreset preset, JButton toggleButton) {
        if (preset == null) return;
        currentPreset = preset;
        isDarkMode = preset.isDark();
        applyTheme(toggleButton);
    }

    public static void setUiScaleFactor(float scaleFactor, JButton toggleButton) {
        uiScaleFactor = Math.max(0.85f, Math.min(1.35f, scaleFactor));
        applyTheme(toggleButton);
    }

    public static void showThemePopupMenu(Component invoker, JButton toggleButton) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem header = new JMenuItem("🎨 Appearance & Accent Theme");
        header.setEnabled(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        menu.add(header);
        menu.addSeparator();

        for (ThemePreset preset : ThemePreset.values()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(preset.getDisplayName(), preset == currentPreset);
            item.addActionListener(e -> setPreset(preset, toggleButton));
            menu.add(item);
        }

        menu.addSeparator();
        JMenuItem scaleHeader = new JMenuItem("🔍 Interface Zoom / Font Scale");
        scaleHeader.setEnabled(false);
        scaleHeader.setFont(scaleHeader.getFont().deriveFont(Font.BOLD));
        menu.add(scaleHeader);

        float[] scales = {0.9f, 1.0f, 1.1f, 1.25f};
        String[] labels = {"90% (Compact)", "100% (Default)", "110% (Comfortable)", "125% (Large HD)"};
        for (int i = 0; i < scales.length; i++) {
            final float sc = scales[i];
            boolean active = Math.abs(uiScaleFactor - sc) < 0.03f;
            JCheckBoxMenuItem scaleItem = new JCheckBoxMenuItem(labels[i], active);
            scaleItem.addActionListener(e -> setUiScaleFactor(sc, toggleButton));
            menu.add(scaleItem);
        }

        menu.show(invoker, 0, invoker.getHeight());
    }

    public static void init(JButton toggleButton) {
        applyTheme(toggleButton);
    }

    private static void applyTheme(JButton toggleButton) {
        try {
            if (isDarkMode) {
                UIManager.setLookAndFeel(new FlatMacDarkLaf());
                UIManager.put("ScrollBar.thumb", new ColorUIResource(new Color(95, 105, 125)));
                UIManager.put("ScrollBar.hoverThumb", new ColorUIResource(new Color(140, 155, 180)));
                UIManager.put("ScrollBar.pressedThumb", new ColorUIResource(currentPreset.getAccentColor()));
                UIManager.put("ScrollBar.track", new ColorUIResource(new Color(22, 25, 32)));
                if (toggleButton != null) toggleButton.setText("🎨 Theme ▾");
            } else {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
                UIManager.put("ScrollBar.thumb", new ColorUIResource(new Color(165, 175, 190)));
                UIManager.put("ScrollBar.hoverThumb", new ColorUIResource(new Color(115, 125, 145)));
                UIManager.put("ScrollBar.pressedThumb", new ColorUIResource(currentPreset.getAccentColor()));
                UIManager.put("ScrollBar.track", new ColorUIResource(new Color(245, 247, 250)));
                if (toggleButton != null) toggleButton.setText("🎨 Theme ▾");
            }

            // Sleek modern borderless pill scrollbars (8px, no clunky arrow buttons)
            UIManager.put("ScrollBar.width", 9);
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.trackArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

            // Sleek thin split-pane dividers (6px with subtle grip)
            UIManager.put("SplitPane.dividerSize", 6);
            UIManager.put("SplitPane.centerOneTouchButtons", true);
            UIManager.put("SplitPane.oneTouchButtonSize", 14);
            UIManager.put("SplitPane.oneTouchButtonOffset", 4);
            UIManager.put("SplitPaneDivider.style", "grip");

            // Modern rounded corners for buttons, inputs, and tabs
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("CheckBox.arc", 5);
            UIManager.put("TabbedPane.tabArc", 8);
            UIManager.put("TabbedPane.cardTabArc", 8);
            UIManager.put("TabbedPane.selectedBackground", new ColorUIResource(isDarkMode ? new Color(36, 44, 62) : new Color(232, 242, 255)));
            UIManager.put("Component.focusColor", new ColorUIResource(currentPreset.getAccentColor()));

            // TablePlus-style table polish (MUST use ColorUIResource so Swing updates existing JTables on theme switch!)
            ColorUIResource tableBg = new ColorUIResource(currentPreset.getTableBg());
            ColorUIResource tableFg = new ColorUIResource(isDarkMode ? new Color(226, 232, 240) : new Color(15, 23, 42));
            ColorUIResource tableAltBg = new ColorUIResource(currentPreset.getTableAltRowBg());
            ColorUIResource gridCol = new ColorUIResource(isDarkMode ? new Color(42, 48, 60) : new Color(226, 232, 240));
            ColorUIResource selBg = new ColorUIResource(isDarkMode ? new Color(30, 74, 130) : new Color(191, 219, 254));
            ColorUIResource selFg = new ColorUIResource(isDarkMode ? Color.WHITE : new Color(15, 23, 42));
            ColorUIResource headerBg = new ColorUIResource(isDarkMode ? new Color(26, 32, 44) : new Color(241, 245, 249));
            ColorUIResource headerFg = new ColorUIResource(isDarkMode ? new Color(226, 232, 240) : new Color(30, 41, 59));

            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", true);
            UIManager.put("Table.rowHeight", Math.round(26 * uiScaleFactor));
            UIManager.put("Table.cellMargins", new Insets(3, 8, 3, 8));
            UIManager.put("Table.gridColor", gridCol);
            UIManager.put("Table.background", tableBg);
            UIManager.put("Table.foreground", tableFg);
            UIManager.put("Table.alternateRowColor", tableAltBg);
            UIManager.put("Table.selectionBackground", selBg);
            UIManager.put("Table.selectionForeground", selFg);
            UIManager.put("TableHeader.height", Math.round(30 * uiScaleFactor));
            UIManager.put("TableHeader.background", headerBg);
            UIManager.put("TableHeader.foreground", headerFg);
            UIManager.put("TableHeader.bottomSeparatorColor", new ColorUIResource(currentPreset.getAccentColor()));
            UIManager.put("Viewport.background", tableBg);
            UIManager.put("ScrollPane.background", tableBg);

            // Apply UI Font Scale if needed
            applyFontScaling();

            // Ensure smooth dropdown & popup behavior on Ubuntu/Linux (X11/Wayland)
            JPopupMenu.setDefaultLightWeightPopupEnabled(true);
            UIManager.put("ComboBox.maximumRowCount", 14);
            UIManager.put("PopupMenu.consumeEventOnClose", false);
            UIManager.put("ComboBox.showPopupOnNavigation", false);

            FlatLaf.updateUI();

            for (ThemeChangeListener l : listeners) {
                try {
                    l.onThemeChanged(isDarkMode);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
                syncComponentTreeThemeColors(window);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Recursively synchronizes JTable, JTableHeader, and JViewport theme colors across
     * live component hierarchies when switching between Dark and Light modes.
     */
    public static void syncComponentTreeThemeColors(Component c) {
        if (c == null) return;
        if (c instanceof JTable) {
            JTable table = (JTable) c;
            table.setBackground(new ColorUIResource(getTableBackgroundColor()));
            table.setForeground(new ColorUIResource(isDarkMode ? new Color(226, 232, 240) : new Color(15, 23, 42)));
            table.setGridColor(new ColorUIResource(isDarkMode ? new Color(42, 48, 60) : new Color(226, 232, 240)));
            table.setSelectionBackground(new ColorUIResource(isDarkMode ? new Color(30, 74, 130) : new Color(191, 219, 254)));
            table.setSelectionForeground(new ColorUIResource(isDarkMode ? Color.WHITE : new Color(15, 23, 42)));
            table.setRowHeight(Math.max(24, Math.round(26 * uiScaleFactor)));
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                header.setBackground(new ColorUIResource(isDarkMode ? new Color(26, 32, 44) : new Color(241, 245, 249)));
                header.setForeground(new ColorUIResource(isDarkMode ? new Color(226, 232, 240) : new Color(30, 41, 59)));
                header.repaint();
            }
            table.repaint();
        } else if (c instanceof JViewport) {
            JViewport vp = (JViewport) c;
            Component view = vp.getView();
            if (view instanceof JTable) {
                vp.setBackground(new ColorUIResource(getTableBackgroundColor()));
            }
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                syncComponentTreeThemeColors(child);
            }
        }
    }

    /**
     * Returns a composite UI font that preserves OS emoji and symbol fallback chains
     * (Segoe UI Emoji / Segoe UI Symbol / Apple Color Emoji / Noto Emoji) instead of
     * stripping fallbacks like {@code new Font("Segoe UI", ...)} does.
     */
    public static Font getUiFont(int style, int size) {
        int scaled = Math.max(10, Math.round(size * uiScaleFactor));
        Font base = UIManager.getFont("defaultFont");
        String family = base != null ? base.getFamily() : "Segoe UI";
        return FontUtils.getCompositeFont(family, style, scaled);
    }

    private static void applyFontScaling() {
        int scaledSize = Math.max(11, Math.round(baseFontSize * uiScaleFactor));
        Font defaultFont = UIManager.getFont("defaultFont");
        if (defaultFont != null) {
            Font composite = FontUtils.getCompositeFont(defaultFont.getFamily(), defaultFont.getStyle(), scaledSize);
            UIManager.put("defaultFont", new FontUIResource(composite));
        }
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        for (Object key : Collections.list(keys)) {
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                FontUIResource orig = (FontUIResource) value;
                Font composite = FontUtils.getCompositeFont(orig.getFamily(), orig.getStyle(), scaledSize);
                UIManager.put(key, new FontUIResource(composite));
            }
        }
    }
}
