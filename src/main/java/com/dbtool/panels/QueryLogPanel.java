package com.dbtool.panels;

import com.dbtool.util.QueryLogger;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class QueryLogPanel extends JPanel {
    private DefaultTableModel model;
    private JTable table;

    public QueryLogPanel() {
        setLayout(new BorderLayout());
        
        String[] columns = {"Time", "Status", "Duration (ms)", "Query"};
        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        
        table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setMaxWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(800);
        
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String status = (String) table.getModel().getValueAt(row, 1);
                if (!isSelected) {
                    if ("Error".equals(status)) {
                        c.setForeground(new Color(200, 0, 0));
                    } else {
                        c.setForeground(UIManager.getColor("Table.foreground"));
                    }
                }
                return c;
            }
        });
        
        JPopupMenu popup = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy Query");
        copyItem.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r >= 0) {
                String q = (String) table.getValueAt(r, 3);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(q), null);
            }
        });
        popup.add(copyItem);
        
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int r = table.rowAtPoint(e.getPoint());
                    if (r >= 0 && r < table.getRowCount()) {
                        table.setRowSelectionInterval(r, r);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
        
        QueryLogger.addListener(() -> {
            SwingUtilities.invokeLater(this::refresh);
        });
    }
    
    private void refresh() {
        model.setRowCount(0);
        List<QueryLogger.LogEntry> logs = QueryLogger.getLogs();
        for (QueryLogger.LogEntry log : logs) {
            model.addRow(new Object[]{
                log.timestamp,
                log.success ? "Success" : "Error",
                log.durationMs,
                log.query
            });
        }
        if (table.getRowCount() > 0) {
            table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, true));
        }
    }
}
