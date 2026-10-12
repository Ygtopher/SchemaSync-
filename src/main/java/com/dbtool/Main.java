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
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.dbtool.model.VisualJoinState;
import com.dbtool.util.VisualJoinProjectManager;
import com.dbtool.util.SqlSnippetsLibrary;
import com.dbtool.panels.SqlMasteryReferenceDialog;

public class Main extends JFrame {
    private JTabbedPane tabbedPane;
    private com.dbtool.panels.MultiTabContainer<BrowseTab> browseTabContainer;
    private com.dbtool.panels.MultiTabContainer<VisualJoinTab> joinTabContainer;

    public class BrowseTab extends JPanel {
    private SearchableComboBox browseTableDropdown = new SearchableComboBox();
    private JButton selectColsButton = new JButton("Columns (All)");
    private List<String> currentSelectedColumns = new ArrayList<>();
    private JTextField limitField = new JTextField(5);
    private JTable browseTable = new UpdatableTable(() -> (String) browseTableDropdown.getSelectedItem());
    private JPanel browseWhereContainer = new JPanel();
    private List<WherePanel> browseWherePanels = new ArrayList<>();
    private JPanel browseOrderByContainer = new JPanel();
    private List<OrderByPanel> browseOrderByPanels = new ArrayList<>();
    private String lastExecutedBrowseQuery = "";
    private int browseCurrentOffset = 0;
    private boolean isBrowseLoading = false;
    private String lastBrowseBaseQuery = "";
    private JCheckBox browseDistinctCheckbox = new JCheckBox("Distinct");

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
                loadTableData();
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
            JButton applyOrderBtn = new JButton("▶ Run Query");
            applyOrderBtn.setFont(applyOrderBtn.getFont().deriveFont(java.awt.Font.BOLD));
            applyOrderBtn.setForeground(new java.awt.Color(0, 150, 0));
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
            JButton openInSqlBtn = new JButton("▶ Open in SQL Editor");
            openInSqlBtn.setToolTipText("Open current query in SQL Editor tab");
            openInSqlBtn.addActionListener(e -> {
                if (!lastExecutedBrowseQuery.isEmpty()) {
                    sqlEditorPanel.setEditorText(lastExecutedBrowseQuery);
                    tabbedPane.setSelectedComponent(sqlEditorPanel);
                } else {
                    JOptionPane.showMessageDialog(this, "Please run a query first.");
                }
            });
            tbActions.add(exportCsvBtn);
            tbActions.add(exportPdfBtn);
            tbActions.add(exportXlsxBtn);
            tbActions.add(exportInsertBtn);
            tbActions.addSeparator();
            tbActions.add(copyBtn);
            tbActions.add(copySqlBtn);
            tbActions.add(openInSqlBtn);
            tbActions.addSeparator();

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
                        sqlEditorPanel.setEditorText(sql);
                        tabbedPane.setSelectedComponent(sqlEditorPanel);
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
                new SqlMasteryReferenceDialog(Main.this, sql -> {
                    sqlEditorPanel.setEditorText(sql);
                    tabbedPane.setSelectedComponent(sqlEditorPanel);
                }).setVisible(true);
            });
            tbActions.add(templatesBtn);
            tbActions.add(cheatSheetBtn);
    
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
    
            JDialog dialog = new JDialog(Main.this, "Select Columns", true);
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
            int count = 0;
            for (int i = 0; i < browseWherePanels.size(); i++) {
                WherePanel wp = browseWherePanels.get(i);
                String cond = wp.buildConditionClause(dbManager);
                if (cond.isEmpty()) continue;
                if (count > 0) sb.append(wp.logicDropdown.getSelectedItem()).append(" ");
                sb.append(cond);
                count++;
            }
            return count > 0 ? sb.toString() : "";
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
    private void addBrowseWhereRow() {
            boolean isFirst = browseWherePanels.isEmpty();
            String selectedTable = (String) browseTableDropdown.getSelectedItem();
            WherePanel wp = new WherePanel(isFirst, browseWherePanels, browseWhereContainer);
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
        public BrowseTab() {
            setLayout(new BorderLayout());
            add(createBrowsePanel(), BorderLayout.CENTER);
        }
        public void refreshTables() {
            Object sel = browseTableDropdown.getSelectedItem();
            browseTableDropdown.setAllItems(loadedTables);
            if (sel != null && loadedTables.contains(sel)) browseTableDropdown.setSelectedItem(sel);
            for (WherePanel wp : browseWherePanels) {
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
            for (WherePanel wp : browseWherePanels) {
                bs.wheres.add(wp.captureWhereState());
            }
            for (OrderByPanel op : browseOrderByPanels) {
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
                currentSelectedColumns.addAll(bs.selectedColumns);
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

    public class VisualJoinTab extends JPanel {
    private SearchableComboBox baseTableDropdown = new SearchableComboBox();
    private JPanel implicitTablesContainer = new JPanel();
        private java.util.List<ImplicitTablePanel> implicitTablePanels = new java.util.ArrayList<>();
        private JPanel joinsContainer = new JPanel();
    private List<JoinPanel> joinPanels = new ArrayList<>();
    private JTable joinResultTable = new UpdatableTable(() -> (String) baseTableDropdown.getSelectedItem());
    private JButton joinSelectColsButton = new JButton("Columns (All)");
    private List<String> joinSelectedColumns = new ArrayList<>();
    private JTextField joinLimitField = new JTextField(5);
    private String lastExecutedJoinQuery = "";
    private JCheckBox joinDistinctCheckbox = new JCheckBox("Distinct");
    private JPanel whereContainer = new JPanel();
    private List<WherePanel> wherePanels = new ArrayList<>();
    private JPanel joinOrderByContainer = new JPanel();
    private List<OrderByPanel> joinOrderByPanels = new ArrayList<>();

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
                executeVisualJoin();
            });
            baseTableDropdown.setPreferredSize(new Dimension(200, 26));
            tbBase.add(baseTableDropdown);
            tbBase.add(new JLabel(" | "));
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
            configPanel.add(tbBase, BorderLayout.NORTH);
            
            // Dynamic Joins and Where List
            joinsContainer.setLayout(new BoxLayout(joinsContainer, BoxLayout.Y_AXIS));
            whereContainer.setLayout(new BoxLayout(whereContainer, BoxLayout.Y_AXIS));
            
            implicitTablesContainer.setLayout(new BoxLayout(implicitTablesContainer, BoxLayout.Y_AXIS));
            JPanel listsPanel = new JPanel();
            listsPanel.setLayout(new BoxLayout(listsPanel, BoxLayout.Y_AXIS));
            listsPanel.add(implicitTablesContainer);
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
                    sqlEditorPanel.setEditorText(lastExecutedJoinQuery);
                    tabbedPane.setSelectedComponent(sqlEditorPanel);
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
                        sqlEditorPanel.setEditorText(sql);
                        tabbedPane.setSelectedComponent(sqlEditorPanel);
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
                new SqlMasteryReferenceDialog(Main.this, sql -> {
                    sqlEditorPanel.setEditorText(sql);
                    tabbedPane.setSelectedComponent(sqlEditorPanel);
                }).setVisible(true);
            });
            tbActions.add(templatesBtn);
            tbActions.add(cheatSheetBtn);
            
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
    
            JDialog dialog = new JDialog(Main.this, "Select Join Columns", true);
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
    private void addJoinOrderByRow() {
            OrderByPanel op = new OrderByPanel(true, joinOrderByPanels, joinOrderByContainer);
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
            WherePanel wp = new WherePanel(isFirst, wherePanels, whereContainer);
            wp.fetchTablesFromDb();
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
                for (ImplicitTablePanel itp : implicitTablePanels) {
                    String t = (String) itp.tableDropdown.getSelectedItem();
                    if (t != null && !t.isEmpty()) availableLeftTables.add(t);
                }
                for (ImplicitTablePanel itp : implicitTablePanels) {
                    String t = (String) itp.tableDropdown.getSelectedItem();
                    if (t != null && !t.isEmpty()) availableLeftTables.add(t);
                }
    
                for (JoinPanel jp : joinPanels) {
                    String joinTable = (String) jp.joinTableDropdown.getSelectedItem();
                    if (joinTable != null) {
                        List<String> rightCols = new ArrayList<>(dbManager.getColumnNames(joinTable));
                        
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
            
            for (WherePanel wp : wherePanels) {
                state.wheres.add(wp.captureWhereState());
            }
            
            for (OrderByPanel op : joinOrderByPanels) {
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
                WherePanel wp = wherePanels.get(wherePanels.size() - 1);
                wp.restoreWhereState(ws);
            }
            
            for (VisualJoinState.OrderByState os : state.orderBys) {
                addJoinOrderByRow();
                OrderByPanel op = joinOrderByPanels.get(joinOrderByPanels.size() - 1);
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
            sql.append("SELECT ").append(distinctStr).append(cols).append(" FROM ").append(dbManager.quoteTableName(baseTable));
            
            for (ImplicitTablePanel itp : implicitTablePanels) {
                String t = (String) itp.tableDropdown.getSelectedItem();
                if (t != null && !t.isEmpty()) {
                    sql.append(", ").append(dbManager.quoteTableName(t));
                }
            }
            sql.append(" ");
    
            for (JoinPanel jp : joinPanels) {
                String type = (String) jp.joinTypeDropdown.getSelectedItem();
                String table = (String) jp.joinTableDropdown.getSelectedItem();
                
                if (table == null || table.isEmpty()) continue;
                
                sql.append(" ").append(type).append(" ").append(dbManager.quoteTableName(table)).append(" ");
                
                boolean isCrossOrApply = type != null && (type.equalsIgnoreCase("CROSS JOIN")
                        || type.equalsIgnoreCase("CROSS JOIN LATERAL")
                        || type.equalsIgnoreCase("CROSS APPLY")
                        || type.equalsIgnoreCase("OUTER APPLY"));

                for (int i = 0; i < jp.conditionPanels.size(); i++) {
                    JoinConditionPanel cp = jp.conditionPanels.get(i);
                    String leftTable = (String) cp.leftTableDropdown.getSelectedItem();
                    String leftCol = (String) cp.leftColDropdown.getSelectedItem();
                    String rightCol = (String) cp.rightColDropdown.getSelectedItem();
                    
                    if (leftTable != null && leftCol != null && rightCol != null) {
                        String leftFormatted = dbManager.quoteColumnName(leftTable + "." + leftCol);
                        String rightFormatted = dbManager.quoteColumnName(table + "." + rightCol);
                        
                        sql.append(cp.operator).append(" ").append(leftFormatted).append(" = ").append(rightFormatted).append(" ");
                    } else if (!isCrossOrApply) {
                        JOptionPane.showMessageDialog(this, "Please make sure both 'Left' and 'Right' columns are selected for the join condition on table: " + table, "Incomplete Join", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
            }
            if (!wherePanels.isEmpty()) {
                StringBuilder wsb = new StringBuilder("WHERE ");
                int count = 0;
                for (int i = 0; i < wherePanels.size(); i++) {
                    WherePanel wp = wherePanels.get(i);
                    String cond = wp.buildConditionClause(dbManager);
                    if (cond.isEmpty()) continue;
                    if (count > 0) wsb.append(wp.logicDropdown.getSelectedItem()).append(" ");
                    wsb.append(cond);
                    count++;
                }
                if (count > 0) {
                    sql.append(wsb.toString());
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
            
            System.out.println("Executing JOIN: " + lastExecutedJoinQuery);
            new Thread(() -> {
                try {
                    DefaultTableModel model = dbManager.executeQuery(lastExecutedJoinQuery);
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
                                    dbManager.learnJoin(leftTable, leftTable + "." + leftCol, table, table + "." + rightCol);
                                }
                            }
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(VisualJoinTab.this, "SQL Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        }
    class JoinConditionPanel extends JPanel {
            String operator;
            SearchableComboBox leftTableDropdown = new SearchableComboBox();
            SearchableComboBox leftColDropdown = new SearchableComboBox();
            JLabel rightTableLabel = new JLabel("");
            SearchableComboBox rightColDropdown = new SearchableComboBox();
            
            public JoinConditionPanel(String operator, boolean isFirst, JoinPanel parent) {
                this.operator = operator;
                setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
                add(new JLabel(" " + operator + " "));
                
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
    
                add(leftTableDropdown);
                add(new JLabel(" . "));
                add(leftColDropdown);
                add(new JLabel(" = "));
                add(rightTableLabel);
                add(rightColDropdown);
                
                if (isFirst) {
                    JButton addConditionBtn = new JButton("+ AND");
                    addConditionBtn.addActionListener(e -> parent.addNewCondition("AND"));
                    add(addConditionBtn);
                } else {
                    JButton removeConditionBtn = new JButton("- Remove AND");
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
            SearchableComboBox joinTableDropdown = new SearchableComboBox();
            
            JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JPanel conditionsContainer = new JPanel();
            java.util.List<JoinConditionPanel> conditionPanels = new ArrayList<>();
    
            public JoinPanel() {
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                conditionsContainer.setLayout(new BoxLayout(conditionsContainer, BoxLayout.Y_AXIS));
                
                joinTableDropdown.setAllItems(loadedTables);
                joinTableDropdown.addActionListener(e -> {
                    joinSelectedColumns.clear();
                    joinSelectColsButton.setText("Columns (All)");
                    updateJoinDropdowns();
                });
    
                headerPanel.add(joinTypeDropdown);
                headerPanel.add(joinTableDropdown);
                
                JButton removeBtn = new JButton("X Remove Join");
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
        public VisualJoinTab() {
            setLayout(new BorderLayout());
            add(createJoinPanel(), BorderLayout.CENTER);
        }
        public void refreshTables() {
            Object sel = baseTableDropdown.getSelectedItem();
            baseTableDropdown.setAllItems(loadedTables);
            if (sel != null && loadedTables.contains(sel)) baseTableDropdown.setSelectedItem(sel);
            updateJoinDropdowns();
            for (WherePanel wp : wherePanels) {
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
            SearchableComboBox tableDropdown = new SearchableComboBox();

            public ImplicitTablePanel() {
                setLayout(new FlowLayout(FlowLayout.LEFT));
                add(new JLabel("And Table: "));
                
                tableDropdown.setAllItems(loadedTables);
                tableDropdown.setPreferredSize(new Dimension(200, 26));
                tableDropdown.addActionListener(e -> {
                    joinSelectedColumns.clear();
                    joinSelectColsButton.setText("Columns (All)");
                    updateJoinDropdowns();
                });
                add(tableDropdown);
                
                JButton removeBtn = new JButton("X");
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
        }
    }


        class UpdatableTable extends JTable {
        private java.util.function.Supplier<String> tableNameSupplier;
        private final java.util.Map<String, java.util.Vector<String>> fkOptionsCache = new java.util.concurrent.ConcurrentHashMap<>();

        public UpdatableTable(java.util.function.Supplier<String> tableNameSupplier) {
            super();
            this.tableNameSupplier = tableNameSupplier;
            this.setShowGrid(true);
            
            JPopupMenu popupMenu = new JPopupMenu();
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
                
                StringBuilder sb = new StringBuilder();
                for (int row : rows) {
                    StringBuilder sql = new StringBuilder("INSERT INTO " + dbManager.quoteTableName(tableName) + " (");
                    StringBuilder vals = new StringBuilder("VALUES (");
                    for (int c = 0; c < getColumnCount(); c++) {
                        String colName = getColumnName(c);
                        if (colName.contains(".")) {
                            String[] p = colName.split("\\.");
                            if (p.length == 2 && !p[0].equals(tableName)) continue;
                            colName = p.length == 2 ? p[1] : colName;
                        }
                        sql.append(dbManager.quoteColumnName(colName)).append(c < getColumnCount() - 1 ? ", " : "");
                        Object val = getValueAt(row, c);
                        if (val == null) {
                            vals.append("NULL");
                        } else {
                            vals.append("'").append(val.toString().replace("'", "''")).append("'");
                        }
                        vals.append(c < getColumnCount() - 1 ? ", " : "");
                    }
                    if (sql.toString().endsWith(", ")) sql.setLength(sql.length() - 2);
                    if (vals.toString().endsWith(", ")) vals.setLength(vals.length() - 2);
                    sql.append(") ").append(vals).append(");\n");
                    sb.append(sql);
                }
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(sb.toString()), null);
            });
            popupMenu.add(copyInsertItem);
            
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
            
            // 1. Check for Foreign Key
            if (actualTable != null && !actualTable.isEmpty()) {
                java.util.Map<String, String[]> fks = dbManager.getForeignKeys(actualTable);
                if (fks.containsKey(actualCol)) {
                    String[] pkInfo = fks.get(actualCol);
                    String pkTable = pkInfo[0];
                    String pkCol = pkInfo[1];
                    String cacheKey = pkTable + "." + pkCol;
                    java.util.Vector<String> options = fkOptionsCache.get(cacheKey);
                    if (options == null) {
                        try {
                            javax.swing.table.DefaultTableModel rs = dbManager.executeQuery("SELECT DISTINCT " + dbManager.quoteColumnName(pkCol) + " FROM " + dbManager.quoteTableName(pkTable) + " LIMIT 500");
                            options = new java.util.Vector<>();
                            for (int i = 0; i < rs.getRowCount(); i++) {
                                Object val = rs.getValueAt(i, 0);
                                if (val != null) options.add(val.toString());
                            }
                            if (!options.isEmpty()) {
                                fkOptionsCache.put(cacheKey, options);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (options != null && !options.isEmpty()) {
                        JComboBox<String> combo = new JComboBox<>(options);
                        combo.setEditable(true);
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
                return false;
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
                
                boolean first = true;
                for (int c = 0; c < getColumnCount(); c++) {
                    String currentHeader = getColumnName(c);
                    String curTable = tableName;
                    String curCol = currentHeader;
                    if (currentHeader.contains(".")) {
                        String[] parts = currentHeader.split("\\.");
                        if (parts.length == 2) {
                            curTable = parts[0];
                            curCol = parts[1];
                        }
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
    private TableComparePanel tableComparePanel;
    private com.dbtool.panels.DualDatabaseComparePanel dualDbComparePanel;
    private ScriptBuilderPanel scriptBuilderPanel;
    private DataOpsPanel dataOpsPanel;
    
    // Cache for currently loaded tables
    private List<String> loadedTables = new ArrayList<>();

    private JButton loadDbButton = new JButton("Connect & Upload (.accdb, .sql, .sql.gz)");
    private JButton connectExistingBtn = new JButton("Connect to Existing DB");
    private JButton sandboxBtn = new JButton("⚡ Sample Sandbox DB");
    private JButton seedSampleBtn = new JButton("🌱 Seed Sample Tables");
    private JButton disconnectDbButton = new JButton("Disconnect DB");
    private JButton exportDbBtn = new JButton("Export DB");
    private JButton autoSaveIndicatorBtn = new JButton("💾 Auto-Saved");
    public Main() {
        setTitle("SchemaSync");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

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
        tableComparePanel = new TableComparePanel(dbManager);
        dualDbComparePanel = new com.dbtool.panels.DualDatabaseComparePanel(dbManager);
        scriptBuilderPanel = new ScriptBuilderPanel(dbManager);
        dataOpsPanel = new DataOpsPanel(dbManager);

        // Schema panel: double-click loads table in browse
        schemaPanel.setOnTableSelected(tableName -> {
            if (tabbedPane != null) tabbedPane.setSelectedIndex(0); // switch to browse tab
            if (loadedTables.contains(tableName) && browseTabContainer != null && !browseTabContainer.getTabs().isEmpty()) {
                browseTabContainer.getTabs().get(0).setBrowseTableDropdownValue(tableName);
            }
        });

        // Top Panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
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

        JButton themeBtn = new JButton("🌙 Dark Mode");
        themeBtn.addActionListener(e -> com.dbtool.util.ThemeManager.toggleTheme(themeBtn));
        topPanel.add(themeBtn);

        topPanel.add(statusLabel);
        
        // Init theme
        com.dbtool.util.ThemeManager.init(themeBtn);
        add(topPanel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
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

        // AI Assistant tab stopped until further notice
        // com.dbtool.panels.AiAssistantTab aiAssistantTab = new com.dbtool.panels.AiAssistantTab(dbManager);
        // tabbedPane.addTab("AI Assistant", aiAssistantTab);

        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex != -1) {
                String title = tabbedPane.getTitleAt(selectedIndex);
                if ("ER Diagram".equals(title) && erDiagramPanel != null) {
                    erDiagramPanel.checkAndRefresh();
                }
            }
        });
        
        com.dbtool.panels.NetworkScannerPanel networkScannerPanel = new com.dbtool.panels.NetworkScannerPanel();
        // Network Scanner tab is hidden by default

        // Main layout: Schema sidebar left, tabs center
        com.dbtool.panels.QueryLogPanel queryLogPanel = new com.dbtool.panels.QueryLogPanel();
        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, queryLogPanel);
        centerSplit.setDividerLocation(600);
        centerSplit.setResizeWeight(0.8);
        
        JTabbedPane leftSidebarTabs = new JTabbedPane();
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

        JButton toggleQueryLogBtn = new JButton("\u2630");
        toggleQueryLogBtn.setToolTipText("Toggle Global Query Log");
        toggleQueryLogBtn.addActionListener(e -> {
            if (centerSplit.getDividerLocation() >= centerSplit.getHeight() - 40) {
                centerSplit.setDividerLocation(centerSplit.getHeight() - 250); // Expand up
            } else {
                centerSplit.setDividerLocation(centerSplit.getHeight()); // Collapse down
            }
        });
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
        autoSaveIndicatorBtn.setToolTipText("Auto-Save is active (Click for recovery options)");
        autoSaveIndicatorBtn.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem saveNowItem = new JMenuItem("💾 Save Snapshot Now");
            saveNowItem.addActionListener(ev -> {
                boolean saved = com.dbtool.util.SessionRecoveryManager.saveSessionSnapshot(captureCurrentSessionSnapshot());
                String timeStr = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
                onAutoSaved(timeStr);
                JOptionPane.showMessageDialog(this, saved ? "Session snapshot saved successfully!" : "Session is already up to date.", "Snapshot Info", JOptionPane.INFORMATION_MESSAGE);
            });
            JMenuItem openFolderItem = new JMenuItem("📁 Open Recovery Folder...");
            openFolderItem.addActionListener(ev -> {
                try {
                    java.awt.Desktop.getDesktop().open(com.dbtool.util.SessionRecoveryManager.getRecoveryDir());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Recovery folder: " + com.dbtool.util.SessionRecoveryManager.getRecoveryDir().getAbsolutePath());
                }
            });
            JMenuItem discardItem = new JMenuItem("🗑 Clear Autosave Backup");
            discardItem.addActionListener(ev -> {
                com.dbtool.util.SessionRecoveryManager.discardCrashedSession();
                autoSaveIndicatorBtn.setText("💾 Auto-Save: Active");
                JOptionPane.showMessageDialog(this, "Autosave backup cleared.");
            });
            menu.add(saveNowItem);
            menu.add(openFolderItem);
            menu.addSeparator();
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

        // Start background auto-save daemon and register shutdown hook
        com.dbtool.util.SessionRecoveryManager.startAutoSaveDaemon(this::captureCurrentSessionSnapshot, this::onAutoSaved, 15);
        com.dbtool.util.SessionRecoveryManager.registerShutdownHook(this::captureCurrentSessionSnapshot);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                com.dbtool.util.SessionRecoveryManager.markCleanExit();
            }
        });
    }

    private void onAutoSaved(String timeStr) {
        autoSaveIndicatorBtn.setText("💾 Auto-Saved (" + timeStr + ")");
        autoSaveIndicatorBtn.setToolTipText("Last auto-saved at " + timeStr + ". Click for recovery options.");
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
            if (dbManager.isSandbox()) {
                snapshot.connection.type = "SANDBOX";
            } else if (dbManager.getLastDumpFile() != null) {
                snapshot.connection.type = "FILE";
                snapshot.connection.filePath = dbManager.getLastDumpFile();
            } else if (dbManager.getLastHost() != null) {
                snapshot.connection.type = "POSTGRES";
                snapshot.connection.host = dbManager.getLastHost();
                snapshot.connection.port = dbManager.getLastPort();
                snapshot.connection.dbName = dbManager.getLastDbName();
                snapshot.connection.user = dbManager.getLastUser();
                snapshot.connection.pass = dbManager.getLastPass();
            } else {
                snapshot.connection.type = "NONE";
            }
        }

        // SQL Editor
        if (sqlEditorPanel != null) {
            snapshot.sqlEditor.queryText = sqlEditorPanel.getEditorText();
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

        // 1. Restore Database Connection
        if (snapshot.connection != null && !"NONE".equals(snapshot.connection.type)) {
            new Thread(() -> {
                try {
                    if ("SANDBOX".equals(snapshot.connection.type)) {
                        dbManager.connectSandbox();
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Database: Sandbox (In-Memory)");
                            statusLabel.setForeground(new java.awt.Color(0, 150, 0));
                            populateTablesUI();
                        });
                    } else if ("POSTGRES".equals(snapshot.connection.type) && snapshot.connection.host != null) {
                        dbManager.connectExistingPostgres(snapshot.connection.host, snapshot.connection.port, snapshot.connection.dbName, snapshot.connection.user, snapshot.connection.pass);
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Connected: " + snapshot.connection.dbName);
                            statusLabel.setForeground(new java.awt.Color(0, 150, 0));
                            populateTablesUI();
                            connectExistingBtn.setVisible(false);
                            loadDbButton.setVisible(false);
                            disconnectDbButton.setVisible(true);
                            exportDbBtn.setVisible(true);
                        });
                    }
                } catch (Exception ex) {
                    System.err.println("Could not auto-reconnect on restore: " + ex.getMessage());
                }
            }).start();
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
                tab.restoreBrowseState(bs);
            }
        }

        // 4. Restore Visual Join Tabs
        if (joinTabContainer != null && snapshot.joinTabs != null && !snapshot.joinTabs.isEmpty()) {
            joinTabContainer.clearAllTabs();
            for (com.dbtool.model.VisualJoinState js : snapshot.joinTabs) {
                VisualJoinTab tab = joinTabContainer.addNewTabAndGet();
                tab.restoreJoinState(js);
            }
        }

        // 5. Restore Table Compare
        if (tableComparePanel != null && snapshot.tableCompare != null) {
            tableComparePanel.setQueryA(snapshot.tableCompare.queryA);
            tableComparePanel.setQueryB(snapshot.tableCompare.queryB);
        }

        // 6. Restore Active Tab
        if (tabbedPane != null && snapshot.activeTabIndex >= 0 && snapshot.activeTabIndex < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(snapshot.activeTabIndex);
        }
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
                    
                    // Pre-fetch caches in background so we don't freeze EDT
                    java.util.List<String> tables = dbManager.getTableNames();
                    if (tables != null) {
                        for (String t : tables) {
                            dbManager.getColumnNames(t);
                        }
                    }

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
    }

    private void connectExistingDatabase() {
        com.dbtool.panels.ConnectionManagerDialog dialog = new com.dbtool.panels.ConnectionManagerDialog(this, dbManager);
        dialog.setVisible(true);
        if (!dialog.isApproved()) {
            return;
        }

        final String finalHost = dialog.getHost();
        final String finalPort = dialog.getPort();
        final String finalUser = dialog.getUser();
        final String finalPass = dialog.getPass();
        final String finalDbName = dialog.getDbName();
        
        statusLabel.setText("Connecting to " + finalDbName + "...");
        statusLabel.setForeground(java.awt.Color.BLUE);
        
        new Thread(() -> {
            try {
                dbManager.connectExistingPostgres(finalHost, finalPort, finalDbName, finalUser, finalPass);
                
                // Pre-fetch caches in background so we don't freeze EDT
                java.util.List<String> tables = dbManager.getTableNames();
                if (tables != null) {
                    for (String t : tables) {
                        dbManager.getColumnNames(t);
                    }
                }

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

    class WherePanel extends JPanel {
        JComboBox<String> logicDropdown = new JComboBox<>(new String[]{"AND", "OR"});
        SearchableComboBox tableDropdown = new SearchableComboBox();
        SearchableComboBox columnDropdown = new SearchableComboBox();
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

        public WherePanel(boolean isFirst, List<WherePanel> ownerList, JPanel ownerContainer) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 3, 2));

            if (!isFirst) {
                add(logicDropdown);
            }

            tableDropdown.setPreferredSize(new Dimension(140, 26));
            columnDropdown.setPreferredSize(new Dimension(140, 26));
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
            add(new JLabel(" . "));
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
                    if (valueField.getParent() != WherePanel.this) {
                        WherePanel.this.add(valueField, Math.max(0, WherePanel.this.getComponentCount() - 1));
                    }
                    valueField.setVisible(true);
                    valueField.setToolTipText(null);
                }
                revalidate();
                repaint();
            });

            add(inTypeDropdown);
            add(inControlsPanel);
            add(valueField);

            JButton removeBtn = new JButton("X");
            removeBtn.addActionListener(e -> {
                int idx = ownerList.indexOf(this);
                ownerList.remove(this);
                ownerContainer.remove(this);
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
            String inType = (String) inTypeDropdown.getSelectedItem();

            if ("Distinct Values".equals(inType)) {
                inControlsPanel.add(inValuesDropdown);
                inControlsPanel.add(inPickBtn);
                inControlsPanel.add(valueField);
                valueField.setVisible(true);
                valueField.setToolTipText("Comma-separated values (pick from dropdown or click Pick...)");
            } else if ("Subquery (SELECT)".equals(inType)) {
                inControlsPanel.add(subSelectLabel);
                inControlsPanel.add(inSubColDropdown);
                inControlsPanel.add(subFromLabel);
                inControlsPanel.add(inSubTableDropdown);
                inControlsPanel.add(inSubClausesBtn);
                inControlsPanel.add(subCloseLabel);
                valueField.setVisible(false);

                fetchTablesFromDb();
                updateSubClausesBtnDisplay();
            } else { // Manual Values
                inControlsPanel.add(valueField);
                valueField.setVisible(true);
                valueField.setToolTipText("Enter values separated by commas: e.g. 101, 102 or 'IT', 'HR'");
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
            JButton delBtn = new JButton("X");

            public SubqueryConditionRow(boolean isFirst, List<String> cols, SubqueryWhereCondition initData, Runnable onUpdate, Runnable onRemove) {
                setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
                logicCombo.setPreferredSize(new Dimension(65, 26));
                colCombo.setPreferredSize(new Dimension(140, 26));
                opCombo.setPreferredSize(new Dimension(95, 26));
                valField.setPreferredSize(new Dimension(140, 26));
                delBtn.setPreferredSize(new Dimension(38, 24));
                delBtn.setMargin(new Insets(1, 4, 1, 4));

                colCombo.setAllItems(cols);

                if (!isFirst) {
                    add(logicCombo);
                } else {
                    JPanel spacer = new JPanel();
                    spacer.setPreferredSize(new Dimension(65, 26));
                    spacer.setOpaque(false);
                    add(spacer);
                }
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
                removeAll();
                if (!isFirst) {
                    add(logicCombo);
                } else {
                    JPanel spacer = new JPanel();
                    spacer.setPreferredSize(new Dimension(65, 26));
                    spacer.setOpaque(false);
                    add(spacer);
                }
                add(colCombo);
                add(opCombo);
                add(valField);
                add(delBtn);
                revalidate();
                repaint();
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

    public static void main(String[] args) {
        com.dbtool.util.ThemeManager.init(null);
        SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }

    static class SearchableComboBox extends com.dbtool.ui.components.SearchableComboBox<String> {
        public SearchableComboBox() {
            super();
        }
    }
}


