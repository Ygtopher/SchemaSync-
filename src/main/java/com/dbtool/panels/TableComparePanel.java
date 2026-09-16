package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ExportUtil;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.*;

public class TableComparePanel extends JPanel {

    private final DatabaseManager dbManager;
    private final RSyntaxTextArea queryAArea = new RSyntaxTextArea(6, 40);
    private final RSyntaxTextArea queryBArea = new RSyntaxTextArea(6, 40);
    private final JComboBox<String> keyColDropdown = new JComboBox<>();
    
    private final JTable dataTableA = new JTable();
    private final JTable dataTableB = new JTable();
    private final JTable diffTable = new JTable();
    
    private final JLabel summaryLabel = new JLabel("Enter two queries and execute to begin comparison.");
    
    private DefaultTableModel modelA;
    private DefaultTableModel modelB;

    public TableComparePanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // Top controls: Split pane for two queries
        queryAArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        queryAArea.setCodeFoldingEnabled(true);
        queryAArea.setText("SELECT * FROM table_a LIMIT 100");
        
        queryBArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        queryBArea.setCodeFoldingEnabled(true);
        queryBArea.setText("SELECT * FROM table_b LIMIT 100");

        JPanel topPanel = new JPanel(new BorderLayout());
        
        JSplitPane splitQueries = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapQueryArea("Query A", queryAArea),
                wrapQueryArea("Query B", queryBArea));
        splitQueries.setResizeWeight(0.5);
        topPanel.add(splitQueries, BorderLayout.CENTER);

        // Toolbars
        JToolBar tbRun = new JToolBar();
        tbRun.setFloatable(false);
        tbRun.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton runQueriesBtn = new JButton("▶ Execute Both Queries");
        runQueriesBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        runQueriesBtn.setForeground(new Color(0, 102, 51));
        runQueriesBtn.addActionListener(e -> runBothQueries());
        
        tbRun.add(runQueriesBtn);
        tbRun.add(new JLabel(" | Key Column: "));
        keyColDropdown.setPreferredSize(new Dimension(150, 26));
        tbRun.add(keyColDropdown);
        
        JButton compareBtn = new JButton("Compare Data");
        compareBtn.addActionListener(e -> performDataDiff());
        tbRun.add(compareBtn);
        
        JButton exportBtn = new JButton("📥 Export Diff CSV");
        exportBtn.addActionListener(e -> ExportUtil.exportCsv(diffTable, this));
        tbRun.add(new JLabel(" | "));
        tbRun.add(exportBtn);

        topPanel.add(tbRun, BorderLayout.SOUTH);

        // Results tabs
        JTabbedPane resultTabs = new JTabbedPane();
        
        JSplitPane splitResults = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(dataTableA),
                new JScrollPane(dataTableB));
        splitResults.setResizeWeight(0.5);
        
        resultTabs.addTab("Side-by-Side Results", splitResults);
        resultTabs.addTab("Data Diff", new JScrollPane(diffTable));

        add(topPanel, BorderLayout.NORTH);
        add(resultTabs, BorderLayout.CENTER);
        add(summaryLabel, BorderLayout.SOUTH);
    }

    private JPanel wrapQueryArea(String title, RSyntaxTextArea area) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new RTextScrollPane(area), BorderLayout.CENTER);
        return p;
    }

    public void refreshTables() {
        // No-op for query compare panel
    }

    private void runBothQueries() {
        String qA = queryAArea.getText().trim();
        String qB = queryBArea.getText().trim();
        if (qA.isEmpty() || qB.isEmpty()) return;

        summaryLabel.setText("Executing queries...");
        new Thread(() -> {
            try {
                modelA = dbManager.executeQuery(qA);
                modelB = dbManager.executeQuery(qB);
                
                SwingUtilities.invokeLater(() -> {
                    dataTableA.setModel(modelA);
                    dataTableA.setAutoCreateRowSorter(true);
        dataTableA.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                    dataTableB.setModel(modelB);
                    dataTableB.setAutoCreateRowSorter(true);
        dataTableB.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                    
                    updateKeyDropdown();
                    summaryLabel.setText("Queries executed. Rows: A=" + modelA.getRowCount() + ", B=" + modelB.getRowCount() + ". Select a Key Column to compare.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> summaryLabel.setText("Error executing queries: " + ex.getMessage()));
            }
        }).start();
    }
    
    private void updateKeyDropdown() {
        keyColDropdown.removeAllItems();
        if (modelA == null || modelB == null) return;
        
        List<String> colsA = new ArrayList<>();
        for (int i=0; i<modelA.getColumnCount(); i++) colsA.add(modelA.getColumnName(i));
        
        for (int i=0; i<modelB.getColumnCount(); i++) {
            String colB = modelB.getColumnName(i);
            if (colsA.contains(colB)) {
                keyColDropdown.addItem(colB);
            }
        }
    }

    private void performDataDiff() {
        if (modelA == null || modelB == null) return;
        String key = (String) keyColDropdown.getSelectedItem();
        if (key == null) {
            JOptionPane.showMessageDialog(this, "Please select a Key Column to compare rows.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        summaryLabel.setText("Computing diff...");
        
        new Thread(() -> {
            int keyIdxA = -1;
            int keyIdxB = -1;
            for (int i=0; i<modelA.getColumnCount(); i++) {
                if (modelA.getColumnName(i).equals(key)) { keyIdxA = i; break; }
            }
            for (int i=0; i<modelB.getColumnCount(); i++) {
                if (modelB.getColumnName(i).equals(key)) { keyIdxB = i; break; }
            }
            
            if (keyIdxA == -1 || keyIdxB == -1) return;
            
            Map<String, Vector<Object>> mapA = new HashMap<>();
            for (int r=0; r<modelA.getRowCount(); r++) {
                Object k = modelA.getValueAt(r, keyIdxA);
                if (k != null) {
                    Vector<Object> row = new Vector<>();
                    for (int c=0; c<modelA.getColumnCount(); c++) row.add(modelA.getValueAt(r, c));
                    mapA.put(k.toString(), row);
                }
            }
            
            Map<String, Vector<Object>> mapB = new HashMap<>();
            for (int r=0; r<modelB.getRowCount(); r++) {
                Object k = modelB.getValueAt(r, keyIdxB);
                if (k != null) {
                    Vector<Object> row = new Vector<>();
                    for (int c=0; c<modelB.getColumnCount(); c++) row.add(modelB.getValueAt(r, c));
                    mapB.put(k.toString(), row);
                }
            }
            
            // Diff columns: Key, Status, Column Name, Value A, Value B
            Vector<String> colNames = new Vector<>(Arrays.asList("Key", "Status", "Column", "Value A", "Value B"));
            Vector<Vector<Object>> data = new Vector<>();
            
            int diffCount = 0;
            
            Set<String> allKeys = new HashSet<>(mapA.keySet());
            allKeys.addAll(mapB.keySet());
            
            for (String k : allKeys) {
                if (!mapB.containsKey(k)) {
                    Vector<Object> diffRow = new Vector<>(Arrays.asList(k, "Only in A", "*", "(Row exists)", "(Missing)"));
                    data.add(diffRow);
                    diffCount++;
                } else if (!mapA.containsKey(k)) {
                    Vector<Object> diffRow = new Vector<>(Arrays.asList(k, "Only in B", "*", "(Missing)", "(Row exists)"));
                    data.add(diffRow);
                    diffCount++;
                } else {
                    // In both. Compare common columns.
                    Vector<Object> rowA = mapA.get(k);
                    Vector<Object> rowB = mapB.get(k);
                    
                    for (int cA = 0; cA < modelA.getColumnCount(); cA++) {
                        String colName = modelA.getColumnName(cA);
                        if (colName.equals(key)) continue;
                        
                        // Find matching column in B
                        int cB = -1;
                        for (int i=0; i<modelB.getColumnCount(); i++) {
                            if (modelB.getColumnName(i).equals(colName)) { cB = i; break; }
                        }
                        
                        if (cB != -1) {
                            Object valA = rowA.get(cA);
                            Object valB = rowB.get(cB);
                            
                            String sA = valA == null ? "NULL" : valA.toString();
                            String sB = valB == null ? "NULL" : valB.toString();
                            
                            if (!sA.equals(sB)) {
                                Vector<Object> diffRow = new Vector<>(Arrays.asList(k, "Modified", colName, sA, sB));
                                data.add(diffRow);
                                diffCount++;
                            }
                        }
                    }
                }
            }
            
            DefaultTableModel diffModel = new DefaultTableModel(data, colNames);
            int finalDiffCount = diffCount;
            
            SwingUtilities.invokeLater(() -> {
                diffTable.setModel(diffModel);
                diffTable.setAutoCreateRowSorter(true);
        diffTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                summaryLabel.setText("Diff complete. Found " + finalDiffCount + " differences.");
            });
            
        }).start();
    }
}
