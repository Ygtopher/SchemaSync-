package com.dbtool;

import com.dbtool.panels.SchemaPanel;
import com.dbtool.panels.SqlEditorPanel;
import com.dbtool.panels.TableComparePanel;
import com.dbtool.panels.ScriptBuilderPanel;
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

public class Main extends JFrame {

    private DatabaseManager dbManager = new DatabaseManager();
    private JLabel statusLabel = new JLabel("No database loaded.");
    
    // Browse Tab Components
    private JComboBox<String> browseTableDropdown = new JComboBox<>();
    private JButton selectColsButton = new JButton("Columns (All)");
    private List<String> currentSelectedColumns = new ArrayList<>();
    private JTextField limitField = new JTextField(5);
    private JTextField browseSearchField = new JTextField(20);
    private JTextField browseTableSearchField = new JTextField(12);
    private JTable browseTable = new JTable();
    private JPanel browseWhereContainer = new JPanel();
    private List<WherePanel> browseWherePanels = new ArrayList<>();
    private JComboBox<String> orderByDropdown = new JComboBox<>();
    private JComboBox<String> orderDirDropdown = new JComboBox<>(new String[]{"ASC", "DESC"});
    private String lastExecutedBrowseQuery = "";
    // Join Tab Components
    private JComboBox<String> baseTableDropdown = new JComboBox<>();
    private JPanel joinsContainer = new JPanel();
    private List<JoinPanel> joinPanels = new ArrayList<>();
    private JTable joinResultTable = new JTable();
    private JButton joinSelectColsButton = new JButton("Columns (All)");
    private List<String> joinSelectedColumns = new ArrayList<>();
    private JTextField joinLimitField = new JTextField(5);
    private String lastExecutedJoinQuery = "";
    private JPanel whereContainer = new JPanel();
    private List<WherePanel> wherePanels = new ArrayList<>();
    
    // New panels
    private SchemaPanel schemaPanel;
    private SqlEditorPanel sqlEditorPanel;
    private TableComparePanel tableComparePanel;
    private ScriptBuilderPanel scriptBuilderPanel;
    
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

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Browse & Search", createBrowsePanel());
        tabbedPane.addTab("Visual Join Builder", createJoinPanel());
        tabbedPane.addTab("SQL Editor", sqlEditorPanel);
        tabbedPane.addTab("Script Builder", scriptBuilderPanel);
        tabbedPane.addTab("Table Compare", tableComparePanel);

        // Main layout: Schema sidebar left, tabs center
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, schemaPanel, tabbedPane);
        mainSplit.setDividerLocation(230);
        mainSplit.setResizeWeight(0.0);
        add(mainSplit, BorderLayout.CENTER);
    }

    private JPanel createBrowsePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Row 1: Table selector + column selector + search + limit + where
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Table:"));
        browseTableDropdown.addActionListener(e -> {
            currentSelectedColumns.clear();
            selectColsButton.setText("Columns (All)");
            browseWherePanels.clear();
            browseWhereContainer.removeAll();
            browseWhereContainer.revalidate();
            browseWhereContainer.repaint();
            refreshOrderByDropdown();
            loadTableData();
        });
        row1.add(browseTableDropdown);
        
        // Table search filter
        browseTableSearchField.setToolTipText("Filter table list...");
        browseTableSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterBrowseTableList(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterBrowseTableList(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterBrowseTableList(); }
        });
        row1.add(new JLabel("Filter:"));
        row1.add(browseTableSearchField);

        selectColsButton.addActionListener(e -> openColumnSelector());
        row1.add(selectColsButton);
        
        row1.add(new JLabel("Search:"));
        row1.add(browseSearchField);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> performSearch());
        row1.add(searchBtn);
        
        limitField.setToolTipText("Limit (empty for none)");
        row1.add(new JLabel("Limit:"));
        row1.add(limitField);
        
        JButton addWhereBtn = new JButton("+ Add Where");
        addWhereBtn.addActionListener(e -> addBrowseWhereRow());
        row1.add(addWhereBtn);

        // Row 2: ORDER BY + export + edit actions
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Order By:"));
        row2.add(orderByDropdown);
        row2.add(orderDirDropdown);
        JButton applyOrderBtn = new JButton("Apply");
        applyOrderBtn.addActionListener(e -> loadTableData());
        row2.add(applyOrderBtn);
        row2.add(new JSeparator(SwingConstants.VERTICAL));

        JButton exportCsvBtn = new JButton("📥 CSV");
        JButton exportXlsxBtn = new JButton("📥 Excel");
        JButton exportInsertBtn = new JButton("📥 SQL Inserts");
        JButton copyBtn = new JButton("📋 Copy Data");
        JButton copySqlBtn = new JButton("📋 Copy SQL");
        exportCsvBtn.addActionListener(e -> ExportUtil.exportCsv(browseTable, this));
        exportXlsxBtn.addActionListener(e -> ExportUtil.exportExcel(browseTable, this));
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
        row2.add(exportCsvBtn);
        row2.add(exportXlsxBtn);
        row2.add(exportInsertBtn);
        row2.add(copyBtn);
        row2.add(copySqlBtn);
        row2.add(copyBtn);
        row2.add(new JSeparator(SwingConstants.VERTICAL));

        JButton insertRowBtn = new JButton("➕ Insert Row");
        JButton deleteRowBtn = new JButton("🗑 Delete Row");
        insertRowBtn.addActionListener(e -> openInsertRowDialog());
        deleteRowBtn.addActionListener(e -> deleteSelectedRow());
        row2.add(insertRowBtn);
        row2.add(deleteRowBtn);

        // Enable sorting on browseTable
        browseTable.setAutoCreateRowSorter(true);

        browseWhereContainer.setLayout(new BoxLayout(browseWhereContainer, BoxLayout.Y_AXIS));
        
        JPanel northPanel = new JPanel(new BorderLayout());
        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.add(row1);
        rowsPanel.add(row2);
        northPanel.add(rowsPanel, BorderLayout.NORTH);
        northPanel.add(browseWhereContainer, BorderLayout.CENTER);
        
        panel.add(northPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(browseTable), BorderLayout.CENTER);
        
        return panel;
    }

    private void filterBrowseTableList() {
        String filter = browseTableSearchField.getText().toLowerCase().trim();
        browseTableDropdown.removeAllItems();
        for (String tbl : loadedTables) {
            if (filter.isEmpty() || tbl.toLowerCase().contains(filter)) browseTableDropdown.addItem(tbl);
        }
    }

    private void refreshOrderByDropdown() {
        orderByDropdown.removeAllItems();
        orderByDropdown.addItem("(none)");
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl != null) for (String col : dbManager.getColumnNames(tbl)) orderByDropdown.addItem(col);
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
        JPanel basePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        basePanel.add(new JLabel("Base Table:"));
        baseTableDropdown.addActionListener(e -> {
            joinSelectedColumns.clear();
            joinSelectColsButton.setText("Columns (All)");
            updateJoinDropdowns();
        });
        basePanel.add(baseTableDropdown);
        JButton addJoinBtn = new JButton("+ Add Join");
        addJoinBtn.addActionListener(e -> addJoinRow());
        basePanel.add(addJoinBtn);
        JButton addWhereBtn = new JButton("+ Add Where");
        addWhereBtn.addActionListener(e -> addWhereRow());
        basePanel.add(addWhereBtn);
        configPanel.add(basePanel, BorderLayout.NORTH);
        
        // Dynamic Joins and Where List
        joinsContainer.setLayout(new BoxLayout(joinsContainer, BoxLayout.Y_AXIS));
        whereContainer.setLayout(new BoxLayout(whereContainer, BoxLayout.Y_AXIS));
        
        JPanel listsPanel = new JPanel();
        listsPanel.setLayout(new BoxLayout(listsPanel, BoxLayout.Y_AXIS));
        listsPanel.add(joinsContainer);
        listsPanel.add(whereContainer);
        
        configPanel.add(new JScrollPane(listsPanel), BorderLayout.CENTER);
        
        // Run Button
        JPanel runPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        joinSelectColsButton.addActionListener(e -> openJoinColumnSelector());
        runPanel.add(joinSelectColsButton);
        joinLimitField.setToolTipText("Limit (empty for none)");
        runPanel.add(new JLabel("Limit:"));
        runPanel.add(joinLimitField);
        JButton copySqlBtn = new JButton("📋 Copy SQL");
        copySqlBtn.addActionListener(e -> {
            if (!lastExecutedJoinQuery.isEmpty()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(lastExecutedJoinQuery), null);
                JOptionPane.showMessageDialog(this, "SQL copied to clipboard!");
            } else {
                JOptionPane.showMessageDialog(this, "No query executed yet.");
            }
        });
        runPanel.add(copySqlBtn);
        
        JButton runJoinBtn = new JButton("Execute Join");
        runJoinBtn.addActionListener(e -> executeVisualJoin());
        runPanel.add(runJoinBtn);
        configPanel.add(runPanel, BorderLayout.SOUTH);
        
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
        browseTableDropdown.removeAllItems();
        baseTableDropdown.removeAllItems();
        for (String tbl : loadedTables) {
            browseTableDropdown.addItem(tbl);
            baseTableDropdown.addItem(tbl);
        }
        joinsContainer.removeAll();
        joinPanels.clear();
        joinsContainer.revalidate();
        joinsContainer.repaint();
        // Refresh new panels
        schemaPanel.refresh();
        tableComparePanel.refreshTables();
        sqlEditorPanel.refreshSavedQueries();
    }


    private void connectExistingDatabase() {
        String host = "localhost";
        String port = "5432";
        String user = "postgres";
        String pass = "postgres";
        String dbName = "fisa";

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
            "Database Name:", dbNameField
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
        
        String orderCol = (String) orderByDropdown.getSelectedItem();
        String orderClause = "";
        if (orderCol != null && !orderCol.equals("(none)")) {
            orderClause = " ORDER BY " + dbManager.quoteColumnName(orderCol) + " " + orderDirDropdown.getSelectedItem();
        }
        String limitStr = limitField.getText().trim();
        String limitClause = "";
        if (!limitStr.isEmpty()) {
            try { limitClause = " LIMIT " + Integer.parseInt(limitStr); } catch (NumberFormatException ignored) {}
        }
        
        String query = "";
        try {
            query = "SELECT " + cols + " FROM " + dbManager.quoteTableName(selectedTable)
                    + buildBrowseWhereClause() + orderClause + limitClause;
            lastExecutedBrowseQuery = query;
            DefaultTableModel model = dbManager.executeQuery(query);
            browseTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            String debugInfo = "Query: " + query + "\n";
            try {
                java.sql.ResultSet rs = dbManager.connection.createStatement().executeQuery("SELECT current_database(), current_schema()");
                if (rs.next()) {
                    debugInfo += "DB: " + rs.getString(1) + ", Schema: " + rs.getString(2) + "\n";
                }
            } catch (Exception e) {}
            JOptionPane.showMessageDialog(this, debugInfo + "Error loading data: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void performSearch() {
        String selectedTable = (String) browseTableDropdown.getSelectedItem();
        String keyword = browseSearchField.getText().trim();
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
        
        String orderCol = (String) orderByDropdown.getSelectedItem();
        String orderClause = "";
        if (orderCol != null && !orderCol.equals("(none)")) {
            orderClause = " ORDER BY " + dbManager.quoteColumnName(orderCol) + " " + orderDirDropdown.getSelectedItem();
        }
        String limitStr = limitField.getText().trim();
        String limitClause = "";
        if (!limitStr.isEmpty()) {
            try { limitClause = " LIMIT " + Integer.parseInt(limitStr); } catch (NumberFormatException ignored) {}
        }
        
        String query = "";
        try {
            String whereClause = buildBrowseWhereClause();
            DefaultTableModel model;
            if (keyword.isEmpty()) {
                query = "SELECT " + cols + " FROM " + dbManager.quoteTableName(selectedTable) + whereClause + orderClause + limitClause;
                model = dbManager.executeQuery(query);
            } else {
                // If there's a where clause, we need to build the full query manually
                if (!whereClause.isEmpty()) {
                    query = "SELECT " + cols + " FROM " + dbManager.quoteTableName(selectedTable)
                            + whereClause + " AND CAST(" + dbManager.quoteTableName(selectedTable) + "::text AS TEXT) ILIKE '%" + keyword.replace("'", "''") + "%'" + orderClause + limitClause;
                    model = dbManager.executeQuery(query);
                } else {
                    query = "SELECT " + cols + " FROM " + dbManager.quoteTableName(selectedTable)
                            + " WHERE CAST(" + dbManager.quoteTableName(selectedTable) + "::text AS TEXT) ILIKE '%" + keyword.replace("'", "''") + "%'" + orderClause + limitClause;
                    model = dbManager.executeQuery(query);
                }
            }
            lastExecutedBrowseQuery = query;
            browseTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            String debugInfo = "Query: " + query + "\n";
            try {
                java.sql.ResultSet rs = dbManager.connection.createStatement().executeQuery("SELECT current_database(), current_schema()");
                if (rs.next()) {
                    debugInfo += "DB: " + rs.getString(1) + ", Schema: " + rs.getString(2) + "\n";
                }
            } catch (Exception e) {}
            JOptionPane.showMessageDialog(this, debugInfo + "Error searching data: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String buildBrowseWhereClause() {
        if (browseWherePanels.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" WHERE ");
        for (int i = 0; i < browseWherePanels.size(); i++) {
            WherePanel wp = browseWherePanels.get(i);
            if (i > 0) sb.append(wp.logicDropdown.getSelectedItem()).append(" ");
            String col = (String) wp.columnDropdown.getSelectedItem();
            String op = (String) wp.operatorDropdown.getSelectedItem();
            String val = wp.valueField.getText();
            if (col != null) {
                sb.append(dbManager.quoteColumnName(col)).append(" ").append(op).append(" ");
                if (!op.contains("NULL")) {
                    sb.append("'").append(val.replace("'", "''")).append("' ");
                }
            }
        }
        return sb.toString();
    }

    private void addBrowseWhereRow() {
        boolean isFirst = browseWherePanels.isEmpty();
        String selectedTable = (String) browseTableDropdown.getSelectedItem();
        List<String> cols = selectedTable != null ? dbManager.getColumnNames(selectedTable) : new ArrayList<>();
        WherePanel wp = new WherePanel(isFirst, browseWherePanels, browseWhereContainer);
        wp.updateColumns(cols);
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
        // Collect all available tables from the base table and previous joins to populate the "Left" side of the ON clause
        List<String> availableLeftColumns = new ArrayList<>();
        
        String baseTable = (String) baseTableDropdown.getSelectedItem();
        if (baseTable != null) {
            for (String col : dbManager.getColumnNames(baseTable)) {
                availableLeftColumns.add(baseTable + "." + col);
            }
        }

        for (JoinPanel jp : joinPanels) {
            String joinTable = (String) jp.joinTableDropdown.getSelectedItem();
            if (joinTable != null) {
                // The right side columns are strictly from the joined table
                List<String> rightCols = new ArrayList<>();
                for (String col : dbManager.getColumnNames(joinTable)) {
                    rightCols.add(joinTable + "." + col);
                }
                jp.updateRightColumns(rightCols);
                
                // Now this joinTable becomes available for the left side of subsequent joins
                jp.updateLeftColumns(new ArrayList<>(availableLeftColumns));
                availableLeftColumns.addAll(rightCols);
            }
        }
        updateWhereDropdowns();
    }

    private void updateWhereDropdowns() {
        List<String> allCols = new ArrayList<>();
        String baseTable = (String) baseTableDropdown.getSelectedItem();
        if (baseTable != null) {
            for (String col : dbManager.getColumnNames(baseTable)) {
                allCols.add(baseTable + "." + col);
            }
        }
        for (JoinPanel jp : joinPanels) {
            String joinTable = (String) jp.joinTableDropdown.getSelectedItem();
            if (joinTable != null) {
                for (String col : dbManager.getColumnNames(joinTable)) {
                    allCols.add(joinTable + "." + col);
                }
            }
        }
        for (WherePanel wp : wherePanels) {
            wp.updateColumns(allCols);
        }
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
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(cols).append(" FROM ").append(dbManager.quoteTableName(baseTable)).append(" ");

        for (JoinPanel jp : joinPanels) {
            String type = (String) jp.joinTypeDropdown.getSelectedItem();
            String table = (String) jp.joinTableDropdown.getSelectedItem();
            String leftCol = (String) jp.leftColDropdown.getSelectedItem();
            String rightCol = (String) jp.rightColDropdown.getSelectedItem();
            
            if (table != null && leftCol != null && rightCol != null) {
                String leftFormatted = dbManager.quoteColumnName(leftCol);
                String rightFormatted = dbManager.quoteColumnName(rightCol);

                sql.append(type).append(" JOIN ").append(dbManager.quoteTableName(table)).append(" ");
                sql.append("ON ").append(leftFormatted).append(" = ").append(rightFormatted).append(" ");
            }
        }
        if (!wherePanels.isEmpty()) {
            sql.append("WHERE ");
            for (int i = 0; i < wherePanels.size(); i++) {
                WherePanel wp = wherePanels.get(i);
                if (i > 0) {
                    sql.append(wp.logicDropdown.getSelectedItem()).append(" ");
                }
                
                String col = (String) wp.columnDropdown.getSelectedItem();
                String op = (String) wp.operatorDropdown.getSelectedItem();
                String val = wp.valueField.getText();
                
                if (col != null) {
                    sql.append(dbManager.quoteColumnName(col)).append(" ").append(op).append(" ");
                    if (!op.contains("NULL")) {
                        sql.append("'").append(val.replace("'", "''")).append("' ");
                    }
                }
            }
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
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "SQL Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Inner class representing a single Join row in the UI
    class JoinPanel extends JPanel {
        JComboBox<String> joinTypeDropdown = new JComboBox<>(new String[]{"INNER", "LEFT", "RIGHT"});
        JComboBox<String> joinTableDropdown = new JComboBox<>();
        JComboBox<String> leftColDropdown = new JComboBox<>();
        JComboBox<String> rightColDropdown = new JComboBox<>();

        public JoinPanel() {
            setLayout(new FlowLayout(FlowLayout.LEFT));
            
            for (String tbl : loadedTables) {
                joinTableDropdown.addItem(tbl);
            }
            
            joinTableDropdown.addActionListener(e -> {
                joinSelectedColumns.clear();
                joinSelectColsButton.setText("Columns (All)");
                updateJoinDropdowns();
            });

            add(joinTypeDropdown);
            add(new JLabel("JOIN"));
            add(joinTableDropdown);
            add(new JLabel("ON"));
            add(leftColDropdown);
            add(new JLabel("="));
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

        public void updateLeftColumns(List<String> cols) {
            Object selected = leftColDropdown.getSelectedItem();
            leftColDropdown.removeAllItems();
            for (String col : cols) leftColDropdown.addItem(col);
            if (selected != null && cols.contains(selected)) leftColDropdown.setSelectedItem(selected);
        }

        public void updateRightColumns(List<String> cols) {
            Object selected = rightColDropdown.getSelectedItem();
            rightColDropdown.removeAllItems();
            for (String col : cols) rightColDropdown.addItem(col);
            if (selected != null && cols.contains(selected)) rightColDropdown.setSelectedItem(selected);
        }
    }

    class WherePanel extends JPanel {
        JComboBox<String> logicDropdown = new JComboBox<>(new String[]{"AND", "OR"});
        JComboBox<String> columnDropdown = new JComboBox<>();
        JComboBox<String> operatorDropdown = new JComboBox<>(new String[]{"=", "!=", ">", "<", ">=", "<=", "LIKE", "ILIKE", "IS NULL", "IS NOT NULL"});
        JTextField valueField = new JTextField(15);

        public WherePanel(boolean isFirst, List<WherePanel> ownerList, JPanel ownerContainer) {
            setLayout(new FlowLayout(FlowLayout.LEFT));
            
            if (!isFirst) {
                add(logicDropdown);
            }
            
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

        public void updateColumns(List<String> cols) {
            Object selected = columnDropdown.getSelectedItem();
            columnDropdown.removeAllItems();
            for (String col : cols) columnDropdown.addItem(col);
            if (selected != null && cols.contains(selected)) columnDropdown.setSelectedItem(selected);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }
}
