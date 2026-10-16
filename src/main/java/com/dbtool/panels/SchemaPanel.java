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

        // Double-click → load table in Browse; Single-click (debounced) → show fast stats
        final javax.swing.Timer[] statsDebounceTimer = new javax.swing.Timer[1];
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (statsDebounceTimer[0] != null) {
                        statsDebounceTimer[0].stop();
                    }
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
                        final String targetTable = tableName;
                        if (statsDebounceTimer[0] != null) {
                            statsDebounceTimer[0].stop();
                        }
                        statsDebounceTimer[0] = new javax.swing.Timer(260, ev -> showTableStats(targetTable));
                        statsDebounceTimer[0].setRepeats(false);
                        statsDebounceTimer[0].start();
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
        if (dbManager == null || dbManager.connection == null) {
            masterRoot = null;
            root.setUserObject("Database");
            treeModel.reload();
            return;
        }
        root.setUserObject("Loading schema...");
        treeModel.reload();

        SwingWorker<DefaultMutableTreeNode, Void> worker = new SwingWorker<DefaultMutableTreeNode, Void>() {
            @Override
            protected DefaultMutableTreeNode doInBackground() throws Exception {
                DefaultMutableTreeNode newMaster = new DefaultMutableTreeNode();
                String dbName = null;
                try {
                    dbName = dbManager.connection.getCatalog();
                } catch (Exception ignored) {}
                String rootName = dbName != null ? dbName : "Database";
                newMaster.setUserObject(rootName);

                dbManager.preloadAllColumns();
                dbManager.preloadAllForeignKeys();

                List<String> tables = dbManager.getTableNames();
                for (String tbl : tables) {
                    DefaultMutableTreeNode tableNode = new DefaultMutableTreeNode(tbl);
                    for (String col : dbManager.getColumnNames(tbl)) {
                        tableNode.add(new DefaultMutableTreeNode("  " + col));
                    }
                    newMaster.add(tableNode);
                }
                return newMaster;
            }

            @Override
            protected void done() {
                try {
                    masterRoot = get();
                    root.setUserObject(masterRoot.getUserObject());
                    applyFilter();
                } catch (Exception ex) {
                    root.setUserObject("Error: " + ex.getMessage());
                    treeModel.reload();
                }
            }
        };
        worker.execute();
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
                String cleanTable = tableName.contains(".") ? tableName.split("\\.")[1] : tableName;
                if (dbManager.isPostgres()) {
                    long rows = -1;
                    String prettySize = null;
                    try (Statement st = dbManager.connection.createStatement();
                         ResultSet rs = st.executeQuery(
                                 "SELECT COALESCE(n_live_tup, -1), pg_size_pretty(pg_total_relation_size(relid)) " +
                                 "FROM pg_stat_user_tables WHERE relname = '" + cleanTable.replace("'", "''") + "' LIMIT 1")) {
                        if (rs.next()) {
                            rows = rs.getLong(1);
                            prettySize = rs.getString(2);
                        }
                    } catch (Exception ignored) {}
                    if (rows >= 0) sb.append("Rows (est): ").append(rows).append("\n");
                    if (prettySize != null) sb.append("Size: ").append(prettySize).append("\n");
                    try (Statement st = dbManager.connection.createStatement();
                         ResultSet idxRs = st.executeQuery("SELECT indexname FROM pg_indexes WHERE tablename = '" + cleanTable.replace("'", "''") + "'")) {
                        sb.append("Indexes:\n");
                        while (idxRs.next()) sb.append("  • ").append(idxRs.getString(1)).append("\n");
                    } catch (Exception ignored) {}
                } else if (dbManager.isMySQL()) {
                    try (Statement st = dbManager.connection.createStatement();
                         ResultSet rs = st.executeQuery(
                                 "SELECT COALESCE(table_rows, 0), COALESCE(data_length + index_length, 0) " +
                                 "FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '" + cleanTable.replace("'", "''") + "' LIMIT 1")) {
                        if (rs.next()) {
                            sb.append("Rows (est): ").append(rs.getLong(1)).append("\n");
                            sb.append("Size: ").append(rs.getLong(2) / 1024).append(" KB\n");
                        }
                    } catch (Exception ignored) {}
                } else {
                    try (Statement st = dbManager.connection.createStatement();
                         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + dbManager.quoteTableName(tableName))) {
                        if (rs.next()) sb.append("Rows: ").append(rs.getLong(1)).append("\n");
                    } catch (Exception ignored) {}
                }

                // Foreign keys from in-memory fkCache (instant, 0 SQL queries when preloaded)
                java.util.Map<String, String[]> fks = dbManager.getForeignKeys(tableName);
                if (fks != null && !fks.isEmpty()) {
                    sb.append("Foreign Keys:\n");
                    for (java.util.Map.Entry<String, String[]> fk : fks.entrySet()) {
                        String[] ref = fk.getValue();
                        if (ref != null && ref.length >= 2) {
                            sb.append("  • ").append(fk.getKey()).append(" → ").append(ref[0]).append(".").append(ref[1]).append("\n");
                        }
                    }
                }
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
        menu.addSeparator();

        JMenuItem codeGenItem = new JMenuItem("💻 Generate Entity / ORM Code...");
        codeGenItem.addActionListener(ev -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new EntityCodeGeneratorDialog(owner, dbManager, tbl).setVisible(true);
        });
        menu.add(codeGenItem);

        JMenuItem mockDataItem = new JMenuItem("🧪 Generate Mock Test Data...");
        mockDataItem.addActionListener(ev -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new MockDataGeneratorDialog(owner, dbManager, tbl, this::refresh).setVisible(true);
        });
        menu.add(mockDataItem);

        JMenuItem importItem = new JMenuItem("📥 Import Data (CSV / Excel)...");
        importItem.addActionListener(ev -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new DataImporterDialog(owner, dbManager, tbl, this::refresh).setVisible(true);
        });
        menu.add(importItem);

        JMenuItem indexAdvisorItem = new JMenuItem("⚡ Table Index & Performance Advisor...");
        indexAdvisorItem.addActionListener(ev -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new IndexAdvisorDialog(owner, dbManager, tbl).setVisible(true);
        });
        menu.add(indexAdvisorItem);

        JMenuItem maskDataItem = new JMenuItem("🔒 Mask & Anonymize Data...");
        maskDataItem.addActionListener(ev -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new DataMaskingDialog(owner, dbManager, tbl).setVisible(true);
        });
        menu.add(maskDataItem);

        menu.show(tree, e.getX(), e.getY());
    }
}
