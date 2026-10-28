package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.core.diff.*;
import com.dbtool.core.inspector.JdbcSchemaInspector;
import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;
import com.dbtool.ui.components.DiffTableCellRenderer;
import com.dbtool.ui.components.SearchableComboBox;
import com.dbtool.util.ExportUtil;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.sql.Statement;
import java.util.*;
import java.util.List;

/**
 * Advanced Multi-Database & Multi-Table Compare Panel.
 * Supports connecting 2, 3, or more databases simultaneously,
 * multi-tab table comparisons, full SQL clauses (WHERE, JOIN, ORDER BY, DISTINCT),
 * and live schema and row diffing.
 */
public class DualDatabaseComparePanel extends JPanel {

    private final DatabaseManager primaryDbManager;

    // Database Pool (A, B, C, D...)
    private final List<DatabaseEntry> databaseEntries = new ArrayList<>();
    private final JPanel dbCardsContainer = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 8, 4));
    private final JScrollPane dbCardsScrollPane = new JScrollPane(dbCardsContainer);

    // Sub-Tabs
    private final JTabbedPane subTabbedPane = new JTabbedPane();

    // ==========================================
    // 1. Schema Diff Controls
    // ==========================================
    private final JButton compareSchemasBtn = new JButton("Compare Schemas");
    private final JComboBox<DatabaseEntry> schemaSourceDbCombo = new JComboBox<>();
    private final JComboBox<Object> schemaTargetDbCombo = new JComboBox<>();
    private final JComboBox<String> schemaScopeCombo = new JComboBox<>(new String[]{"All Tables", "Single Table"});
    private final SearchableComboBox<String> schemaSingleTableCombo = new SearchableComboBox<>();
    private final JLabel schemaStatusLabel = new JLabel("Connect databases and click 'Compare Schemas'.");
    private final JTable schemaTablesTable = new JTable();
    private final JTable columnDiffTable = new JTable();
    private final JLabel selectedTableTitleLabel = new JLabel("Select a table to inspect column differences");
    private final RSyntaxTextArea tableSyncDdlArea = new RSyntaxTextArea(4, 30);
    private final JButton generateAllSyncBtn = new JButton("Generate Migration DDL Script");
    private final JTextField schemaSearchField = new JTextField(12);

    private SchemaDiffResult lastSchemaDiffResult;
    private DefaultTableModel schemaTablesModel;
    private TableRowSorter<DefaultTableModel> schemaTablesSorter;

    // Filter Buttons for Schema
    private final JToggleButton btnAllSchema = new JToggleButton("All");
    private final JToggleButton btnDiffOnlySchema = new JToggleButton("Differences");
    private final JToggleButton btnOnlyASchema = new JToggleButton("In Source Only");
    private final JToggleButton btnOnlyBSchema = new JToggleButton("In Target Only");
    private final JToggleButton btnModSchema = new JToggleButton("Modified");
    private final JToggleButton btnIdentSchema = new JToggleButton("Identical");

    // ==========================================
    // 2. Data Diff Multi-Tab Controls
    // ==========================================
    private final JTabbedPane dataCompareTabbedPane = new JTabbedPane();
    private final List<DataCompareTabPanel> dataCompareTabs = new ArrayList<>();
    private int dataTabCounter = 1;

    // Top Header & Toolbar Buttons
    private final JButton btnAddDatabase = new JButton("+ Add Database (DB C...)");
    private final JButton btnNewTab = new JButton("+ New Table Compare Tab");
    private final JButton btnDuplicateTab = new JButton("Duplicate Current Tab");
    private final JLabel tipLabel = new JLabel("💡 Compare Modes: Quick Table Match, Unified SQL (one query affects all DBs), and Custom SQL per database.");

    public DualDatabaseComparePanel(DatabaseManager primaryDbManager) {
        this.primaryDbManager = primaryDbManager;
        setLayout(new BorderLayout());

        // Initialize default Database A and Database B
        DatabaseEntry dbA = new DatabaseEntry("A", "Database A (Source)", new DatabaseManager(), false);
        DatabaseEntry dbB = new DatabaseEntry("B", "Database B (Target)", new DatabaseManager(), false);
        databaseEntries.add(dbA);
        databaseEntries.add(dbB);

        // 1. Build Top Connection Headers
        add(buildConnectionHeaderPanel(), BorderLayout.NORTH);

        // 2. Build Sub-Tabs (Schema Diff & Data Diff)
        subTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        subTabbedPane.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");
        subTabbedPane.addTab("🏗️ Schema Diff (Structure & Metadata)", buildSchemaDiffPanel());
        subTabbedPane.addTab("📊 Data Diff (Table & Query Rows)", buildDataDiffContainerPanel());
        add(subTabbedPane, BorderLayout.CENTER);

        // Populate initial tabs and sync dropdowns
        addDataCompareTab("Compare Session 1");
        refreshDatabaseSelectors();

        // Listen for Theme Changes and apply accessible colors
        ThemeManager.addThemeChangeListener(isDark -> updateThemeColors());
        updateThemeColors();
    }

    public DatabaseManager getDbManagerA() {
        return databaseEntries.get(0).getDbManager();
    }

    public DatabaseManager getDbManagerB() {
        return databaseEntries.get(1).getDbManager();
    }

    public List<DatabaseEntry> getDatabaseEntries() {
        return Collections.unmodifiableList(databaseEntries);
    }

    // ==========================================
    // Top Connection Header Panel (Supports N DBs)
    // ==========================================
    private JPanel buildConnectionHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(4, 2));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        dbCardsContainer.setOpaque(false);
        dbCardsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        dbCardsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        dbCardsScrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        rebuildDatabaseCardsUI();

        JPanel rightActions = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.RIGHT, 4, 4));
        btnAddDatabase.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAddDatabase.setForeground(ThemeManager.getPrimaryActionColor());
        btnAddDatabase.setToolTipText("Add a 3rd or additional database to compare (Database C, D, etc.)");
        btnAddDatabase.addActionListener(e -> addDatabaseEntry());
        rightActions.add(btnAddDatabase);

        headerPanel.add(dbCardsScrollPane, BorderLayout.CENTER);
        headerPanel.add(rightActions, BorderLayout.EAST);
        return headerPanel;
    }

    public void updateThemeColors() {
        btnAddDatabase.setForeground(ThemeManager.getPrimaryActionColor());
        compareSchemasBtn.setForeground(ThemeManager.getSuccessColor());
        btnNewTab.setForeground(ThemeManager.getSuccessColor());
        tipLabel.setForeground(ThemeManager.getMutedTextColor());
        for (DatabaseEntry entry : databaseEntries) {
            entry.updateStatusLabel();
        }
        for (DataCompareTabPanel tab : dataCompareTabs) {
            tab.updateThemeColors();
        }
        repaint();
    }

    public DatabaseEntry addDatabaseEntry() {
        char nextLetter = (char) ('A' + databaseEntries.size());
        String id = String.valueOf(nextLetter);
        String label = "Database " + id;
        DatabaseEntry entry = new DatabaseEntry(id, label, new DatabaseManager(), true);
        databaseEntries.add(entry);

        rebuildDatabaseCardsUI();
        refreshDatabaseSelectors();
        entry.updateStatusLabel();
        return entry;
    }

    public void removeDatabaseEntry(DatabaseEntry entry) {
        if (!entry.isRemovable()) return;
        entry.disconnect();
        databaseEntries.remove(entry);
        rebuildDatabaseCardsUI();
        refreshDatabaseSelectors();
    }

    private void rebuildDatabaseCardsUI() {
        dbCardsContainer.removeAll();
        for (DatabaseEntry entry : databaseEntries) {
            dbCardsContainer.add(entry.getCardPanel());
        }
        dbCardsContainer.revalidate();
        dbCardsContainer.repaint();
    }

    public void refreshDatabaseSelectors() {
        // 1. Refresh Schema Diff DB combos
        DatabaseEntry selSource = (DatabaseEntry) schemaSourceDbCombo.getSelectedItem();
        Object selTarget = schemaTargetDbCombo.getSelectedItem();

        schemaSourceDbCombo.removeAllItems();
        for (DatabaseEntry d : databaseEntries) {
            schemaSourceDbCombo.addItem(d);
        }
        if (selSource != null && databaseEntries.contains(selSource)) {
            schemaSourceDbCombo.setSelectedItem(selSource);
        } else if (schemaSourceDbCombo.getItemCount() > 0) {
            schemaSourceDbCombo.setSelectedIndex(0);
        }

        updateSchemaTargetDbCombo(selTarget);

        // 2. Refresh each Data Compare Tab's DB combos
        for (DataCompareTabPanel tab : dataCompareTabs) {
            tab.refreshDatabaseSelectors();
        }

        refreshSchemaTableDropdowns();
    }

    private void updateSchemaTargetDbCombo(Object prevSel) {
        schemaTargetDbCombo.removeAllItems();
        DatabaseEntry currentSource = (DatabaseEntry) schemaSourceDbCombo.getSelectedItem();

        for (DatabaseEntry d : databaseEntries) {
            if (d != currentSource) {
                schemaTargetDbCombo.addItem(d);
            }
        }
        if (databaseEntries.size() >= 3) {
            schemaTargetDbCombo.addItem("🌐 All Connected Databases (Matrix)");
        }

        if (prevSel != null) {
            for (int i = 0; i < schemaTargetDbCombo.getItemCount(); i++) {
                if (Objects.equals(schemaTargetDbCombo.getItemAt(i), prevSel)) {
                    schemaTargetDbCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (schemaTargetDbCombo.getItemCount() > 0) {
            schemaTargetDbCombo.setSelectedIndex(0);
        }
    }

    private void refreshSchemaTableDropdowns() {
        DatabaseEntry src = (DatabaseEntry) schemaSourceDbCombo.getSelectedItem();
        Object tgt = schemaTargetDbCombo.getSelectedItem();

        Set<String> allTbls = new LinkedHashSet<>();
        if (src != null && src.getDbManager().isConnected()) {
            allTbls.addAll(src.getDbManager().getTableNames());
        }
        if (tgt instanceof DatabaseEntry) {
            DatabaseEntry td = (DatabaseEntry) tgt;
            if (td.getDbManager().isConnected()) allTbls.addAll(td.getDbManager().getTableNames());
        } else if ("🌐 All Connected Databases (Matrix)".equals(tgt)) {
            for (DatabaseEntry d : databaseEntries) {
                if (d.getDbManager().isConnected()) allTbls.addAll(d.getDbManager().getTableNames());
            }
        }
        schemaSingleTableCombo.setAllItems(allTbls);
    }

    // ==========================================
    // 1. Schema Diff Panel Construction
    // ==========================================
    private JPanel buildSchemaDiffPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Top Toolbar
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setLayout(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));

        compareSchemasBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        compareSchemasBtn.setForeground(ThemeManager.getSuccessColor());
        compareSchemasBtn.addActionListener(e -> runSchemaComparison());
        tb.add(compareSchemasBtn);

        tb.add(new JLabel(" Source: "));
        schemaSourceDbCombo.setPreferredSize(new Dimension(150, 26));
        schemaSourceDbCombo.setRenderer(new DatabaseEntryRenderer());
        schemaSourceDbCombo.addActionListener(e -> {
            updateSchemaTargetDbCombo(schemaTargetDbCombo.getSelectedItem());
            refreshSchemaTableDropdowns();
        });
        tb.add(schemaSourceDbCombo);

        tb.add(new JLabel(" ➔ Target: "));
        schemaTargetDbCombo.setPreferredSize(new Dimension(190, 26));
        schemaTargetDbCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DatabaseEntry) {
                    DatabaseEntry d = (DatabaseEntry) value;
                    lbl.setText(d.getLabel() + (d.getDbManager().isConnected() ? " [🟢]" : " [⚪]"));
                } else if (value != null) {
                    lbl.setText(value.toString());
                }
                return lbl;
            }
        });
        schemaTargetDbCombo.addActionListener(e -> refreshSchemaTableDropdowns());
        tb.add(schemaTargetDbCombo);

        tb.add(new JLabel(" | Scope: "));
        schemaScopeCombo.setPreferredSize(new Dimension(105, 26));
        schemaSingleTableCombo.setPreferredSize(new Dimension(160, 26));
        schemaSingleTableCombo.setVisible(false);
        schemaScopeCombo.addActionListener(e -> {
            boolean isSingle = "Single Table".equals(schemaScopeCombo.getSelectedItem());
            schemaSingleTableCombo.setVisible(isSingle);
            tb.revalidate();
            tb.repaint();
        });
        tb.add(schemaScopeCombo);
        tb.add(schemaSingleTableCombo);
        tb.addSeparator();

        // Schema Filters
        ButtonGroup bg = new ButtonGroup();
        bg.add(btnAllSchema); bg.add(btnDiffOnlySchema); bg.add(btnOnlyASchema); bg.add(btnOnlyBSchema); bg.add(btnModSchema); bg.add(btnIdentSchema);
        btnAllSchema.setSelected(true);

        java.awt.event.ActionListener filterL = e -> applySchemaFilter();
        btnAllSchema.addActionListener(filterL);
        btnDiffOnlySchema.addActionListener(filterL);
        btnOnlyASchema.addActionListener(filterL);
        btnOnlyBSchema.addActionListener(filterL);
        btnModSchema.addActionListener(filterL);
        btnIdentSchema.addActionListener(filterL);

        tb.add(btnAllSchema);
        tb.add(btnDiffOnlySchema);
        tb.add(btnOnlyASchema);
        tb.add(btnOnlyBSchema);
        tb.add(btnModSchema);
        tb.add(btnIdentSchema);
        tb.addSeparator();

        tb.add(new JLabel("🔍 Filter: "));
        schemaSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applySchemaFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applySchemaFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applySchemaFilter(); }
        });
        tb.add(schemaSearchField);

        tb.add(Box.createHorizontalGlue());
        JButton templatesBtn = new JButton("📝 SQL Templates ▾");
        templatesBtn.setToolTipText("Browse advanced SQL snippets & templates (Advisory locks, Index schemas, DDL, DCL...)");
        templatesBtn.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            com.dbtool.util.SqlSnippetsLibrary.populatePopupMenu(menu, sql -> {
                tableSyncDdlArea.setText(sql);
            });
            menu.show(templatesBtn, 0, templatesBtn.getHeight());
        });

        JButton cheatSheetBtn = new JButton("💡 Cheat Sheet");
        cheatSheetBtn.setToolTipText("Open interactive Advanced SQL Mastery Cheat Sheet");
        cheatSheetBtn.addActionListener(e -> {
            new com.dbtool.panels.SqlMasteryReferenceDialog(SwingUtilities.getWindowAncestor(this), sql -> {
                tableSyncDdlArea.setText(sql);
            }).setVisible(true);
        });

        tb.add(templatesBtn);
        tb.add(cheatSheetBtn);
        tb.addSeparator();

        generateAllSyncBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        generateAllSyncBtn.addActionListener(e -> openMigrationDdlDialog());
        generateAllSyncBtn.setEnabled(false);
        tb.add(generateAllSyncBtn);

        panel.add(tb, BorderLayout.NORTH);

        // Center SplitPane (Tables Table + Column Level Detail Inspector)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.45);

        // Top Tables List
        JPanel tablesPanel = new JPanel(new BorderLayout());
        tablesPanel.setBorder(BorderFactory.createTitledBorder("Tables Comparison Overview"));
        schemaTablesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        schemaTablesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                inspectSelectedTableColumns();
            }
        });
        schemaTablesTable.setDefaultRenderer(Object.class, new DiffTableCellRenderer());
        JScrollPane scrollTables = new JScrollPane(schemaTablesTable);
        com.dbtool.util.TableRowUtilities.addRowNumbers(schemaTablesTable, scrollTables);
        tablesPanel.add(scrollTables, BorderLayout.CENTER);
        splitPane.setTopComponent(tablesPanel);

        // Bottom Column Detail Inspector
        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBorder(BorderFactory.createTitledBorder("Column-by-Column Inspector & Synchronization DDL"));

        JPanel detailHeader = new JPanel(new BorderLayout());
        selectedTableTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        selectedTableTitleLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        detailHeader.add(selectedTableTitleLabel, BorderLayout.CENTER);
        detailPanel.add(detailHeader, BorderLayout.NORTH);

        columnDiffTable.setDefaultRenderer(Object.class, new DiffTableCellRenderer());
        JScrollPane scrollCols = new JScrollPane(columnDiffTable);
        com.dbtool.util.TableRowUtilities.addRowNumbers(columnDiffTable, scrollCols);

        // Single Table Sync DDL
        tableSyncDdlArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        tableSyncDdlArea.setEditable(false);
        RTextScrollPane ddlScroll = new RTextScrollPane(tableSyncDdlArea);
        ddlScroll.setPreferredSize(new Dimension(300, 150));
        ddlScroll.setBorder(BorderFactory.createTitledBorder("Generated Table Sync DDL"));

        JSplitPane detailSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollCols, ddlScroll);
        detailSplit.setResizeWeight(0.65);
        detailPanel.add(detailSplit, BorderLayout.CENTER);

        splitPane.setBottomComponent(detailPanel);
        panel.add(splitPane, BorderLayout.CENTER);

        // Bottom Status Label
        schemaStatusLabel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        panel.add(schemaStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void runSchemaComparison() {
        DatabaseEntry srcEntry = (DatabaseEntry) schemaSourceDbCombo.getSelectedItem();
        Object tgtObj = schemaTargetDbCombo.getSelectedItem();

        if (srcEntry == null || !srcEntry.getDbManager().isConnected()) {
            JOptionPane.showMessageDialog(this, "Source database is not connected.", "Missing Connection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isMatrix = "🌐 All Connected Databases (Matrix)".equals(tgtObj);
        DatabaseEntry tgtEntry = (!isMatrix && tgtObj instanceof DatabaseEntry) ? (DatabaseEntry) tgtObj : null;

        if (!isMatrix && (tgtEntry == null || !tgtEntry.getDbManager().isConnected())) {
            JOptionPane.showMessageDialog(this, "Target database is not connected.", "Missing Connection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        compareSchemasBtn.setEnabled(false);
        compareSchemasBtn.setText("Comparing...");
        schemaStatusLabel.setText("Inspecting schemas...");
        com.dbtool.util.AppUsageTracker.recordDualDbComparison();

        new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                JdbcSchemaInspector inspector = new JdbcSchemaInspector();

                boolean isSingle = "Single Table".equals(schemaScopeCombo.getSelectedItem());
                String singleTbl = (String) schemaSingleTableCombo.getSelectedItem();

                DatabaseManager dmA = srcEntry.getDbManager();
                List<String> listA = dmA.getTableNames();
                if (isSingle && singleTbl != null && !singleTbl.isEmpty()) {
                    listA = listA.contains(singleTbl) ? Collections.singletonList(singleTbl) : Collections.emptyList();
                }

                List<TableMetadata> tablesA = new ArrayList<>();
                for (String tName : listA) {
                    try { tablesA.add(inspector.inspectTable(dmA.connection, null, null, tName)); } catch (Exception ignored) {}
                }

                if (!isMatrix) {
                    // Pairwise Comparison
                    DatabaseManager dmB = tgtEntry.getDbManager();
                    List<String> listB = dmB.getTableNames();
                    if (isSingle && singleTbl != null && !singleTbl.isEmpty()) {
                        listB = listB.contains(singleTbl) ? Collections.singletonList(singleTbl) : Collections.emptyList();
                    }

                    List<TableMetadata> tablesB = new ArrayList<>();
                    for (String tName : listB) {
                        try { tablesB.add(inspector.inspectTable(dmB.connection, null, null, tName)); } catch (Exception ignored) {}
                    }

                    SchemaComparator comparator = new SchemaComparator();
                    SchemaDiffResult diffResult = comparator.compareSchemas(tablesA, tablesB);
                    lastSchemaDiffResult = diffResult;

                    SwingUtilities.invokeLater(() -> {
                        compareSchemasBtn.setEnabled(true);
                        compareSchemasBtn.setText("Compare Schemas");
                        generateAllSyncBtn.setEnabled(true);
                        populateSchemaTablesModel(diffResult, System.currentTimeMillis() - start, srcEntry.getLabel(), tgtEntry.getLabel());
                    });
                } else {
                    // Multi-Database Matrix Mode (All Connected DBs)
                    Map<DatabaseEntry, List<TableMetadata>> dbMetaMap = new LinkedHashMap<>();
                    dbMetaMap.put(srcEntry, tablesA);

                    for (DatabaseEntry d : databaseEntries) {
                        if (d != srcEntry && d.getDbManager().isConnected()) {
                            List<String> tbls = d.getDbManager().getTableNames();
                            if (isSingle && singleTbl != null && !singleTbl.isEmpty()) {
                                tbls = tbls.contains(singleTbl) ? Collections.singletonList(singleTbl) : Collections.emptyList();
                            }
                            List<TableMetadata> tList = new ArrayList<>();
                            for (String tn : tbls) {
                                try { tList.add(inspector.inspectTable(d.getDbManager().connection, null, null, tn)); } catch (Exception ignored) {}
                            }
                            dbMetaMap.put(d, tList);
                        }
                    }

                    SwingUtilities.invokeLater(() -> {
                        compareSchemasBtn.setEnabled(true);
                        compareSchemasBtn.setText("Compare Schemas");
                        generateAllSyncBtn.setEnabled(false);
                        populateMultiDbMatrixModel(dbMetaMap, System.currentTimeMillis() - start);
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    compareSchemasBtn.setEnabled(true);
                    compareSchemasBtn.setText("Compare Schemas");
                    schemaStatusLabel.setText("Schema comparison failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Schema comparison failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void populateSchemaTablesModel(SchemaDiffResult result, long durationMs, String labelA, String labelB) {
        String[] cols = {"Status", "Table Name", "Cols in " + labelA, "Cols in " + labelB, "Added in Target", "Missing in Target", "Modified Cols", "Summary"};
        schemaTablesModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        int countIdent = 0, countMod = 0, countOnlyA = 0, countOnlyB = 0;

        for (TableDiff td : result.getTableDiffs()) {
            String status;
            switch (td.getType()) {
                case REMOVED:
                    status = "🟥 In Source Only";
                    countOnlyA++;
                    break;
                case ADDED:
                    status = "🟩 In Target Only";
                    countOnlyB++;
                    break;
                case MODIFIED:
                    status = "🟨 Modified";
                    countMod++;
                    break;
                default:
                    status = "🟢 Identical";
                    countIdent++;
                    break;
            }

            int colsA = td.getSourceTable() != null ? td.getSourceTable().getColumns().size() : 0;
            int colsB = td.getTargetTable() != null ? td.getTargetTable().getColumns().size() : 0;
            int added = td.getAddedColumnsCount();
            int removed = td.getRemovedColumnsCount();
            int modified = td.getModifiedColumnsCount();

            String summary;
            if (td.getType() == DifferenceType.IDENTICAL) {
                summary = "Identical structure (" + colsA + " columns)";
            } else if (td.getType() == DifferenceType.REMOVED) {
                summary = "Missing in " + labelB;
            } else if (td.getType() == DifferenceType.ADDED) {
                summary = "Missing in " + labelA;
            } else {
                List<String> parts = new ArrayList<>();
                if (added > 0) parts.add("+" + added + " cols");
                if (removed > 0) parts.add("-" + removed + " cols");
                if (modified > 0) parts.add(modified + " modified");
                summary = String.join(", ", parts);
            }

            schemaTablesModel.addRow(new Object[]{status, td.getTableName(), colsA, colsB, added, removed, modified, summary});
        }

        schemaTablesSorter = new TableRowSorter<>(schemaTablesModel);
        schemaTablesTable.setModel(schemaTablesModel);
        schemaTablesTable.setRowSorter(schemaTablesSorter);

        schemaStatusLabel.setText(String.format("Compared %d tables in %d ms:  🟢 %d Identical | 🟨 %d Modified | 🟥 %d Only in %s | 🟩 %d Only in %s",
                result.getTableDiffs().size(), durationMs, countIdent, countMod, countOnlyA, labelA, countOnlyB, labelB));

        if (schemaTablesTable.getRowCount() > 0) {
            schemaTablesTable.setRowSelectionInterval(0, 0);
        }
    }

    private void populateMultiDbMatrixModel(Map<DatabaseEntry, List<TableMetadata>> dbMetaMap, long durationMs) {
        List<DatabaseEntry> dbs = new ArrayList<>(dbMetaMap.keySet());
        Set<String> allTableNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (List<TableMetadata> tList : dbMetaMap.values()) {
            for (TableMetadata tm : tList) allTableNames.add(tm.getTableName());
        }

        List<String> colHeaders = new ArrayList<>();
        colHeaders.add("Status");
        colHeaders.add("Table Name");
        for (DatabaseEntry d : dbs) {
            colHeaders.add("Cols in " + d.getId() + " (" + d.getDbManager().getLastDbName() + ")");
        }
        colHeaders.add("Matrix Summary");

        schemaTablesModel = new DefaultTableModel(colHeaders.toArray(new String[0]), 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        for (String tblName : allTableNames) {
            List<Object> row = new ArrayList<>();
            int presentCount = 0;
            int firstCols = -1;
            boolean colsMatch = true;

            List<Object> countCells = new ArrayList<>();
            for (DatabaseEntry d : dbs) {
                TableMetadata tm = findTableInList(dbMetaMap.get(d), tblName);
                if (tm != null) {
                    presentCount++;
                    int cCount = tm.getColumns().size();
                    countCells.add(cCount + " cols");
                    if (firstCols == -1) firstCols = cCount;
                    else if (firstCols != cCount) colsMatch = false;
                } else {
                    countCells.add("❌ Missing");
                }
            }

            String status;
            String summary;
            if (presentCount == dbs.size()) {
                if (colsMatch) {
                    status = "🟢 In All DBs (Identical)";
                    summary = "Present in all " + dbs.size() + " databases (" + firstCols + " cols)";
                } else {
                    status = "🟨 In All DBs (Modified)";
                    summary = "Present in all, but column counts vary";
                }
            } else {
                status = "🟦 Partial (" + presentCount + "/" + dbs.size() + ")";
                summary = "Missing in " + (dbs.size() - presentCount) + " database(s)";
            }

            row.add(status);
            row.add(tblName);
            row.addAll(countCells);
            row.add(summary);
            schemaTablesModel.addRow(row.toArray());
        }

        schemaTablesSorter = new TableRowSorter<>(schemaTablesModel);
        schemaTablesTable.setModel(schemaTablesModel);
        schemaTablesTable.setRowSorter(schemaTablesSorter);

        schemaStatusLabel.setText(String.format("Multi-DB Matrix: %d unique tables evaluated across %d connected databases in %d ms.",
                allTableNames.size(), dbs.size(), durationMs));

        if (schemaTablesTable.getRowCount() > 0) {
            schemaTablesTable.setRowSelectionInterval(0, 0);
        }
    }

    private TableMetadata findTableInList(List<TableMetadata> list, String tableName) {
        if (list == null) return null;
        for (TableMetadata tm : list) {
            if (tableName.equalsIgnoreCase(tm.getTableName())) return tm;
        }
        return null;
    }

    private void applySchemaFilter() {
        if (schemaTablesSorter == null) return;
        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        if (btnDiffOnlySchema.isSelected()) {
            filters.add(RowFilter.notFilter(RowFilter.regexFilter("(?i)🟢 Identical", 0)));
        } else if (btnOnlyASchema.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)🟥 In Source Only", 0));
        } else if (btnOnlyBSchema.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)🟩 In Target Only", 0));
        } else if (btnModSchema.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)🟨 Modified", 0));
        } else if (btnIdentSchema.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)🟢 Identical", 0));
        }

        String search = schemaSearchField.getText().trim();
        if (!search.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + search, 1));
        }

        if (filters.isEmpty()) {
            schemaTablesSorter.setRowFilter(null);
        } else {
            schemaTablesSorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    private void inspectSelectedTableColumns() {
        int row = schemaTablesTable.getSelectedRow();
        if (row < 0 || lastSchemaDiffResult == null) {
            columnDiffTable.setModel(new DefaultTableModel());
            tableSyncDdlArea.setText("");
            return;
        }

        int modelRow = schemaTablesTable.convertRowIndexToModel(row);
        String tblName = (String) schemaTablesModel.getValueAt(modelRow, 1);
        TableDiff td = lastSchemaDiffResult.findTableDiff(tblName);
        if (td == null) return;

        selectedTableTitleLabel.setText("Inspecting Table: " + tblName + " [" + td.getType() + "]");

        String[] cols = {"Status", "Column Name", "Type in Source", "Nullable Src", "Default Src", "Type in Target", "Nullable Tgt", "Default Tgt", "Diff Description"};
        DefaultTableModel colModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        TableMetadata tmA = td.getSourceTable();
        TableMetadata tmB = td.getTargetTable();

        Set<String> allCols = new LinkedHashSet<>();
        if (tmA != null) {
            for (ColumnMetadata c : tmA.getColumns()) allCols.add(c.getColumnName());
        }
        if (tmB != null) {
            for (ColumnMetadata c : tmB.getColumns()) allCols.add(c.getColumnName());
        }

        for (String cName : allCols) {
            ColumnMetadata cA = tmA != null ? tmA.findColumn(cName) : null;
            ColumnMetadata cB = tmB != null ? tmB.findColumn(cName) : null;

            String status;
            String diffDesc = "";
            String typeA = cA != null ? cA.getTypeName() : "-";
            String nullA = cA != null ? String.valueOf(cA.isNullable()) : "-";
            String defA = cA != null ? (cA.getDefaultValue() != null ? cA.getDefaultValue() : "") : "-";

            String typeB = cB != null ? cB.getTypeName() : "-";
            String nullB = cB != null ? String.valueOf(cB.isNullable()) : "-";
            String defB = cB != null ? (cB.getDefaultValue() != null ? cB.getDefaultValue() : "") : "-";

            if (cA != null && cB == null) {
                status = "🟥 In Source Only";
                diffDesc = "Column missing in Target database";
            } else if (cA == null && cB != null) {
                status = "🟩 In Target Only";
                diffDesc = "Column missing in Source database";
            } else if (!typeA.equalsIgnoreCase(typeB) || !nullA.equals(nullB) || !defA.equals(defB)) {
                status = "🟨 Modified";
                List<String> diffs = new ArrayList<>();
                if (!typeA.equalsIgnoreCase(typeB)) diffs.add("Type: " + typeA + " ➔ " + typeB);
                if (!nullA.equals(nullB)) diffs.add("Nullable: " + nullA + " ➔ " + nullB);
                if (!defA.equals(defB)) diffs.add("Default changed");
                diffDesc = String.join(", ", diffs);
            } else {
                status = "🟢 Identical";
                diffDesc = "Match";
            }

            colModel.addRow(new Object[]{status, cName, typeA, nullA, defA, typeB, nullB, defB, diffDesc});
        }

        columnDiffTable.setModel(colModel);
        columnDiffTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Generate Single Table Sync DDL
        StringBuilder ddl = new StringBuilder();
        if (td.getType() == DifferenceType.REMOVED && tmA != null) {
            ddl.append(DiffScriptGenerator.generateCreateTableDdl(tmA));
        } else if (td.getType() == DifferenceType.MODIFIED) {
            for (ColumnDiff cd : td.getColumnDiffs()) {
                if (cd.getType() == DifferenceType.REMOVED && cd.getSourceColumn() != null) {
                    ddl.append("ALTER TABLE ").append(td.getTableName()).append(" ADD COLUMN ")
                       .append(cd.getSourceColumn().getColumnName()).append(" ")
                       .append(cd.getSourceColumn().getTypeName()).append(";\n");
                } else if (cd.getType() == DifferenceType.MODIFIED && cd.getSourceColumn() != null) {
                    ddl.append("ALTER TABLE ").append(td.getTableName()).append(" ALTER COLUMN ")
                       .append(cd.getSourceColumn().getColumnName()).append(" TYPE ")
                       .append(cd.getSourceColumn().getTypeName()).append(";\n");
                }
            }
        } else if (td.getType() == DifferenceType.ADDED) {
            ddl.append("-- Optional: Table only in Target\n-- DROP TABLE ").append(td.getTableName()).append(";");
        } else {
            ddl.append("-- Table structures are identical. No synchronization needed.");
        }
        tableSyncDdlArea.setText(ddl.toString());
    }

    private void openMigrationDdlDialog() {
        if (lastSchemaDiffResult == null) return;
        DatabaseEntry src = (DatabaseEntry) schemaSourceDbCombo.getSelectedItem();
        Object tgt = schemaTargetDbCombo.getSelectedItem();
        if (!(tgt instanceof DatabaseEntry)) return;

        DatabaseEntry tgtEntry = (DatabaseEntry) tgt;
        MigrationWorkbenchDialog dlg = new MigrationWorkbenchDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            lastSchemaDiffResult,
            src != null ? src.getLabel() : "Source DB",
            tgtEntry,
            () -> runSchemaComparison()
        );
        dlg.setVisible(true);
    }

    // ==========================================
    // 2. Data Diff Multi-Tab Container Construction
    // ==========================================
    private JPanel buildDataDiffContainerPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Top Toolbar for adding/managing compare tabs
        JToolBar tbTabs = new JToolBar();
        tbTabs.setFloatable(false);
        tbTabs.setLayout(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 6, 2));

        btnNewTab.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnNewTab.setForeground(ThemeManager.getSuccessColor());
        btnNewTab.setToolTipText("Open another tab to compare a different table or database concurrently");
        btnNewTab.addActionListener(e -> addDataCompareTab("Compare Session " + (++dataTabCounter)));
        tbTabs.add(btnNewTab);

        btnDuplicateTab.setToolTipText("Duplicate the currently selected comparison tab with all its configured clauses");
        btnDuplicateTab.addActionListener(e -> duplicateActiveDataCompareTab());
        tbTabs.add(btnDuplicateTab);

        tbTabs.add(Box.createHorizontalGlue());
        tipLabel.setForeground(ThemeManager.getMutedTextColor());
        tbTabs.add(tipLabel);

        dataCompareTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        dataCompareTabbedPane.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");

        panel.add(tbTabs, BorderLayout.NORTH);
        panel.add(dataCompareTabbedPane, BorderLayout.CENTER);
        return panel;
    }

    public DataCompareTabPanel addDataCompareTab(String title) {
        DataCompareTabPanel tab = new DataCompareTabPanel(this);
        dataCompareTabs.add(tab);

        dataCompareTabbedPane.addTab(title, tab);
        int idx = dataCompareTabbedPane.indexOfComponent(tab);

        // Custom tab header with title and close button
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        tabHeader.setOpaque(false);
        JLabel titleLbl = new JLabel(title);
        tab.setTabTitleLabel(titleLbl);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setOpaque(false);
        closeBtn.setToolTipText("Close this comparison tab");
        closeBtn.addActionListener(e -> {
            if (dataCompareTabbedPane.getTabCount() > 1) {
                dataCompareTabbedPane.remove(tab);
                dataCompareTabs.remove(tab);
            } else {
                JOptionPane.showMessageDialog(this, "At least one comparison tab must remain open.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { closeBtn.setForeground(Color.RED); }
            public void mouseExited(java.awt.event.MouseEvent e) { closeBtn.setForeground(null); }
        });

        tabHeader.add(titleLbl);
        tabHeader.add(closeBtn);
        dataCompareTabbedPane.setTabComponentAt(idx, tabHeader);
        dataCompareTabbedPane.setSelectedIndex(idx);
        return tab;
    }

    private void duplicateActiveDataCompareTab() {
        int idx = dataCompareTabbedPane.getSelectedIndex();
        if (idx < 0 || idx >= dataCompareTabs.size()) return;
        DataCompareTabPanel current = dataCompareTabs.get(idx);

        DataCompareTabPanel duplicate = addDataCompareTab(current.getTabTitle() + " (Copy)");
        duplicate.copyStateFrom(current);
    }

    public JTabbedPane getDataTabsPane() {
        return dataCompareTabbedPane;
    }

    public List<DataCompareTabPanel> getDataCompareTabs() {
        return Collections.unmodifiableList(dataCompareTabs);
    }

    public int getDataCompareTabCount() {
        return dataCompareTabs.size();
    }

    public String buildQueryForDatabase(DatabaseManager dbm, String mainTable, String keyCol) {
        int idx = dataCompareTabbedPane.getSelectedIndex();
        if (idx >= 0 && idx < dataCompareTabs.size()) {
            return dataCompareTabs.get(idx).buildQueryForDatabase(dbm, mainTable, keyCol);
        }
        if (!dataCompareTabs.isEmpty()) {
            return dataCompareTabs.get(0).buildQueryForDatabase(dbm, mainTable, keyCol);
        }
        return "SELECT * FROM " + dbm.quoteTableName(mainTable);
    }

    // ==========================================
    // DatabaseEntry Model (Wrapper for Connection)
    // ==========================================
    public class DatabaseEntry {
        private final String id;
        private final String label;
        private final DatabaseManager dbManager;
        private final boolean removable;
        private final JLabel statusLabel = new JLabel();
        private final JPanel cardPanel;

        public DatabaseEntry(String id, String label, DatabaseManager dbManager, boolean removable) {
            this.id = id;
            this.label = label;
            this.dbManager = dbManager;
            this.removable = removable;
            this.cardPanel = buildCard();
            updateStatusLabel();
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public DatabaseManager getDbManager() { return dbManager; }
        public boolean isRemovable() { return removable; }
        public JPanel getCardPanel() { return cardPanel; }

        private JPanel buildCard() {
            JPanel card = new JPanel(new BorderLayout(4, 2));
            card.setPreferredSize(new Dimension(300, 78));
            card.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), label, TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11)));

            statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            card.add(statusLabel, BorderLayout.NORTH);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
            JButton linkAppBtn = new JButton("App DB");
            linkAppBtn.setMargin(new Insets(1, 4, 1, 4));
            linkAppBtn.setToolTipText("Link with active application connection");
            linkAppBtn.addActionListener(e -> linkWithApp());
            actions.add(linkAppBtn);

            JButton sandboxBtn = new JButton("Sandbox");
            sandboxBtn.setMargin(new Insets(1, 4, 1, 4));
            sandboxBtn.setToolTipText("Launch independent in-memory sandbox for " + label);
            sandboxBtn.addActionListener(e -> connectSandbox());
            actions.add(sandboxBtn);

            JButton connectPgBtn = new JButton("Postgres...");
            connectPgBtn.setMargin(new Insets(1, 4, 1, 4));
            connectPgBtn.addActionListener(e -> connectPostgres());
            actions.add(connectPgBtn);

            JButton disconnectBtn = new JButton("Disconnect");
            disconnectBtn.setMargin(new Insets(1, 4, 1, 4));
            disconnectBtn.setToolTipText("Disconnect " + label);
            disconnectBtn.addActionListener(e -> disconnect());
            actions.add(disconnectBtn);

            if (removable) {
                JButton removeBtn = new JButton("✕");
                removeBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
                removeBtn.setForeground(ThemeManager.getDangerColor());
                removeBtn.setMargin(new Insets(1, 4, 1, 4));
                removeBtn.setToolTipText("Remove " + label + " from comparison workspace");
                removeBtn.addActionListener(e -> removeDatabaseEntry(this));
                actions.add(removeBtn);
            }

            card.add(actions, BorderLayout.CENTER);
            return card;
        }

        public void linkWithApp() {
            if (primaryDbManager == null || !primaryDbManager.isConnected()) {
                JOptionPane.showMessageDialog(DualDatabaseComparePanel.this, "Main application has no active connection.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                dbManager.useExistingConnection(primaryDbManager);
                updateStatusLabel();
                refreshDatabaseSelectors();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(DualDatabaseComparePanel.this, "Failed to link " + label + ": " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        public void connectSandbox() {
            new Thread(() -> {
                try {
                    dbManager.connectSandbox("dual_db_sandbox_" + id.toLowerCase());
                    SwingUtilities.invokeLater(() -> {
                        updateStatusLabel();
                        refreshDatabaseSelectors();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(DualDatabaseComparePanel.this, "Failed to connect Sandbox: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            }).start();
        }

        public void connectPostgres() {
            ConnectionManagerDialog dialog = new ConnectionManagerDialog((JFrame) SwingUtilities.getWindowAncestor(DualDatabaseComparePanel.this), dbManager);
            dialog.setVisible(true);
            if (dialog.isApproved()) {
                new Thread(() -> {
                    try {
                        dbManager.connectExistingPostgres(dialog.getHost(), dialog.getPort(), dialog.getDbName(), dialog.getUser(), dialog.getPass());
                        SwingUtilities.invokeLater(() -> {
                            updateStatusLabel();
                            refreshDatabaseSelectors();
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(DualDatabaseComparePanel.this, "Failed to connect " + label + ": " + ex.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE));
                    }
                }).start();
            }
        }

        public void disconnect() {
            if (dbManager.connection != null) {
                try { dbManager.connection.close(); } catch (Exception ignored) {}
                dbManager.connection = null;
                dbManager.clearCache();
            }
            updateStatusLabel();
            refreshDatabaseSelectors();
        }

        public void updateStatusLabel() {
            if (dbManager.isConnected()) {
                statusLabel.setText("● Connected: " + dbManager.getLastDbName() + (dbManager.getLastHost() != null ? " (" + dbManager.getLastHost() + ")" : ""));
                statusLabel.setForeground(ThemeManager.getSuccessColor());
            } else {
                statusLabel.setText("○ " + label + ": Disconnected");
                statusLabel.setForeground(ThemeManager.getMutedTextColor());
            }
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class DatabaseEntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DatabaseEntry) {
                DatabaseEntry d = (DatabaseEntry) value;
                lbl.setText(d.getLabel() + (d.getDbManager().isConnected() ? " [Connected]" : " [Disconnected]"));
            }
            return lbl;
        }
    }
}
