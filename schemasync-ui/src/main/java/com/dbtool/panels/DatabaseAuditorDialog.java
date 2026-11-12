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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.List;

public class DatabaseAuditorDialog extends JDialog {

    public enum Severity {
        CRITICAL("🔴 Critical", new Color(239, 68, 68)),
        WARNING("🟡 Warning", new Color(245, 158, 11)),
        INFO("🔵 Info", new Color(59, 130, 246)),
        PASSED("🟢 Passed", new Color(16, 185, 129));

        public final String label;
        public final Color color;
        Severity(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    public static class AuditIssue {
        public final String category;
        public final String tableName;
        public final String title;
        public final String description;
        public final Severity severity;
        public final String suggestedDdl;

        public AuditIssue(String category, String tableName, String title, String description, Severity severity, String suggestedDdl) {
            this.category = category;
            this.tableName = tableName;
            this.title = title;
            this.description = description;
            this.severity = severity;
            this.suggestedDdl = suggestedDdl;
        }
    }

    private final DatabaseManager dbManager;
    private final DefaultTableModel issuesModel;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTable issuesTable;
    private final JLabel scoreBadgeLabel;
    private final JLabel summaryStatsLabel;
    private final RSyntaxTextArea ddlArea;
    private final JButton btnAll = new JButton("All");
    private final JButton btnCritical = new JButton("🔴 Critical Only");
    private final JButton btnWarnings = new JButton("🟡 Warnings Only");
    private final List<AuditIssue> allIssues = new ArrayList<>();

    public DatabaseAuditorDialog(Frame parent, DatabaseManager dbManager) {
        super(parent, "🛡 SchemaSync Database Health & Performance Auditor", true);
        this.dbManager = dbManager;

        setSize(960, 640);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // Header Panel with Health Score
        JPanel headerPanel = new JPanel(new BorderLayout(15, 10));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        JLabel titleLabel = new JLabel("Database Health & Performance Diagnostics");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        summaryStatsLabel = new JLabel("Scanning database schema and structure...");
        summaryStatsLabel.setForeground(Color.GRAY);
        titleBlock.add(titleLabel);
        titleBlock.add(summaryStatsLabel);

        scoreBadgeLabel = new JLabel("Score: --", SwingConstants.CENTER);
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

        // Center Split: Issues Table on top, Fix Script & Detail on bottom
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.6);

        // Filter Bar & Table
        JPanel tableContainer = new JPanel(new BorderLayout(5, 5));
        tableContainer.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        String[] cols = {"Severity", "Category", "Table", "Issue Title", "Description", "Suggested Action"};
        issuesModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        issuesTable = new JTable(issuesModel);
        sorter = new TableRowSorter<>(issuesModel);
        issuesTable.setRowSorter(sorter);
        issuesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filterBar.add(new JLabel("Filter:"));
        filterBar.add(btnAll);
        filterBar.add(btnCritical);
        filterBar.add(btnWarnings);

        btnAll.addActionListener(e -> sorter.setRowFilter(null));
        btnCritical.addActionListener(e -> sorter.setRowFilter(RowFilter.regexFilter("(?i)Critical", 0)));
        btnWarnings.addActionListener(e -> sorter.setRowFilter(RowFilter.regexFilter("(?i)Warning", 0)));

        tableContainer.add(filterBar, BorderLayout.NORTH);

        issuesTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if (val.contains("Critical")) l.setForeground(new Color(239, 68, 68));
                else if (val.contains("Warning")) l.setForeground(new Color(245, 158, 11));
                else if (val.contains("Passed")) l.setForeground(new Color(16, 185, 129));
                else l.setForeground(new Color(59, 130, 246));
                return l;
            }
        });

        tableContainer.add(new JScrollPane(issuesTable), BorderLayout.CENTER);
        splitPane.setTopComponent(tableContainer);

        // Bottom Detail & Fix DDL
        JPanel bottomDetail = new JPanel(new BorderLayout(5, 5));
        bottomDetail.setBorder(BorderFactory.createTitledBorder("Suggested Remediation DDL"));

        ddlArea = new RSyntaxTextArea(4, 30);
        ddlArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        ddlArea.setEditable(true);
        bottomDetail.add(new RTextScrollPane(ddlArea), BorderLayout.CENTER);

        issuesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selected = issuesTable.getSelectedRow();
                if (selected >= 0) {
                    int mIdx = issuesTable.convertRowIndexToModel(selected);
                    if (mIdx < allIssues.size()) {
                        ddlArea.setText(allIssues.get(mIdx).suggestedDdl);
                    }
                }
            }
        });

        splitPane.setBottomComponent(bottomDetail);
        add(splitPane, BorderLayout.CENTER);

        // Bottom Action Toolbar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton reAuditBtn = new JButton("🔄 Re-Scan Database");
        reAuditBtn.addActionListener(e -> runAudit());
        bottomBar.add(reAuditBtn);

        JButton copyAllFixBtn = new JButton("📋 Copy All Fix DDL");
        copyAllFixBtn.addActionListener(e -> {
            StringBuilder allFixes = new StringBuilder("-- SchemaSync Database Remediation Script\n\n");
            for (AuditIssue issue : allIssues) {
                if (issue.suggestedDdl != null && !issue.suggestedDdl.trim().isEmpty()) {
                    allFixes.append("-- Fix for: ").append(issue.title).append(" on ").append(issue.tableName).append("\n");
                    allFixes.append(issue.suggestedDdl).append("\n\n");
                }
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(allFixes.toString()), null);
            JOptionPane.showMessageDialog(this, "Remediation DDL script copied to clipboard!");
        });
        bottomBar.add(copyAllFixBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn);

        add(bottomBar, BorderLayout.SOUTH);

        // Automatically run initial audit
        SwingUtilities.invokeLater(this::runAudit);
    }

    private void runAudit() {
        if (dbManager == null || dbManager.connection == null) {
            summaryStatsLabel.setText("No database connected.");
            return;
        }

        allIssues.clear();
        issuesModel.setRowCount(0);
        summaryStatsLabel.setText("Auditing tables, primary keys, foreign keys, and indexes...");

        new Thread(() -> {
            try {
                Connection conn = dbManager.connection;
                DatabaseMetaData meta = conn.getMetaData();
                List<String> tables = dbManager.getTableNames();

                int criticalCount = 0;
                int warningCount = 0;
                int infoCount = 0;

                for (String tbl : tables) {
                    if (tbl.startsWith("pg_") || tbl.startsWith("sql_") || tbl.equalsIgnoreCase("information_schema")) continue;

                    // 1. Primary Key Check
                    boolean hasPk = false;
                    try (ResultSet rs = meta.getPrimaryKeys(null, null, tbl)) {
                        if (rs.next()) hasPk = true;
                    }
                    if (!hasPk) {
                        AuditIssue issue = new AuditIssue(
                            "Primary Keys", tbl, "Missing Primary Key",
                            "Table has no primary key defined. Without a PK, queries and ORMs cannot uniquely identify rows, and replication may fail.",
                            Severity.CRITICAL,
                            "-- Recommended: Add an identity primary key\nALTER TABLE " + quote(tbl) + " ADD COLUMN id SERIAL PRIMARY KEY;"
                        );
                        allIssues.add(issue);
                        criticalCount++;
                    }

                    // 2. Unindexed Foreign Key Check
                    Map<String, String> fkCols = new LinkedHashMap<>();
                    try (ResultSet rs = meta.getImportedKeys(null, null, tbl)) {
                        while (rs.next()) {
                            String fkCol = rs.getString("FKCOLUMN_NAME");
                            String pkTbl = rs.getString("PKTABLE_NAME");
                            if (fkCol != null) fkCols.put(fkCol, pkTbl);
                        }
                    }

                    Set<String> indexedFirstCols = new HashSet<>();
                    try (ResultSet rs = meta.getIndexInfo(null, null, tbl, false, false)) {
                        while (rs.next()) {
                            int ordinal = rs.getInt("ORDINAL_POSITION");
                            String colName = rs.getString("COLUMN_NAME");
                            if (ordinal == 1 && colName != null) {
                                indexedFirstCols.add(colName.toLowerCase());
                            }
                        }
                    }

                    for (Map.Entry<String, String> entry : fkCols.entrySet()) {
                        String fkCol = entry.getKey();
                        String pkTbl = entry.getValue();
                        if (!indexedFirstCols.contains(fkCol.toLowerCase())) {
                            AuditIssue issue = new AuditIssue(
                                "Indexing", tbl, "Unindexed Foreign Key (" + fkCol + ")",
                                "Foreign key column '" + fkCol + "' referencing '" + pkTbl + "' has no index. Causes slow sequential scans during JOINs and cascade updates.",
                                Severity.WARNING,
                                "CREATE INDEX idx_" + tbl + "_" + fkCol + " ON " + quote(tbl) + " (" + quote(fkCol) + ");"
                            );
                            allIssues.add(issue);
                            warningCount++;
                        }
                    }

                    // 3. Wide Table (Column Sprawl)
                    List<String> cols = dbManager.getColumnNames(tbl);
                    if (cols != null && cols.size() > 25) {
                        AuditIssue issue = new AuditIssue(
                            "Design", tbl, "Wide Table (" + cols.size() + " columns)",
                            "Table contains more than 25 columns. May indicate a normalization issue or table bloat leading to excessive I/O.",
                            Severity.INFO,
                            "-- Consider refactoring or splitting wide table into 1:1 related sub-tables."
                        );
                        allIssues.add(issue);
                        infoCount++;
                    }

                    // 4. Empty / Inactive Table check
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + quote(tbl))) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            AuditIssue issue = new AuditIssue(
                                "Storage", tbl, "Empty Table (0 rows)",
                                "Table has 0 records. Check if this table is orphaned, obsolete, or pending migration seeding.",
                                Severity.INFO,
                                "-- Verify whether table " + quote(tbl) + " is still required."
                            );
                            allIssues.add(issue);
                            infoCount++;
                        }
                    } catch (Exception ignored) {}
                }

                // Calculate Health Score
                int totalScore = Math.max(0, 100 - (criticalCount * 15) - (warningCount * 5) - (infoCount * 1));
                final int finalScore = totalScore;
                final int finalCrit = criticalCount;
                final int finalWarn = warningCount;
                final int finalInfo = infoCount;

                SwingUtilities.invokeLater(() -> {
                    for (AuditIssue issue : allIssues) {
                        issuesModel.addRow(new Object[]{
                            issue.severity.label,
                            issue.category,
                            issue.tableName,
                            issue.title,
                            issue.description,
                            issue.suggestedDdl.replace("\n", " ")
                        });
                    }

                    scoreBadgeLabel.setText("Health Score: " + finalScore + " / 100");
                    if (finalScore >= 85) scoreBadgeLabel.setBackground(new Color(16, 185, 129));
                    else if (finalScore >= 60) scoreBadgeLabel.setBackground(new Color(245, 158, 11));
                    else scoreBadgeLabel.setBackground(new Color(239, 68, 68));

                    summaryStatsLabel.setText(String.format("Found %d issues: 🔴 %d Critical, 🟡 %d Warnings, 🔵 %d Info",
                        allIssues.size(), finalCrit, finalWarn, finalInfo));

                    if (!allIssues.isEmpty()) {
                        issuesTable.setRowSelectionInterval(0, 0);
                    }
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    summaryStatsLabel.setText("Audit failed: " + ex.getMessage());
                });
            }
        }).start();
    }

    private static String quote(String id) {
        if (id == null) return "";
        if (id.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return id;
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }
}
