package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ExportUtil;
import com.dbtool.util.QueryHistory;
import com.dbtool.util.SavedQueries;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class SqlEditorPanel extends JPanel {

    private final DatabaseManager dbManager;
    private final RSyntaxTextArea editor;
    private final JTabbedPane resultTabs = new JTabbedPane();
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JComboBox<String> savedQueriesDropdown = new JComboBox<>();

    public SqlEditorPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // SQL Editor
        editor = new RSyntaxTextArea(12, 80);
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        editor.setCodeFoldingEnabled(true);
        editor.setAntiAliasingEnabled(true);
        editor.setText("-- Write your SQL here\nSELECT 1;");
        RTextScrollPane editorScroll = new RTextScrollPane(editor);
        editorScroll.setLineNumbersEnabled(true);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton runBtn = new JButton("▶ Run");
        JButton explainBtn = new JButton("⚡ Explain");
        JButton clearBtn = new JButton("Clear");
        JButton saveQueryBtn = new JButton("Save Query");
        JButton loadQueryBtn = new JButton("Load Query");
        JButton exportCsvBtn = new JButton("Export CSV");
        JButton exportXlsxBtn = new JButton("Export XLSX");
        
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
            try { if (dbManager.connection != null) dbManager.connection.commit(); statusLabel.setText("Transaction committed."); } catch (Exception ex) {}
        });
        rollbackBtn.addActionListener(e -> {
            try { if (dbManager.connection != null) dbManager.connection.rollback(); statusLabel.setText("Transaction rolled back."); } catch (Exception ex) {}
        });

        savedQueriesDropdown.setPrototypeDisplayValue("Select saved query...");

        toolbar.add(runBtn);
        toolbar.add(explainBtn);
        toolbar.add(clearBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(autoCommitCheck);
        toolbar.add(commitBtn);
        toolbar.add(rollbackBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(saveQueryBtn);
        toolbar.add(savedQueriesDropdown);
        toolbar.add(loadQueryBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(exportCsvBtn);
        toolbar.add(exportXlsxBtn);

        // Editor + toolbar top section
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.add(toolbar, BorderLayout.NORTH);
        topSection.add(editorScroll, BorderLayout.CENTER);
        topSection.add(statusLabel, BorderLayout.SOUTH);

        // History sidebar
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setPreferredSize(new Dimension(220, 0));
        JPanel historyPanel = new JPanel(new BorderLayout());
        JLabel histLabel = new JLabel(" Query History");
        histLabel.setFont(histLabel.getFont().deriveFont(Font.BOLD));
        JButton clearHistBtn = new JButton("Clear History");
        historyPanel.add(histLabel, BorderLayout.NORTH);
        historyPanel.add(historyScroll, BorderLayout.CENTER);
        historyPanel.add(clearHistBtn, BorderLayout.SOUTH);

        // Left split: editor top, results bottom
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSection, resultTabs);
        vertSplit.setResizeWeight(0.45);

        // Main split: vertSplit left, history right
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, vertSplit, historyPanel);
        mainSplit.setResizeWeight(0.82);

        add(mainSplit, BorderLayout.CENTER);

        // Actions
        runBtn.addActionListener(e -> executeQuery(editor.getText().trim(), false));
        explainBtn.addActionListener(e -> {
            String sql = editor.getText().trim();
            if (!sql.isEmpty()) executeQuery("EXPLAIN ANALYZE " + sql, true);
        });
        clearBtn.addActionListener(e -> editor.setText(""));
        saveQueryBtn.addActionListener(e -> saveCurrentQuery());
        loadQueryBtn.addActionListener(e -> {
            String sel = (String) savedQueriesDropdown.getSelectedItem();
            if (sel != null) {
                Map<String, String> sq = SavedQueries.load();
                if (sq.containsKey(sel)) editor.setText(sq.get(sel));
            }
        });
        clearHistBtn.addActionListener(e -> { QueryHistory.clear(); historyModel.clear(); });
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isConsumed() && e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    String sel = historyList.getSelectedValue();
                    if (sel != null) editor.setText(sel);
                }
            }
            private void showPopup(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int idx = historyList.locationToIndex(e.getPoint());
                if (idx != -1) {
                    historyList.setSelectedIndex(idx);
                    javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
                    javax.swing.JMenuItem delItem = new javax.swing.JMenuItem("Delete");
                    delItem.addActionListener(evt -> {
                        String sel = historyList.getSelectedValue();
                        if (sel != null) {
                            QueryHistory.remove(sel);
                            refreshHistory();
                        }
                    });
                    popup.add(delItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) { showPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { showPopup(e); }
        });

        historyList.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE || e.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    String sel = historyList.getSelectedValue();
                    if (sel != null) {
                        QueryHistory.remove(sel);
                        refreshHistory();
                    }
                }
            }
        });

        exportCsvBtn.addActionListener(e -> {
            JTable t = getActiveResultTable();
            if (t != null) ExportUtil.exportCsv(t, this);
        });
        exportXlsxBtn.addActionListener(e -> {
            JTable t = getActiveResultTable();
            if (t != null) ExportUtil.exportExcel(t, this);
        });

        refreshHistory();
        refreshSavedQueries();
    }

    private void executeQuery(String sql, boolean isExplain) {
        if (sql.isEmpty()) return;
        if (dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "No database connected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        statusLabel.setText("Running...");
        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                if (isExplain) {
                    // Show explain as text
                    java.sql.ResultSet rs = dbManager.connection.createStatement().executeQuery(sql);
                    StringBuilder sb = new StringBuilder();
                    while (rs.next()) sb.append(rs.getString(1)).append("\n");
                    String plan = sb.toString();
                    SwingUtilities.invokeLater(() -> {
                        JTextArea ta = new JTextArea(plan);
                        ta.setEditable(false);
                        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                        addResultTab("Explain Plan", new JScrollPane(ta));
                        statusLabel.setText("Explain done in " + (System.currentTimeMillis() - start) + "ms");
                    });
                } else {
                    java.sql.Statement stmt = dbManager.connection.createStatement();
                    boolean hasResultSet = stmt.execute(sql);
                    long elapsed = System.currentTimeMillis() - start;
                    
                    if (hasResultSet) {
                        try (java.sql.ResultSet rs = stmt.getResultSet()) {
                            java.sql.ResultSetMetaData metaData = rs.getMetaData();
                            java.util.Vector<String> columnNames = new java.util.Vector<>();
                            int columnCount = metaData.getColumnCount();
                            for (int column = 1; column <= columnCount; column++) {
                                columnNames.add(metaData.getColumnLabel(column));
                            }
                            java.util.Vector<java.util.Vector<Object>> data = new java.util.Vector<>();
                            while (rs.next()) {
                                java.util.Vector<Object> vector = new java.util.Vector<>();
                                for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                                    vector.add(rs.getObject(columnIndex));
                                }
                                data.add(vector);
                            }
                            DefaultTableModel model = new DefaultTableModel(data, columnNames);
                            SwingUtilities.invokeLater(() -> {
                                JTable table = new JTable(model);
                                table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                                table.setAutoCreateRowSorter(true);
                                addResultTab("Result (" + model.getRowCount() + " rows)", new JScrollPane(table));
                                statusLabel.setText(model.getRowCount() + " rows in " + elapsed + "ms");
                            });
                        }
                    } else {
                        int affected = stmt.getUpdateCount();
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText(affected + " rows affected in " + elapsed + "ms");
                            addResultTab("DML Result", createTextTab(affected + " rows affected."));
                        });
                    }
                    QueryHistory.add(sql);
                    SwingUtilities.invokeLater(this::refreshHistory);
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + ex.getMessage());
                    addResultTab("Error", createTextTab("Error: " + ex.getMessage()));
                });
            }
        }).start();
    }

    private void addResultTab(String title, JComponent content) {
        if (resultTabs.getTabCount() >= 10) resultTabs.removeTabAt(0);
        int idx = resultTabs.getTabCount();
        resultTabs.addTab(title, content);
        // Add close button
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabHeader.setOpaque(false);
        JLabel lbl = new JLabel(title + "  ");
        JButton closeBtn = new JButton("✕");
        closeBtn.setMargin(new Insets(0, 2, 0, 2));
        closeBtn.setFocusable(false);
        closeBtn.addActionListener(e -> resultTabs.remove(content));
        tabHeader.add(lbl);
        tabHeader.add(closeBtn);
        resultTabs.setTabComponentAt(idx, tabHeader);
        resultTabs.setSelectedIndex(idx);
    }

    private JScrollPane createTextTab(String text) {
        JTextArea ta = new JTextArea(text);
        ta.setEditable(false);
        return new JScrollPane(ta);
    }

    private void saveCurrentQuery() {
        String sql = editor.getText().trim();
        if (sql.isEmpty()) return;
        String name = JOptionPane.showInputDialog(this, "Query name:", "Save Query", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isEmpty()) {
            SavedQueries.save(name, sql);
            refreshSavedQueries();
        }
    }

    private void refreshHistory() {
        historyModel.clear();
        for (String q : QueryHistory.load()) historyModel.addElement(q);
    }

    public void refreshSavedQueries() {
        savedQueriesDropdown.removeAllItems();
        for (String name : SavedQueries.load().keySet()) savedQueriesDropdown.addItem(name);
    }

    private JTable getActiveResultTable() {
        Component sel = resultTabs.getSelectedComponent();
        if (sel instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) sel;
            Component view = sp.getViewport().getView();
            if (view instanceof JTable) {
                return (JTable) view;
            }
        }
        return null;
    }
}
