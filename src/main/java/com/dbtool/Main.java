package com.dbtool;

import com.dbtool.panels.SchemaPanel;
import com.dbtool.panels.SqlEditorPanel;
import com.dbtool.panels.TableComparePanel;
import com.dbtool.panels.ScriptBuilderPanel;
import com.dbtool.panels.DataOpsPanel;
import com.dbtool.util.ExportUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.dbtool.model.VisualJoinState;
import com.dbtool.util.VisualJoinProjectManager;

public class Main extends JFrame {

    private DatabaseManager dbManager = new DatabaseManager();
    private JLabel statusLabel = new JLabel("No database loaded.");
    
    // Browse Tab Components
    private SearchableComboBox browseTableDropdown = new SearchableComboBox();
    private JButton selectColsButton = new JButton("Columns (All)");
    private List<String> currentSelectedColumns = new ArrayList<>();
    private JTextField limitField = new JTextField(5);
    
    
    private JTable browseTable = new JTable();
    private JPanel browseWhereContainer = new JPanel();
    private List<WherePanel> browseWherePanels = new ArrayList<>();
    private JPanel browseOrderByContainer = new JPanel();
    private List<OrderByPanel> browseOrderByPanels = new ArrayList<>();
    private String lastExecutedBrowseQuery = "";
    private int browseCurrentOffset = 0;
    private boolean isBrowseLoading = false;
    private String lastBrowseBaseQuery = "";
    private JCheckBox browseDistinctCheckbox = new JCheckBox("Distinct");
    
    // Join Tab Components
    private SearchableComboBox baseTableDropdown = new SearchableComboBox();
    private JPanel joinsContainer = new JPanel();
    private List<JoinPanel> joinPanels = new ArrayList<>();
    private JTable joinResultTable = new JTable();
    private JButton joinSelectColsButton = new JButton("Columns (All)");
    private List<String> joinSelectedColumns = new ArrayList<>();
    private JTextField joinLimitField = new JTextField(5);
    private String lastExecutedJoinQuery = "";
    private JCheckBox joinDistinctCheckbox = new JCheckBox("Distinct");
    private JPanel whereContainer = new JPanel();
    private List<WherePanel> wherePanels = new ArrayList<>();
    private JPanel joinOrderByContainer = new JPanel();
    private List<OrderByPanel> joinOrderByPanels = new ArrayList<>();
    
    // New panels
    private SchemaPanel schemaPanel;
    private SqlEditorPanel sqlEditorPanel;
    private TableComparePanel tableComparePanel;
    private ScriptBuilderPanel scriptBuilderPanel;
    private DataOpsPanel dataOpsPanel;
    
    // Cache for currently loaded tables
    private List<String> loadedTables = new ArrayList<>();

    public Main() {
        setTitle("SchemaSync");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Initialize new panels
        schemaPanel = new SchemaPanel(dbManager);
        sqlEditorPanel = new SqlEditorPanel(dbManager);
        tableComparePanel = new TableComparePanel(dbManager);
        scriptBuilderPanel = new ScriptBuilderPanel(dbManager);
        dataOpsPanel = new DataOpsPanel(dbManager);

        // Schema panel: double-click loads table in browse
        schemaPanel.setOnTableSelected(tableName -> {
            browseTableDropdown.setSelectedItem(tableName);
        });

        // Top Panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadDbButton = new JButton("Connect & Upload (.accdb, .sql, .sql.gz)");
        loadDbButton.addActionListener(e -> loadDatabase());
        topPanel.add(loadDbButton);
        
        JButton connectExistingBtn = new JButton("Connect to Existing DB");
        connectExistingBtn.addActionListener(e -> connectExistingDatabase());
        topPanel.add(connectExistingBtn);

        JButton themeBtn = new JButton("🌙 Dark Mode");
        themeBtn.addActionListener(e -> com.dbtool.util.ThemeManager.toggleTheme(themeBtn));
        topPanel.add(themeBtn);

        topPanel.add(statusLabel);
        
        // Init theme
        com.dbtool.util.ThemeManager.init(themeBtn);
        add(topPanel, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Browse & Search", createBrowsePanel());
        tabbedPane.addTab("Visual Join Builder", createJoinPanel());
        tabbedPane.addTab("Data Operations", dataOpsPanel);
        tabbedPane.addTab("SQL Editor", sqlEditorPanel);
        tabbedPane.addTab("Script Builder", scriptBuilderPanel);
        tabbedPane.addTab("Compare Queries", tableComparePanel);
        
        com.dbtool.panels.SshTerminalPanel sshTerminalPanel = new com.dbtool.panels.SshTerminalPanel(dbManager);
        tabbedPane.addTab("SSH / Server", sshTerminalPanel);

        // Main layout: Schema sidebar left, tabs center
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, schemaPanel, tabbedPane);
        mainSplit.setDividerLocation(230);
        // mainSplit.setOneTouchExpandable(true);
        mainSplit.setResizeWeight(0.0);
        add(mainSplit, BorderLayout.CENTER);

        JButton toggleSidebarBtn = new JButton("☰");
        toggleSidebarBtn.setToolTipText("Toggle Sidebar");
        toggleSidebarBtn.addActionListener(e -> {
            if (mainSplit.getDividerLocation() <= 10) {
                mainSplit.setDividerLocation(230);
            } else {
                mainSplit.setDividerLocation(0);
            }
        });
        topPanel.add(toggleSidebarBtn, 0); // Add to extreme left

    }

    private JPanel createBrowsePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JToolBar tbSelect = new JToolBar();
        tbSelect.setFloatable(false);
        tbSelect.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));

        
        tbSelect.add(new JLabel(" |  Table: "));
        browseTableDropdown.addActionListener(e -> {
            String selected = (String) browseTableDropdown.getSelectedItem();
            if (selected == null || !loadedTables.contains(selected)) return;

            currentSelectedColumns.clear();
            selectColsButton.setText("Columns (All)");
            browseWherePanels.clear();
            browseWhereContainer.removeAll();
            browseOrderByPanels.clear();
            browseOrderByContainer.removeAll();
            browseWhereContainer.revalidate();
            browseWhereContainer.repaint();
            refreshOrderByDropdown();
            
        });
        browseTableDropdown.setPreferredSize(new Dimension(200, 26));
        tbSelect.add(browseTableDropdown);
        
        tbSelect.add(new JLabel(" | "));
        selectColsButton.addActionListener(e -> openColumnSelector());
        tbSelect.add(selectColsButton);
        

        
        tbSelect.add(new JLabel(" |  Limit: "));
        limitField.setPreferredSize(new Dimension(60, 26));
        tbSelect.add(limitField);
        
        JToolBar tbQuery = new JToolBar();
        tbQuery.setFloatable(false);
        tbQuery.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addWhereBtn = new JButton("+ Add Where");
        addWhereBtn.addActionListener(e -> addBrowseWhereRow());
        tbQuery.add(addWhereBtn);
        tbQuery.add(new JLabel(" | "));
        tbQuery.add(browseDistinctCheckbox);
        tbQuery.add(new JLabel(" | "));
        JButton addBrowseOrderBtn = new JButton("+ Add Order By");
        addBrowseOrderBtn.addActionListener(e -> addBrowseOrderByRow());
        tbQuery.add(addBrowseOrderBtn);
        JButton applyOrderBtn = new JButton("Apply");
        applyOrderBtn.addActionListener(e -> loadTableData());
        tbQuery.add(applyOrderBtn);
        
        JToolBar tbActions = new JToolBar();
        tbActions.setFloatable(false);
        tbActions.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton insertRowBtn = new JButton("➕ Insert Row");
        JButton deleteRowBtn = new JButton("🗑 Delete Row");
        insertRowBtn.addActionListener(e -> openInsertRowDialog());
        deleteRowBtn.addActionListener(e -> deleteSelectedRow());
        tbActions.add(insertRowBtn);
        tbActions.add(deleteRowBtn);
        tbActions.addSeparator();
        
        JCheckBox autoCommitCheck = new JCheckBox("Auto-Commit", true);
        JButton commitBtn = new JButton("Commit");
        JButton rollbackBtn = new JButton("Rollback");
        commitBtn.setEnabled(false);
        rollbackBtn.setEnabled(false);
        autoCommitCheck.addActionListener(e -> {
            boolean ac = autoCommitCheck.isSelected();
            try {
                if (dbManager.connection != null) dbManager.connection.setAutoCommit(ac);
                commitBtn.setEnabled(!ac);
                rollbackBtn.setEnabled(!ac);
            } catch (Exception ex) {}
        });
        commitBtn.addActionListener(e -> {
            try { if (dbManager.connection != null) dbManager.connection.commit(); JOptionPane.showMessageDialog(this, "Transaction committed."); } catch (Exception ex) {}
        });
        rollbackBtn.addActionListener(e -> {
            try { if (dbManager.connection != null) dbManager.connection.rollback(); JOptionPane.showMessageDialog(this, "Transaction rolled back."); loadTableData(); } catch (Exception ex) {}
        });
        
        tbActions.add(autoCommitCheck);
        tbActions.add(commitBtn);
        tbActions.add(rollbackBtn);
        tbActions.addSeparator();

        JButton exportCsvBtn = new JButton("📄 CSV");
        JButton exportPdfBtn = new JButton("📄 PDF");
        JButton exportXlsxBtn = new JButton("📥 Excel");
        JButton exportInsertBtn = new JButton("📥 SQL Inserts");
        JButton copyBtn = new JButton("📋 Copy Data");
        JButton copySqlBtn = new JButton("📋 Copy SQL");
        exportCsvBtn.addActionListener(e -> ExportUtil.exportCsv(browseTable, this));
        exportXlsxBtn.addActionListener(e -> ExportUtil.exportExcel(browseTable, this));
        exportPdfBtn.addActionListener(e -> ExportUtil.exportPdf(browseTable, this));
        exportInsertBtn.addActionListener(e -> {
            String tbl = (String) browseTableDropdown.getSelectedItem();
            if (tbl != null) ExportUtil.exportSqlInserts(browseTable, tbl, this);
        });
        copyBtn.addActionListener(e -> ExportUtil.copyToClipboard(browseTable));
        copySqlBtn.addActionListener(e -> {
            if (!lastExecutedBrowseQuery.isEmpty()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(lastExecutedBrowseQuery), null);
                JOptionPane.showMessageDialog(this, "SQL copied to clipboard!");
            } else {
                JOptionPane.showMessageDialog(this, "No query executed yet.");
            }
        });
        tbActions.add(exportCsvBtn);
        tbActions.add(exportPdfBtn);
        tbActions.add(exportXlsxBtn);
        tbActions.add(exportInsertBtn);
        tbActions.addSeparator();
        tbActions.add(copyBtn);
        tbActions.add(copySqlBtn);

        // Enable sorting on browseTable
        browseTable.setAutoCreateRowSorter(true);
        com.dbtool.util.TableTooltipUtil.attachHeaderTooltips(browseTable);
        browseTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        browseWhereContainer.setLayout(new BoxLayout(browseWhereContainer, BoxLayout.Y_AXIS));
        
        JPanel northPanel = new JPanel(new BorderLayout());
        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.add(tbSelect);
        rowsPanel.add(tbQuery);
        rowsPanel.add(tbActions);
        browseOrderByContainer.setLayout(new BoxLayout(browseOrderByContainer, BoxLayout.Y_AXIS));
        northPanel.add(rowsPanel, BorderLayout.NORTH);
        JPanel filtersPanel = new JPanel();
        filtersPanel.setLayout(new BoxLayout(filtersPanel, BoxLayout.Y_AXIS));
        filtersPanel.add(browseWhereContainer);
        filtersPanel.add(browseOrderByContainer);
        northPanel.add(filtersPanel, BorderLayout.CENTER);
        
        panel.add(northPanel, BorderLayout.NORTH);
        JScrollPane browseScroll = new JScrollPane(browseTable);
        browseScroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                javax.swing.JScrollBar scrollBar = (javax.swing.JScrollBar) e.getAdjustable();
                int extent = scrollBar.getModel().getExtent();
                int maximum = scrollBar.getModel().getMaximum();
                if (maximum > 0 && scrollBar.getValue() + extent >= maximum - 50) {
                    if (!isBrowseLoading && lastBrowseBaseQuery != null && !lastBrowseBaseQuery.isEmpty()) {
                        fetchBrowseData(true);
                    }
                }
            }
        });
        panel.add(browseScroll, BorderLayout.CENTER);
        
        return panel;
    }

    

    private void refreshOrderByDropdown() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl != null) {
            java.util.List<String> cols = dbManager.getColumnNames(tbl);
            for (OrderByPanel op : browseOrderByPanels) op.updateColumns(cols);
        }
    }

    private void openInsertRowDialog() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl == null) return;
        List<String> cols = dbManager.getColumnNames(tbl);
        JPanel form = new JPanel(new GridLayout(cols.size(), 2, 5, 5));
        List<JTextField> fields = new ArrayList<>();
        for (String col : cols) {
            form.add(new JLabel(col + ":"));
            JTextField tf = new JTextField(20);
            fields.add(tf);
            form.add(tf);
        }
        int res = JOptionPane.showConfirmDialog(this, new JScrollPane(form), "Insert Row into " + tbl, JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;
        StringBuilder sql = new StringBuilder("INSERT INTO " + dbManager.quoteTableName(tbl) + " (");
        StringBuilder vals = new StringBuilder(") VALUES (");
        for (int i = 0; i < cols.size(); i++) {
            sql.append("\"").append(cols.get(i)).append("\"");
            vals.append("'").append(fields.get(i).getText().replace("'", "''")).append("'");
            if (i < cols.size() - 1) { sql.append(", "); vals.append(", "); }
        }
        try {
            dbManager.connection.createStatement().executeUpdate(sql.toString() + vals.toString() + ")");
            JOptionPane.showMessageDialog(this, "Row inserted successfully.");
            loadTableData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Insert failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedRow() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl == null) return;
        int[] rows = browseTable.getSelectedRows();
        if (rows.length == 0) { JOptionPane.showMessageDialog(this, "Select rows to delete first."); return; }
        int confirm = JOptionPane.showConfirmDialog(this, "Delete " + rows.length + " selected row(s)?", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        // Build delete using all column values as the key (safest approach without requiring PK)
        DefaultTableModel model = (DefaultTableModel) browseTable.getModel();
        int deleted = 0;
        for (int viewRow : rows) {
            int modelRow = browseTable.convertRowIndexToModel(viewRow);
            StringBuilder sql = new StringBuilder("DELETE FROM " + dbManager.quoteTableName(tbl) + " WHERE ");
            for (int c = 0; c < model.getColumnCount(); c++) {
                Object val = model.getValueAt(modelRow, c);
                sql.append("\"").append(model.getColumnName(c)).append("\"");
                if (val == null) sql.append(" IS NULL");
                else sql.append(" = '").append(val.toString().replace("'", "''")).append("'");
                if (c < model.getColumnCount() - 1) sql.append(" AND ");
            }
            sql.append(" LIMIT 1");
            try { dbManager.connection.createStatement().executeUpdate(sql.toString()); deleted++; }
            catch (Exception ex) { System.err.println("Delete failed for row: " + ex.getMessage()); }
        }
        JOptionPane.showMessageDialog(this, deleted + " row(s) deleted.");
        loadTableData();
    }



    private void openColumnSelector() {
        String selectedTable = (String) browseTableDropdown.getSelectedItem();
        if (selectedTable == null) return;
        List<String> allCols = dbManager.getColumnNames(selectedTable);
        if (allCols.isEmpty()) return;

        JDialog dialog = new JDialog(this, "Select Columns", true);
        dialog.setLayout(new BorderLayout());
        JPanel cbPanel = new JPanel();
        cbPanel.setLayout(new BoxLayout(cbPanel, BoxLayout.Y_AXIS));
        
        List<JCheckBox> checkBoxes = new ArrayList<>();
        for (String col : allCols) {
            JCheckBox cb = new JCheckBox(col);
            if (currentSelectedColumns.isEmpty() || currentSelectedColumns.contains(col)) {
                cb.setSelected(true);
            }
            checkBoxes.add(cb);
            cbPanel.add(cb);
        }
        
        JScrollPane scroll = new JScrollPane(cbPanel);
        dialog.add(scroll, BorderLayout.CENTER);
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton selectAllBtn = new JButton("Select All");
        JButton unselectAllBtn = new JButton("Unselect All");
        selectAllBtn.addActionListener(e -> {
            for (JCheckBox cb : checkBoxes) cb.setSelected(true);
        });
        unselectAllBtn.addActionListener(e -> {
            for (JCheckBox cb : checkBoxes) cb.setSelected(false);
        });
        topPanel.add(selectAllBtn);
        topPanel.add(unselectAllBtn);
        dialog.add(topPanel, BorderLayout.NORTH);
        
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            currentSelectedColumns.clear();
            int selectedCount = 0;
            for (JCheckBox cb : checkBoxes) {
                if (cb.isSelected()) {
                    currentSelectedColumns.add(cb.getText());
                    selectedCount++;
                }
            }
            if (selectedCount == allCols.size() || selectedCount == 0) {
                currentSelectedColumns.clear();
                selectColsButton.setText("Columns (All)");
            } else {
                selectColsButton.setText("Columns (" + selectedCount + ")");
            }
            dialog.dispose();
            loadTableData();
        });
        
        JPanel bottomPanel = new JPanel();
        bottomPanel.add(okBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        
        dialog.setSize(300, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel createJoinPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JPanel configPanel = new JPanel(new BorderLayout());
        
        // Base Table Selection
        JToolBar tbBase = new JToolBar();
        tbBase.setFloatable(false);
        tbBase.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        tbBase.add(new JLabel(" Base Table: "));
        baseTableDropdown.addActionListener(e -> {
            joinSelectedColumns.clear();
            joinSelectColsButton.setText("Columns (All)");
            updateJoinDropdowns();
        });
        baseTableDropdown.setPreferredSize(new Dimension(200, 26));
        tbBase.add(baseTableDropdown);
        tbBase.add(new JLabel(" | "));
        JButton addJoinBtn = new JButton("+ Add Join");
        addJoinBtn.addActionListener(e -> addJoinRow());
        tbBase.add(addJoinBtn);
        JButton addWhereBtn = new JButton("+ Add Where");
        addWhereBtn.addActionListener(e -> addWhereRow());
        tbBase.add(addWhereBtn);
        configPanel.add(tbBase, BorderLayout.NORTH);
        
        // Dynamic Joins and Where List
        joinsContainer.setLayout(new BoxLayout(joinsContainer, BoxLayout.Y_AXIS));
        whereContainer.setLayout(new BoxLayout(whereContainer, BoxLayout.Y_AXIS));
        
        JPanel listsPanel = new JPanel();
        listsPanel.setLayout(new BoxLayout(listsPanel, BoxLayout.Y_AXIS));
        listsPanel.add(joinsContainer);
        listsPanel.add(whereContainer);
        
        configPanel.add(new JScrollPane(listsPanel), BorderLayout.CENTER);
        
        // Bottom Options
        JPanel bottomOptionsPanel = new JPanel();
        bottomOptionsPanel.setLayout(new BoxLayout(bottomOptionsPanel, BoxLayout.Y_AXIS));
        
        JToolBar tbRun = new JToolBar();
        tbRun.setFloatable(false);
        tbRun.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        joinSelectColsButton.addActionListener(e -> openJoinColumnSelector());
        tbRun.add(joinSelectColsButton);
        tbRun.add(new JLabel(" | "));
        tbRun.add(joinDistinctCheckbox);
        tbRun.add(new JLabel(" |  Limit: "));
        joinLimitField.setToolTipText("Limit (empty for none)");
        joinLimitField.setPreferredSize(new Dimension(60, 26));
        tbRun.add(joinLimitField);
        
        tbRun.add(new JLabel(" | "));

        tbRun.add(new JSeparator(SwingConstants.VERTICAL));
        JButton saveJoinBtn = new JButton("💾 Save Project");
        JButton loadJoinBtn = new JButton("📂 Load Project");
        saveJoinBtn.addActionListener(e -> saveVisualJoinProject());
        loadJoinBtn.addActionListener(e -> loadVisualJoinProject());
        tbRun.add(saveJoinBtn);
        tbRun.add(loadJoinBtn);
        tbRun.add(new JLabel(" | "));
        JButton addJoinOrderBtn = new JButton("+ Add Order By");
        addJoinOrderBtn.addActionListener(e -> addJoinOrderByRow());
        tbRun.add(addJoinOrderBtn);
        
        tbRun.add(new JLabel(" | "));
        JButton runJoinBtn = new JButton("▶ Execute Join");
        runJoinBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        runJoinBtn.setForeground(new Color(0, 102, 51));
        runJoinBtn.addActionListener(e -> executeVisualJoin());
        tbRun.add(runJoinBtn);
        
        JToolBar tbActions = new JToolBar();
        tbActions.setFloatable(false);
        tbActions.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton exportCsvBtn = new JButton("📥 CSV");
        JButton exportPdfBtn = new JButton("📥 PDF");
        JButton exportXlsxBtn = new JButton("📥 Excel");
        JButton exportInsertBtn = new JButton("📥 SQL Inserts");
        JButton copyDataBtn = new JButton("📋 Copy Data");
        JButton copySqlBtn = new JButton("📋 Copy SQL");
        
        exportCsvBtn.addActionListener(e -> ExportUtil.exportCsv(joinResultTable, this));
        exportPdfBtn.addActionListener(e -> ExportUtil.exportPdf(joinResultTable, this));
        exportXlsxBtn.addActionListener(e -> ExportUtil.exportExcel(joinResultTable, this));
        exportInsertBtn.addActionListener(e -> {
            String tbl = (String) baseTableDropdown.getSelectedItem();
            if (tbl != null) ExportUtil.exportSqlInserts(joinResultTable, tbl, this);
        });
        copyDataBtn.addActionListener(e -> ExportUtil.copyToClipboard(joinResultTable));
        copySqlBtn.addActionListener(e -> {
            if (!lastExecutedJoinQuery.isEmpty()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(lastExecutedJoinQuery), null);
                JOptionPane.showMessageDialog(this, "SQL copied to clipboard!");
            } else {
                JOptionPane.showMessageDialog(this, "No query executed yet.");
            }
        });
        
        tbActions.add(exportCsvBtn);
        tbActions.add(exportPdfBtn);
        tbActions.add(exportXlsxBtn);
        tbActions.add(exportInsertBtn);
        tbActions.addSeparator();
        tbActions.add(copyDataBtn);
        tbActions.add(copySqlBtn);
        
        joinOrderByContainer.setLayout(new BoxLayout(joinOrderByContainer, BoxLayout.Y_AXIS));
        bottomOptionsPanel.add(joinOrderByContainer);
        bottomOptionsPanel.add(tbRun);
        bottomOptionsPanel.add(tbActions);
        configPanel.add(bottomOptionsPanel, BorderLayout.SOUTH);
        
        panel.add(configPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(joinResultTable), BorderLayout.CENTER);
        
        return panel;
    }

    private void openJoinColumnSelector() {
        String baseTable = (String) baseTableDropdown.getSelectedItem();
        if (baseTable == null) return;

        // Build a map: tableName -> list of checkboxes
        List<String> tableOrder = new ArrayList<>();
        Map<String, List<JCheckBox>> tableCheckboxMap = new LinkedHashMap<>();
        List<JCheckBox> allCheckBoxes = new ArrayList<>();
        int totalCols = 0;

        // Base table
        tableOrder.add(baseTable);
        List<JCheckBox> baseCbs = new ArrayList<>();
        for (String col : dbManager.getColumnNames(baseTable)) {
            String fullCol = baseTable + "." + col;
            JCheckBox cb = new JCheckBox(col); // show just col name in tab; full name is in text
            cb.setText(fullCol);
            if (joinSelectedColumns.isEmpty() || joinSelectedColumns.contains(fullCol)) cb.setSelected(true);
            baseCbs.add(cb);
            allCheckBoxes.add(cb);
            totalCols++;
        }
        tableCheckboxMap.put(baseTable, baseCbs);

        // Joined tables
        for (JoinPanel jp : joinPanels) {
            String joinTable = (String) jp.joinTableDropdown.getSelectedItem();
            if (joinTable != null && !tableCheckboxMap.containsKey(joinTable)) {
                tableOrder.add(joinTable);
                List<JCheckBox> cbs = new ArrayList<>();
                for (String col : dbManager.getColumnNames(joinTable)) {
                    String fullCol = joinTable + "." + col;
                    JCheckBox cb = new JCheckBox(fullCol);
                    if (joinSelectedColumns.isEmpty() || joinSelectedColumns.contains(fullCol)) cb.setSelected(true);
                    cbs.add(cb);
                    allCheckBoxes.add(cb);
                    totalCols++;
                }
                tableCheckboxMap.put(joinTable, cbs);
            }
        }

        if (totalCols == 0) return;

        JDialog dialog = new JDialog(this, "Select Join Columns", true);
        dialog.setLayout(new BorderLayout());

        // One tab per table
        JTabbedPane tabs = new JTabbedPane();
        for (String tbl : tableOrder) {
            JPanel tabPanel = new JPanel();
            tabPanel.setLayout(new BoxLayout(tabPanel, BoxLayout.Y_AXIS));
            for (JCheckBox cb : tableCheckboxMap.get(tbl)) {
                tabPanel.add(cb);
            }
            tabs.addTab(tbl, new JScrollPane(tabPanel));
        }
        dialog.add(tabs, BorderLayout.CENTER);

        // Top: Select All / Unselect All (affects active tab only)
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton selectAllBtn = new JButton("Select All (Tab)");
        JButton unselectAllBtn = new JButton("Unselect All (Tab)");
        JButton selectGlobalBtn = new JButton("Select All");
        JButton unselectGlobalBtn = new JButton("Unselect All");
        selectAllBtn.addActionListener(e -> {
            String tbl = tableOrder.get(tabs.getSelectedIndex());
            for (JCheckBox cb : tableCheckboxMap.get(tbl)) cb.setSelected(true);
        });
        unselectAllBtn.addActionListener(e -> {
            String tbl = tableOrder.get(tabs.getSelectedIndex());
            for (JCheckBox cb : tableCheckboxMap.get(tbl)) cb.setSelected(false);
        });
        selectGlobalBtn.addActionListener(e -> { for (JCheckBox cb : allCheckBoxes) cb.setSelected(true); });
        unselectGlobalBtn.addActionListener(e -> { for (JCheckBox cb : allCheckBoxes) cb.setSelected(false); });
        topPanel.add(selectGlobalBtn);
        topPanel.add(unselectGlobalBtn);
        topPanel.add(selectAllBtn);
        topPanel.add(unselectAllBtn);
        dialog.add(topPanel, BorderLayout.NORTH);

        final int finalTotalCols = totalCols;
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            joinSelectedColumns.clear();
            int selectedCount = 0;
            for (JCheckBox cb : allCheckBoxes) {
                if (cb.isSelected()) {
                    joinSelectedColumns.add(cb.getText());
                    selectedCount++;
                }
            }
            if (selectedCount == finalTotalCols || selectedCount == 0) {
                joinSelectedColumns.clear();
                joinSelectColsButton.setText("Columns (All)");
            } else {
                joinSelectColsButton.setText("Columns (" + selectedCount + ")");
            }
            dialog.dispose();
        });

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(okBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setSize(500, 550);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void loadDatabase() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            String host = "localhost";
            String port = "5432";
            String user = "postgres";
            String pass = "postgres";
            String dbName = "analyzer_db";

            if (selectedFile.getName().toLowerCase().endsWith(".sql") || selectedFile.getName().toLowerCase().endsWith(".sql.gz")) {
                // Show connection dialog for Postgres
                JTextField hostField = new JTextField(host);
                JTextField portField = new JTextField(port);
                JTextField userField = new JTextField(user);
                JPasswordField passField = new JPasswordField(pass);
                JTextField dbNameField = new JTextField(dbName);

                Object[] message = {
                    "PostgreSQL Host:", hostField,
                    "PostgreSQL Port:", portField,
                    "PostgreSQL Username:", userField,
                    "PostgreSQL Password:", passField,
                    "New Database Name:", dbNameField
                };

                int option = JOptionPane.showConfirmDialog(this, message, "Enter PostgreSQL Credentials", JOptionPane.OK_CANCEL_OPTION);
                if (option != JOptionPane.OK_OPTION) {
                    return; // User canceled
                }
                host = hostField.getText();
                port = portField.getText();
                user = userField.getText();
                pass = new String(passField.getPassword());
                dbName = dbNameField.getText();
            }
            
            final String finalHost = host;
            final String finalPort = port;
            final String finalUser = user;
            final String finalPass = pass;
            final String finalDbName = dbName;
            
            statusLabel.setText("Loading " + selectedFile.getName() + " to PostgreSQL...");
            statusLabel.setForeground(Color.BLUE);
            
            new Thread(() -> {
                try {
                    dbManager.connectPostgres(selectedFile.getAbsolutePath(), finalHost, finalPort, finalUser, finalPass, finalDbName);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Loaded: " + selectedFile.getName());
                        statusLabel.setForeground(new Color(0, 150, 0));
                        populateTablesUI();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Failed to load database.");
                        statusLabel.setForeground(Color.RED);
                        JOptionPane.showMessageDialog(Main.this, "Failed to connect or parse database:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        }
    }

    private void populateTablesUI() {
        loadedTables = dbManager.getTableNames();
        browseTableDropdown.setAllItems(loadedTables);
        baseTableDropdown.setAllItems(loadedTables);
        joinsContainer.removeAll();
        joinPanels.clear();
        joinsContainer.revalidate();
        joinsContainer.repaint();
        // Refresh new panels
        schemaPanel.refresh();
        tableComparePanel.refreshTables();
        sqlEditorPanel.refreshSavedQueries();
        if (dataOpsPanel != null) dataOpsPanel.refreshTables();
    }


    private void connectExistingDatabase() {
        java.util.Properties savedConnections = dbManager.getSavedConnections();
        JComboBox<String> profileDropdown = new JComboBox<>();
        profileDropdown.setEditable(true);
        JTextField editor = (JTextField) profileDropdown.getEditor().getEditorComponent();

        profileDropdown.addItem("-- New Connection --");
        for (String key : savedConnections.stringPropertyNames()) {
            profileDropdown.addItem(key);
        }

        editor.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent e) {
                char ch = e.getKeyChar();
                if (Character.isLetterOrDigit(ch) || Character.isSpaceChar(ch) || e.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    String filter = editor.getText();
                    String filterLower = filter.toLowerCase();
                    profileDropdown.hidePopup();
                    profileDropdown.removeAllItems();
                    profileDropdown.addItem("-- New Connection --");
                    for (String key : savedConnections.stringPropertyNames()) {
                        if (key.toLowerCase().contains(filterLower)) {
                            profileDropdown.addItem(key);
                        }
                    }
                    editor.setText(filter);
                    profileDropdown.showPopup();
                }
            }
        });

        JTextField hostField = new JTextField("localhost");
        JTextField portField = new JTextField("5432");
        JTextField userField = new JTextField("postgres");
        JPasswordField passField = new JPasswordField("postgres");
        JToggleButton showPassBtn = new JToggleButton("👁");
        showPassBtn.setMargin(new java.awt.Insets(0, 4, 0, 4));
        showPassBtn.setFocusable(false);
        showPassBtn.addActionListener(e -> {
            if (showPassBtn.isSelected()) {
                passField.setEchoChar((char) 0);
            } else {
                passField.setEchoChar('•');
            }
        });
        JPanel passPanel = new JPanel(new BorderLayout());
        passPanel.add(passField, BorderLayout.CENTER);
        passPanel.add(showPassBtn, BorderLayout.EAST);

        JTextField dbNameField = new JTextField("fisa");
        JCheckBox saveProfileCheck = new JCheckBox("Save this connection profile", true);
        
        profileDropdown.addActionListener(e -> {
            String selected = (String) profileDropdown.getSelectedItem();
            if (selected != null && !selected.equals("-- New Connection --")) {
                String val = savedConnections.getProperty(selected);
                if (val != null) {
                    String[] parts = val.split(";");
                    if (parts.length >= 5) {
                        hostField.setText(parts[0]);
                        portField.setText(parts[1]);
                        userField.setText(parts[2]);
                        try {
                            String decodedPass = new String(java.util.Base64.getDecoder().decode(parts[3]));
                            passField.setText(decodedPass);
                        } catch (Exception ex) {}
                        dbNameField.setText(parts[4]);
                    }
                }
            }
        });

        Object[] message = {
            "Search / Select Profile:", profileDropdown,
            " ",
            "PostgreSQL Host:", hostField,
            "PostgreSQL Port:", portField,
            "PostgreSQL Username:", userField,
            "PostgreSQL Password:", passPanel,
            "Database Name:", dbNameField,
            " ",
            saveProfileCheck
        };

        int option = JOptionPane.showConfirmDialog(this, message, "Connect to Existing Database", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) {
            return; // User canceled
        }
        
        final String finalHost = hostField.getText();
        final String finalPort = portField.getText();
        final String finalUser = userField.getText();
        final String finalPass = new String(passField.getPassword());
        final String finalDbName = dbNameField.getText();
        
        if (saveProfileCheck.isSelected()) {
            String profileName = finalUser + "@" + finalHost + ":" + finalPort + "/" + finalDbName;
            dbManager.saveConnectionProfile(profileName, finalHost, finalPort, finalUser, finalPass, finalDbName);
        }
        
        statusLabel.setText("Connecting to " + finalDbName + "...");
        statusLabel.setForeground(Color.BLUE);
        
        new Thread(() -> {
            try {
                dbManager.connectExistingPostgres(finalHost, finalPort, finalDbName, finalUser, finalPass);
                
                SwingUtilities.invokeLater(() -> {
                    try {
                        statusLabel.setText("Connected: " + finalDbName);
                        statusLabel.setForeground(new Color(0, 150, 0));
                        populateTablesUI();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        statusLabel.setText("Failed to load tables.");
                        statusLabel.setForeground(Color.RED);
                        JOptionPane.showMessageDialog(Main.this, "Failed to load tables:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Failed to connect.");
                    statusLabel.setForeground(Color.RED);
                    JOptionPane.showMessageDialog(Main.this, "Connection failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void loadTableData() {
        String selectedTable = (String) browseTableDropdown.getSelectedItem();
        if (selectedTable == null) return;
        
        String cols = "*";
        if (!currentSelectedColumns.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < currentSelectedColumns.size(); i++) {
                sb.append(dbManager.quoteColumnName(currentSelectedColumns.get(i)));
                if (i < currentSelectedColumns.size() - 1) sb.append(", ");
            }
            cols = sb.toString();
        }
        
        String orderClause = "";
        if (!browseOrderByPanels.isEmpty()) {
            StringBuilder osb = new StringBuilder(" ORDER BY ");
            for (int i = 0; i < browseOrderByPanels.size(); i++) {
                OrderByPanel op = browseOrderByPanels.get(i);
                String col = (String) op.columnDropdown.getSelectedItem();
                String dir = (String) op.dirDropdown.getSelectedItem();
                if (col != null) {
                    osb.append(dbManager.quoteColumnName(col)).append(" ").append(dir);
                    if (i < browseOrderByPanels.size() - 1) osb.append(", ");
                }
            }
            orderClause = osb.toString();
        }
        
        String distinctStr = browseDistinctCheckbox.isSelected() ? "DISTINCT " : "";
        
        lastBrowseBaseQuery = "SELECT " + distinctStr + cols + " FROM " + dbManager.quoteTableName(selectedTable)
                + buildBrowseWhereClause() + orderClause;
        
        browseCurrentOffset = 0;
        fetchBrowseData(false);
    }

    
    
    private void fetchBrowseData(boolean append) {
        if (isBrowseLoading || lastBrowseBaseQuery == null || lastBrowseBaseQuery.isEmpty()) return;
        
        String limitStr = limitField.getText().trim();
        Integer userLimit = null;
        if (!limitStr.isEmpty()) {
            try { userLimit = Integer.parseInt(limitStr); } catch (NumberFormatException ignored) {}
        }
        
        if (userLimit != null && append && browseCurrentOffset >= userLimit) {
            return;
        }
        
        isBrowseLoading = true;
        
        int fetchLimit = 1000;
        if (userLimit != null) {
            fetchLimit = Math.min(1000, userLimit - browseCurrentOffset);
        }
        
        String query = lastBrowseBaseQuery + " LIMIT " + fetchLimit + " OFFSET " + browseCurrentOffset;
        
        if (!append) {
            lastExecutedBrowseQuery = query;
            browseTable.setModel(new DefaultTableModel(new Object[][]{{"Loading..."}}, new String[]{"Status"}));
        }
        
        final String finalQuery = query;
        final int currentLimit = fetchLimit;
        
        new Thread(() -> {
            try {
                DefaultTableModel model = dbManager.executeQuery(finalQuery);
                SwingUtilities.invokeLater(() -> {
                    if (append) {
                        // Append rows if the model isn't "Loading..."
                        if (browseTable.getModel() instanceof DefaultTableModel && browseTable.getModel().getColumnCount() == model.getColumnCount()) {
                            DefaultTableModel existing = (DefaultTableModel) browseTable.getModel();
                            for (int r = 0; r < model.getRowCount(); r++) {
                                java.util.Vector<Object> row = new java.util.Vector<>();
                                for (int c = 0; c < model.getColumnCount(); c++) {
                                    row.add(model.getValueAt(r, c));
                                }
                                existing.addRow(row);
                            }
                        }
                    } else {
                        browseTable.setModel(model);
                    }
                    
                    if (model.getRowCount() > 0) {
                        browseCurrentOffset += currentLimit;
                    }
                    isBrowseLoading = false;
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    if (!append) browseTable.setModel(new DefaultTableModel(new Object[][]{{"Error"}}, new String[]{"Status"}));
                    isBrowseLoading = false;
                    String debugInfo = "Query: " + finalQuery + "\n";
                    try {
                        java.sql.ResultSet rs = dbManager.connection.createStatement().executeQuery("SELECT current_database(), current_schema()");
                        if (rs.next()) debugInfo += "DB: " + rs.getString(1) + ", Schema: " + rs.getString(2) + "\n";
                    } catch (Exception e) {}
                    JOptionPane.showMessageDialog(Main.this, debugInfo + "Error fetching data: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private String buildBrowseWhereClause() {
        if (browseWherePanels.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" WHERE ");
        for (int i = 0; i < browseWherePanels.size(); i++) {
            WherePanel wp = browseWherePanels.get(i);
            if (i > 0) sb.append(wp.logicDropdown.getSelectedItem()).append(" ");
            String tbl = (String) wp.tableDropdown.getSelectedItem();
            String col = (String) wp.columnDropdown.getSelectedItem();
            String op = (String) wp.operatorDropdown.getSelectedItem();
            String val = wp.valueField.getText();
            if (tbl != null && col != null) {
                sb.append(dbManager.quoteColumnName(tbl + "." + col)).append(" ").append(op).append(" ");
                if (!op.contains("NULL")) {
                    if ((op.equalsIgnoreCase("LIKE") || op.equalsIgnoreCase("ILIKE")) && !val.contains("%")) {
                        val = "%" + val + "%";
                    }
                    sb.append("'").append(val.replace("'", "''")).append("' ");
                }
            }
        }
        return sb.toString();
    }

    
    private void addBrowseOrderByRow() {
        String selectedTable = (String) browseTableDropdown.getSelectedItem();
        List<String> cols = selectedTable != null ? dbManager.getColumnNames(selectedTable) : new ArrayList<>();
        OrderByPanel op = new OrderByPanel(false, browseOrderByPanels, browseOrderByContainer);
        op.updateColumns(cols);
        browseOrderByPanels.add(op);
        browseOrderByContainer.add(op);
        browseOrderByContainer.revalidate();
        browseOrderByContainer.repaint();
    }

    private void addJoinOrderByRow() {
        OrderByPanel op = new OrderByPanel(true, joinOrderByPanels, joinOrderByContainer);
        joinOrderByPanels.add(op);
        joinOrderByContainer.add(op);
        joinOrderByContainer.revalidate();
        joinOrderByContainer.repaint();
        updateWhereDropdowns(); // Re-use this to update tables
    }

    private void addBrowseWhereRow() {
        boolean isFirst = browseWherePanels.isEmpty();
        String selectedTable = (String) browseTableDropdown.getSelectedItem();
        WherePanel wp = new WherePanel(isFirst, browseWherePanels, browseWhereContainer);
        List<String> tables = new ArrayList<>();
        if (selectedTable != null) tables.add(selectedTable);
        wp.updateTables(tables);
        if (selectedTable != null) wp.tableDropdown.setSelectedItem(selectedTable);
        browseWherePanels.add(wp);
        browseWhereContainer.add(wp);
        browseWhereContainer.revalidate();
        browseWhereContainer.repaint();
    }

    private void addJoinRow() {
        JoinPanel jp = new JoinPanel();
        joinPanels.add(jp);
        joinsContainer.add(jp);
        joinsContainer.revalidate();
        joinsContainer.repaint();
        updateJoinDropdowns();
    }

    private void addWhereRow() {
        boolean isFirst = wherePanels.isEmpty();
        WherePanel wp = new WherePanel(isFirst, wherePanels, whereContainer);
        wherePanels.add(wp);
        whereContainer.add(wp);
        whereContainer.revalidate();
        whereContainer.repaint();
        updateWhereDropdowns();
    }

    private void updateJoinDropdowns() {
        new Thread(() -> {
            // Collect all available tables from the base table and previous joins to populate the "Left" side of the ON clause
            List<String> availableLeftTables = new ArrayList<>();
            
            String baseTable = (String) baseTableDropdown.getSelectedItem();
            if (baseTable != null) {
                availableLeftTables.add(baseTable);
            }

            for (JoinPanel jp : joinPanels) {
                String joinTable = (String) jp.joinTableDropdown.getSelectedItem();
                if (joinTable != null) {
                    List<String> rightCols = new ArrayList<>(dbManager.getColumnNames(joinTable));
                    
                    Object currentLeftTable = jp.leftTableDropdown.getSelectedItem();
                    Object currentLeftCol = jp.leftColDropdown.getSelectedItem();
                    Object currentRightCol = jp.rightColDropdown.getSelectedItem();
                    boolean hadSelection = (currentLeftTable != null && currentLeftCol != null && currentRightCol != null);
                    
                    List<String> finalAvailableLeftTables = new ArrayList<>(availableLeftTables);
                    SwingUtilities.invokeLater(() -> {
                        jp.updateRightColumns(rightCols);
                        jp.updateLeftTables(finalAvailableLeftTables);
                    });
                    
                    if (!hadSelection || jp.leftColDropdown.getSelectedItem() == null) {
                        boolean found = false;
                        for (String leftTable : availableLeftTables) {
                            String[] match = dbManager.getLearnedJoin(leftTable, joinTable);
                            if (match == null) match = dbManager.getForeignKeyMatch(leftTable, joinTable);
                            if (match == null) match = dbManager.getHeuristicMatch(leftTable, joinTable);
                            if (match != null) {
                                String leftMatch = match[0];
                                if (leftMatch.startsWith(leftTable + ".")) leftMatch = leftMatch.substring(leftTable.length() + 1);
                                
                                String rightMatch = match[1];
                                if (rightMatch.startsWith(joinTable + ".")) rightMatch = rightMatch.substring(joinTable.length() + 1);
                                
                                final String finalLeftMatch = leftMatch;
                                final String finalRightMatch = rightMatch;
                                
                                SwingUtilities.invokeLater(() -> {
                                    jp.leftTableDropdown.setSelectedItem(leftTable);
                                    jp.leftColDropdown.setSelectedItem(finalLeftMatch);
                                    jp.rightColDropdown.setSelectedItem(finalRightMatch);
                                });
                                found = true;
                                break;
                            }
                        }
                        if (!found && !availableLeftTables.isEmpty()) {
                            SwingUtilities.invokeLater(() -> {
                                jp.leftTableDropdown.setSelectedItem(finalAvailableLeftTables.get(0));
                            });
                        }
                    }
                    
                    availableLeftTables.add(joinTable);
                }
            }
        }).start();
    }

    private void updateWhereDropdowns() {
        List<String> availableTables = new ArrayList<>();
        String baseTable = (String) baseTableDropdown.getSelectedItem();
        if (baseTable != null) {
            availableTables.add(baseTable);
        }
        for (JoinPanel jp : joinPanels) {
            String joinTable = (String) jp.joinTableDropdown.getSelectedItem();
            if (joinTable != null) {
                availableTables.add(joinTable);
            }
        }
        
        for (OrderByPanel op : joinOrderByPanels) {
            op.updateTables(availableTables);
        }

        for (WherePanel wp : wherePanels) {
            wp.updateTables(availableTables);
        }
    }


    private void saveVisualJoinProject() {
        VisualJoinState state = new VisualJoinState();
        state.baseTable = (String) baseTableDropdown.getSelectedItem();
        state.distinct = joinDistinctCheckbox.isSelected();
        state.selectedColumns.addAll(joinSelectedColumns);
        state.limit = joinLimitField.getText();
        
        for (JoinPanel jp : joinPanels) {
            VisualJoinState.JoinState js = new VisualJoinState.JoinState();
            js.type = (String) jp.joinTypeDropdown.getSelectedItem();
            js.table = (String) jp.joinTableDropdown.getSelectedItem();
            js.leftTable = (String) jp.leftTableDropdown.getSelectedItem();
            js.leftCol = (String) jp.leftColDropdown.getSelectedItem();
            js.rightCol = (String) jp.rightColDropdown.getSelectedItem();
            state.joins.add(js);
        }
        
        for (WherePanel wp : wherePanels) {
            VisualJoinState.WhereState ws = new VisualJoinState.WhereState();
            ws.logic = (String) wp.logicDropdown.getSelectedItem();
            ws.table = (String) wp.tableDropdown.getSelectedItem();
            ws.column = (String) wp.columnDropdown.getSelectedItem();
            ws.operator = (String) wp.operatorDropdown.getSelectedItem();
            ws.value = wp.valueField.getText();
            state.wheres.add(ws);
        }
        
        for (OrderByPanel op : joinOrderByPanels) {
            VisualJoinState.OrderByState os = new VisualJoinState.OrderByState();
            os.table = (String) op.tableDropdown.getSelectedItem();
            os.column = (String) op.columnDropdown.getSelectedItem();
            os.direction = (String) op.dirDropdown.getSelectedItem();
            state.orderBys.add(os);
        }
        
        VisualJoinProjectManager.saveProject(state, this);
    }
    
    private void loadVisualJoinProject() {
        VisualJoinState state = VisualJoinProjectManager.loadProject(this);
        if (state == null) return;
        
        // Reset current UI
        joinPanels.clear();
        joinsContainer.removeAll();
        wherePanels.clear();
        whereContainer.removeAll();
        joinOrderByPanels.clear();
        joinOrderByContainer.removeAll();
        joinSelectedColumns.clear();
        
        baseTableDropdown.setSelectedItem(state.baseTable);
        joinDistinctCheckbox.setSelected(state.distinct);
        joinLimitField.setText(state.limit != null ? state.limit : "");
        
        for (VisualJoinState.JoinState js : state.joins) {
            addJoinRow();
            JoinPanel jp = joinPanels.get(joinPanels.size() - 1);
            jp.joinTypeDropdown.setSelectedItem(js.type);
            jp.joinTableDropdown.setSelectedItem(js.table);
            jp.leftTableDropdown.setSelectedItem(js.leftTable);
            jp.leftColDropdown.setSelectedItem(js.leftCol);
            jp.rightColDropdown.setSelectedItem(js.rightCol);
        }
        
        for (VisualJoinState.WhereState ws : state.wheres) {
            addWhereRow();
            WherePanel wp = wherePanels.get(wherePanels.size() - 1);
            wp.logicDropdown.setSelectedItem(ws.logic);
            wp.tableDropdown.setSelectedItem(ws.table);
            wp.columnDropdown.setSelectedItem(ws.column);
            wp.operatorDropdown.setSelectedItem(ws.operator);
            wp.valueField.setText(ws.value);
        }
        
        for (VisualJoinState.OrderByState os : state.orderBys) {
            addJoinOrderByRow();
            OrderByPanel op = joinOrderByPanels.get(joinOrderByPanels.size() - 1);
            op.tableDropdown.setSelectedItem(os.table);
            op.columnDropdown.setSelectedItem(os.column);
            op.dirDropdown.setSelectedItem(os.direction);
        }
        
        joinSelectedColumns.addAll(state.selectedColumns);
                joinSelectColsButton.setText(joinSelectedColumns.isEmpty() ? "Columns (All)" : "Columns (" + joinSelectedColumns.size() + ")");
        joinsContainer.revalidate(); joinsContainer.repaint();
        whereContainer.revalidate(); whereContainer.repaint();
        joinOrderByContainer.revalidate(); joinOrderByContainer.repaint();
    }
    private void executeVisualJoin() {
        String baseTable = (String) baseTableDropdown.getSelectedItem();
        if (baseTable == null) return;

        String cols = "*";
        if (!joinSelectedColumns.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < joinSelectedColumns.size(); i++) {
                sb.append(dbManager.quoteColumnName(joinSelectedColumns.get(i)));
                if (i < joinSelectedColumns.size() - 1) sb.append(", ");
            }
            cols = sb.toString();
        }
        
        String distinctStr = joinDistinctCheckbox.isSelected() ? "DISTINCT " : "";
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(distinctStr).append(cols).append(" FROM ").append(dbManager.quoteTableName(baseTable)).append(" ");

        for (JoinPanel jp : joinPanels) {
            String type = (String) jp.joinTypeDropdown.getSelectedItem();
            String table = (String) jp.joinTableDropdown.getSelectedItem();
            String leftTable = (String) jp.leftTableDropdown.getSelectedItem();
            String leftCol = (String) jp.leftColDropdown.getSelectedItem();
            String rightCol = (String) jp.rightColDropdown.getSelectedItem();
            
            if (table != null && leftTable != null && leftCol != null && rightCol != null) {
                String leftFormatted = dbManager.quoteColumnName(leftTable + "." + leftCol);
                String rightFormatted = dbManager.quoteColumnName(table + "." + rightCol);

                sql.append(" ").append(type).append(" ").append(dbManager.quoteTableName(table)).append(" ");
                sql.append("ON ").append(leftFormatted).append(" = ").append(rightFormatted).append(" ");
            } else {
                JOptionPane.showMessageDialog(this, "Please make sure both 'Left' and 'Right' columns are selected for the join on table: " + table, "Incomplete Join", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (!wherePanels.isEmpty()) {
            sql.append("WHERE ");
            for (int i = 0; i < wherePanels.size(); i++) {
                WherePanel wp = wherePanels.get(i);
                if (i > 0) {
                    sql.append(wp.logicDropdown.getSelectedItem()).append(" ");
                }
                
                String tbl = (String) wp.tableDropdown.getSelectedItem();
                String col = (String) wp.columnDropdown.getSelectedItem();
                String op = (String) wp.operatorDropdown.getSelectedItem();
                String val = wp.valueField.getText();
                
                if (tbl != null && col != null) {
                    sql.append(dbManager.quoteColumnName(tbl + "." + col)).append(" ").append(op).append(" ");
                    if (!op.contains("NULL")) {
                        if ((op.equalsIgnoreCase("LIKE") || op.equalsIgnoreCase("ILIKE")) && !val.contains("%")) {
                            val = "%" + val + "%";
                        }
                        sql.append("'").append(val.replace("'", "''")).append("' ");
                    }
                }
            }
        }
        if (!joinOrderByPanels.isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < joinOrderByPanels.size(); i++) {
                OrderByPanel op = joinOrderByPanels.get(i);
                String tbl = (String) op.tableDropdown.getSelectedItem();
                String col = (String) op.columnDropdown.getSelectedItem();
                String dir = (String) op.dirDropdown.getSelectedItem();
                if (tbl != null && col != null) {
                    sql.append(dbManager.quoteColumnName(tbl + "." + col)).append(" ").append(dir);
                    if (i < joinOrderByPanels.size() - 1) sql.append(", ");
                }
            }
            if (sql.toString().endsWith(", ")) {
                sql.setLength(sql.length() - 2);
            }
            sql.append(" ");
        }
        
        String limitStr = joinLimitField.getText().trim();
        if (!limitStr.isEmpty()) {
            try { sql.append(" LIMIT ").append(Integer.parseInt(limitStr)); } catch (NumberFormatException ignored) {}
        }
        
        lastExecutedJoinQuery = sql.toString();
        
        try {
            System.out.println("Executing JOIN: " + lastExecutedJoinQuery);
            DefaultTableModel model = dbManager.executeQuery(lastExecutedJoinQuery);
            joinResultTable.setModel(model);
            
            // If execution succeeded, learn the joins!
            for (JoinPanel jp : joinPanels) {
                String table = (String) jp.joinTableDropdown.getSelectedItem();
                String leftTable = (String) jp.leftTableDropdown.getSelectedItem();
                String leftCol = (String) jp.leftColDropdown.getSelectedItem();
                String rightCol = (String) jp.rightColDropdown.getSelectedItem();
                
                if (table != null && leftTable != null && leftCol != null && rightCol != null) {
                    dbManager.learnJoin(leftTable, leftTable + "." + leftCol, table, table + "." + rightCol);
                }
            }
            
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "SQL Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Inner class representing a single Join row in the UI
    class JoinPanel extends JPanel {
        JComboBox<String> joinTypeDropdown = new JComboBox<>(new String[]{"JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN"});
        SearchableComboBox joinTableDropdown = new SearchableComboBox();
        SearchableComboBox leftTableDropdown = new SearchableComboBox();
        SearchableComboBox leftColDropdown = new SearchableComboBox();
        JLabel rightTableLabel = new JLabel("");
        SearchableComboBox rightColDropdown = new SearchableComboBox();

        public JoinPanel() {
            setLayout(new FlowLayout(FlowLayout.LEFT));
            
            joinTableDropdown.setAllItems(loadedTables);
            
            joinTableDropdown.addActionListener(e -> {
                String t = (String) joinTableDropdown.getSelectedItem();
                if (t != null) rightTableLabel.setText(t + " . ");
                joinSelectedColumns.clear();
                joinSelectColsButton.setText("Columns (All)");
                updateJoinDropdowns();
            });

            leftTableDropdown.addActionListener(e -> {
                String t = (String) leftTableDropdown.getSelectedItem();
                if (t != null) {
                    new Thread(() -> {
                        java.util.List<String> cols = dbManager.getColumnNames(t);
                        SwingUtilities.invokeLater(() -> {
                            Object currentCol = leftColDropdown.getSelectedItem();
                            leftColDropdown.setAllItems(cols);
                            if (currentCol != null && cols.contains(currentCol)) leftColDropdown.setSelectedItem(currentCol);
                        });
                    }).start();
                }
            });

            add(joinTypeDropdown);
            add(joinTableDropdown);
            add(new JLabel(" ON "));
            add(leftTableDropdown);
            add(new JLabel(" . "));
            add(leftColDropdown);
            add(new JLabel(" = "));
            add(rightTableLabel);
            add(rightColDropdown);
            
            JButton removeBtn = new JButton("X");
            removeBtn.addActionListener(e -> {
                joinPanels.remove(this);
                joinsContainer.remove(this);
                joinsContainer.revalidate();
                joinsContainer.repaint();
                joinSelectedColumns.clear();
                joinSelectColsButton.setText("Columns (All)");
                updateJoinDropdowns();
            });
            add(removeBtn);
        }

        public void updateLeftTables(List<String> tables) {
            Object selected = leftTableDropdown.getSelectedItem();
            leftTableDropdown.setAllItems(tables);
            if (selected != null && tables.contains(selected)) leftTableDropdown.setSelectedItem(selected);
        }

        public void updateRightColumns(List<String> cols) {
            Object selected = rightColDropdown.getSelectedItem();
            rightColDropdown.setAllItems(cols);
            if (selected != null && cols.contains(selected)) rightColDropdown.setSelectedItem(selected);
        }
    }

        class OrderByPanel extends JPanel {
        SearchableComboBox tableDropdown = new SearchableComboBox();
        SearchableComboBox columnDropdown = new SearchableComboBox();
        JComboBox<String> dirDropdown = new JComboBox<>(new String[]{"ASC", "DESC"});

        public OrderByPanel(boolean showTable, List<OrderByPanel> ownerList, JPanel ownerContainer) {
            setLayout(new FlowLayout(FlowLayout.LEFT));
            
            if (showTable) {
                tableDropdown.addActionListener(e -> {
                    String t = (String) tableDropdown.getSelectedItem();
                    if (t != null) {
                        new Thread(() -> {
                            java.util.List<String> cols = dbManager.getColumnNames(t);
                            SwingUtilities.invokeLater(() -> {
                                Object currentCol = columnDropdown.getSelectedItem();
                                columnDropdown.setAllItems(cols);
                                if (currentCol != null && cols.contains(currentCol)) columnDropdown.setSelectedItem(currentCol);
                            });
                        }).start();
                    }
                });
                add(tableDropdown);
                add(new JLabel(" . "));
            }
            
            add(columnDropdown);
            add(dirDropdown);
            
            JButton removeBtn = new JButton("X");
            removeBtn.addActionListener(e -> {
                ownerList.remove(this);
                ownerContainer.remove(this);
                ownerContainer.revalidate();
                ownerContainer.repaint();
            });
            add(removeBtn);
        }

        public void updateTables(List<String> tables) {
            Object selected = tableDropdown.getSelectedItem();
            tableDropdown.setAllItems(tables);
            if (selected != null && tables.contains(selected)) tableDropdown.setSelectedItem(selected);
        }

        public void updateColumns(List<String> cols) {
            Object selected = columnDropdown.getSelectedItem();
            columnDropdown.setAllItems(cols);
            if (selected != null && cols.contains(selected)) columnDropdown.setSelectedItem(selected);
        }
    }

class WherePanel extends JPanel {
        JComboBox<String> logicDropdown = new JComboBox<>(new String[]{"AND", "OR"});
        SearchableComboBox tableDropdown = new SearchableComboBox();
        SearchableComboBox columnDropdown = new SearchableComboBox();
        JComboBox<String> operatorDropdown = new JComboBox<>(new String[]{"=", "!=", ">", "<", ">=", "<=", "LIKE", "ILIKE", "IS NULL", "IS NOT NULL"});
        JTextField valueField = new JTextField(15);

        public WherePanel(boolean isFirst, List<WherePanel> ownerList, JPanel ownerContainer) {
            setLayout(new FlowLayout(FlowLayout.LEFT));
            
            if (!isFirst) {
                add(logicDropdown);
            }
            
            tableDropdown.addActionListener(e -> {
                String t = (String) tableDropdown.getSelectedItem();
                if (t != null) {
                    new Thread(() -> {
                        java.util.List<String> cols = dbManager.getColumnNames(t);
                        SwingUtilities.invokeLater(() -> {
                            Object currentCol = columnDropdown.getSelectedItem();
                            columnDropdown.setAllItems(cols);
                            if (currentCol != null && cols.contains(currentCol)) columnDropdown.setSelectedItem(currentCol);
                        });
                    }).start();
                }
            });
            
            add(tableDropdown);
            add(new JLabel(" . "));
            add(columnDropdown);
            add(operatorDropdown);
            add(valueField);
            
            operatorDropdown.addActionListener(e -> {
                String op = (String) operatorDropdown.getSelectedItem();
                valueField.setEnabled(!op.contains("NULL"));
            });
            
            JButton removeBtn = new JButton("X");
            removeBtn.addActionListener(e -> {
                int idx = ownerList.indexOf(this);
                ownerList.remove(this);
                ownerContainer.remove(this);
                // If we removed the first row and there are still rows remaining,
                // make sure the next first row no longer shows the logic dropdown
                if (idx == 0 && !ownerList.isEmpty()) {
                    WherePanel newFirst = ownerList.get(0);
                    newFirst.remove(newFirst.logicDropdown);
                    newFirst.revalidate();
                    newFirst.repaint();
                }
                ownerContainer.revalidate();
                ownerContainer.repaint();
            });
            add(removeBtn);
        }

        public void updateTables(List<String> tables) {
            Object selected = tableDropdown.getSelectedItem();
            tableDropdown.setAllItems(tables);
            if (selected != null && tables.contains(selected)) tableDropdown.setSelectedItem(selected);
        }
    }

    public static void main(String[] args) {
        com.dbtool.util.ThemeManager.init(null);
        SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }

    static class SearchableComboBox extends JComboBox<String> {
        private List<String> allItems = new ArrayList<>();
        private boolean isAdjusting = false;
        
        public SearchableComboBox() {
            super();
            setEditable(true);
            JTextField editor = (JTextField) getEditor().getEditorComponent();
            
            editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
                
                private void update() {
                    if (isAdjusting) return;
                    SwingUtilities.invokeLater(() -> {
                        isAdjusting = true;
                        String text = editor.getText();
                        int caret = editor.getCaretPosition();
                        
                        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                        for (String item : allItems) {
                            if (item.toLowerCase().contains(text.toLowerCase())) {
                                model.addElement(item);
                            }
                        }
                        model.setSelectedItem(text);
                        setModel(model);
                        editor.setText(text);
                        editor.setCaretPosition(Math.min(caret, text.length()));
                        
                        if (model.getSize() > 0 && editor.hasFocus()) {
                            showPopup();
                        } else {
                            hidePopup();
                        }
                        isAdjusting = false;
                    });
                }
            });

            this.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    if (isAdjusting) return;
                    
                    isAdjusting = true;
                    String text = editor.getText();
                    int caret = editor.getCaretPosition();
                    
                    if (allItems.contains(text) || text.isEmpty()) {
                        if (getModel().getSize() != allItems.size()) {
                            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                            for (String item : allItems) {
                                model.addElement(item);
                            }
                            setModel(model);
                            editor.setText(text);
                            editor.setCaretPosition(Math.min(caret, text.length()));
                            SwingUtilities.invokeLater(() -> showPopup());
                        }
                    }
                    isAdjusting = false;
                }
                @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
                @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
            });
        }
        
        public void setAllItems(List<String> items) {
            isAdjusting = true;
            this.allItems = new ArrayList<>(items);
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (String item : allItems) {
                model.addElement(item);
            }
            setModel(model);
            setSelectedIndex(-1);
            ((JTextField) getEditor().getEditorComponent()).setText("");
            isAdjusting = false;
        }

        @Override
        public void setSelectedItem(Object anObject) {
            boolean wasAdjusting = isAdjusting;
            isAdjusting = true;
            super.setSelectedItem(anObject);
            isAdjusting = wasAdjusting;
        }
    }
}
