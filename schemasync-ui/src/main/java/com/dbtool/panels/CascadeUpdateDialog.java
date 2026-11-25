package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.CascadeUpdatePlan;
import com.dbtool.DatabaseManager.CascadeUpdateRowNode;
import com.dbtool.DatabaseManager.CascadeUpdateTableMapping;
import com.dbtool.DatabaseManager.PreparedUpdate;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;

/**
 * Enterprise Multi-Table Cascade Update Studio.
 * Enables updating attributes (e.g. first_name) or primary/foreign key IDs
 * across all related database tables linked by foreign keys and relational constraints
 * inside an atomic, rollback-safe transaction.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class CascadeUpdateDialog extends JDialog {

    private final DatabaseManager dbManager;
    private CascadeUpdatePlan plan;
    private final Runnable onSuccessCallback;

    // Top configuration bar
    private JComboBox<String> tableCombo;
    private JComboBox<String> columnCombo;
    private JTextField filterField;
    private JTextField newValField;
    private JButton analyzeBtn;

    // Header Badges
    private JLabel totalBadge;
    private JLabel tableBadge;
    private JLabel modeBadge;

    // Main tabs
    private JTree dependencyTree;
    private DefaultTableModel recordDetailsModel;
    private JTable recordDetailsTable;
    private JLabel selectedNodeInfoLabel;

    private DefaultTableModel mappingTableModel;
    private JTable mappingTable;

    private RSyntaxTextArea sqlArea;
    private DefaultTableModel summaryModel;

    // Initial state
    private String currentTable;
    private List<Map<String, Object>> currentRows;
    private String currentColumn;
    private Object currentNewVal;

    public CascadeUpdateDialog(Frame owner, DatabaseManager dbManager, CascadeUpdatePlan initialPlan, Runnable onSuccessCallback) {
        super(owner, "⚡ Multi-Table Cascade Update & Linked Records Studio", true);
        this.dbManager = dbManager;
        this.plan = initialPlan;
        this.onSuccessCallback = onSuccessCallback;
        if (initialPlan != null) {
            this.currentTable = initialPlan.rootTable;
            this.currentRows = initialPlan.rootTargetRows;
            if (!initialPlan.fieldUpdates.isEmpty()) {
                this.currentColumn = initialPlan.fieldUpdates.keySet().iterator().next();
                this.currentNewVal = initialPlan.fieldUpdates.get(this.currentColumn);
            }
        }
        initDialog();
    }

    public CascadeUpdateDialog(Frame owner, DatabaseManager dbManager, String table,
                               List<Map<String, Object>> rows, String column, Object newVal,
                               Runnable onSuccessCallback) {
        super(owner, "⚡ Multi-Table Cascade Update & Linked Records Studio", true);
        this.dbManager = dbManager;
        this.currentTable = table;
        this.currentRows = rows != null ? rows : new ArrayList<>();
        this.currentColumn = column;
        this.currentNewVal = newVal;
        this.onSuccessCallback = onSuccessCallback;

        if (table != null && !this.currentRows.isEmpty() && column != null) {
            Map<String, Object> fieldUpdates = new LinkedHashMap<>();
            fieldUpdates.put(column, newVal);
            this.plan = dbManager.buildCascadeUpdatePlan(table, this.currentRows, fieldUpdates);
        }

        initDialog();
    }

    private void initDialog() {
        setSize(1020, 720);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout());

        initComponents();
        if (plan != null) {
            populateUIFromPlan();
        }
    }

    private void initComponents() {
        // --- 1. Top Header Banner ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        headerPanel.setBackground(ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(248, 250, 252));

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);
        JLabel titleLabel = new JLabel("⚡ Multi-Table Cascade Update & Linked Records Studio");
        titleLabel.setFont(ThemeManager.getUiFont(Font.BOLD, 16));

        JLabel subtitleLabel = new JLabel("Update attributes (e.g. first_name) or IDs across all related database tables linked by foreign keys in an atomic transaction.");
        subtitleLabel.setFont(ThemeManager.getUiFont(Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);

        titleBox.add(titleLabel);
        titleBox.add(subtitleLabel);

        JPanel badgesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        badgesPanel.setOpaque(false);

        totalBadge = new JLabel("  0 Records Affected  ");
        totalBadge.setOpaque(true);
        totalBadge.setBackground(new Color(16, 185, 129));
        totalBadge.setForeground(Color.WHITE);
        totalBadge.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        totalBadge.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        tableBadge = new JLabel("  0 Tables Involved  ");
        tableBadge.setOpaque(true);
        tableBadge.setBackground(new Color(59, 130, 246));
        tableBadge.setForeground(Color.WHITE);
        tableBadge.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        tableBadge.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        modeBadge = new JLabel("  Mode: Linked Attributes  ");
        modeBadge.setOpaque(true);
        modeBadge.setBackground(new Color(139, 92, 246));
        modeBadge.setForeground(Color.WHITE);
        modeBadge.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        modeBadge.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        badgesPanel.add(totalBadge);
        badgesPanel.add(tableBadge);
        badgesPanel.add(modeBadge);

        headerPanel.add(titleBox, BorderLayout.WEST);
        headerPanel.add(badgesPanel, BorderLayout.EAST);

        // --- 2. Top Control / Target Bar ---
        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        controlBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.isDarkMode() ? new Color(51, 65, 85) : new Color(226, 232, 240)),
                new EmptyBorder(4, 10, 4, 10)
        ));

        controlBar.add(new JLabel("Root Table:"));
        List<String> allTables = dbManager != null ? dbManager.getTableNames() : new ArrayList<>();
        tableCombo = new JComboBox<>(allTables.toArray(new String[0]));
        if (currentTable != null) tableCombo.setSelectedItem(currentTable);
        tableCombo.addActionListener(e -> updateColumnsForSelectedTable());
        controlBar.add(tableCombo);

        controlBar.add(new JLabel("Field to Update:"));
        columnCombo = new JComboBox<>();
        updateColumnsForSelectedTable();
        if (currentColumn != null) columnCombo.setSelectedItem(currentColumn);
        controlBar.add(columnCombo);

        controlBar.add(new JLabel("Target / Filter:"));
        String filterText = (currentRows != null && !currentRows.isEmpty())
                ? currentRows.size() + " selected record(s)"
                : "id = 1";
        filterField = new JTextField(filterText, 12);
        filterField.setToolTipText("Filter criteria or row count to target in the root table");
        controlBar.add(filterField);

        controlBar.add(new JLabel("New Value:"));
        newValField = new JTextField(currentNewVal != null ? currentNewVal.toString() : "", 14);
        newValField.setToolTipText("Proposed new value to cascade across linked tables");
        controlBar.add(newValField);

        analyzeBtn = new JButton("⚡ Analyze & Trace Linked Records");
        analyzeBtn.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        analyzeBtn.setBackground(new Color(59, 130, 246));
        analyzeBtn.setForeground(Color.WHITE);
        analyzeBtn.addActionListener(e -> executeAnalysisAndBuildPlan());
        controlBar.add(analyzeBtn);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(headerPanel, BorderLayout.NORTH);
        northContainer.add(controlBar, BorderLayout.SOUTH);
        add(northContainer, BorderLayout.NORTH);

        // --- 3. Main Center Tabs ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Dependency Tree & Field Diffs
        tabbedPane.addTab("🌳 Linked Dependency Tree & Preview", createTreeTab());

        // Tab 2: Column & Table Mapping Configurator
        tabbedPane.addTab("⚙️ Column & Table Mapping Configurator", createConfigTab());

        // Tab 3: Generated SQL Transaction Script
        tabbedPane.addTab("📜 Generated SQL Script", createScriptTab());

        // Tab 4: Affected Tables Breakdown
        tabbedPane.addTab("📊 Affected Tables Breakdown", createSummaryTab());

        add(tabbedPane, BorderLayout.CENTER);

        // --- 4. Bottom Button Bar ---
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonBar.setBorder(new EmptyBorder(6, 12, 10, 12));

        JButton copyScriptBtn = new JButton("📋 Copy SQL Script");
        copyScriptBtn.addActionListener(e -> {
            if (plan != null && plan.fullScript != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(plan.fullScript), null);
                JOptionPane.showMessageDialog(this, "SQL cascade update script copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JButton executeBtn = new JButton("⚡ Execute Cascade Update (Safe Transaction)");
        executeBtn.setFont(executeBtn.getFont().deriveFont(Font.BOLD));
        executeBtn.setBackground(new Color(16, 185, 129)); // emerald green
        executeBtn.setForeground(Color.WHITE);
        executeBtn.setFocusPainted(false);
        executeBtn.addActionListener(e -> executeCascadeUpdate());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonBar.add(copyScriptBtn);
        buttonBar.add(cancelBtn);
        buttonBar.add(executeBtn);

        add(buttonBar, BorderLayout.SOUTH);
    }

    private void updateColumnsForSelectedTable() {
        String tbl = (String) tableCombo.getSelectedItem();
        columnCombo.removeAllItems();
        if (tbl != null && dbManager != null) {
            List<String> cols = dbManager.getColumnNames(tbl);
            for (String col : cols) {
                columnCombo.addItem(col);
            }
        }
    }

    private JPanel createTreeTab() {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Root Update Target");
        dependencyTree = new JTree(rootNode);
        dependencyTree.setRootVisible(false);
        dependencyTree.setShowsRootHandles(true);

        dependencyTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode) {
                    Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                    if (userObj instanceof CascadeUpdateRowNode) {
                        CascadeUpdateRowNode node = (CascadeUpdateRowNode) userObj;
                        if (node.depth == 0) {
                            setText("🎯 " + node.getDisplayLabel() + " [TARGET RECORD]");
                            setFont(getFont().deriveFont(Font.BOLD));
                            setForeground(ThemeManager.isDarkMode() ? new Color(52, 211, 153) : new Color(5, 150, 105));
                        } else if (node.updateColumn != null) {
                            setText("🔗 " + node.getDisplayLabel() + " (Level " + node.depth + ")");
                            setFont(getFont().deriveFont(Font.PLAIN));
                        } else {
                            setText("⚪ " + node.tableName + " (Linked, no matching column update)");
                            setFont(getFont().deriveFont(Font.ITALIC));
                            setForeground(Color.GRAY);
                        }
                    }
                }
                return this;
            }
        });

        selectedNodeInfoLabel = new JLabel("Click any record node above to inspect its columns and relational constraint.");
        selectedNodeInfoLabel.setBorder(new EmptyBorder(6, 8, 6, 8));
        selectedNodeInfoLabel.setFont(ThemeManager.getUiFont(Font.BOLD, 12));

        recordDetailsModel = new DefaultTableModel(new String[]{"Column Name", "Current / Old Value", "Proposed New Value"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        recordDetailsTable = new JTable(recordDetailsModel);
        recordDetailsTable.setRowHeight(24);

        // Custom renderer for updated column row highlighting
        recordDetailsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String colName = String.valueOf(table.getValueAt(row, 0));
                String target = (plan != null && !plan.fieldUpdates.isEmpty()) ? plan.fieldUpdates.keySet().iterator().next() : "";
                if (colName.contains(target) || colName.contains("⚡")) {
                    if (!isSelected) {
                        c.setBackground(ThemeManager.isDarkMode() ? new Color(6, 78, 59) : new Color(209, 250, 229));
                        c.setForeground(ThemeManager.isDarkMode() ? new Color(110, 231, 183) : new Color(4, 120, 87));
                    }
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    if (!isSelected) {
                        c.setBackground(table.getBackground());
                        c.setForeground(table.getForeground());
                    }
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                return c;
            }
        });

        dependencyTree.addTreeSelectionListener(e -> {
            TreePath path = dependencyTree.getSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (selected.getUserObject() instanceof CascadeUpdateRowNode) {
                displayNodeDetails((CascadeUpdateRowNode) selected.getUserObject());
            }
        });

        JScrollPane treeScroll = new JScrollPane(dependencyTree);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Relational Dependency Tree (Parents ➔ Children ➔ Grandchildren)"));

        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.add(selectedNodeInfoLabel, BorderLayout.NORTH);
        detailsPanel.add(new JScrollPane(recordDetailsTable), BorderLayout.CENTER);
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Selected Record Column Details & Proposed Changes"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, detailsPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.5);

        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private void displayNodeDetails(CascadeUpdateRowNode node) {
        selectedNodeInfoLabel.setText("Table: " + node.tableName + "  |  Relation: " + node.relationReason);
        recordDetailsModel.setRowCount(0);

        for (Map.Entry<String, Object> entry : node.allRowValues.entrySet()) {
            String col = entry.getKey();
            Object val = entry.getValue();
            boolean isUpdated = (node.updateColumn != null && col.equalsIgnoreCase(node.updateColumn));
            boolean isKey = node.keyValues.containsKey(col);

            String colLabel = (isUpdated ? "⚡ " : (isKey ? "🔑 " : "")) + col;
            String oldValStr = val != null ? val.toString() : "<NULL>";
            String newValStr = isUpdated ? (node.newValue != null ? node.newValue.toString() : "<NULL>") : "-";

            recordDetailsModel.addRow(new Object[]{colLabel, oldValStr, newValStr});
        }
    }

    private JPanel createConfigTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel info = new JLabel("💡 Customize which tables participate in the cascade, map target column names per table, or provide custom new values:");
        info.setFont(ThemeManager.getUiFont(Font.PLAIN, 12));
        panel.add(info, BorderLayout.NORTH);

        String[] cols = {"Include?", "Table Name", "Link Relationship", "Column to Update", "New Value", "Records"};
        mappingTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 0 || col == 3 || col == 4;
            }
        };

        mappingTable = new JTable(mappingTableModel);
        mappingTable.setRowHeight(28);
        mappingTable.getColumnModel().getColumn(0).setMaxWidth(70);
        mappingTable.getColumnModel().getColumn(5).setMaxWidth(90);

        panel.add(new JScrollPane(mappingTable), BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton selAllBtn = new JButton("Select All");
        JButton deselAllBtn = new JButton("Deselect All");
        JButton applyConfigBtn = new JButton("🔄 Re-calculate Plan & Script");
        applyConfigBtn.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        applyConfigBtn.setBackground(new Color(59, 130, 246));
        applyConfigBtn.setForeground(Color.WHITE);

        selAllBtn.addActionListener(e -> toggleAllMappings(true));
        deselAllBtn.addActionListener(e -> toggleAllMappings(false));
        applyConfigBtn.addActionListener(e -> applyMappingChangesAndRecalculate());

        toolbar.add(selAllBtn);
        toolbar.add(deselAllBtn);
        toolbar.add(applyConfigBtn);

        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    private void toggleAllMappings(boolean select) {
        for (int r = 0; r < mappingTableModel.getRowCount(); r++) {
            mappingTableModel.setValueAt(select, r, 0);
        }
    }

    private void applyMappingChangesAndRecalculate() {
        if (plan == null) return;

        Map<String, String> columnOverrides = new LinkedHashMap<>();
        Map<String, Object> valueOverrides = new LinkedHashMap<>();
        Set<String> excluded = new HashSet<>();

        for (int r = 0; r < mappingTableModel.getRowCount(); r++) {
            boolean inc = (Boolean) mappingTableModel.getValueAt(r, 0);
            String tbl = (String) mappingTableModel.getValueAt(r, 1);
            String targetCol = (String) mappingTableModel.getValueAt(r, 3);
            String newValStr = (String) mappingTableModel.getValueAt(r, 4);

            CascadeUpdateTableMapping m = plan.tableMappings.get(tbl);
            if (m != null) {
                m.isIncluded = inc;
                if (!inc) {
                    excluded.add(tbl);
                }
                m.targetColumn = (targetCol != null && !targetCol.equalsIgnoreCase("<Skip>")) ? targetCol.trim() : null;
                m.newValue = newValStr;
                columnOverrides.put(tbl, m.targetColumn);
                valueOverrides.put(tbl, m.newValue);
            }
        }

        dbManager.regenerateCascadeUpdateScript(plan);
        populateUIFromPlan();
        JOptionPane.showMessageDialog(this, "Plan and SQL script recalculated successfully!", "Recalculated", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel createScriptTab() {
        JPanel panel = new JPanel(new BorderLayout());
        sqlArea = new RSyntaxTextArea(plan != null ? plan.fullScript : "");
        sqlArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        sqlArea.setEditable(false);
        sqlArea.setFont(new Font("Consolas", Font.PLAIN, 13));

        RTextScrollPane scroll = new RTextScrollPane(sqlArea);
        panel.add(scroll, BorderLayout.CENTER);

        JLabel info = new JLabel("💡 All statements execute in a single database transaction. If any statement fails, changes are completely rolled back.");
        info.setBorder(new EmptyBorder(8, 10, 8, 10));
        panel.add(info, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSummaryTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"Table Name", "Role in Cascade", "Column Updated", "Target New Value", "Affected Records"};
        summaryModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable summaryTable = new JTable(summaryModel);
        summaryTable.setRowHeight(26);
        panel.add(new JScrollPane(summaryTable), BorderLayout.CENTER);

        return panel;
    }

    private void executeAnalysisAndBuildPlan() {
        String tbl = (String) tableCombo.getSelectedItem();
        String col = (String) columnCombo.getSelectedItem();
        String newVal = newValField.getText().trim();

        if (tbl == null || col == null) {
            JOptionPane.showMessageDialog(this, "Please select both a table and a field to update.", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            List<Map<String, Object>> targetRows = currentRows;
            if (targetRows == null || targetRows.isEmpty()) {
                // Query root rows based on filter
                String filter = filterField.getText().trim();
                String query = "SELECT * FROM " + dbManager.quoteTableName(tbl) + (filter.isEmpty() ? " LIMIT 10" : " WHERE " + filter);
                targetRows = new ArrayList<>();
                try (java.sql.Statement stmt = dbManager.connection.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery(query)) {
                    java.sql.ResultSetMetaData md = rs.getMetaData();
                    int cc = md.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int c = 1; c <= cc; c++) {
                            row.put(md.getColumnName(c), rs.getObject(c));
                        }
                        targetRows.add(row);
                    }
                }
            }

            if (targetRows.isEmpty()) {
                setCursor(Cursor.getDefaultCursor());
                JOptionPane.showMessageDialog(this, "No records found matching the criteria in " + tbl, "No Records Found", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Map<String, Object> fieldUpdates = new LinkedHashMap<>();
            fieldUpdates.put(col, newVal);

            this.plan = dbManager.buildCascadeUpdatePlan(tbl, targetRows, fieldUpdates);
            setCursor(Cursor.getDefaultCursor());

            populateUIFromPlan();
        } catch (Exception ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, "Failed to analyze cascade update plan:\n" + ex.getMessage(), "Analysis Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void populateUIFromPlan() {
        if (plan == null) return;

        // 1. Badges
        totalBadge.setText("  " + plan.totalRows + " Records Affected  ");
        tableBadge.setText("  " + plan.tableCounts.size() + " Table(s) Involved  ");
        modeBadge.setText("  Mode: " + (plan.isKeyCascade ? "Key Cascade" : "Linked Attributes") + "  ");

        // 2. Tree
        DefaultMutableTreeNode rootGroup = new DefaultMutableTreeNode("Root Target Group (" + plan.rootTable + ")");
        for (CascadeUpdateRowNode rootNode : plan.rootNodes) {
            rootGroup.add(buildTreeNode(rootNode));
        }
        DefaultTreeModel treeModel = (DefaultTreeModel) dependencyTree.getModel();
        treeModel.setRoot(rootGroup);
        for (int i = 0; i < dependencyTree.getRowCount(); i++) {
            dependencyTree.expandRow(i);
        }
        if (rootGroup.getChildCount() > 0) {
            dependencyTree.setSelectionPath(new TreePath(((DefaultMutableTreeNode) rootGroup.getFirstChild()).getPath()));
        }

        // 3. Mapping Table
        mappingTableModel.setRowCount(0);
        for (Map.Entry<String, CascadeUpdateTableMapping> entry : plan.tableMappings.entrySet()) {
            CascadeUpdateTableMapping m = entry.getValue();
            mappingTableModel.addRow(new Object[]{
                    m.isIncluded,
                    m.tableName,
                    m.linkRelationship,
                    m.targetColumn != null ? m.targetColumn : "<Skip>",
                    m.newValue != null ? m.newValue.toString() : "",
                    m.affectedRowCount + " record(s)"
            });
        }

        // 4. Script
        sqlArea.setText(plan.fullScript);
        sqlArea.setCaretPosition(0);

        // 5. Summary Table
        summaryModel.setRowCount(0);
        for (Map.Entry<String, Integer> e : plan.tableCounts.entrySet()) {
            String tbl = e.getKey();
            int count = e.getValue();
            CascadeUpdateTableMapping m = plan.tableMappings.get(tbl);
            String role = tbl.equalsIgnoreCase(plan.rootTable) ? "🎯 Root Target" : "🔗 Referencing Child Table";
            String col = m != null && m.targetColumn != null ? m.targetColumn : "-";
            String val = m != null && m.newValue != null ? m.newValue.toString() : "-";
            summaryModel.addRow(new Object[]{tbl, role, col, val, count});
        }
    }

    private DefaultMutableTreeNode buildTreeNode(CascadeUpdateRowNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        for (CascadeUpdateRowNode child : node.children) {
            treeNode.add(buildTreeNode(child));
        }
        return treeNode;
    }

    private void executeCascadeUpdate() {
        if (plan == null || plan.totalRows == 0) {
            JOptionPane.showMessageDialog(this, "No records found to update.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cascade update " + plan.totalRows + " record(s) across " +
                        plan.tableCounts.size() + " table(s)?\n\n" +
                        "All statements will be executed in a single atomic transaction.\n" +
                        "If any statement fails, the entire transaction will be rolled back safely with zero data corruption.",
                "Confirm Atomic Cascade Update",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            dbManager.executeCascadeUpdatePlan(plan);
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this,
                    "Cascade update completed successfully!\n" +
                            plan.totalRows + " record(s) updated across " + plan.tableCounts.size() + " table(s).",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

            if (onSuccessCallback != null) {
                onSuccessCallback.run();
            }
            dispose();
        } catch (Exception ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this,
                    "Cascade update failed and was safely rolled back:\n\n" + ex.getMessage(),
                    "Execution Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
