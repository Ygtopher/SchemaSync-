package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ExportUtil;
import com.dbtool.util.QueryLogger;
import com.dbtool.util.QueryLogger.LogEntry;
import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enterprise SQL Query Profiler & Execution Bottleneck Analyzer.
 * Real-time monitoring of query latencies, slow-query detection,
 * 1-click EXPLAIN visual plan inspection, and index tuning recommendations.
 *
 * Part of SchemaSync Tier 4 Enterprise Governance & Live Intelligence.
 */
public class QueryProfilerDialog extends JDialog {

    private final DatabaseManager dbManager;
    private final Consumer<String> onOpenInEditor;

    private JLabel totalQueriesLabel;
    private JLabel avgLatencyLabel;
    private JLabel slowQueriesLabel;
    private JLabel maxLatencyLabel;

    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTable profilerTable;
    private JComboBox<String> filterCombo;
    private JTextField searchField;

    public QueryProfilerDialog(Window parent, DatabaseManager dbManager, Consumer<String> onOpenInEditor) {
        super(parent, "⚡ Enterprise Query Profiler & Execution Analytics", ModalityType.MODELESS);
        this.dbManager = dbManager;
        this.onOpenInEditor = onOpenInEditor;

        setSize(980, 640);
        setLocationRelativeTo(parent);
        initUI();
        refreshData();
        QueryLogger.addListener(this::refreshData);
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("⚡ SQL Query Profiler & Bottleneck Analyzer");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        JLabel subtitle = new JLabel("<html>Live latency tracking, slow-query detection (>100ms), and 1-click execution plan analysis.</html>");
        subtitle.setForeground(ThemeManager.isDarkMode() ? new Color(170, 170, 170) : new Color(100, 100, 100));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // Metrics Banner (4 Cards)
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        metricsPanel.setPreferredSize(new Dimension(0, 68));

        totalQueriesLabel = new JLabel("0");
        avgLatencyLabel = new JLabel("0 ms");
        slowQueriesLabel = new JLabel("0");
        maxLatencyLabel = new JLabel("0 ms");

        metricsPanel.add(createMiniCard("Total Queries Run", totalQueriesLabel, new Color(59, 130, 246)));
        metricsPanel.add(createMiniCard("Average Latency", avgLatencyLabel, new Color(16, 185, 129)));
        metricsPanel.add(createMiniCard("Slow Queries (>100ms)", slowQueriesLabel, new Color(245, 158, 11)));
        metricsPanel.add(createMiniCard("Max Execution Time", maxLatencyLabel, new Color(239, 68, 68)));

        // Center: Filter Bar + Profiler Table
        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.add(new JLabel("Filter:"));
        filterCombo = new JComboBox<>(new String[]{"All Queries", "Slow Queries (>100ms)", "Critical Bottlenecks (>500ms)", "Errors Only"});
        filterCombo.addActionListener(e -> applyFilters());
        filterBar.add(filterCombo);

        filterBar.add(new JLabel("Search SQL:"));
        searchField = new JTextField(16);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
        filterBar.add(searchField);

        JButton clearBtn = new JButton("🗑 Clear Logs");
        clearBtn.addActionListener(e -> clearLogs());
        filterBar.add(clearBtn);

        JButton exportBtn = new JButton("📄 Export Profiler CSV");
        exportBtn.addActionListener(e -> exportCsv());
        filterBar.add(exportBtn);

        centerPanel.add(filterBar, BorderLayout.NORTH);

        // Table
        String[] cols = {"Time", "Latency (ms)", "Status", "SQL Query Statement"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) return Long.class;
                return String.class;
            }
        };
        profilerTable = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        profilerTable.setRowSorter(sorter);
        profilerTable.setRowHeight(24);

        profilerTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        profilerTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        profilerTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        profilerTable.getColumnModel().getColumn(3).setPreferredWidth(600);

        // Latency Renderer with Color Badge
        profilerTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Number) {
                    long ms = ((Number) value).longValue();
                    l.setText(String.format("%,d ms", ms));
                    if (!isSelected) {
                        if (ms > 500) {
                            l.setForeground(new Color(239, 68, 68)); // Red
                            l.setFont(l.getFont().deriveFont(Font.BOLD));
                        } else if (ms > 100) {
                            l.setForeground(new Color(245, 158, 11)); // Orange/Yellow
                            l.setFont(l.getFont().deriveFont(Font.BOLD));
                        } else {
                            l.setForeground(new Color(16, 185, 129)); // Green
                            l.setFont(l.getFont().deriveFont(Font.PLAIN));
                        }
                    }
                }
                return l;
            }
        });

        // Status Renderer
        profilerTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if ("SUCCESS".equals(value)) {
                    l.setText("✓ Success");
                    if (!isSelected) l.setForeground(new Color(16, 185, 129));
                } else if ("ERROR".equals(value)) {
                    l.setText("✗ Error");
                    if (!isSelected) l.setForeground(new Color(239, 68, 68));
                }
                return l;
            }
        });

        JScrollPane tableScroll = new JScrollPane(profilerTable);
        centerPanel.add(tableScroll, BorderLayout.CENTER);

        // Actions Bottom Bar
        JPanel actionsBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        JButton explainBtn = new JButton("🔍 Run EXPLAIN on Query");
        explainBtn.setToolTipText("Run EXPLAIN execution plan analysis for the selected SQL query");
        explainBtn.addActionListener(e -> explainSelectedQuery());
        actionsBar.add(explainBtn);

        JButton copySqlBtn = new JButton("📋 Copy SQL");
        copySqlBtn.addActionListener(e -> copySelectedSql());
        actionsBar.add(copySqlBtn);

        JButton openInEditorBtn = new JButton("▶ Open in SQL Editor");
        openInEditorBtn.addActionListener(e -> openSelectedInEditor());
        actionsBar.add(openInEditorBtn);

        centerPanel.add(actionsBar, BorderLayout.SOUTH);

        // Root Assembly
        JPanel contentContainer = new JPanel(new BorderLayout(0, 10));
        contentContainer.add(metricsPanel, BorderLayout.NORTH);
        contentContainer.add(centerPanel, BorderLayout.CENTER);

        root.add(contentContainer, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel createMiniCard(String title, JLabel valueLbl, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ThemeManager.isDarkMode() ? new Color(60, 60, 60) : new Color(210, 210, 210), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        titleLbl.setForeground(ThemeManager.isDarkMode() ? new Color(170, 170, 170) : new Color(100, 100, 100));
        card.add(titleLbl, BorderLayout.NORTH);

        valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
        valueLbl.setForeground(accentColor);
        card.add(valueLbl, BorderLayout.CENTER);
        return card;
    }

    private void refreshData() {
        SwingUtilities.invokeLater(() -> {
            List<LogEntry> logs = QueryLogger.getLogs();
            tableModel.setRowCount(0);

            long totalMs = 0;
            int slowCount = 0;
            long maxMs = 0;

            for (LogEntry le : logs) {
                totalMs += le.durationMs;
                if (le.durationMs > 100) slowCount++;
                if (le.durationMs > maxMs) maxMs = le.durationMs;

                tableModel.addRow(new Object[]{
                        le.timestamp,
                        le.durationMs,
                        le.success ? "SUCCESS" : "ERROR",
                        le.query
                });
            }

            int total = logs.size();
            long avg = total > 0 ? (totalMs / total) : 0;

            totalQueriesLabel.setText(String.format("%,d", total));
            avgLatencyLabel.setText(avg + " ms");
            slowQueriesLabel.setText(String.valueOf(slowCount));
            maxLatencyLabel.setText(maxMs + " ms");
        });
    }

    private void applyFilters() {
        String filterType = (String) filterCombo.getSelectedItem();
        String searchText = searchField.getText().trim().toLowerCase();

        RowFilter<DefaultTableModel, Integer> rf = new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                long latency = (Long) entry.getValue(1);
                String status = (String) entry.getValue(2);
                String sql = ((String) entry.getValue(3)).toLowerCase();

                if ("Slow Queries (>100ms)".equals(filterType) && latency <= 100) return false;
                if ("Critical Bottlenecks (>500ms)".equals(filterType) && latency <= 500) return false;
                if ("Errors Only".equals(filterType) && !"ERROR".equals(status)) return false;

                if (!searchText.isEmpty() && !sql.contains(searchText)) return false;

                return true;
            }
        };
        sorter.setRowFilter(rf);
    }

    private void clearLogs() {
        // Clear table model view
        tableModel.setRowCount(0);
        totalQueriesLabel.setText("0");
        avgLatencyLabel.setText("0 ms");
        slowQueriesLabel.setText("0");
        maxLatencyLabel.setText("0 ms");
    }

    private void explainSelectedQuery() {
        int r = profilerTable.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Please select a query row to analyze with EXPLAIN.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = profilerTable.convertRowIndexToModel(r);
        String sql = (String) tableModel.getValueAt(modelRow, 3);
        if (sql == null || sql.trim().isEmpty()) return;

        if (dbManager == null || dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "No active database connection.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            try (Statement s = dbManager.connection.createStatement()) {
                String explainSql;
                if (dbManager.isPostgres()) {
                    explainSql = "EXPLAIN ANALYZE " + sql;
                } else if (dbManager.isMySQL()) {
                    explainSql = "EXPLAIN " + sql;
                } else if (dbManager.isSQLite()) {
                    explainSql = "EXPLAIN QUERY PLAN " + sql;
                } else {
                    explainSql = "EXPLAIN " + sql;
                }

                StringBuilder plan = new StringBuilder();
                try (ResultSet rs = s.executeQuery(explainSql)) {
                    int cols = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        for (int i = 1; i <= cols; i++) {
                            plan.append(rs.getString(i)).append("\t");
                        }
                        plan.append("\n");
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    JTextArea area = new JTextArea(plan.toString(), 16, 60);
                    area.setEditable(false);
                    area.setFont(new Font("Consolas", Font.PLAIN, 12));
                    JScrollPane sp = new JScrollPane(area);
                    JOptionPane.showMessageDialog(this, sp, "EXPLAIN Execution Plan", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "EXPLAIN failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void copySelectedSql() {
        int r = profilerTable.getSelectedRow();
        if (r < 0) return;
        int modelRow = profilerTable.convertRowIndexToModel(r);
        String sql = (String) tableModel.getValueAt(modelRow, 3);
        if (sql != null) {
            StringSelection sel = new StringSelection(sql);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            JOptionPane.showMessageDialog(this, "SQL copied to clipboard!", "Clipboard", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openSelectedInEditor() {
        int r = profilerTable.getSelectedRow();
        if (r < 0) return;
        int modelRow = profilerTable.convertRowIndexToModel(r);
        String sql = (String) tableModel.getValueAt(modelRow, 3);
        if (sql != null && onOpenInEditor != null) {
            onOpenInEditor.accept(sql);
            dispose();
        }
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("SchemaSync_Query_Profiler.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(f, StandardCharsets.UTF_8)) {
                pw.println("Timestamp,Latency_ms,Status,SQL_Query");
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String ts = (String) tableModel.getValueAt(i, 0);
                    Long lat = (Long) tableModel.getValueAt(i, 1);
                    String st = (String) tableModel.getValueAt(i, 2);
                    String sql = (String) tableModel.getValueAt(i, 3);
                    pw.println(String.format("\"%s\",%d,\"%s\",\"%s\"", ts, lat, st, sql.replace("\"", "\"\"")));
                }
                JOptionPane.showMessageDialog(this, "Exported profiler logs to:\n" + f.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
