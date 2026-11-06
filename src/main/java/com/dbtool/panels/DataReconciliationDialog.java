package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * Interactive SQL Dry-Run & Safe Transactional Data Reconciliation Dialog.
 * Synchronizes mismatched records between databases with preview and atomic rollback.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class DataReconciliationDialog extends JDialog {

    private final Window parentWindow;
    private final DatabaseManager targetDbManager;
    private final String targetDbLabel;
    private final String targetTableName;
    private final List<String> sqlStatements;
    private final Runnable onSuccessCallback;

    private final RSyntaxTextArea sqlArea = new RSyntaxTextArea(14, 60);
    private final JCheckBox chkTransaction = new JCheckBox("Execute inside an isolated transaction (Rollback on error)", true);
    private final JButton btnExecute = new JButton("▶ Execute Reconciliation");
    private final JButton btnSaveSql = new JButton("💾 Save SQL Script...");
    private final JButton btnClose = new JButton("Cancel");

    public DataReconciliationDialog(Window parentWindow,
                                    DatabaseManager targetDbManager,
                                    String targetDbLabel,
                                    String targetTableName,
                                    List<String> sqlStatements,
                                    int insertCount,
                                    int updateCount,
                                    int deleteCount,
                                    Runnable onSuccessCallback) {
        super(parentWindow, "Data Reconciliation & Synchronization Preview", ModalityType.APPLICATION_MODAL);
        this.parentWindow = parentWindow;
        this.targetDbManager = targetDbManager;
        this.targetDbLabel = targetDbLabel;
        this.targetTableName = targetTableName;
        this.sqlStatements = sqlStatements;
        this.onSuccessCallback = onSuccessCallback;

        setSize(780, 560);
        setLocationRelativeTo(parentWindow);
        setLayout(new BorderLayout(8, 8));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        JLabel titleLabel = new JLabel("⚡ Reconcile Data into " + targetDbLabel + " (" + targetTableName + ")");
        titleLabel.setFont(ThemeManager.getUiFont(Font.BOLD, 15));
        titleLabel.setForeground(ThemeManager.getPrimaryActionColor());

        JLabel summaryLabel = new JLabel(String.format("Generated %d SQL statement(s): %d INSERT, %d UPDATE, %d DELETE.",
                sqlStatements.size(), insertCount, updateCount, deleteCount));
        summaryLabel.setFont(ThemeManager.getUiFont(Font.PLAIN, 12));
        summaryLabel.setForeground(ThemeManager.getMutedTextColor());

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(summaryLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Center SQL Preview Area
        sqlArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        sqlArea.setEditable(true);
        sqlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        StringBuilder sb = new StringBuilder();
        for (String sql : sqlStatements) {
            sb.append(sql).append(";\n");
        }
        sqlArea.setText(sb.toString());

        RTextScrollPane scrollPane = new RTextScrollPane(sqlArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("SQL Dry-Run Preview (Editable)"));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Controls
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));

        chkTransaction.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        bottomPanel.add(chkTransaction, BorderLayout.NORTH);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        btnSaveSql.addActionListener(e -> saveSqlToFile());
        actionsPanel.add(btnSaveSql);

        btnExecute.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        btnExecute.setForeground(ThemeManager.getSuccessColor());
        btnExecute.addActionListener(e -> executeReconciliation());
        actionsPanel.add(btnExecute);

        btnClose.addActionListener(e -> dispose());
        actionsPanel.add(btnClose);

        bottomPanel.add(actionsPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void saveSqlToFile() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("sync_" + targetTableName + ".sql"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(fc.getSelectedFile())) {
                fw.write(sqlArea.getText());
                JOptionPane.showMessageDialog(this, "SQL Script saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void executeReconciliation() {
        if (targetDbManager == null || !targetDbManager.isConnected()) {
            JOptionPane.showMessageDialog(this, "Target database is not connected.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String rawSql = sqlArea.getText().trim();
        if (rawSql.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No SQL statements to execute.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to execute these statements against:\n" + targetDbLabel + " (" + targetTableName + ")?",
                "Confirm Synchronization", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        btnExecute.setEnabled(false);
        btnExecute.setText("Executing...");

        new Thread(() -> {
            Connection conn = targetDbManager.connection;
            boolean originalAutoCommit = true;
            boolean useTx = chkTransaction.isSelected();
            int executedCount = 0;

            try {
                if (conn == null || conn.isClosed()) {
                    throw new IllegalStateException("Target connection is closed.");
                }
                originalAutoCommit = conn.getAutoCommit();
                if (useTx) {
                    conn.setAutoCommit(false);
                }

                try (Statement stmt = conn.createStatement()) {
                    String[] statements = rawSql.split(";\\s*(\\r?\\n)+|;\\s*$");
                    for (String s : statements) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                            stmt.execute(trimmed);
                            executedCount++;
                        }
                    }
                }

                if (useTx) {
                    conn.commit();
                }

                final int finalCount = executedCount;
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            String.format("✅ Successfully executed %d statement(s) into %s!\nTarget table '%s' is now synchronized.",
                                    finalCount, targetDbLabel, targetTableName),
                            "Reconciliation Succeeded", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    if (onSuccessCallback != null) {
                        onSuccessCallback.run();
                    }
                });

            } catch (Exception ex) {
                if (useTx && conn != null) {
                    try {
                        conn.rollback();
                    } catch (Exception ignored) {}
                }
                SwingUtilities.invokeLater(() -> {
                    btnExecute.setEnabled(true);
                    btnExecute.setText("▶ Execute Reconciliation");
                    JOptionPane.showMessageDialog(this,
                            "❌ Reconciliation failed with error:\n" + ex.getMessage() +
                                    (useTx ? "\n\nAll changes were safely rolled back." : ""),
                            "Execution Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                if (useTx && conn != null) {
                    try {
                        conn.setAutoCommit(originalAutoCommit);
                    } catch (Exception ignored) {}
                }
            }
        }, "SchemaSync-ReconciliationWorker").start();
    }
}
