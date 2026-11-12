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
    public class BrowseTabPanel extends JPanel {
    public Main.SearchableComboBox browseTableDropdown = new Main.SearchableComboBox();
    private JButton selectColsButton = new JButton("Columns (All)");
    private List<String> currentSelectedColumns = new ArrayList<>();
    private JTextField limitField = new JTextField(5);
    private final JTable browseTable;
    private JPanel browseWhereContainer = new JPanel();
    private List<Main.WherePanel> browseWherePanels = new ArrayList<>();
    private JPanel browseOrderByContainer = new JPanel();
    private List<Main.OrderByPanel> browseOrderByPanels = new ArrayList<>();
    private JPanel browseAggregateContainer = new JPanel();
    private List<Main.AggregateRowPanel> browseAggregatePanels = new ArrayList<>();
    private JPanel browseGroupByContainer = new JPanel();
    private List<Main.GroupByRowPanel> browseGroupByPanels = new ArrayList<>();
    private JPanel browseHavingContainer = new JPanel();
    private List<Main.HavingRowPanel> browseHavingPanels = new ArrayList<>();
    private JPanel browseWindowFuncContainer = new JPanel();
    private List<Main.WindowFuncRowPanel> browseWindowFuncPanels = new ArrayList<>();
    private JComboBox<String> browseLockDropdown = new JComboBox<>(new String[]{
        "Lock: None", "FOR UPDATE", "FOR UPDATE SKIP LOCKED", "FOR SHARE", "FOR NO KEY UPDATE"
    });
    private JComboBox<String> browseSampleDropdown = new JComboBox<>(new String[]{
        "Sample: None", "BERNOULLI (10)", "BERNOULLI (5)", "BERNOULLI (1)", "SYSTEM (10)", "SYSTEM (5)"
    });
    private String lastExecutedBrowseQuery = "";
    private int browseCurrentOffset = 0;
    private boolean isBrowseLoading = false;
    private String lastBrowseBaseQuery = "";
    private JCheckBox browseDistinctCheckbox = new JCheckBox("Distinct");

    private JPanel createBrowsePanel() {
            JPanel panel = new JPanel(new BorderLayout());
            
            JPanel tbSelect = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));
    
            
            tbSelect.add(new JLabel(" |  Table: "));
            browseTableDropdown.addActionListener(e -> {
                String selected = (String) browseTableDropdown.getSelectedItem();
                if (selected == null || !mainFrame.getLoadedTables().contains(selected)) return;
    
                currentSelectedColumns.clear();
                selectColsButton.setText("Columns (All)");
                browseWherePanels.clear();
                browseWhereContainer.removeAll();
                browseOrderByPanels.clear();
                browseOrderByContainer.removeAll();
                browseAggregatePanels.clear();
                browseAggregateContainer.removeAll();
                browseGroupByPanels.clear();
                browseGroupByContainer.removeAll();
                browseHavingPanels.clear();
                browseHavingContainer.removeAll();
                browseWindowFuncPanels.clear();
                browseWindowFuncContainer.removeAll();
                browseWhereContainer.revalidate();
                browseWhereContainer.repaint();
                browseAggregateContainer.revalidate();
                browseAggregateContainer.repaint();
                browseGroupByContainer.revalidate();
                browseGroupByContainer.repaint();
                browseHavingContainer.revalidate();
                browseHavingContainer.repaint();
                browseWindowFuncContainer.revalidate();
                browseWindowFuncContainer.repaint();
                refreshOrderByDropdown();
                loadTableData();
            });
            browseTableDropdown.setPreferredSize(new Dimension(200, 26));
            tbSelect.add(browseTableDropdown);

            JButton runBrowseTopBtn = new JButton("▶ Run Query");
            runBrowseTopBtn.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.BOLD, 12));
            runBrowseTopBtn.setForeground(new Color(0, 150, 0));
            runBrowseTopBtn.setToolTipText("Execute query for selected table (Ctrl+Enter / F5)");
            runBrowseTopBtn.addActionListener(e -> loadTableData());
            tbSelect.add(runBrowseTopBtn);
            
            tbSelect.add(new JLabel(" | "));
            selectColsButton.addActionListener(e -> openColumnSelector());
            tbSelect.add(selectColsButton);
            
            tbSelect.add(new JLabel(" |  Limit: "));
            limitField.setPreferredSize(new Dimension(60, 26));
            tbSelect.add(limitField);

            tbSelect.add(new JLabel(" | "));
            JToggleButton compactBuilderBtn = new JToggleButton("▲ Compact Grid");
            compactBuilderBtn.setToolTipText("Collapse secondary query builder toolbars to maximize data grid space");
            tbSelect.add(compactBuilderBtn);

            JToggleButton inspectorToggleBtn = new JToggleButton("📐 Row Inspector");
            inspectorToggleBtn.setToolTipText("Toggle side-by-side TablePlus-style Row Inspector & Quick Editor drawer");
            tbSelect.add(inspectorToggleBtn);

            JButton autoFitBrowseBtn = new JButton("⚡ Auto-Fit");
            autoFitBrowseBtn.setToolTipText("Automatically adjust all column widths to match the length of data contents and headers");
            autoFitBrowseBtn.addActionListener(e -> com.dbtool.ui.components.SmartTableColumnAdjuster.adjustColumnWidths(browseTable));
            tbSelect.add(autoFitBrowseBtn);

            JLabel activeClausesBadge = new JLabel("");
            activeClausesBadge.setForeground(com.dbtool.util.ThemeManager.getPrimaryActionColor());
            activeClausesBadge.setFont(activeClausesBadge.getFont().deriveFont(Font.BOLD, 11.5f));
            tbSelect.add(activeClausesBadge);
            
            JPanel tbQuery = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));

            JButton applyOrderBtn = new JButton("▶ Run Query");
            applyOrderBtn.setFont(com.dbtool.util.ThemeManager.getUiFont(Font.BOLD, 12));
            applyOrderBtn.setForeground(new java.awt.Color(0, 150, 0));
            applyOrderBtn.setToolTipText("Execute query (Ctrl+Enter / F5)");
            applyOrderBtn.addActionListener(e -> loadTableData());
            tbQuery.add(applyOrderBtn);
            tbQuery.add(new JLabel(" | "));

            JButton addWhereBtn = new JButton("+ Add Where");
            addWhereBtn.addActionListener(e -> addBrowseWhereRow());
            tbQuery.add(addWhereBtn);
            
            JButton addAggBtn = new JButton("+ Add Aggregate");
            addAggBtn.setToolTipText("Add dynamic aggregate row: COUNT, SUM, AVG, MIN, MAX with optional FILTER clause");
            addAggBtn.addActionListener(e -> addBrowseAggregateRow());
            tbQuery.add(addAggBtn);

            JButton addGrpBtn = new JButton("+ Add Group By");
            addGrpBtn.setToolTipText("Add GROUP BY column row");
            addGrpBtn.addActionListener(e -> addBrowseGroupByRow());
            tbQuery.add(addGrpBtn);

            JButton addHavBtn = new JButton("+ Add Having");
            addHavBtn.setToolTipText("Add HAVING condition row");
            addHavBtn.addActionListener(e -> addBrowseHavingRow());
            tbQuery.add(addHavBtn);

            JButton addWinBtn = new JButton("+ Add Window Func");
            addWinBtn.setToolTipText("Add Window function row (OVER partition/order/frame)");
            addWinBtn.addActionListener(e -> addBrowseWindowFuncRow());
            tbQuery.add(addWinBtn);

            JButton addBrowseOrderBtn = new JButton("+ Add Order By");
            addBrowseOrderBtn.addActionListener(e -> addBrowseOrderByRow());
            tbQuery.add(addBrowseOrderBtn);

            JPanel tbAdvanced = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));

            browseSampleDropdown.setToolTipText("TABLESAMPLE clause (Bernoulli or System sampling)");
            tbAdvanced.add(browseSampleDropdown);
            browseLockDropdown.setToolTipText("Row-level locking clause (FOR UPDATE, SKIP LOCKED)");
            tbAdvanced.add(browseLockDropdown);
            tbAdvanced.add(new JLabel(" | "));
            tbAdvanced.add(browseDistinctCheckbox);
            tbAdvanced.add(new JLabel(" | "));

            JButton aggregatesBtn = new JButton("📊 Aggregates Dialog");
            aggregatesBtn.setToolTipText("Open full-table aggregate metrics & grouping dialog");
            aggregatesBtn.addActionListener(e -> openBrowseAggregatesDialog());
            tbAdvanced.add(aggregatesBtn);

            JButton explainBtn = new JButton("🔍 EXPLAIN ▾");
            explainBtn.setToolTipText("Inspect execution plan for query (EXPLAIN, ANALYZE, BUFFERS)");
            explainBtn.addActionListener(e -> mainFrame.showExplainMenu(explainBtn, () -> buildCurrentBrowseQuery(false)));
            tbAdvanced.add(explainBtn);
            
            JPanel tbRecordActions = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));
            JButton insertRowBtn = new JButton("➕ Insert Row");
            JButton updateRowBtn = new JButton("✏️ Update Row");
            JButton deleteRowBtn = new JButton("🗑 Delete Row");
            JButton cascadeDeleteBtn = new JButton("⚡ Cascade Delete");
            insertRowBtn.setToolTipText("Insert a new record into the current table");
            updateRowBtn.setToolTipText("Edit & update the selected table row in a dialog (or double-click row)");
            deleteRowBtn.setToolTipText("Permanently delete selected row(s) (or press Delete key)");
            cascadeDeleteBtn.setToolTipText("Inspect foreign key dependencies and safely cascade-delete selected record(s) in a transaction");
            insertRowBtn.addActionListener(e -> openInsertRowDialog());
            updateRowBtn.addActionListener(e -> openUpdateRowDialog());
            deleteRowBtn.addActionListener(e -> deleteSelectedRow());
            cascadeDeleteBtn.addActionListener(e -> openCascadeDeleteDialog());
            tbRecordActions.add(insertRowBtn);
            tbRecordActions.add(updateRowBtn);
            tbRecordActions.add(deleteRowBtn);
            tbRecordActions.add(cascadeDeleteBtn);
            tbRecordActions.add(new JLabel(" | "));

            JButton importDataBtn = new JButton("📥 Import Data");
            JButton mockDataBtn = new JButton("🧪 Mock Data");
            JButton codeGenBtn = new JButton("💻 Generate Code");
            JButton indexAdvisorBtn = new JButton("⚡ Index Advisor");
            importDataBtn.setToolTipText("Import data from CSV, TSV, or Excel (.xlsx) file into this table");
            mockDataBtn.setToolTipText("Generate realistic synthetic test data respecting foreign keys");
            codeGenBtn.setToolTipText("Generate Spring Boot JPA, TypeScript, Python, or C# model code for this table");
            indexAdvisorBtn.setToolTipText("Analyze unindexed foreign keys, redundant indexes, and bloat for this table");
            importDataBtn.addActionListener(e -> openDataImportDialog());
            mockDataBtn.addActionListener(e -> openMockDataDialog());
            codeGenBtn.addActionListener(e -> openCodeGenDialog());
            indexAdvisorBtn.addActionListener(e -> openIndexAdvisorDialog());
            tbRecordActions.add(importDataBtn);
            tbRecordActions.add(mockDataBtn);
            tbRecordActions.add(codeGenBtn);
            tbRecordActions.add(indexAdvisorBtn);
            tbRecordActions.add(new JLabel(" | "));
            
            JCheckBox autoCommitCheck = new JCheckBox("Auto-Commit", true);
            JButton commitBtn = new JButton("Commit");
            JButton rollbackBtn = new JButton("Rollback");
            commitBtn.setEnabled(false);
            rollbackBtn.setEnabled(false);
            autoCommitCheck.addActionListener(e -> {
                boolean ac = autoCommitCheck.isSelected();
                try {
                    if (mainFrame.getDbManager().connection != null) mainFrame.getDbManager().connection.setAutoCommit(ac);
                    commitBtn.setEnabled(!ac);
                    rollbackBtn.setEnabled(!ac);
                } catch (Exception ex) {}
            });
            commitBtn.addActionListener(e -> {
                try { if (mainFrame.getDbManager().connection != null) mainFrame.getDbManager().connection.commit(); JOptionPane.showMessageDialog(this, "Transaction committed."); } catch (Exception ex) {}
            });
            rollbackBtn.addActionListener(e -> {
                try { if (mainFrame.getDbManager().connection != null) mainFrame.getDbManager().connection.rollback(); JOptionPane.showMessageDialog(this, "Transaction rolled back."); loadTableData(); } catch (Exception ex) {}
            });
            
            tbRecordActions.add(autoCommitCheck);
            tbRecordActions.add(commitBtn);
            tbRecordActions.add(rollbackBtn);

            JPanel tbDataTools = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));

            JButton exportMenuBtn = new JButton("📥 Export ▾");
            exportMenuBtn.setToolTipText("Export table records to CSV, Excel, PDF, or SQL Inserts");
            exportMenuBtn.addActionListener(e -> {
                JPopupMenu menu = new JPopupMenu();
                JMenuItem mCsv = new JMenuItem("📄 Export to CSV (.csv)");
                mCsv.addActionListener(ev -> ExportUtil.exportCsv(browseTable, this));
                menu.add(mCsv);

                JMenuItem mExcel = new JMenuItem("📥 Export to Excel (.xlsx)");
                mExcel.addActionListener(ev -> ExportUtil.exportExcel(browseTable, this));
                menu.add(mExcel);

                JMenuItem mPdf = new JMenuItem("📄 Export to PDF (.pdf)");
                mPdf.addActionListener(ev -> ExportUtil.exportPdf(browseTable, this));
                menu.add(mPdf);

                JMenuItem mSql = new JMenuItem("📥 Export to SQL INSERT Statements (.sql)");
                mSql.addActionListener(ev -> {
                    String tbl = (String) browseTableDropdown.getSelectedItem();
                    if (tbl != null) ExportUtil.exportSqlInserts(browseTable, tbl, this);
                });
                menu.add(mSql);

                menu.show(exportMenuBtn, 0, exportMenuBtn.getHeight());
            });

            JButton exportCsvBtn = new JButton("📄 CSV");
            JButton exportPdfBtn = new JButton("📄 PDF");
            JButton exportXlsxBtn = new JButton("📥 Excel");
            JButton exportInsertBtn = new JButton("📥 SQL Inserts");
            JButton chartBtn = new JButton("📊 Chart");
            chartBtn.setToolTipText("Visualize table data as Bar, Line, or Donut Chart");
            JButton copyBtn = new JButton("📋 Copy Data");
            JButton copySqlBtn = new JButton("📋 Copy SQL");
            exportCsvBtn.addActionListener(e -> ExportUtil.exportCsv(browseTable, this));
            exportXlsxBtn.addActionListener(e -> ExportUtil.exportExcel(browseTable, this));
            exportPdfBtn.addActionListener(e -> ExportUtil.exportPdf(browseTable, this));
            chartBtn.addActionListener(e -> {
                if (browseTable.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(this, "No data available to chart. Run a query first.");
                    return;
                }
                String tbl = (String) browseTableDropdown.getSelectedItem();
                new com.dbtool.panels.DataChartDialog(mainFrame, browseTable, tbl).setVisible(true);
            });
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
            JButton openInSqlBtn = new JButton("▶ Open in SQL Editor");
            openInSqlBtn.setToolTipText("Open current query in SQL Editor tab");
            openInSqlBtn.addActionListener(e -> {
                if (!lastExecutedBrowseQuery.isEmpty()) {
                    mainFrame.getSqlEditorPanel().setEditorText(lastExecutedBrowseQuery);
                    mainFrame.getTabbedPane().setSelectedComponent(mainFrame.getSqlEditorPanel());
                } else {
                    JOptionPane.showMessageDialog(this, "Please run a query first.");
                }
            });

            tbDataTools.add(exportMenuBtn);
            tbDataTools.add(exportCsvBtn);
            tbDataTools.add(exportXlsxBtn);
            tbDataTools.add(exportPdfBtn);
            tbDataTools.add(exportInsertBtn);
            tbDataTools.add(new JLabel(" | "));
            tbDataTools.add(chartBtn);
            tbDataTools.add(new JLabel(" | "));
            tbDataTools.add(copyBtn);
            tbDataTools.add(copySqlBtn);
            tbDataTools.add(openInSqlBtn);
            tbDataTools.add(new JLabel(" | "));

            JButton templatesBtn = new JButton("📝 SQL Templates ▾");
            templatesBtn.setToolTipText("Browse advanced SQL snippets & templates (LATERAL, Window frames, Recursive CTEs, Advisory locks, Indexes...)");
            templatesBtn.addActionListener(e -> {
                JPopupMenu menu = new JPopupMenu();
                SqlSnippetsLibrary.populatePopupMenu(menu, sql -> {
                    int opt = JOptionPane.showOptionDialog(this,
                        "Snippet selected:\n\n" + (sql.length() > 220 ? sql.substring(0, 220) + "..." : sql) + "\n\nWhere would you like to use this snippet?",
                        "SQL Template Action",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new String[]{"Open in SQL Editor", "Copy to Clipboard", "Cancel"},
                        "Open in SQL Editor");
                    if (opt == 0) {
                        mainFrame.getSqlEditorPanel().setEditorText(sql);
                        mainFrame.getTabbedPane().setSelectedComponent(mainFrame.getSqlEditorPanel());
                    } else if (opt == 1) {
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
            JButton maskDataBtn = new JButton("🔒 Mask Data");
            maskDataBtn.setToolTipText("Sensitive Data Masking & Anonymization Studio (GDPR/PII)");
            maskDataBtn.addActionListener(e -> mainFrame.openDataMaskingDialog());

            JButton profilerBtn = new JButton("⏱️ Profiler");
            profilerBtn.setToolTipText("SQL Query Profiler & Latency Bottleneck Analyzer");
            profilerBtn.addActionListener(e -> mainFrame.openQueryProfilerDialog());

            tbDataTools.add(templatesBtn);
            tbDataTools.add(cheatSheetBtn);
            tbDataTools.add(new JLabel(" | "));
            tbDataTools.add(maskDataBtn);
            tbDataTools.add(profilerBtn);
    
            // Enable sorting on browseTable
            browseTable.setAutoCreateRowSorter(true);
            com.dbtool.util.TableTooltipUtil.attachHeaderTooltips(browseTable);
            browseTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    
            browseWhereContainer.setLayout(new BoxLayout(browseWhereContainer, BoxLayout.Y_AXIS));
            browseAggregateContainer.setLayout(new BoxLayout(browseAggregateContainer, BoxLayout.Y_AXIS));
            browseGroupByContainer.setLayout(new BoxLayout(browseGroupByContainer, BoxLayout.Y_AXIS));
            browseHavingContainer.setLayout(new BoxLayout(browseHavingContainer, BoxLayout.Y_AXIS));
            browseWindowFuncContainer.setLayout(new BoxLayout(browseWindowFuncContainer, BoxLayout.Y_AXIS));
            browseOrderByContainer.setLayout(new BoxLayout(browseOrderByContainer, BoxLayout.Y_AXIS));
            
            com.dbtool.ui.components.ScrollablePanel northPanel = new com.dbtool.ui.components.ScrollablePanel(new BorderLayout());
            JPanel rowsPanel = new JPanel();
            rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
            rowsPanel.add(tbSelect);
            rowsPanel.add(tbQuery);
            rowsPanel.add(tbAdvanced);
            rowsPanel.add(tbRecordActions);
            rowsPanel.add(tbDataTools);
            northPanel.add(rowsPanel, BorderLayout.NORTH);
            JPanel filtersPanel = new JPanel();
            filtersPanel.setLayout(new BoxLayout(filtersPanel, BoxLayout.Y_AXIS));
            filtersPanel.add(browseWhereContainer);
            filtersPanel.add(browseAggregateContainer);
            filtersPanel.add(browseGroupByContainer);
            filtersPanel.add(browseHavingContainer);
            filtersPanel.add(browseWindowFuncContainer);
            filtersPanel.add(browseOrderByContainer);
            northPanel.add(filtersPanel, BorderLayout.CENTER);
            
            JScrollPane northScroll = new JScrollPane(northPanel);
            northScroll.setBorder(BorderFactory.createEmptyBorder());
            northScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            northScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            northScroll.getVerticalScrollBar().setUnitIncrement(16);

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

            JSplitPane browseSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, northScroll, null);
            browseSplit.setResizeWeight(0.20);
            browseSplit.setDividerSize(6);
            browseSplit.setBorder(BorderFactory.createEmptyBorder());

            final com.dbtool.panels.RowInspectorDrawer[] drawerHolder = new com.dbtool.panels.RowInspectorDrawer[1];
            JSplitPane tableAndInspectorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, browseScroll, null);
            tableAndInspectorSplit.setResizeWeight(1.0);
            tableAndInspectorSplit.setDividerSize(0);
            tableAndInspectorSplit.setBorder(BorderFactory.createEmptyBorder());

            drawerHolder[0] = new com.dbtool.panels.RowInspectorDrawer(
                mainFrame.getDbManager(),
                () -> {
                    inspectorToggleBtn.setSelected(false);
                    tableAndInspectorSplit.setRightComponent(null);
                    tableAndInspectorSplit.setDividerSize(0);
                    tableAndInspectorSplit.revalidate();
                    tableAndInspectorSplit.repaint();
                },
                this::loadTableData
            );

            Runnable syncInspectorSelection = () -> {
                if (!inspectorToggleBtn.isSelected()) return;
                int selRow = browseTable.getSelectedRow();
                String tbl = (String) browseTableDropdown.getSelectedItem();
                if (selRow >= 0 && selRow < browseTable.getRowCount()) {
                    drawerHolder[0].inspectRow(tbl, browseTable, selRow);
                } else {
                    drawerHolder[0].showEmptyState();
                }
            };

            inspectorToggleBtn.addActionListener(e -> {
                if (inspectorToggleBtn.isSelected()) {
                    tableAndInspectorSplit.setRightComponent(drawerHolder[0]);
                    tableAndInspectorSplit.setDividerSize(6);
                    int w = tableAndInspectorSplit.getWidth();
                    tableAndInspectorSplit.setDividerLocation(w > 450 ? w - 350 : 650);
                    syncInspectorSelection.run();
                } else {
                    tableAndInspectorSplit.setRightComponent(null);
                    tableAndInspectorSplit.setDividerSize(0);
                }
                tableAndInspectorSplit.revalidate();
                tableAndInspectorSplit.repaint();
            });

            browseTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    syncInspectorSelection.run();
                }
            });

            Runnable updateClauseBadges = () -> {
                int wCount = browseWherePanels.size();
                int oCount = browseOrderByPanels.size();
                int aCount = browseAggregatePanels.size() + browseGroupByPanels.size() + browseHavingPanels.size() + browseWindowFuncPanels.size();
                if (wCount + oCount + aCount > 0) {
                    activeClausesBadge.setText(String.format("  •  Active Clauses: WHERE (%d) | ORDER (%d) | AGG/GROUP (%d)", wCount, oCount, aCount));
                } else {
                    activeClausesBadge.setText("");
                }
            };

            compactBuilderBtn.addActionListener(e -> {
                boolean compact = compactBuilderBtn.isSelected();
                tbQuery.setVisible(!compact);
                tbAdvanced.setVisible(!compact);
                tbRecordActions.setVisible(!compact);
                tbDataTools.setVisible(!compact);
                filtersPanel.setVisible(!compact);
                compactBuilderBtn.setText(compact ? "▼ Expand Builder" : "▲ Compact Grid");
                updateClauseBadges.run();
                northPanel.revalidate();
                northPanel.repaint();
                SwingUtilities.invokeLater(() -> {
                    browseSplit.setDividerLocation(compact ? 42 : Math.min(240, northPanel.getPreferredSize().height + 12));
                });
            });

            browseSplit.setBottomComponent(tableAndInspectorSplit);
            panel.add(browseSplit, BorderLayout.CENTER);

            // Shortcuts (Ctrl+Enter / F5 / Delete)
            KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK);
            KeyStroke f5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
            Action runAction = new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { loadTableData(); }
            };
            panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ctrlEnter, "runBrowseQuery");
            panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(f5, "runBrowseQuery");
            panel.getActionMap().put("runBrowseQuery", runAction);

            KeyStroke delKey = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
            browseTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(delKey, "deleteRowAction");
            browseTable.getActionMap().put("deleteRowAction", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { deleteSelectedRow(); }
            });

            JPopupMenu tableMenu = browseTable.getComponentPopupMenu();
            if (tableMenu != null) {
                tableMenu.addSeparator();
                JMenuItem inspectDrawerItem = new JMenuItem("📐 Inspect Row in Side Drawer");
                inspectDrawerItem.setFont(inspectDrawerItem.getFont().deriveFont(Font.BOLD));
                inspectDrawerItem.addActionListener(e -> {
                    if (!inspectorToggleBtn.isSelected()) {
                        inspectorToggleBtn.doClick();
                    } else {
                        syncInspectorSelection.run();
                    }
                });
                tableMenu.add(inspectDrawerItem);

                JMenuItem editMenuItem = new JMenuItem("✏️ Update Selected Row (Modal Dialog)...");
                editMenuItem.setFont(editMenuItem.getFont().deriveFont(Font.BOLD));
                editMenuItem.addActionListener(e -> openUpdateRowDialog());
                tableMenu.add(editMenuItem);

                JMenuItem delMenuItem = new JMenuItem("🗑 Delete Selected Row(s)");
                delMenuItem.setFont(delMenuItem.getFont().deriveFont(Font.BOLD));
                delMenuItem.setForeground(new Color(220, 38, 38));
                delMenuItem.addActionListener(e -> deleteSelectedRow());
                tableMenu.add(delMenuItem);

                JMenuItem cascadeDelMenuItem = new JMenuItem("⚡ Cascade Delete & Inspect Dependencies...");
                cascadeDelMenuItem.setFont(cascadeDelMenuItem.getFont().deriveFont(Font.BOLD));
                cascadeDelMenuItem.setForeground(new Color(234, 88, 12));
                cascadeDelMenuItem.addActionListener(e -> openCascadeDeleteDialog());
                tableMenu.add(cascadeDelMenuItem);

                tableMenu.addSeparator();
                JMenuItem codeGenMenuItem = new JMenuItem("💻 Generate Entity / ORM Code...");
                codeGenMenuItem.addActionListener(e -> openCodeGenDialog());
                tableMenu.add(codeGenMenuItem);

                JMenuItem mockDataMenuItem = new JMenuItem("🧪 Generate Mock Test Data...");
                mockDataMenuItem.addActionListener(e -> openMockDataDialog());
                tableMenu.add(mockDataMenuItem);

                JMenuItem importDataMenuItem = new JMenuItem("📥 Import Data (CSV / Excel)...");
                importDataMenuItem.addActionListener(e -> openDataImportDialog());
                tableMenu.add(importDataMenuItem);

                JMenuItem indexAdvisorMenuItem = new JMenuItem("⚡ Table Index & Performance Advisor...");
                indexAdvisorMenuItem.addActionListener(e -> openIndexAdvisorDialog());
                tableMenu.add(indexAdvisorMenuItem);
            }

            browseTable.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        openUpdateRowDialog();
                    }
                }
            });

            return panel;
        }
    private void refreshOrderByDropdown() {
            String tbl = (String) browseTableDropdown.getSelectedItem();
            if (tbl != null) {
                java.util.List<String> cols = mainFrame.getDbManager().getColumnNames(tbl);
                for (Main.OrderByPanel op : browseOrderByPanels) op.updateColumns(cols);
            }
        }
    private boolean showResizableRowFormDialog(String title, List<JLabel> labels, List<JTextField> fields, String okButtonText) {
        JDialog dialog = new JDialog(mainFrame, title, true);
        dialog.setResizable(true);
        dialog.setLayout(new BorderLayout(8, 8));

        // Top search / filter bar for tables with many columns
        JPanel topBar = new JPanel(new BorderLayout(6, 0));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        JLabel filterLbl = new JLabel("🔍 Filter columns (" + fields.size() + "): ");
        JTextField filterField = new JTextField();
        filterField.setToolTipText("Type to filter columns by name...");
        topBar.add(filterLbl, BorderLayout.WEST);
        topBar.add(filterField, BorderLayout.CENTER);
        dialog.add(topBar, BorderLayout.NORTH);

        // Form built with GridBagLayout anchored at NORTH so rows stay compact and stretch horizontally on resize
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(6, 12, 10, 12));

        Runnable rebuildFormRows = () -> {
            form.removeAll();
            String query = filterField.getText().trim().toLowerCase(java.util.Locale.ROOT);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            int rowIdx = 0;
            for (int i = 0; i < labels.size(); i++) {
                JLabel lbl = labels.get(i);
                JTextField tf = fields.get(i);
                if (!query.isEmpty() && !lbl.getText().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                    continue;
                }
                gbc.gridx = 0;
                gbc.gridy = rowIdx;
                gbc.weightx = 0.0;
                gbc.fill = GridBagConstraints.NONE;
                form.add(lbl, gbc);

                gbc.gridx = 1;
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                form.add(tf, gbc);
                rowIdx++;
            }
            form.revalidate();
            form.repaint();
        };
        rebuildFormRows.run();

        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { rebuildFormRows.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { rebuildFormRows.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { rebuildFormRows.run(); }
        });

        JPanel formNorthWrapper = new JPanel(new BorderLayout());
        formNorthWrapper.add(form, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(
                formNorthWrapper,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(18);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 12),
                scrollPane.getBorder()
        ));
        dialog.add(scrollPane, BorderLayout.CENTER);

        final boolean[] confirmed = {false};
        JPanel bottomBar = new JPanel(new BorderLayout(8, 0));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 10, 12));
        JLabel tipLabel = new JLabel("💡 Drag edges to resize • Scroll with mouse wheel");
        tipLabel.setFont(tipLabel.getFont().deriveFont(Font.ITALIC, 11f));
        bottomBar.add(tipLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton okBtn = new JButton(okButtonText);
        okBtn.setFont(okBtn.getFont().deriveFont(Font.BOLD));
        JButton cancelBtn = new JButton("Cancel");

        okBtn.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        bottomBar.add(btnPanel, BorderLayout.EAST);
        dialog.add(bottomBar, BorderLayout.SOUTH);

        dialog.getRootPane().setDefaultButton(okBtn);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxH = Math.min(640, (int) (screen.height * 0.78));
        int desiredH = Math.min(maxH, Math.max(280, fields.size() * 36 + 150));
        dialog.setMinimumSize(new Dimension(420, 260));
        dialog.setSize(580, desiredH);
        dialog.setLocationRelativeTo(mainFrame);
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
        dialog.setVisible(true);

        return confirmed[0];
    }

    private void openInsertRowDialog() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl == null || tbl.trim().isEmpty()) return;
        List<String> cols = mainFrame.getDbManager().getColumnNames(tbl);
        if (cols.isEmpty()) return;

        List<JLabel> labels = new ArrayList<>();
        List<JTextField> fields = new ArrayList<>();
        for (String col : cols) {
            labels.add(new JLabel(col + ":"));
            JTextField tf = new JTextField(22);
            fields.add(tf);
        }
        if (!showResizableRowFormDialog("➕ Insert Row into " + tbl, labels, fields, "Insert Row")) return;

        StringBuilder sql = new StringBuilder("INSERT INTO " + mainFrame.getDbManager().quoteTableName(tbl) + " (");
        StringBuilder vals = new StringBuilder(") VALUES (");
        List<Object> params = new ArrayList<>();

        boolean first = true;
        for (int i = 0; i < cols.size(); i++) {
            String val = fields.get(i).getText().trim();
            // If field is left empty, skip to let the database assign default/auto-increment value
            if (val.isEmpty()) continue;

            if (!first) {
                sql.append(", ");
                vals.append(", ");
            }
            first = false;

            sql.append(mainFrame.getDbManager().quoteColumnName(cols.get(i)));
            vals.append("?");
            params.add(val);
        }

        if (params.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No values were provided to insert.", "Insert Skipped", JOptionPane.WARNING_MESSAGE);
            return;
        }

        sql.append(vals).append(")");
        try {
            mainFrame.getDbManager().executeUpdate(sql.toString(), params);
            JOptionPane.showMessageDialog(this, "Row inserted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            loadTableData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Insert failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openUpdateRowDialog() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl == null || tbl.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No table is currently selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int viewRow = browseTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row from the table to update.", "No Row Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = browseTable.convertRowIndexToModel(viewRow);
        DefaultTableModel model = (DefaultTableModel) browseTable.getModel();
        int totalCols = model.getColumnCount();
        if (totalCols == 0) return;

        List<String> pks = mainFrame.getDbManager().getPrimaryKeys(tbl);

        List<JLabel> labels = new ArrayList<>();
        List<JTextField> fields = new ArrayList<>();
        List<Object> oldValues = new ArrayList<>();
        List<String> colNames = new ArrayList<>();

        for (int c = 0; c < totalCols; c++) {
            String colName = model.getColumnName(c);
            String cleanCol = colName.contains(".") ? colName.split("\\.", 2)[1] : colName;
            colNames.add(cleanCol);

            boolean isPk = false;
            for (String pk : pks) {
                if (pk.equalsIgnoreCase(cleanCol)) { isPk = true; break; }
            }

            JLabel lbl = new JLabel(cleanCol + (isPk ? " (PK):" : ":"));
            if (isPk) {
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
                lbl.setForeground(new Color(16, 185, 129));
            }
            labels.add(lbl);

            Object currentVal = model.getValueAt(modelRow, c);
            oldValues.add(currentVal);

            JTextField tf = new JTextField(currentVal != null ? currentVal.toString() : "", 22);
            fields.add(tf);
        }

        if (!showResizableRowFormDialog("✏️ Update Row in " + tbl, labels, fields, "Update Row")) return;

        // Collect changed fields
        List<String> changedCols = new ArrayList<>();
        List<Object> newValues = new ArrayList<>();
        for (int c = 0; c < totalCols; c++) {
            String oldValStr = oldValues.get(c) != null ? oldValues.get(c).toString() : "";
            String newValStr = fields.get(c).getText().trim();
            if (!newValStr.equals(oldValStr)) {
                changedCols.add(colNames.get(c));
                newValues.add(newValStr.isEmpty() ? null : newValStr);
            }
        }

        if (changedCols.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No columns were modified.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder sql = new StringBuilder("UPDATE " + mainFrame.getDbManager().quoteTableName(tbl) + " SET ");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < changedCols.size(); i++) {
            sql.append(mainFrame.getDbManager().quoteColumnName(changedCols.get(i))).append(" = ?");
            params.add(newValues.get(i));
            if (i < changedCols.size() - 1) sql.append(", ");
        }

        sql.append(" WHERE ");

        // Primary key targeting
        List<Integer> pkIndices = new ArrayList<>();
        List<String> matchedPks = new ArrayList<>();
        for (String pk : pks) {
            for (int c = 0; c < totalCols; c++) {
                if (colNames.get(c).equalsIgnoreCase(pk)) {
                    pkIndices.add(c);
                    matchedPks.add(colNames.get(c));
                    break;
                }
            }
        }

        if (!pks.isEmpty() && pkIndices.size() == pks.size()) {
            for (int i = 0; i < pkIndices.size(); i++) {
                int c = pkIndices.get(i);
                Object val = oldValues.get(c);
                sql.append(mainFrame.getDbManager().quoteColumnName(matchedPks.get(i)));
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
            for (int c = 0; c < totalCols; c++) {
                Object val = oldValues.get(c);
                if (!first) sql.append(" AND ");
                first = false;
                sql.append(mainFrame.getDbManager().quoteColumnName(colNames.get(c)));
                if (val == null) {
                    sql.append(" IS NULL");
                } else {
                    sql.append(" = ?");
                    params.add(val);
                }
            }
        }

        try {
            mainFrame.getDbManager().executeUpdate(sql.toString(), params);
            JOptionPane.showMessageDialog(this, "Row updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            loadTableData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Update failed:\n" + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedRow() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl == null || tbl.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No table is currently selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int[] rows = browseTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select at least one row from the table to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to permanently delete " + rows.length + " selected row(s) from table '" + tbl + "'?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        DefaultTableModel model = (DefaultTableModel) browseTable.getModel();
        int totalCols = model.getColumnCount();
        if (totalCols == 0) return;

        // 1. Detect Primary Key columns for table 'tbl'
        List<String> pkNames = mainFrame.getDbManager().getPrimaryKeys(tbl);

        // 2. Map PK names to model column indices if present in the current view
        List<Integer> pkColIndices = new ArrayList<>();
        List<String> matchedPkNames = new ArrayList<>();
        for (String pk : pkNames) {
            for (int c = 0; c < totalCols; c++) {
                String colHeader = model.getColumnName(c);
                String cleanHeader = colHeader.contains(".") ? colHeader.split("\\.", 2)[1] : colHeader;
                if (cleanHeader.equalsIgnoreCase(pk)) {
                    pkColIndices.add(c);
                    matchedPkNames.add(cleanHeader);
                    break;
                }
            }
        }

        boolean usePk = !pkNames.isEmpty() && (pkColIndices.size() == pkNames.size());

        int deleted = 0;
        List<String> errors = new ArrayList<>();

        for (int viewRow : rows) {
            int modelRow = browseTable.convertRowIndexToModel(viewRow);
            StringBuilder sql = new StringBuilder("DELETE FROM " + mainFrame.getDbManager().quoteTableName(tbl) + " WHERE ");
            List<Object> params = new ArrayList<>();

            if (usePk) {
                // Delete specifically by Primary Key(s) - 100% reliable across all database engines
                for (int i = 0; i < pkColIndices.size(); i++) {
                    int c = pkColIndices.get(i);
                    String pkCol = matchedPkNames.get(i);
                    Object val = model.getValueAt(modelRow, c);
                    sql.append(mainFrame.getDbManager().quoteColumnName(pkCol));
                    if (val == null) {
                        sql.append(" IS NULL");
                    } else {
                        sql.append(" = ?");
                        params.add(val);
                    }
                    if (i < pkColIndices.size() - 1) {
                        sql.append(" AND ");
                    }
                }
            } else {
                // No PK available or PK not visible: match by all visible columns in the row
                boolean first = true;
                for (int c = 0; c < totalCols; c++) {
                    String colHeader = model.getColumnName(c);
                    String cleanCol = colHeader.contains(".") ? colHeader.split("\\.", 2)[1] : colHeader;
                    Object val = model.getValueAt(modelRow, c);

                    if (!first) sql.append(" AND ");
                    first = false;

                    sql.append(mainFrame.getDbManager().quoteColumnName(cleanCol));
                    if (val == null) {
                        sql.append(" IS NULL");
                    } else {
                        sql.append(" = ?");
                        params.add(val);
                    }
                }
                // Only MySQL supports LIMIT on DELETE; omit on Postgres, SQLite, and Access
                if (mainFrame.getDbManager().isMySQL()) {
                    sql.append(" LIMIT 1");
                }
            }

            try {
                mainFrame.getDbManager().executeUpdate(sql.toString(), params);
                deleted++;
            } catch (Exception ex) {
                String msg = ex.getMessage();
                if (msg != null && !errors.contains(msg)) {
                    errors.add(msg);
                }
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder errMsg = new StringBuilder();
            errMsg.append("Could not delete ").append(rows.length - deleted).append(" row(s).\n\n");
            errMsg.append("Reason:\n");
            String childTable = null;
            boolean isFkViolation = false;
            for (String err : errors) {
                errMsg.append("• ").append(err).append("\n");
                if (err.toLowerCase().contains("foreign key constraint") || err.toLowerCase().contains("clé étrangère") || err.contains("23503")) {
                    isFkViolation = true;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:table [«\"]|from table [«\"])([^»\"]+)[»\"]").matcher(err);
                    if (m.find()) {
                        childTable = m.group(1);
                    }
                }
            }

            if (isFkViolation) {
                final String targetChildTable = childTable;
                List<Object> optionsList = new ArrayList<>();
                optionsList.add("⚡ Open Cascade Delete Assistant");
                if (targetChildTable != null && mainFrame.getLoadedTables().contains(targetChildTable)) {
                    optionsList.add("🔍 View Child Rows in '" + targetChildTable + "'");
                }
                optionsList.add("OK");
                Object[] options = optionsList.toArray();
                int choice = JOptionPane.showOptionDialog(this,
                    errMsg.toString() + "\n💡 Tip: Relational integrity requires deleting or reassigning child records first.\nWould you like SchemaSync to safely inspect dependencies and prepare an atomic cascade deletion?",
                    "Foreign Key Constraint Blocked Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);

                if (choice == 0) {
                    openCascadeDeleteDialog();
                } else if (choice == 1 && targetChildTable != null && mainFrame.getLoadedTables().contains(targetChildTable)) {
                    browseTableDropdown.setSelectedItem(targetChildTable);
                }
            } else {
                JOptionPane.showMessageDialog(this, errMsg.toString(), "Delete Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Successfully deleted " + deleted + " row(s).", "Delete Success", JOptionPane.INFORMATION_MESSAGE);
        }

        loadTableData();
    }

    private void openCascadeDeleteDialog() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl == null || tbl.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No table is currently selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int[] rows = browseTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select at least one row from the table to inspect cascade dependencies.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DefaultTableModel model = (DefaultTableModel) browseTable.getModel();
        int totalCols = model.getColumnCount();
        if (totalCols == 0) return;

        List<Map<String, Object>> targetRows = new ArrayList<>();
        for (int viewRow : rows) {
            int modelRow = browseTable.convertRowIndexToModel(viewRow);
            Map<String, Object> rowVals = new LinkedHashMap<>();
            for (int c = 0; c < totalCols; c++) {
                String colHeader = model.getColumnName(c);
                String cleanHeader = colHeader.contains(".") ? colHeader.split("\\.", 2)[1] : colHeader;
                rowVals.put(cleanHeader, model.getValueAt(modelRow, c));
            }
            targetRows.add(rowVals);
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            com.dbtool.DatabaseManager.CascadePlan plan = mainFrame.getDbManager().buildCascadePlan(tbl, targetRows);
            setCursor(Cursor.getDefaultCursor());
            new com.dbtool.panels.CascadeDeleteDialog(mainFrame, mainFrame.getDbManager(), plan, this::loadTableData).setVisible(true);
        } catch (Exception ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, "Failed to inspect foreign key dependencies:\n" + ex.getMessage(), "Inspection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openCodeGenDialog() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl == null || tbl.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No table is currently selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new com.dbtool.panels.EntityCodeGeneratorDialog(mainFrame, mainFrame.getDbManager(), tbl).setVisible(true);
    }

    private void openMockDataDialog() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        if (tbl == null || tbl.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No table is currently selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new com.dbtool.panels.MockDataGeneratorDialog(mainFrame, mainFrame.getDbManager(), tbl, this::loadTableData).setVisible(true);
    }

    private void openDataImportDialog() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        new com.dbtool.panels.DataImporterDialog(mainFrame, mainFrame.getDbManager(), tbl, this::loadTableData).setVisible(true);
    }

    private void openIndexAdvisorDialog() {
        String tbl = (String) browseTableDropdown.getSelectedItem();
        new com.dbtool.panels.IndexAdvisorDialog(mainFrame, mainFrame.getDbManager(), tbl).setVisible(true);
    }

    private void openColumnSelector() {
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            if (selectedTable == null) return;
            List<String> rawCols = mainFrame.getDbManager().getColumnNames(selectedTable);
            if (rawCols == null || rawCols.isEmpty()) return;
            List<String> allCols = new ArrayList<>(new LinkedHashSet<>(rawCols));
            if (allCols.isEmpty()) return;
    
            JDialog dialog = new JDialog(mainFrame, "Select Columns — " + selectedTable, true);
            dialog.setResizable(true);
            dialog.setLayout(new BorderLayout(6, 6));
            JPanel cbPanel = new JPanel();
            cbPanel.setLayout(new BoxLayout(cbPanel, BoxLayout.Y_AXIS));
            cbPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            
            Set<String> selectedSet = new LinkedHashSet<>(currentSelectedColumns);
            Set<String> addedCheckboxes = new HashSet<>();
            List<JCheckBox> checkBoxes = new ArrayList<>();
            for (String col : allCols) {
                if (!addedCheckboxes.add(col.trim())) continue;
                JCheckBox cb = new JCheckBox(col.trim());
                if (selectedSet.isEmpty() || selectedSet.contains(col.trim())) {
                    cb.setSelected(true);
                }
                checkBoxes.add(cb);
                cbPanel.add(cb);
            }
            
            JScrollPane scroll = new JScrollPane(cbPanel);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            dialog.add(scroll, BorderLayout.CENTER);
            
            // Search bar + Select All / Unselect All header
            JPanel northContainer = new JPanel(new BorderLayout(4, 4));
            northContainer.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));

            JPanel searchRow = new JPanel(new BorderLayout(6, 0));
            JLabel searchLbl = new JLabel("🔍 Search:");
            JTextField searchField = new JTextField();
            searchField.setToolTipText("Type to filter columns by name...");
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
                for (JCheckBox cb : checkBoxes) if (cb.isSelected()) sel++;
                countLabel.setText("Selected: " + sel + " / " + checkBoxes.size());
            };
            for (JCheckBox cb : checkBoxes) {
                cb.addActionListener(e -> updateCountLabel.run());
            }
            updateCountLabel.run();

            Runnable filterColumns = () -> {
                String q = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                cbPanel.removeAll();
                for (JCheckBox cb : checkBoxes) {
                    if (q.isEmpty() || cb.getText().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        cbPanel.add(cb);
                    }
                }
                cbPanel.revalidate();
                cbPanel.repaint();
            };

            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filterColumns.run(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filterColumns.run(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filterColumns.run(); }
            });

            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
            JButton selectAllBtn = new JButton("Select All");
            JButton unselectAllBtn = new JButton("Unselect All");
            selectAllBtn.addActionListener(e -> {
                String q = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                for (JCheckBox cb : checkBoxes) {
                    if (q.isEmpty() || cb.getText().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        cb.setSelected(true);
                    }
                }
                updateCountLabel.run();
            });
            unselectAllBtn.addActionListener(e -> {
                String q = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                for (JCheckBox cb : checkBoxes) {
                    if (q.isEmpty() || cb.getText().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        cb.setSelected(false);
                    }
                }
                updateCountLabel.run();
            });
            topPanel.add(selectAllBtn);
            topPanel.add(unselectAllBtn);

            northContainer.add(searchRow, BorderLayout.NORTH);
            northContainer.add(topPanel, BorderLayout.SOUTH);
            dialog.add(northContainer, BorderLayout.NORTH);
            
            JButton okBtn = new JButton("OK");
            okBtn.setFont(okBtn.getFont().deriveFont(Font.BOLD));
            okBtn.addActionListener(e -> {
                currentSelectedColumns.clear();
                Set<String> chosen = new LinkedHashSet<>();
                for (JCheckBox cb : checkBoxes) {
                    if (cb.isSelected()) {
                        chosen.add(cb.getText().trim());
                    }
                }
                currentSelectedColumns.addAll(chosen);
                if (currentSelectedColumns.size() == allCols.size() || currentSelectedColumns.isEmpty()) {
                    currentSelectedColumns.clear();
                    selectColsButton.setText("Columns (All)");
                } else {
                    selectColsButton.setText("Columns (" + currentSelectedColumns.size() + ")");
                }
                dialog.dispose();
                loadTableData();
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
            
            dialog.setSize(380, 480);
            dialog.setLocationRelativeTo(this);
            SwingUtilities.invokeLater(searchField::requestFocusInWindow);
            dialog.setVisible(true);
    }

    private void openBrowseAggregatesDialog() {
        String selectedTable = (String) browseTableDropdown.getSelectedItem();
        if (selectedTable == null) {
            JOptionPane.showMessageDialog(this, "Please select a table first.", "No Table Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> cols = mainFrame.getDbManager().getColumnNames(selectedTable);
        if (cols.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No columns found for table: " + selectedTable, "Empty Table", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(mainFrame, "📊 Aggregates & Group By — " + selectedTable, true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setSize(680, 560);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel mainForm = new JPanel(new GridBagLayout());
        mainForm.setBorder(BorderFactory.createEmptyBorder(10, 14, 8, 14));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Group By row
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.35;
        mainForm.add(new JLabel("GROUP BY Column:"), gbc);
        JComboBox<String> groupByCb = new JComboBox<>();
        groupByCb.addItem("-- None (Overall Table Summary) --");
        for (String c : cols) groupByCb.addItem(c);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(groupByCb, gbc);

        // Header
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel aggHeader = new JLabel("Aggregate Metrics (COUNT, SUM, AVG, MIN, MAX):");
        aggHeader.setFont(aggHeader.getFont().deriveFont(Font.BOLD));
        mainForm.add(aggHeader, gbc);
        gbc.gridwidth = 1;

        // 1. COUNT(*)
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.35;
        JCheckBox countAllCb = new JCheckBox("COUNT(*) Total Rows", true);
        mainForm.add(countAllCb, gbc);
        JTextField countAllAlias = new JTextField("total_count", 10);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(countAllAlias, gbc);

        // 2. COUNT(DISTINCT col)
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.35;
        JCheckBox countDistCb = new JCheckBox("COUNT(DISTINCT):");
        mainForm.add(countDistCb, gbc);
        JPanel cdP = new JPanel(new BorderLayout(4, 0));
        JComboBox<String> countDistCol = new JComboBox<>(cols.toArray(new String[0]));
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
        JComboBox<String> sumCol = new JComboBox<>(cols.toArray(new String[0]));
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
        JComboBox<String> avgCol = new JComboBox<>(cols.toArray(new String[0]));
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
        JComboBox<String> minCol = new JComboBox<>(cols.toArray(new String[0]));
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
        JComboBox<String> maxCol = new JComboBox<>(cols.toArray(new String[0]));
        JTextField maxAlias = new JTextField("max_val", 10);
        maxP.add(maxCol, BorderLayout.CENTER);
        maxP.add(maxAlias, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(maxP, gbc);

        // HAVING Clause
        gbc.gridx = 0; gbc.gridy = 8; gbc.weightx = 0.35;
        mainForm.add(new JLabel("HAVING (Optional):"), gbc);
        JTextField havingField = new JTextField(20);
        havingField.setToolTipText("e.g. COUNT(*) > 1 or AVG(salary) > 50000");
        gbc.gridx = 1; gbc.weightx = 0.65;
        mainForm.add(havingField, gbc);

        // Preview Area
        JTextArea previewArea = new JTextArea(4, 40);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Generated SQL Statement"));

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
            q.append(" FROM ").append(mainFrame.getDbManager().quoteTableName(selectedTable));
            String where = buildBrowseWhereClause();
            if (where != null && !where.isEmpty()) {
                q.append(where);
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
        countAllAlias.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
        });
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
        JButton runBtn = new JButton("▶ Run Aggregate Query");
        runBtn.setFont(runBtn.getFont().deriveFont(Font.BOLD));
        runBtn.setForeground(new Color(0, 150, 0));
        JButton openSqlBtn = new JButton("Open in SQL Editor");
        JButton copySqlBtn = new JButton("📋 Copy SQL");
        JButton closeBtn = new JButton("Close");

        runBtn.addActionListener(e -> {
            String sql = previewArea.getText();
            dialog.dispose();
            lastExecutedBrowseQuery = sql;
            new Thread(() -> {
                try {
                    DefaultTableModel model = mainFrame.getDbManager().executeQuery(sql);
                    SwingUtilities.invokeLater(() -> browseTable.setModel(model));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Error executing aggregate query: " + ex.getMessage(), "Query Error", JOptionPane.ERROR_MESSAGE));
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
            JOptionPane.showMessageDialog(dialog, "Aggregate SQL copied to clipboard!");
        });

        closeBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(copySqlBtn);
        btnPanel.add(openSqlBtn);
        btnPanel.add(runBtn);
        btnPanel.add(closeBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private String buildCurrentBrowseQuery(boolean includeLimit) {
        String selectedTable = (String) browseTableDropdown.getSelectedItem();
        if (selectedTable == null) return "";

        List<String> selectParts = new ArrayList<>();
        for (Main.AggregateRowPanel arp : browseAggregatePanels) {
            String expr = arp.buildExpression(mainFrame.getDbManager());
            if (!expr.isEmpty()) selectParts.add(expr);
        }
        for (Main.WindowFuncRowPanel wfp : browseWindowFuncPanels) {
            String expr = wfp.buildExpression(mainFrame.getDbManager());
            if (!expr.isEmpty()) selectParts.add(expr);
        }

        List<String> groupByParts = new ArrayList<>();
        for (Main.GroupByRowPanel grp : browseGroupByPanels) {
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
            if (!currentSelectedColumns.isEmpty()) {
                for (String c : new LinkedHashSet<>(currentSelectedColumns)) {
                    String q = mainFrame.getDbManager().quoteColumnName(c);
                    if (added.add(q)) combined.add(q);
                }
            }
            for (String part : selectParts) {
                if (added.add(part)) combined.add(part);
            }
            cols = String.join(", ", combined);
        } else if (!currentSelectedColumns.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            Set<String> uniqueCols = new LinkedHashSet<>(currentSelectedColumns);
            int i = 0;
            for (String c : uniqueCols) {
                sb.append(mainFrame.getDbManager().quoteColumnName(c));
                if (++i < uniqueCols.size()) sb.append(", ");
            }
            cols = sb.toString();
        } else {
            cols = "*";
        }

        String distinctStr = browseDistinctCheckbox.isSelected() ? "DISTINCT " : "";
        String sampleStr = "";
        String sampleChoice = (String) browseSampleDropdown.getSelectedItem();
        if (sampleChoice != null && !sampleChoice.equals("Sample: None")) {
            sampleStr = " TABLESAMPLE " + sampleChoice;
        }

        StringBuilder q = new StringBuilder();
        q.append("SELECT ").append(distinctStr).append(cols)
         .append(" FROM ").append(mainFrame.getDbManager().quoteTableName(selectedTable))
         .append(sampleStr);

        q.append(buildBrowseWhereClause());

        if (!groupByParts.isEmpty()) {
            q.append(" GROUP BY ").append(String.join(", ", groupByParts));
        }

        if (!browseHavingPanels.isEmpty()) {
            StringBuilder hsb = new StringBuilder(" HAVING ");
            int hCount = 0;
            for (Main.HavingRowPanel hrp : browseHavingPanels) {
                String clause = hrp.buildClause(mainFrame.getDbManager());
                if (clause.isEmpty()) continue;
                if (hCount > 0) hsb.append(" ").append(hrp.logicDropdown.getSelectedItem()).append(" ");
                hsb.append(clause);
                hCount++;
            }
            if (hCount > 0) q.append(hsb.toString());
        }

        if (!browseOrderByPanels.isEmpty()) {
            StringBuilder osb = new StringBuilder(" ORDER BY ");
            for (int i = 0; i < browseOrderByPanels.size(); i++) {
                Main.OrderByPanel op = browseOrderByPanels.get(i);
                String col = (String) op.columnDropdown.getSelectedItem();
                String dir = (String) op.dirDropdown.getSelectedItem();
                if (col != null) {
                    osb.append(mainFrame.getDbManager().quoteColumnName(col)).append(" ").append(dir);
                    if (i < browseOrderByPanels.size() - 1) osb.append(", ");
                }
            }
            q.append(osb.toString());
        }

        if (includeLimit) {
            String limitStr = limitField.getText().trim();
            if (!limitStr.isEmpty()) {
                try { q.append(" LIMIT ").append(Integer.parseInt(limitStr)); } catch (Exception ignored) {}
            }
            String lockChoice = (String) browseLockDropdown.getSelectedItem();
            if (lockChoice != null && !lockChoice.equals("Lock: None")) {
                q.append(" ").append(lockChoice);
            }
        }

        return q.toString();
    }

    public void loadTableData() {
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            if (selectedTable == null) return;
            
            lastBrowseBaseQuery = buildCurrentBrowseQuery(false);
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
            
            String lockChoice = (String) browseLockDropdown.getSelectedItem();
            String lockStr = (lockChoice != null && !lockChoice.equals("Lock: None")) ? " " + lockChoice : "";
            String query = lastBrowseBaseQuery + " LIMIT " + fetchLimit + " OFFSET " + browseCurrentOffset + lockStr;
            
            if (!append) {
                lastExecutedBrowseQuery = query;
                browseTable.setModel(new DefaultTableModel(new Object[][]{{"Loading..."}}, new String[]{"Status"}));
            }
            
            final String finalQuery = query;
            final int currentLimit = fetchLimit;
            final boolean tryVirtual = !append && userLimit == null && lockStr.isEmpty()
                    && mainFrame.getDbManager().resolveDialect().supportsFeature(com.dbtool.core.dialect.SqlFeature.OFFSET_LIMIT_PAGING);

            com.dbtool.core.concurrency.TaskCoordinator.shared().submitRunnable("Browse Fetch", () -> {
                try {
                    DefaultTableModel fetchedModel = null;
                    if (tryVirtual) {
                        try {
                            fetchedModel = mainFrame.getDbManager().createVirtualQueryModel(lastBrowseBaseQuery, 500);
                        } catch (Exception ignoredVirtualFallback) {
                            fetchedModel = null;
                        }
                    }
                    final DefaultTableModel model = fetchedModel != null ? fetchedModel : mainFrame.getDbManager().executeQuery(finalQuery);
                    final boolean usedVirtual = fetchedModel != null;
                    SwingUtilities.invokeLater(() -> {
                        if (append) {
                            if (!usedVirtual && !(browseTable.getModel() instanceof com.dbtool.ui.VirtualResultTableModel)
                                    && browseTable.getModel() instanceof DefaultTableModel
                                    && browseTable.getModel().getColumnCount() == model.getColumnCount()) {
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

                        if (!usedVirtual && model.getRowCount() > 0) {
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
                        try (java.sql.Connection c = mainFrame.getDbManager().borrowConnection();
                             java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT current_database(), current_schema()")) {
                            if (rs.next()) debugInfo += "DB: " + rs.getString(1) + ", Schema: " + rs.getString(2) + "\n";
                        } catch (Exception e) {}
                        JOptionPane.showMessageDialog(mainFrame, debugInfo + "Error fetching data: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
        }
    private String buildBrowseWhereClause() {
            if (browseWherePanels.isEmpty()) return "";
            StringBuilder sb = new StringBuilder(" WHERE ");
            int count = 0;
            for (int i = 0; i < browseWherePanels.size(); i++) {
                Main.WherePanel wp = browseWherePanels.get(i);
                String cond = wp.buildConditionClause(mainFrame.getDbManager());
                if (cond.isEmpty()) continue;
                if (count > 0) sb.append(wp.logicDropdown.getSelectedItem()).append(" ");
                sb.append(cond);
                count++;
            }
            return count > 0 ? sb.toString() : "";
        }
    private void addBrowseAggregateRow() {
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            List<String> cols = selectedTable != null ? mainFrame.getDbManager().getColumnNames(selectedTable) : new ArrayList<>();
            Main.AggregateRowPanel row = mainFrame.new AggregateRowPanel(false, browseAggregatePanels, browseAggregateContainer);
            row.updateColumns(cols);
            browseAggregatePanels.add(row);
            browseAggregateContainer.add(row);
            browseAggregateContainer.revalidate();
            browseAggregateContainer.repaint();
        }
    private void addBrowseGroupByRow() {
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            List<String> cols = selectedTable != null ? mainFrame.getDbManager().getColumnNames(selectedTable) : new ArrayList<>();
            Main.GroupByRowPanel row = mainFrame.new GroupByRowPanel(false, browseGroupByPanels, browseGroupByContainer);
            row.updateColumns(cols);
            browseGroupByPanels.add(row);
            browseGroupByContainer.add(row);
            browseGroupByContainer.revalidate();
            browseGroupByContainer.repaint();
        }
    private void addBrowseHavingRow() {
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            List<String> cols = selectedTable != null ? mainFrame.getDbManager().getColumnNames(selectedTable) : new ArrayList<>();
            boolean isFirst = browseHavingPanels.isEmpty();
            Main.HavingRowPanel row = mainFrame.new HavingRowPanel(isFirst, false, browseHavingPanels, browseHavingContainer);
            row.updateColumns(cols);
            browseHavingPanels.add(row);
            browseHavingContainer.add(row);
            browseHavingContainer.revalidate();
            browseHavingContainer.repaint();
        }
    private void addBrowseWindowFuncRow() {
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            List<String> cols = selectedTable != null ? mainFrame.getDbManager().getColumnNames(selectedTable) : new ArrayList<>();
            Main.WindowFuncRowPanel row = mainFrame.new WindowFuncRowPanel(browseWindowFuncPanels, browseWindowFuncContainer);
            row.updateColumns(cols);
            browseWindowFuncPanels.add(row);
            browseWindowFuncContainer.add(row);
            browseWindowFuncContainer.revalidate();
            browseWindowFuncContainer.repaint();
        }
    private void addBrowseOrderByRow() {
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            List<String> cols = selectedTable != null ? mainFrame.getDbManager().getColumnNames(selectedTable) : new ArrayList<>();
            Main.OrderByPanel op = mainFrame.new OrderByPanel(false, browseOrderByPanels, browseOrderByContainer);
            op.updateColumns(cols);
            browseOrderByPanels.add(op);
            browseOrderByContainer.add(op);
            browseOrderByContainer.revalidate();
            browseOrderByContainer.repaint();
        }
    private void addBrowseWhereRow() {
            boolean isFirst = browseWherePanels.isEmpty();
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            Main.WherePanel wp = mainFrame.new WherePanel(isFirst, browseWherePanels, browseWhereContainer);
            List<String> tables = new ArrayList<>();
            if (selectedTable != null) tables.add(selectedTable);
            wp.updateTables(tables);
            if (selectedTable != null) wp.tableDropdown.setSelectedItem(selectedTable);
            wp.fetchTablesFromDb();
            browseWherePanels.add(wp);
            browseWhereContainer.add(wp);
            browseWhereContainer.revalidate();
            browseWhereContainer.repaint();
        }
        private final Main mainFrame;

    public BrowseTabPanel(Main mainFrame) {
        this.mainFrame = mainFrame;
        this.browseTable = mainFrame.new UpdatableTable(() -> (String) browseTableDropdown.getSelectedItem());
            setLayout(new BorderLayout());
            add(createBrowsePanel(), BorderLayout.CENTER);
        }
        public void refreshTables() {
            Object sel = browseTableDropdown.getSelectedItem();
            browseTableDropdown.setAllItems(mainFrame.getLoadedTables());
            if (sel != null && mainFrame.getLoadedTables().contains(sel)) browseTableDropdown.setSelectedItem(sel);
            for (Main.WherePanel wp : browseWherePanels) {
                wp.fetchTablesFromDb();
            }
        }
        public void setBrowseTableDropdownValue(String val) {
            browseTableDropdown.setSelectedItem(val);
        }

        public com.dbtool.model.SessionSnapshot.BrowseState captureBrowseState() {
            com.dbtool.model.SessionSnapshot.BrowseState bs = new com.dbtool.model.SessionSnapshot.BrowseState();
            bs.tableName = (String) browseTableDropdown.getSelectedItem();
            bs.distinct = browseDistinctCheckbox.isSelected();
            bs.limit = limitField.getText();
            bs.selectedColumns = new ArrayList<>(currentSelectedColumns);
            for (Main.WherePanel wp : browseWherePanels) {
                bs.wheres.add(wp.captureWhereState());
            }
            for (Main.OrderByPanel op : browseOrderByPanels) {
                bs.orderBys.add(op.captureOrderByState());
            }
            return bs;
        }

        public void restoreBrowseState(com.dbtool.model.SessionSnapshot.BrowseState bs) {
            if (bs == null) return;
            if (bs.tableName != null && !bs.tableName.isEmpty()) {
                browseTableDropdown.setSelectedItem(bs.tableName);
            }
            browseDistinctCheckbox.setSelected(bs.distinct);
            if (bs.limit != null) limitField.setText(bs.limit);
            if (bs.selectedColumns != null) {
                currentSelectedColumns.clear();
                currentSelectedColumns.addAll(new LinkedHashSet<>(bs.selectedColumns));
                selectColsButton.setText(currentSelectedColumns.isEmpty() ? "Columns (All)" : "Columns (" + currentSelectedColumns.size() + ")");
            }
            browseWherePanels.clear();
            browseWhereContainer.removeAll();
            if (bs.wheres != null && !bs.wheres.isEmpty()) {
                for (VisualJoinState.WhereState ws : bs.wheres) {
                    addBrowseWhereRow();
                    if (!browseWherePanels.isEmpty()) {
                        browseWherePanels.get(browseWherePanels.size() - 1).restoreWhereState(ws);
                    }
                }
            }
            browseOrderByPanels.clear();
            browseOrderByContainer.removeAll();
            if (bs.orderBys != null && !bs.orderBys.isEmpty()) {
                for (VisualJoinState.OrderByState os : bs.orderBys) {
                    addBrowseOrderByRow();
                    if (!browseOrderByPanels.isEmpty()) {
                        browseOrderByPanels.get(browseOrderByPanels.size() - 1).restoreOrderByState(os);
                    }
                }
            }
            browseWhereContainer.revalidate(); browseWhereContainer.repaint();
            browseOrderByContainer.revalidate(); browseOrderByContainer.repaint();
        }
    }
