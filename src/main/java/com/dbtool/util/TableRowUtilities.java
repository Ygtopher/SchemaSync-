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
        rowHeader.setBackground(UIManager.getColor("TableHeader.background"));
        rowHeader.setForeground(UIManager.getColor("TableHeader.foreground"));
        
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
                    label.setBackground(UIManager.getColor("TableHeader.background"));
                    label.setForeground(UIManager.getColor("TableHeader.foreground"));
                    label.setFont(label.getFont().deriveFont(java.awt.Font.PLAIN));
                }
                label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.LIGHT_GRAY));
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
    }
}
