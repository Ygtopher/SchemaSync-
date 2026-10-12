package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.panels.DualDatabaseComparePanel.DatabaseEntry;
import com.dbtool.ui.components.DiffTableCellRenderer;
import com.dbtool.ui.components.SearchableComboBox;
import com.dbtool.util.ExportUtil;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;

/**
 * Encapsulates an individual table/query data comparison session.
 * Multiple DataCompareTabPanels can be opened concurrently in tabs.
 */
public class DataCompareTabPanel extends JPanel {

    private final DualDatabaseComparePanel parentPanel;
    private JLabel tabTitleLabel;

    // Mode Switcher
    private final JRadioButton radioQuickTable = new JRadioButton("Quick Table Match", true);
    private final JRadioButton radioCustomSql = new JRadioButton("Custom SQL Query");

    // Database Selectors
    private final JComboBox<DatabaseEntry> comboDbA = new JComboBox<>();
    private final JComboBox<DatabaseEntry> comboDbB = new JComboBox<>();

    // Quick Table & Columns Controls
    private final SearchableComboBox<String> comboTableA = new SearchableComboBox<>();
    private final SearchableComboBox<String> comboTableB = new SearchableComboBox<>();
    private final SearchableComboBox<String> comboKeyCol = new SearchableComboBox<>();
    private final JButton btnSelectCols = new JButton("Columns (All)");
    private final Set<String> selectedDataColumns = new LinkedHashSet<>();
    private final JCheckBox chkDistinct = new JCheckBox("Distinct");
    private final JTextField txtLimit = new JTextField("1000", 6);
    private final JTextField txtOffset = new JTextField("", 5);

    // Dynamic Clauses (Where, Join, Order By, Aggregates, Group By, Having, Window Functions)
    private final JPanel clausesContainer = new JPanel();
    private final JPanel joinsContainer = new JPanel();
    private final List<DualJoinRow> joinRows = new ArrayList<>();
    private final JPanel whereContainer = new JPanel();
    private final List<DualWhereRow> whereRows = new ArrayList<>();
    private final JPanel orderByContainer = new JPanel();
    private final List<DualOrderByRow> orderByRows = new ArrayList<>();
    private final JPanel aggregateContainer = new JPanel();
    private final List<DualAggregateRow> aggregateRows = new ArrayList<>();
    private final JPanel groupByContainer = new JPanel();
    private final List<DualGroupByRow> groupByRows = new ArrayList<>();
    private final JPanel havingContainer = new JPanel();
    private final List<DualHavingRow> havingRows = new ArrayList<>();
    private final JPanel windowFuncContainer = new JPanel();
    private final List<DualWindowFuncRow> windowFuncRows = new ArrayList<>();
    private final JComboBox<String> comboLock = new JComboBox<>(new String[]{
        "Lock: None", "FOR UPDATE", "FOR UPDATE SKIP LOCKED", "FOR SHARE", "FOR NO KEY UPDATE"
    });
    private final JComboBox<String> comboSample = new JComboBox<>(new String[]{
        "Sample: None", "BERNOULLI (10)", "BERNOULLI (5)", "BERNOULLI (1)", "SYSTEM (10)", "SYSTEM (5)"
    });
    private final JScrollPane clausesScrollPane = new JScrollPane(clausesContainer);

    // Custom SQL Query Editors
    private final RSyntaxTextArea queryAArea = new RSyntaxTextArea(4, 30);
    private final RSyntaxTextArea queryBArea = new RSyntaxTextArea(4, 30);
    private final JPanel customSqlPanel = new JPanel(new BorderLayout());
    private final JPanel quickTableContainer = new JPanel();

    // Execution & Results
    private final JButton executeDataDiffBtn = new JButton("Run Data Diff (Compare Rows)");
    private final JLabel dataStatusLabel = new JLabel("Select tables and click 'Execute & Compare Data'.");
    private final JTable unifiedDiffTable = new JTable();
    private final JTable sideTableA = new JTable();
    private final JTable sideTableB = new JTable();
    private final JTabbedPane dataViewsTabbedPane = new JTabbedPane();

    private DefaultTableModel unifiedDiffModel;
    private TableRowSorter<DefaultTableModel> unifiedDiffSorter;
    private final JTextField dataSearchField = new JTextField(12);

    // Filter Buttons
    private final JToggleButton btnAllData = new JToggleButton("All Rows");
    private final JToggleButton btnDiffOnlyData = new JToggleButton("Differences Only");
    private final JToggleButton btnModData = new JToggleButton("Modified");
    private final JToggleButton btnOnlyAData = new JToggleButton("In Source Only");
    private final JToggleButton btnOnlyBData = new JToggleButton("In Target Only");

    public DataCompareTabPanel(DualDatabaseComparePanel parentPanel) {
        this.parentPanel = parentPanel;
        setLayout(new BorderLayout());
        buildUI();
        refreshDatabaseSelectors();
    }

    public void setTabTitleLabel(JLabel tabTitleLabel) {
        this.tabTitleLabel = tabTitleLabel;
    }

    public String getTabTitle() {
        return tabTitleLabel != null ? tabTitleLabel.getText().replace("×", "").trim() : "Compare Session";
    }

    private void buildUI() {
        JPanel topConfigPanel = new JPanel(new BorderLayout(4, 4));
        topConfigPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        // 1. Mode Switcher
        JPanel modeSwitcherPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        ButtonGroup modeBg = new ButtonGroup();
        modeBg.add(radioQuickTable); modeBg.add(radioCustomSql);
        modeSwitcherPanel.add(new JLabel("Mode: "));
        modeSwitcherPanel.add(radioQuickTable);
        modeSwitcherPanel.add(radioCustomSql);
        topConfigPanel.add(modeSwitcherPanel, BorderLayout.NORTH);

        // 2. Quick Table Container
        quickTableContainer.setLayout(new BoxLayout(quickTableContainer, BoxLayout.Y_AXIS));

        // Row A: Database Selectors & Tables
        JPanel rowDbAndTable = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        rowDbAndTable.add(new JLabel("Source DB: "));
        comboDbA.setPreferredSize(new Dimension(150, 26));
        comboDbA.setRenderer(new DatabaseEntryRenderer());
        comboDbA.addActionListener(e -> onDatabaseSelectionChanged(true));
        rowDbAndTable.add(comboDbA);

        rowDbAndTable.add(new JLabel(" Table: "));
        comboTableA.setPreferredSize(new Dimension(150, 26));
        comboTableA.addActionListener(e -> {
            String sel = (String) comboTableA.getSelectedItem();
            DatabaseEntry dbB = getSelectedDatabaseB();
            if (sel != null && dbB != null && dbB.getDbManager().isConnected() && dbB.getDbManager().getTableNames().contains(sel)) {
                comboTableB.setSelectedItem(sel);
            }
            selectedDataColumns.clear();
            btnSelectCols.setText("Columns (All)");
            updateKeyColDropdown();
            updateTabTitle();
        });
        rowDbAndTable.add(comboTableA);

        rowDbAndTable.add(new JLabel(" ➔ Target DB: "));
        comboDbB.setPreferredSize(new Dimension(150, 26));
        comboDbB.setRenderer(new DatabaseEntryRenderer());
        comboDbB.addActionListener(e -> onDatabaseSelectionChanged(false));
        rowDbAndTable.add(comboDbB);

        rowDbAndTable.add(new JLabel(" Table: "));
        comboTableB.setPreferredSize(new Dimension(150, 26));
        comboTableB.addActionListener(e -> {
            selectedDataColumns.clear();
            btnSelectCols.setText("Columns (All)");
            updateKeyColDropdown();
            updateTabTitle();
        });
        rowDbAndTable.add(comboTableB);

        rowDbAndTable.add(new JLabel(" | Key: "));
        comboKeyCol.setPreferredSize(new Dimension(120, 26));
        rowDbAndTable.add(comboKeyCol);

        btnSelectCols.setToolTipText("Select specific columns to compare, or compare all columns");
        btnSelectCols.addActionListener(e -> openColumnSelectionDialog());
        rowDbAndTable.add(new JLabel(" | "));
        rowDbAndTable.add(btnSelectCols);

        chkDistinct.setToolTipText("Add DISTINCT clause to SELECT queries");
        rowDbAndTable.add(new JLabel(" | "));
        rowDbAndTable.add(chkDistinct);

        rowDbAndTable.add(new JLabel(" | Limit: "));
        txtLimit.setPreferredSize(new Dimension(55, 26));
        rowDbAndTable.add(txtLimit);

        rowDbAndTable.add(new JLabel(" Offset: "));
        txtOffset.setPreferredSize(new Dimension(45, 26));
        rowDbAndTable.add(txtOffset);

        quickTableContainer.add(rowDbAndTable);

        // Row B: Clauses Toolbar
        JToolBar tbClauses = new JToolBar();
        tbClauses.setFloatable(false);
        tbClauses.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton btnAddAgg = new JButton("+ Add Aggregate");
        btnAddAgg.setToolTipText("Add aggregate expression (COUNT, SUM, AVG, MIN, MAX) with optional FILTER clause");
        btnAddAgg.addActionListener(e -> addAggregateRow());
        tbClauses.add(btnAddAgg);

        JButton btnAddGrp = new JButton("+ Add Group By");
        btnAddGrp.setToolTipText("Add GROUP BY column");
        btnAddGrp.addActionListener(e -> addGroupByRow());
        tbClauses.add(btnAddGrp);

        JButton btnAddHav = new JButton("+ Add Having");
        btnAddHav.setToolTipText("Add HAVING aggregate condition");
        btnAddHav.addActionListener(e -> addHavingRow());
        tbClauses.add(btnAddHav);

        JButton btnAddWin = new JButton("+ Add Window Func");
        btnAddWin.setToolTipText("Add Window function (OVER partition/order/frame)");
        btnAddWin.addActionListener(e -> addWindowFuncRow());
        tbClauses.add(btnAddWin);

        JButton btnAddWhere = new JButton("+ Add Where");
        btnAddWhere.setToolTipText("Add a WHERE filtering condition to compare specific subsets of data");
        btnAddWhere.addActionListener(e -> addWhereRow());
        tbClauses.add(btnAddWhere);

        JButton btnAddJoin = new JButton("+ Add Join");
        btnAddJoin.setToolTipText("Add a table JOIN clause to compare joined datasets");
        btnAddJoin.addActionListener(e -> addJoinRow());
        tbClauses.add(btnAddJoin);

        JButton btnAddOrderBy = new JButton("+ Add Order By");
        btnAddOrderBy.setToolTipText("Add an ORDER BY sorting clause to control comparison ordering");
        btnAddOrderBy.addActionListener(e -> addOrderByRow());
        tbClauses.add(btnAddOrderBy);

        tbClauses.add(new JLabel(" | "));
        comboSample.setToolTipText("TABLESAMPLE sampling clause");
        tbClauses.add(comboSample);
        comboLock.setToolTipText("Row-level locking clause (FOR UPDATE, SKIP LOCKED)");
        tbClauses.add(comboLock);

        JButton btnExplain = new JButton("🔍 EXPLAIN ▾");
        btnExplain.setToolTipText("Inspect execution plan for comparison queries");
        btnExplain.addActionListener(e -> showExplainMenu(btnExplain));
        tbClauses.add(btnExplain);

        tbClauses.addSeparator();

        JButton btnPreviewSql = new JButton("Preview SQL Queries");
        btnPreviewSql.setToolTipText("Preview the exact SQL queries generated for Source and Target DBs");
        btnPreviewSql.addActionListener(e -> previewSqlQueries());
        tbClauses.add(btnPreviewSql);

        JButton btnClearClauses = new JButton("Clear Clauses");
        btnClearClauses.addActionListener(e -> clearAllClauses());
        tbClauses.add(btnClearClauses);

        tbClauses.addSeparator();

        JButton templatesBtn = new JButton("📝 SQL Templates ▾");
        templatesBtn.setToolTipText("Access advanced SQL templates and clauses (Correlated joins, Window frames, CTEs, etc.)");
        templatesBtn.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            com.dbtool.util.SqlSnippetsLibrary.populatePopupMenu(menu, sql -> {
                int opt = JOptionPane.showOptionDialog(this,
                    "Selected SQL Template:\n\n" + (sql.length() > 220 ? sql.substring(0, 220) + "..." : sql) + "\n\nChoose an action:",
                    "SQL Template Action",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"Load into Custom SQL", "Copy to Clipboard", "Cancel"},
                    "Load into Custom SQL");
                if (opt == 0) {
                    radioCustomSql.setSelected(true);
                    quickTableContainer.setVisible(false);
                    customSqlPanel.setVisible(true);
                    queryAArea.setText(sql);
                    queryBArea.setText(sql);
                    revalidate(); repaint();
                } else if (opt == 1) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sql), null);
                    JOptionPane.showMessageDialog(this, "Snippet copied to clipboard!");
                }
            });
            menu.show(templatesBtn, 0, templatesBtn.getHeight());
        });

        JButton cheatSheetBtn = new JButton("💡 Cheat Sheet");
        cheatSheetBtn.setToolTipText("Open interactive Advanced SQL Mastery Cheat Sheet");
        cheatSheetBtn.addActionListener(e -> {
            new com.dbtool.panels.SqlMasteryReferenceDialog(SwingUtilities.getWindowAncestor(this), sql -> {
                radioCustomSql.setSelected(true);
                quickTableContainer.setVisible(false);
                customSqlPanel.setVisible(true);
                queryAArea.setText(sql);
                queryBArea.setText(sql);
                revalidate(); repaint();
            }).setVisible(true);
        });

        tbClauses.add(templatesBtn);
        tbClauses.add(cheatSheetBtn);

        quickTableContainer.add(tbClauses);

        // Row C: Dynamic Clauses Scroll Pane
        clausesContainer.setLayout(new BoxLayout(clausesContainer, BoxLayout.Y_AXIS));
        aggregateContainer.setLayout(new BoxLayout(aggregateContainer, BoxLayout.Y_AXIS));
        groupByContainer.setLayout(new BoxLayout(groupByContainer, BoxLayout.Y_AXIS));
        havingContainer.setLayout(new BoxLayout(havingContainer, BoxLayout.Y_AXIS));
        windowFuncContainer.setLayout(new BoxLayout(windowFuncContainer, BoxLayout.Y_AXIS));
        joinsContainer.setLayout(new BoxLayout(joinsContainer, BoxLayout.Y_AXIS));
        whereContainer.setLayout(new BoxLayout(whereContainer, BoxLayout.Y_AXIS));
        orderByContainer.setLayout(new BoxLayout(orderByContainer, BoxLayout.Y_AXIS));

        clausesContainer.add(aggregateContainer);
        clausesContainer.add(groupByContainer);
        clausesContainer.add(havingContainer);
        clausesContainer.add(windowFuncContainer);
        clausesContainer.add(joinsContainer);
        clausesContainer.add(whereContainer);
        clausesContainer.add(orderByContainer);

        clausesScrollPane.setPreferredSize(new Dimension(800, 130));
        clausesScrollPane.getVerticalScrollBar().setUnitIncrement(14);
        clausesScrollPane.setBorder(BorderFactory.createTitledBorder("Active Query Clauses"));
        clausesScrollPane.setVisible(false);
        quickTableContainer.add(clausesScrollPane);

        // 3. Custom SQL Controls
        queryAArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        queryAArea.setText("SELECT * FROM employees;");
        queryBArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        queryBArea.setText("SELECT * FROM employees;");

        JSplitPane sqlSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapQueryEditor("Query for Source Database", queryAArea),
                wrapQueryEditor("Query for Target Database", queryBArea));
        sqlSplit.setResizeWeight(0.5);
        customSqlPanel.add(sqlSplit, BorderLayout.CENTER);
        customSqlPanel.setVisible(false);

        radioQuickTable.addActionListener(e -> {
            quickTableContainer.setVisible(true);
            customSqlPanel.setVisible(false);
            revalidate(); repaint();
        });
        radioCustomSql.addActionListener(e -> {
            quickTableContainer.setVisible(false);
            customSqlPanel.setVisible(true);
            revalidate(); repaint();
        });

        JPanel container = new JPanel(new BorderLayout());
        container.add(quickTableContainer, BorderLayout.NORTH);
        container.add(customSqlPanel, BorderLayout.CENTER);
        topConfigPanel.add(container, BorderLayout.CENTER);

        // 4. Execution & Filters Toolbar
        JToolBar tbData = new JToolBar();
        tbData.setFloatable(false);
        tbData.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));

        executeDataDiffBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        executeDataDiffBtn.setForeground(ThemeManager.getSuccessColor());
        executeDataDiffBtn.addActionListener(e -> runDataComparison());
        tbData.add(executeDataDiffBtn);
        tbData.addSeparator();

        ButtonGroup dataFilterBg = new ButtonGroup();
        dataFilterBg.add(btnAllData); dataFilterBg.add(btnDiffOnlyData); dataFilterBg.add(btnModData); dataFilterBg.add(btnOnlyAData); dataFilterBg.add(btnOnlyBData);
        btnAllData.setSelected(true);

        java.awt.event.ActionListener dFilterL = e -> applyDataFilter();
        btnAllData.addActionListener(dFilterL);
        btnDiffOnlyData.addActionListener(dFilterL);
        btnModData.addActionListener(dFilterL);
        btnOnlyAData.addActionListener(dFilterL);
        btnOnlyBData.addActionListener(dFilterL);

        tbData.add(btnAllData);
        tbData.add(btnDiffOnlyData);
        tbData.add(btnModData);
        tbData.add(btnOnlyAData);
        tbData.add(btnOnlyBData);
        tbData.addSeparator();

        tbData.add(new JLabel("🔍 Filter: "));
        dataSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyDataFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyDataFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyDataFilter(); }
        });
        tbData.add(dataSearchField);

        JButton exportCsvBtn = new JButton("📥 Export CSV");
        exportCsvBtn.addActionListener(e -> ExportUtil.exportCsv(unifiedDiffTable, this));
        JButton exportPdfBtn = new JButton("📥 Export PDF");
        exportPdfBtn.addActionListener(e -> ExportUtil.exportPdf(unifiedDiffTable, this));

        tbData.add(Box.createHorizontalGlue());
        tbData.add(exportCsvBtn);
        tbData.add(exportPdfBtn);

        topConfigPanel.add(tbData, BorderLayout.SOUTH);
        add(topConfigPanel, BorderLayout.NORTH);

        // 5. Result Views (Unified Diff vs Side-by-Side)
        unifiedDiffTable.setDefaultRenderer(Object.class, new DiffTableCellRenderer());
        unifiedDiffTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane unifiedScroll = new JScrollPane(unifiedDiffTable);
        com.dbtool.util.TableRowUtilities.addRowNumbers(unifiedDiffTable, unifiedScroll);
        dataViewsTabbedPane.addTab("Unified Diff View", unifiedScroll);

        sideTableA.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        sideTableB.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane scrollA = new JScrollPane(sideTableA);
        JScrollPane scrollB = new JScrollPane(sideTableB);
        scrollA.setBorder(BorderFactory.createTitledBorder("Source Database Results"));
        scrollB.setBorder(BorderFactory.createTitledBorder("Target Database Results"));
        JSplitPane sideSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollA, scrollB);
        sideSplit.setResizeWeight(0.5);
        dataViewsTabbedPane.addTab("Side-by-Side View", sideSplit);

        add(dataViewsTabbedPane, BorderLayout.CENTER);

        dataStatusLabel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        add(dataStatusLabel, BorderLayout.SOUTH);
    }

    private JPanel wrapQueryEditor(String title, RSyntaxTextArea area) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new RTextScrollPane(area), BorderLayout.CENTER);
        return p;
    }

    public void refreshDatabaseSelectors() {
        List<DatabaseEntry> entries = parentPanel.getDatabaseEntries();
        DatabaseEntry selA = (DatabaseEntry) comboDbA.getSelectedItem();
        DatabaseEntry selB = (DatabaseEntry) comboDbB.getSelectedItem();

        comboDbA.removeAllItems();
        comboDbB.removeAllItems();

        for (DatabaseEntry d : entries) {
            comboDbA.addItem(d);
            comboDbB.addItem(d);
        }

        if (selA != null && entries.contains(selA)) {
            comboDbA.setSelectedItem(selA);
        } else if (comboDbA.getItemCount() > 0) {
            comboDbA.setSelectedIndex(0);
        }

        if (selB != null && entries.contains(selB)) {
            comboDbB.setSelectedItem(selB);
        } else if (comboDbB.getItemCount() > 1) {
            comboDbB.setSelectedIndex(1);
        } else if (comboDbB.getItemCount() > 0) {
            comboDbB.setSelectedIndex(0);
        }

        onDatabaseSelectionChanged(true);
        onDatabaseSelectionChanged(false);
    }

    private void onDatabaseSelectionChanged(boolean isSource) {
        DatabaseEntry dbA = getSelectedDatabaseA();
        DatabaseEntry dbB = getSelectedDatabaseB();

        if (isSource && dbA != null && dbA.getDbManager().isConnected()) {
            comboTableA.setAllItems(dbA.getDbManager().getTableNames());
        }
        if (!isSource && dbB != null && dbB.getDbManager().isConnected()) {
            comboTableB.setAllItems(dbB.getDbManager().getTableNames());
        }

        updateKeyColDropdown();
    }

    public DatabaseEntry getSelectedDatabaseA() {
        return (DatabaseEntry) comboDbA.getSelectedItem();
    }

    public DatabaseEntry getSelectedDatabaseB() {
        return (DatabaseEntry) comboDbB.getSelectedItem();
    }

    public SearchableComboBox<String> getComboTableA() {
        return comboTableA;
    }

    public SearchableComboBox<String> getComboTableB() {
        return comboTableB;
    }

    public SearchableComboBox<String> getComboKeyCol() {
        return comboKeyCol;
    }

    public JComboBox<DatabaseEntry> getComboDbA() {
        return comboDbA;
    }

    public JComboBox<DatabaseEntry> getComboDbB() {
        return comboDbB;
    }

    public void updateThemeColors() {
        executeDataDiffBtn.setForeground(ThemeManager.getSuccessColor());
        repaint();
    }

    private void updateKeyColDropdown() {
        String tA = (String) comboTableA.getSelectedItem();
        String tB = (String) comboTableB.getSelectedItem();
        DatabaseEntry dbA = getSelectedDatabaseA();
        DatabaseEntry dbB = getSelectedDatabaseB();

        if (tA == null || tB == null || dbA == null || dbB == null) return;

        List<String> colsA = dbA.getDbManager().isConnected() ? dbA.getDbManager().getColumnNames(tA) : Collections.emptyList();
        List<String> colsB = dbB.getDbManager().isConnected() ? dbB.getDbManager().getColumnNames(tB) : Collections.emptyList();

        List<String> commonCols = new ArrayList<>();
        for (String c : colsA) {
            if (colsB.contains(c)) commonCols.add(c);
        }
        comboKeyCol.setAllItems(commonCols);
        if (comboKeyCol.getItemCount() > 0) {
            comboKeyCol.setSelectedIndex(0);
        }
        updateClauseDropdowns();
    }

    public void updateClauseDropdowns() {
        String tA = (String) comboTableA.getSelectedItem();
        String tB = (String) comboTableB.getSelectedItem();
        DatabaseEntry dbA = getSelectedDatabaseA();
        DatabaseEntry dbB = getSelectedDatabaseB();

        List<String> colsA = (tA != null && dbA != null && dbA.getDbManager().isConnected()) ? dbA.getDbManager().getColumnNames(tA) : Collections.emptyList();
        List<String> colsB = (tB != null && dbB != null && dbB.getDbManager().isConnected()) ? dbB.getDbManager().getColumnNames(tB) : Collections.emptyList();

        Set<String> allCols = new LinkedHashSet<>();
        allCols.addAll(colsA);
        allCols.addAll(colsB);

        List<String> tablesB = (dbB != null && dbB.getDbManager().isConnected()) ? dbB.getDbManager().getTableNames() : Collections.emptyList();
        List<String> allColsList = new ArrayList<>(allCols);

        for (DualWhereRow wr : whereRows) wr.updateAvailableColumns(allColsList);
        for (DualOrderByRow or : orderByRows) or.updateAvailableColumns(allColsList);
        for (DualAggregateRow ar : aggregateRows) ar.updateAvailableColumns(allColsList);
        for (DualGroupByRow gr : groupByRows) gr.updateAvailableColumns(allColsList);
        for (DualHavingRow hr : havingRows) hr.updateAvailableColumns(allColsList);
        for (DualWindowFuncRow wr : windowFuncRows) wr.updateAvailableColumns(allColsList);
        for (DualJoinRow jr : joinRows) {
            jr.updateLeftColumns(colsA);
            jr.updateAvailableTables(tablesB);
        }
    }

    private void updateTabTitle() {
        if (tabTitleLabel == null) return;
        String tA = (String) comboTableA.getSelectedItem();
        String tB = (String) comboTableB.getSelectedItem();
        if (tA != null && !tA.isEmpty()) {
            if (tB != null && !tB.isEmpty() && !tB.equalsIgnoreCase(tA)) {
                tabTitleLabel.setText(tA + " ➔ " + tB);
            } else {
                tabTitleLabel.setText(tA);
            }
        }
    }

    public void copyStateFrom(DataCompareTabPanel other) {
        if (other == null) return;
        if (other.getSelectedDatabaseA() != null) comboDbA.setSelectedItem(other.getSelectedDatabaseA());
        if (other.getSelectedDatabaseB() != null) comboDbB.setSelectedItem(other.getSelectedDatabaseB());
        if (other.comboTableA.getSelectedItem() != null) comboTableA.setSelectedItem(other.comboTableA.getSelectedItem());
        if (other.comboTableB.getSelectedItem() != null) comboTableB.setSelectedItem(other.comboTableB.getSelectedItem());
        if (other.comboKeyCol.getSelectedItem() != null) comboKeyCol.setSelectedItem(other.comboKeyCol.getSelectedItem());
        chkDistinct.setSelected(other.chkDistinct.isSelected());
        txtLimit.setText(other.txtLimit.getText());
        txtOffset.setText(other.txtOffset.getText());
        selectedDataColumns.clear();
        selectedDataColumns.addAll(other.selectedDataColumns);
        btnSelectCols.setText(other.btnSelectCols.getText());
        if (other.comboSample.getSelectedItem() != null) comboSample.setSelectedItem(other.comboSample.getSelectedItem());
        if (other.comboLock.getSelectedItem() != null) comboLock.setSelectedItem(other.comboLock.getSelectedItem());
        queryAArea.setText(other.queryAArea.getText());
        queryBArea.setText(other.queryBArea.getText());
        updateTabTitle();
    }

    // ==========================================
    // Clause Management
    // ==========================================
    private void addAggregateRow() {
        DualAggregateRow row = new DualAggregateRow();
        aggregateRows.add(row);
        aggregateContainer.add(row);
        updateClauseDropdowns();
        clausesScrollPane.setVisible(true);
        revalidate(); repaint();
    }

    private void removeAggregateRow(DualAggregateRow row) {
        aggregateRows.remove(row);
        aggregateContainer.remove(row);
        checkClausesVisibility();
        revalidate(); repaint();
    }

    private void addGroupByRow() {
        DualGroupByRow row = new DualGroupByRow();
        groupByRows.add(row);
        groupByContainer.add(row);
        updateClauseDropdowns();
        clausesScrollPane.setVisible(true);
        revalidate(); repaint();
    }

    private void removeGroupByRow(DualGroupByRow row) {
        groupByRows.remove(row);
        groupByContainer.remove(row);
        checkClausesVisibility();
        revalidate(); repaint();
    }

    private void addHavingRow() {
        boolean isFirst = havingRows.isEmpty();
        DualHavingRow row = new DualHavingRow(isFirst);
        havingRows.add(row);
        havingContainer.add(row);
        updateClauseDropdowns();
        clausesScrollPane.setVisible(true);
        revalidate(); repaint();
    }

    private void removeHavingRow(DualHavingRow row) {
        havingRows.remove(row);
        havingContainer.remove(row);
        if (!havingRows.isEmpty()) havingRows.get(0).setFirst(true);
        checkClausesVisibility();
        revalidate(); repaint();
    }

    private void addWindowFuncRow() {
        DualWindowFuncRow row = new DualWindowFuncRow();
        windowFuncRows.add(row);
        windowFuncContainer.add(row);
        updateClauseDropdowns();
        clausesScrollPane.setVisible(true);
        revalidate(); repaint();
    }

    private void removeWindowFuncRow(DualWindowFuncRow row) {
        windowFuncRows.remove(row);
        windowFuncContainer.remove(row);
        checkClausesVisibility();
        revalidate(); repaint();
    }

    private void addWhereRow() {
        boolean isFirst = whereRows.isEmpty();
        DualWhereRow row = new DualWhereRow(isFirst);
        whereRows.add(row);
        whereContainer.add(row);
        updateClauseDropdowns();
        clausesScrollPane.setVisible(true);
        revalidate(); repaint();
    }

    private void removeWhereRow(DualWhereRow row) {
        whereRows.remove(row);
        whereContainer.remove(row);
        if (!whereRows.isEmpty()) whereRows.get(0).setFirst(true);
        checkClausesVisibility();
        revalidate(); repaint();
    }

    private void addJoinRow() {
        DualJoinRow row = new DualJoinRow();
        joinRows.add(row);
        joinsContainer.add(row);
        updateClauseDropdowns();
        clausesScrollPane.setVisible(true);
        revalidate(); repaint();
    }

    private void removeJoinRow(DualJoinRow row) {
        joinRows.remove(row);
        joinsContainer.remove(row);
        checkClausesVisibility();
        revalidate(); repaint();
    }

    private void addOrderByRow() {
        DualOrderByRow row = new DualOrderByRow();
        orderByRows.add(row);
        orderByContainer.add(row);
        updateClauseDropdowns();
        clausesScrollPane.setVisible(true);
        revalidate(); repaint();
    }

    private void removeOrderByRow(DualOrderByRow row) {
        orderByRows.remove(row);
        orderByContainer.remove(row);
        checkClausesVisibility();
        revalidate(); repaint();
    }

    private void clearAllClauses() {
        whereRows.clear();
        whereContainer.removeAll();
        joinRows.clear();
        joinsContainer.removeAll();
        orderByRows.clear();
        orderByContainer.removeAll();
        aggregateRows.clear();
        aggregateContainer.removeAll();
        groupByRows.clear();
        groupByContainer.removeAll();
        havingRows.clear();
        havingContainer.removeAll();
        windowFuncRows.clear();
        windowFuncContainer.removeAll();
        checkClausesVisibility();
        revalidate(); repaint();
    }

    private void checkClausesVisibility() {
        boolean hasAny = !whereRows.isEmpty() || !joinRows.isEmpty() || !orderByRows.isEmpty()
                || !aggregateRows.isEmpty() || !groupByRows.isEmpty() || !havingRows.isEmpty() || !windowFuncRows.isEmpty();
        clausesScrollPane.setVisible(hasAny);
    }

    public void showExplainMenu(JComponent invoker) {
        DatabaseEntry dbA = getSelectedDatabaseA();
        DatabaseEntry dbB = getSelectedDatabaseB();
        String tA = (String) comboTableA.getSelectedItem();
        String tB = (String) comboTableB.getSelectedItem();
        String keyCol = (String) comboKeyCol.getSelectedItem();

        String sqlA = (dbA != null && tA != null) ? buildQueryForDatabase(dbA.getDbManager(), tA, keyCol) : "";
        String sqlB = (dbB != null && tB != null) ? buildQueryForDatabase(dbB.getDbManager(), tB, keyCol) : "";

        JPopupMenu menu = new JPopupMenu();
        String[] options = {
            "EXPLAIN",
            "EXPLAIN (ANALYZE, BUFFERS)",
            "EXPLAIN (FORMAT JSON)",
            "EXPLAIN (ANALYZE, FORMAT JSON)"
        };
        for (String opt : options) {
            JMenuItem itemA = new JMenuItem("🔍 " + opt + " [Source DB]");
            itemA.addActionListener(e -> promptExplainAction(opt + " " + sqlA, "Source Database (" + (dbA != null ? dbA.getLabel() : "A") + ")"));
            menu.add(itemA);

            JMenuItem itemB = new JMenuItem("🔍 " + opt + " [Target DB]");
            itemB.addActionListener(e -> promptExplainAction(opt + " " + sqlB, "Target Database (" + (dbB != null ? dbB.getLabel() : "B") + ")"));
            menu.add(itemB);
            menu.addSeparator();
        }
        menu.show(invoker, 0, invoker.getHeight());
    }

    private void promptExplainAction(String explainSql, String dbLabel) {
        if (explainSql.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select tables first.");
            return;
        }
        int choice = JOptionPane.showOptionDialog(this,
            "Execution Plan Query (" + dbLabel + "):\n\n" + explainSql + "\n\nChoose an action:",
            "Explain Execution Plan",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            new String[]{"Load into Custom SQL", "Copy to Clipboard", "Cancel"},
            "Load into Custom SQL");
        if (choice == 0) {
            radioCustomSql.setSelected(true);
            quickTableContainer.setVisible(false);
            customSqlPanel.setVisible(true);
            queryAArea.setText(explainSql);
            queryBArea.setText(explainSql);
            revalidate(); repaint();
        } else if (choice == 1) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(explainSql), null);
            JOptionPane.showMessageDialog(this, "EXPLAIN query copied to clipboard!");
        }
    }

    // ==========================================
    // Query Builder & SQL Preview
    // ==========================================
    public String buildQueryForDatabase(DatabaseManager dbm, String mainTable, String keyCol) {
        if (mainTable == null || mainTable.trim().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        if (chkDistinct.isSelected()) sb.append("DISTINCT ");

        List<String> selectParts = new ArrayList<>();
        if (!selectedDataColumns.isEmpty()) {
            Set<String> cols = new LinkedHashSet<>();
            if (keyCol != null && !keyCol.isEmpty()) cols.add(keyCol);
            cols.addAll(selectedDataColumns);
            for (String c : cols) selectParts.add(dbm.quoteColumnName(c));
        } else if (!groupByRows.isEmpty()) {
            for (DualGroupByRow gr : groupByRows) {
                String g = gr.buildSql(dbm);
                if (!g.isEmpty() && !selectParts.contains(g)) selectParts.add(g);
            }
        }

        for (DualAggregateRow ar : aggregateRows) {
            String aSql = ar.buildSql(dbm);
            if (!aSql.isEmpty()) selectParts.add(aSql);
        }

        for (DualWindowFuncRow wr : windowFuncRows) {
            String wSql = wr.buildSql(dbm);
            if (!wSql.isEmpty()) selectParts.add(wSql);
        }

        if (selectParts.isEmpty()) {
            sb.append("*");
        } else {
            sb.append(String.join(", ", selectParts));
        }

        sb.append(" FROM ").append(dbm.quoteTableName(mainTable));

        String sampleChoice = (String) comboSample.getSelectedItem();
        if (sampleChoice != null && !sampleChoice.equals("Sample: None")) {
            sb.append(" TABLESAMPLE ").append(sampleChoice.replace("Sample: ", ""));
        }

        for (DualJoinRow jr : joinRows) {
            String jSql = jr.buildSql(dbm, mainTable);
            if (!jSql.isEmpty()) sb.append(" ").append(jSql);
        }

        List<String> wList = new ArrayList<>();
        for (int i = 0; i < whereRows.size(); i++) {
            DualWhereRow wr = whereRows.get(i);
            String cond = wr.buildCondition(dbm);
            if (!cond.isEmpty()) {
                if (wList.isEmpty()) {
                    wList.add(cond);
                } else {
                    String log = wr.getLogic();
                    wList.add((log != null ? log : "AND") + " " + cond);
                }
            }
        }
        if (!wList.isEmpty()) sb.append(" WHERE ").append(String.join(" ", wList));

        List<String> gList = new ArrayList<>();
        for (DualGroupByRow gr : groupByRows) {
            String gStr = gr.buildSql(dbm);
            if (!gStr.isEmpty()) gList.add(gStr);
        }
        if (!gList.isEmpty()) sb.append(" GROUP BY ").append(String.join(", ", gList));

        List<String> hList = new ArrayList<>();
        for (DualHavingRow hr : havingRows) {
            String hStr = hr.buildSql(dbm);
            if (!hStr.isEmpty()) {
                if (hList.isEmpty()) {
                    hList.add(hStr);
                } else {
                    hList.add(hr.getLogic() + " " + hStr);
                }
            }
        }
        if (!hList.isEmpty()) sb.append(" HAVING ").append(String.join(" ", hList));

        List<String> oList = new ArrayList<>();
        for (DualOrderByRow or : orderByRows) {
            String oStr = or.buildSql(dbm);
            if (!oStr.isEmpty()) oList.add(oStr);
        }
        if (!oList.isEmpty()) sb.append(" ORDER BY ").append(String.join(", ", oList));

        String lim = txtLimit.getText().trim();
        if (!lim.isEmpty()) {
            try { sb.append(" LIMIT ").append(Integer.parseInt(lim)); } catch (NumberFormatException ignored) {}
        }
        String off = txtOffset.getText().trim();
        if (!off.isEmpty()) {
            try { sb.append(" OFFSET ").append(Integer.parseInt(off)); } catch (NumberFormatException ignored) {}
        }

        String lockChoice = (String) comboLock.getSelectedItem();
        if (lockChoice != null && !lockChoice.equals("Lock: None")) {
            sb.append(" ").append(lockChoice.replace("Lock: ", ""));
        }

        return sb.toString();
    }

    private void previewSqlQueries() {
        DatabaseEntry dbA = getSelectedDatabaseA();
        DatabaseEntry dbB = getSelectedDatabaseB();
        if (dbA == null || dbB == null) return;

        String tA = (String) comboTableA.getSelectedItem();
        String tB = (String) comboTableB.getSelectedItem();
        String keyCol = (String) comboKeyCol.getSelectedItem();

        String sqlA = buildQueryForDatabase(dbA.getDbManager(), tA != null ? tA : "table_a", keyCol);
        String sqlB = buildQueryForDatabase(dbB.getDbManager(), tB != null ? tB : "table_b", keyCol);

        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Generated Comparison SQL Queries", true);
        dlg.setSize(820, 500);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(8, 8));

        RSyntaxTextArea areaA = new RSyntaxTextArea(sqlA);
        areaA.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        areaA.setEditable(false);

        RSyntaxTextArea areaB = new RSyntaxTextArea(sqlB);
        areaB.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        areaB.setEditable(false);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapQueryEditor("Query for " + dbA.getLabel(), areaA),
                wrapQueryEditor("Query for " + dbB.getLabel(), areaB));
        split.setResizeWeight(0.5);
        dlg.add(split, BorderLayout.CENTER);

        JPanel btnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));

        JButton copyABtn = new JButton("Copy Query " + dbA.getId());
        copyABtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sqlA), null);
            JOptionPane.showMessageDialog(dlg, "Query A copied to clipboard!");
        });
        btnP.add(copyABtn);

        JButton copyBBtn = new JButton("Copy Query " + dbB.getId());
        copyBBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sqlB), null);
            JOptionPane.showMessageDialog(dlg, "Query B copied to clipboard!");
        });
        btnP.add(copyBBtn);

        JButton openCustomBtn = new JButton("Open in Custom SQL Mode");
        openCustomBtn.addActionListener(e -> {
            queryAArea.setText(sqlA);
            queryBArea.setText(sqlB);
            radioCustomSql.setSelected(true);
            quickTableContainer.setVisible(false);
            customSqlPanel.setVisible(true);
            revalidate(); repaint();
            dlg.dispose();
        });
        btnP.add(openCustomBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        btnP.add(closeBtn);

        dlg.add(btnP, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void openColumnSelectionDialog() {
        String tA = (String) comboTableA.getSelectedItem();
        String tB = (String) comboTableB.getSelectedItem();
        DatabaseEntry dbA = getSelectedDatabaseA();
        DatabaseEntry dbB = getSelectedDatabaseB();

        if (tA == null || tB == null || dbA == null || dbB == null) {
            JOptionPane.showMessageDialog(this, "Please select Table A and Table B first.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String> colsA = dbA.getDbManager().isConnected() ? dbA.getDbManager().getColumnNames(tA) : Collections.emptyList();
        List<String> colsB = dbB.getDbManager().isConnected() ? dbB.getDbManager().getColumnNames(tB) : Collections.emptyList();

        Set<String> allCols = new LinkedHashSet<>();
        allCols.addAll(colsA);
        allCols.addAll(colsB);

        if (allCols.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No columns found for the selected tables.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Select Columns to Compare", true);
        dlg.setSize(440, 480);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel checkListPanel = new JPanel();
        checkListPanel.setLayout(new BoxLayout(checkListPanel, BoxLayout.Y_AXIS));
        checkListPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        Map<String, JCheckBox> checkMap = new LinkedHashMap<>();
        String currentKey = (String) comboKeyCol.getSelectedItem();

        for (String c : allCols) {
            boolean isKey = c.equalsIgnoreCase(currentKey);
            boolean initiallySelected = selectedDataColumns.isEmpty() || selectedDataColumns.contains(c) || isKey;
            JCheckBox cb = new JCheckBox(c + (isKey ? "  (Key Column - Required)" : ""), initiallySelected);
            if (isKey) {
                cb.setEnabled(false);
                cb.setSelected(true);
            }
            checkMap.put(c, cb);
            checkListPanel.add(cb);
        }

        JScrollPane scroll = new JScrollPane(checkListPanel);
        dlg.add(scroll, BorderLayout.CENTER);

        JPanel topQuickBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton selAllBtn = new JButton("Select All");
        selAllBtn.addActionListener(e -> {
            for (JCheckBox cb : checkMap.values()) cb.setSelected(true);
        });
        JButton deselAllBtn = new JButton("Deselect All");
        deselAllBtn.addActionListener(e -> {
            for (Map.Entry<String, JCheckBox> entry : checkMap.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(currentKey)) entry.getValue().setSelected(false);
            }
        });
        JButton excludeTimestampsBtn = new JButton("Exclude Timestamps");
        excludeTimestampsBtn.addActionListener(e -> {
            for (Map.Entry<String, JCheckBox> entry : checkMap.entrySet()) {
                String name = entry.getKey().toLowerCase();
                if (name.contains("created") || name.contains("updated") || name.contains("timestamp") || name.contains("modified")) {
                    entry.getValue().setSelected(false);
                }
            }
        });
        topQuickBtns.add(selAllBtn);
        topQuickBtns.add(deselAllBtn);
        topQuickBtns.add(excludeTimestampsBtn);
        dlg.add(topQuickBtns, BorderLayout.NORTH);

        JPanel bottomP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> {
            selectedDataColumns.clear();
            int selectedCount = 0;
            for (Map.Entry<String, JCheckBox> entry : checkMap.entrySet()) {
                if (entry.getValue().isSelected()) {
                    selectedDataColumns.add(entry.getKey());
                    selectedCount++;
                }
            }
            if (selectedCount >= allCols.size()) {
                selectedDataColumns.clear();
                btnSelectCols.setText("Columns (All)");
            } else {
                btnSelectCols.setText("Columns (" + selectedCount + "/" + allCols.size() + ")");
            }
            dlg.dispose();
        });
        bottomP.add(applyBtn);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());
        bottomP.add(cancelBtn);

        dlg.add(bottomP, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void runDataComparison() {
        DatabaseEntry dbA = getSelectedDatabaseA();
        DatabaseEntry dbB = getSelectedDatabaseB();

        if (dbA == null || !dbA.getDbManager().isConnected() || dbB == null || !dbB.getDbManager().isConnected()) {
            JOptionPane.showMessageDialog(this, "Please ensure both selected databases are connected.", "Missing Connections", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String keyCol = (String) comboKeyCol.getSelectedItem();
        if (keyCol == null || keyCol.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a Key Column to match records between databases.", "Missing Key Column", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sqlA, sqlB;
        if (radioQuickTable.isSelected()) {
            String tA = (String) comboTableA.getSelectedItem();
            String tB = (String) comboTableB.getSelectedItem();
            if (tA == null || tB == null) return;
            sqlA = buildQueryForDatabase(dbA.getDbManager(), tA, keyCol);
            sqlB = buildQueryForDatabase(dbB.getDbManager(), tB, keyCol);
        } else {
            sqlA = queryAArea.getText().trim();
            sqlB = queryBArea.getText().trim();
        }

        executeDataDiffBtn.setEnabled(false);
        executeDataDiffBtn.setText("Executing...");
        dataStatusLabel.setText("Executing comparison between " + dbA.getLabel() + " and " + dbB.getLabel() + "...");

        new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                DefaultTableModel modelA = dbA.getDbManager().executeQuery(sqlA);
                DefaultTableModel modelB = dbB.getDbManager().executeQuery(sqlB);

                SwingUtilities.invokeLater(() -> {
                    executeDataDiffBtn.setEnabled(true);
                    executeDataDiffBtn.setText("▶ Execute & Compare Data");

                    sideTableA.setModel(modelA);
                    sideTableB.setModel(modelB);

                    computeUnifiedDataDiff(modelA, modelB, keyCol, System.currentTimeMillis() - start, dbA.getLabel(), dbB.getLabel());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    executeDataDiffBtn.setEnabled(true);
                    executeDataDiffBtn.setText("▶ Execute & Compare Data");
                    dataStatusLabel.setText("Data diff failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Failed to execute queries: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void computeUnifiedDataDiff(DefaultTableModel modelA, DefaultTableModel modelB, String keyCol, long durationMs, String labelA, String labelB) {
        int keyIdxA = -1;
        int keyIdxB = -1;

        List<String> colsA = new ArrayList<>();
        for (int c = 0; c < modelA.getColumnCount(); c++) {
            String colName = modelA.getColumnName(c);
            colsA.add(colName);
            if (colName.equalsIgnoreCase(keyCol)) keyIdxA = c;
        }

        List<String> colsB = new ArrayList<>();
        for (int c = 0; c < modelB.getColumnCount(); c++) {
            String colName = modelB.getColumnName(c);
            colsB.add(colName);
            if (colName.equalsIgnoreCase(keyCol)) keyIdxB = c;
        }

        if (keyIdxA == -1 || keyIdxB == -1) {
            JOptionPane.showMessageDialog(this, "Key column '" + keyCol + "' not found in query results.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> unifiedColumns = new ArrayList<>();
        unifiedColumns.add("Diff Status");
        unifiedColumns.add(keyCol);
        for (String c : colsA) {
            if (!c.equalsIgnoreCase(keyCol) && !unifiedColumns.contains(c)) unifiedColumns.add(c);
        }
        for (String c : colsB) {
            if (!c.equalsIgnoreCase(keyCol) && !unifiedColumns.contains(c)) unifiedColumns.add(c);
        }

        unifiedDiffModel = new DefaultTableModel(unifiedColumns.toArray(new String[0]), 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        Map<String, Vector<Object>> mapB = new LinkedHashMap<>();
        for (int r = 0; r < modelB.getRowCount(); r++) {
            Object k = modelB.getValueAt(r, keyIdxB);
            if (k != null) {
                Vector<Object> row = new Vector<>();
                for (int c = 0; c < modelB.getColumnCount(); c++) row.add(modelB.getValueAt(r, c));
                mapB.put(k.toString(), row);
            }
        }

        int countIdent = 0, countMod = 0, countOnlyA = 0, countOnlyB = 0;

        for (int r = 0; r < modelA.getRowCount(); r++) {
            Object k = modelA.getValueAt(r, keyIdxA);
            String kStr = k != null ? k.toString() : "";
            Vector<Object> rowB = mapB.remove(kStr);

            Vector<Object> outRow = new Vector<>();
            if (rowB == null) {
                outRow.add("🟥 IN " + labelA.toUpperCase() + " ONLY");
                outRow.add(k);
                for (int c = 2; c < unifiedColumns.size(); c++) {
                    String colName = unifiedColumns.get(c);
                    int idxA = colsA.indexOf(colName);
                    outRow.add(idxA >= 0 ? modelA.getValueAt(r, idxA) : "-");
                }
                countOnlyA++;
            } else {
                boolean isModified = false;
                List<Object> cellValues = new ArrayList<>();

                for (int c = 2; c < unifiedColumns.size(); c++) {
                    String colName = unifiedColumns.get(c);
                    int idxA = colsA.indexOf(colName);
                    int idxB = colsB.indexOf(colName);

                    Object valA = idxA >= 0 ? modelA.getValueAt(r, idxA) : null;
                    Object valB = idxB >= 0 ? rowB.get(idxB) : null;

                    String strA = valA != null ? valA.toString() : "";
                    String strB = valB != null ? valB.toString() : "";

                    if (!strA.equals(strB)) {
                        isModified = true;
                        cellValues.add(strA + " ➔ " + strB);
                    } else {
                        cellValues.add(valA);
                    }
                }

                if (isModified) {
                    outRow.add("🟨 MODIFIED");
                    countMod++;
                } else {
                    outRow.add("🟢 IDENTICAL");
                    countIdent++;
                }
                outRow.add(k);
                outRow.addAll(cellValues);
            }
            unifiedDiffModel.addRow(outRow);
        }

        for (Map.Entry<String, Vector<Object>> entry : mapB.entrySet()) {
            Vector<Object> outRow = new Vector<>();
            outRow.add("🟩 IN " + labelB.toUpperCase() + " ONLY");
            outRow.add(entry.getKey());

            Vector<Object> rowB = entry.getValue();
            for (int c = 2; c < unifiedColumns.size(); c++) {
                String colName = unifiedColumns.get(c);
                int idxB = colsB.indexOf(colName);
                outRow.add(idxB >= 0 ? rowB.get(idxB) : "-");
            }
            unifiedDiffModel.addRow(outRow);
            countOnlyB++;
        }

        unifiedDiffSorter = new TableRowSorter<>(unifiedDiffModel);
        unifiedDiffTable.setModel(unifiedDiffModel);
        unifiedDiffTable.setRowSorter(unifiedDiffSorter);

        dataStatusLabel.setText(String.format("Compared in %d ms:  🟢 %d Identical | 🟨 %d Modified | 🟥 %d Only in %s | 🟩 %d Only in %s",
                durationMs, countIdent, countMod, countOnlyA, labelA, countOnlyB, labelB));
    }

    private void applyDataFilter() {
        if (unifiedDiffSorter == null) return;
        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        if (btnDiffOnlyData.isSelected()) {
            filters.add(RowFilter.notFilter(RowFilter.regexFilter("(?i)🟢 IDENTICAL", 0)));
        } else if (btnModData.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)🟨 MODIFIED", 0));
        } else if (btnOnlyAData.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)🟥 IN .* ONLY", 0));
        } else if (btnOnlyBData.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)🟩 IN .* ONLY", 0));
        }

        String search = dataSearchField.getText().trim();
        if (!search.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + search));
        }

        if (filters.isEmpty()) {
            unifiedDiffSorter.setRowFilter(null);
        } else {
            unifiedDiffSorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    // ==========================================
    // Inner Classes: Clause Rows
    // ==========================================

    class DualWhereRow extends JPanel {
        private final JComboBox<String> logicCombo = new JComboBox<>(new String[]{"AND", "OR"});
        private final SearchableComboBox<String> colCombo = new SearchableComboBox<>();
        private final JComboBox<String> opCombo = new JComboBox<>(new String[]{
            "=", "!=", ">", "<", ">=", "<=", "LIKE", "ILIKE", "IN", "NOT IN", "BETWEEN", "IS NULL", "IS NOT NULL"
        });
        private final JTextField valField = new JTextField(12);

        private final JComboBox<String> inTypeCombo = new JComboBox<>(new String[]{"Distinct Values", "Subquery (SELECT)", "Manual Values"});
        private final SearchableComboBox<String> inValuesCombo = new SearchableComboBox<>();
        private final JButton inPickBtn = new JButton("Pick...");
        private final JButton subqueryBtn = new JButton("Clauses...");
        private final JPanel inPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        private final JButton delBtn = new JButton("✕");

        // Subquery State
        private String subTable = "";
        private String subCol = "";
        private boolean subDistinct = false;
        private String subWhere = "";
        private String subOrderBy = "";
        private String subLimit = "";

        public DualWhereRow(boolean isFirst) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 3, 2));
            logicCombo.setPreferredSize(new Dimension(65, 26));
            colCombo.setPreferredSize(new Dimension(140, 26));
            opCombo.setPreferredSize(new Dimension(95, 26));
            valField.setPreferredSize(new Dimension(130, 26));
            inTypeCombo.setPreferredSize(new Dimension(130, 26));
            inValuesCombo.setPreferredSize(new Dimension(130, 26));
            delBtn.setPreferredSize(new Dimension(38, 24));
            delBtn.setMargin(new Insets(1, 4, 1, 4));

            inPanel.setOpaque(false);
            inPanel.setVisible(false);
            inTypeCombo.setVisible(false);

            inPanel.add(inValuesCombo);
            inPanel.add(inPickBtn);
            inPanel.add(subqueryBtn);
            subqueryBtn.setVisible(false);

            setFirst(isFirst);

            add(colCombo);
            add(opCombo);
            add(inTypeCombo);
            add(inPanel);
            add(valField);
            add(delBtn);

            opCombo.addActionListener(e -> updateOpLayout());
            inTypeCombo.addActionListener(e -> updateInLayout());

            inValuesCombo.addActionListener(e -> {
                if (inValuesCombo.isAdjusting) return;
                Object sel = inValuesCombo.getSelectedItem();
                if (sel != null && !sel.toString().trim().isEmpty()) {
                    String formatted = formatValue(sel.toString().trim());
                    String cur = valField.getText().trim();
                    if (cur.isEmpty()) valField.setText(formatted);
                    else if (!cur.contains(formatted)) valField.setText(cur + ", " + formatted);
                }
            });

            inPickBtn.addActionListener(e -> openMultiSelectValuesDialog());
            subqueryBtn.addActionListener(e -> openSubqueryDialog());
            delBtn.addActionListener(e -> removeWhereRow(this));
        }

        public void setFirst(boolean isFirst) {
            removeAll();
            if (!isFirst) add(logicCombo);
            else {
                JPanel spacer = new JPanel();
                spacer.setPreferredSize(new Dimension(65, 26));
                spacer.setOpaque(false);
                add(spacer);
            }
            add(colCombo);
            add(opCombo);
            add(inTypeCombo);
            add(inPanel);
            add(valField);
            add(delBtn);
            revalidate(); repaint();
        }

        public String getLogic() { return (String) logicCombo.getSelectedItem(); }

        public void updateAvailableColumns(List<String> cols) {
            Object cur = colCombo.getSelectedItem();
            colCombo.setAllItems(cols);
            if (cur != null && cols.contains(cur)) colCombo.setSelectedItem(cur);
            else if (colCombo.getItemCount() > 0) colCombo.setSelectedIndex(0);
        }

        private void updateOpLayout() {
            String op = (String) opCombo.getSelectedItem();
            boolean isNull = op != null && op.contains("NULL");
            boolean isIn = op != null && (op.equalsIgnoreCase("IN") || op.equalsIgnoreCase("NOT IN"));

            valField.setEnabled(!isNull);
            inTypeCombo.setVisible(isIn);
            inPanel.setVisible(isIn);

            if (isIn) {
                updateInLayout();
                loadDistinctValues();
            } else {
                valField.setVisible(true);
            }
            revalidate(); repaint();
        }

        private void updateInLayout() {
            String type = (String) inTypeCombo.getSelectedItem();
            if ("Distinct Values".equalsIgnoreCase(type)) {
                inValuesCombo.setVisible(true);
                inPickBtn.setVisible(true);
                subqueryBtn.setVisible(false);
                valField.setVisible(true);
            } else if ("Subquery (SELECT)".equalsIgnoreCase(type)) {
                inValuesCombo.setVisible(false);
                inPickBtn.setVisible(false);
                subqueryBtn.setVisible(true);
                valField.setVisible(false);
            } else {
                inValuesCombo.setVisible(false);
                inPickBtn.setVisible(false);
                subqueryBtn.setVisible(false);
                valField.setVisible(true);
            }
            revalidate(); repaint();
        }

        private void loadDistinctValues() {
            String col = (String) colCombo.getSelectedItem();
            String tbl = (String) comboTableA.getSelectedItem();
            DatabaseEntry dbA = getSelectedDatabaseA();
            if (col == null || tbl == null || dbA == null || !dbA.getDbManager().isConnected()) return;

            new Thread(() -> {
                try {
                    String sql = "SELECT DISTINCT " + dbA.getDbManager().quoteColumnName(col) + " FROM " + dbA.getDbManager().quoteTableName(tbl) + " WHERE " + dbA.getDbManager().quoteColumnName(col) + " IS NOT NULL ORDER BY 1 LIMIT 200;";
                    DefaultTableModel model = dbA.getDbManager().executeQuery(sql);
                    List<String> distVals = new ArrayList<>();
                    for (int r = 0; r < model.getRowCount(); r++) {
                        Object v = model.getValueAt(r, 0);
                        if (v != null) distVals.add(v.toString());
                    }
                    SwingUtilities.invokeLater(() -> inValuesCombo.setAllItems(distVals));
                } catch (Exception ignored) {}
            }).start();
        }

        private void openMultiSelectValuesDialog() {
            List<String> items = inValuesCombo.getAllItems();
            if (items.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No distinct values available. Enter values manually.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Select Distinct Values", true);
            dlg.setSize(380, 420);
            dlg.setLocationRelativeTo(this);
            dlg.setLayout(new BorderLayout());

            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            Map<String, JCheckBox> cbs = new LinkedHashMap<>();

            String curVal = valField.getText();
            for (String item : items) {
                boolean sel = curVal.contains("'" + item + "'") || curVal.contains(item);
                JCheckBox cb = new JCheckBox(item, sel);
                cbs.put(item, cb);
                p.add(cb);
            }

            dlg.add(new JScrollPane(p), BorderLayout.CENTER);

            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = new JButton("Apply");
            okBtn.addActionListener(e -> {
                List<String> picked = new ArrayList<>();
                for (Map.Entry<String, JCheckBox> entry : cbs.entrySet()) {
                    if (entry.getValue().isSelected()) picked.add(formatValue(entry.getKey()));
                }
                valField.setText(String.join(", ", picked));
                dlg.dispose();
            });
            bp.add(okBtn);
            dlg.add(bp, BorderLayout.SOUTH);
            dlg.setVisible(true);
        }

        private void openSubqueryDialog() {
            JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Subquery Clauses Builder", true);
            dlg.setSize(620, 460);
            dlg.setLocationRelativeTo(this);
            dlg.setLayout(new BorderLayout(8, 8));

            JPanel form = new JPanel(new GridLayout(6, 2, 6, 6));
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            form.add(new JLabel("FROM Table:"));
            SearchableComboBox<String> subTblCombo = new SearchableComboBox<>();
            DatabaseEntry dbA = getSelectedDatabaseA();
            List<String> tbls = (dbA != null && dbA.getDbManager().isConnected()) ? dbA.getDbManager().getTableNames() : Collections.emptyList();
            subTblCombo.setAllItems(tbls);
            if (!subTable.isEmpty() && tbls.contains(subTable)) subTblCombo.setSelectedItem(subTable);
            else if (!tbls.isEmpty()) subTblCombo.setSelectedIndex(0);
            form.add(subTblCombo);

            form.add(new JLabel("SELECT Column:"));
            SearchableComboBox<String> subColCombo = new SearchableComboBox<>();
            form.add(subColCombo);

            Runnable loadCols = () -> {
                String t = (String) subTblCombo.getSelectedItem();
                if (t != null && dbA != null && dbA.getDbManager().isConnected()) {
                    List<String> cList = dbA.getDbManager().getColumnNames(t);
                    subColCombo.setAllItems(cList);
                    if (!subCol.isEmpty() && cList.contains(subCol)) subColCombo.setSelectedItem(subCol);
                    else if (!cList.isEmpty()) subColCombo.setSelectedIndex(0);
                }
            };
            subTblCombo.addActionListener(e -> loadCols.run());
            loadCols.run();

            form.add(new JLabel("DISTINCT:"));
            JCheckBox subDistCb = new JCheckBox("SELECT DISTINCT", subDistinct);
            form.add(subDistCb);

            form.add(new JLabel("WHERE Clause:"));
            JTextField subWhereField = new JTextField(subWhere);
            form.add(subWhereField);

            form.add(new JLabel("ORDER BY:"));
            JTextField subOrderField = new JTextField(subOrderBy);
            form.add(subOrderField);

            form.add(new JLabel("LIMIT:"));
            JTextField subLimitField = new JTextField(subLimit);
            form.add(subLimitField);

            dlg.add(form, BorderLayout.CENTER);

            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton insertTplBtn = new JButton("📝 Insert Template ▾");
            insertTplBtn.setToolTipText("Insert advanced subquery template from SQL Mastery Library");
            insertTplBtn.addActionListener(e -> {
                JPopupMenu snippetMenu = new JPopupMenu();
                com.dbtool.util.SqlSnippetsLibrary.populatePopupMenu(snippetMenu, sql -> {
                    subWhereField.setText(sql);
                });
                snippetMenu.show(insertTplBtn, 0, insertTplBtn.getHeight());
            });
            bp.add(insertTplBtn);
            JButton okBtn = new JButton("Apply Subquery");
            okBtn.addActionListener(e -> {
                subTable = (String) subTblCombo.getSelectedItem();
                subCol = (String) subColCombo.getSelectedItem();
                subDistinct = subDistCb.isSelected();
                subWhere = subWhereField.getText().trim();
                subOrderBy = subOrderField.getText().trim();
                subLimit = subLimitField.getText().trim();
                subqueryBtn.setText("⚙ Subquery (" + subTable + "." + subCol + ")");
                dlg.dispose();
            });
            bp.add(okBtn);
            dlg.add(bp, BorderLayout.SOUTH);
            dlg.setVisible(true);
        }

        public String buildCondition(DatabaseManager dbm) {
            String col = (String) colCombo.getSelectedItem();
            String op = (String) opCombo.getSelectedItem();
            if (col == null || col.trim().isEmpty() || op == null) return "";

            String quotedCol = dbm.quoteColumnName(col);
            if (op.contains("NULL")) return quotedCol + " " + op;

            if (op.equalsIgnoreCase("IN") || op.equalsIgnoreCase("NOT IN")) {
                String type = (String) inTypeCombo.getSelectedItem();
                if ("Subquery (SELECT)".equalsIgnoreCase(type)) {
                    if (subTable == null || subTable.isEmpty() || subCol == null || subCol.isEmpty()) return "";
                    StringBuilder sb = new StringBuilder();
                    sb.append("SELECT ");
                    if (subDistinct) sb.append("DISTINCT ");
                    sb.append(dbm.quoteColumnName(subCol)).append(" FROM ").append(dbm.quoteTableName(subTable));
                    if (!subWhere.isEmpty()) sb.append(" WHERE ").append(subWhere);
                    if (!subOrderBy.isEmpty()) sb.append(" ORDER BY ").append(subOrderBy);
                    if (!subLimit.isEmpty()) sb.append(" LIMIT ").append(subLimit);
                    return quotedCol + " " + op + " (" + sb + ")";
                } else {
                    String v = valField.getText().trim();
                    if (v.isEmpty()) return "";
                    if (!v.startsWith("(") && !v.endsWith(")")) {
                        String[] parts = v.split(",");
                        List<String> qParts = new ArrayList<>();
                        for (String p : parts) qParts.add(formatValue(p.trim()));
                        return quotedCol + " " + op + " (" + String.join(", ", qParts) + ")";
                    }
                    return quotedCol + " " + op + " " + v;
                }
            }

            String val = valField.getText().trim();
            if (val.isEmpty()) return "";

            if (op.equalsIgnoreCase("LIKE") || op.equalsIgnoreCase("ILIKE")) {
                if (!val.contains("%")) val = "%" + val + "%";
                return quotedCol + " " + op + " '" + val.replace("'", "''") + "'";
            } else if (op.equalsIgnoreCase("BETWEEN")) {
                return quotedCol + " BETWEEN " + val;
            } else {
                return quotedCol + " " + op + " " + formatValue(val);
            }
        }

        private String formatValue(String raw) {
            if (raw == null) return "NULL";
            String tr = raw.trim();
            if (tr.startsWith("'") && tr.endsWith("'")) return tr;
            try {
                Double.parseDouble(tr);
                return tr;
            } catch (NumberFormatException ignored) {
                return "'" + tr.replace("'", "''") + "'";
            }
        }
    }

    class DualJoinRow extends JPanel {
        private final JComboBox<String> joinTypeCombo = new JComboBox<>(new String[]{
            "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "CROSS JOIN",
            "CROSS JOIN LATERAL", "CROSS APPLY", "OUTER APPLY"
        });
        private final SearchableComboBox<String> joinTableCombo = new SearchableComboBox<>();
        private final SearchableComboBox<String> leftColCombo = new SearchableComboBox<>();
        private final SearchableComboBox<String> rightColCombo = new SearchableComboBox<>();
        private final JButton delBtn = new JButton("X");

        public DualJoinRow() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            joinTypeCombo.setPreferredSize(new Dimension(150, 26));
            joinTableCombo.setPreferredSize(new Dimension(140, 26));
            leftColCombo.setPreferredSize(new Dimension(130, 26));
            rightColCombo.setPreferredSize(new Dimension(130, 26));
            delBtn.setPreferredSize(new Dimension(38, 24));
            delBtn.setMargin(new Insets(1, 4, 1, 4));

            add(new JLabel("Join: "));
            add(joinTypeCombo);
            add(joinTableCombo);
            add(new JLabel(" ON Base."));
            add(leftColCombo);
            add(new JLabel(" = Join."));
            add(rightColCombo);
            add(delBtn);

            joinTableCombo.addActionListener(e -> {
                String jt = (String) joinTableCombo.getSelectedItem();
                DatabaseEntry dbB = getSelectedDatabaseB();
                if (jt != null && !jt.isEmpty() && dbB != null && dbB.getDbManager().isConnected()) {
                    new Thread(() -> {
                        List<String> rCols = dbB.getDbManager().getColumnNames(jt);
                        SwingUtilities.invokeLater(() -> {
                            rightColCombo.setAllItems(rCols);
                            if (rightColCombo.getItemCount() > 0) rightColCombo.setSelectedIndex(0);
                        });
                    }).start();
                }
            });

            delBtn.addActionListener(e -> removeJoinRow(this));
        }

        public void updateAvailableTables(List<String> tables) {
            Object cur = joinTableCombo.getSelectedItem();
            joinTableCombo.setAllItems(tables);
            if (cur != null && tables.contains(cur)) joinTableCombo.setSelectedItem(cur);
        }

        public void updateLeftColumns(List<String> cols) {
            Object cur = leftColCombo.getSelectedItem();
            leftColCombo.setAllItems(cols);
            if (cur != null && cols.contains(cur)) leftColCombo.setSelectedItem(cur);
        }

        public String buildSql(DatabaseManager dbm, String baseTable) {
            String jType = (String) joinTypeCombo.getSelectedItem();
            String jTable = (String) joinTableCombo.getSelectedItem();
            if (jTable == null || jTable.trim().isEmpty()) return "";
            boolean isCrossOrApply = "CROSS JOIN".equalsIgnoreCase(jType)
                    || "CROSS JOIN LATERAL".equalsIgnoreCase(jType)
                    || "CROSS APPLY".equalsIgnoreCase(jType)
                    || "OUTER APPLY".equalsIgnoreCase(jType);
            if (isCrossOrApply) {
                return jType + " " + dbm.quoteTableName(jTable);
            }
            String lCol = (String) leftColCombo.getSelectedItem();
            String rCol = (String) rightColCombo.getSelectedItem();
            if (lCol == null || rCol == null || lCol.trim().isEmpty() || rCol.trim().isEmpty()) {
                return jType + " " + dbm.quoteTableName(jTable);
            }
            return jType + " " + dbm.quoteTableName(jTable) + " ON " +
                   dbm.quoteTableName(baseTable) + "." + dbm.quoteColumnName(lCol) + " = " +
                   dbm.quoteTableName(jTable) + "." + dbm.quoteColumnName(rCol);
        }
    }

    class DualOrderByRow extends JPanel {
        private final SearchableComboBox<String> colCombo = new SearchableComboBox<>();
        private final JComboBox<String> dirCombo = new JComboBox<>(new String[]{"ASC", "DESC"});
        private final JButton delBtn = new JButton("X");

        public DualOrderByRow() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            colCombo.setPreferredSize(new Dimension(150, 26));
            dirCombo.setPreferredSize(new Dimension(75, 26));
            delBtn.setPreferredSize(new Dimension(38, 24));
            delBtn.setMargin(new Insets(1, 4, 1, 4));

            add(new JLabel("Order By: "));
            add(colCombo);
            add(dirCombo);
            add(delBtn);

            delBtn.addActionListener(e -> removeOrderByRow(this));
        }

        public void updateAvailableColumns(List<String> cols) {
            Object cur = colCombo.getSelectedItem();
            colCombo.setAllItems(cols);
            if (cur != null && cols.contains(cur)) colCombo.setSelectedItem(cur);
        }

        public String buildSql(DatabaseManager dbm) {
            String col = (String) colCombo.getSelectedItem();
            String dir = (String) dirCombo.getSelectedItem();
            if (col == null || col.trim().isEmpty()) return "";
            return dbm.quoteColumnName(col) + " " + (dir != null ? dir : "ASC");
        }
    }

    class DualAggregateRow extends JPanel {
        private final JComboBox<String> funcCombo = new JComboBox<>(new String[]{
            "COUNT(*)", "COUNT(DISTINCT)", "SUM", "AVG", "MIN", "MAX"
        });
        private final SearchableComboBox<String> colCombo = new SearchableComboBox<>();
        private final JTextField aliasField = new JTextField(10);
        private final JTextField filterField = new JTextField(12);
        private final JButton delBtn = new JButton("X");

        public DualAggregateRow() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            funcCombo.setPreferredSize(new Dimension(130, 26));
            colCombo.setPreferredSize(new Dimension(130, 26));
            aliasField.setPreferredSize(new Dimension(90, 26));
            filterField.setPreferredSize(new Dimension(110, 26));
            filterField.setToolTipText("Optional FILTER clause: e.g. status = 'active' or salary > 50000");
            delBtn.setPreferredSize(new Dimension(38, 24));
            delBtn.setMargin(new Insets(1, 4, 1, 4));

            add(new JLabel("Agg: "));
            add(funcCombo);
            add(colCombo);
            add(new JLabel(" AS "));
            add(aliasField);
            add(new JLabel(" FILTER (WHERE "));
            add(filterField);
            add(new JLabel(") "));
            add(delBtn);

            funcCombo.addActionListener(e -> {
                String f = (String) funcCombo.getSelectedItem();
                colCombo.setEnabled(f != null && !f.equals("COUNT(*)"));
            });
            colCombo.setEnabled(false);

            delBtn.addActionListener(e -> removeAggregateRow(this));
        }

        public void updateAvailableColumns(List<String> cols) {
            Object cur = colCombo.getSelectedItem();
            colCombo.setAllItems(cols);
            if (cur != null && cols.contains(cur)) colCombo.setSelectedItem(cur);
        }

        public String buildSql(DatabaseManager dbm) {
            String f = (String) funcCombo.getSelectedItem();
            if (f == null) return "";
            StringBuilder sb = new StringBuilder();
            if (f.equals("COUNT(*)")) {
                sb.append("COUNT(*)");
            } else if (f.equals("COUNT(DISTINCT)")) {
                String c = (String) colCombo.getSelectedItem();
                if (c == null || c.trim().isEmpty()) return "";
                sb.append("COUNT(DISTINCT ").append(dbm.quoteColumnName(c)).append(")");
            } else {
                String c = (String) colCombo.getSelectedItem();
                if (c == null || c.trim().isEmpty()) return "";
                sb.append(f).append("(").append(dbm.quoteColumnName(c)).append(")");
            }
            String flt = filterField.getText().trim();
            if (!flt.isEmpty()) {
                sb.append(" FILTER (WHERE ").append(flt).append(")");
            }
            String al = aliasField.getText().trim();
            if (!al.isEmpty()) {
                sb.append(" AS ").append(dbm.quoteColumnName(al));
            }
            return sb.toString();
        }
    }

    class DualGroupByRow extends JPanel {
        private final SearchableComboBox<String> colCombo = new SearchableComboBox<>();
        private final JButton delBtn = new JButton("X");

        public DualGroupByRow() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            colCombo.setPreferredSize(new Dimension(150, 26));
            delBtn.setPreferredSize(new Dimension(38, 24));
            delBtn.setMargin(new Insets(1, 4, 1, 4));

            add(new JLabel("Group By: "));
            add(colCombo);
            add(delBtn);

            delBtn.addActionListener(e -> removeGroupByRow(this));
        }

        public void updateAvailableColumns(List<String> cols) {
            Object cur = colCombo.getSelectedItem();
            colCombo.setAllItems(cols);
            if (cur != null && cols.contains(cur)) colCombo.setSelectedItem(cur);
        }

        public String buildSql(DatabaseManager dbm) {
            String c = (String) colCombo.getSelectedItem();
            return (c != null && !c.trim().isEmpty()) ? dbm.quoteColumnName(c) : "";
        }
    }

    class DualHavingRow extends JPanel {
        private final JComboBox<String> logicCombo = new JComboBox<>(new String[]{"AND", "OR"});
        private final JComboBox<String> funcCombo = new JComboBox<>(new String[]{
            "COUNT(*)", "COUNT(col)", "SUM(col)", "AVG(col)", "MIN(col)", "MAX(col)"
        });
        private final SearchableComboBox<String> colCombo = new SearchableComboBox<>();
        private final JComboBox<String> opCombo = new JComboBox<>(new String[]{">", ">=", "<", "<=", "=", "<>"});
        private final JTextField valField = new JTextField(8);
        private final JButton delBtn = new JButton("X");
        private boolean isFirst = false;

        public DualHavingRow(boolean isFirst) {
            this.isFirst = isFirst;
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            logicCombo.setPreferredSize(new Dimension(65, 26));
            funcCombo.setPreferredSize(new Dimension(105, 26));
            colCombo.setPreferredSize(new Dimension(120, 26));
            opCombo.setPreferredSize(new Dimension(60, 26));
            valField.setPreferredSize(new Dimension(60, 26));
            delBtn.setPreferredSize(new Dimension(38, 24));
            delBtn.setMargin(new Insets(1, 4, 1, 4));

            if (!isFirst) add(logicCombo);
            add(new JLabel("Having: "));
            add(funcCombo);
            add(colCombo);
            add(opCombo);
            add(valField);
            add(delBtn);

            funcCombo.addActionListener(e -> {
                String f = (String) funcCombo.getSelectedItem();
                colCombo.setEnabled(f != null && !f.equals("COUNT(*)"));
            });
            colCombo.setEnabled(false);

            delBtn.addActionListener(e -> removeHavingRow(this));
        }

        public void setFirst(boolean isFirst) {
            if (this.isFirst != isFirst) {
                this.isFirst = isFirst;
                logicCombo.setVisible(!isFirst);
            }
        }

        public String getLogic() { return (String) logicCombo.getSelectedItem(); }

        public void updateAvailableColumns(List<String> cols) {
            Object cur = colCombo.getSelectedItem();
            colCombo.setAllItems(cols);
            if (cur != null && cols.contains(cur)) colCombo.setSelectedItem(cur);
        }

        public String buildSql(DatabaseManager dbm) {
            String f = (String) funcCombo.getSelectedItem();
            String op = (String) opCombo.getSelectedItem();
            String val = valField.getText().trim();
            if (f == null || op == null || val.isEmpty()) return "";

            String agg;
            if (f.equals("COUNT(*)")) {
                agg = "COUNT(*)";
            } else {
                String c = (String) colCombo.getSelectedItem();
                if (c == null || c.trim().isEmpty()) return "";
                String baseFunc = f.substring(0, f.indexOf("("));
                agg = baseFunc + "(" + dbm.quoteColumnName(c) + ")";
            }
            return agg + " " + op + " " + val;
        }
    }

    class DualWindowFuncRow extends JPanel {
        private final JComboBox<String> funcCombo = new JComboBox<>(new String[]{
            "ROW_NUMBER()", "RANK()", "DENSE_RANK()", "SUM(col)", "AVG(col)",
            "MIN(col)", "MAX(col)", "LAG(col)", "LEAD(col)", "FIRST_VALUE(col)", "LAST_VALUE(col)", "NTILE(4)"
        });
        private final SearchableComboBox<String> colCombo = new SearchableComboBox<>();
        private final SearchableComboBox<String> partCombo = new SearchableComboBox<>();
        private final SearchableComboBox<String> orderCombo = new SearchableComboBox<>();
        private final JComboBox<String> dirCombo = new JComboBox<>(new String[]{"ASC", "DESC"});
        private final JComboBox<String> frameCombo = new JComboBox<>(new String[]{
            "Frame: Default",
            "ROWS UNBOUNDED PRECEDING",
            "ROWS 6 PRECEDING",
            "ROWS 3 PRECEDING AND 3 FOLLOWING",
            "ROWS 1 PRECEDING EXCLUDE CURRENT ROW"
        });
        private final JTextField aliasField = new JTextField("win_val", 8);
        private final JButton delBtn = new JButton("X");

        public DualWindowFuncRow() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            funcCombo.setPreferredSize(new Dimension(115, 26));
            colCombo.setPreferredSize(new Dimension(110, 26));
            partCombo.setPreferredSize(new Dimension(110, 26));
            orderCombo.setPreferredSize(new Dimension(110, 26));
            dirCombo.setPreferredSize(new Dimension(65, 26));
            frameCombo.setPreferredSize(new Dimension(145, 26));
            aliasField.setPreferredSize(new Dimension(75, 26));
            delBtn.setPreferredSize(new Dimension(38, 24));
            delBtn.setMargin(new Insets(1, 4, 1, 4));

            add(new JLabel("Window: "));
            add(funcCombo);
            add(colCombo);
            add(new JLabel(" PARTITION: "));
            add(partCombo);
            add(new JLabel(" ORDER: "));
            add(orderCombo);
            add(dirCombo);
            add(frameCombo);
            add(new JLabel(" AS "));
            add(aliasField);
            add(delBtn);

            funcCombo.addActionListener(e -> {
                String f = (String) funcCombo.getSelectedItem();
                boolean needsCol = f != null && f.contains("(col)");
                colCombo.setEnabled(needsCol);
            });
            colCombo.setEnabled(false);

            delBtn.addActionListener(e -> removeWindowFuncRow(this));
        }

        public void updateAvailableColumns(List<String> cols) {
            updateCombo(colCombo, cols);
            List<String> partCols = new ArrayList<>(cols);
            partCols.add(0, "-- None --");
            updateCombo(partCombo, partCols);
            List<String> orderCols = new ArrayList<>(cols);
            orderCols.add(0, "-- None --");
            updateCombo(orderCombo, orderCols);
        }

        private void updateCombo(SearchableComboBox<String> cb, List<String> items) {
            Object cur = cb.getSelectedItem();
            cb.setAllItems(items);
            if (cur != null && items.contains(cur)) cb.setSelectedItem(cur);
        }

        public String buildSql(DatabaseManager dbm) {
            String f = (String) funcCombo.getSelectedItem();
            if (f == null) return "";
            StringBuilder expr = new StringBuilder();
            if (f.contains("(col)")) {
                String c = (String) colCombo.getSelectedItem();
                if (c == null || c.trim().isEmpty()) return "";
                String baseFunc = f.substring(0, f.indexOf("("));
                expr.append(baseFunc).append("(").append(dbm.quoteColumnName(c)).append(")");
            } else {
                expr.append(f);
            }
            expr.append(" OVER (");
            boolean hasPart = false;
            String partCol = (String) partCombo.getSelectedItem();
            if (partCol != null && !partCol.equals("-- None --") && !partCol.trim().isEmpty()) {
                expr.append("PARTITION BY ").append(dbm.quoteColumnName(partCol));
                hasPart = true;
            }
            String ordCol = (String) orderCombo.getSelectedItem();
            if (ordCol != null && !ordCol.equals("-- None --") && !ordCol.trim().isEmpty()) {
                if (hasPart) expr.append(" ");
                expr.append("ORDER BY ").append(dbm.quoteColumnName(ordCol)).append(" ").append(dirCombo.getSelectedItem());
            }
            String frame = (String) frameCombo.getSelectedItem();
            if (frame != null && !frame.equals("Frame: Default")) {
                if (frame.equals("ROWS UNBOUNDED PRECEDING")) {
                    expr.append(" ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW");
                } else if (frame.equals("ROWS 6 PRECEDING")) {
                    expr.append(" ROWS BETWEEN 6 PRECEDING AND CURRENT ROW");
                } else if (frame.equals("ROWS 3 PRECEDING AND 3 FOLLOWING")) {
                    expr.append(" ROWS BETWEEN 3 PRECEDING AND 3 FOLLOWING");
                } else if (frame.equals("ROWS 1 PRECEDING EXCLUDE CURRENT ROW")) {
                    expr.append(" ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING EXCLUDE CURRENT ROW");
                }
            }
            expr.append(")");
            String al = aliasField.getText().trim();
            if (!al.isEmpty()) {
                expr.append(" AS ").append(dbm.quoteColumnName(al));
            }
            return expr.toString();
        }
    }

    private static class DatabaseEntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DatabaseEntry) {
                DatabaseEntry d = (DatabaseEntry) value;
                lbl.setText(d.getLabel() + (d.getDbManager().isConnected() ? " [🟢]" : " [⚪]"));
            }
            return lbl;
        }
    }
}
