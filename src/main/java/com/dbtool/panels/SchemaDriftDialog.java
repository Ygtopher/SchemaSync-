package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.core.diff.ColumnDiff;
import com.dbtool.core.diff.DiffScriptGenerator;
import com.dbtool.core.diff.DifferenceType;
import com.dbtool.core.diff.SchemaComparator;
import com.dbtool.core.diff.SchemaDiffResult;
import com.dbtool.core.diff.TableDiff;
import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;
import com.dbtool.DatabaseManager.ColumnDefinition;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Schema Snapshot, Drift Detector & Migration Studio.
 * Allows capturing portable JSON schema snapshots, loading baseline and target snapshots,
 * detecting structural drift across environments (live vs snapshot or snapshot vs snapshot),
 * and generating reversible UP and DOWN SQL migration scripts with transaction dry-run safety.
 */
public class SchemaDriftDialog extends JDialog {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SchemaSnapshot {
        public String snapshotName = "Schema Snapshot";
        public String databaseProductName = "";
        public String databaseVersion = "";
        public String createdAt = "";
        public List<TableSnapshot> tables = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableSnapshot {
        public String tableName;
        public List<ColumnSnapshot> columns = new ArrayList<>();
        public List<String> primaryKeys = new ArrayList<>();
        public List<ForeignKeySnapshot> foreignKeys = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColumnSnapshot {
        public String name;
        public String type;
        public int size;
        public boolean nullable;
        public String defaultValue;
        public boolean primaryKey;
        public boolean autoIncrement;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ForeignKeySnapshot {
        public String fkColumn;
        public String pkTable;
        public String pkColumn;
    }

    private final DatabaseManager dbManager;
    private final JLabel statusLabel = new JLabel("Take a schema snapshot or load baseline/target snapshots to compare drift.");
    private final JLabel compareSourceLabel = new JLabel("Baseline: [None]  ↔  Target: [Live Database]");
    private final DefaultTableModel driftTableModel;
    private final JTable driftTable;
    private final RSyntaxTextArea upArea;
    private final RSyntaxTextArea downArea;
    private final JTabbedPane scriptTabs = new JTabbedPane();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private SchemaSnapshot loadedBaselineSnapshot;
    private String baselineFileName = null;
    private SchemaSnapshot loadedTargetSnapshot;
    private String targetFileName = null;
    private SchemaSnapshot currentLiveSnapshot;
    private SchemaDiffResult lastDiffResult;
    private JButton takeSnapshotBtn;

    public SchemaDriftDialog(Frame parent, DatabaseManager dbManager) {
        super(parent, "📐 SchemaSync Schema Snapshot, Drift & Migration Studio", true);
        this.dbManager = dbManager;

        setSize(1080, 740);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        boolean hasLiveDb = (dbManager != null && dbManager.connection != null);

        // 1. Top Header & Snapshot Toolbar
        JPanel topContainer = new JPanel(new BorderLayout());
        JPanel headerPanel = new JPanel(new BorderLayout(15, 6));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 6, 16));

        JLabel titleLabel = new JLabel("📐 Schema Snapshot & Drift Studio");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        statusLabel.setForeground(Color.GRAY);
        compareSourceLabel.setFont(compareSourceLabel.getFont().deriveFont(Font.BOLD, 12f));
        compareSourceLabel.setForeground(com.dbtool.util.ThemeManager.getPrimaryActionColor());

        JPanel titleBlock = new JPanel(new BorderLayout(4, 2));
        titleBlock.add(titleLabel, BorderLayout.NORTH);
        titleBlock.add(compareSourceLabel, BorderLayout.SOUTH);

        headerPanel.add(titleBlock, BorderLayout.NORTH);
        headerPanel.add(statusLabel, BorderLayout.SOUTH);
        topContainer.add(headerPanel, BorderLayout.NORTH);

        JPanel snapshotBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        snapshotBar.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));

        takeSnapshotBtn = new JButton("💾 Save Live Schema Snapshot (.json)");
        takeSnapshotBtn.setToolTipText(hasLiveDb ? "Export full live schema definition to portable JSON file" : "Connect to a database first to capture live schema");
        takeSnapshotBtn.setEnabled(hasLiveDb);
        takeSnapshotBtn.addActionListener(e -> saveCurrentSnapshot());
        snapshotBar.add(takeSnapshotBtn);

        JButton loadBaselineBtn = new JButton("📂 Load Baseline Snapshot (.json)");
        loadBaselineBtn.setToolTipText("Load a previously saved schema JSON baseline to detect drift");
        loadBaselineBtn.addActionListener(e -> loadBaselineSnapshot());
        snapshotBar.add(loadBaselineBtn);

        JButton loadTargetBtn = new JButton("📂 Load Target Snapshot (.json)");
        loadTargetBtn.setToolTipText("Load a target schema snapshot file (enables comparing 2 snapshot files offline)");
        loadTargetBtn.addActionListener(e -> loadTargetSnapshot());
        snapshotBar.add(loadTargetBtn);

        JButton swapBtn = new JButton("⇄ Swap");
        swapBtn.setToolTipText("Swap Baseline and Target sources");
        swapBtn.addActionListener(e -> swapSources());
        snapshotBar.add(swapBtn);

        JButton compareDriftBtn = new JButton("🔄 Detect Schema Drift");
        compareDriftBtn.setFont(compareDriftBtn.getFont().deriveFont(Font.BOLD, 12f));
        compareDriftBtn.setForeground(new Color(0, 140, 0));
        compareDriftBtn.setToolTipText("Compare schemas and generate UP / DOWN migrations");
        compareDriftBtn.addActionListener(e -> runDriftComparison());
        snapshotBar.add(compareDriftBtn);

        JButton inspectBtn = new JButton("📜 Inspect Tables");
        inspectBtn.setToolTipText("View table definitions inside the currently loaded snapshots");
        inspectBtn.addActionListener(e -> inspectLoadedSnapshots());
        snapshotBar.add(inspectBtn);

        topContainer.add(snapshotBar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // Update initial label state
        updateComparisonSourceLabels();

        // 2. Center Split (Drift Overview on Top, UP/DOWN Scripts on Bottom)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setResizeWeight(0.48);

        // Top Drift Table
        String[] cols = {"Status", "Table", "Change Description", "Suggested Action"};
        driftTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        driftTable = new JTable(driftTableModel);
        driftTable.setRowHeight(22);

        driftTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if (val.contains("NEW")) l.setForeground(new Color(16, 185, 129));
                else if (val.contains("MISSING")) l.setForeground(new Color(239, 68, 68));
                else if (val.contains("MODIFIED")) l.setForeground(new Color(245, 158, 11));
                else l.setForeground(Color.GRAY);
                return l;
            }
        });

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Detected Schema Drift & Discrepancies"));
        tablePanel.add(new JScrollPane(driftTable), BorderLayout.CENTER);
        mainSplit.setTopComponent(tablePanel);

        // Bottom Scripts Tabs
        upArea = new RSyntaxTextArea();
        upArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        upArea.setEditable(true);

        downArea = new RSyntaxTextArea();
        downArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        downArea.setEditable(true);

        scriptTabs.addTab("⚡ Forward Migration (UP)", new RTextScrollPane(upArea));
        scriptTabs.addTab("↩️ Rollback Script (DOWN)", new RTextScrollPane(downArea));
        mainSplit.setBottomComponent(scriptTabs);

        add(mainSplit, BorderLayout.CENTER);

        // 3. Bottom Execution Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        JPanel actionsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JButton dryRunBtn = new JButton("🧪 Dry-Run Transaction");
        dryRunBtn.setToolTipText("Test UP migration script inside a transaction and rollback automatically");
        dryRunBtn.addActionListener(e -> executeDryRun());
        actionsRight.add(dryRunBtn);

        JButton exportScriptsBtn = new JButton("💾 Export Migration Files (.sql)");
        exportScriptsBtn.setToolTipText("Save UP and DOWN scripts to disk (Flyway/Liquibase compatible)");
        exportScriptsBtn.addActionListener(e -> exportMigrationScripts());
        actionsRight.add(exportScriptsBtn);

        JButton applyBtn = new JButton("⚡ Apply Migration to Live DB");
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD, 12f));
        applyBtn.setForeground(new Color(220, 38, 38));
        applyBtn.setToolTipText("Execute UP migration statements against the active database");
        applyBtn.addActionListener(e -> applyMigration());
        actionsRight.add(applyBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        actionsRight.add(closeBtn);

        bottomBar.add(actionsRight, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void updateComparisonSourceLabels() {
        boolean hasLiveDb = (dbManager != null && dbManager.connection != null);
        String baseDesc = loadedBaselineSnapshot != null
                ? (baselineFileName != null ? baselineFileName : "Baseline Snapshot") + " (" + loadedBaselineSnapshot.tables.size() + " tables)"
                : "[None - Load .json file]";

        String targetDesc;
        if (loadedTargetSnapshot != null) {
            targetDesc = (targetFileName != null ? targetFileName : "Target Snapshot") + " (" + loadedTargetSnapshot.tables.size() + " tables)";
        } else if (hasLiveDb) {
            targetDesc = "Live DB: " + (dbManager.getLastDbName() != null ? dbManager.getLastDbName() : "Connected");
        } else {
            targetDesc = "[None - Connect DB or Load .json file]";
        }

        compareSourceLabel.setText("Baseline: " + baseDesc + "   ⇄   Target: " + targetDesc);
    }

    private void swapSources() {
        SchemaSnapshot tempSnap = loadedBaselineSnapshot;
        String tempName = baselineFileName;

        loadedBaselineSnapshot = loadedTargetSnapshot;
        baselineFileName = targetFileName;

        loadedTargetSnapshot = tempSnap;
        targetFileName = tempName;

        updateComparisonSourceLabels();
        if (loadedBaselineSnapshot != null && (loadedTargetSnapshot != null || (dbManager != null && dbManager.connection != null))) {
            runDriftComparison();
        }
    }

    private SchemaSnapshot captureLiveSchema() throws Exception {
        if (dbManager == null || dbManager.connection == null) {
            throw new IllegalStateException("No active database connection.");
        }

        Connection conn = dbManager.connection;
        DatabaseMetaData meta = conn.getMetaData();

        SchemaSnapshot snapshot = new SchemaSnapshot();
        snapshot.snapshotName = "Live Database (" + meta.getDatabaseProductName() + ")";
        snapshot.databaseProductName = meta.getDatabaseProductName();
        snapshot.databaseVersion = meta.getDatabaseProductVersion();
        snapshot.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        List<String> tables = dbManager.getTableNames();
        if (tables != null) {
            for (String tbl : tables) {
                if (tbl == null) continue;
                if (tbl.startsWith("pg_") || tbl.startsWith("sql_") || tbl.equalsIgnoreCase("information_schema")) continue;

                TableSnapshot ts = new TableSnapshot();
                ts.tableName = tbl;

                // Columns
                try {
                    List<ColumnDefinition> cols = dbManager.getDetailedColumns(tbl);
                    if (cols != null) {
                        for (ColumnDefinition c : cols) {
                            if (c == null) continue;
                            ColumnSnapshot cs = new ColumnSnapshot();
                            cs.name = c.name;
                            cs.type = c.getFriendlyType() != null ? c.getFriendlyType() : "VARCHAR";
                            cs.size = c.size;
                            cs.nullable = c.isNullable;
                            cs.primaryKey = c.isPrimaryKey;
                            cs.autoIncrement = c.isAutoIncrement;
                            if (c.isPrimaryKey) ts.primaryKeys.add(c.name);
                            ts.columns.add(cs);
                        }
                    }
                } catch (Exception ignored) {}

                // Foreign Keys
                try {
                    Map<String, String[]> fks = dbManager.getForeignKeys(tbl);
                    if (fks != null) {
                        for (Map.Entry<String, String[]> entry : fks.entrySet()) {
                            if (entry.getKey() != null && entry.getValue() != null && entry.getValue().length >= 2) {
                                ForeignKeySnapshot fk = new ForeignKeySnapshot();
                                fk.fkColumn = entry.getKey();
                                fk.pkTable = entry.getValue()[0];
                                fk.pkColumn = entry.getValue()[1];
                                ts.foreignKeys.add(fk);
                            }
                        }
                    }
                } catch (Exception ignored) {}

                snapshot.tables.add(ts);
            }
        }

        return snapshot;
    }

    private void saveCurrentSnapshot() {
        try {
            SchemaSnapshot snapshot = captureLiveSchema();
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("schema-snapshot-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".schema.json"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File target = chooser.getSelectedFile();
                if (!target.getName().toLowerCase().endsWith(".json")) {
                    target = new File(target.getParentFile(), target.getName() + ".schema.json");
                }
                mapper.writeValue(target, snapshot);
                com.dbtool.util.AppUsageTracker.recordSchemaDriftAction();
                JOptionPane.showMessageDialog(this, "Schema snapshot saved successfully to:\n" + target.getAbsolutePath(), "Snapshot Saved", JOptionPane.INFORMATION_MESSAGE);
                statusLabel.setText("Snapshot saved: " + target.getName() + " (" + snapshot.tables.size() + " tables).");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to capture snapshot: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadBaselineSnapshot() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                loadedBaselineSnapshot = mapper.readValue(file, SchemaSnapshot.class);
                baselineFileName = file.getName();
                statusLabel.setText("Baseline loaded: " + file.getName() + " (" + loadedBaselineSnapshot.tables.size() + " tables). Click 'Detect Schema Drift'.");
                updateComparisonSourceLabels();
                runDriftComparison();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load snapshot JSON: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadTargetSnapshot() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                loadedTargetSnapshot = mapper.readValue(file, SchemaSnapshot.class);
                targetFileName = file.getName();
                statusLabel.setText("Target loaded: " + file.getName() + " (" + loadedTargetSnapshot.tables.size() + " tables).");
                updateComparisonSourceLabels();
                if (loadedBaselineSnapshot != null) {
                    runDriftComparison();
                } else {
                    JOptionPane.showMessageDialog(this, "Target snapshot loaded. Now load a Baseline Snapshot to detect differences.", "Baseline Needed", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load target snapshot JSON: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void inspectLoadedSnapshots() {
        if (loadedBaselineSnapshot == null && loadedTargetSnapshot == null && (dbManager == null || dbManager.connection == null)) {
            JOptionPane.showMessageDialog(this, "No snapshots or live databases are currently loaded to inspect.", "No Schemas Loaded", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (loadedBaselineSnapshot != null) {
            sb.append("=== BASELINE SNAPSHOT: ").append(baselineFileName != null ? baselineFileName : "Baseline").append(" ===\n");
            sb.append("Created: ").append(loadedBaselineSnapshot.createdAt).append("\n");
            sb.append("Database: ").append(loadedBaselineSnapshot.databaseProductName).append(" ").append(loadedBaselineSnapshot.databaseVersion).append("\n");
            sb.append("Tables (").append(loadedBaselineSnapshot.tables.size()).append("):\n");
            for (TableSnapshot ts : loadedBaselineSnapshot.tables) {
                sb.append("  • ").append(ts.tableName).append(" (").append(ts.columns.size()).append(" columns)\n");
            }
            sb.append("\n");
        }

        if (loadedTargetSnapshot != null) {
            sb.append("=== TARGET SNAPSHOT: ").append(targetFileName != null ? targetFileName : "Target").append(" ===\n");
            sb.append("Created: ").append(loadedTargetSnapshot.createdAt).append("\n");
            sb.append("Database: ").append(loadedTargetSnapshot.databaseProductName).append(" ").append(loadedTargetSnapshot.databaseVersion).append("\n");
            sb.append("Tables (").append(loadedTargetSnapshot.tables.size()).append("):\n");
            for (TableSnapshot ts : loadedTargetSnapshot.tables) {
                sb.append("  • ").append(ts.tableName).append(" (").append(ts.columns.size()).append(" columns)\n");
            }
        }

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(550, 400));
        JOptionPane.showMessageDialog(this, sp, "Loaded Snapshot Structure", JOptionPane.PLAIN_MESSAGE);
    }

    private void runDriftComparison() {
        if (loadedBaselineSnapshot == null) {
            JOptionPane.showMessageDialog(this, "Please load a baseline snapshot JSON file first.", "No Baseline", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean hasLiveDb = (dbManager != null && dbManager.connection != null);
        if (loadedTargetSnapshot == null && !hasLiveDb) {
            JOptionPane.showMessageDialog(this, "Please connect to a live database or load a Target Snapshot JSON file to compare against the baseline.", "No Target Schema", JOptionPane.WARNING_MESSAGE);
            return;
        }

        driftTableModel.setRowCount(0);
        upArea.setText("");
        downArea.setText("");
        statusLabel.setText("Comparing schema against baseline snapshot...");

        new Thread(() -> {
            try {
                final SchemaSnapshot targetSnap;
                final String targetLabel;
                final String baselineLabel = baselineFileName != null ? baselineFileName : "Baseline Snapshot";

                if (loadedTargetSnapshot != null) {
                    targetSnap = loadedTargetSnapshot;
                    targetLabel = targetFileName != null ? targetFileName : "Target Snapshot";
                } else {
                    currentLiveSnapshot = captureLiveSchema();
                    targetSnap = currentLiveSnapshot;
                    targetLabel = "Live DB (" + (dbManager.getLastDbName() != null ? dbManager.getLastDbName() : "Active") + ")";
                }

                // Convert to TableMetadata for SchemaComparator
                List<TableMetadata> baselineTables = convertToTableMetadata(loadedBaselineSnapshot);
                List<TableMetadata> targetTables = convertToTableMetadata(targetSnap);

                SchemaComparator comparator = new SchemaComparator();
                lastDiffResult = comparator.compareSchemas(targetTables, baselineTables);

                SwingUtilities.invokeLater(() -> {
                    int newCount = 0;
                    int missingCount = 0;
                    int modifiedCount = 0;

                    for (TableDiff td : lastDiffResult.getTableDiffs()) {
                        if (td.getType() == DifferenceType.ADDED) {
                            // Present in Target, missing in Baseline
                            driftTableModel.addRow(new Object[]{
                                "🟢 NEW TABLE", td.getTableName(), "Table exists in " + targetLabel + " but not in baseline", "CREATE TABLE in baseline / Keep in target"
                            });
                            newCount++;
                        } else if (td.getType() == DifferenceType.REMOVED) {
                            // Present in Baseline, missing in Target
                            driftTableModel.addRow(new Object[]{
                                "🔴 MISSING TABLE", td.getTableName(), "Table defined in baseline is missing in " + targetLabel, "CREATE TABLE in target"
                            });
                            missingCount++;
                        } else if (td.getType() == DifferenceType.MODIFIED) {
                            for (ColumnDiff cd : td.getColumnDiffs()) {
                                String desc = cd.getDescription();
                                driftTableModel.addRow(new Object[]{
                                    "🟡 MODIFIED", td.getTableName() + "." + cd.getColumnName(), desc, "ALTER TABLE"
                                });
                            }
                            modifiedCount++;
                        }
                    }

                    // Generate UP & DOWN Migration SQL
                    String upSql = DiffScriptGenerator.generateSchemaSyncScript(lastDiffResult, targetLabel, baselineLabel);
                    String downSql = DiffScriptGenerator.generateRollbackScript(lastDiffResult, targetLabel, baselineLabel);

                    upArea.setText(upSql);
                    downArea.setText(downSql);
                    com.dbtool.util.AppUsageTracker.recordSchemaDriftAction();

                    statusLabel.setText(String.format("Drift Analysis: 🟢 %d New Tables, 🔴 %d Missing Tables, 🟡 %d Modified Tables (%d total diffs)",
                        newCount, missingCount, modifiedCount, lastDiffResult.getTableDiffs().size()));
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Drift analysis failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Comparison error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private List<TableMetadata> convertToTableMetadata(SchemaSnapshot snapshot) {
        List<TableMetadata> list = new ArrayList<>();
        if (snapshot == null || snapshot.tables == null) return list;

        for (TableSnapshot ts : snapshot.tables) {
            if (ts == null || ts.tableName == null) continue;
            TableMetadata tm = new TableMetadata(null, ts.tableName);
            if (ts.columns != null) {
                for (ColumnSnapshot cs : ts.columns) {
                    if (cs == null || cs.name == null) continue;
                    ColumnMetadata cm = new ColumnMetadata();
                    cm.setColumnName(cs.name);
                    cm.setTypeName(cs.type != null ? cs.type : "VARCHAR");
                    cm.setColumnSize(cs.size);
                    cm.setNullable(cs.nullable);
                    cm.setDefaultValue(cs.defaultValue);
                    cm.setPrimaryKey(cs.primaryKey);
                    tm.addColumn(cm);
                }
            }
            list.add(tm);
        }
        return list;
    }

    private void executeDryRun() {
        if (dbManager == null || dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "Dry-run requires an active database connection to test transactions.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sql = upArea.getText().trim();
        if (sql.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No migration SQL to dry-run.", "Empty Script", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Connection conn = dbManager.connection;
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                String[] statements = sql.split(";");
                int executed = 0;
                for (String s : statements) {
                    String clean = s.trim();
                    if (!clean.isEmpty() && !clean.startsWith("--")) {
                        stmt.execute(clean);
                        executed++;
                    }
                }
                JOptionPane.showMessageDialog(this,
                    "✅ Dry-run Successful!\n\nExecuted " + executed + " statements in transaction with zero syntax errors.\n(Transaction was safely rolled back. Live database was not modified).",
                    "Dry-Run Passed", JOptionPane.INFORMATION_MESSAGE);
            } finally {
                conn.rollback();
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "❌ Dry-Run Failed!\n\nError: " + ex.getMessage(),
                "Dry-Run Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyMigration() {
        if (dbManager == null || dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "Applying migration requires an active live database connection.", "No Database Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sql = upArea.getText().trim();
        if (sql.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No migration statements to apply.", "Empty Script", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to execute this forward migration on the live connected database?\n\nThis will modify tables and schema structure.",
            "Confirm Live Migration",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        try {
            Connection conn = dbManager.connection;
            try (Statement stmt = conn.createStatement()) {
                String[] statements = sql.split(";");
                int executed = 0;
                for (String s : statements) {
                    String clean = s.trim();
                    if (!clean.isEmpty() && !clean.startsWith("--")) {
                        stmt.execute(clean);
                        executed++;
                    }
                }
                JOptionPane.showMessageDialog(this,
                    "⚡ Migration applied successfully!\nExecuted " + executed + " statements.",
                    "Migration Complete", JOptionPane.INFORMATION_MESSAGE);
                runDriftComparison();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Error applying migration: " + ex.getMessage(),
                "Execution Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportMigrationScripts() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("V1__schema_migration.sql"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File upFile = chooser.getSelectedFile();
            File downFile = new File(upFile.getParentFile(), "U1__rollback_" + upFile.getName());
            try {
                try (FileWriter fw = new FileWriter(upFile)) {
                    fw.write(upArea.getText());
                }
                try (FileWriter fw = new FileWriter(downFile)) {
                    fw.write(downArea.getText());
                }
                JOptionPane.showMessageDialog(this,
                    "Saved migration scripts:\n• " + upFile.getAbsolutePath() + "\n• " + downFile.getAbsolutePath(),
                    "Scripts Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving files: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
