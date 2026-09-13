package com.dbtool.panels;

import com.dbtool.DatabaseManager;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.List;
import java.util.function.Consumer;

public class SchemaPanel extends JPanel {

    private final DatabaseManager dbManager;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Database");
    private final JTree tree;
    private final JTextField searchField = new JTextField(14);
    private final JTextArea statsArea = new JTextArea(6, 30);
    private Consumer<String> onTableSelected;

    public SchemaPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(230, 0));

        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        // Search bar
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(new JLabel("🔍 "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JButton refreshBtn = new JButton("↺");
        refreshBtn.setToolTipText("Refresh schema");
        refreshBtn.addActionListener(e -> refresh());
        searchPanel.add(refreshBtn, BorderLayout.EAST);

        // Filter tree on type
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTree(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTree(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTree(); }
        });

        // Stats area (bottom)
        statsArea.setEditable(false);
        statsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        statsArea.setText("Click a table to see stats.");
        JScrollPane statsScroll = new JScrollPane(statsArea);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tree), statsScroll);
        split.setResizeWeight(0.7);

        add(searchPanel, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // Double-click → load table in Browse
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null && path.getPathCount() == 2) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        String tableName = node.getUserObject().toString();
                        // Strip row count suffix if present
                        if (tableName.contains(" (")) tableName = tableName.substring(0, tableName.indexOf(" ("));
                        if (onTableSelected != null) onTableSelected.accept(tableName);
                    }
                } else if (e.getClickCount() == 1) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null && path.getPathCount() == 2) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        String tableName = node.getUserObject().toString();
                        if (tableName.contains(" (")) tableName = tableName.substring(0, tableName.indexOf(" ("));
                        showTableStats(tableName);
                    }
                }
            }
        });

        // Right-click context menu
        tree.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
        });
    }

    public void setOnTableSelected(Consumer<String> handler) {
        this.onTableSelected = handler;
    }

    public void refresh() {
        root.removeAllChildren();
        if (dbManager.connection == null) {
            treeModel.reload();
            return;
        }
        try {
            String dbName = dbManager.connection.getCatalog();
            root.setUserObject(dbName != null ? dbName : "Database");
            List<String> tables = dbManager.getTableNames();
            for (String tbl : tables) {
                DefaultMutableTreeNode tableNode = new DefaultMutableTreeNode(tbl);
                // Add columns as children
                for (String col : dbManager.getColumnNames(tbl)) {
                    tableNode.add(new DefaultMutableTreeNode("  " + col));
                }
                root.add(tableNode);
            }
            treeModel.reload();
            // Expand all top-level nodes
            for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        } catch (Exception ex) {
            root.setUserObject("Error: " + ex.getMessage());
            treeModel.reload();
        }
    }

    private void filterTree() {
        String filter = searchField.getText().toLowerCase().trim();
        refresh();
        if (filter.isEmpty()) return;
        // Remove non-matching table nodes
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            String name = node.getUserObject().toString().toLowerCase();
            if (!name.contains(filter)) root.remove(i);
        }
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void showTableStats(String tableName) {
        if (dbManager.connection == null) return;
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                // Row count
                ResultSet rs = dbManager.connection.createStatement()
                        .executeQuery("SELECT COUNT(*) FROM " + dbManager.quoteTableName(tableName));
                if (rs.next()) sb.append("Rows: ").append(rs.getLong(1)).append("\n");

                // Disk size (PostgreSQL only)
                try {
                    ResultSet sizeRs = dbManager.connection.createStatement()
                            .executeQuery("SELECT pg_size_pretty(pg_total_relation_size('" + tableName + "'))");
                    if (sizeRs.next()) sb.append("Size: ").append(sizeRs.getString(1)).append("\n");
                } catch (Exception ignored) {}

                // Indexes
                try {
                    ResultSet idxRs = dbManager.connection.createStatement()
                            .executeQuery("SELECT indexname FROM pg_indexes WHERE tablename = '" + tableName + "'");
                    sb.append("Indexes:\n");
                    while (idxRs.next()) sb.append("  • ").append(idxRs.getString(1)).append("\n");
                } catch (Exception ignored) {}

                // Foreign keys
                try {
                    ResultSet fkRs = dbManager.connection.createStatement().executeQuery(
                            "SELECT kcu.column_name, ccu.table_name, ccu.column_name AS foreign_col " +
                            "FROM information_schema.table_constraints tc " +
                            "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                            "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name " +
                            "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_name='" + tableName + "'");
                    sb.append("Foreign Keys:\n");
                    while (fkRs.next()) sb.append("  • ").append(fkRs.getString(1))
                            .append(" → ").append(fkRs.getString(2)).append(".").append(fkRs.getString(3)).append("\n");
                } catch (Exception ignored) {}

            } catch (Exception ex) {
                sb.append("Error: ").append(ex.getMessage());
            }
            String text = sb.toString();
            SwingUtilities.invokeLater(() -> statsArea.setText(text.isEmpty() ? "(No stats available)" : text));
        }).start();
    }

    private void showContextMenu(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null || path.getPathCount() != 2) return;
        tree.setSelectionPath(path);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        String tableName = node.getUserObject().toString();
        if (tableName.contains(" (")) tableName = tableName.substring(0, tableName.indexOf(" ("));
        final String tbl = tableName;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem openItem = new JMenuItem("Browse Table");
        JMenuItem statsItem = new JMenuItem("Show Stats");
        openItem.addActionListener(ev -> { if (onTableSelected != null) onTableSelected.accept(tbl); });
        statsItem.addActionListener(ev -> showTableStats(tbl));
        menu.add(openItem);
        menu.add(statsItem);
        menu.show(tree, e.getX(), e.getY());
    }
}
