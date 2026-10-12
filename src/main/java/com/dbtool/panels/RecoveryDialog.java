package com.dbtool.panels;

import com.dbtool.model.SessionSnapshot;
import com.dbtool.util.SessionRecoveryManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RecoveryDialog extends JDialog {

    public enum RecoveryChoice {
        RESTORE,
        DISCARD,
        CANCEL
    }

    private RecoveryChoice choice = RecoveryChoice.CANCEL;

    public RecoveryDialog(Frame parent, SessionSnapshot snapshot) {
        super(parent, "Crash Recovery - Unsaved Session Detected", true);
        setLayout(new BorderLayout(12, 12));
        setSize(560, 480);
        setLocationRelativeTo(parent);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        JLabel iconLabel = new JLabel("⚠️");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 36));
        headerPanel.add(iconLabel, BorderLayout.WEST);

        JPanel headerTextP = new JPanel(new GridLayout(2, 1, 2, 2));
        JLabel titleLabel = new JLabel("Unsaved Session Detected");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        
        String dateStr = new SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm:ss a").format(new Date(snapshot.timestamp));
        JLabel subLabel = new JLabel("<html>SchemaSync detected an unexpected shutdown from <b>" + dateStr + "</b>.</html>");
        headerTextP.add(titleLabel);
        headerTextP.add(subLabel);
        headerPanel.add(headerTextP, BorderLayout.CENTER);

        mainPanel.add(headerPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Content details card
        JPanel detailsCard = new JPanel(new GridBagLayout());
        detailsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Recoverable Work Summary"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // DB Connection
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        detailsCard.add(new JLabel("🗄️ Database:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        String dbDesc = "None";
        if (snapshot.connection != null) {
            if ("SANDBOX".equalsIgnoreCase(snapshot.connection.type)) {
                dbDesc = "⚡ Sample Sandbox DB (In-Memory)";
            } else if ("POSTGRES".equalsIgnoreCase(snapshot.connection.type)) {
                dbDesc = snapshot.connection.dbName + " (" + snapshot.connection.host + ":" + snapshot.connection.port + ")";
            } else if (snapshot.connection.filePath != null && !snapshot.connection.filePath.isEmpty()) {
                dbDesc = new File(snapshot.connection.filePath).getName();
            }
        }
        detailsCard.add(new JLabel("<html><b>" + dbDesc + "</b></html>"), gbc);
        row++;

        // SQL Editor
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        detailsCard.add(new JLabel("📝 SQL Editor:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        String sqlPreview = "Empty";
        if (snapshot.sqlEditor != null && snapshot.sqlEditor.queryText != null && !snapshot.sqlEditor.queryText.trim().isEmpty()) {
            int lines = snapshot.sqlEditor.queryText.split("\r\n|\r|\n").length;
            sqlPreview = lines + " line(s) of unsaved SQL code";
        }
        detailsCard.add(new JLabel(sqlPreview), gbc);
        row++;

        // Browse Tabs
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        detailsCard.add(new JLabel("🔍 Browse & Search:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        int browseCount = snapshot.browseTabs != null ? snapshot.browseTabs.size() : 0;
        detailsCard.add(new JLabel(browseCount + " tab(s) with custom filters and columns"), gbc);
        row++;

        // Visual Join Tabs
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        detailsCard.add(new JLabel("🔗 Visual Joins:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        int joinCount = snapshot.joinTabs != null ? snapshot.joinTabs.size() : 0;
        detailsCard.add(new JLabel(joinCount + " tab(s) with visual join queries and conditions"), gbc);
        row++;

        // Compare Queries
        if (snapshot.tableCompare != null && ((snapshot.tableCompare.queryA != null && !snapshot.tableCompare.queryA.isEmpty()) || (snapshot.tableCompare.queryB != null && !snapshot.tableCompare.queryB.isEmpty()))) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
            detailsCard.add(new JLabel("⚖️ Query Compare:"), gbc);
            gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
            detailsCard.add(new JLabel("Query A & Query B statements preserved"), gbc);
            row++;
        }

        mainPanel.add(detailsCard);
        mainPanel.add(Box.createVerticalStrut(10));

        // SQL Snippet preview area if available
        if (snapshot.sqlEditor != null && snapshot.sqlEditor.queryText != null && !snapshot.sqlEditor.queryText.trim().isEmpty()) {
            JPanel snippetP = new JPanel(new BorderLayout(4, 4));
            snippetP.setBorder(BorderFactory.createTitledBorder("SQL Draft Preview"));
            JTextArea previewArea = new JTextArea(snapshot.sqlEditor.queryText);
            previewArea.setRows(5);
            previewArea.setEditable(false);
            previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            snippetP.add(new JScrollPane(previewArea), BorderLayout.CENTER);
            mainPanel.add(snippetP);
        }

        // Bottom Actions
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

        JButton exportBtn = new JButton("💾 Export to File...");
        exportBtn.setToolTipText("Save this recovery snapshot to a file on your computer");
        exportBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("recovered_draft.sql"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File f = fc.getSelectedFile();
                    String content = snapshot.sqlEditor != null ? snapshot.sqlEditor.queryText : "";
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        fos.write(content.getBytes(StandardCharsets.UTF_8));
                    }
                    JOptionPane.showMessageDialog(this, "Saved draft to " + f.getAbsolutePath());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to save file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton discardBtn = new JButton("Discard");
        discardBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to discard this unsaved session? It will be archived and removed.",
                    "Confirm Discard", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                choice = RecoveryChoice.DISCARD;
                SessionRecoveryManager.discardCrashedSession();
                dispose();
            }
        });

        JButton restoreBtn = new JButton("⚡ Restore Workspace");
        restoreBtn.setFont(restoreBtn.getFont().deriveFont(Font.BOLD));
        restoreBtn.setForeground(new Color(0, 140, 0));
        restoreBtn.addActionListener(e -> {
            choice = RecoveryChoice.RESTORE;
            dispose();
        });

        bottomBar.add(exportBtn);
        bottomBar.add(discardBtn);
        bottomBar.add(restoreBtn);

        add(mainPanel, BorderLayout.CENTER);
        add(bottomBar, BorderLayout.SOUTH);
    }

    public RecoveryChoice getChoice() {
        return choice;
    }
}
