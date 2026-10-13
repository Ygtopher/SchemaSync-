package com.dbtool.panels;

import com.dbtool.core.diff.DiffScriptGenerator;
import com.dbtool.core.diff.DifferenceType;
import com.dbtool.core.diff.SchemaDiffResult;
import com.dbtool.core.diff.TableDiff;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.Statement;

public class MigrationWorkbenchDialog extends JDialog {

    private final RSyntaxTextArea forwardArea;
    private final RSyntaxTextArea rollbackArea;
    private final JTabbedPane tabbedPane;
    private final DualDatabaseComparePanel.DatabaseEntry targetEntry;
    private final Runnable onMigrationApplied;

    public MigrationWorkbenchDialog(Frame parent, SchemaDiffResult diffResult,
                                    String srcLabel, DualDatabaseComparePanel.DatabaseEntry targetEntry,
                                    Runnable onMigrationApplied) {
        super(parent, "⚡ SchemaSync Migration & Rollback Workbench", true);
        this.targetEntry = targetEntry;
        this.onMigrationApplied = onMigrationApplied;

        setSize(850, 580);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        String tgtLabel = targetEntry.getLabel();
        String forwardSql = DiffScriptGenerator.generateSchemaSyncScript(diffResult, srcLabel, tgtLabel);
        String rollbackSql = DiffScriptGenerator.generateRollbackScript(diffResult, srcLabel, tgtLabel);

        tabbedPane = new JTabbedPane();

        // 1. Forward Migration Tab
        forwardArea = new RSyntaxTextArea(forwardSql);
        forwardArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        forwardArea.setEditable(true);
        tabbedPane.addTab("⚡ Forward Migration (UP)", new RTextScrollPane(forwardArea));

        // 2. Rollback Tab
        rollbackArea = new RSyntaxTextArea(rollbackSql);
        rollbackArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        rollbackArea.setEditable(true);
        tabbedPane.addTab("🔄 Rollback Script (DOWN)", new RTextScrollPane(rollbackArea));

        // 3. Summary Tab
        JPanel summaryPanel = buildSummaryPanel(diffResult, srcLabel, tgtLabel);
        tabbedPane.addTab("📋 Impact Summary", summaryPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom Action Toolbar
        JPanel bottomBar = new JPanel(new BorderLayout(10, 5));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton dryRunBtn = new JButton("🧪 Dry-Run (Test in Transaction)");
        dryRunBtn.setToolTipText("Simulate execution on target in an uncommitted transaction to verify syntax and safety");
        dryRunBtn.addActionListener(e -> executeDryRun());
        leftActions.add(dryRunBtn);

        JButton saveBtn = new JButton("💾 Export (.sql)");
        saveBtn.addActionListener(e -> exportScripts());
        leftActions.add(saveBtn);

        JButton copyBtn = new JButton("📋 Copy Active Script");
        copyBtn.addActionListener(e -> {
            String activeText = tabbedPane.getSelectedIndex() == 1 ? rollbackArea.getText() : forwardArea.getText();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(activeText), null);
            JOptionPane.showMessageDialog(this, "SQL script copied to clipboard!");
        });
        leftActions.add(copyBtn);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        JButton applyBtn = new JButton("⚡ Apply Migration to " + tgtLabel);
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD, 12f));
        applyBtn.setForeground(ThemeManager.getDangerColor());
        applyBtn.addActionListener(e -> applyMigration());
        rightActions.add(applyBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        rightActions.add(closeBtn);

        bottomBar.add(leftActions, BorderLayout.WEST);
        bottomBar.add(rightActions, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private JPanel buildSummaryPanel(SchemaDiffResult diffResult, String srcLabel, String tgtLabel) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        int newTables = 0;
        int modifiedTables = 0;
        int addedCols = 0;
        int alteredCols = 0;

        if (diffResult != null) {
            for (TableDiff td : diffResult.getTableDiffs()) {
                if (td.getType() == DifferenceType.REMOVED) newTables++;
                else if (td.getType() == DifferenceType.MODIFIED) {
                    modifiedTables++;
                    addedCols += td.getRemovedColumnsCount();
                    alteredCols += td.getModifiedColumnsCount();
                }
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;'>");
        html.append("<h2>Migration Impact Analysis</h2>");
        html.append("<p>Synchronizing <b>").append(tgtLabel).append("</b> to match <b>").append(srcLabel).append("</b></p>");
        html.append("<hr/>");
        html.append("<table cellpadding='6' cellspacing='0' style='font-size:13px;'>");
        html.append("<tr><td><b>Tables to be Created:</b></td><td style='color:#10b981; font-weight:bold;'>").append(newTables).append("</td></tr>");
        html.append("<tr><td><b>Tables to be Modified:</b></td><td style='color:#f59e0b; font-weight:bold;'>").append(modifiedTables).append("</td></tr>");
        html.append("<tr><td><b>Columns to be Added:</b></td><td style='color:#06b6d4; font-weight:bold;'>").append(addedCols).append("</td></tr>");
        html.append("<tr><td><b>Columns with Type/Nullability Updates:</b></td><td style='color:#8b5cf6; font-weight:bold;'>").append(alteredCols).append("</td></tr>");
        html.append("</table>");
        html.append("<br/><p style='color:gray;'>💡 Use <b>Dry-Run</b> to test this migration without altering any persistent data on the target database.</p>");
        html.append("</body></html>");

        JLabel label = new JLabel(html.toString());
        label.setVerticalAlignment(SwingConstants.TOP);
        panel.add(new JScrollPane(label), BorderLayout.CENTER);
        return panel;
    }

    private void executeDryRun() {
        if (targetEntry == null || targetEntry.getDbManager() == null || !targetEntry.getDbManager().isConnected()) {
            JOptionPane.showMessageDialog(this, "Target database is not connected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sql = forwardArea.getText();
        String[] statements = sql.split(";");

        Connection conn = targetEntry.getDbManager().connection;
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false); // Begin transaction

            int count = 0;
            try (Statement stmt = conn.createStatement()) {
                for (String s : statements) {
                    String tr = s.trim();
                    if (!tr.isEmpty() && !tr.startsWith("--")) {
                        stmt.execute(tr);
                        count++;
                    }
                }
            }

            // Always rollback in Dry-Run
            conn.rollback();
            conn.setAutoCommit(originalAutoCommit);

            JOptionPane.showMessageDialog(this,
                "✅ Dry-Run Succeeded!\n\n" + count + " DDL statements were successfully simulated in an isolated transaction.\n" +
                "All statements executed with valid syntax and no constraint violations.\nTransaction was rolled back safely.",
                "Dry-Run Passed", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            try { conn.rollback(); conn.setAutoCommit(originalAutoCommit); } catch (Exception ignored) {}
            JOptionPane.showMessageDialog(this,
                "❌ Dry-Run Failed!\n\nError during simulation:\n" + ex.getMessage(),
                "Dry-Run Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyMigration() {
        if (targetEntry == null || targetEntry.getDbManager() == null || !targetEntry.getDbManager().isConnected()) {
            JOptionPane.showMessageDialog(this, "Target database is not connected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to execute this migration on " + targetEntry.getLabel() + "?\nThis will permanently modify tables and schemas on the target database.",
            "Confirm Migration Execution", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        String sql = forwardArea.getText();
        String[] statements = sql.split(";");

        new Thread(() -> {
            try {
                Connection conn = targetEntry.getDbManager().connection;
                int count = 0;
                try (Statement stmt = conn.createStatement()) {
                    for (String s : statements) {
                        String tr = s.trim();
                        if (!tr.isEmpty() && !tr.startsWith("--")) {
                            stmt.execute(tr);
                            count++;
                        }
                    }
                }
                targetEntry.getDbManager().clearCache();

                final int finalCount = count;
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "🎉 Migration executed successfully!\n" + finalCount + " statements applied to " + targetEntry.getLabel(),
                        "Migration Success", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    if (onMigrationApplied != null) onMigrationApplied.run();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Failed to apply migration:\n" + ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void exportScripts() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select Directory to Export Migration & Rollback Scripts");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = fc.getSelectedFile();
            long ts = System.currentTimeMillis();
            File fwdFile = new File(dir, "V" + ts + "__migration_up.sql");
            File rollFile = new File(dir, "U" + ts + "__rollback_down.sql");
            try {
                try (FileWriter fw = new FileWriter(fwdFile)) { fw.write(forwardArea.getText()); }
                try (FileWriter fw = new FileWriter(rollFile)) { fw.write(rollbackArea.getText()); }
                JOptionPane.showMessageDialog(this,
                    "Exported successfully:\n• " + fwdFile.getName() + "\n• " + rollFile.getName(),
                    "Scripts Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error writing files: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
