package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.ColumnDefinition;
import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Synthetic Realistic Mock Data Generator
 * Generates realistic test data respecting data types, primary keys,
 * and foreign key relationships for load testing and development.
 * 
 * Part of SchemaSync Tier 2 Developer Productivity Suite.
 */
public class MockDataGeneratorDialog extends JDialog {

    public enum MockStrategy {
        AUTO_INCREMENT("Auto-Increment / Sequential"),
        FULL_NAME("Full Name"),
        FIRST_NAME("First Name"),
        LAST_NAME("Last Name"),
        EMAIL("Email Address"),
        PHONE("Phone Number"),
        COMPANY("Company / Org Name"),
        CITY("City / Country"),
        CURRENCY("Currency / Balance (10 - 5000)"),
        INTEGER_RANGE("Random Integer (1 - 1000)"),
        BOOLEAN("Boolean (true/false)"),
        DATE_PAST("Date (Past 1-2 Years)"),
        TIMESTAMP_NOW("Current Timestamp"),
        UUID_V4("UUID (v4)"),
        STATUS("Status (ACTIVE, PENDING, etc.)"),
        FOREIGN_KEY_SAMPLE("Foreign Key (Sample Parent Table)"),
        NULL_VALUE("Set as NULL");

        public final String label;
        MockStrategy(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final DatabaseManager dbManager;
    private final Runnable onSuccessCallback;

    private JComboBox<String> tableCombo;
    private JSpinner rowCountSpinner;
    private JCheckBox atomicCheck;

    private DefaultTableModel strategyModel;
    private JTable strategyTable;

    private DefaultTableModel previewModel;
    private JTable previewTable;

    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton generateBtn;
    private JButton insertBtn;

    private List<ColumnDefinition> currentColumns = new ArrayList<>();
    private final Map<String, List<Object>> foreignKeySampleCache = new HashMap<>();

    public MockDataGeneratorDialog(Frame owner, DatabaseManager dbManager, String initialTable, Runnable onSuccessCallback) {
        super(owner, "🧪 Synthetic Realistic Mock Data Generator", true);
        this.dbManager = dbManager;
        this.onSuccessCallback = onSuccessCallback;

        setSize(1020, 720);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        initComponents(initialTable);
    }

    private void initComponents(String initialTable) {
        // --- 1. Top Panel: Header & Controls ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 6));
        headerPanel.setBorder(new EmptyBorder(12, 16, 10, 16));
        headerPanel.setBackground(new Color(248, 250, 252));
        if (ThemeManager.isDarkMode()) {
            headerPanel.setBackground(new Color(30, 41, 59));
        }

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);
        JLabel titleLabel = new JLabel("🧪 Synthetic Realistic Mock Data Generator");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel subtitleLabel = new JLabel("Populate tables with realistic mock data while strictly maintaining relational foreign key constraints.");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        titleBox.add(titleLabel);
        titleBox.add(subtitleLabel);
        headerPanel.add(titleBox, BorderLayout.WEST);

        JPanel controlsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        controlsBar.setOpaque(false);

        controlsBar.add(new JLabel("Table:"));
        List<String> tables = dbManager.getTableNames();
        tableCombo = new JComboBox<>(tables.toArray(new String[0]));
        if (initialTable != null && tables.contains(initialTable)) {
            tableCombo.setSelectedItem(initialTable);
        }
        tableCombo.setPreferredSize(new Dimension(180, 26));
        tableCombo.addActionListener(e -> reloadColumns());
        controlsBar.add(tableCombo);

        controlsBar.add(new JLabel("Rows to Generate:"));
        rowCountSpinner = new JSpinner(new SpinnerNumberModel(100, 5, 10000, 50));
        controlsBar.add(rowCountSpinner);

        atomicCheck = new JCheckBox("Atomic Transaction (Auto-Rollback on Error)", true);
        controlsBar.add(atomicCheck);

        generateBtn = new JButton("🎲 Preview Sample Data");
        generateBtn.addActionListener(e -> generatePreviewData());
        controlsBar.add(generateBtn);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(headerPanel, BorderLayout.NORTH);
        northContainer.add(controlsBar, BorderLayout.SOUTH);
        add(northContainer, BorderLayout.NORTH);

        // --- 2. Center Tabs: Column Strategies & Live Preview ---
        JTabbedPane tabs = new JTabbedPane();

        // Strategy Tab
        JPanel strategyPanel = new JPanel(new BorderLayout());
        String[] cols = {"Column Name", "Database Type", "Generation Strategy", "Sample Value"};
        strategyModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }
        };
        strategyTable = new JTable(strategyModel);
        strategyTable.setRowHeight(26);

        JComboBox<MockStrategy> strategyEditor = new JComboBox<>(MockStrategy.values());
        strategyTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(strategyEditor));
        strategyTable.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 2) refreshSampleValues();
        });

        strategyPanel.add(new JScrollPane(strategyTable), BorderLayout.CENTER);
        JLabel stratTip = new JLabel("💡 Foreign Key columns automatically sample existing records from referenced parent tables to prevent constraint violations.");
        stratTip.setBorder(new EmptyBorder(6, 8, 6, 8));
        strategyPanel.add(stratTip, BorderLayout.SOUTH);
        tabs.addTab("⚙️ Column Strategies", strategyPanel);

        // Preview Tab
        previewModel = new DefaultTableModel();
        previewTable = new JTable(previewModel);
        previewTable.setRowHeight(24);
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.add(new JScrollPane(previewTable), BorderLayout.CENTER);
        tabs.addTab("👁 Live Generated Preview (10 Rows)", previewPanel);

        add(tabs, BorderLayout.CENTER);

        // --- 3. Bottom: Progress & Insert Actions ---
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBorder(new EmptyBorder(8, 14, 10, 14));

        JPanel progressBox = new JPanel(new BorderLayout(4, 4));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        statusLabel = new JLabel("Configure strategies and click 'Generate & Insert Data'.");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        progressBox.add(progressBar, BorderLayout.NORTH);
        progressBox.add(statusLabel, BorderLayout.SOUTH);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        insertBtn = new JButton("⚡ Generate & Insert Data");
        insertBtn.setFont(insertBtn.getFont().deriveFont(Font.BOLD));
        insertBtn.setBackground(new Color(16, 185, 129));
        insertBtn.setForeground(Color.WHITE);
        insertBtn.setFocusPainted(false);
        insertBtn.addActionListener(e -> executeBatchInsert());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        buttonBar.add(insertBtn);
        buttonBar.add(closeBtn);

        bottomContainer.add(progressBox, BorderLayout.CENTER);
        bottomContainer.add(buttonBar, BorderLayout.EAST);
        add(bottomContainer, BorderLayout.SOUTH);

        reloadColumns();
    }

    private void reloadColumns() {
        String tbl = (String) tableCombo.getSelectedItem();
        if (tbl == null) return;

        currentColumns = dbManager.getDetailedColumns(tbl);
        strategyModel.setRowCount(0);
        foreignKeySampleCache.clear();

        for (ColumnDefinition cd : currentColumns) {
            MockStrategy strategy = autoDetectStrategy(cd);
            strategyModel.addRow(new Object[]{cd.name, cd.getFriendlyType(), strategy, ""});
        }
        refreshSampleValues();
        generatePreviewData();
    }

    private MockStrategy autoDetectStrategy(ColumnDefinition cd) {
        String n = cd.name.toLowerCase();
        String t = cd.typeName != null ? cd.typeName.toLowerCase() : "";

        if (cd.foreignKeyRef != null) {
            return MockStrategy.FOREIGN_KEY_SAMPLE;
        }
        if (cd.isPrimaryKey && (cd.isAutoIncrement || n.equals("id") || n.endsWith("_id"))) {
            return MockStrategy.AUTO_INCREMENT;
        }
        if (n.contains("email") || n.contains("mail")) return MockStrategy.EMAIL;
        if (n.contains("phone") || n.contains("mobile") || n.contains("tel")) return MockStrategy.PHONE;
        if (n.contains("first_name") || n.contains("firstname") || n.contains("prenom")) return MockStrategy.FIRST_NAME;
        if (n.contains("last_name") || n.contains("lastname") || n.contains("nom")) return MockStrategy.LAST_NAME;
        if (n.contains("name") || n.contains("full_name") || n.contains("titulaire")) return MockStrategy.FULL_NAME;
        if (n.contains("company") || n.contains("org") || n.contains("agency") || n.contains("agence") || n.contains("ag")) {
            return cd.foreignKeyRef != null ? MockStrategy.FOREIGN_KEY_SAMPLE : MockStrategy.COMPANY;
        }
        if (n.contains("city") || n.contains("country") || n.contains("ville") || n.contains("address")) return MockStrategy.CITY;
        if (n.contains("balance") || n.contains("amount") || n.contains("price") || n.contains("solde") || n.contains("total") || t.contains("numeric") || t.contains("decimal")) {
            return MockStrategy.CURRENCY;
        }
        if (n.contains("status") || n.contains("state") || n.contains("etat")) return MockStrategy.STATUS;
        if (t.contains("bool")) return MockStrategy.BOOLEAN;
        if (t.contains("timestamp") || t.contains("datetime")) return MockStrategy.TIMESTAMP_NOW;
        if (t.contains("date")) return MockStrategy.DATE_PAST;
        if (t.contains("uuid")) return MockStrategy.UUID_V4;
        if (t.contains("int")) return MockStrategy.INTEGER_RANGE;

        return MockStrategy.FULL_NAME;
    }

    private void refreshSampleValues() {
        Random rand = new Random();
        for (int r = 0; r < strategyModel.getRowCount(); r++) {
            MockStrategy strat = (MockStrategy) strategyModel.getValueAt(r, 2);
            ColumnDefinition cd = r < currentColumns.size() ? currentColumns.get(r) : null;
            Object sample = generateSingleValue(strat, cd, 1, rand);
            strategyModel.setValueAt(sample != null ? sample.toString() : "<NULL>", r, 3);
        }
    }

    private Object generateSingleValue(MockStrategy strat, ColumnDefinition cd, int sequenceIndex, Random rand) {
        String[] firstNames = {"Alice", "Bob", "Charlie", "David", "Eric", "Frank", "Grace", "Henry", "Isabelle", "Jean", "Kevin", "Laura", "Marie", "Nathan", "Olivia", "Pierre", "Rose", "Samuel"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Mugisha", "Uwase", "Habimana", "Niyonzima", "Miller", "Davis", "Pierre", "Ndagijimana", "Brown", "Wilson", "Taylor"};
        String[] companies = {"Acme Capital", "Apex Solutions", "Nexus Financial", "Zenith Holdings", "Horizon Tech", "BlueSky Microfinance", "Kigali Logistics"};
        String[] cities = {"Kigali", "Nairobi", "Paris", "New York", "London", "Brussels", "Kampala", "Tokyo", "Berlin"};
        String[] statuses = {"ACTIVE", "PENDING", "COMPLETED", "SUSPENDED", "CLOSED"};

        switch (strat) {
            case AUTO_INCREMENT:
                return sequenceIndex;
            case FIRST_NAME:
                return firstNames[rand.nextInt(firstNames.length)];
            case LAST_NAME:
                return lastNames[rand.nextInt(lastNames.length)];
            case FULL_NAME:
                return firstNames[rand.nextInt(firstNames.length)] + " " + lastNames[rand.nextInt(lastNames.length)];
            case EMAIL:
                String fn = firstNames[rand.nextInt(firstNames.length)].toLowerCase();
                String ln = lastNames[rand.nextInt(lastNames.length)].toLowerCase();
                return fn + "." + ln + rand.nextInt(100) + "@example.com";
            case PHONE:
                return "+250 788 " + (100000 + rand.nextInt(900000));
            case COMPANY:
                return companies[rand.nextInt(companies.length)];
            case CITY:
                return cities[rand.nextInt(cities.length)];
            case CURRENCY:
                return String.format(Locale.US, "%.2f", 50.0 + (rand.nextDouble() * 4950.0));
            case INTEGER_RANGE:
                return 1 + rand.nextInt(1000);
            case BOOLEAN:
                return rand.nextBoolean();
            case DATE_PAST:
                long pastTime = System.currentTimeMillis() - (rand.nextInt(365) * 86400000L);
                return new SimpleDateFormat("yyyy-MM-dd").format(new Date(pastTime));
            case TIMESTAMP_NOW:
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            case UUID_V4:
                return UUID.randomUUID().toString();
            case STATUS:
                return statuses[rand.nextInt(statuses.length)];
            case FOREIGN_KEY_SAMPLE:
                return getSampleFkValue(cd, rand);
            case NULL_VALUE:
            default:
                return null;
        }
    }

    private Object getSampleFkValue(ColumnDefinition cd, Random rand) {
        if (cd == null || cd.foreignKeyRef == null) return 1;
        if (!foreignKeySampleCache.containsKey(cd.foreignKeyRef)) {
            List<Object> samples = new ArrayList<>();
            String[] parts = cd.foreignKeyRef.split("\\.");
            String parentTable = parts[0];
            String parentCol = parts[1];
            try (Statement stmt = dbManager.connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT " + dbManager.quoteColumnName(parentCol) +
                         " FROM " + dbManager.quoteTableName(parentTable) + " LIMIT 50")) {
                while (rs.next()) {
                    samples.add(rs.getObject(1));
                }
            } catch (Exception ignored) {}
            if (samples.isEmpty()) samples.add(1);
            foreignKeySampleCache.put(cd.foreignKeyRef, samples);
        }
        List<Object> cached = foreignKeySampleCache.get(cd.foreignKeyRef);
        return cached.get(rand.nextInt(cached.size()));
    }

    private void generatePreviewData() {
        Vector<String> colNames = new Vector<>();
        for (ColumnDefinition cd : currentColumns) colNames.add(cd.name);

        Vector<Vector<Object>> data = new Vector<>();
        Random rand = new Random(42); // deterministic seed for preview consistency
        for (int i = 1; i <= 10; i++) {
            Vector<Object> row = new Vector<>();
            for (int r = 0; r < strategyModel.getRowCount(); r++) {
                MockStrategy strat = (MockStrategy) strategyModel.getValueAt(r, 2);
                ColumnDefinition cd = r < currentColumns.size() ? currentColumns.get(r) : null;
                row.add(generateSingleValue(strat, cd, i, rand));
            }
            data.add(row);
        }
        previewModel.setDataVector(data, colNames);
    }

    private void executeBatchInsert() {
        String tbl = (String) tableCombo.getSelectedItem();
        if (tbl == null || currentColumns.isEmpty()) return;

        int rowCount = (Integer) rowCountSpinner.getValue();
        boolean isAtomic = atomicCheck.isSelected();

        int confirm = JOptionPane.showConfirmDialog(this,
                "Ready to generate and insert " + rowCount + " realistic mock record(s) into table '" + tbl + "'.\n\nProceed?",
                "Confirm Mock Data Insertion", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        insertBtn.setEnabled(false);
        progressBar.setMaximum(rowCount);
        progressBar.setValue(0);
        statusLabel.setText("Generating and inserting mock data...");

        new SwingWorker<Void, Integer>() {
            private int inserted = 0;
            private long startTime;

            @Override
            protected Void doInBackground() throws Exception {
                startTime = System.currentTimeMillis();
                boolean origAutoCommit = dbManager.connection.getAutoCommit();
                if (isAtomic) dbManager.connection.setAutoCommit(false);

                // Build insert SQL
                StringBuilder sql = new StringBuilder("INSERT INTO ");
                sql.append(dbManager.quoteTableName(tbl)).append(" (");
                StringBuilder placeholders = new StringBuilder();

                for (int i = 0; i < currentColumns.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                        placeholders.append(", ");
                    }
                    sql.append(dbManager.quoteColumnName(currentColumns.get(i).name));
                    placeholders.append("?");
                }
                sql.append(") VALUES (").append(placeholders).append(")");

                Random rand = new Random();
                int batchSize = 250;

                try (PreparedStatement pstmt = dbManager.connection.prepareStatement(sql.toString())) {
                    for (int r = 1; r <= rowCount; r++) {
                        for (int c = 0; c < currentColumns.size(); c++) {
                            MockStrategy strat = (MockStrategy) strategyModel.getValueAt(c, 2);
                            ColumnDefinition cd = currentColumns.get(c);
                            Object val = generateSingleValue(strat, cd, r, rand);
                            pstmt.setObject(c + 1, val);
                        }
                        pstmt.addBatch();
                        inserted++;

                        if (inserted % batchSize == 0 || inserted == rowCount) {
                            pstmt.executeBatch();
                            if (isAtomic) dbManager.connection.commit();
                            publish(inserted);
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
                progressBar.setString("Inserted " + last + " / " + rowCount + " records...");
            }

            @Override
            protected void done() {
                insertBtn.setEnabled(true);
                try {
                    get();
                    long elapsed = System.currentTimeMillis() - startTime;
                    com.dbtool.util.AppUsageTracker.recordMockRowsGenerated(inserted);
                    JOptionPane.showMessageDialog(MockDataGeneratorDialog.this,
                            "Successfully generated and inserted " + inserted + " mock record(s) into table '" + tbl + "' in " + elapsed + " ms!",
                            "Mock Generation Complete", JOptionPane.INFORMATION_MESSAGE);
                    if (onSuccessCallback != null) onSuccessCallback.run();
                    dispose();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MockDataGeneratorDialog.this,
                            "Mock generation failed" + (isAtomic ? " and was rolled back:\n" : ":\n") + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
