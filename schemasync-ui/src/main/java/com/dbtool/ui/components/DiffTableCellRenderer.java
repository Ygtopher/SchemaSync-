package com.dbtool.ui.components;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class DiffTableCellRenderer extends DefaultTableCellRenderer {

    private static final Color LIGHT_ADDED_BG = new Color(232, 245, 233);
    private static final Color LIGHT_REMOVED_BG = new Color(255, 235, 238);
    private static final Color LIGHT_MODIFIED_BG = new Color(255, 248, 225);
    private static final Color LIGHT_CELL_DIFF_BG = new Color(255, 224, 130);

    private static final Color DARK_ADDED_BG = new Color(27, 54, 34);
    private static final Color DARK_REMOVED_BG = new Color(60, 25, 25);
    private static final Color DARK_MODIFIED_BG = new Color(55, 48, 20);
    private static final Color DARK_CELL_DIFF_BG = new Color(85, 70, 20);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

        if (isSelected) {
            return c;
        }

        boolean isDark = isDarkTheme(table);
        Color addedBg = isDark ? DARK_ADDED_BG : LIGHT_ADDED_BG;
        Color removedBg = isDark ? DARK_REMOVED_BG : LIGHT_REMOVED_BG;
        Color modifiedBg = isDark ? DARK_MODIFIED_BG : LIGHT_MODIFIED_BG;
        Color cellDiffBg = isDark ? DARK_CELL_DIFF_BG : LIGHT_CELL_DIFF_BG;

        // Check overall row diff type from status column (usually column 0)
        String rowStatus = "";
        try {
            Object statusObj = table.getValueAt(row, 0);
            if (statusObj != null) {
                rowStatus = statusObj.toString().toUpperCase();
            }
        } catch (Exception ignored) {}

        if (rowStatus.contains("ADDED") || rowStatus.contains("NEW") || rowStatus.contains("ONLY IN B")) {
            c.setBackground(addedBg);
        } else if (rowStatus.contains("REMOVED") || rowStatus.contains("DELETED") || rowStatus.contains("ONLY IN A") || rowStatus.contains("MISSING")) {
            c.setBackground(removedBg);
        } else if (rowStatus.contains("MODIFIED") || rowStatus.contains("DIFF")) {
            c.setBackground(modifiedBg);
        } else {
            c.setBackground(row % 2 == 0 ? table.getBackground() : getAlternateColor(table.getBackground(), isDark));
        }

        // Highlight cells that contain change arrow "➔"
        String text = value != null ? value.toString() : "";
        if (text.contains("➔") || text.contains("->")) {
            c.setBackground(cellDiffBg);
            c.setFont(c.getFont().deriveFont(Font.BOLD));
            if (!isDark) {
                c.setForeground(new Color(130, 60, 0));
            } else {
                c.setForeground(new Color(255, 200, 100));
            }
        } else {
            c.setFont(c.getFont().deriveFont(Font.PLAIN));
            c.setForeground(table.getForeground());
        }

        if (value == null) {
            setText("NULL");
            setFont(c.getFont().deriveFont(Font.ITALIC));
            setForeground(Color.GRAY);
        }

        return c;
    }

    private static boolean isDarkTheme(Component c) {
        Color bg = c.getBackground();
        if (bg == null) return false;
        double luma = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue());
        return luma < 128;
    }

    private static Color getAlternateColor(Color bg, boolean isDark) {
        if (bg == null) return isDark ? new Color(40, 40, 40) : new Color(245, 245, 245);
        int delta = isDark ? 8 : -8;
        int r = Math.max(0, Math.min(255, bg.getRed() + delta));
        int g = Math.max(0, Math.min(255, bg.getGreen() + delta));
        int b = Math.max(0, Math.min(255, bg.getBlue() + delta));
        return new Color(r, g, b);
    }
}
