package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.CascadePlan;
import com.dbtool.DatabaseManager.CascadeRowNode;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Map;

/**
 * Interactive Dialog to inspect recursive foreign key dependencies
 * and safely execute cascade deletion inside an atomic transaction.
 * 
 * Part of SchemaSync Tier 1 Advanced Relational Features.
 */
public class CascadeDeleteDialog extends JDialog {

    private final DatabaseManager dbManager;
    private final CascadePlan plan;
    private final Runnable onSuccessCallback;

    private JTree dependencyTree;
    private RSyntaxTextArea sqlArea;
    private JTable recordDetailsTable;
    private DefaultTableModel recordDetailsModel;
    private JLabel selectedNodeInfoLabel;

    public CascadeDeleteDialog(Frame owner, DatabaseManager dbManager, CascadePlan plan, Runnable onSuccessCallback) {
        super(owner, "⚡ Cascade Deletion & Foreign Key Dependency Inspector", true);
        this.dbManager = dbManager;
        this.plan = plan;
        this.onSuccessCallback = onSuccessCallback;

        setSize(960, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        initComponents();
    }

    private void initComponents() {
        // --- 1. Top Header Banner ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        headerPanel.setBackground(new Color(248, 250, 252));
        if (ThemeManager.isDarkMode()) {
            headerPanel.setBackground(new Color(30, 41, 59));
        }

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);
        JLabel titleLabel = new JLabel("⚡ Foreign Key Dependency Inspector & Cascade Delete");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JLabel subtitleLabel = new JLabel("Preview referencing child records across all dependency levels and delete safely inside an atomic transaction.");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);

        titleBox.add(titleLabel);
        titleBox.add(subtitleLabel);

        JPanel badgesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        badgesPanel.setOpaque(false);

        JLabel totalBadge = new JLabel("  " + plan.totalRows + " Total Records Affected  ");
        totalBadge.setOpaque(true);
        totalBadge.setBackground(new Color(239, 68, 68));
        totalBadge.setForeground(Color.WHITE);
        totalBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        totalBadge.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JLabel tableBadge = new JLabel("  " + plan.tableCounts.size() + " Table(s) Involved  ");
        tableBadge.setOpaque(true);
        tableBadge.setBackground(new Color(59, 130, 246));
        tableBadge.setForeground(Color.WHITE);
        tableBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tableBadge.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        badgesPanel.add(totalBadge);
        badgesPanel.add(tableBadge);

        headerPanel.add(titleBox, BorderLayout.WEST);
        headerPanel.add(badgesPanel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // --- 2. Main Content Tabs ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Dependency Tree & Details
        JPanel treeTab = createTreeTab();
        tabbedPane.addTab("🌳 Dependency Tree & Data", treeTab);

        // Tab 2: SQL Transaction Script
        JPanel scriptTab = createScriptTab();
        tabbedPane.addTab("📜 Generated SQL Script", scriptTab);

        // Tab 3: Tables Breakdown
        JPanel summaryTab = createSummaryTab();
        tabbedPane.addTab("📊 Affected Tables Breakdown", summaryTab);

        add(tabbedPane, BorderLayout.CENTER);

        // --- 3. Bottom Button Bar ---
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonBar.setBorder(new EmptyBorder(6, 12, 10, 12));

        JButton copyScriptBtn = new JButton("📋 Copy SQL Script");
        copyScriptBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(plan.fullScript), null);
            JOptionPane.showMessageDialog(this, "SQL script copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton executeBtn = new JButton("⚡ Execute Cascade Delete (Safe Transaction)");
        executeBtn.setFont(executeBtn.getFont().deriveFont(Font.BOLD));
        executeBtn.setBackground(new Color(220, 38, 38));
        executeBtn.setForeground(Color.WHITE);
        executeBtn.setFocusPainted(false);
        executeBtn.addActionListener(e -> executeCascadeDeletion());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonBar.add(copyScriptBtn);
        buttonBar.add(cancelBtn);
        buttonBar.add(executeBtn);

        add(buttonBar, BorderLayout.SOUTH);
    }

    private JPanel createTreeTab() {
        JPanel panel = new JPanel(new BorderLayout());

        // Build Tree Model
        DefaultMutableTreeNode rootTreeNode = new DefaultMutableTreeNode("Root Deletion Group");
        for (CascadeRowNode rootNode : plan.rootNodes) {
            DefaultMutableTreeNode targetNode = buildTreeNode(rootNode);
            rootTreeNode.add(targetNode);
        }

        dependencyTree = new JTree(rootTreeNode);
        dependencyTree.setRootVisible(false);
        dependencyTree.setShowsRootHandles(true);

        // Custom Cell Renderer
        dependencyTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode) {
                    Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                    if (userObj instanceof CascadeRowNode) {
                        CascadeRowNode node = (CascadeRowNode) userObj;
                        if (node.depth == 0) {
                            setText("🎯 " + node.getDisplayLabel() + " [TARGET ROW]");
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else {
                            setText("🔗 " + node.getDisplayLabel() + " (Level " + node.depth + ")");
                            setFont(getFont().deriveFont(Font.PLAIN));
                        }
                    }
                }
                return this;
            }
        });

        // Expand all nodes
        for (int i = 0; i < dependencyTree.getRowCount(); i++) {
            dependencyTree.expandRow(i);
        }

        // Details on tree node click
        selectedNodeInfoLabel = new JLabel("Click any record node above to view its column data and relational constraint.");
        selectedNodeInfoLabel.setBorder(new EmptyBorder(6, 8, 6, 8));
        selectedNodeInfoLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        recordDetailsModel = new DefaultTableModel(new String[]{"Column Name", "Value"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        recordDetailsTable = new JTable(recordDetailsModel);
        recordDetailsTable.setRowHeight(24);

        dependencyTree.addTreeSelectionListener(e -> {
            TreePath path = dependencyTree.getSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (selected.getUserObject() instanceof CascadeRowNode) {
                CascadeRowNode node = (CascadeRowNode) selected.getUserObject();
                displayNodeDetails(node);
            }
        });

        // Auto select first root node
        if (rootTreeNode.getChildCount() > 0) {
            dependencyTree.setSelectionPath(new TreePath(((DefaultMutableTreeNode) rootTreeNode.getFirstChild()).getPath()));
        }

        JScrollPane treeScroll = new JScrollPane(dependencyTree);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Relational Dependency Tree (Parents ➔ Children ➔ Grandchildren)"));

        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.add(selectedNodeInfoLabel, BorderLayout.NORTH);
        detailsPanel.add(new JScrollPane(recordDetailsTable), BorderLayout.CENTER);
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Selected Record Details"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, detailsPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.5);

        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private DefaultMutableTreeNode buildTreeNode(CascadeRowNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        for (CascadeRowNode child : node.children) {
            treeNode.add(buildTreeNode(child));
        }
        return treeNode;
    }

    private void displayNodeDetails(CascadeRowNode node) {
        selectedNodeInfoLabel.setText("Table: " + node.tableName + "  |  Constraint: " + node.relationReason);
        recordDetailsModel.setRowCount(0);
        for (Map.Entry<String, Object> entry : node.allRowValues.entrySet()) {
            String col = entry.getKey();
            Object val = entry.getValue();
            boolean isKey = node.keyValues.containsKey(col);
            recordDetailsModel.addRow(new Object[]{isKey ? "🔑 " + col : col, val != null ? val.toString() : "<NULL>"});
        }
    }

    private JPanel createScriptTab() {
        JPanel panel = new JPanel(new BorderLayout());
        sqlArea = new RSyntaxTextArea(plan.fullScript);
        sqlArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        sqlArea.setEditable(false);
        sqlArea.setFont(new Font("Consolas", Font.PLAIN, 13));

        RTextScrollPane scroll = new RTextScrollPane(sqlArea);
        panel.add(scroll, BorderLayout.CENTER);

        JLabel info = new JLabel("💡 This script is generated in reverse-topological order so that all child records are removed before their parents.");
        info.setBorder(new EmptyBorder(8, 10, 8, 10));
        panel.add(info, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSummaryTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"Table Name", "Affected Records", "Role in Deletion"};
        DefaultTableModel summaryModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        for (Map.Entry<String, Integer> e : plan.tableCounts.entrySet()) {
            String tbl = e.getKey();
            int count = e.getValue();
            String role = tbl.equalsIgnoreCase(plan.rootTable) ? "🎯 Primary Target" : "🔗 Referencing Child Table";
            summaryModel.addRow(new Object[]{tbl, count, role});
        }

        JTable summaryTable = new JTable(summaryModel);
        summaryTable.setRowHeight(26);
        panel.add(new JScrollPane(summaryTable), BorderLayout.CENTER);

        return panel;
    }

    private void executeCascadeDeletion() {
        if (plan.totalRows == 0) {
            JOptionPane.showMessageDialog(this, "No records found to delete.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to permanently delete " + plan.totalRows + " record(s) across " +
                        plan.tableCounts.size() + " table(s)?\n\n" +
                        "All statements will be executed in a single transaction.\n" +
                        "If any statement fails, the entire transaction will be rolled back safely.",
                "Confirm Atomic Cascade Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            dbManager.executeCascadePlan(plan);
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this,
                    "Cascade delete completed successfully!\n" +
                            plan.totalRows + " record(s) deleted across " + plan.tableCounts.size() + " table(s).",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

            if (onSuccessCallback != null) {
                onSuccessCallback.run();
            }
            dispose();
        } catch (Exception ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this,
                    "Cascade deletion failed and was safely rolled back:\n\n" + ex.getMessage(),
                    "Execution Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
