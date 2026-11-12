package com.dbtool;

import com.dbtool.panels.SchemaPanel;
import com.dbtool.panels.SqlEditorPanel;
import com.dbtool.panels.ErDiagramPanel;
import com.dbtool.panels.TableComparePanel;
import com.dbtool.panels.ScriptBuilderPanel;
import com.dbtool.panels.DataOpsPanel;
import com.dbtool.util.ExportUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import com.dbtool.model.VisualJoinState;
import com.dbtool.util.VisualJoinProjectManager;
import com.dbtool.util.SqlSnippetsLibrary;
import com.dbtool.panels.SqlMasteryReferenceDialog;

public class Main extends JFrame {
    public static final String APP_VERSION = "2.0.0";
    private JTabbedPane tabbedPane;
    private com.dbtool.panels.MultiTabContainer<BrowseTab> browseTabContainer;
    private com.dbtool.panels.MultiTabContainer<VisualJoinTab> joinTabContainer;
    private com.dbtool.panels.QueryLogPanel queryLogPanel;
    private JSplitPane centerSplit;
    private JButton toggleQueryLogBtn;
    private boolean isQueryLogMinimized = false;
    private int queryLogExpandedHeight = 220;
    private boolean isAdjustingQueryLogSplit = false;

    public class BrowseTab extends com.dbtool.panels.BrowseTabPanel {
        public BrowseTab() { super(Main.this); }
    }

    public class VisualJoinTab extends com.dbtool.panels.VisualJoinTabPanel {
        public VisualJoinTab() { super(Main.this); }
    }

    public DatabaseManager getDbManager() { return dbManager; }
    public List<String> getLoadedTables() { return loadedTables; }
    public JTabbedPane getTabbedPane() { return tabbedPane; }
    public SqlEditorPanel getSqlEditorPanel() { return sqlEditorPanel; }


    public void showExplainMenu(JComponent invoker, java.util.function.Supplier<String> sqlSupplier) {
        JPopupMenu menu = new JPopupMenu();
        String[] options = {
            "EXPLAIN",
            "EXPLAIN (ANALYZE, BUFFERS)",
            "EXPLAIN (ANALYZE, COSTS, VERBOSE)",
            "EXPLAIN (FORMAT JSON)",
            "EXPLAIN (ANALYZE, FORMAT JSON)"
        };
        for (String opt : options) {
            JMenuItem item = new JMenuItem("🔍 " + opt);
            item.addActionListener(e -> {
                String baseSql = sqlSupplier.get();
                if (baseSql == null || baseSql.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please configure or run a query first.");
                    return;
                }
                String explainSql = opt + " " + baseSql;
                int choice = JOptionPane.showOptionDialog(this,
                    "Execution Plan Query:\n\n" + explainSql + "\n\nChoose an action:",
                    "Explain Execution Plan",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"Execute in SQL Editor", "Copy to Clipboard", "Cancel"},
                    "Execute in SQL Editor");
                if (choice == 0) {
                    if (sqlEditorPanel != null) {
                        sqlEditorPanel.setEditorText(explainSql);
                        tabbedPane.setSelectedComponent(sqlEditorPanel);
                    }
                } else if (choice == 1) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(explainSql), null);
                    JOptionPane.showMessageDialog(this, "EXPLAIN query copied to clipboard!");
                }
            });
            menu.add(item);
        }
        menu.show(invoker, 0, invoker.getHeight());
    }


        public class UpdatableTable extends JTable {
        private java.util.function.Supplier<String> tableNameSupplier;
        private final java.util.Map<String, java.util.Vector<String>> fkOptionsCache = new java.util.concurrent.ConcurrentHashMap<>();

        public UpdatableTable(java.util.function.Supplier<String> tableNameSupplier) {
            super();
            this.tableNameSupplier = tableNameSupplier;
            this.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            this.setShowGrid(true);
            this.setShowHorizontalLines(true);
            this.setShowVerticalLines(true);
            this.setIntercellSpacing(new Dimension(1, 1));
            this.setGridColor(new javax.swing.plaf.ColorUIResource(com.dbtool.util.ThemeManager.getTableGridColor()));
            this.setBackground(new javax.swing.plaf.ColorUIResource(com.dbtool.util.ThemeManager.getTableBackgroundColor()));
            this.setForeground(new javax.swing.plaf.ColorUIResource(com.dbtool.util.ThemeManager.isDarkMode() ? new Color(241, 245, 249) : new Color(15, 23, 42)));
            com.dbtool.ui.components.SmartTableCellRenderer.install(
                this,
                col -> {
                    String t = this.tableNameSupplier != null ? this.tableNameSupplier.get() : null;
                    return t != null && dbManager != null && dbManager.getPrimaryKeys(t).contains(col);
                },
                col -> {
                    String t = this.tableNameSupplier != null ? this.tableNameSupplier.get() : null;
                    return t != null && dbManager != null && dbManager.getCachedForeignKeys(t).containsKey(col);
                }
            );
            
            JPopupMenu popupMenu = new JPopupMenu();
            JMenuItem autoFitItem = new JMenuItem("⚡ Auto-Fit All Columns to Content");
            autoFitItem.setToolTipText("Automatically adjust all column widths to match data contents and headers");
            autoFitItem.addActionListener(e -> com.dbtool.ui.components.SmartTableColumnAdjuster.adjustColumnWidths(this));
            popupMenu.add(autoFitItem);
            popupMenu.addSeparator();

            JMenuItem editItem = new JMenuItem("Edit Cell");
            editItem.addActionListener(e -> {
                int row = this.getSelectedRow();
                int col = this.getSelectedColumn();
                if (row >= 0 && col >= 0) {
                    this.editCellAt(row, col, null);
                    java.awt.Component editor = this.getEditorComponent();
                    if (editor != null) {
                        editor.requestFocus();
                    }
                }
            });
            popupMenu.add(editItem);
            
            JMenuItem copyItem = new JMenuItem("Copy Cell");
            copyItem.addActionListener(e -> {
                int row = this.getSelectedRow();
                int col = this.getSelectedColumn();
                if (row >= 0 && col >= 0) {
                    Object val = this.getValueAt(row, col);
                    if (val != null) {
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                            new java.awt.datatransfer.StringSelection(val.toString()), null);
                    }
                }
            });
            popupMenu.add(copyItem);
            
            JMenuItem copyInsertItem = new JMenuItem("Copy Row as SQL INSERT");
            copyInsertItem.addActionListener(e -> {
                int[] rows = this.getSelectedRows();
                if (rows.length == 0 || tableNameSupplier == null) return;
                String tableName = tableNameSupplier.get();
                if (tableName == null) return;
                
                String sql = com.dbtool.util.SqlInsertGenerator.generateInsertStatements(this, rows, tableName, dbManager, true);
                if (!sql.isEmpty()) {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(sql), null);
                }
            });
            popupMenu.add(copyInsertItem);

            JMenuItem copyInsertWithPkItem = new JMenuItem("Copy Row as SQL INSERT (Include Primary Key)");
            copyInsertWithPkItem.addActionListener(e -> {
                int[] rows = this.getSelectedRows();
                if (rows.length == 0 || tableNameSupplier == null) return;
                String tableName = tableNameSupplier.get();
                if (tableName == null) return;
                
                String sql = com.dbtool.util.SqlInsertGenerator.generateInsertStatements(this, rows, tableName, dbManager, false);
                if (!sql.isEmpty()) {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(sql), null);
                }
            });
            popupMenu.add(copyInsertWithPkItem);

            JMenuItem copyUpdateItem = new JMenuItem("Copy Row as SQL UPDATE");
            copyUpdateItem.addActionListener(e -> {
                int[] rows = this.getSelectedRows();
                if (rows.length == 0 || tableNameSupplier == null) return;
                String tableName = tableNameSupplier.get();
                if (tableName == null) return;

                String sql = com.dbtool.util.SqlInsertGenerator.generateUpdateStatements(this, rows, tableName, dbManager);
                if (!sql.isEmpty()) {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(sql), null);
                }
            });
            popupMenu.add(copyUpdateItem);

            JMenuItem copyJsonItem = new JMenuItem("Copy Selected as JSON Array");
            copyJsonItem.addActionListener(e -> {
                int[] rows = this.getSelectedRows();
                if (rows.length == 0) return;
                String json = com.dbtool.util.SqlInsertGenerator.generateJsonArray(this, rows);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(json), null);
            });
            popupMenu.add(copyJsonItem);

            JMenuItem copyMarkdownItem = new JMenuItem("Copy Selected as Markdown Table");
            copyMarkdownItem.addActionListener(e -> {
                int[] rows = this.getSelectedRows();
                if (rows.length == 0) return;
                String md = com.dbtool.util.SqlInsertGenerator.generateMarkdownTable(this, rows);
                if (!md.isEmpty()) {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(md), null);
                }
            });
            popupMenu.add(copyMarkdownItem);
            
            JMenuItem exportCsvItem = new JMenuItem("Export Selected to CSV");
            exportCsvItem.addActionListener(e -> {
                int[] rows = this.getSelectedRows();
                if (rows.length == 0) return;
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < getColumnCount(); c++) {
                    sb.append("\"").append(getColumnName(c)).append("\"").append(c < getColumnCount() - 1 ? "," : "");
                }
                sb.append("\n");
                for (int row : rows) {
                    for (int c = 0; c < getColumnCount(); c++) {
                        Object val = getValueAt(row, c);
                        String s = val == null ? "" : val.toString().replace("\"", "\"\"");
                        sb.append("\"").append(s).append("\"").append(c < getColumnCount() - 1 ? "," : "");
                    }
                    sb.append("\n");
                }
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(sb.toString()), null);
                JOptionPane.showMessageDialog(this, "CSV copied to clipboard!");
            });
            popupMenu.add(exportCsvItem);
            
            JMenuItem exportJsonItem = new JMenuItem("Export Selected to JSON");
            exportJsonItem.addActionListener(e -> {
                int[] rows = this.getSelectedRows();
                if (rows.length == 0) return;
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.node.ArrayNode arrayNode = mapper.createArrayNode();
                    for (int row : rows) {
                        com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
                        for (int c = 0; c < getColumnCount(); c++) {
                            Object val = getValueAt(row, c);
                            if (val == null) {
                                node.putNull(getColumnName(c));
                            } else if (val instanceof Number) {
                                node.put(getColumnName(c), ((Number)val).doubleValue());
                            } else if (val instanceof Boolean) {
                                node.put(getColumnName(c), (Boolean)val);
                            } else {
                                node.put(getColumnName(c), val.toString());
                            }
                        }
                        arrayNode.add(node);
                    }
                    String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode);
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(json), null);
                    JOptionPane.showMessageDialog(this, "JSON copied to clipboard!");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            popupMenu.add(exportJsonItem);
            popupMenu.addSeparator();
            JMenuItem cheatSheetItem = new JMenuItem("💡 SQL Mastery Reference & Templates...");
            cheatSheetItem.addActionListener(e -> {
                new SqlMasteryReferenceDialog(Main.this, sql -> {
                    sqlEditorPanel.setEditorText(sql);
                    tabbedPane.setSelectedComponent(sqlEditorPanel);
                }).setVisible(true);
            });
            popupMenu.add(cheatSheetItem);
            this.setComponentPopupMenu(popupMenu);
            
            this.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                        int row = rowAtPoint(e.getPoint());
                        int col = columnAtPoint(e.getPoint());
                        if (row >= 0 && col >= 0) {
                            if (!isRowSelected(row) || !isColumnSelected(col)) {
                                changeSelection(row, col, false, false);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void setModel(javax.swing.table.TableModel dataModel) {
            super.setModel(dataModel);
            com.dbtool.ui.components.SmartTableColumnAdjuster.adjustColumnWidths(this);
        }

        @Override
        public javax.swing.table.TableCellEditor getCellEditor(int row, int column) {
            String tableName = tableNameSupplier != null ? tableNameSupplier.get() : null;
            String colName = getColumnName(column);
            
            String actualTable = tableName;
            String actualCol = colName;
            if (colName.contains(".")) {
                String[] parts = colName.split("\\.");
                if (parts.length == 2) {
                    actualTable = parts[0];
                    actualCol = parts[1];
                }
            }
            
            // 1. Check for Foreign Key (non-blocking cache lookup on EDT)
            if (actualTable != null && !actualTable.isEmpty()) {
                java.util.Map<String, String[]> fks = dbManager.getCachedForeignKeys(actualTable);
                if (fks.containsKey(actualCol)) {
                    String[] pkInfo = fks.get(actualCol);
                    String pkTable = pkInfo[0];
                    String pkCol = pkInfo[1];
                    String cacheKey = pkTable + "." + pkCol;
                    java.util.Vector<String> options = fkOptionsCache.get(cacheKey);
                    if (options != null && !options.isEmpty()) {
                        JComboBox<String> combo = new JComboBox<>(options);
                        combo.setEditable(true);
                        return new DefaultCellEditor(combo);
                    } else if (options == null) {
                        // Populate FK options asynchronously so we never block the Swing EDT
                        fkOptionsCache.put(cacheKey, new java.util.Vector<>());
                        JComboBox<String> combo = new JComboBox<>();
                        combo.setEditable(true);
                        Object curVal = getValueAt(row, column);
                        if (curVal != null) combo.addItem(curVal.toString());
                        new Thread(() -> {
                            try {
                                javax.swing.table.DefaultTableModel rs = dbManager.executeQuery(
                                        "SELECT DISTINCT " + dbManager.quoteColumnName(pkCol) +
                                        " FROM " + dbManager.quoteTableName(pkTable) + " LIMIT 500");
                                java.util.Vector<String> fetched = new java.util.Vector<>();
                                for (int i = 0; i < rs.getRowCount(); i++) {
                                    Object val = rs.getValueAt(i, 0);
                                    if (val != null) fetched.add(val.toString());
                                }
                                if (!fetched.isEmpty()) {
                                    fkOptionsCache.put(cacheKey, fetched);
                                    SwingUtilities.invokeLater(() -> {
                                        combo.setModel(new DefaultComboBoxModel<>(fetched));
                                    });
                                }
                            } catch (Exception ignored) {}
                        }).start();
                        return new DefaultCellEditor(combo);
                    }
                }
            }
            
            // 2. Check for Rich Text / JSON editor
            if (getModel() instanceof com.dbtool.util.TypedTableModel) {
                String type = ((com.dbtool.util.TypedTableModel) getModel()).getColumnType(column);
                if (type != null) {
                    type = type.toUpperCase();
                    if (type.contains("TEXT") || type.contains("JSON") || type.contains("CHAR") || type.contains("CLOB")) {
                        // Return a button that opens a dialog
                        return new MultiLineCellEditor(actualCol);
                    }
                }
            }
            
            return super.getCellEditor(row, column);
        }
        
        class MultiLineCellEditor extends javax.swing.AbstractCellEditor implements javax.swing.table.TableCellEditor {
            private JLabel label;
            private String currentValue;
            private String columnName;
            
            public MultiLineCellEditor(String columnName) {
                this.columnName = columnName;
                label = new JLabel(" Editing...");
            }
            
            private void openEditorDialog() {
                JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(UpdatableTable.this), "Edit " + columnName, JDialog.ModalityType.APPLICATION_MODAL);
                dialog.setSize(600, 400);
                dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(UpdatableTable.this));
                
                org.fife.ui.rsyntaxtextarea.RSyntaxTextArea textArea = new org.fife.ui.rsyntaxtextarea.RSyntaxTextArea();
                textArea.setText(currentValue);
                if (columnName.toLowerCase().contains("json")) {
                    textArea.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON);
                } else {
                    textArea.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL);
                }
                
                JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                JButton saveBtn = new JButton("Save");
                JButton cancelBtn = new JButton("Cancel");
                
                saveBtn.addActionListener(evt -> {
                    currentValue = textArea.getText();
                    stopCellEditing();
                    dialog.dispose();
                });
                
                cancelBtn.addActionListener(evt -> {
                    cancelCellEditing();
                    dialog.dispose();
                });
                
                bottom.add(cancelBtn);
                bottom.add(saveBtn);
                
                dialog.setLayout(new BorderLayout());
                dialog.add(new org.fife.ui.rtextarea.RTextScrollPane(textArea), BorderLayout.CENTER);
                dialog.add(bottom, BorderLayout.SOUTH);
                dialog.setVisible(true);
            }
            
            @Override
            public Object getCellEditorValue() {
                return currentValue;
            }
            
            @Override
            public java.awt.Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                currentValue = value != null ? value.toString() : "";
                SwingUtilities.invokeLater(this::openEditorDialog);
                return label;
            }
        }

        @Override
        public boolean editCellAt(int row, int column, java.util.EventObject e) {
            if (e instanceof java.awt.event.MouseEvent) {
                java.awt.event.MouseEvent me = (java.awt.event.MouseEvent) e;
                if (me.getClickCount() < 2) {
                    return false;
                }
            }
            return super.editCellAt(row, column, e);
        }
        
        @Override
        public void setValueAt(Object aValue, int row, int column) {
            Object oldValue = getValueAt(row, column);
            if (oldValue != null && oldValue.equals(aValue)) {
                super.setValueAt(aValue, row, column);
                return;
            }
            if (oldValue == null && aValue == null) {
                return;
            }
            
            String tableName = tableNameSupplier != null ? tableNameSupplier.get() : null;
            if (tableName == null || tableName.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Cannot update inline: Base table not selected.", "Update Failed", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            String colName = getColumnName(column);
            String actualTable = tableName;
            String actualCol = colName;
            if (colName.contains(".")) {
                String[] parts = colName.split("\\.");
                if (parts.length == 2) {
                    actualTable = parts[0];
                    actualCol = parts[1];
                }
            }
            
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to update column '" + actualCol + "'\nin table '" + actualTable + "'\nfrom '" + oldValue + "' to '" + aValue + "'?",
                    "Confirm Update", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    
            if (confirm == JOptionPane.YES_OPTION) {
                StringBuilder sql = new StringBuilder("UPDATE " + dbManager.quoteTableName(actualTable) + " SET ");
                sql.append(dbManager.quoteColumnName(actualCol)).append(" = ? WHERE ");
                
                List<Object> params = new ArrayList<>();
                params.add(aValue);

                List<String> pks = dbManager.getPrimaryKeys(actualTable);
                List<Integer> pkIndices = new ArrayList<>();
                List<String> matchedPks = new ArrayList<>();
                for (String pk : pks) {
                    for (int c = 0; c < getColumnCount(); c++) {
                        String currentHeader = getColumnName(c);
                        String curCol = currentHeader.contains(".") ? currentHeader.split("\\.", 2)[1] : currentHeader;
                        if (curCol.equalsIgnoreCase(pk)) {
                            pkIndices.add(c);
                            matchedPks.add(curCol);
                            break;
                        }
                    }
                }

                if (!pks.isEmpty() && pkIndices.size() == pks.size()) {
                    for (int i = 0; i < pkIndices.size(); i++) {
                        int c = pkIndices.get(i);
                        Object val = (c == column) ? oldValue : getValueAt(row, c);
                        sql.append(dbManager.quoteColumnName(matchedPks.get(i)));
                        if (val == null) {
                            sql.append(" IS NULL");
                        } else {
                            sql.append(" = ?");
                            params.add(val);
                        }
                        if (i < pkIndices.size() - 1) sql.append(" AND ");
                    }
                } else {
                    boolean first = true;
                    for (int c = 0; c < getColumnCount(); c++) {
                        String currentHeader = getColumnName(c);
                        String curTable = tableName;
                        String curCol = currentHeader;
                        if (currentHeader.contains(".")) {
                            String[] parts = currentHeader.split("\\.", 2);
                            curTable = parts[0];
                            curCol = parts[1];
                        }
                        if (!curTable.equals(actualTable)) continue;
                        
                        if (!first) sql.append(" AND ");
                        first = false;
                        
                        Object val = (c == column) ? oldValue : getValueAt(row, c);
                        sql.append(dbManager.quoteColumnName(curCol));
                        if (val == null) {
                            sql.append(" IS NULL");
                        } else {
                            sql.append(" = ?");
                            params.add(val);
                        }
                    }
                }
                
                try {
                    dbManager.executeUpdate(sql.toString(), params);
                    super.setValueAt(aValue, row, column);
                } catch (SQLException e) {
                    JOptionPane.showMessageDialog(Main.this, "Update failed:\n" + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private DatabaseManager dbManager = new DatabaseManager();
    private JLabel statusLabel = new JLabel("No database loaded.");
    
    // Browse Tab Components
    
    
    
    // Join Tab Components
    
    // New panels
    private SchemaPanel schemaPanel;
    private SqlEditorPanel sqlEditorPanel;
    private ErDiagramPanel erDiagramPanel;
    private com.dbtool.panels.DashboardPanel dashboardPanel;
    private TableComparePanel tableComparePanel;
    private com.dbtool.panels.DualDatabaseComparePanel dualDbComparePanel;
    private ScriptBuilderPanel scriptBuilderPanel;
    private DataOpsPanel dataOpsPanel;
    
    // Cache for currently loaded tables
    private List<String> loadedTables = new ArrayList<>();

    private JButton loadDbButton = new JButton("Open DB File (.sqlite, .db, .accdb, .sql)");
    private JButton connectExistingBtn = new JButton("Connect to Existing DB");
    private JButton sandboxBtn = new JButton("⚡ Sample Sandbox DB");
    private JButton seedSampleBtn = new JButton("🌱 Seed Sample Tables");
    private JButton disconnectDbButton = new JButton("Disconnect DB");
    private JButton exportDbBtn = new JButton("Export DB");
    private JButton autoSaveIndicatorBtn = new JButton("💾 Auto-Saved");

    private void loadAppIcons() {
        try {
            List<Image> icons = new ArrayList<>();
            String[] iconPaths = {"/icons/app-icon-16.png", "/icons/app-icon-32.png", "/icons/app-icon-64.png", "/icons/app-icon.png"};
            for (String p : iconPaths) {
                java.net.URL url = getClass().getResource(p);
                if (url != null) {
                    icons.add(new ImageIcon(url).getImage());
                }
            }
            if (!icons.isEmpty()) {
                setIconImages(icons);
            }
        } catch (Exception ignored) {
        }
    }

    private void showAboutDialog() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel iconLbl = new JLabel();
        java.net.URL iconUrl = getClass().getResource("/icons/app-icon-64.png");
        if (iconUrl != null) {
            iconLbl.setIcon(new ImageIcon(iconUrl));
        }
        panel.add(iconLbl, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel("SchemaSync");
        titleLbl.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.BOLD, 18));
        textPanel.add(titleLbl);
        textPanel.add(Box.createVerticalStrut(4));

        JLabel verLbl = new JLabel("Version " + APP_VERSION);
        verLbl.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.PLAIN, 12));
        textPanel.add(verLbl);
        textPanel.add(Box.createVerticalStrut(10));

        JLabel creatorLbl = new JLabel("Created & Developed by: NSENGIYUMVA Christophe");
        creatorLbl.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.BOLD, 13));
        creatorLbl.setForeground(new Color(16, 185, 129));
        textPanel.add(creatorLbl);
        textPanel.add(Box.createVerticalStrut(6));

        JLabel copyLbl = new JLabel("Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.");
        copyLbl.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.PLAIN, 11));
        textPanel.add(copyLbl);
        textPanel.add(Box.createVerticalStrut(6));

        JLabel descLbl = new JLabel("Multi-Database Synchronization, Visual Query Builder & Data Operations");
        descLbl.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.ITALIC, 11));
        textPanel.add(descLbl);

        panel.add(textPanel, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(this, panel, "About SchemaSync", JOptionPane.PLAIN_MESSAGE);
    }

    public Main() {
        setTitle("SchemaSync - by NSENGIYUMVA Christophe");
        setSize(1280, 800);
        setMinimumSize(new Dimension(850, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        loadAppIcons();

        // Initialize new panels
        schemaPanel = new SchemaPanel(dbManager);
        sqlEditorPanel = new SqlEditorPanel(dbManager);
        sqlEditorPanel.setSendToVisualBuilderAction(sql -> {
            tabbedPane.setSelectedComponent(joinTabContainer);
            VisualJoinTab tab = joinTabContainer.getActiveTab();
            if (tab == null) {
                tab = joinTabContainer.addNewTabAndGet();
            }
            tab.loadFromSql(sql);
        });
        sqlEditorPanel.setOnSchemaModified(() -> {
            dbManager.clearCache();
            loadedTables = dbManager.getTableNames();
            if (browseTabContainer != null) {
                for (BrowseTab tab : browseTabContainer.getTabs()) {
                    tab.refreshTables();
                }
            }
            if (joinTabContainer != null) {
                for (VisualJoinTab tab : joinTabContainer.getTabs()) {
                    tab.refreshTables();
                }
            }
            schemaPanel.refresh();
            if (erDiagramPanel != null) erDiagramPanel.markNeedsRefresh(dbManager);
        });
        erDiagramPanel = new ErDiagramPanel();
        erDiagramPanel.setTableActionHandler(new ErDiagramPanel.TableActionHandler() {
            @Override
            public void onOpenTable(String tableName) {
                openTableInBrowse(tableName);
            }
            @Override
            public void onGenerateCode(String tableName) {
                new com.dbtool.panels.EntityCodeGeneratorDialog(Main.this, dbManager, tableName).setVisible(true);
            }
            @Override
            public void onMockData(String tableName) {
                new com.dbtool.panels.MockDataGeneratorDialog(Main.this, dbManager, tableName, () -> {
                    if (browseTabContainer != null && browseTabContainer.getActiveTab() != null) {
                        browseTabContainer.getActiveTab().loadTableData();
                    }
                }).setVisible(true);
            }
            @Override
            public void onImportData(String tableName) {
                new com.dbtool.panels.DataImporterDialog(Main.this, dbManager, tableName, () -> {
                    if (browseTabContainer != null && browseTabContainer.getActiveTab() != null) {
                        browseTabContainer.getActiveTab().loadTableData();
                    }
                }).setVisible(true);
            }
            @Override
            public void onIndexAdvisor(String tableName) {
                new com.dbtool.panels.IndexAdvisorDialog(Main.this, dbManager, tableName).setVisible(true);
            }
            @Override
            public void onMaskData(String tableName) {
                new com.dbtool.panels.DataMaskingDialog(Main.this, dbManager, tableName).setVisible(true);
            }
        });
        tableComparePanel = new TableComparePanel(dbManager);
        dualDbComparePanel = new com.dbtool.panels.DualDatabaseComparePanel(dbManager);
        scriptBuilderPanel = new ScriptBuilderPanel(dbManager);
        dataOpsPanel = new DataOpsPanel(dbManager);

        // Schema panel: double-click loads table in browse (auto-creates tab if all tabs were closed)
        schemaPanel.setOnTableSelected(tableName -> {
            if (tabbedPane != null) tabbedPane.setSelectedComponent(browseTabContainer); // switch to browse tab
            if (loadedTables.contains(tableName) && browseTabContainer != null) {
                BrowseTab active = browseTabContainer.getActiveTab();
                if (active == null) {
                    active = browseTabContainer.addNewTabAndGet();
                }
                active.setBrowseTableDropdownValue(tableName);
            }
        });

        // Top Panel
        JPanel topPanel = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 6, 3));
        loadDbButton.addActionListener(e -> loadDatabase());
        topPanel.add(loadDbButton);
        
        connectExistingBtn.addActionListener(e -> connectExistingDatabase());
        topPanel.add(connectExistingBtn);

        sandboxBtn.setToolTipText("Load built-in in-memory Sandbox DB (Employees, Departments, Orders, and Sales)");
        sandboxBtn.addActionListener(e -> {
            try {
                dbManager.connectSandbox();
                statusLabel.setText("Database: Sandbox (In-Memory)");
                statusLabel.setForeground(new java.awt.Color(0, 150, 0));
                populateTablesUI();
                JOptionPane.showMessageDialog(this, "Sample Sandbox DB loaded!\n\nPre-loaded tables ready to query:\n• employees\n• departments\n• contractors\n• new_hires\n• projects\n• accounts\n• orders\n• sales\n• audit_log", "Sandbox Ready", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load Sandbox: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        topPanel.add(sandboxBtn);

        seedSampleBtn.setToolTipText("Seed sample tables into currently connected database");
        seedSampleBtn.addActionListener(e -> {
            if (dbManager.connection == null) {
                JOptionPane.showMessageDialog(this, "No database connected. Please connect first or click '⚡ Sample Sandbox DB'.", "Notice", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                dbManager.seedSampleSchema();
                loadedTables = dbManager.getTableNames();
                schemaPanel.refresh();
                if (browseTabContainer != null) for (BrowseTab tab : browseTabContainer.getTabs()) tab.refreshTables();
                if (joinTabContainer != null) for (VisualJoinTab tab : joinTabContainer.getTabs()) tab.refreshTables();
                if (erDiagramPanel != null) erDiagramPanel.markNeedsRefresh(dbManager);
                JOptionPane.showMessageDialog(this, "Sample tables (employees, departments, contractors, orders, sales, accounts) created and populated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to seed sample tables: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        seedSampleBtn.setVisible(false);
        topPanel.add(seedSampleBtn);

        disconnectDbButton.addActionListener(e -> disconnectDatabase());
        disconnectDbButton.setVisible(false);
        topPanel.add(disconnectDbButton);

        exportDbBtn.addActionListener(e -> showExportDatabaseDialog());
        exportDbBtn.setVisible(false);
        topPanel.add(exportDbBtn);

        JButton auditDbBtn = new JButton("🛡 Health Audit");
        auditDbBtn.setToolTipText("Run 1-Click Database Health & Performance Audit");
        auditDbBtn.addActionListener(e -> {
            if (dbManager.connection == null) {
                JOptionPane.showMessageDialog(this, "Please connect to a database first to run health diagnostics.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            com.dbtool.util.AppUsageTracker.recordFeatureUse("🛡 Health Audit");
            new com.dbtool.panels.DatabaseAuditorDialog(this, dbManager).setVisible(true);
        });
        topPanel.add(auditDbBtn);

        JButton indexAdvisorBtn = new JButton("⚡ Index Advisor");
        indexAdvisorBtn.setToolTipText("Database Performance & Index Optimization Advisor (Unindexed FKs, Redundant Indexes, Search Candidates)");
        indexAdvisorBtn.addActionListener(e -> {
            if (dbManager.connection == null) {
                JOptionPane.showMessageDialog(this, "Please connect to a database first to analyze indexes.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            com.dbtool.util.AppUsageTracker.recordFeatureUse("⚡ Index Advisor");
            new com.dbtool.panels.IndexAdvisorDialog(this, dbManager).setVisible(true);
        });
        topPanel.add(indexAdvisorBtn);

        JButton schemaDriftBtn = new JButton("📐 Schema Drift");
        schemaDriftBtn.setToolTipText("Schema Snapshot, Drift Detector & Migration Studio");
        schemaDriftBtn.addActionListener(e -> {
            if (dbManager.connection == null) {
                JOptionPane.showMessageDialog(this, "Please connect to a database first to inspect schema drift.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            com.dbtool.util.AppUsageTracker.recordFeatureUse("📐 Schema Drift");
            new com.dbtool.panels.SchemaDriftDialog(this, dbManager).setVisible(true);
        });
        topPanel.add(schemaDriftBtn);

        JButton omniSearchBtn = new JButton("🔍 OmniSearch");
        omniSearchBtn.setToolTipText("Global Database Search across all tables (Ctrl+Shift+F)");
        omniSearchBtn.addActionListener(e -> openOmniSearchDialog());
        topPanel.add(omniSearchBtn);

        JButton commandPaletteBtn = new JButton("⚡ Quick Jump / Commands (Ctrl+K)");
        commandPaletteBtn.setFont(commandPaletteBtn.getFont().deriveFont(Font.BOLD));
        commandPaletteBtn.setForeground(com.dbtool.util.ThemeManager.getPrimaryActionColor());
        commandPaletteBtn.setToolTipText("Open Command Palette to jump to any table, tab, studio tool, or theme (Ctrl+K / Ctrl+P)");
        commandPaletteBtn.addActionListener(e -> openCommandPaletteDialog());
        topPanel.add(commandPaletteBtn);

        JMenuItem maskMenuItem = new JMenuItem("🔒 Sensitive Data Masking Studio (GDPR/PII)");
        maskMenuItem.addActionListener(e -> openDataMaskingDialog());

        JMenuItem vaultMenuItem = new JMenuItem("🛡️ AES-256 Credential Vault");
        vaultMenuItem.addActionListener(e -> openVaultDialog());

        JMenuItem profilerMenuItem = new JMenuItem("⏱️ SQL Query Profiler & Bottleneck Analyzer");
        profilerMenuItem.addActionListener(e -> openQueryProfilerDialog());

        JButton studiosMenuBtn = new JButton("🧰 Studios & Tools ▾");
        studiosMenuBtn.setToolTipText("Access Database Studios: Health Audit, Index Advisor, Schema Drift, Data Masking, Vault & Profiler");
        studiosMenuBtn.addActionListener(e -> {
            JPopupMenu studiosMenu = new JPopupMenu();
            JMenuItem mAudit = new JMenuItem("🛡 Database Health & Performance Audit");
            mAudit.addActionListener(ev -> auditDbBtn.doClick());
            studiosMenu.add(mAudit);

            JMenuItem mIdx = new JMenuItem("⚡ Index & Query Performance Advisor");
            mIdx.addActionListener(ev -> indexAdvisorBtn.doClick());
            studiosMenu.add(mIdx);

            JMenuItem mDrift = new JMenuItem("📐 Schema Snapshot & Drift Migration Studio");
            mDrift.addActionListener(ev -> schemaDriftBtn.doClick());
            studiosMenu.add(mDrift);

            studiosMenu.addSeparator();
            studiosMenu.add(maskMenuItem);
            studiosMenu.add(vaultMenuItem);
            studiosMenu.add(profilerMenuItem);

            studiosMenu.addSeparator();
            JMenuItem mImport = new JMenuItem("📥 Smart CSV / Excel Data Importer");
            mImport.addActionListener(ev -> openDataImportDialog());
            studiosMenu.add(mImport);

            JMenuItem mMock = new JMenuItem("🧪 Synthetic Mock Data Generator");
            mMock.addActionListener(ev -> openMockDataDialog());
            studiosMenu.add(mMock);

            studiosMenu.addSeparator();
            JMenuItem mDrivers = new JMenuItem("🔌 External JDBC Driver Manager...");
            mDrivers.addActionListener(ev -> new com.dbtool.panels.DriverManagerDialog(Main.this).setVisible(true));
            studiosMenu.add(mDrivers);

            studiosMenu.show(studiosMenuBtn, 0, studiosMenuBtn.getHeight());
        });
        // Keep quick-access studio menu compact instead of 6 separate top-bar buttons
        auditDbBtn.setVisible(false);
        indexAdvisorBtn.setVisible(false);
        schemaDriftBtn.setVisible(false);
        topPanel.add(studiosMenuBtn);

        JButton themeBtn = new JButton("🎨 Theme ▾");
        themeBtn.setToolTipText("Switch Accent Theme Preset (Midnight Blue, Emerald, Obsidian, Cyber Purple, Solar Light) & UI Zoom");
        themeBtn.addActionListener(e -> com.dbtool.util.ThemeManager.showThemePopupMenu(themeBtn, themeBtn));
        topPanel.add(themeBtn);

        JButton aboutBtn = new JButton("ℹ About");
        aboutBtn.setToolTipText("About SchemaSync & Creator Info");
        aboutBtn.addActionListener(e -> showAboutDialog());
        topPanel.add(aboutBtn);

        JButton updateBtn = new JButton("🔄 Updates");
        updateBtn.setToolTipText("Check GitHub for SchemaSync updates & instant delta patches");
        com.dbtool.util.UpdateChecker.registerToolbarButton(updateBtn);
        updateBtn.addActionListener(e -> com.dbtool.util.UpdateChecker.checkForUpdates(this, false));
        topPanel.add(updateBtn);

        topPanel.add(statusLabel);
        
        // Init theme
        com.dbtool.util.ThemeManager.init(themeBtn);
        add(topPanel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");

        dashboardPanel = new com.dbtool.panels.DashboardPanel(
            dbManager,
            this::openTableInBrowse,
            this::openIndexAdvisorDialog,
            () -> tabbedPane.setSelectedComponent(erDiagramPanel),
            this::openSchemaDriftDialog,
            this::openOmniSearchDialog,
            this::openDataMaskingDialog,
            this::openVaultDialog,
            this::openDataImportDialog,
            this::openMockDataDialog
        );
        tabbedPane.addTab("🏠 Dashboard", dashboardPanel);

        browseTabContainer = new com.dbtool.panels.MultiTabContainer<>("Browse", () -> {
            BrowseTab tab = new BrowseTab();
            tab.refreshTables();
            return tab;
        });
        tabbedPane.addTab("Browse & Search", browseTabContainer);
        joinTabContainer = new com.dbtool.panels.MultiTabContainer<>("Join", () -> {
            VisualJoinTab tab = new VisualJoinTab();
            tab.refreshTables();
            return tab;
        });
        tabbedPane.addTab("Visual Join Builder", joinTabContainer);
        tabbedPane.addTab("Data Operations", dataOpsPanel);
        tabbedPane.addTab("SQL Editor", sqlEditorPanel);
        tabbedPane.addTab("Activity Monitor", new com.dbtool.panels.ActivityMonitorPanel(dbManager));
        tabbedPane.addTab("Schema Dependencies", new com.dbtool.panels.DependencyGraphPanel(dbManager));
        tabbedPane.addTab("ER Diagram", erDiagramPanel);
        tabbedPane.addTab("Script Builder", scriptBuilderPanel);
        tabbedPane.addTab("Compare Queries", tableComparePanel);
        tabbedPane.addTab("Dual DB Compare", dualDbComparePanel);
        
        com.dbtool.panels.SshTerminalPanel sshTerminalPanel = new com.dbtool.panels.SshTerminalPanel(dbManager);
        tabbedPane.addTab("SSH / Server", sshTerminalPanel);

        com.dbtool.panels.AiAssistantTab aiAssistantTab = new com.dbtool.panels.AiAssistantTab(dbManager);
        tabbedPane.addTab("🤖 AI Assistant", aiAssistantTab);

        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex != -1) {
                String title = tabbedPane.getTitleAt(selectedIndex);
                com.dbtool.util.AppUsageTracker.recordFeatureUse(title);
                if ("ER Diagram".equals(title) && erDiagramPanel != null) {
                    erDiagramPanel.checkAndRefresh();
                } else if ("🏠 Dashboard".equals(title) && dashboardPanel != null) {
                    dashboardPanel.refresh();
                }
            }
        });
        
        com.dbtool.panels.NetworkScannerPanel networkScannerPanel = new com.dbtool.panels.NetworkScannerPanel();
        // Network Scanner tab is hidden by default

        // Main layout: Schema sidebar left, tabs center
        queryLogPanel = new com.dbtool.panels.QueryLogPanel();
        queryLogPanel.setToggleCallback(this::toggleQueryLog);

        centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, queryLogPanel);
        centerSplit.setResizeWeight(1.0); // 100% of extra vertical space goes to top workspace
        centerSplit.setDividerLocation(600);

        // Pin query log to bottom when minimized on any window resize or maximize
        centerSplit.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int totalH = centerSplit.getHeight();
                if (totalH <= 0) return;
                int divSize = centerSplit.getDividerSize();
                int headerH = queryLogPanel.getHeaderPreferredHeight();
                isAdjustingQueryLogSplit = true;
                try {
                    if (isQueryLogMinimized) {
                        int targetDiv = Math.max(0, totalH - divSize - headerH);
                        centerSplit.setDividerLocation(targetDiv);
                        SwingUtilities.invokeLater(() -> {
                            if (isQueryLogMinimized && centerSplit != null) {
                                int tH = centerSplit.getHeight();
                                if (tH > 0) {
                                    int dS = centerSplit.getDividerSize();
                                    int hH = queryLogPanel.getHeaderPreferredHeight();
                                    isAdjustingQueryLogSplit = true;
                                    try {
                                        centerSplit.setDividerLocation(Math.max(0, tH - dS - hH));
                                    } finally {
                                        isAdjustingQueryLogSplit = false;
                                    }
                                }
                            }
                        });
                    } else {
                        int targetDiv = Math.max(100, totalH - divSize - queryLogExpandedHeight);
                        centerSplit.setDividerLocation(targetDiv);
                    }
                } finally {
                    isAdjustingQueryLogSplit = false;
                }
            }
        });

        // Track user dragging divider to remember preferred expanded height or detect minimize
        centerSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            if (isAdjustingQueryLogSplit) return;
            // When minimized, preserve minimized state across window resizes
            if (isQueryLogMinimized) {
                int totalH = centerSplit.getHeight();
                if (totalH > 0) {
                    int divSize = centerSplit.getDividerSize();
                    int headerH = queryLogPanel.getHeaderPreferredHeight();
                    int targetDiv = Math.max(0, totalH - divSize - headerH);
                    if (centerSplit.getDividerLocation() != targetDiv) {
                        isAdjustingQueryLogSplit = true;
                        try {
                            centerSplit.setDividerLocation(targetDiv);
                        } finally {
                            isAdjustingQueryLogSplit = false;
                        }
                    }
                }
                return;
            }

            int totalH = centerSplit.getHeight();
            if (totalH <= 0) return;
            int divLoc = centerSplit.getDividerLocation();
            int divSize = centerSplit.getDividerSize();
            int headerH = queryLogPanel.getHeaderPreferredHeight();
            int currentLogHeight = totalH - divLoc - divSize;

            if (currentLogHeight <= headerH + 12) {
                isQueryLogMinimized = true;
                queryLogPanel.setMinimizedUI(true);
            } else {
                queryLogExpandedHeight = Math.max(headerH + 50, currentLogHeight);
            }
        });
        
        JTabbedPane leftSidebarTabs = new JTabbedPane();
        leftSidebarTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        leftSidebarTabs.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");
        leftSidebarTabs.addTab("Schema", schemaPanel);
        
        com.dbtool.panels.WorkspacesPanel workspacesPanel = new com.dbtool.panels.WorkspacesPanel();
        leftSidebarTabs.addTab("Workspaces", workspacesPanel);
        
        // Handle double click on workspace tree
        workspacesPanel.getTree().addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    javax.swing.tree.DefaultMutableTreeNode node = (javax.swing.tree.DefaultMutableTreeNode) workspacesPanel.getTree().getLastSelectedPathComponent();
                    if (node != null && node.isLeaf()) {
                        String fileName = (String) node.getUserObject();
                        java.io.File file = new java.io.File(workspacesPanel.getWorkspaceDir(), fileName);
                        if (fileName.endsWith(".json")) {
                            // Load Visual Join Project
                            try {
                                com.dbtool.model.VisualJoinState state = new com.fasterxml.jackson.databind.ObjectMapper().readValue(file, com.dbtool.model.VisualJoinState.class);
                                
                                VisualJoinTab newTab = joinTabContainer.addNewTabAndGet();
                                // A real implementation would apply the state to the tab here
                                // newTab.loadState(state);
                                javax.swing.JOptionPane.showMessageDialog(Main.this, "Loaded Workspace: " + fileName);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        } else if (fileName.endsWith(".sql")) {
                            // Load SQL
                            try {
                                String sql = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                                sqlEditorPanel.setEditorText(sql);
                                tabbedPane.setSelectedComponent(sqlEditorPanel);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            }
        });

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSidebarTabs, centerSplit);
        mainSplit.setDividerLocation(230);
        // mainSplit.setOneTouchExpandable(true);
        mainSplit.setResizeWeight(0.0);
        add(mainSplit, BorderLayout.CENTER);

        JButton toggleSidebarBtn = new JButton("\u2630");
        toggleSidebarBtn.setToolTipText("Toggle Sidebar");
        toggleSidebarBtn.addActionListener(e -> {
            if (mainSplit.getDividerLocation() <= 10) {
                mainSplit.setDividerLocation(230);
            } else {
                mainSplit.setDividerLocation(0);
            }
        });
        topPanel.add(toggleSidebarBtn, 0); // Add to extreme left

        toggleQueryLogBtn = new JButton("\u2630");
        toggleQueryLogBtn.setToolTipText("Toggle Global Query Log (Show / Minimize)");
        toggleQueryLogBtn.addActionListener(e -> toggleQueryLog());
        topPanel.add(toggleQueryLogBtn, 1); // Add right next to sidebar toggle
        
        JToggleButton adminToolsBtn = new JToggleButton("Admin Tools");
        adminToolsBtn.setToolTipText("Show/Hide Network Scanner (Requires Password)");
        adminToolsBtn.addActionListener(e -> {
            if (adminToolsBtn.isSelected()) {
                JPasswordField pf = new JPasswordField();
                int okCxl = JOptionPane.showConfirmDialog(null, pf, "Enter Admin Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (okCxl == JOptionPane.OK_OPTION) {
                    String password = new String(pf.getPassword());
                    if ("admin".equals(password)) {
                        tabbedPane.addTab("Network Scanner", networkScannerPanel);
                        tabbedPane.setSelectedComponent(networkScannerPanel);
                    } else {
                        JOptionPane.showMessageDialog(null, "Incorrect password!", "Access Denied", JOptionPane.ERROR_MESSAGE);
                        adminToolsBtn.setSelected(false);
                    }
                } else {
                    adminToolsBtn.setSelected(false);
                }
            } else {
                tabbedPane.remove(networkScannerPanel);
            }
        });
        topPanel.add(adminToolsBtn);

        // Auto-Save Indicator Button
        autoSaveIndicatorBtn.setToolTipText("Auto-Save & Workspace Snapshots (Click for recovery & snapshot options)");
        autoSaveIndicatorBtn.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem saveNowItem = new JMenuItem("💾 Save Snapshot Now");
            saveNowItem.addActionListener(ev -> {
                com.dbtool.model.SessionSnapshot snapshot = captureCurrentSessionSnapshot();
                boolean saved = com.dbtool.util.SessionRecoveryManager.saveSessionSnapshot(snapshot);
                com.dbtool.util.SessionRecoveryManager.archiveCurrentSnapshot(snapshot);
                String timeStr = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
                onAutoSaved(timeStr);
                JOptionPane.showMessageDialog(this, "Session snapshot saved and archived successfully!", "Snapshot Info", JOptionPane.INFORMATION_MESSAGE);
            });
            menu.add(saveNowItem);

            JMenuItem restoreLatestItem = new JMenuItem("♻️ Restore Latest Autosaved Snapshot...");
            restoreLatestItem.setEnabled(com.dbtool.util.SessionRecoveryManager.hasAutosavedSession());
            restoreLatestItem.addActionListener(ev -> {
                com.dbtool.model.SessionSnapshot snapshot = com.dbtool.util.SessionRecoveryManager.loadAutosavedSession();
                if (snapshot != null) {
                    com.dbtool.panels.RecoveryDialog dlg = new com.dbtool.panels.RecoveryDialog(this, snapshot, true);
                    dlg.setVisible(true);
                    if (dlg.getChoice() == com.dbtool.panels.RecoveryDialog.RecoveryChoice.RESTORE) {
                        restoreSessionSnapshot(snapshot);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "No autosaved snapshot found.", "Snapshot Recovery", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            menu.add(restoreLatestItem);

            JMenuItem loadFileItem = new JMenuItem("📂 Load Snapshot from File...");
            loadFileItem.addActionListener(ev -> {
                JFileChooser fc = new JFileChooser(com.dbtool.util.SessionRecoveryManager.getRecoveryDir());
                fc.setDialogTitle("Select Saved Session Snapshot (.json)");
                fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Session Snapshots (*.json)", "json"));
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    com.dbtool.model.SessionSnapshot snapshot = com.dbtool.util.SessionRecoveryManager.loadSnapshotFromFile(file);
                    if (snapshot != null) {
                        com.dbtool.panels.RecoveryDialog dlg = new com.dbtool.panels.RecoveryDialog(this, snapshot, true);
                        dlg.setVisible(true);
                        if (dlg.getChoice() == com.dbtool.panels.RecoveryDialog.RecoveryChoice.RESTORE) {
                            restoreSessionSnapshot(snapshot);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to load snapshot from:\n" + file.getAbsolutePath(), "Load Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            menu.add(loadFileItem);

            JMenuItem exportFileItem = new JMenuItem("💾 Export Current Snapshot to File...");
            exportFileItem.addActionListener(ev -> {
                com.dbtool.model.SessionSnapshot snapshot = captureCurrentSessionSnapshot();
                JFileChooser fc = new JFileChooser();
                fc.setSelectedFile(new File("schemasync_snapshot_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".session.json"));
                if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File f = fc.getSelectedFile();
                    boolean saved = com.dbtool.util.SessionRecoveryManager.saveSnapshotToFile(f, snapshot);
                    if (saved) {
                        JOptionPane.showMessageDialog(this, "Snapshot exported successfully to:\n" + f.getAbsolutePath(), "Snapshot Exported", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to export snapshot.", "Export Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            menu.add(exportFileItem);

            menu.addSeparator();

            // Submenu for recent archived snapshots
            JMenu recentMenu = new JMenu("📜 Recent / Archived Snapshots");
            java.util.List<File> archives = com.dbtool.util.SessionRecoveryManager.listArchivedSnapshots();
            if (archives.isEmpty()) {
                JMenuItem noneItem = new JMenuItem("(No archived snapshots)");
                noneItem.setEnabled(false);
                recentMenu.add(noneItem);
            } else {
                int limit = Math.min(archives.size(), 10);
                for (int i = 0; i < limit; i++) {
                    File af = archives.get(i);
                    String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(af.lastModified()));
                    JMenuItem item = new JMenuItem(af.getName() + " (" + dateStr + ")");
                    item.addActionListener(ev -> {
                        com.dbtool.model.SessionSnapshot snap = com.dbtool.util.SessionRecoveryManager.loadSnapshotFromFile(af);
                        if (snap != null) {
                            com.dbtool.panels.RecoveryDialog dlg = new com.dbtool.panels.RecoveryDialog(this, snap, true);
                            dlg.setVisible(true);
                            if (dlg.getChoice() == com.dbtool.panels.RecoveryDialog.RecoveryChoice.RESTORE) {
                                restoreSessionSnapshot(snap);
                            }
                        }
                    });
                    recentMenu.add(item);
                }
            }
            menu.add(recentMenu);

            JMenuItem openFolderItem = new JMenuItem("📁 Open Recovery Folder...");
            openFolderItem.addActionListener(ev -> {
                try {
                    java.awt.Desktop.getDesktop().open(com.dbtool.util.SessionRecoveryManager.getRecoveryDir());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Recovery folder: " + com.dbtool.util.SessionRecoveryManager.getRecoveryDir().getAbsolutePath());
                }
            });
            menu.add(openFolderItem);

            menu.addSeparator();

            JMenuItem discardItem = new JMenuItem("🗑 Clear Autosave Backup");
            discardItem.addActionListener(ev -> {
                com.dbtool.util.SessionRecoveryManager.discardCrashedSession();
                autoSaveIndicatorBtn.setText("💾 Auto-Save: Active");
                JOptionPane.showMessageDialog(this, "Autosave backup cleared.");
            });
            menu.add(discardItem);

            menu.show(autoSaveIndicatorBtn, 0, autoSaveIndicatorBtn.getHeight());
        });
        topPanel.add(autoSaveIndicatorBtn);

        // Crash Recovery Check on Startup
        if (com.dbtool.util.SessionRecoveryManager.hasCrashedSession()) {
            SwingUtilities.invokeLater(() -> {
                com.dbtool.model.SessionSnapshot snapshot = com.dbtool.util.SessionRecoveryManager.loadAutosavedSession();
                if (snapshot != null) {
                    com.dbtool.panels.RecoveryDialog dlg = new com.dbtool.panels.RecoveryDialog(this, snapshot);
                    dlg.setVisible(true);
                    if (dlg.getChoice() == com.dbtool.panels.RecoveryDialog.RecoveryChoice.RESTORE) {
                        restoreSessionSnapshot(snapshot);
                        JOptionPane.showMessageDialog(this, "Workspace session restored successfully!", "Session Restored", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            });
        }

        // Record application launch and refresh dashboard usage stats
        com.dbtool.util.AppUsageTracker.recordSessionStart();
        if (dashboardPanel != null) {
            dashboardPanel.refreshUsageStatistics();
        }

        // Start background auto-save daemon and register shutdown hook
        com.dbtool.util.SessionRecoveryManager.startAutoSaveDaemon(this::captureCurrentSessionSnapshot, this::onAutoSaved, 15);
        com.dbtool.util.SessionRecoveryManager.registerShutdownHook(this::captureCurrentSessionSnapshot);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                com.dbtool.util.AppUsageTracker.flushSessionUptime();
                if (sqlEditorPanel != null) {
                    sqlEditorPanel.saveEditorDraft();
                }
                com.dbtool.util.SessionRecoveryManager.markCleanExit();
            }
        });

        // Background update check after startup (quietly, 3.5 seconds delay)
        javax.swing.Timer startupUpdateTimer = new javax.swing.Timer(3500, e -> {
            com.dbtool.util.UpdateChecker.checkForUpdates(this, true);
        });
        startupUpdateTimer.setRepeats(false);
        startupUpdateTimer.start();

        // Global Shortcut: Ctrl+Shift+F for Database OmniSearch
        KeyStroke omniSearchKs = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(omniSearchKs, "omniSearchGlobal");
        getRootPane().getActionMap().put("omniSearchGlobal", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                openOmniSearchDialog();
            }
        });

        // Global Shortcuts: Ctrl+K and Ctrl+P for Command Palette & Quick Table Switcher
        KeyStroke ctrlK = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK);
        KeyStroke ctrlP = KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlK, "commandPaletteGlobal");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlP, "commandPaletteGlobal");
        getRootPane().getActionMap().put("commandPaletteGlobal", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                openCommandPaletteDialog();
            }
        });
    }

    public void setQueryLogMinimized(boolean minimize) {
        this.isQueryLogMinimized = minimize;
        if (queryLogPanel != null) {
            queryLogPanel.setMinimizedUI(minimize);
        }
        if (centerSplit != null) {
            int totalH = centerSplit.getHeight();
            if (totalH > 0) {
                int divSize = centerSplit.getDividerSize();
                int headerH = queryLogPanel != null ? queryLogPanel.getHeaderPreferredHeight() : 28;
                isAdjustingQueryLogSplit = true;
                try {
                    if (minimize) {
                        int targetDiv = Math.max(0, totalH - divSize - headerH);
                        centerSplit.setDividerLocation(targetDiv);
                    } else {
                        int targetDiv = Math.max(100, totalH - divSize - queryLogExpandedHeight);
                        centerSplit.setDividerLocation(targetDiv);
                    }
                } finally {
                    isAdjustingQueryLogSplit = false;
                }
            }
        }
    }

    public void toggleQueryLog() {
        setQueryLogMinimized(!isQueryLogMinimized);
    }

    public boolean isQueryLogMinimized() {
        return isQueryLogMinimized;
    }

    public void openCommandPaletteDialog() {
        List<com.dbtool.panels.CommandPaletteDialog.PaletteItem> items = new ArrayList<>();

        // 1. All loaded database tables
        if (loadedTables != null) {
            for (String tbl : loadedTables) {
                items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                        "TABLE",
                        "🗄️ " + tbl,
                        "Open table '" + tbl + "' in Browse & Search",
                        "Enter",
                        () -> openTableInBrowse(tbl)
                ));
            }
        }

        // 2. All Workbench Tabs
        if (tabbedPane != null) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                final int tabIdx = i;
                String title = tabbedPane.getTitleAt(i);
                items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                        "TAB",
                        "🧭 Switch to " + title,
                        "Activate the '" + title + "' workbench tab",
                        "",
                        () -> tabbedPane.setSelectedIndex(tabIdx)
                ));
            }
        }

        // 3. Studio Tools & Actions
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "STUDIO", "🔍 Global Database OmniSearch", "Search across all tables and columns", "Ctrl+Shift+F", this::openOmniSearchDialog));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "STUDIO", "⚡ Index & Performance Advisor", "Detect unindexed FKs, redundant indexes, and table bloat", "", this::openIndexAdvisorDialog));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "STUDIO", "📐 Schema Snapshot & Drift Studio", "Capture schema snapshots and generate UP/DOWN migrations", "", this::openSchemaDriftDialog));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "STUDIO", "🔒 Sensitive Data Masking Studio", "Anonymize PII columns for GDPR/HIPAA compliance", "", this::openDataMaskingDialog));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "STUDIO", "🛡️ AES-256 Credential Vault", "Manage encrypted database connection profiles", "", this::openVaultDialog));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "STUDIO", "⏱️ SQL Query Profiler", "Analyze query execution latency and EXPLAIN plans", "", this::openQueryProfilerDialog));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "STUDIO", "📥 Import CSV / Excel Data", "Import spreadsheets into the current table", "", this::openDataImportDialog));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "STUDIO", "🧪 Generate Synthetic Mock Data", "Populate realistic FK-safe test records", "", this::openMockDataDialog));

        // 4. Session & Workspace Snapshots
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "SNAPSHOT", "💾 Save Workspace Snapshot Now", "Capture and archive current workspace snapshot", "",
                () -> {
                    com.dbtool.model.SessionSnapshot snap = captureCurrentSessionSnapshot();
                    com.dbtool.util.SessionRecoveryManager.saveSessionSnapshot(snap);
                    com.dbtool.util.SessionRecoveryManager.archiveCurrentSnapshot(snap);
                    JOptionPane.showMessageDialog(this, "Session snapshot saved and archived successfully!", "Snapshot Saved", JOptionPane.INFORMATION_MESSAGE);
                }));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "SNAPSHOT", "♻️ Restore Latest Autosaved Workspace Snapshot", "Load and restore latest auto-saved session", "",
                () -> {
                    com.dbtool.model.SessionSnapshot snap = com.dbtool.util.SessionRecoveryManager.loadAutosavedSession();
                    if (snap != null) {
                        com.dbtool.panels.RecoveryDialog dlg = new com.dbtool.panels.RecoveryDialog(this, snap, true);
                        dlg.setVisible(true);
                        if (dlg.getChoice() == com.dbtool.panels.RecoveryDialog.RecoveryChoice.RESTORE) {
                            restoreSessionSnapshot(snap);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "No autosaved snapshot found.", "Snapshot Recovery", JOptionPane.INFORMATION_MESSAGE);
                    }
                }));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "SNAPSHOT", "📂 Load Workspace Snapshot from File...", "Load a saved .session.json snapshot file", "",
                () -> {
                    JFileChooser fc = new JFileChooser(com.dbtool.util.SessionRecoveryManager.getRecoveryDir());
                    fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Session Snapshots (*.json)", "json"));
                    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File file = fc.getSelectedFile();
                        com.dbtool.model.SessionSnapshot snap = com.dbtool.util.SessionRecoveryManager.loadSnapshotFromFile(file);
                        if (snap != null) {
                            com.dbtool.panels.RecoveryDialog dlg = new com.dbtool.panels.RecoveryDialog(this, snap, true);
                            dlg.setVisible(true);
                            if (dlg.getChoice() == com.dbtool.panels.RecoveryDialog.RecoveryChoice.RESTORE) {
                                restoreSessionSnapshot(snap);
                            }
                        } else {
                            JOptionPane.showMessageDialog(this, "Failed to load snapshot from:\n" + file.getAbsolutePath(), "Load Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }));
        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "SNAPSHOT", "💾 Export Workspace Snapshot to File...", "Save current tabs and queries to a portable JSON file", "",
                () -> {
                    com.dbtool.model.SessionSnapshot snap = captureCurrentSessionSnapshot();
                    JFileChooser fc = new JFileChooser();
                    fc.setSelectedFile(new File("schemasync_snapshot_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".session.json"));
                    if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File f = fc.getSelectedFile();
                        boolean saved = com.dbtool.util.SessionRecoveryManager.saveSnapshotToFile(f, snap);
                        if (saved) {
                            JOptionPane.showMessageDialog(this, "Snapshot exported successfully to:\n" + f.getAbsolutePath(), "Snapshot Exported", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                }));

        // 5. Theme Presets & UI Zoom
        for (com.dbtool.util.ThemeManager.ThemePreset preset : com.dbtool.util.ThemeManager.ThemePreset.values()) {
            items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                    "THEME",
                    "🎨 Theme: " + preset.getDisplayName(),
                    "Switch application color palette and accent theme",
                    "",
                    () -> com.dbtool.util.ThemeManager.setPreset(preset, null)
            ));
        }

        float[] scales = {0.9f, 1.0f, 1.1f, 1.25f};
        for (float sc : scales) {
            int pct = Math.round(sc * 100);
            items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                    "ZOOM",
                    "🔍 Interface Zoom: " + pct + "%",
                    "Scale global fonts and table row heights to " + pct + "%",
                    "",
                    () -> com.dbtool.util.ThemeManager.setUiScaleFactor(sc, null)
            ));
        }

        items.add(new com.dbtool.panels.CommandPaletteDialog.PaletteItem(
                "SYSTEM", "🔄 Check for Updates", "Check GitHub Releases for instant 1.2 MB delta patch", "",
                () -> com.dbtool.util.UpdateChecker.checkForUpdates(this, false)));

        new com.dbtool.panels.CommandPaletteDialog(this, items).setVisible(true);
    }

    public void openOmniSearchDialog() {
        if (dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "Please connect to a database first to use OmniSearch.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        com.dbtool.util.AppUsageTracker.recordFeatureUse("🔍 OmniSearch");
        new com.dbtool.panels.OmniSearchDialog(this, dbManager, this::jumpToTableRow).setVisible(true);
    }

    public void jumpToTableRow(String tableName, String columnName, Object matchedValue, Map<String, Object> pks) {
        if (tabbedPane != null) {
            tabbedPane.setSelectedComponent(browseTabContainer); // Browse & Search
        }
        if (browseTabContainer != null) {
            BrowseTab activeTab = browseTabContainer.getActiveTab();
            if (activeTab == null) {
                activeTab = browseTabContainer.addNewTabAndGet();
            }
            activeTab.browseTableDropdown.setSelectedItem(tableName);
            activeTab.loadTableData();
        }
        toFront();
        requestFocus();
    }

    public void openTableInBrowse(String tableName) {
        if (tabbedPane != null) {
            tabbedPane.setSelectedComponent(browseTabContainer); // Browse & Search
        }
        if (browseTabContainer != null) {
            BrowseTab activeTab = browseTabContainer.getActiveTab();
            if (activeTab == null) {
                activeTab = browseTabContainer.addNewTabAndGet();
            }
            activeTab.browseTableDropdown.setSelectedItem(tableName);
            activeTab.loadTableData();
        }
        toFront();
        requestFocus();
    }

    public String getActiveBrowseTable() {
        if (browseTabContainer != null && browseTabContainer.getActiveTab() != null) {
            return (String) browseTabContainer.getActiveTab().browseTableDropdown.getSelectedItem();
        }
        return null;
    }

    public void openDataMaskingDialog() {
        if (dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "Please connect to a database first to use Data Masking.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        com.dbtool.util.AppUsageTracker.recordFeatureUse("🔒 Data Masking");
        String tbl = getActiveBrowseTable();
        new com.dbtool.panels.DataMaskingDialog(this, dbManager, tbl).setVisible(true);
    }

    public void openIndexAdvisorDialog() {
        if (dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "Please connect to a database first to analyze indexes.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        com.dbtool.util.AppUsageTracker.recordFeatureUse("⚡ Index Advisor");
        String tbl = getActiveBrowseTable();
        new com.dbtool.panels.IndexAdvisorDialog(this, dbManager, tbl).setVisible(true);
    }

    public void openMockDataDialog() {
        if (dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "Please connect to a database first to generate mock data.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        com.dbtool.util.AppUsageTracker.recordFeatureUse("🧪 Mock Data Generator");
        String tbl = getActiveBrowseTable();
        new com.dbtool.panels.MockDataGeneratorDialog(this, dbManager, tbl, () -> {
            if (browseTabContainer != null && browseTabContainer.getActiveTab() != null) {
                browseTabContainer.getActiveTab().loadTableData();
            }
        }).setVisible(true);
    }

    public void openDataImportDialog() {
        if (dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "Please connect to a database first to import data.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        com.dbtool.util.AppUsageTracker.recordFeatureUse("📥 Data Importer");
        String tbl = getActiveBrowseTable();
        new com.dbtool.panels.DataImporterDialog(this, dbManager, tbl, () -> {
            if (browseTabContainer != null && browseTabContainer.getActiveTab() != null) {
                browseTabContainer.getActiveTab().loadTableData();
            }
        }).setVisible(true);
    }

    public void openVaultDialog() {
        com.dbtool.util.AppUsageTracker.recordFeatureUse("🛡️ Credential Vault");
        new com.dbtool.panels.VaultDialog(this, dbManager, null).setVisible(true);
    }

    public void openQueryProfilerDialog() {
        com.dbtool.util.AppUsageTracker.recordFeatureUse("⏱️ Query Profiler");
        new com.dbtool.panels.QueryProfilerDialog(this, dbManager, sql -> {
            tabbedPane.setSelectedComponent(sqlEditorPanel);
            sqlEditorPanel.setEditorText(sql);
        }).setVisible(true);
    }

    public void openSchemaDriftDialog() {
        com.dbtool.util.AppUsageTracker.recordFeatureUse("📐 Schema Drift");
        new com.dbtool.panels.SchemaDriftDialog(this, dbManager).setVisible(true);
    }

    private void onAutoSaved(String timeStr) {
        autoSaveIndicatorBtn.setText("💾 Auto-Saved (" + timeStr + ")");
        autoSaveIndicatorBtn.setToolTipText("Last auto-saved at " + timeStr + ". Click for recovery & snapshot options.");
    }

    public com.dbtool.model.SessionSnapshot captureCurrentSessionSnapshot() {
        com.dbtool.model.SessionSnapshot snapshot = new com.dbtool.model.SessionSnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.activeTabIndex = tabbedPane != null ? tabbedPane.getSelectedIndex() : 0;
        if (tabbedPane != null && snapshot.activeTabIndex >= 0 && snapshot.activeTabIndex < tabbedPane.getTabCount()) {
            snapshot.activeTabTitle = tabbedPane.getTitleAt(snapshot.activeTabIndex);
        }

        // Connection
        if (dbManager != null) {
            snapshot.connection = new com.dbtool.model.SessionSnapshot.ConnectionInfo();
            String lastType = dbManager.getLastType();
            String dumpFile = dbManager.getLastDumpFile();

            if (dbManager.isSandbox() || "SANDBOX".equalsIgnoreCase(lastType)) {
                snapshot.connection.type = "SANDBOX";
                snapshot.connection.dbName = dbManager.getLastDbName();
            } else if ("SQLITE".equalsIgnoreCase(lastType) || (dumpFile != null && (dumpFile.toLowerCase().endsWith(".sqlite") || dumpFile.toLowerCase().endsWith(".db") || dumpFile.toLowerCase().endsWith(".sqlite3")))) {
                snapshot.connection.type = "SQLITE";
                snapshot.connection.filePath = dumpFile;
                snapshot.connection.dbName = dbManager.getLastDbName();
            } else if ("ACCESS".equalsIgnoreCase(lastType) || (dumpFile != null && (dumpFile.toLowerCase().endsWith(".accdb") || dumpFile.toLowerCase().endsWith(".mdb")))) {
                snapshot.connection.type = "ACCESS";
                snapshot.connection.filePath = dumpFile;
                snapshot.connection.dbName = dbManager.getLastDbName();
            } else if ("MYSQL".equalsIgnoreCase(lastType)) {
                snapshot.connection.type = "MYSQL";
                snapshot.connection.host = dbManager.getLastHost();
                snapshot.connection.port = dbManager.getLastPort();
                snapshot.connection.dbName = dbManager.getLastDbName();
                snapshot.connection.user = dbManager.getLastUser();
                snapshot.connection.pass = dbManager.getLastPass();
            } else if (dumpFile != null && (dumpFile.toLowerCase().endsWith(".sql") || dumpFile.toLowerCase().endsWith(".sql.gz"))) {
                snapshot.connection.type = "POSTGRES";
                snapshot.connection.filePath = dumpFile;
                snapshot.connection.host = dbManager.getLastHost();
                snapshot.connection.port = dbManager.getLastPort();
                snapshot.connection.dbName = dbManager.getLastDbName();
                snapshot.connection.user = dbManager.getLastUser();
                snapshot.connection.pass = dbManager.getLastPass();
            } else if (dbManager.getLastHost() != null) {
                snapshot.connection.type = "POSTGRES";
                snapshot.connection.host = dbManager.getLastHost();
                snapshot.connection.port = dbManager.getLastPort();
                snapshot.connection.dbName = dbManager.getLastDbName();
                snapshot.connection.user = dbManager.getLastUser();
                snapshot.connection.pass = dbManager.getLastPass();
            } else if (dumpFile != null) {
                snapshot.connection.type = "FILE";
                snapshot.connection.filePath = dumpFile;
            } else {
                snapshot.connection.type = "NONE";
            }
        }

        // SQL Editor
        if (sqlEditorPanel != null) {
            snapshot.sqlEditor.queryText = sqlEditorPanel.getEditorText();
            sqlEditorPanel.saveEditorDraft();
        }

        // Browse Tabs
        if (browseTabContainer != null) {
            for (BrowseTab tab : browseTabContainer.getTabs()) {
                snapshot.browseTabs.add(tab.captureBrowseState());
            }
        }

        // Join Tabs
        if (joinTabContainer != null) {
            for (VisualJoinTab tab : joinTabContainer.getTabs()) {
                snapshot.joinTabs.add(tab.captureJoinState());
            }
        }

        // Table Compare
        if (tableComparePanel != null) {
            snapshot.tableCompare.queryA = tableComparePanel.getQueryA();
            snapshot.tableCompare.queryB = tableComparePanel.getQueryB();
        }

        return snapshot;
    }

    public void restoreSessionSnapshot(com.dbtool.model.SessionSnapshot snapshot) {
        if (snapshot == null) return;

        statusLabel.setText("Restoring workspace session...");
        statusLabel.setForeground(java.awt.Color.BLUE);

        new Thread(() -> {
            boolean dbReconnected = false;
            String errorMsg = null;

            if (snapshot.connection != null && !"NONE".equalsIgnoreCase(snapshot.connection.type)) {
                try {
                    String connType = snapshot.connection.type != null ? snapshot.connection.type.toUpperCase() : "NONE";
                    String filePath = snapshot.connection.filePath;

                    // Fallback detection if generic FILE
                    if ("FILE".equalsIgnoreCase(connType) && filePath != null) {
                        String lower = filePath.toLowerCase();
                        if (lower.endsWith(".sqlite") || lower.endsWith(".db") || lower.endsWith(".sqlite3")) {
                            connType = "SQLITE";
                        } else if (lower.endsWith(".accdb") || lower.endsWith(".mdb")) {
                            connType = "ACCESS";
                        } else if (lower.endsWith(".sql") || lower.endsWith(".sql.gz")) {
                            connType = "POSTGRES";
                        }
                    }

                    if ("SANDBOX".equalsIgnoreCase(connType)) {
                        dbManager.connectSandbox();
                        dbReconnected = true;
                    } else if ("SQLITE".equalsIgnoreCase(connType) && filePath != null) {
                        dbManager.connectSQLite(filePath);
                        dbReconnected = true;
                    } else if ("ACCESS".equalsIgnoreCase(connType) && filePath != null) {
                        dbManager.connect(filePath);
                        dbReconnected = true;
                    } else if ("MYSQL".equalsIgnoreCase(connType) && snapshot.connection.host != null) {
                        dbManager.connectMySQL(snapshot.connection.host, snapshot.connection.port, snapshot.connection.dbName, snapshot.connection.user, snapshot.connection.pass);
                        dbReconnected = true;
                    } else if ("POSTGRES".equalsIgnoreCase(connType)) {
                        if (filePath != null && !filePath.isEmpty() && (filePath.toLowerCase().endsWith(".sql") || filePath.toLowerCase().endsWith(".sql.gz"))) {
                            dbManager.connectPostgres(filePath,
                                    snapshot.connection.host != null ? snapshot.connection.host : "localhost",
                                    snapshot.connection.port != null ? snapshot.connection.port : "5432",
                                    snapshot.connection.user != null ? snapshot.connection.user : "postgres",
                                    snapshot.connection.pass != null ? snapshot.connection.pass : "postgres",
                                    snapshot.connection.dbName != null ? snapshot.connection.dbName : "analyzer_db");
                        } else if (snapshot.connection.host != null) {
                            dbManager.connectExistingPostgres(snapshot.connection.host, snapshot.connection.port, snapshot.connection.dbName, snapshot.connection.user, snapshot.connection.pass);
                        }
                        dbReconnected = true;
                    }

                    if (dbReconnected) {
                        dbManager.getTableNames();
                        dbManager.preloadAllColumns();
                        dbManager.preloadAllForeignKeys();
                    }
                } catch (Exception ex) {
                    errorMsg = ex.getMessage();
                    System.err.println("Could not auto-reconnect on restore: " + ex.getMessage());
                }
            }

            final boolean finalReconnected = dbReconnected;
            final String finalErrorMsg = errorMsg;

            SwingUtilities.invokeLater(() -> {
                // 1. Update Connection UI
                if (finalReconnected) {
                    String title = dbManager.isSandbox()
                            ? "Database: Sandbox (In-Memory)"
                            : "Connected: " + (dbManager.getLastDbName() != null ? dbManager.getLastDbName() : "Database");
                    statusLabel.setText(title);
                    statusLabel.setForeground(new java.awt.Color(0, 150, 0));
                    connectExistingBtn.setVisible(false);
                    loadDbButton.setVisible(false);
                    disconnectDbButton.setVisible(true);
                    exportDbBtn.setVisible(true);
                    populateTablesUI();
                } else if (finalErrorMsg != null) {
                    statusLabel.setText("Reconnection failed: " + finalErrorMsg);
                    statusLabel.setForeground(java.awt.Color.RED);
                }

                // 2. Restore SQL Editor
                if (sqlEditorPanel != null && snapshot.sqlEditor != null && snapshot.sqlEditor.queryText != null) {
                    sqlEditorPanel.setEditorText(snapshot.sqlEditor.queryText);
                }

                // 3. Restore Browse Tabs
                if (browseTabContainer != null && snapshot.browseTabs != null && !snapshot.browseTabs.isEmpty()) {
                    browseTabContainer.clearAllTabs();
                    for (com.dbtool.model.SessionSnapshot.BrowseState bs : snapshot.browseTabs) {
                        BrowseTab tab = browseTabContainer.addNewTabAndGet();
                        if (finalReconnected) {
                            tab.refreshTables();
                        }
                        tab.restoreBrowseState(bs);
                        if (finalReconnected && bs.tableName != null && !bs.tableName.trim().isEmpty()) {
                            tab.loadTableData();
                        }
                    }
                }

                // 4. Restore Visual Join Tabs
                if (joinTabContainer != null && snapshot.joinTabs != null && !snapshot.joinTabs.isEmpty()) {
                    joinTabContainer.clearAllTabs();
                    for (com.dbtool.model.VisualJoinState js : snapshot.joinTabs) {
                        VisualJoinTab tab = joinTabContainer.addNewTabAndGet();
                        if (finalReconnected) {
                            tab.refreshTables();
                        }
                        tab.restoreJoinState(js);
                        if (finalReconnected && js.baseTable != null && !js.baseTable.trim().isEmpty()) {
                            tab.executeVisualJoin();
                        }
                    }
                }

                // 5. Restore Table Compare
                if (tableComparePanel != null && snapshot.tableCompare != null) {
                    tableComparePanel.setQueryA(snapshot.tableCompare.queryA != null ? snapshot.tableCompare.queryA : "");
                    tableComparePanel.setQueryB(snapshot.tableCompare.queryB != null ? snapshot.tableCompare.queryB : "");
                }

                // 6. Restore Active Tab
                if (tabbedPane != null && snapshot.activeTabIndex >= 0 && snapshot.activeTabIndex < tabbedPane.getTabCount()) {
                    tabbedPane.setSelectedIndex(snapshot.activeTabIndex);
                }

                if (finalReconnected) {
                    JOptionPane.showMessageDialog(this, "Workspace session restored successfully!", "Session Restored", JOptionPane.INFORMATION_MESSAGE);
                } else if (snapshot.connection != null && !"NONE".equalsIgnoreCase(snapshot.connection.type)) {
                    JOptionPane.showMessageDialog(this, "Workspace tabs and queries restored, but database reconnection failed:\n" + (finalErrorMsg != null ? finalErrorMsg : "Unknown connection error") + "\n\nPlease connect manually to run queries.", "Partial Restore", JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Workspace session restored successfully (no database was active in snapshot).", "Session Restored", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        }).start();
    }

    

    

    

    

    



    

    

    

    private void loadDatabase() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Supported Database Files (*.sqlite, *.db, *.accdb, *.sql, *.sql.gz)",
            "sqlite", "db", "sqlite3", "accdb", "mdb", "sql", "gz"
        ));
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
            
            statusLabel.setText("Loading " + selectedFile.getName() + "...");
            statusLabel.setForeground(Color.BLUE);
            
            new Thread(() -> {
                try {
                    dbManager.connectPostgres(selectedFile.getAbsolutePath(), finalHost, finalPort, finalUser, finalPass, finalDbName);
                    
                    // Pre-fetch caches in bulk (2 queries instead of N*2) so we don't freeze EDT
                    dbManager.getTableNames();
                    dbManager.preloadAllColumns();
                    dbManager.preloadAllForeignKeys();

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



    private void showExportDatabaseDialog() {
        if (dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "No database connected.");
            return;
        }
        
        Object[] formats = {".sql", ".sql.gz", ".accdb"};
        String format = (String) JOptionPane.showInputDialog(this, 
                "Select export format:", "Export Database",
                JOptionPane.PLAIN_MESSAGE, null, formats, formats[0]);
                
        if (format == null) return;
        
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Database As...");
        
        String defaultName = "export";
        try {
            if (dbManager.connection != null) {
                String catalog = dbManager.connection.getCatalog();
                if (catalog != null && !catalog.isEmpty()) {
                    defaultName = catalog;
                } else {
                    String url = dbManager.connection.getMetaData().getURL();
                    if (url != null) {
                        int lastSlash = url.lastIndexOf('/');
                        if (lastSlash > -1) {
                            int qMark = url.indexOf('?', lastSlash);
                            if (qMark > -1) {
                                defaultName = url.substring(lastSlash + 1, qMark);
                            } else {
                                defaultName = url.substring(lastSlash + 1);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {}
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd_MM_yyyy_HH'h'mm");
        defaultName = defaultName + "_" + sdf.format(new java.util.Date());
        
        chooser.setSelectedFile(new java.io.File(defaultName + format));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File targetFile = chooser.getSelectedFile();
            
            // Show a waiting dialog
            JDialog loadingDialog = new JDialog(this, "Exporting...", true);
            loadingDialog.setSize(400, 150);
            loadingDialog.setLocationRelativeTo(this);
            loadingDialog.setLayout(new BorderLayout(10, 10));
            
            JLabel statusLabel = new JLabel("Exporting database, please wait...", SwingConstants.CENTER);
            loadingDialog.add(statusLabel, BorderLayout.NORTH);
            
            JProgressBar progressBar = new JProgressBar();
            progressBar.setStringPainted(true);
            loadingDialog.add(progressBar, BorderLayout.CENTER);
            
            JButton cancelBtn = new JButton("Cancel");
            final boolean[] isCancelled = {false};
            cancelBtn.addActionListener(e -> {
                isCancelled[0] = true;
                cancelBtn.setEnabled(false);
                cancelBtn.setText("Cancelling...");
            });
            JPanel bottomPanel = new JPanel();
            bottomPanel.add(cancelBtn);
            loadingDialog.add(bottomPanel, BorderLayout.SOUTH);
            
            new Thread(() -> {
                try {
                    com.dbtool.core.export.ExportProgressMonitor monitor = new com.dbtool.core.export.ExportProgressMonitor() {
                        public void onProgress(int processedRows, int totalRows) {
                            SwingUtilities.invokeLater(() -> {
                                if (totalRows > 0) {
                                    int pct = (int) ((processedRows * 100L) / totalRows);
                                    progressBar.setValue(pct);
                                    progressBar.setString("Exported " + processedRows + " / " + totalRows + " rows (" + pct + "%)");
                                } else {
                                    progressBar.setIndeterminate(true);
                                    progressBar.setString(processedRows + " rows exported...");
                                }
                            });
                        }
                        public boolean isCancelled() { return isCancelled[0]; }
                    };
                    
                    com.dbtool.core.export.FullDatabaseExporter.exportDatabase(dbManager.connection, "postgres", targetFile, format.replace(".", ""), monitor);
                    SwingUtilities.invokeLater(() -> {
                        loadingDialog.dispose();
                        JOptionPane.showMessageDialog(Main.this, "Database exported successfully to:\n" + targetFile.getAbsolutePath());
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        loadingDialog.dispose();
                        JOptionPane.showMessageDialog(Main.this, "Error exporting database:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
            loadingDialog.setVisible(true);
        }
    }

    private void disconnectDatabase() {
        if (dbManager.connection != null) {
            try { dbManager.connection.close(); } catch (Exception e) {}
            dbManager.connection = null;
            dbManager.clearCache();
        }
        loadedTables.clear();
        
        if (browseTabContainer != null) {
            for (BrowseTab tab : browseTabContainer.getTabs()) {
                tab.refreshTables();
            }
        }
        if (joinTabContainer != null) {
            for (VisualJoinTab tab : joinTabContainer.getTabs()) {
                tab.refreshTables();
                tab.clearJoins();
            }
        }
        
        schemaPanel.refresh();
        if (erDiagramPanel != null) erDiagramPanel.refresh(null, null);
        if (dashboardPanel != null) dashboardPanel.refresh();
        tableComparePanel.refreshTables();
        if (dataOpsPanel != null) dataOpsPanel.refreshTables();
        
        statusLabel.setText("Disconnected");
        statusLabel.setForeground(java.awt.Color.BLACK);
        
        loadDbButton.setEnabled(true);
        loadDbButton.setVisible(true);
        connectExistingBtn.setEnabled(true);
        connectExistingBtn.setVisible(true);
        sandboxBtn.setEnabled(true);
        sandboxBtn.setVisible(true);
        seedSampleBtn.setVisible(false);
        disconnectDbButton.setVisible(false);
        exportDbBtn.setVisible(false);
    }




    private void populateTablesUI() {
        loadDbButton.setEnabled(false);
        loadDbButton.setVisible(false);
        connectExistingBtn.setEnabled(false);
        connectExistingBtn.setVisible(false);
        sandboxBtn.setVisible(false);
        seedSampleBtn.setVisible(true);
        disconnectDbButton.setVisible(true);
        exportDbBtn.setVisible(true);
        loadedTables = dbManager.getTableNames();
        if (browseTabContainer != null) {
            for (BrowseTab tab : browseTabContainer.getTabs()) {
                tab.refreshTables();
            }
        }
        if (joinTabContainer != null) {
            for (VisualJoinTab tab : joinTabContainer.getTabs()) {
                tab.refreshTables();
            }
        }
        schemaPanel.refresh();
        if (erDiagramPanel != null) erDiagramPanel.markNeedsRefresh(dbManager);
        if (dashboardPanel != null && tabbedPane != null && tabbedPane.getSelectedComponent() == dashboardPanel) {
            dashboardPanel.refresh();
        }
    }

    private void connectExistingDatabase() {
        com.dbtool.panels.ConnectionManagerDialog dialog = new com.dbtool.panels.ConnectionManagerDialog(this, dbManager);
        dialog.setVisible(true);
        if (!dialog.isApproved()) {
            return;
        }

        final String finalType = dialog.getSelectedType();
        final com.dbtool.core.model.SslMode finalSslMode = dialog.getSelectedSslMode();
        final String finalHost = dialog.getHost();
        final String finalPort = dialog.getPort();
        final String finalUser = dialog.getUser();
        final String finalPass = dialog.getPass();
        final String finalDbName = dialog.getDbName();
        
        statusLabel.setText("Connecting to " + finalDbName + " (" + finalType + ")...");
        statusLabel.setForeground(java.awt.Color.BLUE);
        
        new Thread(() -> {
            try {
                if ("MySQL".equalsIgnoreCase(finalType)) {
                    dbManager.connectMySQL(finalHost, finalPort, finalDbName, finalUser, finalPass, finalSslMode);
                } else if ("SQLite".equalsIgnoreCase(finalType)) {
                    dbManager.connectSQLite(finalDbName);
                } else {
                    dbManager.connectExistingPostgres(finalHost, finalPort, finalDbName, finalUser, finalPass, finalSslMode);
                }
                
                // Pre-fetch caches in bulk (2 queries instead of N*2) so we don't freeze EDT
                dbManager.getTableNames();
                dbManager.preloadAllColumns();
                dbManager.preloadAllForeignKeys();

                javax.swing.SwingUtilities.invokeLater(() -> {
                    try {
                        statusLabel.setText("Connected: " + finalDbName);
                        statusLabel.setForeground(new java.awt.Color(0, 150, 0));
                        populateTablesUI();
                        
                        connectExistingBtn.setVisible(false);
                        loadDbButton.setVisible(false);
                        disconnectDbButton.setVisible(true);
                        exportDbBtn.setVisible(true);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        statusLabel.setText("Failed to load tables.");
                        statusLabel.setForeground(java.awt.Color.RED);
                        javax.swing.JOptionPane.showMessageDialog(Main.this, "Failed to load tables:\n" + ex.getMessage(), "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connection Failed.");
                    statusLabel.setForeground(java.awt.Color.RED);
                    javax.swing.JOptionPane.showMessageDialog(Main.this, "Failed to connect:\n" + ex.getMessage(), "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    

    
    
    

    

    
    

    

    

    

    

    

    


    
    
    
    

    // Inner class representing a single Join row in the UI





        public class AggregateRowPanel extends JPanel {
        JComboBox<String> funcDropdown = new JComboBox<>(new String[]{
            "COUNT", "COUNT(DISTINCT)", "SUM", "AVG", "MIN", "MAX"
        });
        public SearchableComboBox tableDropdown;
        SearchableComboBox colDropdown = new SearchableComboBox();
        JTextField aliasField = new JTextField(10);
        JTextField filterField = new JTextField(12);
        JButton removeBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove aggregate row");

        public AggregateRowPanel(boolean showTable, List<AggregateRowPanel> ownerList, JPanel container) {
            com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
            add(com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
                com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.AGGREGATE
            ));
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(funcDropdown, 128);
            add(funcDropdown);
            if (showTable) {
                tableDropdown = new SearchableComboBox();
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    tableDropdown, com.dbtool.ui.components.ClauseBadgeFactory.TABLE_WIDTH
                );
                tableDropdown.addActionListener(e -> {
                    String tbl = (String) tableDropdown.getSelectedItem();
                    if (tbl != null) {
                        new Thread(() -> {
                            List<String> cols = dbManager.getColumnNames(tbl);
                            SwingUtilities.invokeLater(() -> updateColumns(cols));
                        }).start();
                    }
                });
                add(tableDropdown);
                add(com.dbtool.ui.components.ClauseBadgeFactory.createDotSeparator());
            }
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                colDropdown, com.dbtool.ui.components.ClauseBadgeFactory.COLUMN_WIDTH
            );
            add(colDropdown);

            add(new JLabel("AS"));
            aliasField.setToolTipText("Column alias name");
            aliasField.setPreferredSize(new Dimension(95, com.dbtool.ui.components.ClauseBadgeFactory.ROW_HEIGHT));
            add(aliasField);

            add(new JLabel("FILTER (WHERE:"));
            filterField.setToolTipText("Optional SQL FILTER predicate, e.g. salary > 100000 or status = 'open'");
            filterField.setPreferredSize(new Dimension(115, com.dbtool.ui.components.ClauseBadgeFactory.ROW_HEIGHT));
            add(filterField);
            add(new JLabel(")"));

            removeBtn.addActionListener(e -> {
                ownerList.remove(this);
                container.remove(this);
                container.revalidate();
                container.repaint();
            });
            add(removeBtn);

            funcDropdown.addActionListener(e -> {
                String f = (String) funcDropdown.getSelectedItem();
                String c = (String) colDropdown.getSelectedItem();
                if (f != null && aliasField.getText().trim().isEmpty() && c != null && !c.isEmpty()) {
                    String cleanF = f.replace("(DISTINCT)", "_distinct").toLowerCase();
                    aliasField.setText(cleanF + "_" + (c.equals("*") ? "rows" : c));
                }
            });
        }

        public void updateTables(List<String> tables) {
            if (tableDropdown != null) {
                tableDropdown.setAllItems(tables);
            }
        }

        public void updateColumns(List<String> cols) {
            List<String> withStar = new ArrayList<>();
            withStar.add("*");
            withStar.addAll(cols);
            colDropdown.setAllItems(withStar);
            if (colDropdown.getSelectedItem() == null && !withStar.isEmpty()) {
                colDropdown.setSelectedItem("*");
            }
        }

        public String buildExpression(DatabaseManager dbManager) {
            String func = (String) funcDropdown.getSelectedItem();
            if (func == null) return "";
            String col = (String) colDropdown.getSelectedItem();
            if (col == null || col.isEmpty()) col = "*";

            String qualifiedCol;
            if (tableDropdown != null && tableDropdown.getSelectedItem() != null) {
                String tbl = (String) tableDropdown.getSelectedItem();
                qualifiedCol = col.equals("*") ? "*" : dbManager.quoteColumnName(tbl + "." + col);
            } else {
                qualifiedCol = col.equals("*") ? "*" : dbManager.quoteColumnName(col);
            }

            String expr = func.equals("COUNT(DISTINCT)")
                ? "COUNT(DISTINCT " + qualifiedCol + ")"
                : func + "(" + qualifiedCol + ")";

            String filter = filterField.getText().trim();
            if (!filter.isEmpty()) {
                expr += " FILTER (WHERE " + filter + ")";
            }

            String alias = aliasField.getText().trim();
            if (!alias.isEmpty()) {
                expr += " AS " + alias;
            }
            return expr;
        }
    }

    public class GroupByRowPanel extends JPanel {
        public SearchableComboBox tableDropdown;
        SearchableComboBox colDropdown = new SearchableComboBox();
        JButton removeBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove GROUP BY column");

        public GroupByRowPanel(boolean showTable, List<GroupByRowPanel> ownerList, JPanel container) {
            com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
            add(com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
                com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.GROUP_BY
            ));
            if (showTable) {
                tableDropdown = new SearchableComboBox();
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    tableDropdown, com.dbtool.ui.components.ClauseBadgeFactory.TABLE_WIDTH
                );
                tableDropdown.addActionListener(e -> {
                    String tbl = (String) tableDropdown.getSelectedItem();
                    if (tbl != null) {
                        new Thread(() -> {
                            List<String> cols = dbManager.getColumnNames(tbl);
                            SwingUtilities.invokeLater(() -> updateColumns(cols));
                        }).start();
                    }
                });
                add(tableDropdown);
                add(com.dbtool.ui.components.ClauseBadgeFactory.createDotSeparator());
            }
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                colDropdown, com.dbtool.ui.components.ClauseBadgeFactory.COLUMN_WIDTH
            );
            add(colDropdown);

            removeBtn.addActionListener(e -> {
                ownerList.remove(this);
                container.remove(this);
                container.revalidate();
                container.repaint();
            });
            add(removeBtn);
        }

        public void updateTables(List<String> tables) {
            if (tableDropdown != null) {
                tableDropdown.setAllItems(tables);
            }
        }

        public void updateColumns(List<String> cols) {
            colDropdown.setAllItems(cols);
        }

        public String getColumnExpression(DatabaseManager dbManager) {
            String col = (String) colDropdown.getSelectedItem();
            if (col == null || col.isEmpty()) return "";
            if (tableDropdown != null && tableDropdown.getSelectedItem() != null) {
                return dbManager.quoteColumnName(tableDropdown.getSelectedItem() + "." + col);
            }
            return dbManager.quoteColumnName(col);
        }
    }

    public class HavingRowPanel extends JPanel {
        public JComboBox<String> logicDropdown = new JComboBox<>(new String[]{"AND", "OR"});
        JComboBox<String> funcDropdown = new JComboBox<>(new String[]{
            "COUNT(*)", "COUNT", "SUM", "AVG", "MIN", "MAX"
        });
        public SearchableComboBox tableDropdown;
        SearchableComboBox colDropdown = new SearchableComboBox();
        JComboBox<String> operatorDropdown = new JComboBox<>(new String[]{
            ">", "<", ">=", "<=", "=", "!=", "BETWEEN"
        });
        JTextField valueField = new JTextField(10);
        JButton removeBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove HAVING condition");
        private final JComponent havingBadge = com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
            com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.HAVING
        );
        private final JPanel prefixSlot = new JPanel(new BorderLayout());

        public void setFirst(boolean isFirst) {
            prefixSlot.removeAll();
            if (isFirst) {
                prefixSlot.add(havingBadge, BorderLayout.CENTER);
            } else {
                prefixSlot.add(logicDropdown, BorderLayout.CENTER);
            }
            prefixSlot.revalidate();
            prefixSlot.repaint();
        }

        public HavingRowPanel(boolean isFirst, boolean showTable, List<HavingRowPanel> ownerList, JPanel container) {
            com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
            com.dbtool.ui.components.ClauseBadgeFactory.styleLogicDropdown(logicDropdown);
            prefixSlot.setOpaque(false);
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                prefixSlot, com.dbtool.ui.components.ClauseBadgeFactory.PREFIX_WIDTH
            );
            setFirst(isFirst);
            add(prefixSlot);
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(funcDropdown, 105);
            add(funcDropdown);
            if (showTable) {
                tableDropdown = new SearchableComboBox();
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    tableDropdown, com.dbtool.ui.components.ClauseBadgeFactory.TABLE_WIDTH
                );
                tableDropdown.addActionListener(e -> {
                    String tbl = (String) tableDropdown.getSelectedItem();
                    if (tbl != null) {
                        new Thread(() -> {
                            List<String> cols = dbManager.getColumnNames(tbl);
                            SwingUtilities.invokeLater(() -> updateColumns(cols));
                        }).start();
                    }
                });
                add(tableDropdown);
                add(com.dbtool.ui.components.ClauseBadgeFactory.createDotSeparator());
            }
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                colDropdown, com.dbtool.ui.components.ClauseBadgeFactory.COLUMN_WIDTH
            );
            add(colDropdown);
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                operatorDropdown, com.dbtool.ui.components.ClauseBadgeFactory.OPERATOR_WIDTH
            );
            add(operatorDropdown);
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                valueField, com.dbtool.ui.components.ClauseBadgeFactory.VALUE_WIDTH
            );
            valueField.putClientProperty("JTextField.placeholderText", "Enter value...");
            add(valueField);

            removeBtn.addActionListener(e -> {
                ownerList.remove(this);
                container.remove(this);
                for (int i = 0; i < ownerList.size(); i++) {
                    ownerList.get(i).setFirst(i == 0);
                }
                container.revalidate();
                container.repaint();
            });
            add(removeBtn);

            funcDropdown.addActionListener(e -> {
                String f = (String) funcDropdown.getSelectedItem();
                colDropdown.setEnabled(f != null && !f.equals("COUNT(*)"));
            });
            colDropdown.setEnabled(false);
        }

        public void updateTables(List<String> tables) {
            if (tableDropdown != null) {
                tableDropdown.setAllItems(tables);
            }
        }

        public void updateColumns(List<String> cols) {
            colDropdown.setAllItems(cols);
        }

        public String buildClause(DatabaseManager dbManager) {
            String f = (String) funcDropdown.getSelectedItem();
            String val = valueField.getText().trim();
            if (f == null || val.isEmpty()) return "";

            String expr;
            if (f.equals("COUNT(*)")) {
                expr = "COUNT(*)";
            } else {
                String col = (String) colDropdown.getSelectedItem();
                if (col == null || col.isEmpty()) return "";
                if (tableDropdown != null && tableDropdown.getSelectedItem() != null) {
                    expr = f + "(" + dbManager.quoteColumnName(tableDropdown.getSelectedItem() + "." + col) + ")";
                } else {
                    expr = f + "(" + dbManager.quoteColumnName(col) + ")";
                }
            }
            String op = (String) operatorDropdown.getSelectedItem();
            return expr + " " + op + " " + val;
        }
    }

    public class WindowFuncRowPanel extends JPanel {
        JComboBox<String> funcDropdown = new JComboBox<>(new String[]{
            "ROW_NUMBER()", "RANK()", "DENSE_RANK()", "NTILE(4)", "NTILE(10)",
            "SUM", "AVG", "COUNT", "MIN", "MAX",
            "FIRST_VALUE", "LAST_VALUE", "LAG", "LEAD"
        });
        SearchableComboBox valColDropdown = new SearchableComboBox();
        SearchableComboBox partitionColDropdown = new SearchableComboBox();
        SearchableComboBox orderColDropdown = new SearchableComboBox();
        JComboBox<String> orderDirDropdown = new JComboBox<>(new String[]{"ASC", "DESC"});
        JComboBox<String> frameDropdown = new JComboBox<>(new String[]{
            "Default Frame",
            "ROWS UNBOUNDED PRECEDING AND CURRENT ROW",
            "ROWS 6 PRECEDING AND CURRENT ROW",
            "ROWS 3 PRECEDING AND 3 FOLLOWING",
            "RANGE UNBOUNDED PRECEDING AND CURRENT ROW",
            "ROWS 2 PRECEDING AND 2 FOLLOWING EXCLUDE CURRENT ROW"
        });
        JTextField aliasField = new JTextField(10);
        JButton removeBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove WINDOW function row");

        public WindowFuncRowPanel(List<WindowFuncRowPanel> ownerList, JPanel container) {
            com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
            add(com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
                com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.WINDOW
            ));
            add(funcDropdown);

            valColDropdown.setPreferredSize(new Dimension(110, 26));
            add(new JLabel("Col:"));
            add(valColDropdown);

            partitionColDropdown.setPreferredSize(new Dimension(120, 26));
            add(new JLabel("PARTITION BY:"));
            add(partitionColDropdown);

            orderColDropdown.setPreferredSize(new Dimension(120, 26));
            add(new JLabel("ORDER BY:"));
            add(orderColDropdown);
            add(orderDirDropdown);

            add(new JLabel("Frame:"));
            add(frameDropdown);

            add(new JLabel("AS:"));
            aliasField.setPreferredSize(new Dimension(90, 26));
            add(aliasField);

            removeBtn.addActionListener(e -> {
                ownerList.remove(this);
                container.remove(this);
                container.revalidate();
                container.repaint();
            });
            add(removeBtn);

            funcDropdown.addActionListener(e -> {
                String f = (String) funcDropdown.getSelectedItem();
                boolean needsCol = f != null && !f.endsWith("()");
                valColDropdown.setEnabled(needsCol);
                if (f != null && aliasField.getText().trim().isEmpty()) {
                    String base = f.replace("()", "").replace("(4)", "_q").replace("(10)", "_dec").toLowerCase();
                    aliasField.setText(base + "_val");
                }
            });
            valColDropdown.setEnabled(false);
        }

        public void updateColumns(List<String> cols) {
            List<String> withNone = new ArrayList<>();
            withNone.add("-- None --");
            withNone.addAll(cols);

            valColDropdown.setAllItems(cols);
            partitionColDropdown.setAllItems(withNone);
            orderColDropdown.setAllItems(withNone);
        }

        public String buildExpression(DatabaseManager dbManager) {
            String f = (String) funcDropdown.getSelectedItem();
            if (f == null) return "";

            String funcExpr;
            if (f.endsWith("()") || f.startsWith("NTILE")) {
                funcExpr = f;
            } else {
                String col = (String) valColDropdown.getSelectedItem();
                if (col == null || col.isEmpty() || col.equals("-- None --")) col = "*";
                funcExpr = f + "(" + (col.equals("*") ? "*" : dbManager.quoteColumnName(col)) + ")";
            }

            StringBuilder over = new StringBuilder(" OVER (");
            boolean hasOverSpec = false;

            String pCol = (String) partitionColDropdown.getSelectedItem();
            if (pCol != null && !pCol.isEmpty() && !pCol.equals("-- None --")) {
                over.append("PARTITION BY ").append(dbManager.quoteColumnName(pCol));
                hasOverSpec = true;
            }

            String oCol = (String) orderColDropdown.getSelectedItem();
            if (oCol != null && !oCol.isEmpty() && !oCol.equals("-- None --")) {
                if (hasOverSpec) over.append(" ");
                over.append("ORDER BY ").append(dbManager.quoteColumnName(oCol)).append(" ").append(orderDirDropdown.getSelectedItem());
                hasOverSpec = true;
            }

            String frame = (String) frameDropdown.getSelectedItem();
            if (frame != null && !frame.equals("Default Frame")) {
                if (hasOverSpec) over.append(" ");
                over.append(frame);
                hasOverSpec = true;
            }

            over.append(")");

            String res = funcExpr + over.toString();
            String al = aliasField.getText().trim();
            if (!al.isEmpty()) {
                res += " AS " + al;
            }
            return res;
        }
    }

    public class OrderByPanel extends JPanel {
        public SearchableComboBox tableDropdown = new SearchableComboBox();
        public SearchableComboBox columnDropdown = new SearchableComboBox();
        public JComboBox<String> dirDropdown = new JComboBox<>(new String[]{"ASC", "DESC"});

        public OrderByPanel(boolean showTable, List<OrderByPanel> ownerList, JPanel ownerContainer) {
            com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
            add(com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
                com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.ORDER_BY
            ));
            
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                tableDropdown, com.dbtool.ui.components.ClauseBadgeFactory.TABLE_WIDTH
            );
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                columnDropdown, com.dbtool.ui.components.ClauseBadgeFactory.COLUMN_WIDTH
            );
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                dirDropdown, com.dbtool.ui.components.ClauseBadgeFactory.OPERATOR_WIDTH
            );

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
                add(com.dbtool.ui.components.ClauseBadgeFactory.createDotSeparator());
            }
            
            add(columnDropdown);
            add(dirDropdown);
            
            JButton removeBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove ORDER BY clause");
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

        public VisualJoinState.OrderByState captureOrderByState() {
            VisualJoinState.OrderByState os = new VisualJoinState.OrderByState();
            os.table = (String) tableDropdown.getSelectedItem();
            os.column = (String) columnDropdown.getSelectedItem();
            os.direction = (String) dirDropdown.getSelectedItem();
            return os;
        }

        public void restoreOrderByState(VisualJoinState.OrderByState os) {
            if (os == null) return;
            if (os.table != null) tableDropdown.setSelectedItem(os.table);
            if (os.column != null) columnDropdown.setSelectedItem(os.column);
            if (os.direction != null) dirDropdown.setSelectedItem(os.direction);
        }
    }

    public class WherePanel extends JPanel {
        public JComboBox<String> logicDropdown = new JComboBox<>(new String[]{"AND", "OR"});
        public SearchableComboBox tableDropdown = new SearchableComboBox();
        public SearchableComboBox columnDropdown = new SearchableComboBox();
        JComboBox<String> operatorDropdown = new JComboBox<>(new String[]{"=", "!=", ">", "<", ">=", "<=", "LIKE", "ILIKE", "IN", "NOT IN", "BETWEEN", "IS NULL", "IS NOT NULL"});
        JTextField valueField = new JTextField(15);

        // IN / NOT IN enhanced controls
        JComboBox<String> inTypeDropdown = new JComboBox<>(new String[]{"Distinct Values", "Subquery (SELECT)", "Manual Values"});
        SearchableComboBox inValuesDropdown = new SearchableComboBox();
        JButton inPickBtn = new JButton("☑ Pick...");
        JPanel inControlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

        // Subquery controls
        JLabel subSelectLabel = new JLabel("(SELECT ");
        SearchableComboBox inSubColDropdown = new SearchableComboBox();
        JLabel subFromLabel = new JLabel(" FROM ");
        SearchableComboBox inSubTableDropdown = new SearchableComboBox();
        JButton inSubClausesBtn = new JButton("⚙ Clauses (WHERE, ORDER...)");
        JLabel subCloseLabel = new JLabel(") ");

        // Subquery Clauses State
        class SubqueryWhereCondition {
            String logic = "AND";
            String column = "";
            String operator = "=";
            String value = "";

            public SubqueryWhereCondition() {}
            public SubqueryWhereCondition(String logic, String column, String operator, String value) {
                this.logic = logic;
                this.column = column;
                this.operator = operator;
                this.value = value;
            }
        }

        private boolean subDistinct = false;
        private List<SubqueryWhereCondition> subWhereConditions = new ArrayList<>();
        private String subOrderByCol = "";
        private String subOrderDir = "ASC";
        private String subLimit = "";
        private String subOffset = "";
        private String subGroupByCol = "";
        private String subHaving = "";
        private boolean useCustomSubquery = false;
        private String customSubqueryText = "";

        private List<String> cachedDistinctValues = new ArrayList<>();
        private final JComponent whereBadge = com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
            com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.WHERE
        );
        private final JPanel prefixSlot = new JPanel(new BorderLayout());

        public void setFirst(boolean isFirst) {
            prefixSlot.removeAll();
            if (isFirst) {
                prefixSlot.add(whereBadge, BorderLayout.CENTER);
            } else {
                prefixSlot.add(logicDropdown, BorderLayout.CENTER);
            }
            prefixSlot.revalidate();
            prefixSlot.repaint();
        }

        public WherePanel(boolean isFirst, List<WherePanel> ownerList, JPanel ownerContainer) {
            com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
            com.dbtool.ui.components.ClauseBadgeFactory.styleLogicDropdown(logicDropdown);
            prefixSlot.setOpaque(false);
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                prefixSlot, com.dbtool.ui.components.ClauseBadgeFactory.PREFIX_WIDTH
            );
            setFirst(isFirst);
            add(prefixSlot);

            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                tableDropdown, com.dbtool.ui.components.ClauseBadgeFactory.TABLE_WIDTH
            );
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                columnDropdown, com.dbtool.ui.components.ClauseBadgeFactory.COLUMN_WIDTH
            );
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                operatorDropdown, com.dbtool.ui.components.ClauseBadgeFactory.OPERATOR_WIDTH
            );
            com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                valueField, com.dbtool.ui.components.ClauseBadgeFactory.VALUE_WIDTH
            );
            valueField.putClientProperty("JTextField.placeholderText", "Enter value...");
            inValuesDropdown.setPreferredSize(new Dimension(140, 26));
            inSubTableDropdown.setPreferredSize(new Dimension(130, 26));
            inSubColDropdown.setPreferredSize(new Dimension(130, 26));

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

            columnDropdown.addActionListener(e -> {
                String op = (String) operatorDropdown.getSelectedItem();
                if (op != null && (op.equalsIgnoreCase("IN") || op.equalsIgnoreCase("NOT IN"))) {
                    loadDistinctValues();
                }
            });

            add(tableDropdown);
            add(com.dbtool.ui.components.ClauseBadgeFactory.createDotSeparator());
            add(columnDropdown);
            add(operatorDropdown);

            inControlsPanel.setOpaque(false);
            inControlsPanel.setVisible(false);
            inTypeDropdown.setVisible(false);

            inPickBtn.setToolTipText("Open checklist dialog to select multiple distinct values");
            inPickBtn.addActionListener(e -> openMultiSelectDialog());

            inValuesDropdown.addActionListener(e -> {
                if (inValuesDropdown.isAdjusting) return;
                Object sel = inValuesDropdown.getSelectedItem();
                if (sel != null && !sel.toString().trim().isEmpty()) {
                    String formatted = formatSingleValue(sel.toString().trim());
                    String cur = valueField.getText().trim();
                    if (cur.isEmpty()) {
                        valueField.setText(formatted);
                    } else if (!cur.contains(formatted)) {
                        valueField.setText(cur + ", " + formatted);
                    }
                }
            });

            inSubClausesBtn.setToolTipText("Configure WHERE conditions, ORDER BY, LIMIT, and other clauses for subquery");
            inSubClausesBtn.addActionListener(e -> openSubqueryClausesDialog());

            inSubTableDropdown.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    if (inSubTableDropdown.getModel().getSize() <= 1 && dbManager != null && dbManager.connection != null) {
                        fetchTablesFromDb();
                    }
                }
                @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
                @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
            });

            inSubTableDropdown.addActionListener(e -> {
                String subT = (String) inSubTableDropdown.getSelectedItem();
                if (subT != null) {
                    new Thread(() -> {
                        List<String> cols = dbManager.getColumnNames(subT);
                        SwingUtilities.invokeLater(() -> {
                            Object cur = inSubColDropdown.getSelectedItem();
                            inSubColDropdown.setAllItems(cols);
                            String targetCol = (String) columnDropdown.getSelectedItem();
                            if (targetCol != null && cols.contains(targetCol)) {
                                inSubColDropdown.setSelectedItem(targetCol);
                            } else if (cur != null && cols.contains(cur)) {
                                inSubColDropdown.setSelectedItem(cur);
                            } else if (!cols.isEmpty()) {
                                inSubColDropdown.setSelectedItem(cols.get(0));
                            }
                            updateSubClausesBtnDisplay();
                        });
                    }).start();
                }
            });

            inTypeDropdown.addActionListener(e -> updateInControlsLayout());

            operatorDropdown.addActionListener(e -> {
                String op = (String) operatorDropdown.getSelectedItem();
                boolean isNullOp = op != null && op.contains("NULL");
                boolean isInOp = op != null && (op.equalsIgnoreCase("IN") || op.equalsIgnoreCase("NOT IN"));

                valueField.setEnabled(!isNullOp);
                inTypeDropdown.setVisible(isInOp);
                inControlsPanel.setVisible(isInOp);

                if (isInOp) {
                    updateInControlsLayout();
                    loadDistinctValues();
                    fetchTablesFromDb();
                } else {
                    valueField.setVisible(true);
                    valueField.setToolTipText(null);
                }
                revalidate();
                repaint();
            });

            add(inTypeDropdown);
            add(inControlsPanel);
            add(valueField);

            JButton removeBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove WHERE condition");
            removeBtn.addActionListener(e -> {
                ownerList.remove(this);
                ownerContainer.remove(this);
                for (int i = 0; i < ownerList.size(); i++) {
                    ownerList.get(i).setFirst(i == 0);
                }
                ownerContainer.revalidate();
                ownerContainer.repaint();
            });
            add(removeBtn);

            fetchTablesFromDb();
        }

        public void fetchTablesFromDb() {
            new Thread(() -> {
                List<String> tbls = null;
                if (dbManager != null && dbManager.connection != null) {
                    try {
                        tbls = dbManager.getTableNames();
                    } catch (Exception ignored) {}
                }
                if (tbls == null || tbls.isEmpty()) {
                    tbls = loadedTables;
                }
                if (tbls != null && !tbls.isEmpty()) {
                    final List<String> fTbls = new ArrayList<>(tbls);
                    SwingUtilities.invokeLater(() -> {
                        Object cur = inSubTableDropdown.getSelectedItem();
                        inSubTableDropdown.setAllItems(fTbls);
                        if (cur != null && fTbls.contains(cur)) {
                            inSubTableDropdown.setSelectedItem(cur);
                        } else {
                            String parentTbl = (String) tableDropdown.getSelectedItem();
                            if (parentTbl != null && fTbls.contains(parentTbl)) {
                                inSubTableDropdown.setSelectedItem(parentTbl);
                            } else if (inSubTableDropdown.getItemCount() > 0) {
                                inSubTableDropdown.setSelectedIndex(0);
                            }
                        }
                        String selT = (String) inSubTableDropdown.getSelectedItem();
                        if (selT != null) {
                            new Thread(() -> {
                                List<String> cols = dbManager.getColumnNames(selT);
                                SwingUtilities.invokeLater(() -> {
                                    Object curCol = inSubColDropdown.getSelectedItem();
                                    inSubColDropdown.setAllItems(cols);
                                    String targetCol = (String) columnDropdown.getSelectedItem();
                                    if (targetCol != null && cols.contains(targetCol)) {
                                        inSubColDropdown.setSelectedItem(targetCol);
                                    } else if (curCol != null && cols.contains(curCol)) {
                                        inSubColDropdown.setSelectedItem(curCol);
                                    } else if (!cols.isEmpty()) {
                                        inSubColDropdown.setSelectedItem(cols.get(0));
                                    }
                                    updateSubClausesBtnDisplay();
                                });
                            }).start();
                        }
                    });
                }
            }).start();
        }

        private void updateInControlsLayout() {
            inControlsPanel.removeAll();
            String op = (String) operatorDropdown.getSelectedItem();
            boolean isInOp = op != null && (op.equalsIgnoreCase("IN") || op.equalsIgnoreCase("NOT IN"));
            String inType = (String) inTypeDropdown.getSelectedItem();

            if ("Distinct Values".equals(inType)) {
                inControlsPanel.add(inValuesDropdown);
                inControlsPanel.add(inPickBtn);
                valueField.setVisible(true);
                if (isInOp) {
                    valueField.setToolTipText("Comma-separated values (pick from dropdown or click Pick...)");
                }
            } else if ("Subquery (SELECT)".equals(inType)) {
                inControlsPanel.add(subSelectLabel);
                inControlsPanel.add(inSubColDropdown);
                inControlsPanel.add(subFromLabel);
                inControlsPanel.add(inSubTableDropdown);
                inControlsPanel.add(inSubClausesBtn);
                inControlsPanel.add(subCloseLabel);
                valueField.setVisible(!isInOp);

                if (isInOp) {
                    fetchTablesFromDb();
                    updateSubClausesBtnDisplay();
                }
            } else { // Manual Values
                valueField.setVisible(true);
                if (isInOp) {
                    valueField.setToolTipText("Enter values separated by commas: e.g. 101, 102 or 'IT', 'HR'");
                }
            }
            inControlsPanel.revalidate();
            inControlsPanel.repaint();
            revalidate();
            repaint();
        }

        private void updateSubClausesBtnDisplay() {
            if (useCustomSubquery && customSubqueryText != null && !customSubqueryText.trim().isEmpty()) {
                inSubClausesBtn.setText("⚙ Custom SQL (Active)");
                inSubClausesBtn.setForeground(new Color(0, 140, 0));
                inSubClausesBtn.setToolTipText("<html><b>Custom Subquery SQL:</b><br><pre>" + customSubqueryText + "</pre></html>");
                return;
            }

            List<String> activeList = new ArrayList<>();
            if (subDistinct) activeList.add("DISTINCT");
            int whereCount = 0;
            if (subWhereConditions != null) {
                for (SubqueryWhereCondition c : subWhereConditions) {
                    if (c.column != null && !c.column.isEmpty() && (c.operator.contains("NULL") || !c.value.isEmpty())) {
                        whereCount++;
                    }
                }
            }
            if (whereCount > 0) activeList.add(whereCount + " WHERE");
            if (subGroupByCol != null && !subGroupByCol.isEmpty() && !subGroupByCol.equals("(None)")) activeList.add("GROUP BY");
            if (subOrderByCol != null && !subOrderByCol.isEmpty() && !subOrderByCol.equals("(None)")) activeList.add("ORDER BY");
            if (subLimit != null && !subLimit.trim().isEmpty()) activeList.add("LIMIT " + subLimit.trim());

            if (!activeList.isEmpty()) {
                inSubClausesBtn.setText("⚙ Clauses (" + String.join(", ", activeList) + ")");
                inSubClausesBtn.setForeground(new Color(0, 140, 0));
            } else {
                inSubClausesBtn.setText("⚙ Clauses (WHERE, ORDER...)");
                inSubClausesBtn.setForeground(null);
            }

            String sql = buildSubquerySql(dbManager);
            if (!sql.isEmpty()) {
                inSubClausesBtn.setToolTipText("<html><b>Subquery SQL:</b><br><code>" + sql + "</code><br><i>Click to edit WHERE, ORDER BY, LIMIT clauses</i></html>");
            } else {
                inSubClausesBtn.setToolTipText("Click to configure WHERE, ORDER BY, LIMIT, and other clauses for this subquery");
            }
        }

        private String buildSubqueryWhereString(DatabaseManager dbManager) {
            if (subWhereConditions == null || subWhereConditions.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (SubqueryWhereCondition cond : subWhereConditions) {
                if (cond.column == null || cond.column.trim().isEmpty() || cond.operator == null) continue;
                boolean isNullOp = cond.operator.contains("NULL");
                if (!isNullOp && (cond.value == null || cond.value.trim().isEmpty())) continue;

                if (count > 0) {
                    sb.append(" ").append(cond.logic != null && !cond.logic.isEmpty() ? cond.logic : "AND").append(" ");
                }
                sb.append(dbManager.quoteColumnName(cond.column)).append(" ").append(cond.operator).append(" ");
                if (!isNullOp) {
                    String v = cond.value.trim();
                    if (cond.operator.equalsIgnoreCase("IN") || cond.operator.equalsIgnoreCase("NOT IN")) {
                        if (!v.startsWith("(") && !v.endsWith(")")) {
                            String[] parts = v.split(",");
                            List<String> formatted = new ArrayList<>();
                            for (String p : parts) {
                                formatted.add(formatSingleValue(p.trim()));
                            }
                            sb.append("(").append(String.join(", ", formatted)).append(")");
                        } else {
                            sb.append(v);
                        }
                    } else if (cond.operator.equalsIgnoreCase("LIKE") || cond.operator.equalsIgnoreCase("ILIKE")) {
                        if (!v.contains("%")) v = "%" + v + "%";
                        sb.append("'").append(v.replace("'", "''")).append("'");
                    } else if (cond.operator.equalsIgnoreCase("BETWEEN")) {
                        sb.append(v);
                    } else {
                        sb.append(formatSingleValue(v));
                    }
                }
                count++;
            }
            return sb.toString();
        }

        public String buildSubquerySql(DatabaseManager dbManager) {
            if (useCustomSubquery && customSubqueryText != null && !customSubqueryText.trim().isEmpty()) {
                String trimmed = customSubqueryText.trim();
                while (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
                }
                return trimmed;
            }

            String subT = (String) inSubTableDropdown.getSelectedItem();
            String subC = (String) inSubColDropdown.getSelectedItem();
            if (subT == null || subC == null || subT.trim().isEmpty() || subC.trim().isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("SELECT ");
            if (subDistinct) {
                sb.append("DISTINCT ");
            }
            sb.append(dbManager.quoteColumnName(subC)).append(" FROM ").append(dbManager.quoteTableName(subT));

            String whereStr = buildSubqueryWhereString(dbManager);
            if (!whereStr.isEmpty()) {
                sb.append(" WHERE ").append(whereStr);
            }

            if (subGroupByCol != null && !subGroupByCol.trim().isEmpty() && !subGroupByCol.equals("(None)")) {
                sb.append(" GROUP BY ").append(dbManager.quoteColumnName(subGroupByCol));
                if (subHaving != null && !subHaving.trim().isEmpty()) {
                    sb.append(" HAVING ").append(subHaving.trim());
                }
            }

            if (subOrderByCol != null && !subOrderByCol.trim().isEmpty() && !subOrderByCol.equals("(None)")) {
                sb.append(" ORDER BY ").append(dbManager.quoteColumnName(subOrderByCol)).append(" ").append(subOrderDir != null ? subOrderDir : "ASC");
            }

            if (subLimit != null && !subLimit.trim().isEmpty()) {
                sb.append(" LIMIT ").append(subLimit.trim());
                if (subOffset != null && !subOffset.trim().isEmpty()) {
                    sb.append(" OFFSET ").append(subOffset.trim());
                }
            }

            return sb.toString();
        }

        class SubqueryConditionRow extends JPanel {
            JComboBox<String> logicCombo = new JComboBox<>(new String[]{"AND", "OR"});
            SearchableComboBox colCombo = new SearchableComboBox();
            JComboBox<String> opCombo = new JComboBox<>(new String[]{"=", "!=", ">", "<", ">=", "<=", "LIKE", "ILIKE", "IN", "NOT IN", "BETWEEN", "IS NULL", "IS NOT NULL"});
            JTextField valField = new JTextField(12);
            JButton delBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove subquery condition");
            private final JComponent whereBadge = com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
                com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.WHERE
            );
            private final JPanel prefixSlot = new JPanel(new BorderLayout());

            public SubqueryConditionRow(boolean isFirst, List<String> cols, SubqueryWhereCondition initData, Runnable onUpdate, Runnable onRemove) {
                com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
                com.dbtool.ui.components.ClauseBadgeFactory.styleLogicDropdown(logicCombo);
                prefixSlot.setOpaque(false);
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    prefixSlot, com.dbtool.ui.components.ClauseBadgeFactory.PREFIX_WIDTH
                );
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    colCombo, com.dbtool.ui.components.ClauseBadgeFactory.COLUMN_WIDTH
                );
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    opCombo, com.dbtool.ui.components.ClauseBadgeFactory.OPERATOR_WIDTH
                );
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    valField, com.dbtool.ui.components.ClauseBadgeFactory.VALUE_WIDTH
                );
                valField.putClientProperty("JTextField.placeholderText", "Enter value...");

                colCombo.setAllItems(cols);

                setFirst(isFirst);
                add(prefixSlot);
                add(colCombo);
                add(opCombo);
                add(valField);
                add(delBtn);

                if (initData != null) {
                    if (initData.logic != null) logicCombo.setSelectedItem(initData.logic);
                    if (initData.column != null && cols.contains(initData.column)) colCombo.setSelectedItem(initData.column);
                    if (initData.operator != null) opCombo.setSelectedItem(initData.operator);
                    if (initData.value != null) valField.setText(initData.value);
                } else if (!cols.isEmpty()) {
                    colCombo.setSelectedIndex(0);
                }

                opCombo.addActionListener(e -> {
                    String op = (String) opCombo.getSelectedItem();
                    boolean isNull = op != null && op.contains("NULL");
                    valField.setEnabled(!isNull);
                    if (onUpdate != null) onUpdate.run();
                });

                colCombo.addActionListener(e -> { if (onUpdate != null) onUpdate.run(); });
                logicCombo.addActionListener(e -> { if (onUpdate != null) onUpdate.run(); });
                valField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { if (onUpdate != null) onUpdate.run(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { if (onUpdate != null) onUpdate.run(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { if (onUpdate != null) onUpdate.run(); }
                });

                delBtn.addActionListener(e -> {
                    if (onRemove != null) onRemove.run();
                });
            }

            public void updateCols(List<String> newCols) {
                Object cur = colCombo.getSelectedItem();
                colCombo.setAllItems(newCols);
                if (cur != null && newCols.contains(cur)) {
                    colCombo.setSelectedItem(cur);
                } else if (!newCols.isEmpty()) {
                    colCombo.setSelectedIndex(0);
                }
            }

            public void setFirst(boolean isFirst) {
                prefixSlot.removeAll();
                if (isFirst) {
                    prefixSlot.add(whereBadge, BorderLayout.CENTER);
                } else {
                    prefixSlot.add(logicCombo, BorderLayout.CENTER);
                }
                prefixSlot.revalidate();
                prefixSlot.repaint();
            }
        }

        private void openSubqueryClausesDialog() {
            JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Subquery Clauses Builder (IN Subquery)", true);
            dlg.setLayout(new BorderLayout(8, 8));

            JPanel contentP = new JPanel();
            contentP.setLayout(new BoxLayout(contentP, BoxLayout.Y_AXIS));
            contentP.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Section 1: Target SELECT ... FROM ...
            JPanel targetCard = new JPanel(new GridBagLayout());
            targetCard.setBorder(BorderFactory.createTitledBorder("1. Subquery Target (SELECT ... FROM ...)"));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
            targetCard.add(new JLabel("FROM Table:"), gbc);

            gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
            SearchableComboBox dlgTableCombo = new SearchableComboBox();
            List<String> allTbls = new ArrayList<>();
            for (int i = 0; i < inSubTableDropdown.getItemCount(); i++) {
                allTbls.add(inSubTableDropdown.getItemAt(i));
            }
            if (allTbls.isEmpty() && loadedTables != null) allTbls.addAll(loadedTables);
            dlgTableCombo.setAllItems(allTbls);
            Object curTbl = inSubTableDropdown.getSelectedItem();
            if (curTbl != null && allTbls.contains(curTbl)) {
                dlgTableCombo.setSelectedItem(curTbl);
            } else if (!allTbls.isEmpty()) {
                dlgTableCombo.setSelectedIndex(0);
            }
            targetCard.add(dlgTableCombo, gbc);

            gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.0;
            JButton refreshTblsBtn = new JButton("🔄 Refresh Tables");
            refreshTblsBtn.setToolTipText("Fetch fresh tables list directly from connected database");
            targetCard.add(refreshTblsBtn, gbc);

            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
            targetCard.add(new JLabel("SELECT Column:"), gbc);

            gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
            SearchableComboBox dlgColCombo = new SearchableComboBox();
            targetCard.add(dlgColCombo, gbc);

            gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0.0;
            JCheckBox dlgDistinctCb = new JCheckBox("DISTINCT");
            dlgDistinctCb.setSelected(subDistinct);
            targetCard.add(dlgDistinctCb, gbc);

            contentP.add(targetCard);
            contentP.add(Box.createVerticalStrut(6));

            // Section 2: WHERE filters
            JPanel whereCard = new JPanel(new BorderLayout(5, 5));
            whereCard.setBorder(BorderFactory.createTitledBorder("2. WHERE Clause (Filters for Subquery)"));

            JPanel whereListPanel = new JPanel();
            whereListPanel.setLayout(new BoxLayout(whereListPanel, BoxLayout.Y_AXIS));

            JScrollPane whereScroll = new JScrollPane(whereListPanel);
            whereScroll.setPreferredSize(new Dimension(680, 130));
            whereScroll.getVerticalScrollBar().setUnitIncrement(16);

            JPanel whereBtnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
            JButton addCondBtn = new JButton("➕ Add WHERE Condition");
            whereBtnP.add(addCondBtn);

            whereCard.add(whereScroll, BorderLayout.CENTER);
            whereCard.add(whereBtnP, BorderLayout.SOUTH);

            contentP.add(whereCard);
            contentP.add(Box.createVerticalStrut(6));

            // Section 3: GROUP BY & HAVING, ORDER BY & LIMIT
            JPanel clausesCard = new JPanel(new GridLayout(2, 1, 4, 4));
            clausesCard.setBorder(BorderFactory.createTitledBorder("3. GROUP BY, HAVING, ORDER BY & LIMIT"));

            JPanel rowA = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            rowA.add(new JLabel("GROUP BY:"));
            SearchableComboBox dlgGroupByCombo = new SearchableComboBox();
            dlgGroupByCombo.setPreferredSize(new Dimension(140, 26));
            rowA.add(dlgGroupByCombo);

            rowA.add(Box.createHorizontalStrut(10));
            rowA.add(new JLabel("HAVING:"));
            JTextField dlgHavingField = new JTextField(subHaving != null ? subHaving : "", 16);
            dlgHavingField.putClientProperty("JTextField.placeholderText", "e.g. COUNT(*) > 1");
            rowA.add(dlgHavingField);
            clausesCard.add(rowA);

            JPanel rowB = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            rowB.add(new JLabel("ORDER BY:"));
            SearchableComboBox dlgOrderByCombo = new SearchableComboBox();
            dlgOrderByCombo.setPreferredSize(new Dimension(140, 26));
            rowB.add(dlgOrderByCombo);

            JComboBox<String> dlgOrderDirCombo = new JComboBox<>(new String[]{"ASC", "DESC"});
            dlgOrderDirCombo.setSelectedItem(subOrderDir != null ? subOrderDir : "ASC");
            rowB.add(dlgOrderDirCombo);

            rowB.add(Box.createHorizontalStrut(10));
            rowB.add(new JLabel("LIMIT:"));
            JTextField dlgLimitField = new JTextField(subLimit != null ? subLimit : "", 5);
            rowB.add(dlgLimitField);

            rowB.add(new JLabel("OFFSET:"));
            JTextField dlgOffsetField = new JTextField(subOffset != null ? subOffset : "", 5);
            rowB.add(dlgOffsetField);
            clausesCard.add(rowB);

            contentP.add(clausesCard);
            contentP.add(Box.createVerticalStrut(6));

            // Section 4: Live SQL Preview / Custom Override
            JPanel previewCard = new JPanel(new BorderLayout(4, 4));
            previewCard.setBorder(BorderFactory.createTitledBorder("4. Live SQL Preview / Direct Override"));

            JTextArea sqlArea = new JTextArea(3, 50);
            sqlArea.setLineWrap(true);
            sqlArea.setWrapStyleWord(true);
            sqlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            sqlArea.setEditable(useCustomSubquery);
            if (useCustomSubquery && customSubqueryText != null && !customSubqueryText.trim().isEmpty()) {
                sqlArea.setText(customSubqueryText);
            }
            JScrollPane sqlScroll = new JScrollPane(sqlArea);

            JPanel overrideTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            JCheckBox customOverrideCb = new JCheckBox("Direct SQL Override (edit subquery manually)");
            customOverrideCb.setSelected(useCustomSubquery);
            JButton genFromGuiBtn = new JButton("Copy from GUI Clauses");
            genFromGuiBtn.setEnabled(useCustomSubquery);
            JButton insertSnippetBtn = new JButton("📝 Insert Template ▾");
            insertSnippetBtn.setToolTipText("Insert advanced subquery or query template (LATERAL, Recursive CTE, Window function, etc.)");
            insertSnippetBtn.addActionListener(e -> {
                JPopupMenu snippetMenu = new JPopupMenu();
                SqlSnippetsLibrary.populatePopupMenu(snippetMenu, sql -> {
                    customOverrideCb.setSelected(true);
                    sqlArea.setEditable(true);
                    genFromGuiBtn.setEnabled(true);
                    sqlArea.setText(sql);
                });
                snippetMenu.show(insertSnippetBtn, 0, insertSnippetBtn.getHeight());
            });
            overrideTop.add(customOverrideCb);
            overrideTop.add(genFromGuiBtn);
            overrideTop.add(insertSnippetBtn);

            previewCard.add(overrideTop, BorderLayout.NORTH);
            previewCard.add(sqlScroll, BorderLayout.CENTER);

            contentP.add(previewCard);

            List<SubqueryConditionRow> condRows = new ArrayList<>();

            Runnable updatePreview = () -> {
                if (customOverrideCb.isSelected()) {
                    return;
                }
                String t = (String) dlgTableCombo.getSelectedItem();
                String c = (String) dlgColCombo.getSelectedItem();
                if (t == null || c == null || t.trim().isEmpty() || c.trim().isEmpty()) {
                    sqlArea.setText("");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("SELECT ");
                if (dlgDistinctCb.isSelected()) sb.append("DISTINCT ");
                sb.append(dbManager.quoteColumnName(c)).append(" FROM ").append(dbManager.quoteTableName(t));

                StringBuilder wsb = new StringBuilder();
                int count = 0;
                for (SubqueryConditionRow r : condRows) {
                    String col = (String) r.colCombo.getSelectedItem();
                    String op = (String) r.opCombo.getSelectedItem();
                    String val = r.valField.getText().trim();
                    if (col == null || col.trim().isEmpty() || op == null) continue;
                    boolean isNull = op.contains("NULL");
                    if (!isNull && val.isEmpty()) continue;

                    if (count > 0) {
                        wsb.append(" ").append(r.logicCombo.getSelectedItem()).append(" ");
                    }
                    wsb.append(dbManager.quoteColumnName(col)).append(" ").append(op).append(" ");
                    if (!isNull) {
                        if (op.equalsIgnoreCase("IN") || op.equalsIgnoreCase("NOT IN")) {
                            if (!val.startsWith("(") && !val.endsWith(")")) {
                                String[] parts = val.split(",");
                                List<String> formatted = new ArrayList<>();
                                for (String p : parts) formatted.add(formatSingleValue(p.trim()));
                                wsb.append("(").append(String.join(", ", formatted)).append(")");
                            } else {
                                wsb.append(val);
                            }
                        } else if (op.equalsIgnoreCase("LIKE") || op.equalsIgnoreCase("ILIKE")) {
                            if (!val.contains("%")) val = "%" + val + "%";
                            wsb.append("'").append(val.replace("'", "''")).append("'");
                        } else if (op.equalsIgnoreCase("BETWEEN")) {
                            wsb.append(val);
                        } else {
                            wsb.append(formatSingleValue(val));
                        }
                    }
                    count++;
                }

                if (wsb.length() > 0) {
                    sb.append(" WHERE ").append(wsb);
                }

                String gb = (String) dlgGroupByCombo.getSelectedItem();
                if (gb != null && !gb.trim().isEmpty() && !gb.equals("(None)")) {
                    sb.append(" GROUP BY ").append(dbManager.quoteColumnName(gb));
                    String hav = dlgHavingField.getText().trim();
                    if (!hav.isEmpty()) sb.append(" HAVING ").append(hav);
                }

                String ob = (String) dlgOrderByCombo.getSelectedItem();
                if (ob != null && !ob.trim().isEmpty() && !ob.equals("(None)")) {
                    sb.append(" ORDER BY ").append(dbManager.quoteColumnName(ob)).append(" ").append(dlgOrderDirCombo.getSelectedItem());
                }

                String lim = dlgLimitField.getText().trim();
                if (!lim.isEmpty()) {
                    sb.append(" LIMIT ").append(lim);
                    String off = dlgOffsetField.getText().trim();
                    if (!off.isEmpty()) sb.append(" OFFSET ").append(off);
                }

                sqlArea.setText(sb.toString());
            };

            java.util.function.Consumer<SubqueryConditionRow> removeRow = row -> {
                condRows.remove(row);
                whereListPanel.remove(row);
                if (!condRows.isEmpty()) {
                    condRows.get(0).setFirst(true);
                }
                whereListPanel.revalidate();
                whereListPanel.repaint();
                updatePreview.run();
            };

            addCondBtn.addActionListener(e -> {
                List<String> cols = new ArrayList<>();
                for (int i = 0; i < dlgColCombo.getItemCount(); i++) {
                    cols.add(dlgColCombo.getItemAt(i));
                }
                SubqueryConditionRow r = new SubqueryConditionRow(condRows.isEmpty(), cols, null, updatePreview, null);
                r.delBtn.addActionListener(ev -> removeRow.accept(r));
                condRows.add(r);
                whereListPanel.add(r);
                whereListPanel.revalidate();
                whereListPanel.repaint();
                updatePreview.run();
            });

            // Populate initial columns and conditions
            List<String> initCols = new ArrayList<>();
            for (int i = 0; i < inSubColDropdown.getItemCount(); i++) {
                initCols.add(inSubColDropdown.getItemAt(i));
            }
            dlgColCombo.setAllItems(initCols);
            Object curCol = inSubColDropdown.getSelectedItem();
            if (curCol != null && initCols.contains(curCol)) {
                dlgColCombo.setSelectedItem(curCol);
            } else if (!initCols.isEmpty()) {
                dlgColCombo.setSelectedIndex(0);
            }

            List<String> initColsWithNone = new ArrayList<>();
            initColsWithNone.add("(None)");
            initColsWithNone.addAll(initCols);

            dlgGroupByCombo.setAllItems(initColsWithNone);
            if (subGroupByCol != null && initColsWithNone.contains(subGroupByCol)) dlgGroupByCombo.setSelectedItem(subGroupByCol);
            else dlgGroupByCombo.setSelectedItem("(None)");

            dlgOrderByCombo.setAllItems(initColsWithNone);
            if (subOrderByCol != null && initColsWithNone.contains(subOrderByCol)) dlgOrderByCombo.setSelectedItem(subOrderByCol);
            else dlgOrderByCombo.setSelectedItem("(None)");

            if (subWhereConditions != null && !subWhereConditions.isEmpty()) {
                for (int i = 0; i < subWhereConditions.size(); i++) {
                    SubqueryWhereCondition cond = subWhereConditions.get(i);
                    SubqueryConditionRow r = new SubqueryConditionRow(i == 0, initCols, cond, updatePreview, null);
                    r.delBtn.addActionListener(ev -> removeRow.accept(r));
                    condRows.add(r);
                    whereListPanel.add(r);
                }
            }

            dlgTableCombo.addActionListener(e -> {
                String selectedTbl = (String) dlgTableCombo.getSelectedItem();
                if (selectedTbl == null || selectedTbl.trim().isEmpty()) return;
                new Thread(() -> {
                    List<String> cols = dbManager.getColumnNames(selectedTbl);
                    SwingUtilities.invokeLater(() -> {
                        Object curC = dlgColCombo.getSelectedItem();
                        dlgColCombo.setAllItems(cols);
                        if (curC != null && cols.contains(curC)) {
                            dlgColCombo.setSelectedItem(curC);
                        } else if (!cols.isEmpty()) {
                            dlgColCombo.setSelectedIndex(0);
                        }

                        List<String> withNone = new ArrayList<>();
                        withNone.add("(None)");
                        withNone.addAll(cols);

                        Object curG = dlgGroupByCombo.getSelectedItem();
                        dlgGroupByCombo.setAllItems(withNone);
                        if (curG != null && withNone.contains(curG)) dlgGroupByCombo.setSelectedItem(curG);
                        else dlgGroupByCombo.setSelectedItem("(None)");

                        Object curO = dlgOrderByCombo.getSelectedItem();
                        dlgOrderByCombo.setAllItems(withNone);
                        if (curO != null && withNone.contains(curO)) dlgOrderByCombo.setSelectedItem(curO);
                        else dlgOrderByCombo.setSelectedItem("(None)");

                        for (SubqueryConditionRow r : condRows) {
                            r.updateCols(cols);
                        }
                        updatePreview.run();
                    });
                }).start();
            });

            refreshTblsBtn.addActionListener(e -> {
                refreshTblsBtn.setEnabled(false);
                refreshTblsBtn.setText("Fetching...");
                new Thread(() -> {
                    List<String> freshTbls = dbManager.getTableNames();
                    SwingUtilities.invokeLater(() -> {
                        refreshTblsBtn.setEnabled(true);
                        refreshTblsBtn.setText("🔄 Refresh Tables");
                        if (freshTbls != null && !freshTbls.isEmpty()) {
                            Object sel = dlgTableCombo.getSelectedItem();
                            dlgTableCombo.setAllItems(freshTbls);
                            inSubTableDropdown.setAllItems(freshTbls);
                            if (sel != null && freshTbls.contains(sel)) {
                                dlgTableCombo.setSelectedItem(sel);
                            } else {
                                dlgTableCombo.setSelectedIndex(0);
                            }
                        }
                    });
                }).start();
            });

            dlgColCombo.addActionListener(e -> updatePreview.run());
            dlgDistinctCb.addActionListener(e -> updatePreview.run());
            dlgGroupByCombo.addActionListener(e -> updatePreview.run());
            dlgOrderByCombo.addActionListener(e -> updatePreview.run());
            dlgOrderDirCombo.addActionListener(e -> updatePreview.run());

            javax.swing.event.DocumentListener docL = new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
            };
            dlgHavingField.getDocument().addDocumentListener(docL);
            dlgLimitField.getDocument().addDocumentListener(docL);
            dlgOffsetField.getDocument().addDocumentListener(docL);

            customOverrideCb.addActionListener(e -> {
                boolean active = customOverrideCb.isSelected();
                genFromGuiBtn.setEnabled(active);
                sqlArea.setEditable(active);
                if (!active) {
                    updatePreview.run();
                }
            });

            genFromGuiBtn.addActionListener(e -> {
                customOverrideCb.setSelected(false);
                updatePreview.run();
                customOverrideCb.setSelected(true);
                genFromGuiBtn.setEnabled(true);
                sqlArea.setEditable(true);
            });

            JPanel bottomBtnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

            JButton clearBtn = new JButton("Clear All Clauses");
            clearBtn.addActionListener(e -> {
                dlgDistinctCb.setSelected(false);
                condRows.clear();
                whereListPanel.removeAll();
                whereListPanel.revalidate();
                whereListPanel.repaint();
                dlgGroupByCombo.setSelectedItem("(None)");
                dlgHavingField.setText("");
                dlgOrderByCombo.setSelectedItem("(None)");
                dlgOrderDirCombo.setSelectedItem("ASC");
                dlgLimitField.setText("");
                dlgOffsetField.setText("");
                customOverrideCb.setSelected(false);
                genFromGuiBtn.setEnabled(false);
                sqlArea.setEditable(false);
                updatePreview.run();
            });

            JButton applyBtn = new JButton("Apply");
            applyBtn.setPreferredSize(new Dimension(85, 28));
            applyBtn.addActionListener(e -> {
                Object selT = dlgTableCombo.getSelectedItem();
                if (selT != null) inSubTableDropdown.setSelectedItem(selT);
                Object selC = dlgColCombo.getSelectedItem();
                if (selC != null) inSubColDropdown.setSelectedItem(selC);

                subDistinct = dlgDistinctCb.isSelected();

                subWhereConditions.clear();
                for (SubqueryConditionRow r : condRows) {
                    String col = (String) r.colCombo.getSelectedItem();
                    String op = (String) r.opCombo.getSelectedItem();
                    String v = r.valField.getText().trim();
                    String log = (String) r.logicCombo.getSelectedItem();
                    if (col != null && !col.trim().isEmpty() && op != null) {
                        subWhereConditions.add(new SubqueryWhereCondition(log, col, op, v));
                    }
                }

                subGroupByCol = (String) dlgGroupByCombo.getSelectedItem();
                subHaving = dlgHavingField.getText().trim();
                subOrderByCol = (String) dlgOrderByCombo.getSelectedItem();
                subOrderDir = (String) dlgOrderDirCombo.getSelectedItem();
                subLimit = dlgLimitField.getText().trim();
                subOffset = dlgOffsetField.getText().trim();
                useCustomSubquery = customOverrideCb.isSelected();
                customSubqueryText = sqlArea.getText().trim();

                updateSubClausesBtnDisplay();
                dlg.dispose();
            });

            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.addActionListener(e -> dlg.dispose());

            bottomBtnP.add(clearBtn);
            bottomBtnP.add(applyBtn);
            bottomBtnP.add(cancelBtn);

            dlg.add(contentP, BorderLayout.CENTER);
            dlg.add(bottomBtnP, BorderLayout.SOUTH);

            updatePreview.run();

            dlg.pack();
            dlg.setMinimumSize(new Dimension(720, 580));
            dlg.setSize(750, 640);
            dlg.setLocationRelativeTo(this);
            dlg.setVisible(true);
        }

        private void loadDistinctValues() {
            String t = (String) tableDropdown.getSelectedItem();
            String c = (String) columnDropdown.getSelectedItem();
            if (t == null || c == null || dbManager.connection == null) return;

            new Thread(() -> {
                List<String> vals = new ArrayList<>();
                try {
                    String q = "SELECT DISTINCT " + dbManager.quoteColumnName(c) + " FROM " + dbManager.quoteTableName(t) + " WHERE " + dbManager.quoteColumnName(c) + " IS NOT NULL ORDER BY 1 LIMIT 300";
                    try (java.sql.Statement s = dbManager.connection.createStatement();
                         java.sql.ResultSet rs = s.executeQuery(q)) {
                        while (rs.next()) {
                            String v = rs.getString(1);
                            if (v != null) vals.add(v);
                        }
                    }
                } catch (Exception ex) {
                    try {
                        String q = "SELECT DISTINCT " + dbManager.quoteColumnName(c) + " FROM " + dbManager.quoteTableName(t) + " WHERE " + dbManager.quoteColumnName(c) + " IS NOT NULL LIMIT 300";
                        try (java.sql.Statement s = dbManager.connection.createStatement();
                             java.sql.ResultSet rs = s.executeQuery(q)) {
                            while (rs.next()) {
                                String v = rs.getString(1);
                                if (v != null) vals.add(v);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                SwingUtilities.invokeLater(() -> {
                    cachedDistinctValues = vals;
                    inValuesDropdown.setAllItems(vals);
                });
            }).start();
        }

        private void openMultiSelectDialog() {
            if (cachedDistinctValues.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No distinct values loaded yet for column " + columnDropdown.getSelectedItem(), "Notice", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Select Values for IN (" + columnDropdown.getSelectedItem() + ")", true);
            dlg.setLayout(new BorderLayout(8, 8));

            JPanel topP = new JPanel(new BorderLayout(5, 5));
            topP.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
            JTextField searchField = new JTextField();
            searchField.putClientProperty("JTextField.placeholderText", "Search values...");
            topP.add(new JLabel("Search: "), BorderLayout.WEST);
            topP.add(searchField, BorderLayout.CENTER);

            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

            Set<String> currentlySelected = new HashSet<>();
            String currentText = valueField.getText().trim();
            if (!currentText.isEmpty()) {
                String[] parts = currentText.split(",");
                for (String p : parts) {
                    currentlySelected.add(p.trim().replaceAll("^['\"]|['\"]$", ""));
                }
            }

            List<JCheckBox> checkBoxes = new ArrayList<>();
            for (String val : cachedDistinctValues) {
                JCheckBox cb = new JCheckBox(val);
                if (currentlySelected.contains(val)) cb.setSelected(true);
                checkBoxes.add(cb);
                listPanel.add(cb);
            }

            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void filter() {
                    String q = searchField.getText().toLowerCase().trim();
                    listPanel.removeAll();
                    for (JCheckBox cb : checkBoxes) {
                        if (q.isEmpty() || cb.getText().toLowerCase().contains(q)) {
                            listPanel.add(cb);
                        }
                    }
                    listPanel.revalidate();
                    listPanel.repaint();
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            });

            JScrollPane scroll = new JScrollPane(listPanel);
            scroll.setBorder(BorderFactory.createTitledBorder("Distinct Values (" + cachedDistinctValues.size() + ")"));

            JPanel selectBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton selectAll = new JButton("Select All");
            JButton unselectAll = new JButton("Clear All");
            selectAll.addActionListener(e -> { for (JCheckBox cb : checkBoxes) cb.setSelected(true); });
            unselectAll.addActionListener(e -> { for (JCheckBox cb : checkBoxes) cb.setSelected(false); });
            selectBtns.add(selectAll);
            selectBtns.add(unselectAll);

            JPanel centerContainer = new JPanel(new BorderLayout());
            centerContainer.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            centerContainer.add(selectBtns, BorderLayout.NORTH);
            centerContainer.add(scroll, BorderLayout.CENTER);

            JPanel bottomP = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = new JButton("OK");
            JButton cancelBtn = new JButton("Cancel");
            okBtn.addActionListener(e -> {
                List<String> chosen = new ArrayList<>();
                for (JCheckBox cb : checkBoxes) {
                    if (cb.isSelected()) {
                        chosen.add(formatSingleValue(cb.getText()));
                    }
                }
                valueField.setText(String.join(", ", chosen));
                dlg.dispose();
            });
            cancelBtn.addActionListener(e -> dlg.dispose());
            bottomP.add(okBtn);
            bottomP.add(cancelBtn);

            dlg.add(topP, BorderLayout.NORTH);
            dlg.add(centerContainer, BorderLayout.CENTER);
            dlg.add(bottomP, BorderLayout.SOUTH);

            dlg.setSize(380, 480);
            dlg.setLocationRelativeTo(this);
            dlg.setVisible(true);
        }

        private String formatSingleValue(String val) {
            if (val == null) return "NULL";
            val = val.trim();
            if ((val.startsWith("'") && val.endsWith("'")) || (val.startsWith("\"") && val.endsWith("\""))) {
                return val;
            }
            try {
                Double.parseDouble(val);
                return val;
            } catch (NumberFormatException e) {
                return "'" + val.replace("'", "''") + "'";
            }
        }

        public String buildConditionClause(DatabaseManager dbManager) {
            String tbl = (String) tableDropdown.getSelectedItem();
            String col = (String) columnDropdown.getSelectedItem();
            String op = (String) operatorDropdown.getSelectedItem();
            if (tbl == null || col == null || op == null) return "";

            StringBuilder sb = new StringBuilder();
            sb.append(dbManager.quoteColumnName(tbl + "." + col)).append(" ").append(op).append(" ");

            if (op.contains("NULL")) {
                return sb.toString();
            }

            if (op.equalsIgnoreCase("IN") || op.equalsIgnoreCase("NOT IN")) {
                String inType = (String) inTypeDropdown.getSelectedItem();
                if ("Subquery (SELECT)".equals(inType)) {
                    String subSql = buildSubquerySql(dbManager);
                    if (!subSql.isEmpty()) {
                        sb.append("(").append(subSql).append(") ");
                        return sb.toString();
                    }
                    return "";
                }
                String val = valueField.getText().trim();
                if (val.isEmpty()) {
                    Object sel = inValuesDropdown.getSelectedItem();
                    if (sel != null && !sel.toString().trim().isEmpty()) {
                        val = formatSingleValue(sel.toString().trim());
                    }
                }
                if (val.isEmpty()) return "";

                if (!val.startsWith("(") && !val.endsWith(")")) {
                    sb.append("(").append(val).append(") ");
                } else {
                    sb.append(val).append(" ");
                }
                return sb.toString();
            }

            if (op.equalsIgnoreCase("BETWEEN")) {
                String val = valueField.getText().trim();
                sb.append(val).append(" ");
                return sb.toString();
            }

            String val = valueField.getText().trim();
            if ((op.equalsIgnoreCase("LIKE") || op.equalsIgnoreCase("ILIKE")) && !val.contains("%")) {
                val = "%" + val + "%";
            }
            sb.append("'").append(val.replace("'", "''")).append("' ");
            return sb.toString();
        }

        public void updateTables(List<String> tables) {
            Object selected = tableDropdown.getSelectedItem();
            tableDropdown.setAllItems(tables);
            if (selected != null && tables.contains(selected)) tableDropdown.setSelectedItem(selected);
            List<String> allTables = (loadedTables != null && !loadedTables.isEmpty()) ? loadedTables : tables;
            inSubTableDropdown.setAllItems(allTables);
            fetchTablesFromDb();
        }

        public VisualJoinState.WhereState captureWhereState() {
            VisualJoinState.WhereState ws = new VisualJoinState.WhereState();
            ws.logic = (String) logicDropdown.getSelectedItem();
            ws.table = (String) tableDropdown.getSelectedItem();
            ws.column = (String) columnDropdown.getSelectedItem();
            ws.operator = (String) operatorDropdown.getSelectedItem();
            ws.value = valueField.getText();

            ws.inType = (String) inTypeDropdown.getSelectedItem();
            ws.subTable = (String) inSubTableDropdown.getSelectedItem();
            ws.subCol = (String) inSubColDropdown.getSelectedItem();
            ws.subDistinct = subDistinct;
            ws.subGroupByCol = subGroupByCol;
            ws.subHaving = subHaving;
            ws.subOrderByCol = subOrderByCol;
            ws.subOrderDir = subOrderDir != null ? subOrderDir : "ASC";
            ws.subLimit = subLimit;
            ws.subOffset = subOffset;
            ws.useCustomSubquery = useCustomSubquery;
            ws.customSubqueryText = customSubqueryText;

            if (subWhereConditions != null) {
                for (SubqueryWhereCondition c : subWhereConditions) {
                    VisualJoinState.SubqueryConditionState cs = new VisualJoinState.SubqueryConditionState();
                    cs.logic = c.logic;
                    cs.column = c.column;
                    cs.operator = c.operator;
                    cs.value = c.value;
                    ws.subConditions.add(cs);
                }
            }
            return ws;
        }

        public void restoreWhereState(VisualJoinState.WhereState ws) {
            if (ws == null) return;
            if (ws.logic != null) logicDropdown.setSelectedItem(ws.logic);
            if (ws.table != null) tableDropdown.setSelectedItem(ws.table);
            if (ws.column != null) columnDropdown.setSelectedItem(ws.column);
            if (ws.operator != null) operatorDropdown.setSelectedItem(ws.operator);
            if (ws.value != null) valueField.setText(ws.value);

            if (ws.inType != null) inTypeDropdown.setSelectedItem(ws.inType);
            if (ws.subTable != null) inSubTableDropdown.setSelectedItem(ws.subTable);
            if (ws.subCol != null) inSubColDropdown.setSelectedItem(ws.subCol);
            this.subDistinct = ws.subDistinct;
            this.subGroupByCol = ws.subGroupByCol;
            this.subHaving = ws.subHaving;
            this.subOrderByCol = ws.subOrderByCol;
            this.subOrderDir = ws.subOrderDir != null ? ws.subOrderDir : "ASC";
            this.subLimit = ws.subLimit;
            this.subOffset = ws.subOffset;
            this.useCustomSubquery = ws.useCustomSubquery;
            this.customSubqueryText = ws.customSubqueryText;

            this.subWhereConditions.clear();
            if (ws.subConditions != null) {
                for (VisualJoinState.SubqueryConditionState cs : ws.subConditions) {
                    this.subWhereConditions.add(new SubqueryWhereCondition(cs.logic, cs.column, cs.operator, cs.value));
                }
            }
            updateSubClausesBtnDisplay();
        }
    }

    private static void setLinuxAppId() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win") && !os.contains("mac")) {
            // Prevent X11/XWayland offscreen pixmap stalls on Ubuntu (GNOME/Mutter)
            if (System.getProperty("sun.java2d.pmoffscreen") == null) {
                System.setProperty("sun.java2d.pmoffscreen", "false");
            }
            try {
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                Class<?> toolkitClass = toolkit.getClass();
                if ("sun.awt.X11.XToolkit".equals(toolkitClass.getName())) {
                    java.lang.reflect.Field field = toolkitClass.getDeclaredField("awtAppClassName");
                    field.setAccessible(true);
                    field.set(null, "schemasync");
                }
            } catch (Throwable ignored) {
            }
            try {
                com.dbtool.util.UpdateChecker.ensureLinuxDesktopIntegration();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void setWindowsAppUserModelID() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                com.sun.jna.platform.win32.Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new com.sun.jna.WString("SchemaSync.DatabaseTool"));
            } catch (Throwable ignored) {
            }
        }
    }

    public static void main(String[] args) {
        setLinuxAppId();
        setWindowsAppUserModelID();
        com.dbtool.util.ThemeManager.init(null);
        SwingUtilities.invokeLater(() -> {
            com.dbtool.ui.SplashScreen splash = new com.dbtool.ui.SplashScreen(() -> {
                new Main().setVisible(true);
            });
            splash.start();
        });
    }

    public static class SearchableComboBox extends com.dbtool.ui.components.SearchableComboBox<String> {
        public SearchableComboBox() {
            super();
        }
    }
}

