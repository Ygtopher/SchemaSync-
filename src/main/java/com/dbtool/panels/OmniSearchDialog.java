package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Global Database OmniSearch (Spotlight Search)
 * Scans all tables and columns in parallel / streaming mode, with live results
 * and 1-click navigation to the target table row.
 * 
 * Part of SchemaSync Tier 1 Advanced Relational Features.
 */
public class OmniSearchDialog extends JDialog {

    public interface RowNavigationHandler {
        void navigateToRow(String tableName, String columnName, Object matchedValue, Map<String, Object> pks);
    }

    public static class SearchHit {
        public final String tableName;
        public final String columnName;
        public final String matchedValue;
        public final String pkDisplay;
        public final Map<String, Object> pkValues;
        public final Map<String, Object> allValues;

        public SearchHit(String tableName, String columnName, String matchedValue, String pkDisplay, Map<String, Object> pkValues, Map<String, Object> allValues) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.matchedValue = matchedValue;
            this.pkDisplay = pkDisplay;
            this.pkValues = pkValues;
            this.allValues = allValues;
        }
    }

    private final DatabaseManager dbManager;
    private final RowNavigationHandler navigationHandler;

    private JTextField searchField;
    private JComboBox<String> matchTypeCombo;
    private JComboBox<String> tableScopeCombo;
    private JSpinner limitSpinner;
    private JButton searchBtn;
    private JButton stopBtn;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel matchCountBadge;

    private DefaultTableModel resultsModel;
    private JTable resultsTable;
    private TableRowSorter<DefaultTableModel> sorter;
    private final List<SearchHit> hitsList = new ArrayList<>();

    private SwingWorker<Void, SearchHit> currentWorker = null;

    public OmniSearchDialog(Frame owner, DatabaseManager dbManager, RowNavigationHandler navigationHandler) {
        super(owner, "🔍 Database OmniSearch — Global Spotlight", false);
        this.dbManager = dbManager;
        this.navigationHandler = navigationHandler;

        setSize(1020, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        initComponents();
    }

    private void initComponents() {
        // --- 1. Top Panel: Search Bar and Configuration ---
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBorder(new EmptyBorder(12, 14, 8, 14));
        topPanel.setBackground(new Color(248, 250, 252));
        if (ThemeManager.isDarkMode()) {
            topPanel.setBackground(new Color(30, 41, 59));
        }

        // Primary Search Row
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);

        JLabel searchIconLabel = new JLabel("🔍 Find:");
        searchIconLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        searchField.setToolTipText("Enter text, numeric ID, date, or regex pattern to search");
        searchField.addActionListener(e -> startSearch());

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionBtns.setOpaque(false);

        searchBtn = new JButton("Search");
        searchBtn.setFont(searchBtn.getFont().deriveFont(Font.BOLD));
        searchBtn.setBackground(new Color(59, 130, 246));
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setFocusPainted(false);
        searchBtn.addActionListener(e -> startSearch());

        stopBtn = new JButton("⏹ Stop");
        stopBtn.setEnabled(false);
        stopBtn.addActionListener(e -> stopSearch());

        actionBtns.add(searchBtn);
        actionBtns.add(stopBtn);

        searchRow.add(searchIconLabel, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(actionBtns, BorderLayout.EAST);

        // Options Row
        JPanel optionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        optionsRow.setOpaque(false);

        optionsRow.add(new JLabel("Match:"));
        matchTypeCombo = new JComboBox<>(new String[]{
                "Contains (Case-Insensitive)",
                "Exact Match",
                "Starts With",
                "Regular Expression"
        });
        optionsRow.add(matchTypeCombo);

        optionsRow.add(new JLabel("Scope:"));
        List<String> tables = dbManager.getTableNames();
        List<String> scopeOptions = new ArrayList<>();
        scopeOptions.add("All Tables (" + tables.size() + ")");
        scopeOptions.addAll(tables);
        tableScopeCombo = new JComboBox<>(scopeOptions.toArray(new String[0]));
        tableScopeCombo.setPreferredSize(new Dimension(200, 26));
        optionsRow.add(tableScopeCombo);

        optionsRow.add(new JLabel("Max Per Table:"));
        limitSpinner = new JSpinner(new SpinnerNumberModel(50, 5, 1000, 25));
        optionsRow.add(limitSpinner);

        matchCountBadge = new JLabel("  0 Matches Found  ");
        matchCountBadge.setOpaque(true);
        matchCountBadge.setBackground(new Color(100, 116, 139));
        matchCountBadge.setForeground(Color.WHITE);
        matchCountBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        matchCountBadge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        optionsRow.add(matchCountBadge);

        topPanel.add(searchRow, BorderLayout.NORTH);
        topPanel.add(optionsRow, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // --- 2. Center: Results Table ---
        String[] columns = {"Table", "Column", "Matched Value", "Row Primary Key / Identifier"};
        resultsModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        resultsTable = new JTable(resultsModel);
        resultsTable.setRowHeight(25);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sorter = new TableRowSorter<>(resultsModel);
        resultsTable.setRowSorter(sorter);

        // Custom cell renderer to highlight matched table and column
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(320);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(300);

        DefaultTableCellRenderer boldRenderer = new DefaultTableCellRenderer();
        boldRenderer.setFont(boldRenderer.getFont().deriveFont(Font.BOLD));
        resultsTable.getColumnModel().getColumn(0).setCellRenderer(boldRenderer);

        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    triggerRowJump();
                }
            }
        });

        // Live Results Filter bar
        JPanel centerContainer = new JPanel(new BorderLayout());
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.add(new JLabel("Filter Results:"));
        JTextField liveFilterField = new JTextField(20);
        liveFilterField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String txt = liveFilterField.getText();
                if (txt == null || txt.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(txt)));
                }
            }
        });
        filterBar.add(liveFilterField);
        centerContainer.add(filterBar, BorderLayout.NORTH);
        centerContainer.add(new JScrollPane(resultsTable), BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);

        // --- 3. Bottom: Progress & Action Controls ---
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBorder(new EmptyBorder(6, 12, 10, 12));

        JPanel progressPanel = new JPanel(new BorderLayout(6, 4));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        statusLabel = new JLabel("Enter a query and click 'Search' or press Enter.");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);

        progressPanel.add(progressBar, BorderLayout.NORTH);
        progressPanel.add(statusLabel, BorderLayout.SOUTH);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton jumpBtn = new JButton("🔍 Jump to Table Row");
        jumpBtn.setFont(jumpBtn.getFont().deriveFont(Font.BOLD));
        jumpBtn.setBackground(new Color(16, 185, 129));
        jumpBtn.setForeground(Color.WHITE);
        jumpBtn.setFocusPainted(false);
        jumpBtn.addActionListener(e -> triggerRowJump());

        JButton copyValBtn = new JButton("📋 Copy Value");
        copyValBtn.addActionListener(e -> copySelectedValue());

        JButton exportCsvBtn = new JButton("📄 Export CSV");
        exportCsvBtn.addActionListener(e -> exportHitsToCsv());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> {
            stopSearch();
            dispose();
        });

        buttonBar.add(jumpBtn);
        buttonBar.add(copyValBtn);
        buttonBar.add(exportCsvBtn);
        buttonBar.add(closeBtn);

        bottomContainer.add(progressPanel, BorderLayout.CENTER);
        bottomContainer.add(buttonBar, BorderLayout.EAST);
        add(bottomContainer, BorderLayout.SOUTH);

        // Shortcut Esc to close
        KeyStroke escKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escKey, "closeDialog");
        getRootPane().getActionMap().put("closeDialog", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                stopSearch();
                dispose();
            }
        });
    }

    private void startSearch() {
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a search term.", "Empty Query", JOptionPane.WARNING_MESSAGE);
            searchField.requestFocus();
            return;
        }

        stopSearch();

        resultsModel.setRowCount(0);
        hitsList.clear();
        matchCountBadge.setText("  0 Matches Found  ");
        matchCountBadge.setBackground(new Color(100, 116, 139));

        searchBtn.setEnabled(false);
        stopBtn.setEnabled(true);

        String scope = (String) tableScopeCombo.getSelectedItem();
        List<String> targetTables = new ArrayList<>();
        if (scope == null || scope.startsWith("All Tables")) {
            targetTables.addAll(dbManager.getTableNames());
        } else {
            targetTables.add(scope);
        }

        String matchType = (String) matchTypeCombo.getSelectedItem();
        int maxPerTable = (Integer) limitSpinner.getValue();

        progressBar.setMaximum(targetTables.size());
        progressBar.setValue(0);
        progressBar.setString("0 / " + targetTables.size() + " tables scanned");

        currentWorker = new SwingWorker<Void, SearchHit>() {
            private int tablesScanned = 0;
            private int totalMatches = 0;

            @Override
            protected Void doInBackground() {
                for (String tbl : targetTables) {
                    if (isCancelled()) break;

                    tablesScanned++;
                    publishProgress(tablesScanned, targetTables.size(), tbl, totalMatches);

                    scanTable(tbl, query, matchType, maxPerTable);
                }
                return null;
            }

            private void publishProgress(int current, int total, String currentTable, int matchCount) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(current);
                    progressBar.setString(current + " / " + total + " tables scanned (" + matchCount + " matches)");
                    statusLabel.setText("Scanning table: " + currentTable + "...");
                });
            }

            private void scanTable(String tbl, String q, String mType, int limit) {
                List<String> cols = dbManager.getColumnNames(tbl);
                if (cols.isEmpty()) return;

                List<String> pks = dbManager.getPrimaryKeys(tbl);
                String patternLower = q.toLowerCase();

                // 1. Try SQL query first for speed
                boolean sqlAttemptSuccess = false;
                if (!"Regular Expression".equals(mType)) {
                    try {
                        StringBuilder sql = new StringBuilder("SELECT * FROM ");
                        sql.append(dbManager.quoteTableName(tbl)).append(" WHERE ");
                        List<Object> params = new ArrayList<>();

                        String castType = dbManager.isMySQL() ? "CHAR" : "TEXT";
                        for (int i = 0; i < cols.size(); i++) {
                            if (i > 0) sql.append(" OR ");
                            String colQuoted = dbManager.quoteColumnName(cols.get(i));

                            if ("Exact Match".equals(mType)) {
                                sql.append("CAST(").append(colQuoted).append(" AS ").append(castType).append(") = ?");
                                params.add(q);
                            } else if ("Starts With".equals(mType)) {
                                if (dbManager.isPostgres()) {
                                    sql.append("CAST(").append(colQuoted).append(" AS TEXT) ILIKE ?");
                                } else {
                                    sql.append("LOWER(CAST(").append(colQuoted).append(" AS ").append(castType).append(")) LIKE ?");
                                }
                                params.add(q.toLowerCase() + "%");
                            } else { // Contains
                                if (dbManager.isPostgres()) {
                                    sql.append("CAST(").append(colQuoted).append(" AS TEXT) ILIKE ?");
                                } else {
                                    sql.append("LOWER(CAST(").append(colQuoted).append(" AS ").append(castType).append(")) LIKE ?");
                                }
                                params.add("%" + q.toLowerCase() + "%");
                            }
                        }

                        if (!dbManager.isAccess()) {
                            sql.append(" LIMIT ").append(limit);
                        }

                        try (PreparedStatement pstmt = dbManager.connection.prepareStatement(sql.toString())) {
                            for (int p = 0; p < params.size(); p++) {
                                pstmt.setObject(p + 1, params.get(p));
                            }
                            try (ResultSet rs = pstmt.executeQuery()) {
                                processResultSetRows(rs, tbl, cols, pks, q, mType);
                                sqlAttemptSuccess = true;
                            }
                        }
                    } catch (Exception ex) {
                        // Fallback to memory scanning on unsupported types
                    }
                }

                // 2. Fallback to direct row scanning if SQL casting failed or Regex
                if (!sqlAttemptSuccess && !isCancelled()) {
                    try {
                        String query = dbManager.isAccess() ? "SELECT TOP " + limit + " * FROM " + dbManager.quoteTableName(tbl)
                                : "SELECT * FROM " + dbManager.quoteTableName(tbl) + " LIMIT " + limit;
                        try (Statement stmt = dbManager.connection.createStatement();
                             ResultSet rs = stmt.executeQuery(query)) {
                            processResultSetRows(rs, tbl, cols, pks, q, mType);
                        }
                    } catch (Exception ignored) {}
                }
            }

            private void processResultSetRows(ResultSet rs, String tbl, List<String> cols, List<String> pks, String q, String mType) throws Exception {
                Pattern regexPattern = null;
                if ("Regular Expression".equals(mType)) {
                    try {
                        regexPattern = Pattern.compile(q, Pattern.CASE_INSENSITIVE);
                    } catch (Exception ex) {
                        return;
                    }
                }

                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();

                while (rs.next() && !isCancelled()) {
                    Map<String, Object> allRowVals = new LinkedHashMap<>();
                    Map<String, Object> pkVals = new LinkedHashMap<>();

                    for (int c = 1; c <= colCount; c++) {
                        String cName = md.getColumnName(c);
                        Object val = rs.getObject(c);
                        allRowVals.put(cName, val);
                        if (pks.contains(cName)) {
                            pkVals.put(cName, val);
                        }
                    }

                    // Build PK display
                    StringBuilder pkSb = new StringBuilder();
                    if (!pkVals.isEmpty()) {
                        boolean first = true;
                        for (Map.Entry<String, Object> e : pkVals.entrySet()) {
                            if (!first) pkSb.append(", ");
                            first = false;
                            pkSb.append(e.getKey()).append("=").append(e.getValue());
                        }
                    } else {
                        // If no PK, pick first column
                        if (!allRowVals.isEmpty()) {
                            Map.Entry<String, Object> firstEntry = allRowVals.entrySet().iterator().next();
                            pkSb.append(firstEntry.getKey()).append("=").append(firstEntry.getValue());
                        }
                    }
                    String pkDisplay = pkSb.toString();

                    // Check which column(s) matched
                    for (Map.Entry<String, Object> entry : allRowVals.entrySet()) {
                        Object cellVal = entry.getValue();
                        if (cellVal == null) continue;
                        String valStr = cellVal.toString();
                        boolean matched = false;

                        if ("Exact Match".equals(mType)) {
                            matched = valStr.equalsIgnoreCase(q);
                        } else if ("Starts With".equals(mType)) {
                            matched = valStr.toLowerCase().startsWith(q.toLowerCase());
                        } else if ("Regular Expression".equals(mType) && regexPattern != null) {
                            matched = regexPattern.matcher(valStr).find();
                        } else {
                            matched = valStr.toLowerCase().contains(q.toLowerCase());
                        }

                        if (matched) {
                            totalMatches++;
                            SearchHit hit = new SearchHit(tbl, entry.getKey(), valStr, pkDisplay, pkVals, allRowVals);
                            publish(hit);
                        }
                    }
                }
            }

            @Override
            protected void process(List<SearchHit> chunks) {
                for (SearchHit hit : chunks) {
                    hitsList.add(hit);
                    resultsModel.addRow(new Object[]{hit.tableName, hit.columnName, hit.matchedValue, hit.pkDisplay});
                }
                matchCountBadge.setText("  " + hitsList.size() + " Matches Found  ");
                matchCountBadge.setBackground(new Color(16, 185, 129));
            }

            @Override
            protected void done() {
                searchBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                if (isCancelled()) {
                    statusLabel.setText("Search stopped by user. Found " + hitsList.size() + " match(es).");
                    progressBar.setString("Search Cancelled (" + hitsList.size() + " matches)");
                } else {
                    statusLabel.setText("Search complete! Found " + hitsList.size() + " match(es) across " + targetTables.size() + " tables.");
                    progressBar.setValue(progressBar.getMaximum());
                    progressBar.setString("Completed (" + hitsList.size() + " matches)");
                }
            }
        };

        currentWorker.execute();
    }

    private void stopSearch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            currentWorker = null;
        }
        searchBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    private void triggerRowJump() {
        int selectedViewRow = resultsTable.getSelectedRow();
        if (selectedViewRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a match from the results table to jump to.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = resultsTable.convertRowIndexToModel(selectedViewRow);
        if (modelRow >= 0 && modelRow < hitsList.size()) {
            SearchHit hit = hitsList.get(modelRow);
            if (navigationHandler != null) {
                navigationHandler.navigateToRow(hit.tableName, hit.columnName, hit.matchedValue, hit.pkValues);
            }
        }
    }

    private void copySelectedValue() {
        int selectedViewRow = resultsTable.getSelectedRow();
        if (selectedViewRow >= 0) {
            Object val = resultsTable.getValueAt(selectedViewRow, 2);
            if (val != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(val.toString()), null);
                statusLabel.setText("Copied: " + val.toString());
            }
        }
    }

    private void exportHitsToCsv() {
        if (hitsList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No search results to export.", "Empty Results", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("omnisearch_results.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File file = chooser.getSelectedFile();
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                writer.println("Table,Column,Matched Value,Primary Key Identifier");
                for (SearchHit hit : hitsList) {
                    writer.println(String.format("\"%s\",\"%s\",\"%s\",\"%s\"",
                            hit.tableName.replace("\"", "\"\""),
                            hit.columnName.replace("\"", "\"\""),
                            hit.matchedValue.replace("\"", "\"\""),
                            hit.pkDisplay.replace("\"", "\"\"")));
                }
                JOptionPane.showMessageDialog(this, "Successfully exported " + hitsList.size() + " matches to:\n" + file.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export CSV:\n" + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
