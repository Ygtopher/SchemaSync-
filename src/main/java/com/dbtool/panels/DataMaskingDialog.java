package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.ColumnDefinition;
import com.dbtool.util.ThemeManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.*;
import java.util.List;

/**
 * Enterprise Sensitive Data Masking & Anonymization Studio.
 * Enables GDPR/HIPAA-compliant data sanitization for development, testing,
 * and staging environments with live side-by-side preview and multi-format export.
 *
 * Part of SchemaSync Tier 4 Enterprise Security & Governance Suite.
 */
public class DataMaskingDialog extends JDialog {

    public enum MaskingStrategy {
        KEEP_ORIGINAL("None (Keep Original)"),
        EMAIL_MASK("Email Mask (j***e@domain.com)"),
        PHONE_MASK("Phone Mask (***-***-1234)"),
        CARD_MASK("Card / Account (****-****-****-1234)"),
        REDACT("Full Redaction ([REDACTED])"),
        SYNTHETIC_NAME("Synthetic Realistic Name"),
        SYNTHETIC_EMAIL("Synthetic Fake Email"),
        NUMERIC_JITTER("Numeric Perturbation (±15%)"),
        SHA256_HASH("Deterministic SHA-256 Hash"),
        UUID_TOKEN("UUID Tokenize");

        public final String label;
        MaskingStrategy(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final DatabaseManager dbManager;
    private final String initialTable;

    private JComboBox<String> tableDropdown;
    private DefaultTableModel strategyModel;
    private JTable strategyTable;

    private DefaultTableModel originalModel;
    private JTable originalTable;

    private DefaultTableModel maskedModel;
    private JTable maskedTable;

    private JSpinner exportLimitSpinner;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    private final List<String[]> cachedSampleRows = new ArrayList<>();
    private final List<String> cachedColNames = new ArrayList<>();

    private static final String[] FAKE_FIRST_NAMES = {
            "Alexander", "Amara", "Benjamin", "Charlotte", "Daniel", "Elena", "Felix", "Grace",
            "Henry", "Isabella", "James", "Khadija", "Liam", "Maya", "Noah", "Olivia",
            "Patrick", "Quinn", "Ryan", "Sophia", "Thomas", "Uma", "Victor", "William", "Zoe"
    };

    private static final String[] FAKE_LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
            "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White"
    };

    public DataMaskingDialog(Window parent, DatabaseManager dbManager, String initialTable) {
        super(parent, "🔒 Sensitive Data Masking & Anonymization Studio", ModalityType.MODELESS);
        this.dbManager = dbManager;
        this.initialTable = initialTable;

        setSize(1050, 720);
        setLocationRelativeTo(parent);
        initUI();
        loadTables();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("🔒 Sensitive Data Masking & Anonymization Studio");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        JLabel subtitle = new JLabel("<html>Sanitize personally identifiable information (PII) before exporting or sharing datasets with dev/staging teams.</html>");
        subtitle.setForeground(ThemeManager.isDarkMode() ? new Color(170, 170, 170) : new Color(100, 100, 100));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // Center Split: Top = Config & Column Strategies; Bottom = Live Side-by-Side Preview
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setResizeWeight(0.40);

        // Top Panel: Table Selector + Strategy Grid
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        topBar.add(new JLabel("Target Table:"));
        tableDropdown = new JComboBox<>();
        tableDropdown.setPreferredSize(new Dimension(220, 26));
        tableDropdown.addActionListener(e -> onTableSelected());
        topBar.add(tableDropdown);

        JButton autoDetectBtn = new JButton("✨ Auto-Detect PII Fields");
        autoDetectBtn.setToolTipText("Automatically detect and configure email, phone, card, and name masking based on column names");
        autoDetectBtn.addActionListener(e -> autoDetectPII());
        topBar.add(autoDetectBtn);

        JButton resetBtn = new JButton("Reset All");
        resetBtn.addActionListener(e -> resetStrategies());
        topBar.add(resetBtn);

        JButton refreshPreviewBtn = new JButton("🔄 Refresh Preview");
        refreshPreviewBtn.addActionListener(e -> updateMaskedPreview());
        topBar.add(refreshPreviewBtn);

        topPanel.add(topBar, BorderLayout.NORTH);

        // Strategy Table
        String[] stratCols = {"Column Name", "Data Type", "Masking Strategy"};
        strategyModel = new DefaultTableModel(stratCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }
        };
        strategyTable = new JTable(strategyModel);
        strategyTable.setRowHeight(24);

        JComboBox<MaskingStrategy> strategyCombo = new JComboBox<>(MaskingStrategy.values());
        strategyCombo.addActionListener(e -> updateMaskedPreview());
        strategyTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(strategyCombo));
        strategyTable.getModel().addTableModelListener(e -> updateMaskedPreview());

        JScrollPane stratScroll = new JScrollPane(strategyTable);
        stratScroll.setBorder(BorderFactory.createTitledBorder("Column Masking Rules (Select Strategy for each column)"));
        topPanel.add(stratScroll, BorderLayout.CENTER);
        mainSplit.setTopComponent(topPanel);

        // Bottom Panel: Side-by-Side Live Preview
        JPanel bottomPanel = new JPanel(new GridLayout(1, 2, 8, 0));

        // Original Table
        originalModel = new DefaultTableModel();
        originalTable = new JTable(originalModel);
        originalTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        originalTable.setEnabled(false);
        JScrollPane origScroll = new JScrollPane(originalTable);
        origScroll.setBorder(BorderFactory.createTitledBorder("Original Sample Data (Live First 5 Rows)"));
        bottomPanel.add(origScroll);

        // Masked Table
        maskedModel = new DefaultTableModel();
        maskedTable = new JTable(maskedModel);
        maskedTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        maskedTable.setEnabled(false);
        JScrollPane maskedScroll = new JScrollPane(maskedTable);
        maskedScroll.setBorder(BorderFactory.createTitledBorder("Anonymized / Masked Data Preview"));
        bottomPanel.add(maskedScroll);

        mainSplit.setBottomComponent(bottomPanel);
        root.add(mainSplit, BorderLayout.CENTER);

        // Action Toolbar & Export
        JPanel footer = new JPanel(new BorderLayout(8, 8));

        JPanel exportControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        exportControls.add(new JLabel("Max Rows to Export:"));
        exportLimitSpinner = new JSpinner(new SpinnerNumberModel(5000, 10, 500000, 500));
        exportLimitSpinner.setPreferredSize(new Dimension(80, 26));
        exportControls.add(exportLimitSpinner);

        JButton exportCsvBtn = new JButton("📄 Export Masked CSV");
        exportCsvBtn.addActionListener(e -> exportMaskedData("CSV"));
        exportControls.add(exportCsvBtn);

        JButton exportXlsxBtn = new JButton("📥 Export Masked Excel");
        exportXlsxBtn.addActionListener(e -> exportMaskedData("EXCEL"));
        exportControls.add(exportXlsxBtn);

        JButton exportSqlBtn = new JButton("📥 Export Masked SQL Inserts");
        exportSqlBtn.addActionListener(e -> exportMaskedData("SQL"));
        exportControls.add(exportSqlBtn);

        JButton copySqlBtn = new JButton("📋 Copy Masked SQL");
        copySqlBtn.addActionListener(e -> copyMaskedSqlToClipboard());
        exportControls.add(copySqlBtn);

        footer.add(exportControls, BorderLayout.NORTH);

        JPanel statusPanel = new JPanel(new BorderLayout(6, 0));
        statusLabel = new JLabel("Ready.");
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(150, 18));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(progressBar, BorderLayout.EAST);
        footer.add(statusPanel, BorderLayout.SOUTH);

        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void loadTables() {
        new Thread(() -> {
            List<String> tables = dbManager.getTableNames();
            SwingUtilities.invokeLater(() -> {
                tableDropdown.removeAllItems();
                for (String t : tables) {
                    tableDropdown.addItem(t);
                }
                if (initialTable != null && tables.contains(initialTable)) {
                    tableDropdown.setSelectedItem(initialTable);
                } else if (!tables.isEmpty()) {
                    tableDropdown.setSelectedIndex(0);
                }
            });
        }).start();
    }

    private void onTableSelected() {
        String selectedTable = (String) tableDropdown.getSelectedItem();
        if (selectedTable == null) return;

        strategyModel.setRowCount(0);
        cachedSampleRows.clear();
        cachedColNames.clear();

        new Thread(() -> {
            List<ColumnDefinition> cols = dbManager.getDetailedColumns(selectedTable);
            List<String[]> rows = new ArrayList<>();
            List<String> names = new ArrayList<>();

            for (ColumnDefinition cd : cols) {
                names.add(cd.name);
            }

            // Fetch sample 5 rows
            try (Statement stmt = dbManager.connection.createStatement()) {
                stmt.setMaxRows(5);
                String sql = "SELECT * FROM " + selectedTable;
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int count = md.getColumnCount();
                    while (rs.next()) {
                        String[] r = new String[count];
                        for (int i = 1; i <= count; i++) {
                            r[i - 1] = rs.getString(i);
                        }
                        rows.add(r);
                    }
                }
            } catch (Exception ex) {
                // Table might be empty or query failed
            }

            SwingUtilities.invokeLater(() -> {
                cachedColNames.addAll(names);
                cachedSampleRows.addAll(rows);

                for (ColumnDefinition cd : cols) {
                    MaskingStrategy defaultStrategy = detectStrategy(cd.name);
                    strategyModel.addRow(new Object[]{cd.name, cd.typeName, defaultStrategy});
                }

                // Populate original model
                originalModel.setDataVector(
                        rows.toArray(new Object[0][]),
                        names.toArray(new Object[0])
                );

                updateMaskedPreview();
            });
        }).start();
    }

    private MaskingStrategy detectStrategy(String colName) {
        String lower = colName.toLowerCase();
        if (lower.contains("email") || lower.contains("mail")) return MaskingStrategy.EMAIL_MASK;
        if (lower.contains("phone") || lower.contains("tel") || lower.contains("mobile") || lower.contains("cell")) return MaskingStrategy.PHONE_MASK;
        if (lower.contains("card") || lower.contains("pan") || lower.contains("iban") || lower.contains("account_no")) return MaskingStrategy.CARD_MASK;
        if (lower.contains("password") || lower.contains("pwd") || lower.contains("secret") || lower.contains("ssn") || lower.contains("token")) return MaskingStrategy.REDACT;
        if (lower.contains("first_name") || lower.contains("last_name") || lower.contains("prenom") || lower.contains("nom") || lower.equals("name")) return MaskingStrategy.SYNTHETIC_NAME;
        if (lower.contains("salary") || lower.contains("balance") || lower.contains("solde") || lower.contains("amount")) return MaskingStrategy.NUMERIC_JITTER;
        return MaskingStrategy.KEEP_ORIGINAL;
    }

    private void autoDetectPII() {
        int rows = strategyModel.getRowCount();
        for (int i = 0; i < rows; i++) {
            String colName = (String) strategyModel.getValueAt(i, 0);
            MaskingStrategy strat = detectStrategy(colName);
            strategyModel.setValueAt(strat, i, 2);
        }
        updateMaskedPreview();
        JOptionPane.showMessageDialog(this, "Auto-detected PII masking rules applied to columns.", "PII Detection", JOptionPane.INFORMATION_MESSAGE);
    }

    private void resetStrategies() {
        int rows = strategyModel.getRowCount();
        for (int i = 0; i < rows; i++) {
            strategyModel.setValueAt(MaskingStrategy.KEEP_ORIGINAL, i, 2);
        }
        updateMaskedPreview();
    }

    private void updateMaskedPreview() {
        if (cachedSampleRows.isEmpty()) {
            maskedModel.setDataVector(new Object[0][], cachedColNames.toArray(new Object[0]));
            return;
        }

        Map<Integer, MaskingStrategy> stratMap = new HashMap<>();
        for (int i = 0; i < strategyModel.getRowCount(); i++) {
            MaskingStrategy s = (MaskingStrategy) strategyModel.getValueAt(i, 2);
            stratMap.put(i, s != null ? s : MaskingStrategy.KEEP_ORIGINAL);
        }

        Object[][] maskedRows = new Object[cachedSampleRows.size()][cachedColNames.size()];
        for (int r = 0; r < cachedSampleRows.size(); r++) {
            String[] orig = cachedSampleRows.get(r);
            for (int c = 0; c < orig.length; c++) {
                MaskingStrategy strat = stratMap.getOrDefault(c, MaskingStrategy.KEEP_ORIGINAL);
                maskedRows[r][c] = applyMask(orig[c], strat, r * 31 + c);
            }
        }

        maskedModel.setDataVector(maskedRows, cachedColNames.toArray(new Object[0]));
    }

    public static String applyMask(String value, MaskingStrategy strategy, int seed) {
        if (value == null) return null;
        if (strategy == MaskingStrategy.KEEP_ORIGINAL) return value;

        switch (strategy) {
            case EMAIL_MASK:
                if (value.contains("@")) {
                    String[] p = value.split("@", 2);
                    String name = p[0];
                    String domain = p[1];
                    String maskedName = name.length() <= 2 ? "**" : name.charAt(0) + "***" + name.charAt(name.length() - 1);
                    return maskedName + "@" + (domain.length() <= 4 ? domain : domain.charAt(0) + "***." + domain.substring(domain.lastIndexOf('.') + 1));
                }
                return "user***@example.com";

            case PHONE_MASK:
                String digits = value.replaceAll("\\D", "");
                if (digits.length() >= 4) {
                    String last4 = digits.substring(digits.length() - 4);
                    return "***-***-" + last4;
                }
                return "***-***-0000";

            case CARD_MASK:
                String cardDigits = value.replaceAll("\\D", "");
                if (cardDigits.length() >= 4) {
                    return "****-****-****-" + cardDigits.substring(cardDigits.length() - 4);
                }
                return "****-****-****-1234";

            case REDACT:
                return "[REDACTED]";

            case SYNTHETIC_NAME:
                int fnIdx = Math.abs(seed) % FAKE_FIRST_NAMES.length;
                int lnIdx = Math.abs(seed * 7 + 3) % FAKE_LAST_NAMES.length;
                return FAKE_FIRST_NAMES[fnIdx] + " " + FAKE_LAST_NAMES[lnIdx];

            case SYNTHETIC_EMAIL:
                int fn = Math.abs(seed) % FAKE_FIRST_NAMES.length;
                int ln = Math.abs(seed * 7 + 3) % FAKE_LAST_NAMES.length;
                return FAKE_FIRST_NAMES[fn].toLowerCase() + "." + FAKE_LAST_NAMES[ln].toLowerCase() + (seed % 99 + 1) + "@testmail.org";

            case NUMERIC_JITTER:
                try {
                    double num = Double.parseDouble(value.replace(",", "."));
                    Random rng = new Random(seed);
                    double jitterPercent = 0.85 + (rng.nextDouble() * 0.30); // 85% to 115%
                    double result = Math.round(num * jitterPercent * 100.0) / 100.0;
                    return String.valueOf(result);
                } catch (Exception ex) {
                    return value;
                }

            case SHA256_HASH:
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < 8; i++) { // First 16 hex chars
                        hex.append(String.format("%02x", digest[i]));
                    }
                    return "hash_" + hex.toString();
                } catch (Exception ex) {
                    return "hash_" + Math.abs(value.hashCode());
                }

            case UUID_TOKEN:
                return UUID.nameUUIDFromBytes((value + seed).getBytes(StandardCharsets.UTF_8)).toString();

            default:
                return value;
        }
    }

    private void exportMaskedData(String format) {
        String selectedTable = (String) tableDropdown.getSelectedItem();
        if (selectedTable == null) return;

        int maxRows = (int) exportLimitSpinner.getValue();
        JFileChooser chooser = new JFileChooser();
        String ext = "EXCEL".equals(format) ? ".xlsx" : ("SQL".equals(format) ? ".sql" : ".csv");
        chooser.setSelectedFile(new File(selectedTable + "_anonymized" + ext));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File targetFile = chooser.getSelectedFile();

        // Capture strategies
        Map<Integer, MaskingStrategy> stratMap = new HashMap<>();
        for (int i = 0; i < strategyModel.getRowCount(); i++) {
            MaskingStrategy s = (MaskingStrategy) strategyModel.getValueAt(i, 2);
            stratMap.put(i, s != null ? s : MaskingStrategy.KEEP_ORIGINAL);
        }

        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        statusLabel.setText("Exporting masked data to " + format + "...");

        new Thread(() -> {
            int exported = 0;
            try {
                if ("CSV".equals(format)) {
                    exported = exportCsv(selectedTable, targetFile, stratMap, maxRows);
                } else if ("EXCEL".equals(format)) {
                    exported = exportExcel(selectedTable, targetFile, stratMap, maxRows);
                } else if ("SQL".equals(format)) {
                    exported = exportSql(selectedTable, targetFile, stratMap, maxRows);
                }

                final int finalCount = exported;
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Successfully exported " + finalCount + " masked records.");
                    JOptionPane.showMessageDialog(this, "Exported " + finalCount + " anonymized rows to:\n" + targetFile.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Export failed.");
                    JOptionPane.showMessageDialog(this, "Failed to export: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private int exportCsv(String table, File target, Map<Integer, MaskingStrategy> stratMap, int maxRows) throws Exception {
        int count = 0;
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(target), true, StandardCharsets.UTF_8);
             Statement stmt = dbManager.connection.createStatement()) {
            stmt.setMaxRows(maxRows);
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + table)) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();

                // Header
                StringBuilder head = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) head.append(",");
                    head.append("\"").append(md.getColumnName(i).replace("\"", "\"\"")).append("\"");
                }
                writer.println(head);

                while (rs.next()) {
                    StringBuilder line = new StringBuilder();
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) line.append(",");
                        String val = rs.getString(i);
                        MaskingStrategy strat = stratMap.getOrDefault(i - 1, MaskingStrategy.KEEP_ORIGINAL);
                        String masked = applyMask(val, strat, count * 31 + i);
                        if (masked != null) {
                            line.append("\"").append(masked.replace("\"", "\"\"")).append("\"");
                        } else {
                            line.append("");
                        }
                    }
                    writer.println(line);
                    count++;
                }
            }
        }
        return count;
    }

    private int exportExcel(String table, File target, Map<Integer, MaskingStrategy> stratMap, int maxRows) throws Exception {
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             Statement stmt = dbManager.connection.createStatement()) {
            stmt.setMaxRows(maxRows);
            Sheet sheet = workbook.createSheet(table.length() > 30 ? table.substring(0, 30) : table);

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + table)) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();

                // Header
                Row headerRow = sheet.createRow(0);
                CellStyle headerStyle = workbook.createCellStyle();
                org.apache.poi.ss.usermodel.Font font = workbook.createFont();
                font.setBold(true);
                headerStyle.setFont(font);

                for (int i = 1; i <= cols; i++) {
                    Cell cell = headerRow.createCell(i - 1);
                    cell.setCellValue(md.getColumnName(i));
                    cell.setCellStyle(headerStyle);
                }

                while (rs.next()) {
                    count++;
                    Row row = sheet.createRow(count);
                    for (int i = 1; i <= cols; i++) {
                        String val = rs.getString(i);
                        MaskingStrategy strat = stratMap.getOrDefault(i - 1, MaskingStrategy.KEEP_ORIGINAL);
                        String masked = applyMask(val, strat, count * 31 + i);
                        if (masked != null) {
                            row.createCell(i - 1).setCellValue(masked);
                        }
                    }
                }
            }

            try (FileOutputStream out = new FileOutputStream(target)) {
                workbook.write(out);
            }
        }
        return count;
    }

    private int exportSql(String table, File target, Map<Integer, MaskingStrategy> stratMap, int maxRows) throws Exception {
        int count = 0;
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(target), true, StandardCharsets.UTF_8);
             Statement stmt = dbManager.connection.createStatement()) {
            stmt.setMaxRows(maxRows);
            writer.println("-- SchemaSync Anonymized Dataset Export: " + table);
            writer.println("-- Generated: " + new Date());
            writer.println("BEGIN;\n");

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + table)) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();

                StringBuilder colNames = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) colNames.append(", ");
                    colNames.append(md.getColumnName(i));
                }

                while (rs.next()) {
                    StringBuilder vals = new StringBuilder();
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) vals.append(", ");
                        String val = rs.getString(i);
                        MaskingStrategy strat = stratMap.getOrDefault(i - 1, MaskingStrategy.KEEP_ORIGINAL);
                        String masked = applyMask(val, strat, count * 31 + i);
                        if (masked == null) {
                            vals.append("NULL");
                        } else {
                            vals.append("'").append(masked.replace("'", "''")).append("'");
                        }
                    }
                    writer.println("INSERT INTO " + table + " (" + colNames + ") VALUES (" + vals + ");");
                    count++;
                }
            }
            writer.println("\nCOMMIT;");
        }
        return count;
    }

    private void copyMaskedSqlToClipboard() {
        String selectedTable = (String) tableDropdown.getSelectedItem();
        if (selectedTable == null || cachedSampleRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No data available to copy.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Map<Integer, MaskingStrategy> stratMap = new HashMap<>();
        for (int i = 0; i < strategyModel.getRowCount(); i++) {
            MaskingStrategy s = (MaskingStrategy) strategyModel.getValueAt(i, 2);
            stratMap.put(i, s != null ? s : MaskingStrategy.KEEP_ORIGINAL);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-- Sample Masked Inserts for ").append(selectedTable).append("\n");
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < cachedColNames.size(); i++) {
            if (i > 0) cols.append(", ");
            cols.append(cachedColNames.get(i));
        }

        for (int r = 0; r < cachedSampleRows.size(); r++) {
            String[] row = cachedSampleRows.get(r);
            StringBuilder vals = new StringBuilder();
            for (int c = 0; c < row.length; c++) {
                if (c > 0) vals.append(", ");
                String m = applyMask(row[c], stratMap.getOrDefault(c, MaskingStrategy.KEEP_ORIGINAL), r * 31 + c);
                if (m == null) vals.append("NULL");
                else vals.append("'").append(m.replace("'", "''")).append("'");
            }
            sb.append("INSERT INTO ").append(selectedTable).append(" (").append(cols).append(") VALUES (").append(vals).append(");\n");
        }

        java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        JOptionPane.showMessageDialog(this, "Sample masked SQL statements copied to clipboard!", "Clipboard", JOptionPane.INFORMATION_MESSAGE);
    }
}
