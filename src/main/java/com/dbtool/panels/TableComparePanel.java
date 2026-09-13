package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ExportUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.List;

public class TableComparePanel extends JPanel {

    private final DatabaseManager dbManager;
    private final JComboBox<String> tableADropdown = new JComboBox<>();
    private final JComboBox<String> tableBDropdown = new JComboBox<>();
    private final JComboBox<String> keyColDropdown = new JComboBox<>();
    private final JTextArea schemaDiffArea = new JTextArea();
    private final JTable dataTable = new JTable();
    private final JLabel summaryLabel = new JLabel("Select two tables to compare.");

    public TableComparePanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // Top controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("Table A:"));
        controlPanel.add(tableADropdown);
        controlPanel.add(new JLabel("Table B:"));
        controlPanel.add(tableBDropdown);
        controlPanel.add(new JLabel("Key Column:"));
        controlPanel.add(keyColDropdown);

        JButton schemaDiffBtn = new JButton("Schema Diff");
        JButton dataDiffBtn = new JButton("Data Diff");
        JButton rowCountBtn = new JButton("Row Counts");
        JButton exportBtn = new JButton("Export Diff CSV");
        JButton copySqlBtn = new JButton("📋 Copy SQL");
        controlPanel.add(schemaDiffBtn);
        controlPanel.add(dataDiffBtn);
        controlPanel.add(rowCountBtn);
        controlPanel.add(exportBtn);
        controlPanel.add(copySqlBtn);

        // Schema diff text area
        schemaDiffArea.setEditable(false);
        schemaDiffArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        schemaDiffArea.setText("Schema diff will appear here.");

        // Tab results
        JTabbedPane resultTabs = new JTabbedPane();
        resultTabs.addTab("Schema Diff", new JScrollPane(schemaDiffArea));
        resultTabs.addTab("Data Diff", new JScrollPane(dataTable));

        add(controlPanel, BorderLayout.NORTH);
        add(summaryLabel, BorderLayout.SOUTH);
        add(resultTabs, BorderLayout.CENTER);

        // Update key column dropdown when tableA changes
        tableADropdown.addActionListener(e -> refreshKeyColumns());

        // Actions
        schemaDiffBtn.addActionListener(e -> runSchemaDiff());
        dataDiffBtn.addActionListener(e -> runDataDiff(resultTabs));
        rowCountBtn.addActionListener(e -> runRowCounts());
        exportBtn.addActionListener(e -> ExportUtil.exportCsv(dataTable, this));
        copySqlBtn.addActionListener(e -> {
            if (lastQuery != null && !lastQuery.isEmpty()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(lastQuery), null);
                JOptionPane.showMessageDialog(this, "SQL copied to clipboard!");
            } else {
                JOptionPane.showMessageDialog(this, "No data diff query executed yet.");
            }
        });
    }

    private String lastQuery = "";

    public void refreshTables() {
        List<String> tables = dbManager.getTableNames();
        tableADropdown.removeAllItems();
        tableBDropdown.removeAllItems();
        for (String t : tables) {
            tableADropdown.addItem(t);
            tableBDropdown.addItem(t);
        }
    }

    private void refreshKeyColumns() {
        keyColDropdown.removeAllItems();
        String tblA = (String) tableADropdown.getSelectedItem();
        if (tblA != null) {
            for (String col : dbManager.getColumnNames(tblA)) keyColDropdown.addItem(col);
        }
    }

    private void runSchemaDiff() {
        String tblA = (String) tableADropdown.getSelectedItem();
        String tblB = (String) tableBDropdown.getSelectedItem();
        if (tblA == null || tblB == null) return;
        new Thread(() -> {
            try {
                List<String> colsA = dbManager.getColumnNames(tblA);
                List<String> colsB = dbManager.getColumnNames(tblB);
                StringBuilder sb = new StringBuilder();
                sb.append("=== Schema Diff: ").append(tblA).append(" vs ").append(tblB).append(" ===\n\n");

                sb.append("Columns in ").append(tblA).append(" but NOT in ").append(tblB).append(":\n");
                boolean any = false;
                for (String c : colsA) { if (!colsB.contains(c)) { sb.append("  - ").append(c).append("\n"); any = true; } }
                if (!any) sb.append("  (none)\n");

                sb.append("\nColumns in ").append(tblB).append(" but NOT in ").append(tblA).append(":\n");
                any = false;
                for (String c : colsB) { if (!colsA.contains(c)) { sb.append("  + ").append(c).append("\n"); any = true; } }
                if (!any) sb.append("  (none)\n");

                sb.append("\nCommon columns (").append(colsA.stream().filter(colsB::contains).count()).append("):\n");
                for (String c : colsA) { if (colsB.contains(c)) sb.append("  = ").append(c).append("\n"); }

                String result = sb.toString();
                SwingUtilities.invokeLater(() -> schemaDiffArea.setText(result));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> schemaDiffArea.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    private void runDataDiff(JTabbedPane tabs) {
        String tblA = (String) tableADropdown.getSelectedItem();
        String tblB = (String) tableBDropdown.getSelectedItem();
        String key = (String) keyColDropdown.getSelectedItem();
        if (tblA == null || tblB == null || key == null) return;
        summaryLabel.setText("Running data diff...");
        new Thread(() -> {
            try {
                String quotedKey = "\"" + key + "\"";
                String sql = "SELECT '" + tblA + "' AS source, " + quotedKey + " FROM " + dbManager.quoteTableName(tblA) +
                        " WHERE " + quotedKey + " NOT IN (SELECT " + quotedKey + " FROM " + dbManager.quoteTableName(tblB) + ")" +
                        " UNION ALL " +
                        "SELECT '" + tblB + "' AS source, " + quotedKey + " FROM " + dbManager.quoteTableName(tblB) +
                        " WHERE " + quotedKey + " NOT IN (SELECT " + quotedKey + " FROM " + dbManager.quoteTableName(tblA) + ")" +
                        " LIMIT 500";
                lastQuery = sql;
                DefaultTableModel model = dbManager.executeQuery(sql);
                SwingUtilities.invokeLater(() -> {
                    dataTable.setModel(model);
                    dataTable.setAutoCreateRowSorter(true);
                    tabs.setSelectedIndex(1);
                    summaryLabel.setText("Data diff: " + model.getRowCount() + " differing rows (limit 500).");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> summaryLabel.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    private void runRowCounts() {
        String tblA = (String) tableADropdown.getSelectedItem();
        String tblB = (String) tableBDropdown.getSelectedItem();
        if (tblA == null || tblB == null) return;
        new Thread(() -> {
            try {
                ResultSet rsA = dbManager.connection.createStatement()
                        .executeQuery("SELECT COUNT(*) FROM " + dbManager.quoteTableName(tblA));
                long countA = rsA.next() ? rsA.getLong(1) : 0;
                ResultSet rsB = dbManager.connection.createStatement()
                        .executeQuery("SELECT COUNT(*) FROM " + dbManager.quoteTableName(tblB));
                long countB = rsB.next() ? rsB.getLong(1) : 0;
                String msg = tblA + ": " + countA + " rows   |   " + tblB + ": " + countB + " rows   |   Diff: " + Math.abs(countA - countB);
                SwingUtilities.invokeLater(() -> summaryLabel.setText(msg));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> summaryLabel.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }
}
