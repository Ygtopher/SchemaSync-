package com.dbtool.panels;

import com.dbtool.DatabaseManager;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ScriptBuilderPanel extends JPanel {

    private final DatabaseManager dbManager;
    private final DefaultListModel<String> stepModel = new DefaultListModel<>();
    private final JList<String> stepList = new JList<>(stepModel);
    private final JTextArea scriptPreview = new JTextArea();
    private final JTextArea resultArea = new JTextArea();
    private final List<String> steps = new ArrayList<>();

    public ScriptBuilderPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // Step types toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(new JLabel("Add Step:"));
        String[] stepTypes = {"SELECT", "INSERT", "UPDATE", "DELETE", "BEGIN", "COMMIT", "Get Sequence", "Set Variable", "Echo", "Custom SQL"};
        for (String type : stepTypes) {
            JButton btn = new JButton(type);
            btn.addActionListener(e -> addStep(type));
            toolbar.add(btn);
        }

        // Step list (left)
        stepList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane stepScroll = new JScrollPane(stepList);
        stepScroll.setPreferredSize(new Dimension(200, 0));
        JPanel stepPanel = new JPanel(new BorderLayout());
        stepPanel.add(new JLabel(" Script Steps"), BorderLayout.NORTH);
        stepPanel.add(stepScroll, BorderLayout.CENTER);
        JPanel stepBtnPanel = new JPanel(new FlowLayout());
        JButton removeStepBtn = new JButton("Remove");
        JButton moveUpBtn = new JButton("↑");
        JButton moveDownBtn = new JButton("↓");
        stepBtnPanel.add(moveUpBtn);
        stepBtnPanel.add(moveDownBtn);
        stepBtnPanel.add(removeStepBtn);
        stepPanel.add(stepBtnPanel, BorderLayout.SOUTH);

        // Script preview (center)
        scriptPreview.setEditable(true);
        scriptPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane previewScroll = new JScrollPane(scriptPreview);

        // Result area (bottom)
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        resultArea.setText("Run results will appear here.");
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setPreferredSize(new Dimension(0, 140));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, previewScroll, resultScroll);
        centerSplit.setResizeWeight(0.7);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stepPanel, centerSplit);
        mainSplit.setResizeWeight(0.22);

        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton generateBtn = new JButton("Generate Script");
        JButton runBtn = new JButton("▶ Run Script");
        JButton saveBtn = new JButton("Save .sql");
        JButton loadBtn = new JButton("Load .sql");
        JButton copySqlBtn = new JButton("📋 Copy SQL");
        JButton clearBtn = new JButton("Clear All");
        
        copySqlBtn.addActionListener(e -> {
            String script = scriptPreview.getText().trim();
            if (!script.isEmpty()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(script), null);
                JOptionPane.showMessageDialog(this, "Script copied to clipboard!");
            } else {
                JOptionPane.showMessageDialog(this, "Script is empty.");
            }
        });
        
        actionPanel.add(clearBtn);
        actionPanel.add(generateBtn);
        actionPanel.add(saveBtn);
        actionPanel.add(loadBtn);
        actionPanel.add(copySqlBtn);
        actionPanel.add(runBtn);

        add(toolbar, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(actionPanel, BorderLayout.SOUTH);

        // Wire up actions
        removeStepBtn.addActionListener(e -> {
            int idx = stepList.getSelectedIndex();
            if (idx >= 0) { steps.remove(idx); stepModel.remove(idx); refreshPreview(); }
        });
        moveUpBtn.addActionListener(e -> {
            int idx = stepList.getSelectedIndex();
            if (idx > 0) { String s = steps.remove(idx); steps.add(idx - 1, s); refreshStepList(); stepList.setSelectedIndex(idx - 1); refreshPreview(); }
        });
        moveDownBtn.addActionListener(e -> {
            int idx = stepList.getSelectedIndex();
            if (idx >= 0 && idx < steps.size() - 1) { String s = steps.remove(idx); steps.add(idx + 1, s); refreshStepList(); stepList.setSelectedIndex(idx + 1); refreshPreview(); }
        });
        generateBtn.addActionListener(e -> refreshPreview());
        runBtn.addActionListener(e -> runScript());
        saveBtn.addActionListener(e -> saveScript());
        loadBtn.addActionListener(e -> loadScript());
        clearBtn.addActionListener(e -> { steps.clear(); stepModel.clear(); scriptPreview.setText(""); resultArea.setText("Run results will appear here."); });

        stepList.addListSelectionListener(e -> {
            int idx = stepList.getSelectedIndex();
            if (idx >= 0) {
                // Allow editing step in a dialog
            }
        });
        // Double-click to edit step
        stepList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = stepList.getSelectedIndex();
                    if (idx >= 0) editStep(idx);
                }
            }
        });
    }

    private void addStep(String type) {
        String sql = getStepTemplate(type);
        String result = JOptionPane.showInputDialog(this, "SQL for step (" + type + "):", sql);
        if (result != null && !result.trim().isEmpty()) {
            steps.add(result.trim());
            stepModel.addElement(getStepPreview(result.trim()));
            refreshPreview();
        }
    }

    private void editStep(int idx) {
        String current = steps.get(idx);
        String result = JOptionPane.showInputDialog(this, "Edit SQL:", current);
        if (result != null && !result.trim().isEmpty()) {
            steps.set(idx, result.trim());
            stepModel.set(idx, getStepPreview(result.trim()));
            refreshPreview();
        }
    }

    private String getStepTemplate(String type) {
        switch (type) {
            case "SELECT": return "SELECT * FROM my_table LIMIT 100;";
            case "INSERT": return "INSERT INTO my_table (col1, col2) VALUES ('val1', 'val2');";
            case "UPDATE": return "UPDATE my_table SET col1 = 'value' WHERE id = 1;";
            case "DELETE": return "DELETE FROM my_table WHERE id = 1;";
            case "DROP TABLE": return "DROP TABLE IF EXISTS my_table;";
            case "BEGIN": return "BEGIN;";
            case "COMMIT": return "COMMIT;";
            case "Get Sequence": return "SELECT nextval('my_seq') AS var_name \\gset";
            case "Set Variable": return "\\set var_name 'value'";
            case "Echo": return "\\echo 'Message: ':var_name";
            default: return "-- Custom SQL\nSELECT 1;";
        }
    }

    private String getStepPreview(String sql) {
        String preview = sql.trim().replace("\n", " ");
        return (preview.length() > 60 ? preview.substring(0, 60) + "..." : preview);
    }

    private void refreshStepList() {
        stepModel.clear();
        for (String s : steps) stepModel.addElement(getStepPreview(s));
    }

    private void refreshPreview() {
        StringBuilder sb = new StringBuilder("-- Generated SQL Script\n\n");
        for (int i = 0; i < steps.size(); i++) {
            sb.append("-- Step ").append(i + 1).append("\n");
            sb.append(steps.get(i));
            if (!steps.get(i).endsWith(";")) sb.append(";");
            sb.append("\n\n");
        }
        scriptPreview.setText(sb.toString());
    }

    private void runScript() {
        if (dbManager.connection == null) {
            JOptionPane.showMessageDialog(this, "No database connected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String script = scriptPreview.getText().trim();
        if (script.isEmpty()) return;
        resultArea.setText("Running script...\n");
        new Thread(() -> {
            StringBuilder result = new StringBuilder();
            java.util.Map<String, String> variables = new java.util.HashMap<>();
            String[] lines = script.split("\n");
            StringBuilder currentStmt = new StringBuilder();
            int pass = 0, fail = 0;
            
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
                
                if (trimmed.startsWith("\\set ")) {
                    String[] parts = trimmed.split("\\s+", 3);
                    if (parts.length >= 3) {
                        String val = parts[2];
                        if (val.startsWith("'") && val.endsWith("'")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        variables.put(parts[1], val);
                    }
                    continue;
                }
                
                if (trimmed.startsWith("\\echo ")) {
                    String msg = trimmed.substring(6).trim();
                    for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
                        msg = msg.replace(":" + entry.getKey(), entry.getValue());
                        msg = msg.replace(":'" + entry.getKey() + "'", "'" + entry.getValue() + "'");
                    }
                    if (msg.startsWith("'") && msg.endsWith("'")) {
                         msg = msg.substring(1, msg.length() - 1);
                    }
                    result.append("ECHO: ").append(msg.replace("'", "")).append("\n");
                    continue;
                }
                
                boolean isGset = trimmed.endsWith("\\gset");
                if (isGset) {
                    trimmed = trimmed.substring(0, trimmed.length() - 5).trim();
                }
                
                currentStmt.append(trimmed).append(" ");
                
                if (trimmed.endsWith(";") || isGset) {
                    String stmt = currentStmt.toString().trim();
                    currentStmt.setLength(0); // clear
                    
                    if (stmt.endsWith(";")) {
                        stmt = stmt.substring(0, stmt.length() - 1).trim();
                    }
                    if (stmt.isEmpty()) continue;
                    
                    try {
                        if (stmt.equalsIgnoreCase("BEGIN")) {
                            dbManager.connection.setAutoCommit(false);
                            result.append("✅ BEGIN (Transaction started)\n");
                            pass++;
                            continue;
                        }
                        if (stmt.equalsIgnoreCase("COMMIT")) {
                            dbManager.connection.commit();
                            dbManager.connection.setAutoCommit(true);
                            result.append("✅ COMMIT (Transaction saved)\n");
                            pass++;
                            continue;
                        }
                        if (stmt.equalsIgnoreCase("ROLLBACK")) {
                            dbManager.connection.rollback();
                            dbManager.connection.setAutoCommit(true);
                            result.append("✅ ROLLBACK (Transaction reverted)\n");
                            pass++;
                            continue;
                        }
                        
                        // replace variables
                        for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
                            stmt = stmt.replace(":'" + entry.getKey() + "'", "'" + entry.getValue().replace("'", "''") + "'");
                            stmt = stmt.replace(":" + entry.getKey(), entry.getValue());
                        }
                        
                        java.sql.Statement sqlStmt = dbManager.connection.createStatement();
                        boolean hasResultSet = sqlStmt.execute(stmt);
                        if (hasResultSet) {
                            java.sql.ResultSet rs = sqlStmt.getResultSet();
                            if (isGset) {
                                if (rs.next()) {
                                    java.sql.ResultSetMetaData md = rs.getMetaData();
                                    for (int i = 1; i <= md.getColumnCount(); i++) {
                                        variables.put(md.getColumnName(i), rs.getString(i));
                                    }
                                }
                                result.append("✅ \\gset mapped columns to variables\n");
                            } else {
                                int rows = 0;
                                while (rs.next()) rows++;
                                result.append("✅ ").append(getStepPreview(stmt)).append(" → ").append(rows).append(" rows\n");
                            }
                        } else {
                            int affected = sqlStmt.getUpdateCount();
                            result.append("✅ ").append(getStepPreview(stmt)).append(" → ").append(affected).append(" rows affected\n");
                        }
                        pass++;
                    } catch (Exception ex) {
                        result.append("❌ ").append(getStepPreview(stmt)).append(" → ").append(ex.getMessage()).append("\n");
                        fail++;
                        try {
                            if (!dbManager.connection.getAutoCommit()) {
                                dbManager.connection.rollback();
                                dbManager.connection.setAutoCommit(true);
                                result.append("⚠️ Auto-ROLLBACK executed due to failure.\n");
                            }
                        } catch (java.sql.SQLException e) {}
                        break; // stop executing rest of script on error
                    }
                }
            }
            try {
                if (!dbManager.connection.getAutoCommit()) {
                    dbManager.connection.rollback();
                    dbManager.connection.setAutoCommit(true);
                    result.append("\n⚠️ Uncommitted transaction rolled back at script end.");
                }
            } catch (java.sql.SQLException e) {}
            
            result.append("\nDone: ").append(pass).append(" passed, ").append(fail).append(" failed.");
            SwingUtilities.invokeLater(() -> resultArea.setText(result.toString()));
        }).start();
    }

    private void saveScript() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("script.sql"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            pw.print(scriptPreview.getText());
            JOptionPane.showMessageDialog(this, "Saved to " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadScript() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String content = new String(java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath()), StandardCharsets.UTF_8);
            scriptPreview.setText(content);
            // Parse steps from SQL
            steps.clear();
            stepModel.clear();
            for (String stmt : content.split(";")) {
                String s = stmt.trim().replaceAll("--.*\\n", "").trim();
                if (!s.isEmpty()) { steps.add(s); stepModel.addElement(getStepPreview(s)); }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
