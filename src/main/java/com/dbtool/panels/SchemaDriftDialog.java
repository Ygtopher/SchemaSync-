package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.ColumnDefinition;
import com.dbtool.core.diff.*;
import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;
import com.dbtool.util.ThemeManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Schema Snapshot, Drift Detector & Migration Studio.
 * Captures portable JSON schema snapshots, compares live database schemas against baselines,
 * detects schema drift (tables, columns, types, nullability, keys), and generates reversible
 * UP and DOWN migration DDL scripts with transaction dry-run safety testing.
 */
public class SchemaDriftDialog extends JDialog {

    public static class SchemaSnapshot {
        public String snapshotName;
        public String databaseProductName;
        public String databaseVersion;
        public String createdAt;
        public List<TableSnapshot> tables = new ArrayList<>();
    }

    public static class TableSnapshot {
        public String tableName;
        public List<ColumnSnapshot> columns = new ArrayList<>();
        public List<String> primaryKeys = new ArrayList<>();
        public List<ForeignKeySnapshot> foreignKeys = new ArrayList<>();
    }

    public static class ColumnSnapshot {
        public String name;
        public String type;
        public int size;
        public boolean nullable;
        public String defaultValue;
        public boolean primaryKey;
        public boolean autoIncrement;
    }

    public static class ForeignKeySnapshot {
        public String fkColumn;
        public String pkTable;
        public String pkColumn;
    }

    private final DatabaseManager dbManager;
    private final JLabel statusLabel = new JLabel("Take a schema snapshot or load a baseline snapshot to compare drift.");
    private final DefaultTableModel driftTableModel;
    private final JTable driftTable;
    private final RSyntaxTextArea upArea;
    private final RSyntaxTextArea downArea;
    private final JTabbedPane scriptTabs = new JTabbedPane();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private SchemaSnapshot loadedBaselineSnapshot;
    private SchemaSnapshot currentLiveSnapshot;
    private SchemaDiffResult lastDiffResult;

    public SchemaDriftDialog(Frame parent, DatabaseManager dbManager) {
        super(parent, "📐 SchemaSync Schema Snapshot, Drift & Migration Studio", true);
        this.dbManager = dbManager;

        setSize(1040, 720);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // 1. Top Header & Snapshot Toolbar
        JPanel topContainer = new JPanel(new BorderLayout());
        JPanel headerPanel = new JPanel(new BorderLayout(15, 6));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        JLabel titleLabel = new JLabel("📐 Schema Snapshot & Drift Studio");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        statusLabel.setForeground(Color.GRAY);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(statusLabel, BorderLayout.SOUTH);
        topContainer.add(headerPanel, BorderLayout.NORTH);

        JPanel snapshotBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        snapshotBar.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));

        JButton takeSnapshotBtn = new JButton("💾 Save Live Schema Snapshot (.json)");
        takeSnapshotBtn.setToolTipText("Export full schema definition to portable JSON file");
        takeSnapshotBtn.addActionListener(e -> saveCurrentSnapshot());
        snapshotBar.add(takeSnapshotBtn);

        JButton loadBaselineBtn = new JButton("📂 Load Baseline Snapshot (.json)");
        loadBaselineBtn.setToolTipText("Load a previously saved schema JSON to detect drift");
        loadBaselineBtn.addActionListener(e -> loadBaselineSnapshot());
        snapshotBar.add(loadBaselineBtn);

        JButton compareDriftBtn = new JButton("🔄 Detect Schema Drift");
        compareDriftBtn.setFont(compareDriftBtn.getFont().deriveFont(Font.BOLD, 12f));
        compareDriftBtn.setToolTipText("Compare live database against baseline snapshot");
        compareDriftBtn.addActionListener(e -> runDriftComparison());
        snapshotBar.add(compareDriftBtn);

        topContainer.add(snapshotBar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

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

        scriptTabs.addTab("⚡ Forward Migration Script (UP)", new RTextScrollPane(upArea));
        scriptTabs.addTab("🔄 Rollback Script (DOWN)", new RTextScrollPane(downArea));

        mainSplit.setBottomComponent(scriptTabs);
        add(mainSplit, BorderLayout.CENTER);

        // 3. Bottom Action Bar
        JPanel bottomBar = new JPanel(new BorderLayout(10, 5));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JButton dryRunBtn = new JButton("🧪 Dry-Run (Test in Transaction)");
        dryRunBtn.setToolTipText("Simulate execution in an uncommitted transaction to test SQL validity");
        dryRunBtn.addActionListener(e -> executeDryRun());
        leftActions.add(dryRunBtn);

        JButton applyMigrationBtn = new JButton("⚡ Apply Migration to Live DB");
        applyMigrationBtn.setFont(applyMigrationBtn.getFont().deriveFont(Font.BOLD, 12f));
        applyMigrationBtn.setForeground(ThemeManager.getDangerColor());
        applyMigrationBtn.setToolTipText("Execute forward migration statements on the connected database");
        applyMigrationBtn.addActionListener(e -> applyMigration());
        leftActions.add(applyMigrationBtn);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        JButton copySqlBtn = new JButton("📋 Copy Active Script");
        copySqlBtn.addActionListener(e -> {
            String sql = scriptTabs.getSelectedIndex() == 1 ? downArea.getText() : upArea.getText();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sql), null);
            JOptionPane.showMessageDialog(this, "Active migration script copied to clipboard!");
        });
        rightActions.add(copySqlBtn);

        JButton exportSqlBtn = new JButton("💾 Export Migration (.sql)");
        exportSqlBtn.addActionListener(e -> exportMigrationScripts());
        rightActions.add(exportSqlBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        rightActions.add(closeBtn);

        bottomBar.add(leftActions, BorderLayout.WEST);
        bottomBar.add(rightActions, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);
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
        for (String tbl : tables) {
            if (tbl.startsWith("pg_") || tbl.startsWith("sql_") || tbl.equalsIgnoreCase("information_schema")) continue;

            TableSnapshot ts = new TableSnapshot();
            ts.tableName = tbl;

            // Columns
            List<ColumnDefinition> cols = dbManager.getDetailedColumns(tbl);
            if (cols != null) {
                for (ColumnDefinition c : cols) {
                    ColumnSnapshot cs = new ColumnSnapshot();
                    cs.name = c.name;
                    cs.type = c.getFriendlyType();
                    cs.size = c.size;
                    cs.nullable = c.isNullable;
                    cs.primaryKey = c.isPrimaryKey;
                    cs.autoIncrement = c.isAutoIncrement;
                    if (c.isPrimaryKey) ts.primaryKeys.add(c.name);
                    ts.columns.add(cs);
                }
            }

            // Foreign Keys
            Map<String, String[]> fks = dbManager.getForeignKeys(tbl);
            if (fks != null) {
                for (Map.Entry<String, String[]> entry : fks.entrySet()) {
                    ForeignKeySnapshot fk = new ForeignKeySnapshot();
                    fk.fkColumn = entry.getKey();
                    fk.pkTable = entry.getValue()[0];
                    fk.pkColumn = entry.getValue()[1];
                    ts.foreignKeys.add(fk);
                }
            }

            snapshot.tables.add(ts);
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
                statusLabel.setText("Baseline loaded: " + file.getName() + " (" + loadedBaselineSnapshot.tables.size() + " tables). Click 'Detect Schema Drift'.");
                runDriftComparison();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load snapshot JSON: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void runDriftComparison() {
        if (loadedBaselineSnapshot == null) {
            JOptionPane.showMessageDialog(this, "Please load a baseline snapshot JSON file first.", "No Baseline", JOptionPane.WARNING_MESSAGE);
            return;
        }

        driftTableModel.setRowCount(0);
        upArea.setText("");
        downArea.setText("");
        statusLabel.setText("Comparing live schema against baseline snapshot...");

        new Thread(() -> {
            try {
                currentLiveSnapshot = captureLiveSchema();

                // Convert to TableMetadata for SchemaComparator
                List<TableMetadata> baselineTables = convertToTableMetadata(loadedBaselineSnapshot);
                List<TableMetadata> liveTables = convertToTableMetadata(currentLiveSnapshot);

                SchemaComparator comparator = new SchemaComparator();
                lastDiffResult = comparator.compareSchemas(liveTables, baselineTables);

                SwingUtilities.invokeLater(() -> {
                    int newCount = 0;
                    int missingCount = 0;
                    int modifiedCount = 0;

                    for (TableDiff td : lastDiffResult.getTableDiffs()) {
                        if (td.getType() == DifferenceType.ADDED) {
                            // Present in Live, missing in Baseline
                            driftTableModel.addRow(new Object[]{
                                "🟢 NEW TABLE", td.getTableName(), "Table exists in live DB but not in baseline", "CREATE TABLE in target / Keep in live"
                            });
                            newCount++;
                        } else if (td.getType() == DifferenceType.REMOVED) {
                            // Present in Baseline, missing in Live
                            driftTableModel.addRow(new Object[]{
                                "🔴 MISSING TABLE", td.getTableName(), "Table defined in baseline is missing in live DB", "CREATE TABLE in live DB"
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
                    String upSql = DiffScriptGenerator.generateSchemaSyncScript(lastDiffResult, "Live DB", "Baseline Snapshot");
                    String downSql = DiffScriptGenerator.generateRollbackScript(lastDiffResult, "Live DB", "Baseline Snapshot");

                    upArea.setText(upSql);
                    downArea.setText(downSql);

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
            TableMetadata tm = new TableMetadata(null, ts.tableName);
            if (ts.columns != null) {
                for (ColumnSnapshot cs : ts.columns) {
                    ColumnMetadata cm = new ColumnMetadata();
                    cm.setColumnName(cs.name);
                    cm.setTypeName(cs.type);
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
