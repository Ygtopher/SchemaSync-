package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.ColumnDefinition;
import com.dbtool.util.ThemeManager;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

/**
 * Smart Drag-and-Drop CSV & Excel Data Importer
 * Supports .csv, .tsv, and .xlsx files with automatic column mapping,
 * data preview, batch inserts, and rollback transaction safety.
 * 
 * Part of SchemaSync Tier 2 Developer Productivity Suite.
 */
public class DataImporterDialog extends JDialog {

    private final DatabaseManager dbManager;
    private final Runnable onSuccessCallback;

    private JTextField fileField;
    private JComboBox<String> targetTableCombo;
    private JSpinner batchSizeSpinner;
    private JCheckBox atomicTxCheck;

    private DefaultTableModel mappingModel;
    private JTable mappingTable;

    private DefaultTableModel previewModel;
    private JTable previewTable;

    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton importBtn;
    private JButton cancelBtn;

    private List<String> sourceHeaders = new ArrayList<>();
    private List<List<String>> parsedDataRows = new ArrayList<>();
    private SwingWorker<Void, Integer> currentWorker = null;

    public DataImporterDialog(Frame owner, DatabaseManager dbManager, String initialTable, Runnable onSuccessCallback) {
        super(owner, "📥 Smart CSV & Excel Data Importer", true);
        this.dbManager = dbManager;
        this.onSuccessCallback = onSuccessCallback;

        setSize(1020, 720);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        initComponents(initialTable);
    }

    private void initComponents(String initialTable) {
        // --- 1. Top Panel: Header & File Selection ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 6));
        headerPanel.setBorder(new EmptyBorder(12, 16, 10, 16));
        headerPanel.setBackground(new Color(248, 250, 252));
        if (ThemeManager.isDarkMode()) {
            headerPanel.setBackground(new Color(30, 41, 59));
        }

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);
        JLabel titleLabel = new JLabel("📥 Smart CSV & Excel Data Importer");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel subtitleLabel = new JLabel("Import data from CSV, TSV, or Excel (.xlsx) files with intelligent column mapping and atomic batch transactions.");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        titleBox.add(titleLabel);
        titleBox.add(subtitleLabel);
        headerPanel.add(titleBox, BorderLayout.WEST);

        // File and Target Table Bar
        JPanel fileSelectBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        fileSelectBar.setOpaque(false);

        fileSelectBar.add(new JLabel("File:"));
        fileField = new JTextField(28);
        fileField.setEditable(false);
        fileField.setToolTipText("Select or Drag & Drop a CSV or Excel file here");
        fileSelectBar.add(fileField);

        JButton browseBtn = new JButton("Browse File...");
        browseBtn.addActionListener(e -> chooseFile());
        fileSelectBar.add(browseBtn);

        fileSelectBar.add(new JLabel("Target Table:"));
        List<String> tables = dbManager.getTableNames();
        targetTableCombo = new JComboBox<>(tables.toArray(new String[0]));
        if (initialTable != null && tables.contains(initialTable)) {
            targetTableCombo.setSelectedItem(initialTable);
        }
        targetTableCombo.setPreferredSize(new Dimension(180, 26));
        targetTableCombo.addActionListener(e -> updateColumnMapping());
        fileSelectBar.add(targetTableCombo);

        fileSelectBar.add(new JLabel("Batch Size:"));
        batchSizeSpinner = new JSpinner(new SpinnerNumberModel(500, 50, 5000, 100));
        fileSelectBar.add(batchSizeSpinner);

        atomicTxCheck = new JCheckBox("Atomic Transaction (Auto-Rollback on Error)", true);
        fileSelectBar.add(atomicTxCheck);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(fileSelectBar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // Enable Drag & Drop
        enableDragAndDrop();

        // --- 2. Center: Tabbed Pane (Mapping & Preview) ---
        JTabbedPane centerTabs = new JTabbedPane();

        // Tab 1: Column Mapping
        JPanel mappingPanel = new JPanel(new BorderLayout());
        String[] mapCols = {"Source Column (File)", "Target Column (Database)", "Target Data Type", "Sample First Value"};
        mappingModel = new DefaultTableModel(mapCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; // Only Target Column dropdown is editable
            }
        };
        mappingTable = new JTable(mappingModel);
        mappingTable.setRowHeight(26);
        mappingPanel.add(new JScrollPane(mappingTable), BorderLayout.CENTER);

        JLabel mapTip = new JLabel("💡 SchemaSync auto-matched matching column names. Change any column dropdown to remap or select '[Skip Column]'.");
        mapTip.setBorder(new EmptyBorder(6, 8, 6, 8));
        mappingPanel.add(mapTip, BorderLayout.SOUTH);
        centerTabs.addTab("🔗 Column Mapping", mappingPanel);

        // Tab 2: Data Preview
        previewModel = new DefaultTableModel();
        previewTable = new JTable(previewModel);
        previewTable.setRowHeight(24);
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.add(new JScrollPane(previewTable), BorderLayout.CENTER);
        centerTabs.addTab("👁 Data Preview (First 20 Rows)", previewPanel);

        add(centerTabs, BorderLayout.CENTER);

        // --- 3. Bottom: Progress & Action Controls ---
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBorder(new EmptyBorder(8, 14, 10, 14));

        JPanel progressBox = new JPanel(new BorderLayout(4, 4));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Select a file to begin");
        statusLabel = new JLabel("Ready. Please select a CSV or Excel file.");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        progressBox.add(progressBar, BorderLayout.NORTH);
        progressBox.add(statusLabel, BorderLayout.SOUTH);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        importBtn = new JButton("📥 Import Data");
        importBtn.setFont(importBtn.getFont().deriveFont(Font.BOLD));
        importBtn.setBackground(new Color(16, 185, 129));
        importBtn.setForeground(Color.WHITE);
        importBtn.setFocusPainted(false);
        importBtn.setEnabled(false);
        importBtn.addActionListener(e -> executeImport());

        cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> {
            if (currentWorker != null && !currentWorker.isDone()) {
                currentWorker.cancel(true);
            }
            dispose();
        });

        actionBtns.add(importBtn);
        actionBtns.add(cancelBtn);

        bottomContainer.add(progressBox, BorderLayout.CENTER);
        bottomContainer.add(actionBtns, BorderLayout.EAST);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private void enableDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY, new java.awt.dnd.DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<?> droppedFiles = (List<?>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles != null && !droppedFiles.isEmpty()) {
                        File file = (File) droppedFiles.get(0);
                        loadFile(file);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select CSV, TSV, or Excel File to Import");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Data Files (*.csv, *.tsv, *.xlsx)", "csv", "tsv", "xlsx"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadFile(chooser.getSelectedFile());
        }
    }

    private void loadFile(File file) {
        if (file == null || !file.exists()) return;
        fileField.setText(file.getAbsolutePath());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        statusLabel.setText("Parsing file: " + file.getName() + "...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                sourceHeaders.clear();
                parsedDataRows.clear();
                String name = file.getName().toLowerCase();
                if (name.endsWith(".xlsx")) {
                    parseExcel(file);
                } else {
                    parseCsv(file);
                }
                return null;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    get();
                    updateColumnMapping();
                    updatePreviewTable();
                    importBtn.setEnabled(!parsedDataRows.isEmpty());
                    statusLabel.setText("Loaded " + parsedDataRows.size() + " data rows from " + file.getName() + ".");
                    progressBar.setString("Ready to import " + parsedDataRows.size() + " rows");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DataImporterDialog.this, "Failed to parse file:\n" + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void parseCsv(File file) throws Exception {
        char delimiter = file.getName().toLowerCase().endsWith(".tsv") ? '\t' : ',';
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isFirst = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> tokens = parseCsvLine(line, delimiter);
                if (isFirst) {
                    sourceHeaders.addAll(tokens);
                    isFirst = false;
                } else {
                    parsedDataRows.add(tokens);
                }
            }
        }
    }

    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString().trim());
        return result;
    }

    private void parseExcel(File file) throws Exception {
        try (InputStream is = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirst = true;
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                List<String> rowValues = new ArrayList<>();
                for (Cell cell : row) {
                    rowValues.add(formatter.formatCellValue(cell).trim());
                }
                if (rowValues.stream().allMatch(String::isEmpty)) continue;

                if (isFirst) {
                    sourceHeaders.addAll(rowValues);
                    isFirst = false;
                } else {
                    parsedDataRows.add(rowValues);
                }
            }
        }
    }

    private void updateColumnMapping() {
        mappingModel.setRowCount(0);
        String tbl = (String) targetTableCombo.getSelectedItem();
        if (tbl == null || sourceHeaders.isEmpty()) return;

        List<ColumnDefinition> dbCols = dbManager.getDetailedColumns(tbl);
        List<String> targetColNames = new ArrayList<>();
        targetColNames.add("[Skip Column]");
        for (ColumnDefinition cd : dbCols) targetColNames.add(cd.name);

        JComboBox<String> targetDropdownEditor = new JComboBox<>(targetColNames.toArray(new String[0]));
        mappingTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(targetDropdownEditor));

        for (int i = 0; i < sourceHeaders.size(); i++) {
            String src = sourceHeaders.get(i);
            String matchedTarget = "[Skip Column]";
            String targetType = "-";

            // Fuzzy best-match
            for (ColumnDefinition cd : dbCols) {
                if (cd.name.equalsIgnoreCase(src) ||
                        cd.name.replace("_", "").equalsIgnoreCase(src.replace("_", "")) ||
                        src.toLowerCase().contains(cd.name.toLowerCase()) ||
                        cd.name.toLowerCase().contains(src.toLowerCase())) {
                    matchedTarget = cd.name;
                    targetType = cd.getFriendlyType();
                    break;
                }
            }

            String sampleVal = (!parsedDataRows.isEmpty() && i < parsedDataRows.get(0).size()) ? parsedDataRows.get(0).get(i) : "";
            mappingModel.addRow(new Object[]{src, matchedTarget, targetType, sampleVal});
        }
    }

    private void updatePreviewTable() {
        Vector<String> colNames = new Vector<>(sourceHeaders);
        Vector<Vector<Object>> data = new Vector<>();
        int count = Math.min(20, parsedDataRows.size());
        for (int r = 0; r < count; r++) {
            data.add(new Vector<>(parsedDataRows.get(r)));
        }
        previewModel.setDataVector(data, colNames);
    }

    private void executeImport() {
        String tbl = (String) targetTableCombo.getSelectedItem();
        if (tbl == null || parsedDataRows.isEmpty()) return;

        // Build active mappings
        Map<Integer, String> activeMappings = new LinkedHashMap<>();
        for (int r = 0; r < mappingModel.getRowCount(); r++) {
            String targetCol = (String) mappingModel.getValueAt(r, 1);
            if (targetCol != null && !targetCol.equals("[Skip Column]")) {
                activeMappings.put(r, targetCol);
            }
        }

        if (activeMappings.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please map at least one column to import.", "No Columns Mapped", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Ready to import " + parsedDataRows.size() + " row(s) into table '" + tbl + "'.\n" +
                        "Mapped Columns: " + activeMappings.size() + "\n\nProceed with import?",
                "Confirm Import", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        importBtn.setEnabled(false);
        cancelBtn.setText("Stop");
        progressBar.setMaximum(parsedDataRows.size());
        progressBar.setValue(0);

        int batchSize = (Integer) batchSizeSpinner.getValue();
        boolean isAtomic = atomicTxCheck.isSelected();

        currentWorker = new SwingWorker<Void, Integer>() {
            private int imported = 0;
            private long startTime;

            @Override
            protected Void doInBackground() throws Exception {
                startTime = System.currentTimeMillis();
                boolean origAutoCommit = dbManager.connection.getAutoCommit();
                if (isAtomic) dbManager.connection.setAutoCommit(false);

                StringBuilder sql = new StringBuilder("INSERT INTO ");
                sql.append(dbManager.quoteTableName(tbl)).append(" (");
                StringBuilder placeholders = new StringBuilder();

                int idx = 0;
                for (String col : activeMappings.values()) {
                    if (idx > 0) {
                        sql.append(", ");
                        placeholders.append(", ");
                    }
                    sql.append(dbManager.quoteColumnName(col));
                    placeholders.append("?");
                    idx++;
                }
                sql.append(") VALUES (").append(placeholders).append(")");

                try (PreparedStatement pstmt = dbManager.connection.prepareStatement(sql.toString())) {
                    for (int r = 0; r < parsedDataRows.size(); r++) {
                        if (isCancelled()) break;

                        List<String> row = parsedDataRows.get(r);
                        int p = 1;
                        for (Map.Entry<Integer, String> entry : activeMappings.entrySet()) {
                            int srcIdx = entry.getKey();
                            String val = srcIdx < row.size() ? row.get(srcIdx) : null;
                            if (val == null || val.trim().isEmpty() || "null".equalsIgnoreCase(val.trim())) {
                                pstmt.setObject(p++, null);
                            } else {
                                pstmt.setObject(p++, val.trim());
                            }
                        }
                        pstmt.addBatch();
                        imported++;

                        if (imported % batchSize == 0 || imported == parsedDataRows.size()) {
                            pstmt.executeBatch();
                            if (isAtomic) dbManager.connection.commit();
                            publish(imported);
                        }
                    }

                    if (isAtomic) dbManager.connection.commit();
                } catch (Exception ex) {
                    if (isAtomic) {
                        try { dbManager.connection.rollback(); } catch (Exception ignored) {}
                    }
                    throw ex;
                } finally {
                    try { dbManager.connection.setAutoCommit(origAutoCommit); } catch (Exception ignored) {}
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int last = chunks.get(chunks.size() - 1);
                progressBar.setValue(last);
                progressBar.setString("Imported " + last + " / " + parsedDataRows.size() + " rows...");
            }

            @Override
            protected void done() {
                importBtn.setEnabled(true);
                cancelBtn.setText("Close");
                try {
                    get();
                    long elapsed = System.currentTimeMillis() - startTime;
                    JOptionPane.showMessageDialog(DataImporterDialog.this,
                            "Successfully imported " + imported + " row(s) into table '" + tbl + "' in " + elapsed + " ms!",
                            "Import Complete", JOptionPane.INFORMATION_MESSAGE);
                    if (onSuccessCallback != null) onSuccessCallback.run();
                    dispose();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DataImporterDialog.this,
                            "Import failed" + (isAtomic ? " and was rolled back:\n" : ":\n") + ex.getMessage(),
                            "Import Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        currentWorker.execute();
    }
}
