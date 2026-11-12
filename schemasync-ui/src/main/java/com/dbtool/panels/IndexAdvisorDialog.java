package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.List;

/**
 * Database Performance & Index Optimization Advisor.
 * Analyzes database schema to detect unindexed foreign keys, redundant composite indexes,
 * missing primary keys, high-cardinality candidate search columns, and table bloat statistics.
 * Provides one-click fix execution, batch index creation, and DDL export.
 */
public class IndexAdvisorDialog extends JDialog {

    public enum AdvisorCategory {
        UNINDEXED_FK("🔴 Unindexed Foreign Key", new Color(239, 68, 68)),
        REDUNDANT_INDEX("🟡 Redundant Index", new Color(245, 158, 11)),
        MISSING_PK("🟣 Missing Primary Key", new Color(168, 85, 247)),
        SEARCH_CANDIDATE("🔵 Search Candidate", new Color(59, 130, 246)),
        TABLE_STATS("ℹ Table Stats", new Color(107, 114, 128));

        public final String label;
        public final Color color;
        AdvisorCategory(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    public static class PerformanceIssue {
        public final AdvisorCategory category;
        public final String tableName;
        public final String columnName;
        public final String title;
        public final String rationale;
        public final String severity;
        public final String suggestedDdl;

        public PerformanceIssue(AdvisorCategory category, String tableName, String columnName,
                                String title, String rationale, String severity, String suggestedDdl) {
            this.category = category;
            this.tableName = tableName;
            this.columnName = columnName;
            this.title = title;
            this.rationale = rationale;
            this.severity = severity;
            this.suggestedDdl = suggestedDdl;
        }
    }

    private final DatabaseManager dbManager;
    private final String initialFocusTable;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTable issuesTable;
    private final JLabel scoreBadgeLabel;
    private final JLabel summaryStatsLabel;
    private final RSyntaxTextArea ddlArea;
    private final JTextField searchField = new JTextField(12);

    private final List<PerformanceIssue> allIssues = new ArrayList<>();
    private final JToggleButton btnAll = new JToggleButton("All");
    private final JToggleButton btnUnindexedFk = new JToggleButton("Unindexed FKs");
    private final JToggleButton btnRedundant = new JToggleButton("Redundant Indexes");
    private final JToggleButton btnMissingPk = new JToggleButton("Missing PKs");
    private final JToggleButton btnCandidates = new JToggleButton("Search Candidates");

    public IndexAdvisorDialog(Frame parent, DatabaseManager dbManager) {
        this(parent, dbManager, null);
    }

    public IndexAdvisorDialog(Frame parent, DatabaseManager dbManager, String initialFocusTable) {
        super(parent, "⚡ SchemaSync Index & Performance Optimization Advisor", true);
        this.dbManager = dbManager;
        this.initialFocusTable = initialFocusTable;

        setSize(1020, 680);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // 1. Header Banner
        JPanel headerPanel = new JPanel(new BorderLayout(15, 8));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        JLabel titleLabel = new JLabel("⚡ Database Index & Performance Diagnostics");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        summaryStatsLabel = new JLabel("Scanning schema for missing indexes, redundant indexes, and query bottlenecks...");
        summaryStatsLabel.setForeground(Color.GRAY);
        titleBlock.add(titleLabel);
        titleBlock.add(summaryStatsLabel);

        scoreBadgeLabel = new JLabel("Health Score: --", SwingConstants.CENTER);
        scoreBadgeLabel.setFont(scoreBadgeLabel.getFont().deriveFont(Font.BOLD, 14f));
        scoreBadgeLabel.setOpaque(true);
        scoreBadgeLabel.setBackground(new Color(30, 41, 59));
        scoreBadgeLabel.setForeground(Color.WHITE);
        scoreBadgeLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85), 1, true),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(scoreBadgeLabel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // 2. Filter Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));

        ButtonGroup filterGroup = new ButtonGroup();
        filterGroup.add(btnAll);
        filterGroup.add(btnUnindexedFk);
        filterGroup.add(btnRedundant);
        filterGroup.add(btnMissingPk);
        filterGroup.add(btnCandidates);
        btnAll.setSelected(true);

        filterBar.add(new JLabel("Filter:"));
        filterBar.add(btnAll);
        filterBar.add(btnUnindexedFk);
        filterBar.add(btnRedundant);
        filterBar.add(btnMissingPk);
        filterBar.add(btnCandidates);

        filterBar.add(new JLabel("   Search:"));
        searchField.setToolTipText("Filter by table name or column");
        filterBar.add(searchField);

        // Filter Actions
        btnAll.addActionListener(e -> applyFilter());
        btnUnindexedFk.addActionListener(e -> applyFilter());
        btnRedundant.addActionListener(e -> applyFilter());
        btnMissingPk.addActionListener(e -> applyFilter());
        btnCandidates.addActionListener(e -> applyFilter());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });

        // 3. Center Split (Table on top, SQL / Rationale on bottom)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.62);

        String[] cols = {"Severity", "Category", "Table", "Columns", "Issue Title", "Performance Impact"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        issuesTable = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        issuesTable.setRowSorter(sorter);
        issuesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        issuesTable.setRowHeight(24);

        // Severity column color renderer
        issuesTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if (val.contains("Critical")) l.setForeground(new Color(239, 68, 68));
                else if (val.contains("High")) l.setForeground(new Color(249, 115, 22));
                else if (val.contains("Medium")) l.setForeground(new Color(245, 158, 11));
                else l.setForeground(new Color(59, 130, 246));
                return l;
            }
        });

        JPanel tableContainer = new JPanel(new BorderLayout(0, 4));
        tableContainer.add(filterBar, BorderLayout.NORTH);
        tableContainer.add(new JScrollPane(issuesTable), BorderLayout.CENTER);
        splitPane.setTopComponent(tableContainer);

        // Bottom Detail Panel
        JPanel bottomDetail = new JPanel(new BorderLayout(5, 5));
        bottomDetail.setBorder(BorderFactory.createTitledBorder("Suggested Remediation SQL DDL"));

        ddlArea = new RSyntaxTextArea(4, 30);
        ddlArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        ddlArea.setEditable(true);
        bottomDetail.add(new RTextScrollPane(ddlArea), BorderLayout.CENTER);

        // Selection Listener
        issuesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = issuesTable.getSelectedRow();
                if (row >= 0) {
                    int mIdx = issuesTable.convertRowIndexToModel(row);
                    if (mIdx < allIssues.size()) {
                        ddlArea.setText(allIssues.get(mIdx).suggestedDdl);
                    }
                }
            }
        });

        splitPane.setBottomComponent(bottomDetail);
        add(splitPane, BorderLayout.CENTER);

        // 4. Action Toolbar
        JPanel bottomBar = new JPanel(new BorderLayout(10, 5));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JButton applySelectedBtn = new JButton("⚡ Apply Selected Fix");
        applySelectedBtn.setFont(applySelectedBtn.getFont().deriveFont(Font.BOLD, 12f));
        applySelectedBtn.setForeground(ThemeManager.getSuccessColor());
        applySelectedBtn.setToolTipText("Execute the active SQL statement directly on this database");
        applySelectedBtn.addActionListener(e -> applySelectedFix());
        leftActions.add(applySelectedBtn);

        JButton applyAllIndexesBtn = new JButton("⚡ Apply All Recommended Indexes");
        applyAllIndexesBtn.setToolTipText("Batch execute all safe CREATE INDEX statements");
        applyAllIndexesBtn.addActionListener(e -> applyAllRecommendedIndexes());
        leftActions.add(applyAllIndexesBtn);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        JButton copySqlBtn = new JButton("📋 Copy All Fixes");
        copySqlBtn.addActionListener(e -> copyAllSql());
        rightActions.add(copySqlBtn);

        JButton exportSqlBtn = new JButton("💾 Export Script (.sql)");
        exportSqlBtn.addActionListener(e -> exportScript());
        rightActions.add(exportSqlBtn);

        JButton rescanBtn = new JButton("🔄 Re-Scan");
        rescanBtn.addActionListener(e -> runDiagnostics());
        rightActions.add(rescanBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        rightActions.add(closeBtn);

        bottomBar.add(leftActions, BorderLayout.WEST);
        bottomBar.add(rightActions, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        // Run initial diagnostics on startup
        SwingUtilities.invokeLater(this::runDiagnostics);
    }

    private void applyFilter() {
        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        if (btnUnindexedFk.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)Foreign Key", 1));
        } else if (btnRedundant.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)Redundant", 1));
        } else if (btnMissingPk.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)Primary Key", 1));
        } else if (btnCandidates.isSelected()) {
            filters.add(RowFilter.regexFilter("(?i)Search Candidate", 1));
        }

        String search = searchField.getText().trim();
        if (!search.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(search), 2, 3, 4));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    private void runDiagnostics() {
        if (dbManager == null || dbManager.connection == null) {
            summaryStatsLabel.setText("No database connection active.");
            return;
        }

        allIssues.clear();
        tableModel.setRowCount(0);
        ddlArea.setText("");
        summaryStatsLabel.setText("Auditing database indexes, keys, and schemas...");

        new Thread(() -> {
            try {
                Connection conn = dbManager.connection;
                DatabaseMetaData meta = conn.getMetaData();
                String dbProduct = meta.getDatabaseProductName();
                boolean isPostgres = dbProduct != null && dbProduct.toLowerCase().contains("postgres");
                boolean isMysql = dbProduct != null && (dbProduct.toLowerCase().contains("mysql") || dbProduct.toLowerCase().contains("mariadb"));

                List<String> tables = dbManager.getTableNames();
                if (initialFocusTable != null && !initialFocusTable.trim().isEmpty()) {
                    tables = Collections.singletonList(initialFocusTable.trim());
                }

                int unindexedFkCount = 0;
                int redundantIndexCount = 0;
                int missingPkCount = 0;
                int candidateCount = 0;

                for (String tbl : tables) {
                    if (tbl.startsWith("pg_") || tbl.startsWith("sql_") || tbl.equalsIgnoreCase("information_schema")) {
                        continue;
                    }

                    // 1. Missing Primary Key Check
                    boolean hasPk = false;
                    try (ResultSet rs = meta.getPrimaryKeys(null, null, tbl)) {
                        if (rs.next()) hasPk = true;
                    }
                    if (!hasPk) {
                        String pkDdl = "-- Recommended: Add an identity primary key\nALTER TABLE " + quote(tbl) + " ADD COLUMN id SERIAL PRIMARY KEY;";
                        if (isMysql) {
                            pkDdl = "ALTER TABLE " + quote(tbl) + " ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST;";
                        }
                        PerformanceIssue issue = new PerformanceIssue(
                            AdvisorCategory.MISSING_PK, tbl, "(none)",
                            "Missing Primary Key",
                            "Table has no primary key defined. Without a primary key, rows cannot be uniquely identified, causing table-scan updates and replication failures.",
                            "🔴 Critical", pkDdl
                        );
                        allIssues.add(issue);
                        missingPkCount++;
                    }

                    // Inspect existing table indexes
                    Map<String, List<String>> indexColumns = new LinkedHashMap<>();
                    Map<String, Boolean> indexUnique = new HashMap<>();

                    try (ResultSet rs = meta.getIndexInfo(null, null, tbl, false, false)) {
                        while (rs.next()) {
                            String idxName = rs.getString("INDEX_NAME");
                            if (idxName == null || idxName.equalsIgnoreCase("PRIMARY")) continue;
                            String colName = rs.getString("COLUMN_NAME");
                            if (colName == null) continue;
                            boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                            indexUnique.put(idxName, !nonUnique);
                            indexColumns.computeIfAbsent(idxName, k -> new ArrayList<>()).add(colName);
                        }
                    } catch (Exception ignored) {}

                    // 2. Unindexed Foreign Keys Check
                    Map<String, String[]> fks = dbManager.getForeignKeys(tbl);
                    Set<String> indexedLeadingCols = new HashSet<>();
                    for (List<String> colsList : indexColumns.values()) {
                        if (!colsList.isEmpty()) {
                            indexedLeadingCols.add(colsList.get(0).toLowerCase());
                        }
                    }

                    for (Map.Entry<String, String[]> fkEntry : fks.entrySet()) {
                        String fkCol = fkEntry.getKey();
                        String pkTable = fkEntry.getValue()[0];
                        String pkCol = fkEntry.getValue()[1];

                        if (!indexedLeadingCols.contains(fkCol.toLowerCase())) {
                            String idxName = "idx_" + sanitize(tbl) + "_" + sanitize(fkCol);
                            String createDdl = "CREATE INDEX " + quote(idxName) + " ON " + quote(tbl) + " (" + quote(fkCol) + ");";
                            PerformanceIssue issue = new PerformanceIssue(
                                AdvisorCategory.UNINDEXED_FK, tbl, fkCol,
                                "Unindexed Foreign Key (" + fkCol + " ➔ " + pkTable + "." + pkCol + ")",
                                "FK column '" + fkCol + "' has no leading index. Causes full-table scans during JOINs and row locks during parent table UPDATE / DELETE operations.",
                                "🟠 High", createDdl
                            );
                            allIssues.add(issue);
                            unindexedFkCount++;
                        }
                    }

                    // 3. Redundant / Duplicate Index Check
                    for (Map.Entry<String, List<String>> entryA : indexColumns.entrySet()) {
                        String idxA = entryA.getKey();
                        List<String> colsA = entryA.getValue();
                        boolean isUniqueA = Boolean.TRUE.equals(indexUnique.get(idxA));

                        for (Map.Entry<String, List<String>> entryB : indexColumns.entrySet()) {
                            String idxB = entryB.getKey();
                            if (idxA.equalsIgnoreCase(idxB)) continue;
                            List<String> colsB = entryB.getValue();

                            if (!isUniqueA && colsB.size() > colsA.size()) {
                                boolean isPrefix = true;
                                for (int i = 0; i < colsA.size(); i++) {
                                    if (!colsA.get(i).equalsIgnoreCase(colsB.get(i))) {
                                        isPrefix = false;
                                        break;
                                    }
                                }
                                if (isPrefix) {
                                    String dropDdl = "DROP INDEX " + quote(idxA) + (isPostgres ? "" : " ON " + quote(tbl)) + ";";
                                    PerformanceIssue issue = new PerformanceIssue(
                                        AdvisorCategory.REDUNDANT_INDEX, tbl, String.join(", ", colsA),
                                        "Redundant Index: " + idxA,
                                        "Index '" + idxA + "' is redundant because composite index '" + idxB + "' (" + String.join(", ", colsB) + ") already covers these leading columns. Wastes disk space and slows INSERT/UPDATEs.",
                                        "🟡 Medium", dropDdl
                                    );
                                    allIssues.add(issue);
                                    redundantIndexCount++;
                                }
                            }
                        }
                    }

                    // 4. Candidate Search Column Check
                    List<String> cols = dbManager.getColumnNames(tbl);
                    Set<String> allIndexedCols = new HashSet<>();
                    for (List<String> cList : indexColumns.values()) {
                        for (String c : cList) allIndexedCols.add(c.toLowerCase());
                    }

                    if (cols != null) {
                        for (String col : cols) {
                            String colLower = col.toLowerCase();
                            boolean isCandidateName = colLower.endsWith("_uuid") || colLower.endsWith("_code") ||
                                                     colLower.equals("email") || colLower.equals("username") ||
                                                     colLower.equals("phone") || colLower.equals("slug");

                            if (isCandidateName && !allIndexedCols.contains(colLower) && !fks.containsKey(col)) {
                                String idxName = "idx_" + sanitize(tbl) + "_" + sanitize(col);
                                String ddl = "CREATE INDEX " + quote(idxName) + " ON " + quote(tbl) + " (" + quote(col) + ");";
                                PerformanceIssue issue = new PerformanceIssue(
                                    AdvisorCategory.SEARCH_CANDIDATE, tbl, col,
                                    "Search Column Candidate: " + col,
                                    "Column '" + col + "' appears to be a high-frequency filter/lookup field but lacks an index. Adding an index can accelerate WHERE " + col + " = ... queries.",
                                    "🔵 Info", ddl
                                );
                                allIssues.add(issue);
                                candidateCount++;
                            }
                        }
                    }
                }

                // Compute Health Score
                int totalPenalty = (missingPkCount * 25) + (unindexedFkCount * 12) + (redundantIndexCount * 6) + (candidateCount * 2);
                final int finalScore = Math.max(0, 100 - totalPenalty);
                final int finalUnindexed = unindexedFkCount;
                final int finalRedundant = redundantIndexCount;
                final int finalMissingPk = missingPkCount;
                final int finalCandidates = candidateCount;

                SwingUtilities.invokeLater(() -> {
                    for (PerformanceIssue issue : allIssues) {
                        tableModel.addRow(new Object[]{
                            issue.severity,
                            issue.category.label,
                            issue.tableName,
                            issue.columnName,
                            issue.title,
                            issue.rationale
                        });
                    }

                    scoreBadgeLabel.setText("Health Score: " + finalScore + " / 100");
                    if (finalScore >= 85) scoreBadgeLabel.setBackground(new Color(16, 185, 129));
                    else if (finalScore >= 60) scoreBadgeLabel.setBackground(new Color(245, 158, 11));
                    else scoreBadgeLabel.setBackground(new Color(239, 68, 68));

                    summaryStatsLabel.setText(String.format("Found %d issues: 🔴 %d Unindexed FKs, 🟡 %d Redundant Indexes, 🟣 %d Missing PKs, 🔵 %d Search Candidates",
                        allIssues.size(), finalUnindexed, finalRedundant, finalMissingPk, finalCandidates));

                    btnUnindexedFk.setText("Unindexed FKs (" + finalUnindexed + ")");
                    btnRedundant.setText("Redundant (" + finalRedundant + ")");
                    btnMissingPk.setText("Missing PKs (" + finalMissingPk + ")");
                    btnCandidates.setText("Search Candidates (" + finalCandidates + ")");
                    btnAll.setText("All (" + allIssues.size() + ")");

                    if (!allIssues.isEmpty()) {
                        issuesTable.setRowSelectionInterval(0, 0);
                    }
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    summaryStatsLabel.setText("Scan failed: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void applySelectedFix() {
        String sql = ddlArea.getText().trim();
        if (sql.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No SQL statement selected to apply.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to execute this optimization statement?\n\n" + sql,
            "Confirm Execute Optimization",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        try (Statement stmt = dbManager.connection.createStatement()) {
            stmt.execute(sql);
            JOptionPane.showMessageDialog(this, "Optimization statement executed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            runDiagnostics();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to execute statement: " + ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyAllRecommendedIndexes() {
        List<String> createStatements = new ArrayList<>();
        for (PerformanceIssue issue : allIssues) {
            if ((issue.category == AdvisorCategory.UNINDEXED_FK || issue.category == AdvisorCategory.SEARCH_CANDIDATE) &&
                issue.suggestedDdl.toUpperCase().startsWith("CREATE INDEX")) {
                createStatements.add(issue.suggestedDdl);
            }
        }

        if (createStatements.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No CREATE INDEX recommendations found.", "Apply All", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
            "Apply all " + createStatements.size() + " recommended CREATE INDEX statements now?",
            "Confirm Batch Optimization",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        int success = 0;
        int failed = 0;
        for (String ddl : createStatements) {
            try (Statement stmt = dbManager.connection.createStatement()) {
                stmt.execute(ddl);
                success++;
            } catch (Exception ex) {
                failed++;
            }
        }

        JOptionPane.showMessageDialog(this,
            String.format("Batch optimization complete!\n\nApplied: %d indexes\nFailed/Skipped: %d", success, failed),
            "Optimization Complete", JOptionPane.INFORMATION_MESSAGE);

        runDiagnostics();
    }

    private void copyAllSql() {
        StringBuilder sb = new StringBuilder("-- SchemaSync Database Performance & Index Remediation Script\n\n");
        for (PerformanceIssue issue : allIssues) {
            if (issue.suggestedDdl != null && !issue.suggestedDdl.trim().isEmpty()) {
                sb.append("-- ").append(issue.title).append(" on ").append(issue.tableName).append("\n");
                sb.append(issue.suggestedDdl).append("\n\n");
            }
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        JOptionPane.showMessageDialog(this, "All optimization DDL statements copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportScript() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("database-optimization.sql"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                StringBuilder sb = new StringBuilder("-- SchemaSync Database Performance & Index Remediation Script\n\n");
                for (PerformanceIssue issue : allIssues) {
                    if (issue.suggestedDdl != null && !issue.suggestedDdl.trim().isEmpty()) {
                        sb.append("-- ").append(issue.title).append(" on ").append(issue.tableName).append("\n");
                        sb.append(issue.suggestedDdl).append("\n\n");
                    }
                }
                fw.write(sb.toString());
                JOptionPane.showMessageDialog(this, "Optimization script saved to:\n" + chooser.getSelectedFile().getAbsolutePath(), "Export Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static String quote(String id) {
        if (id == null) return "";
        if (id.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return id;
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    private static String sanitize(String str) {
        if (str == null) return "";
        return str.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}
