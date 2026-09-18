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
    private final JTextField colSearchField = new JTextField(14);
    private final JTextArea statsArea = new JTextArea(6, 30);
    private Consumer<String> onTableSelected;
    private DefaultMutableTreeNode masterRoot = null;

    public SchemaPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(230, 0));

        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        // Table Search bar
        JPanel tableSearchPanel = new JPanel(new BorderLayout());
        tableSearchPanel.add(new JLabel("🔍 Tbl: "), BorderLayout.WEST);
        tableSearchPanel.add(searchField, BorderLayout.CENTER);
        JButton refreshBtn = new JButton("↺");
        refreshBtn.setToolTipText("Refresh schema");
        refreshBtn.addActionListener(e -> refresh());
        tableSearchPanel.add(refreshBtn, BorderLayout.EAST);

        // Column Search bar
        JPanel colSearchPanel = new JPanel(new BorderLayout());
        colSearchPanel.add(new JLabel("🔍 Col: "), BorderLayout.WEST);
        colSearchPanel.add(colSearchField, BorderLayout.CENTER);

        JPanel searchContainer = new JPanel();
        searchContainer.setLayout(new BoxLayout(searchContainer, BoxLayout.Y_AXIS));
        searchContainer.add(tableSearchPanel);
        searchContainer.add(colSearchPanel);

        // Filter tree on type
        javax.swing.event.DocumentListener filterListener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTree(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTree(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTree(); }
        };
        searchField.getDocument().addDocumentListener(filterListener);
        colSearchField.getDocument().addDocumentListener(filterListener);

        // Stats area (bottom)
        statsArea.setEditable(false);
        statsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        statsArea.setText("Click a table to see stats.");
        JScrollPane statsScroll = new JScrollPane(statsArea);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tree), statsScroll);
        split.setResizeWeight(0.7);

        add(searchContainer, BorderLayout.NORTH);
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
            masterRoot = null;
            treeModel.reload();
            return;
        }
        try {
            masterRoot = new DefaultMutableTreeNode();
            String dbName = dbManager.connection.getCatalog();
            String rootName = dbName != null ? dbName : "Database";
            masterRoot.setUserObject(rootName);
            root.setUserObject(rootName);
            
            List<String> tables = dbManager.getTableNames();
            for (String tbl : tables) {
                DefaultMutableTreeNode tableNode = new DefaultMutableTreeNode(tbl);
                for (String col : dbManager.getColumnNames(tbl)) {
                    tableNode.add(new DefaultMutableTreeNode("  " + col));
                }
                masterRoot.add(tableNode);
            }
            applyFilter();
        } catch (Exception ex) {
            root.setUserObject("Error: " + ex.getMessage());
            treeModel.reload();
        }
    }

    private void filterTree() {
        applyFilter();
    }

    private void applyFilter() {
        if (masterRoot == null) return;
        
        String tableFilter = searchField.getText().toLowerCase().trim();
        String colFilter = colSearchField.getText().toLowerCase().trim();
        root.removeAllChildren();
        
        for (int i = 0; i < masterRoot.getChildCount(); i++) {
            DefaultMutableTreeNode masterNode = (DefaultMutableTreeNode) masterRoot.getChildAt(i);
            String tableName = masterNode.getUserObject().toString().toLowerCase();
            
            // Check table name match
            boolean tableMatch = tableFilter.isEmpty() || tableName.contains(tableFilter);
            
            if (tableMatch) {
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(masterNode.getUserObject());
                boolean hasMatchingCol = false;
                for (int j = 0; j < masterNode.getChildCount(); j++) {
                    DefaultMutableTreeNode masterColNode = (DefaultMutableTreeNode) masterNode.getChildAt(j);
                    String colName = masterColNode.getUserObject().toString().toLowerCase();
                    
                    if (colFilter.isEmpty() || colName.contains(colFilter)) {
                        newNode.add(new DefaultMutableTreeNode(masterColNode.getUserObject()));
                        hasMatchingCol = true;
                    }
                }
                
                // If we are filtering by column, ONLY include this table if it has a matching column
                if (!colFilter.isEmpty() && !hasMatchingCol) {
                    continue; // Skip this table since it has no matching columns
                }
                
                root.add(newNode);
            }
        }
        treeModel.reload();
        tree.expandRow(0);
        
        if (!colFilter.isEmpty()) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        }
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
