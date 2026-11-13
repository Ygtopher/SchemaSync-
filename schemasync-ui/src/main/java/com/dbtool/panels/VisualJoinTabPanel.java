package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.Main;
import com.dbtool.model.VisualJoinState;
import com.dbtool.util.ExportUtil;
import com.dbtool.util.SqlSnippetsLibrary;
import com.dbtool.util.VisualJoinProjectManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
    public class VisualJoinTabPanel extends JPanel {
    private Main.SearchableComboBox baseTableDropdown = new Main.SearchableComboBox();
    private JPanel implicitTablesContainer = new JPanel();
        private java.util.List<ImplicitTablePanel> implicitTablePanels = new java.util.ArrayList<>();
        private JPanel joinsContainer = new JPanel();
    private List<JoinPanel> joinPanels = new ArrayList<>();
    private final JTable joinResultTable;
    private JButton joinSelectColsButton = new JButton("Columns (All)");
    private List<String> joinSelectedColumns = new ArrayList<>();
    private JTextField joinLimitField = new JTextField(5);
    private String lastExecutedJoinQuery = "";
    private JCheckBox joinDistinctCheckbox = new JCheckBox("Distinct");
    private JPanel whereContainer = new JPanel();
    private List<Main.WherePanel> wherePanels = new ArrayList<>();
    private JPanel joinOrderByContainer = new JPanel();
    private List<Main.OrderByPanel> joinOrderByPanels = new ArrayList<>();
    private JPanel joinAggregateContainer = new JPanel();
    private List<Main.AggregateRowPanel> joinAggregatePanels = new ArrayList<>();
    private JPanel joinGroupByContainer = new JPanel();
    private List<Main.GroupByRowPanel> joinGroupByPanels = new ArrayList<>();
    private JPanel joinHavingContainer = new JPanel();
    private List<Main.HavingRowPanel> joinHavingPanels = new ArrayList<>();
    private JPanel joinWindowFuncContainer = new JPanel();
    private List<Main.WindowFuncRowPanel> joinWindowFuncPanels = new ArrayList<>();
    private JComboBox<String> joinLockDropdown = new JComboBox<>(new String[]{
        "Lock: None", "FOR UPDATE", "FOR UPDATE SKIP LOCKED", "FOR SHARE", "FOR NO KEY UPDATE"
    });
    private JComboBox<String> joinSampleDropdown = new JComboBox<>(new String[]{
        "Sample: None", "BERNOULLI (10)", "BERNOULLI (5)", "BERNOULLI (1)", "SYSTEM (10)", "SYSTEM (5)"
    });

    private JPanel createJoinPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            
            JPanel configPanel = new JPanel(new BorderLayout());
            
            // Base Table Selection
            JToolBar tbBase = new JToolBar();
            tbBase.setFloatable(false);
            tbBase.setLayout(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));
            tbBase.add(new JLabel(" Base Table: "));
            baseTableDropdown.addActionListener(e -> {
                joinSelectedColumns.clear();
                joinSelectColsButton.setText("Columns (All)");
                updateJoinDropdowns();
                executeVisualJoin();
            });
            baseTableDropdown.setPreferredSize(new Dimension(200, 26));
            tbBase.add(baseTableDropdown);

            JButton runJoinTopBtn = new JButton("▶ Run Query");
            runJoinTopBtn.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.BOLD, 12));
            runJoinTopBtn.setForeground(com.dbtool.util.ThemeManager.getSuccessColor());
            runJoinTopBtn.setIcon(com.dbtool.ui.components.UiIconFactory.getIcon(com.dbtool.ui.components.UiIconFactory.IconType.RUN, 13));
            runJoinTopBtn.setToolTipText("Execute the Visual Join query (or press Ctrl+Enter / F5)");
            runJoinTopBtn.addActionListener(e -> executeVisualJoin());
            tbBase.add(runJoinTopBtn);

            tbBase.add(com.dbtool.util.ThemeManager.createToolbarSeparator());
            JButton addTableBtn = new JButton("+ Table");
            addTableBtn.addActionListener(e -> {
                ImplicitTablePanel itp = new ImplicitTablePanel();
                implicitTablePanels.add(itp);
                implicitTablesContainer.add(itp);
                implicitTablesContainer.revalidate();
                implicitTablesContainer.repaint();
                updateJoinDropdowns();
            });
            tbBase.add(addTableBtn);
            JButton addJoinBtn = new JButton("+ Add Join");
            addJoinBtn.addActionListener(e -> addJoinRow());
            tbBase.add(addJoinBtn);
            JButton addWhereBtn = new JButton("+ Add Where");
            addWhereBtn.addActionListener(e -> addWhereRow());
            tbBase.add(addWhereBtn);

            tbBase.add(com.dbtool.util.ThemeManager.createToolbarSeparator());
            JToggleButton compactJoinBuilderBtn = new JToggleButton("▲ Compact Grid");
            compactJoinBuilderBtn.setToolTipText("Collapse join builder clauses and toolbars to maximize result table space");
            tbBase.add(compactJoinBuilderBtn);

            JButton autoFitJoinBtn = new JButton("⚡ Auto-Fit");
            autoFitJoinBtn.setToolTipText("Automatically adjust all column widths to match the length of data contents and headers");
            autoFitJoinBtn.addActionListener(e -> com.dbtool.ui.components.SmartTableColumnAdjuster.adjustColumnWidths(joinResultTable));
            tbBase.add(autoFitJoinBtn);

            JLabel joinClauseBadge = new JLabel("");
            joinClauseBadge.setForeground(com.dbtool.util.ThemeManager.getPrimaryActionColor());
            joinClauseBadge.setFont(joinClauseBadge.getFont().deriveFont(Font.BOLD, 11.5f));
            tbBase.add(joinClauseBadge);
            configPanel.add(tbBase, BorderLayout.NORTH);
            
            // Dynamic Joins and Where List
            joinsContainer.setLayout(new BoxLayout(joinsContainer, BoxLayout.Y_AXIS));
            whereContainer.setLayout(new BoxLayout(whereContainer, BoxLayout.Y_AXIS));
            implicitTablesContainer.setLayout(new BoxLayout(implicitTablesContainer, BoxLayout.Y_AXIS));
            joinAggregateContainer.setLayout(new BoxLayout(joinAggregateContainer, BoxLayout.Y_AXIS));
            joinGroupByContainer.setLayout(new BoxLayout(joinGroupByContainer, BoxLayout.Y_AXIS));
            joinHavingContainer.setLayout(new BoxLayout(joinHavingContainer, BoxLayout.Y_AXIS));
            joinWindowFuncContainer.setLayout(new BoxLayout(joinWindowFuncContainer, BoxLayout.Y_AXIS));

            JPanel listsPanel = new JPanel();
            listsPanel.setLayout(new BoxLayout(listsPanel, BoxLayout.Y_AXIS));
            joinOrderByContainer.setLayout(new BoxLayout(joinOrderByContainer, BoxLayout.Y_AXIS));
            listsPanel.add(implicitTablesContainer);
            listsPanel.add(joinsContainer);
            listsPanel.add(whereContainer);
            listsPanel.add(joinAggregateContainer);
            listsPanel.add(joinGroupByContainer);
            listsPanel.add(joinHavingContainer);
            listsPanel.add(joinWindowFuncContainer);
            listsPanel.add(joinOrderByContainer);
            
            JScrollPane listsScroll = new JScrollPane(listsPanel);
            listsScroll.setBorder(BorderFactory.createEmptyBorder());
            configPanel.add(listsScroll, BorderLayout.CENTER);
            
            // Bottom Options
            JPanel bottomOptionsPanel = new JPanel();
            bottomOptionsPanel.setLayout(new BoxLayout(bottomOptionsPanel, BoxLayout.Y_AXIS));
            
            // Primary execution & query building toolbar
            JToolBar tbRun = new JToolBar();
            tbRun.setFloatable(false);
            tbRun.setLayout(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));

            JButton runJoinBtn = new JButton("▶ Run Query");
            runJoinBtn.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.BOLD, 12));
            runJoinBtn.setForeground(com.dbtool.util.ThemeManager.getSuccessColor());
            runJoinBtn.setIcon(com.dbtool.ui.components.UiIconFactory.getIcon(com.dbtool.ui.components.UiIconFactory.IconType.RUN, 13));
            runJoinBtn.setToolTipText("Execute the Visual Join query (Ctrl+Enter / F5)");
            runJoinBtn.addActionListener(e -> executeVisualJoin());
            tbRun.add(runJoinBtn);
            tbRun.add(com.dbtool.util.ThemeManager.createToolbarSeparator());

            joinSelectColsButton.addActionListener(e -> openJoinColumnSelector());
            tbRun.add(joinSelectColsButton);
            tbRun.add(new JLabel(" | Limit: "));
            joinLimitField.setToolTipText("Limit (empty for none)");
            joinLimitField.setPreferredSize(new Dimension(60, 26));
            tbRun.add(joinLimitField);
            tbRun.add(joinDistinctCheckbox);
            tbRun.add(com.dbtool.util.ThemeManager.createToolbarSeparator());

            JButton addJoinAggBtn = new JButton("+ Add Aggregate");
            addJoinAggBtn.setToolTipText("Add dynamic aggregate row (COUNT, SUM, AVG, MIN, MAX) to joined tables");
            addJoinAggBtn.addActionListener(e -> addJoinAggregateRow());
            tbRun.add(addJoinAggBtn);

            JButton addJoinGroupBtn = new JButton("+ Add Group By");
            addJoinGroupBtn.setToolTipText("Add GROUP BY column to joined tables");
            addJoinGroupBtn.addActionListener(e -> addJoinGroupByRow());
            tbRun.add(addJoinGroupBtn);

            JButton addJoinHavingBtn = new JButton("+ Add Having");
            addJoinHavingBtn.setToolTipText("Add HAVING condition to joined tables");
            addJoinHavingBtn.addActionListener(e -> addJoinHavingRow());
            tbRun.add(addJoinHavingBtn);

            JButton addJoinWindowBtn = new JButton("+ Add Window Func");
            addJoinWindowBtn.setToolTipText("Add window function row (OVER partition/order/frame)");
            addJoinWindowBtn.addActionListener(e -> addJoinWindowFuncRow());
            tbRun.add(addJoinWindowBtn);

            JButton addJoinOrderBtn = new JButton("+ Add Order By");
            addJoinOrderBtn.addActionListener(e -> addJoinOrderByRow());
            tbRun.add(addJoinOrderBtn);

            // Advanced clauses and analysis toolbar
            JToolBar tbClauses = new JToolBar();
            tbClauses.setFloatable(false);
            tbClauses.setLayout(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));

            joinSampleDropdown.setToolTipText("TABLESAMPLE on base table (Bernoulli or System sampling)");
            tbClauses.add(joinSampleDropdown);
            joinLockDropdown.setToolTipText("Row-level locking clause (FOR UPDATE, SKIP LOCKED)");
            tbClauses.add(joinLockDropdown);
            tbClauses.add(com.dbtool.util.ThemeManager.createToolbarSeparator());

            JButton joinAggregatesBtn = new JButton("📊 Aggregates Dialog");
            joinAggregatesBtn.setToolTipText("Open full join aggregate metrics & grouping dialog");
            joinAggregatesBtn.addActionListener(e -> openJoinAggregatesDialog());
            tbClauses.add(joinAggregatesBtn);

            JButton explainJoinBtn = new JButton("🔍 EXPLAIN ▾");
            explainJoinBtn.setToolTipText("Inspect join execution plan (EXPLAIN, ANALYZE, BUFFERS)");
            explainJoinBtn.addActionListener(e -> mainFrame.showExplainMenu(explainJoinBtn, () -> buildCurrentJoinQuery()));
            tbClauses.add(explainJoinBtn);
            tbClauses.add(com.dbtool.util.ThemeManager.createToolbarSeparator());

            JButton saveJoinBtn = new JButton("💾 Save Project");
            JButton loadJoinBtn = new JButton("📂 Load Project");
            saveJoinBtn.addActionListener(e -> saveVisualJoinProject());
            loadJoinBtn.addActionListener(e -> loadVisualJoinProject());
            tbClauses.add(saveJoinBtn);
            tbClauses.add(loadJoinBtn);
            
            JToolBar tbActions = new JToolBar();
            tbActions.setFloatable(false);
            tbActions.setLayout(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));
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

            JButton loadSqlBtn = new JButton("📥 Load SQL");
            loadSqlBtn.setToolTipText("Load or paste a SELECT query with JOIN into Visual Join Builder");
            loadSqlBtn.addActionListener(e -> {
                String input = JOptionPane.showInputDialog(this, "Enter or paste a SELECT query with JOIN:", "Load SQL into Visual Join Builder", JOptionPane.PLAIN_MESSAGE);
                if (input != null && !input.trim().isEmpty()) {
                    loadFromSql(input.trim());
                }
            });
            JButton openInSqlBtn = new JButton("▶ Open in SQL Editor");
            openInSqlBtn.setToolTipText("Open the executed join query in SQL Editor");
            openInSqlBtn.addActionListener(e -> {
                if (!lastExecutedJoinQuery.isEmpty()) {
                    mainFrame.getSqlEditorPanel().setEditorText(lastExecutedJoinQuery);
                    mainFrame.getTabbedPane().setSelectedComponent(mainFrame.getSqlEditorPanel());
                } else {
                    JOptionPane.showMessageDialog(this, "Please execute the join first.");
                }
            });
            tbActions.add(loadSqlBtn);
            tbActions.add(openInSqlBtn);
            tbActions.addSeparator();

            JButton templatesBtn = new JButton("📝 SQL Templates ▾");
            templatesBtn.setToolTipText("Advanced SQL templates (Correlated LATERAL joins, CROSS APPLY, Window clauses...)");
            templatesBtn.addActionListener(e -> {
                JPopupMenu menu = new JPopupMenu();
                SqlSnippetsLibrary.populatePopupMenu(menu, sql -> {
                    int opt = JOptionPane.showOptionDialog(this,
                        "Selected SQL Template:\n\n" + (sql.length() > 220 ? sql.substring(0, 220) + "..." : sql) + "\n\nChoose an action:",
                        "SQL Template Action",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new String[]{"Load into Visual Join", "Open in SQL Editor", "Copy to Clipboard"},
                        "Load into Visual Join");
                    if (opt == 0) {
                        loadFromSql(sql);
                    } else if (opt == 1) {
                        mainFrame.getSqlEditorPanel().setEditorText(sql);
                        mainFrame.getTabbedPane().setSelectedComponent(mainFrame.getSqlEditorPanel());
                    } else if (opt == 2) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(sql), null);
                        JOptionPane.showMessageDialog(this, "Snippet copied to clipboard!");
                    }
                });
                menu.show(templatesBtn, 0, templatesBtn.getHeight());
            });

            JButton cheatSheetBtn = new JButton("💡 Cheat Sheet");
            cheatSheetBtn.setToolTipText("Open interactive Advanced SQL Mastery Cheat Sheet");
            cheatSheetBtn.addActionListener(e -> {
                new SqlMasteryReferenceDialog(mainFrame, sql -> {
                    mainFrame.getSqlEditorPanel().setEditorText(sql);
                    mainFrame.getTabbedPane().setSelectedComponent(mainFrame.getSqlEditorPanel());
                }).setVisible(true);
            });
            tbActions.add(templatesBtn);
            tbActions.add(cheatSheetBtn);
            
            bottomOptionsPanel.add(tbRun);
            bottomOptionsPanel.add(tbClauses);
            bottomOptionsPanel.add(tbActions);
            configPanel.add(bottomOptionsPanel, BorderLayout.SOUTH);
            
            JScrollPane configScroll = new JScrollPane(configPanel);
            configScroll.setBorder(BorderFactory.createEmptyBorder());
            configScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            configScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            configScroll.getVerticalScrollBar().setUnitIncrement(16);

            joinResultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            joinResultTable.setAutoCreateRowSorter(true);
            com.dbtool.util.TableTooltipUtil.attachHeaderTooltips(joinResultTable);
            JScrollPane joinResultScroll = new JScrollPane(joinResultTable);
            com.dbtool.util.TableRowUtilities.addRowNumbers(joinResultTable, joinResultScroll);

            JSplitPane joinSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, configScroll, joinResultScroll);
            joinSplit.setResizeWeight(0.32);
            joinSplit.setDividerSize(6);
            joinSplit.setBorder(BorderFactory.createEmptyBorder());
            panel.add(joinSplit, BorderLayout.CENTER);

            compactJoinBuilderBtn.addActionListener(e -> {
                boolean compact = compactJoinBuilderBtn.isSelected();
                listsScroll.setVisible(!compact);
                bottomOptionsPanel.setVisible(!compact);
                compactJoinBuilderBtn.setText(compact ? "▼ Expand Join Builder" : "▲ Compact Grid");
                int jCount = joinPanels.size() + implicitTablePanels.size();
                int wCount = wherePanels.size();
                int oCount = joinOrderByPanels.size();
                if (compact && (jCount + wCount + oCount > 0)) {
                    joinClauseBadge.setText(String.format("  •  JOIN (%d) | WHERE (%d) | ORDER (%d)", jCount, wCount, oCount));
                } else {
                    joinClauseBadge.setText("");
                }
                configPanel.revalidate();
                configPanel.repaint();
                SwingUtilities.invokeLater(() -> {
                    joinSplit.setDividerLocation(compact ? 42 : 240);
                });
            });

            // Shortcuts (Ctrl+Enter / F5)
            KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK);
            KeyStroke f5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
            Action runAction = new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { executeVisualJoin(); }
            };
            panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ctrlEnter, "runVisualJoin");
            panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(f5, "runVisualJoin");
            panel.getActionMap().put("runVisualJoin", runAction);
            
            return panel;
        }
    private void openJoinColumnSelector() {
            String baseTable = (String) baseTableDropdown.getSelectedItem();
            if (baseTable == null) return;
    
            // Build a map: tableName -> list of checkboxes
            List<String> tableOrder = new ArrayList<>();
            Map<String, List<JCheckBox>> tableCheckboxMap = new LinkedHashMap<>();
            List<JCheckBox> allCheckBoxes = new ArrayList<>();
            Set<String> allFullCols = new HashSet<>();
            int totalCols = 0;
    
            // Base table
            tableOrder.add(baseTable);
            List<JCheckBox> baseCbs = new ArrayList<>();
            List<String> rawBaseCols = mainFrame.getDbManager().getColumnNames(baseTable);
            List<String> baseCols = rawBaseCols != null ? new ArrayList<>(new LinkedHashSet<>(rawBaseCols)) : new ArrayList<>();
            for (String col : baseCols) {
                String fullCol = baseTable + "." + col.trim();
                if (!allFullCols.add(fullCol)) continue;
                JCheckBox cb = new JCheckBox(col.trim()); // show just col name in tab; full name is in text
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
                    List<String> rawJoinCols = mainFrame.getDbManager().getColumnNames(joinTable);
                    List<String> joinCols = rawJoinCols != null ? new ArrayList<>(new LinkedHashSet<>(rawJoinCols)) : new ArrayList<>();
                    for (String col : joinCols) {
                        String fullCol = joinTable + "." + col.trim();
                        if (!allFullCols.add(fullCol)) continue;
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
    
            JDialog dialog = new JDialog(mainFrame, "Select Join Columns", true);
            dialog.setResizable(true);
            dialog.setLayout(new BorderLayout(6, 6));
    
            // One tab per table
            JTabbedPane tabs = new JTabbedPane();
            Map<String, JPanel> tabPanelMap = new LinkedHashMap<>();
            for (String tbl : tableOrder) {
                JPanel tabPanel = new JPanel();
                tabPanel.setLayout(new BoxLayout(tabPanel, BoxLayout.Y_AXIS));
                tabPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                for (JCheckBox cb : tableCheckboxMap.get(tbl)) {
                    tabPanel.add(cb);
                }
                tabPanelMap.put(tbl, tabPanel);
                JScrollPane sp = new JScrollPane(tabPanel);
                sp.getVerticalScrollBar().setUnitIncrement(16);
                tabs.addTab(tbl, sp);
            }
            dialog.add(tabs, BorderLayout.CENTER);
    
            // Top: Search bar + Select All / Unselect All
            JPanel northContainer = new JPanel(new BorderLayout(4, 4));
            northContainer.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));

            JPanel searchRow = new JPanel(new BorderLayout(6, 0));
            JLabel searchLbl = new JLabel("🔍 Search columns:");
            JTextField searchField = new JTextField();
            searchField.setToolTipText("Type to filter columns across all joined tables...");
            JButton clearSearchBtn = new JButton("✕");
            clearSearchBtn.setMargin(new Insets(2, 6, 2, 6));
            clearSearchBtn.setToolTipText("Clear search filter");
            clearSearchBtn.addActionListener(e -> searchField.setText(""));
            searchRow.add(searchLbl, BorderLayout.WEST);
            searchRow.add(searchField, BorderLayout.CENTER);
            searchRow.add(clearSearchBtn, BorderLayout.EAST);

            JLabel countLabel = new JLabel();
            Runnable updateCountLabel = () -> {
                int sel = 0;
                for (JCheckBox cb : allCheckBoxes) if (cb.isSelected()) sel++;
                countLabel.setText("Selected: " + sel + " / " + allCheckBoxes.size());
            };
            for (JCheckBox cb : allCheckBoxes) {
                cb.addActionListener(e -> updateCountLabel.run());
            }
            updateCountLabel.run();

            Runnable filterJoinColumns = () -> {
                String q = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                int firstMatchingTab = -1;
                int currentTabMatches = 0;
                int activeIdx = tabs.getSelectedIndex();

                for (int tIdx = 0; tIdx < tableOrder.size(); tIdx++) {
                    String tbl = tableOrder.get(tIdx);
                    JPanel tabPanel = tabPanelMap.get(tbl);
                    tabPanel.removeAll();
                    int matchCount = 0;
                    for (JCheckBox cb : tableCheckboxMap.get(tbl)) {
                        if (q.isEmpty() || cb.getText().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                            tabPanel.add(cb);
                            matchCount++;
                        }
                    }
                    tabs.setTitleAt(tIdx, q.isEmpty() ? tbl : (tbl + " (" + matchCount + ")"));
                    if (matchCount > 0 && firstMatchingTab < 0) {
                        firstMatchingTab = tIdx;
                    }
                    if (tIdx == activeIdx) {
                        currentTabMatches = matchCount;
                    }
                    tabPanel.revalidate();
                    tabPanel.repaint();
                }

                if (!q.isEmpty() && currentTabMatches == 0 && firstMatchingTab >= 0) {
                    tabs.setSelectedIndex(firstMatchingTab);
                }
            };

            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filterJoinColumns.run(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filterJoinColumns.run(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filterJoinColumns.run(); }
            });

            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
            JButton selectAllBtn = new JButton("Select All (Tab)");
            JButton unselectAllBtn = new JButton("Unselect All (Tab)");
            JButton selectGlobalBtn = new JButton("Select All");
            JButton unselectGlobalBtn = new JButton("Unselect All");
            selectAllBtn.addActionListener(e -> {
                String q = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                String tbl = tableOrder.get(tabs.getSelectedIndex());
                for (JCheckBox cb : tableCheckboxMap.get(tbl)) {
                    if (q.isEmpty() || cb.getText().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        cb.setSelected(true);
                    }
                }
                updateCountLabel.run();
            });
            unselectAllBtn.addActionListener(e -> {
                String q = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                String tbl = tableOrder.get(tabs.getSelectedIndex());
                for (JCheckBox cb : tableCheckboxMap.get(tbl)) {
                    if (q.isEmpty() || cb.getText().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        cb.setSelected(false);
                    }
                }
                updateCountLabel.run();
            });
            selectGlobalBtn.addActionListener(e -> {
                String q = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                for (JCheckBox cb : allCheckBoxes) {
                    if (q.isEmpty() || cb.getText().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        cb.setSelected(true);
                    }
                }
                updateCountLabel.run();
            });
            unselectGlobalBtn.addActionListener(e -> {
                String q = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                for (JCheckBox cb : allCheckBoxes) {
                    if (q.isEmpty() || cb.getText().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        cb.setSelected(false);
                    }
                }
                updateCountLabel.run();
            });
            topPanel.add(selectGlobalBtn);
            topPanel.add(unselectGlobalBtn);
            topPanel.add(selectAllBtn);
            topPanel.add(unselectAllBtn);

            northContainer.add(searchRow, BorderLayout.NORTH);
            northContainer.add(topPanel, BorderLayout.SOUTH);
            dialog.add(northContainer, BorderLayout.NORTH);
    
            final int finalTotalCols = totalCols;
            JButton okBtn = new JButton("OK");
            okBtn.setFont(okBtn.getFont().deriveFont(Font.BOLD));
            okBtn.addActionListener(e -> {
                joinSelectedColumns.clear();
                Set<String> chosen = new LinkedHashSet<>();
                for (JCheckBox cb : allCheckBoxes) {
                    if (cb.isSelected()) {
                        chosen.add(cb.getText().trim());
                    }
                }
                joinSelectedColumns.addAll(chosen);
                if (joinSelectedColumns.size() == finalTotalCols || joinSelectedColumns.isEmpty()) {
                    joinSelectedColumns.clear();
                    joinSelectColsButton.setText("Columns (All)");
                } else {
                    joinSelectColsButton.setText("Columns (" + joinSelectedColumns.size() + ")");
                }
                dialog.dispose();
            });
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.addActionListener(e -> dialog.dispose());
    
            JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
            bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
            bottomPanel.add(countLabel, BorderLayout.WEST);
            JPanel btnRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            btnRight.add(okBtn);
            btnRight.add(cancelBtn);
            bottomPanel.add(btnRight, BorderLayout.EAST);
            dialog.add(bottomPanel, BorderLayout.SOUTH);

            dialog.getRootPane().setDefaultButton(okBtn);
            dialog.getRootPane().registerKeyboardAction(
                    e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
    
            dialog.setSize(520, 560);
            dialog.setLocationRelativeTo(this);
            SwingUtilities.invokeLater(searchField::requestFocusInWindow);
            dialog.setVisible(true);
        }

    private void openJoinAggregatesDialog() {
        String baseTable = (String) baseTableDropdown.getSelectedItem();
        if (baseTable == null) {
            JOptionPane.showMessageDialog(mainFrame, "Please select a base table first.", "No Base Table", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> joinTables = new ArrayList<>();
        joinTables.add(baseTable);
        for (ImplicitTablePanel itp : implicitTablePanels) {
            String t = (String) itp.tableDropdown.getSelectedItem();
            if (t != null && !t.isEmpty() && !joinTables.contains(t)) joinTables.add(t);
        }
        for (JoinPanel jp : joinPanels) {
            String t = (String) jp.joinTableDropdown.getSelectedItem();
            if (t != null && !t.isEmpty() && !joinTables.contains(t)) joinTables.add(t);
        }

        List<String> allQualifiedCols = new ArrayList<>();
        for (String tbl : joinTables) {
            for (String col : mainFrame.getDbManager().getColumnNames(tbl)) {
                allQualifiedCols.add(tbl + "." + col);
            }
        }

        if (allQualifiedCols.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No columns found for joined tables.", "No Columns", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(mainFrame, "📊 Visual Join Aggregates & Group By", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setSize(720, 600);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel mainForm = new JPanel(new GridBagLayout());
        mainForm.setBorder(BorderFactory.createEmptyBorder(10, 14, 8, 14));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Group By
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.35;
        mainForm.add(new JLabel("GROUP BY Column:"), gbc);
        JComboBox<String> groupByCb = new JComboBox<>();
        groupByCb.addItem("-- None (Overall Join Summary) --");
        for (String c : allQualifiedCols) groupByCb.addItem(c);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(groupByCb, gbc);

        // Header
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel aggHeader = new JLabel("Aggregate Metrics Across Joined Tables:");
        aggHeader.setFont(aggHeader.getFont().deriveFont(Font.BOLD));
        mainForm.add(aggHeader, gbc);
        gbc.gridwidth = 1;

        // 1. COUNT(*)
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.35;
        JCheckBox countAllCb = new JCheckBox("COUNT(*) Joined Rows", true);
        mainForm.add(countAllCb, gbc);
        JTextField countAllAlias = new JTextField("total_count", 10);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(countAllAlias, gbc);

        // 2. COUNT(DISTINCT col)
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.35;
        JCheckBox countDistCb = new JCheckBox("COUNT(DISTINCT):");
        mainForm.add(countDistCb, gbc);
        JPanel cdP = new JPanel(new BorderLayout(4, 0));
        JComboBox<String> countDistCol = new JComboBox<>(allQualifiedCols.toArray(new String[0]));
        JTextField countDistAlias = new JTextField("distinct_count", 10);
        cdP.add(countDistCol, BorderLayout.CENTER);
        cdP.add(countDistAlias, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(cdP, gbc);

        // 3. SUM(col)
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.35;
        JCheckBox sumCb = new JCheckBox("SUM(col):");
        mainForm.add(sumCb, gbc);
        JPanel sumP = new JPanel(new BorderLayout(4, 0));
        JComboBox<String> sumCol = new JComboBox<>(allQualifiedCols.toArray(new String[0]));
        JTextField sumAlias = new JTextField("total_sum", 10);
        sumP.add(sumCol, BorderLayout.CENTER);
        sumP.add(sumAlias, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(sumP, gbc);

        // 4. AVG(col)
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.35;
        JCheckBox avgCb = new JCheckBox("AVG(col):");
        mainForm.add(avgCb, gbc);
        JPanel avgP = new JPanel(new BorderLayout(4, 0));
        JComboBox<String> avgCol = new JComboBox<>(allQualifiedCols.toArray(new String[0]));
        JTextField avgAlias = new JTextField("avg_val", 10);
        avgP.add(avgCol, BorderLayout.CENTER);
        avgP.add(avgAlias, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(avgP, gbc);

        // 5. MIN(col)
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0.35;
        JCheckBox minCb = new JCheckBox("MIN(col):");
        mainForm.add(minCb, gbc);
        JPanel minP = new JPanel(new BorderLayout(4, 0));
        JComboBox<String> minCol = new JComboBox<>(allQualifiedCols.toArray(new String[0]));
        JTextField minAlias = new JTextField("min_val", 10);
        minP.add(minCol, BorderLayout.CENTER);
        minP.add(minAlias, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(minP, gbc);

        // 6. MAX(col)
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 0.35;
        JCheckBox maxCb = new JCheckBox("MAX(col):");
        mainForm.add(maxCb, gbc);
        JPanel maxP = new JPanel(new BorderLayout(4, 0));
        JComboBox<String> maxCol = new JComboBox<>(allQualifiedCols.toArray(new String[0]));
        JTextField maxAlias = new JTextField("max_val", 10);
        maxP.add(maxCol, BorderLayout.CENTER);
        maxP.add(maxAlias, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(maxP, gbc);

        // HAVING Clause
        gbc.gridx = 0; gbc.gridy = 8; gbc.weightx = 0.35;
        mainForm.add(new JLabel("HAVING (Optional):"), gbc);
        JTextField havingField = new JTextField(20);
        havingField.setToolTipText("e.g. COUNT(*) > 5 or AVG(salary) > 60000");
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(havingField, gbc);

        // Preview Area
        JTextArea previewArea = new JTextArea(5, 40);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Generated Join Aggregation SQL"));

        Runnable updatePreview = () -> {
            List<String> selectParts = new ArrayList<>();
            String groupCol = (String) groupByCb.getSelectedItem();
            boolean hasGroup = groupCol != null && !groupCol.startsWith("--");
            if (hasGroup) {
                selectParts.add(mainFrame.getDbManager().quoteColumnName(groupCol));
            }
            if (countAllCb.isSelected()) {
                String al = countAllAlias.getText().trim();
                selectParts.add("COUNT(*)" + (al.isEmpty() ? "" : " AS " + al));
            }
            if (countDistCb.isSelected() && countDistCol.getSelectedItem() != null) {
                String al = countDistAlias.getText().trim();
                selectParts.add("COUNT(DISTINCT " + mainFrame.getDbManager().quoteColumnName((String) countDistCol.getSelectedItem()) + ")" + (al.isEmpty() ? "" : " AS " + al));
            }
            if (sumCb.isSelected() && sumCol.getSelectedItem() != null) {
                String al = sumAlias.getText().trim();
                selectParts.add("SUM(" + mainFrame.getDbManager().quoteColumnName((String) sumCol.getSelectedItem()) + ")" + (al.isEmpty() ? "" : " AS " + al));
            }
            if (avgCb.isSelected() && avgCol.getSelectedItem() != null) {
                String al = avgAlias.getText().trim();
                selectParts.add("AVG(" + mainFrame.getDbManager().quoteColumnName((String) avgCol.getSelectedItem()) + ")" + (al.isEmpty() ? "" : " AS " + al));
            }
            if (minCb.isSelected() && minCol.getSelectedItem() != null) {
                String al = minAlias.getText().trim();
                selectParts.add("MIN(" + mainFrame.getDbManager().quoteColumnName((String) minCol.getSelectedItem()) + ")" + (al.isEmpty() ? "" : " AS " + al));
            }
            if (maxCb.isSelected() && maxCol.getSelectedItem() != null) {
                String al = maxAlias.getText().trim();
                selectParts.add("MAX(" + mainFrame.getDbManager().quoteColumnName((String) maxCol.getSelectedItem()) + ")" + (al.isEmpty() ? "" : " AS " + al));
            }

            if (selectParts.isEmpty()) {
                selectParts.add("COUNT(*)");
            }

            StringBuilder q = new StringBuilder("SELECT ");
            q.append(String.join(", ", selectParts));
            q.append(" FROM ").append(mainFrame.getDbManager().quoteTableName(baseTable));

            for (ImplicitTablePanel itp : implicitTablePanels) {
                String t = (String) itp.tableDropdown.getSelectedItem();
                if (t != null && !t.isEmpty()) {
                    q.append(", ").append(mainFrame.getDbManager().quoteTableName(t));
                }
            }

            for (JoinPanel jp : joinPanels) {
                String type = (String) jp.joinTypeDropdown.getSelectedItem();
                String table = (String) jp.joinTableDropdown.getSelectedItem();
                if (table == null || table.isEmpty()) continue;
                q.append(" ").append(type).append(" ").append(mainFrame.getDbManager().quoteTableName(table)).append(" ");
                for (int i = 0; i < jp.conditionPanels.size(); i++) {
                    JoinConditionPanel cp = jp.conditionPanels.get(i);
                    String leftTable = (String) cp.leftTableDropdown.getSelectedItem();
                    String leftCol = (String) cp.leftColDropdown.getSelectedItem();
                    String rightCol = (String) cp.rightColDropdown.getSelectedItem();
                    if (leftTable != null && leftCol != null && rightCol != null) {
                        q.append(cp.operator).append(" ")
                         .append(mainFrame.getDbManager().quoteColumnName(leftTable + "." + leftCol))
                         .append(" = ")
                         .append(mainFrame.getDbManager().quoteColumnName(table + "." + rightCol)).append(" ");
                    }
                }
            }

            if (!wherePanels.isEmpty()) {
                StringBuilder wsb = new StringBuilder(" WHERE ");
                int count = 0;
                for (int i = 0; i < wherePanels.size(); i++) {
                    Main.WherePanel wp = wherePanels.get(i);
                    String cond = wp.buildConditionClause(mainFrame.getDbManager());
                    if (cond.isEmpty()) continue;
                    if (count > 0) wsb.append(wp.logicDropdown.getSelectedItem()).append(" ");
                    wsb.append(cond);
                    count++;
                }
                if (count > 0) q.append(wsb.toString());
            }

            if (hasGroup) {
                q.append(" GROUP BY ").append(mainFrame.getDbManager().quoteColumnName(groupCol));
            }
            String hav = havingField.getText().trim();
            if (!hav.isEmpty()) {
                q.append(" HAVING ").append(hav);
            }
            previewArea.setText(q.toString());
        };

        groupByCb.addActionListener(e -> updatePreview.run());
        countAllCb.addActionListener(e -> updatePreview.run());
        countDistCb.addActionListener(e -> updatePreview.run());
        countDistCol.addActionListener(e -> updatePreview.run());
        sumCb.addActionListener(e -> updatePreview.run());
        sumCol.addActionListener(e -> updatePreview.run());
        avgCb.addActionListener(e -> updatePreview.run());
        avgCol.addActionListener(e -> updatePreview.run());
        minCb.addActionListener(e -> updatePreview.run());
        minCol.addActionListener(e -> updatePreview.run());
        maxCb.addActionListener(e -> updatePreview.run());
        maxCol.addActionListener(e -> updatePreview.run());
        havingField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
        });
        updatePreview.run();

        JPanel centerPanel = new JPanel(new BorderLayout(4, 4));
        centerPanel.add(mainForm, BorderLayout.NORTH);
        centerPanel.add(previewScroll, BorderLayout.CENTER);
        dialog.add(centerPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton runBtn = new JButton("▶ Execute Join Aggregates");
        runBtn.setFont(runBtn.getFont().deriveFont(Font.BOLD));
        runBtn.setForeground(new Color(0, 102, 51));
        JButton openSqlBtn = new JButton("Open in SQL Editor");
        JButton copySqlBtn = new JButton("📋 Copy SQL");
        JButton closeBtn = new JButton("Close");

        runBtn.addActionListener(e -> {
            String sql = previewArea.getText();
            dialog.dispose();
            lastExecutedJoinQuery = sql;
            new Thread(() -> {
                try {
                    DefaultTableModel model = mainFrame.getDbManager().executeQuery(sql);
                    SwingUtilities.invokeLater(() -> joinResultTable.setModel(model));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Error executing join aggregates: " + ex.getMessage(), "Query Error", JOptionPane.ERROR_MESSAGE));
                }
            }).start();
        });

        openSqlBtn.addActionListener(e -> {
            String sql = previewArea.getText();
            mainFrame.getSqlEditorPanel().setEditorText(sql);
            mainFrame.getTabbedPane().setSelectedComponent(mainFrame.getSqlEditorPanel());
            dialog.dispose();
        });

        copySqlBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(previewArea.getText()), null);
            JOptionPane.showMessageDialog(dialog, "Join Aggregate SQL copied to clipboard!");
        });

        closeBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(copySqlBtn);
        btnPanel.add(openSqlBtn);
        btnPanel.add(runBtn);
        btnPanel.add(closeBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void addJoinOrderByRow() {
            Main.OrderByPanel op = mainFrame.new OrderByPanel(true, joinOrderByPanels, joinOrderByContainer);
            joinOrderByPanels.add(op);
            joinOrderByContainer.add(op);
            joinOrderByContainer.revalidate();
            joinOrderByContainer.repaint();
            updateWhereDropdowns(); // Re-use this to update tables
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
            Main.WherePanel wp = mainFrame.new WherePanel(isFirst, wherePanels, whereContainer);
            wp.fetchTablesFromDb();
            wherePanels.add(wp);
            whereContainer.add(wp);
            whereContainer.revalidate();
            whereContainer.repaint();
            updateWhereDropdowns();
        }

    private List<String> getAllJoinTables() {
            List<String> tables = new ArrayList<>();
            String baseTable = (String) baseTableDropdown.getSelectedItem();
            if (baseTable != null && !baseTable.isEmpty()) tables.add(baseTable);
            for (ImplicitTablePanel itp : implicitTablePanels) {
                String t = (String) itp.tableDropdown.getSelectedItem();
                if (t != null && !t.isEmpty() && !tables.contains(t)) tables.add(t);
            }
            for (JoinPanel jp : joinPanels) {
                String t = (String) jp.joinTableDropdown.getSelectedItem();
                if (t != null && !t.isEmpty() && !tables.contains(t)) tables.add(t);
            }
            return tables;
        }

    private List<String> getAllJoinColumns() {
            List<String> allCols = new ArrayList<>();
            for (String tbl : getAllJoinTables()) {
                for (String col : mainFrame.getDbManager().getColumnNames(tbl)) {
                    allCols.add(tbl + "." + col);
                }
            }
            return allCols;
        }

    private void addJoinAggregateRow() {
            Main.AggregateRowPanel row = mainFrame.new AggregateRowPanel(true, joinAggregatePanels, joinAggregateContainer);
            List<String> tbls = getAllJoinTables();
            row.updateTables(tbls);
            if (!tbls.isEmpty()) {
                row.tableDropdown.setSelectedItem(tbls.get(0));
                row.updateColumns(mainFrame.getDbManager().getColumnNames(tbls.get(0)));
            }
            joinAggregatePanels.add(row);
            joinAggregateContainer.add(row);
            joinAggregateContainer.revalidate();
            joinAggregateContainer.repaint();
        }

    private void addJoinGroupByRow() {
            Main.GroupByRowPanel row = mainFrame.new GroupByRowPanel(true, joinGroupByPanels, joinGroupByContainer);
            List<String> tbls = getAllJoinTables();
            row.updateTables(tbls);
            if (!tbls.isEmpty()) {
                row.tableDropdown.setSelectedItem(tbls.get(0));
                row.updateColumns(mainFrame.getDbManager().getColumnNames(tbls.get(0)));
            }
            joinGroupByPanels.add(row);
            joinGroupByContainer.add(row);
            joinGroupByContainer.revalidate();
            joinGroupByContainer.repaint();
        }

    private void addJoinHavingRow() {
            boolean isFirst = joinHavingPanels.isEmpty();
            Main.HavingRowPanel row = mainFrame.new HavingRowPanel(isFirst, true, joinHavingPanels, joinHavingContainer);
            List<String> tbls = getAllJoinTables();
            row.updateTables(tbls);
            if (!tbls.isEmpty()) {
                row.tableDropdown.setSelectedItem(tbls.get(0));
                row.updateColumns(mainFrame.getDbManager().getColumnNames(tbls.get(0)));
            }
            joinHavingPanels.add(row);
            joinHavingContainer.add(row);
            joinHavingContainer.revalidate();
            joinHavingContainer.repaint();
        }

    private void addJoinWindowFuncRow() {
            Main.WindowFuncRowPanel row = mainFrame.new WindowFuncRowPanel(joinWindowFuncPanels, joinWindowFuncContainer);
            row.updateColumns(getAllJoinColumns());
            joinWindowFuncPanels.add(row);
            joinWindowFuncContainer.add(row);
            joinWindowFuncContainer.revalidate();
            joinWindowFuncContainer.repaint();
        }

    private void updateJoinDropdowns() {
            new Thread(() -> {
                // Collect all available tables from the base table and previous joins to populate the "Left" side of the ON clause
                List<String> availableLeftTables = new ArrayList<>();
                
                String baseTable = (String) baseTableDropdown.getSelectedItem();
                if (baseTable != null) {
                    availableLeftTables.add(baseTable);
                }
                for (ImplicitTablePanel itp : implicitTablePanels) {
                    String t = (String) itp.tableDropdown.getSelectedItem();
                    if (t != null && !t.isEmpty()) availableLeftTables.add(t);
                }
    
                for (JoinPanel jp : joinPanels) {
                    String joinTable = (String) jp.joinTableDropdown.getSelectedItem();
                    if (joinTable != null) {
                        List<String> rightCols = new ArrayList<>(mainFrame.getDbManager().getColumnNames(joinTable));
                        
                        JoinConditionPanel firstCp = jp.conditionPanels.isEmpty() ? null : jp.conditionPanels.get(0);
                        Object currentLeftTable = firstCp != null ? firstCp.leftTableDropdown.getSelectedItem() : null;
                        Object currentLeftCol = firstCp != null ? firstCp.leftColDropdown.getSelectedItem() : null;
                        Object currentRightCol = firstCp != null ? firstCp.rightColDropdown.getSelectedItem() : null;
                        boolean hadSelection = (currentLeftTable != null && currentLeftCol != null && currentRightCol != null);
                        
                        List<String> finalAvailableLeftTables = new ArrayList<>(availableLeftTables);
                        SwingUtilities.invokeLater(() -> {
                            jp.updateRightColumns(rightCols);
                            jp.updateLeftTables(finalAvailableLeftTables);
                        });
                        
                        if (firstCp != null && (!hadSelection || firstCp.leftColDropdown.getSelectedItem() == null)) {
                            boolean found = false;
                            for (String leftTable : availableLeftTables) {
                                String[] match = mainFrame.getDbManager().getLearnedJoin(leftTable, joinTable);
                                if (match == null) match = mainFrame.getDbManager().getForeignKeyMatch(leftTable, joinTable);
                                if (match == null) match = mainFrame.getDbManager().getHeuristicMatch(leftTable, joinTable);
                                if (match != null) {
                                    String leftMatch = match[0];
                                    if (leftMatch.startsWith(leftTable + ".")) leftMatch = leftMatch.substring(leftTable.length() + 1);
                                    
                                    String rightMatch = match[1];
                                    if (rightMatch.startsWith(joinTable + ".")) rightMatch = rightMatch.substring(joinTable.length() + 1);
                                    
                                    final String finalLeftMatch = leftMatch;
                                    final String finalRightMatch = rightMatch;
                                    
                                    SwingUtilities.invokeLater(() -> {
                                        firstCp.leftTableDropdown.setSelectedItem(leftTable);
                                        firstCp.leftColDropdown.setSelectedItem(finalLeftMatch);
                                        firstCp.rightColDropdown.setSelectedItem(finalRightMatch);
                                    });
                                    found = true;
                                    break;
                                }
                            }
                            if (!found && !availableLeftTables.isEmpty()) {
                                SwingUtilities.invokeLater(() -> {
                                    firstCp.leftTableDropdown.setSelectedItem(finalAvailableLeftTables.get(0));
                                });
                            }
                        }
                        
                        availableLeftTables.add(joinTable);
                    }
                }

                List<String> allTbls = getAllJoinTables();
                List<String> allCols = getAllJoinColumns();
                SwingUtilities.invokeLater(() -> {
                    for (Main.AggregateRowPanel arp : joinAggregatePanels) arp.updateTables(allTbls);
                    for (Main.GroupByRowPanel grp : joinGroupByPanels) grp.updateTables(allTbls);
                    for (Main.HavingRowPanel hrp : joinHavingPanels) hrp.updateTables(allTbls);
                    for (Main.WindowFuncRowPanel wrp : joinWindowFuncPanels) wrp.updateColumns(allCols);
                });
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
            
            for (Main.OrderByPanel op : joinOrderByPanels) {
                op.updateTables(availableTables);
            }
    
            for (Main.WherePanel wp : wherePanels) {
                wp.updateTables(availableTables);
            }
        }
        public VisualJoinState captureJoinState() {
            VisualJoinState state = new VisualJoinState();
            state.baseTable = (String) baseTableDropdown.getSelectedItem();
            state.distinct = joinDistinctCheckbox.isSelected();
            state.selectedColumns.addAll(joinSelectedColumns);
            state.limit = joinLimitField.getText();
            
            for (JoinPanel jp : joinPanels) {
                VisualJoinState.JoinState js = new VisualJoinState.JoinState();
                js.type = (String) jp.joinTypeDropdown.getSelectedItem();
                js.table = (String) jp.joinTableDropdown.getSelectedItem();
                for (JoinConditionPanel cp : jp.conditionPanels) {
                    VisualJoinState.JoinConditionState cs = new VisualJoinState.JoinConditionState();
                    cs.operator = cp.operator;
                    cs.leftTable = (String) cp.leftTableDropdown.getSelectedItem();
                    cs.leftCol = (String) cp.leftColDropdown.getSelectedItem();
                    cs.rightCol = (String) cp.rightColDropdown.getSelectedItem();
                    js.conditions.add(cs);
                }
                state.joins.add(js);
            }
            
            for (Main.WherePanel wp : wherePanels) {
                state.wheres.add(wp.captureWhereState());
            }
            
            for (Main.OrderByPanel op : joinOrderByPanels) {
                state.orderBys.add(op.captureOrderByState());
            }
            return state;
        }

        private void saveVisualJoinProject() {
            VisualJoinState state = captureJoinState();
            VisualJoinProjectManager.saveProject(state, this);
        }

        public void restoreJoinState(VisualJoinState state) {
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
                
                jp.conditionPanels.clear();
                jp.conditionsContainer.removeAll();
                
                if (js.conditions != null && !js.conditions.isEmpty()) {
                    for (VisualJoinState.JoinConditionState cs : js.conditions) {
                        JoinConditionPanel cp = jp.addNewCondition(cs.operator);
                        cp.leftTableDropdown.setSelectedItem(cs.leftTable);
                        cp.leftColDropdown.setSelectedItem(cs.leftCol);
                        cp.rightColDropdown.setSelectedItem(cs.rightCol);
                    }
                } else {
                    JoinConditionPanel cp = jp.addNewCondition("ON");
                    cp.leftTableDropdown.setSelectedItem(js.leftTable);
                    cp.leftColDropdown.setSelectedItem(js.leftCol);
                    cp.rightColDropdown.setSelectedItem(js.rightCol);
                }
            }
            
            for (VisualJoinState.WhereState ws : state.wheres) {
                addWhereRow();
                Main.WherePanel wp = wherePanels.get(wherePanels.size() - 1);
                wp.restoreWhereState(ws);
            }
            
            for (VisualJoinState.OrderByState os : state.orderBys) {
                addJoinOrderByRow();
                Main.OrderByPanel op = joinOrderByPanels.get(joinOrderByPanels.size() - 1);
                op.restoreOrderByState(os);
            }
            
            joinSelectedColumns.addAll(state.selectedColumns);
            joinSelectColsButton.setText(joinSelectedColumns.isEmpty() ? "Columns (All)" : "Columns (" + joinSelectedColumns.size() + ")");
            joinsContainer.revalidate(); joinsContainer.repaint();
            whereContainer.revalidate(); whereContainer.repaint();
            joinOrderByContainer.revalidate(); joinOrderByContainer.repaint();
        }

        private void loadVisualJoinProject() {
            VisualJoinState state = VisualJoinProjectManager.loadProject(this);
            if (state == null) return;
            restoreJoinState(state);
        }
    public String buildCurrentJoinQuery() {
            String baseTable = (String) baseTableDropdown.getSelectedItem();
            if (baseTable == null) return "";
    
            List<String> selectParts = new ArrayList<>();
            for (Main.AggregateRowPanel arp : joinAggregatePanels) {
                String expr = arp.buildExpression(mainFrame.getDbManager());
                if (!expr.isEmpty()) selectParts.add(expr);
            }
            for (Main.WindowFuncRowPanel wfp : joinWindowFuncPanels) {
                String expr = wfp.buildExpression(mainFrame.getDbManager());
                if (!expr.isEmpty()) selectParts.add(expr);
            }

            List<String> groupByParts = new ArrayList<>();
            for (Main.GroupByRowPanel grp : joinGroupByPanels) {
                String colExpr = grp.getColumnExpression(mainFrame.getDbManager());
                if (!colExpr.isEmpty()) {
                    groupByParts.add(colExpr);
                    if (!selectParts.isEmpty() && !selectParts.contains(colExpr)) {
                        selectParts.add(0, colExpr);
                    }
                }
            }

            String cols;
            if (!selectParts.isEmpty()) {
                List<String> combined = new ArrayList<>();
                Set<String> added = new HashSet<>();
                if (!joinSelectedColumns.isEmpty()) {
                    for (String c : new LinkedHashSet<>(joinSelectedColumns)) {
                        String q = mainFrame.getDbManager().quoteColumnName(c);
                        if (added.add(q)) combined.add(q);
                    }
                }
                for (String part : selectParts) {
                    if (added.add(part)) combined.add(part);
                }
                cols = String.join(", ", combined);
            } else if (!joinSelectedColumns.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                Set<String> uniqueCols = new LinkedHashSet<>(joinSelectedColumns);
                int i = 0;
                for (String c : uniqueCols) {
                    sb.append(mainFrame.getDbManager().quoteColumnName(c));
                    if (++i < uniqueCols.size()) sb.append(", ");
                }
                cols = sb.toString();
            } else {
                cols = "*";
            }
            
            String distinctStr = joinDistinctCheckbox.isSelected() ? "DISTINCT " : "";
            
            String sampleStr = "";
            String sampleChoice = (String) joinSampleDropdown.getSelectedItem();
            if (sampleChoice != null && !sampleChoice.equals("Sample: None")) {
                sampleStr = " TABLESAMPLE " + sampleChoice;
            }

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(distinctStr).append(cols).append(" FROM ").append(mainFrame.getDbManager().quoteTableName(baseTable)).append(sampleStr);
            
            for (ImplicitTablePanel itp : implicitTablePanels) {
                String t = (String) itp.tableDropdown.getSelectedItem();
                if (t != null && !t.isEmpty()) {
                    sql.append(", ").append(mainFrame.getDbManager().quoteTableName(t));
                }
            }
            sql.append(" ");
    
            for (JoinPanel jp : joinPanels) {
                String type = (String) jp.joinTypeDropdown.getSelectedItem();
                String table = (String) jp.joinTableDropdown.getSelectedItem();
                
                if (table == null || table.isEmpty()) continue;
                
                sql.append(" ").append(type).append(" ").append(mainFrame.getDbManager().quoteTableName(table)).append(" ");
                
                for (int i = 0; i < jp.conditionPanels.size(); i++) {
                    JoinConditionPanel cp = jp.conditionPanels.get(i);
                    String leftTable = (String) cp.leftTableDropdown.getSelectedItem();
                    String leftCol = (String) cp.leftColDropdown.getSelectedItem();
                    String rightCol = (String) cp.rightColDropdown.getSelectedItem();
                    
                    if (leftTable != null && leftCol != null && rightCol != null) {
                        String leftFormatted = mainFrame.getDbManager().quoteColumnName(leftTable + "." + leftCol);
                        String rightFormatted = mainFrame.getDbManager().quoteColumnName(table + "." + rightCol);
                        
                        sql.append(cp.operator).append(" ").append(leftFormatted).append(" = ").append(rightFormatted).append(" ");
                    }
                }
            }
            if (!wherePanels.isEmpty()) {
                StringBuilder wsb = new StringBuilder("WHERE ");
                int count = 0;
                for (int i = 0; i < wherePanels.size(); i++) {
                    Main.WherePanel wp = wherePanels.get(i);
                    String cond = wp.buildConditionClause(mainFrame.getDbManager());
                    if (cond.isEmpty()) continue;
                    if (count > 0) wsb.append(wp.logicDropdown.getSelectedItem()).append(" ");
                    wsb.append(cond);
                    count++;
                }
                if (count > 0) {
                    sql.append(wsb.toString());
                }
            }
            if (!groupByParts.isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", groupByParts));
            }
            if (!joinHavingPanels.isEmpty()) {
                StringBuilder hsb = new StringBuilder(" HAVING ");
                int hCount = 0;
                for (Main.HavingRowPanel hrp : joinHavingPanels) {
                    String clause = hrp.buildClause(mainFrame.getDbManager());
                    if (clause.isEmpty()) continue;
                    if (hCount > 0) hsb.append(" ").append(hrp.logicDropdown.getSelectedItem()).append(" ");
                    hsb.append(clause);
                    hCount++;
                }
                if (hCount > 0) sql.append(hsb.toString());
            }
            if (!joinOrderByPanels.isEmpty()) {
                sql.append(" ORDER BY ");
                for (int i = 0; i < joinOrderByPanels.size(); i++) {
                    Main.OrderByPanel op = joinOrderByPanels.get(i);
                    String tbl = (String) op.tableDropdown.getSelectedItem();
                    String col = (String) op.columnDropdown.getSelectedItem();
                    String dir = (String) op.dirDropdown.getSelectedItem();
                    if (tbl != null && col != null) {
                        sql.append(mainFrame.getDbManager().quoteColumnName(tbl + "." + col)).append(" ").append(dir);
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

            String lockChoice = (String) joinLockDropdown.getSelectedItem();
            if (lockChoice != null && !lockChoice.equals("Lock: None")) {
                sql.append(" ").append(lockChoice);
            }
            return sql.toString();
        }

    public void executeVisualJoin() {
            String sql = buildCurrentJoinQuery();
            if (sql.isEmpty()) return;
            lastExecutedJoinQuery = sql;
            
            System.out.println("Executing JOIN: " + lastExecutedJoinQuery);
            new Thread(() -> {
                try {
                    DefaultTableModel model = mainFrame.getDbManager().executeQuery(lastExecutedJoinQuery);
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        joinResultTable.setModel(model);
                        // If execution succeeded, learn the joins!
                        for (JoinPanel jp : joinPanels) {
                            String table = (String) jp.joinTableDropdown.getSelectedItem();
                            for (JoinConditionPanel cp : jp.conditionPanels) {
                                String leftTable = (String) cp.leftTableDropdown.getSelectedItem();
                                String leftCol = (String) cp.leftColDropdown.getSelectedItem();
                                String rightCol = (String) cp.rightColDropdown.getSelectedItem();
                                
                                if (table != null && leftTable != null && leftCol != null && rightCol != null) {
                                    mainFrame.getDbManager().learnJoin(leftTable, leftTable + "." + leftCol, table, table + "." + rightCol);
                                }
                            }
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(VisualJoinTabPanel.this, "SQL Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        }
    class JoinConditionPanel extends JPanel {
            String operator;
            Main.SearchableComboBox leftTableDropdown = new Main.SearchableComboBox();
            Main.SearchableComboBox leftColDropdown = new Main.SearchableComboBox();
            JLabel rightTableLabel = new JLabel("");
            Main.SearchableComboBox rightColDropdown = new Main.SearchableComboBox();
            
            public JoinConditionPanel(String operator, boolean isFirst, JoinPanel parent) {
                this.operator = operator;
                com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
                add(com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
                    isFirst
                        ? com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.ON
                        : com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.AND_ON
                ));
                
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    leftTableDropdown, com.dbtool.ui.components.ClauseBadgeFactory.TABLE_WIDTH
                );
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    leftColDropdown, com.dbtool.ui.components.ClauseBadgeFactory.COLUMN_WIDTH
                );
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    rightColDropdown, com.dbtool.ui.components.ClauseBadgeFactory.VALUE_WIDTH
                );
                rightTableLabel.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.BOLD, 11));

                leftTableDropdown.addActionListener(e -> {
                    String t = (String) leftTableDropdown.getSelectedItem();
                    if (t != null) {
                        new Thread(() -> {
                            java.util.List<String> cols = mainFrame.getDbManager().getColumnNames(t);
                            SwingUtilities.invokeLater(() -> {
                                Object currentCol = leftColDropdown.getSelectedItem();
                                leftColDropdown.setAllItems(cols);
                                if (currentCol != null && cols.contains(currentCol)) leftColDropdown.setSelectedItem(currentCol);
                            });
                        }).start();
                    }
                });
    
                add(leftTableDropdown);
                add(com.dbtool.ui.components.ClauseBadgeFactory.createDotSeparator());
                add(leftColDropdown);
                add(com.dbtool.ui.components.ClauseBadgeFactory.createFixedOperatorLabel("="));
                add(rightTableLabel);
                add(rightColDropdown);
                
                if (isFirst) {
                    JButton addConditionBtn = new JButton("+ AND");
                    addConditionBtn.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.BOLD, 11));
                    addConditionBtn.setPreferredSize(new Dimension(64, com.dbtool.ui.components.ClauseBadgeFactory.ROW_HEIGHT));
                    addConditionBtn.setMargin(new Insets(1, 6, 1, 6));
                    addConditionBtn.setToolTipText("Add another AND condition to this JOIN ON clause");
                    addConditionBtn.addActionListener(e -> parent.addNewCondition("AND"));
                    add(addConditionBtn);
                } else {
                    JButton removeConditionBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove AND join condition");
                    removeConditionBtn.addActionListener(e -> parent.removeCondition(this));
                    add(removeConditionBtn);
                }
            }
    
            public void updateLeftTables(java.util.List<String> tables) {
                Object selected = leftTableDropdown.getSelectedItem();
                leftTableDropdown.setAllItems(tables);
                if (selected != null && tables.contains(selected)) leftTableDropdown.setSelectedItem(selected);
            }
    
            public void updateRightColumns(java.util.List<String> cols, String rightTable) {
                rightTableLabel.setText(rightTable + " . ");
                Object selected = rightColDropdown.getSelectedItem();
                rightColDropdown.setAllItems(cols);
                if (selected != null && cols.contains(selected)) rightColDropdown.setSelectedItem(selected);
            }
        }
    class JoinPanel extends JPanel {
            JComboBox<String> joinTypeDropdown = new JComboBox<>(new String[]{
                "JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL OUTER JOIN",
                "CROSS JOIN", "CROSS JOIN LATERAL", "CROSS APPLY", "OUTER APPLY"
            });
            Main.SearchableComboBox joinTableDropdown = new Main.SearchableComboBox();
            
            JPanel headerPanel = new JPanel();
            JPanel conditionsContainer = new JPanel();
            java.util.List<JoinConditionPanel> conditionPanels = new ArrayList<>();
    
            public JoinPanel() {
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(headerPanel);
                conditionsContainer.setLayout(new BoxLayout(conditionsContainer, BoxLayout.Y_AXIS));
                
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    joinTypeDropdown, com.dbtool.ui.components.ClauseBadgeFactory.TABLE_WIDTH
                );
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    joinTableDropdown, com.dbtool.ui.components.ClauseBadgeFactory.COLUMN_WIDTH
                );

                joinTableDropdown.setAllItems(mainFrame.getLoadedTables());
                joinTableDropdown.addActionListener(e -> {
                    joinSelectedColumns.clear();
                    joinSelectColsButton.setText("Columns (All)");
                    updateJoinDropdowns();
                });
    
                headerPanel.add(com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
                    com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.JOIN
                ));
                headerPanel.add(joinTypeDropdown);
                JLabel arrowSep = com.dbtool.ui.components.ClauseBadgeFactory.createDotSeparator();
                arrowSep.setText("➔");
                headerPanel.add(arrowSep);
                headerPanel.add(joinTableDropdown);
                
                JButton removeBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove JOIN clause");
                removeBtn.addActionListener(e -> {
                    joinPanels.remove(this);
                    joinsContainer.remove(this);
                    joinsContainer.revalidate();
                    joinsContainer.repaint();
                    joinSelectedColumns.clear();
                    joinSelectColsButton.setText("Columns (All)");
                    updateJoinDropdowns();
                });
                headerPanel.add(removeBtn);
    
                add(headerPanel);
                add(conditionsContainer);
    
                addNewCondition("ON");
            }
    
            public JoinConditionPanel addNewCondition(String op) {
                JoinConditionPanel cp = new JoinConditionPanel(op, conditionPanels.isEmpty(), this);
                conditionPanels.add(cp);
                conditionsContainer.add(cp);
                revalidate();
                repaint();
                if (conditionPanels.size() > 1) updateJoinDropdowns();
                return cp;
            }
    
            public void removeCondition(JoinConditionPanel cp) {
                conditionPanels.remove(cp);
                conditionsContainer.remove(cp);
                revalidate();
                repaint();
                updateJoinDropdowns();
            }
    
            public void updateLeftTables(java.util.List<String> tables) {
                for (JoinConditionPanel cp : conditionPanels) {
                    cp.updateLeftTables(tables);
                }
            }
    
            public void updateRightColumns(java.util.List<String> cols) {
                String t = (String) joinTableDropdown.getSelectedItem();
                if (t == null) t = "";
                for (JoinConditionPanel cp : conditionPanels) {
                    cp.updateRightColumns(cols, t);
                }
            }
        }
        private final Main mainFrame;

    public VisualJoinTabPanel(Main mainFrame) {
        this.mainFrame = mainFrame;
        this.joinResultTable = mainFrame.new UpdatableTable(() -> (String) baseTableDropdown.getSelectedItem());
            setLayout(new BorderLayout());
            add(createJoinPanel(), BorderLayout.CENTER);
        }
        public void refreshTables() {
            Object sel = baseTableDropdown.getSelectedItem();
            baseTableDropdown.setAllItems(mainFrame.getLoadedTables());
            if (sel != null && mainFrame.getLoadedTables().contains(sel)) baseTableDropdown.setSelectedItem(sel);
            updateJoinDropdowns();
            for (Main.WherePanel wp : wherePanels) {
                wp.fetchTablesFromDb();
            }
        }
        

        public void loadFromSql(String rawSql) {
            try {
                net.sf.jsqlparser.statement.Statement stmt = net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(rawSql);
                if (stmt instanceof net.sf.jsqlparser.statement.select.Select) {
                    net.sf.jsqlparser.statement.select.Select select = (net.sf.jsqlparser.statement.select.Select) stmt;
                    if (select.getSelectBody() instanceof net.sf.jsqlparser.statement.select.PlainSelect) {
                        net.sf.jsqlparser.statement.select.PlainSelect ps = (net.sf.jsqlparser.statement.select.PlainSelect) select.getSelectBody();
                        
                        clearJoins();
                        
                        net.sf.jsqlparser.schema.Table baseTable = (net.sf.jsqlparser.schema.Table) ps.getFromItem();
                        if (baseTable != null && baseTable.getName() != null) {
                            baseTableDropdown.setSelectedItem(baseTable.getName().replace("\"", ""));
                        }
                        
                        java.util.List<net.sf.jsqlparser.statement.select.Join> joins = ps.getJoins();
                        if (joins != null) {
                            for (net.sf.jsqlparser.statement.select.Join join : joins) {
                                addJoinRow();
                                JoinPanel jp = joinPanels.get(joinPanels.size() - 1);
                                
                                if (join.isInner()) jp.joinTypeDropdown.setSelectedItem("INNER JOIN");
                                else if (join.isLeft()) jp.joinTypeDropdown.setSelectedItem("LEFT JOIN");
                                else if (join.isRight()) jp.joinTypeDropdown.setSelectedItem("RIGHT JOIN");
                                else if (join.isFull()) jp.joinTypeDropdown.setSelectedItem("FULL OUTER JOIN");
                                else if (join.isCross()) jp.joinTypeDropdown.setSelectedItem("CROSS JOIN");
                                else jp.joinTypeDropdown.setSelectedItem("JOIN");
                                
                                net.sf.jsqlparser.schema.Table joinTable = (net.sf.jsqlparser.schema.Table) join.getRightItem();
                                if (joinTable != null) jp.joinTableDropdown.setSelectedItem(joinTable.getName().replace("\"", ""));
                                
                                net.sf.jsqlparser.expression.Expression onExp = join.getOnExpression();
                                if (onExp instanceof net.sf.jsqlparser.expression.operators.relational.EqualsTo) {
                                    net.sf.jsqlparser.expression.operators.relational.EqualsTo eq = (net.sf.jsqlparser.expression.operators.relational.EqualsTo) onExp;
                                    
                                    if (eq.getLeftExpression() instanceof net.sf.jsqlparser.schema.Column && eq.getRightExpression() instanceof net.sf.jsqlparser.schema.Column) {
                                        net.sf.jsqlparser.schema.Column leftCol = (net.sf.jsqlparser.schema.Column) eq.getLeftExpression();
                                        net.sf.jsqlparser.schema.Column rightCol = (net.sf.jsqlparser.schema.Column) eq.getRightExpression();
                                        
                                        JoinConditionPanel cp = jp.conditionPanels.get(0);
                                        
                                        if (leftCol.getTable() != null) cp.leftTableDropdown.setSelectedItem(leftCol.getTable().getName().replace("\"", ""));
                                        cp.leftColDropdown.setSelectedItem(leftCol.getColumnName().replace("\"", ""));
                                        
                                        cp.rightColDropdown.setSelectedItem(rightCol.getColumnName().replace("\"", ""));
                                    }
                                }
                            }
                        }
                        updateJoinDropdowns();
                        executeVisualJoin();
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to parse SQL query: " + e.getMessage(), "Parse Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        
        class ImplicitTablePanel extends JPanel {
            Main.SearchableComboBox tableDropdown = new Main.SearchableComboBox();

            public ImplicitTablePanel() {
                com.dbtool.ui.components.ClauseBadgeFactory.styleClauseRow(this);
                add(com.dbtool.ui.components.ClauseBadgeFactory.createBadge(
                    com.dbtool.ui.components.ClauseBadgeFactory.ClauseType.TABLE
                ));
                
                tableDropdown.setAllItems(mainFrame.getLoadedTables());
                com.dbtool.ui.components.ClauseBadgeFactory.setFixedColumnWidth(
                    tableDropdown, com.dbtool.ui.components.ClauseBadgeFactory.TABLE_WIDTH
                );
                tableDropdown.addActionListener(e -> {
                    joinSelectedColumns.clear();
                    joinSelectColsButton.setText("Columns (All)");
                    updateJoinDropdowns();
                });
                add(tableDropdown);
                
                JButton removeBtn = com.dbtool.ui.components.ClauseBadgeFactory.createRemoveButton("Remove implicit table");
                removeBtn.addActionListener(e -> {
                    implicitTablePanels.remove(this);
                    implicitTablesContainer.remove(this);
                    implicitTablesContainer.revalidate();
                    implicitTablesContainer.repaint();
                    joinSelectedColumns.clear();
                    joinSelectColsButton.setText("Columns (All)");
                    updateJoinDropdowns();
                });
                add(removeBtn);
            }
        }

        public void clearJoins() {
            implicitTablesContainer.removeAll();
            implicitTablePanels.clear();
            implicitTablesContainer.revalidate();
            implicitTablesContainer.repaint();
            joinsContainer.removeAll();
            joinPanels.clear();
            joinsContainer.revalidate();
            joinsContainer.repaint();
            joinAggregateContainer.removeAll();
            joinAggregatePanels.clear();
            joinAggregateContainer.revalidate();
            joinAggregateContainer.repaint();
            joinGroupByContainer.removeAll();
            joinGroupByPanels.clear();
            joinGroupByContainer.revalidate();
            joinGroupByContainer.repaint();
            joinHavingContainer.removeAll();
            joinHavingPanels.clear();
            joinHavingContainer.revalidate();
            joinHavingContainer.repaint();
            joinWindowFuncContainer.removeAll();
            joinWindowFuncPanels.clear();
            joinWindowFuncContainer.revalidate();
            joinWindowFuncContainer.repaint();
        }
    }