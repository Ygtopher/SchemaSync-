package com.dbtool.util;

import javax.swing.*;
import java.awt.*;

public class TableRowUtilities {
    public static void addRowNumbers(JTable table, JScrollPane scrollPane) {
        JList<String> rowHeader = new JList<>(new AbstractListModel<String>() {
            @Override
            public int getSize() {
                return table.getRowCount();
            }
            @Override
            public String getElementAt(int index) {
                return String.valueOf(index + 1);
            }
        });

        rowHeader.setFixedCellHeight(table.getRowHeight());
        rowHeader.setOpaque(true);
        Color hdrBg = UIManager.getColor("TableHeader.background");
        Color hdrFg = UIManager.getColor("TableHeader.foreground");
        if (hdrBg != null) rowHeader.setBackground(new javax.swing.plaf.ColorUIResource(hdrBg));
        if (hdrFg != null) rowHeader.setForeground(new javax.swing.plaf.ColorUIResource(hdrFg));

        table.addPropertyChangeListener("rowHeight", evt -> {
            rowHeader.setFixedCellHeight(table.getRowHeight());
            rowHeader.repaint();
        });

        rowHeader.setSelectionModel(table.getSelectionModel());
        rowHeader.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                if (isSelected) {
                    label.setBackground(table.getSelectionBackground());
                    label.setForeground(table.getSelectionForeground());
                    label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
                } else {
                    Color bg = UIManager.getColor("TableHeader.background");
                    Color fg = UIManager.getColor("TableHeader.foreground");
                    label.setBackground(bg != null ? bg : (ThemeManager.isDarkMode() ? new Color(26, 32, 44) : new Color(241, 245, 249)));
                    label.setForeground(fg != null ? fg : (ThemeManager.isDarkMode() ? new Color(226, 232, 240) : new Color(30, 41, 59)));
                    label.setFont(label.getFont().deriveFont(java.awt.Font.PLAIN));
                }
                Color borderCol = UIManager.getColor("Table.gridColor");
                if (borderCol == null) {
                    borderCol = ThemeManager.isDarkMode() ? new Color(42, 48, 60) : new Color(226, 232, 240);
                }
                label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, borderCol));
                return label;
            }
        });

        // Ensure row header updates when table rows change
        table.getModel().addTableModelListener(e -> {
            rowHeader.setModel(new AbstractListModel<String>() {
                @Override
                public int getSize() {
                    return table.getRowCount();
                }
                @Override
                public String getElementAt(int index) {
                    return String.valueOf(index + 1);
                }
            });
            int maxDigits = String.valueOf(table.getRowCount()).length();
            rowHeader.setFixedCellWidth(Math.max(50, maxDigits * 12 + 20));
        });

        // Also listen to row sorter changes (if user filters the table, row count changes)
        if (table.getRowSorter() != null) {
            table.getRowSorter().addRowSorterListener(e -> {
                rowHeader.updateUI();
            });
        }

        int maxDigits = String.valueOf(table.getRowCount()).length();
        rowHeader.setFixedCellWidth(Math.max(50, maxDigits * 12 + 20));

        scrollPane.setRowHeaderView(rowHeader);
        if (scrollPane.getViewport() != null) {
            scrollPane.getViewport().setBackground(new javax.swing.plaf.ColorUIResource(ThemeManager.getTableBackgroundColor()));
        }
    }
}
