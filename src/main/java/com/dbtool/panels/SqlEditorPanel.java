package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ExportUtil;
import com.dbtool.util.QueryHistory;
import com.dbtool.util.SavedQueries;
import com.dbtool.util.SqlSnippetsLibrary;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlEditorPanel extends JPanel {

    private final DatabaseManager dbManager;
    private final RSyntaxTextArea editor;
    private final JTabbedPane resultTabs = new JTabbedPane();
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private final JLabel statusLabel = new JLabel("Ready.");
    private final javax.swing.JProgressBar progressBar = new javax.swing.JProgressBar();
    private final JComboBox<String> savedQueriesDropdown = new JComboBox<>();
    private java.util.function.Consumer<String> sendToVisualBuilderAction;
    private Runnable onSchemaModified;

    public void setOnSchemaModified(Runnable action) {
        this.onSchemaModified = action;
    }

    public void setSendToVisualBuilderAction(java.util.function.Consumer<String> action) {
        this.sendToVisualBuilderAction = action;
    }

    public SqlEditorPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // SQL Editor
        editor = new RSyntaxTextArea(12, 80);
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        editor.setCodeFoldingEnabled(true);
        editor.setAntiAliasingEnabled(true);
        editor.setText("-- Write your SQL here\nSELECT 1;");
        RTextScrollPane editorScroll = new RTextScrollPane(editor);
        editorScroll.setLineNumbersEnabled(true);

        // Right-click context menu integration
        JPopupMenu editorPopup = editor.getPopupMenu();
        if (editorPopup != null) {
            editorPopup.addSeparator();
            JMenuItem cheatSheetItem = new JMenuItem("📚 SQL Mastery Reference Library...");
            cheatSheetItem.addActionListener(e -> openMasteryCheatSheet());
            editorPopup.add(cheatSheetItem);

            JMenu templatesSubMenu = new JMenu("📝 Insert SQL Template");
            SqlSnippetsLibrary.populateSubMenu(templatesSubMenu, this::insertSnippet);
            editorPopup.add(templatesSubMenu);
        }

        resultTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        resultTabs.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");

        // Toolbar
        JPanel toolbar = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));
        JButton runBtn = new JButton("▶ Run");
        runBtn.setToolTipText("Run current statement or selection (Ctrl+Enter / F5)");
        JButton runAllBtn = new JButton("▶▶ Run All");
        runAllBtn.setToolTipText("Run all statements in the editor (Ctrl+Shift+Enter)");
        JButton explainBtn = new JButton("⚡ Explain");
        JButton toJoinBtn = new JButton("🔀 To Visual Join");
        toJoinBtn.setToolTipText("Send current query or selection to Visual Join Builder");
        toJoinBtn.addActionListener(e -> {
            String sql = getCurrentSql();
            if (sql == null || sql.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "No query found at cursor or selected.", "Notice", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (sendToVisualBuilderAction != null) {
                sendToVisualBuilderAction.accept(sql);
            }
        });
        JButton snippetsBtn = new JButton("📝 SQL Templates ▾");
        snippetsBtn.setToolTipText("Insert SQL templates and query patterns (Core Foundations & Advanced SQL Mastery)");
        snippetsBtn.addActionListener(e -> showSnippetsMenu(snippetsBtn));
        
        JButton sandboxBtn = new JButton("⚡ Sandbox DB");
        sandboxBtn.setToolTipText("Load built-in in-memory sandbox with sample employees & departments data");
        sandboxBtn.addActionListener(e -> {
            try {
                dbManager.connectSandbox();
                statusLabel.setText("Sandbox Database loaded.");
                JOptionPane.showMessageDialog(this, "Sandbox Database loaded with sample employees, departments, and sales tables!", "Sandbox Ready", JOptionPane.INFORMATION_MESSAGE);
                if (onSchemaModified != null) onSchemaModified.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load sandbox: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton seedBtn = new JButton("🌱 Seed Sample Data");
        seedBtn.setToolTipText("Create & populate sample employees and departments in the active database");
        seedBtn.addActionListener(e -> {
            if (dbManager.connection == null) {
                JOptionPane.showMessageDialog(this, "Please connect to a database or use Sandbox DB first.", "Notice", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                dbManager.seedSampleSchema();
                statusLabel.setText("Sample tables created and seeded.");
                JOptionPane.showMessageDialog(this, "Sample tables (employees, departments, contractors, orders, sales, accounts) created and populated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                if (onSchemaModified != null) onSchemaModified.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to seed sample tables: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton clearBtn = new JButton("Clear");
        JButton saveQueryBtn = new JButton("Save Query");
        JButton loadQueryBtn = new JButton("Load Query");
        JButton loadSqlFileBtn = new JButton("📂 Load SQL File");
        JButton toggleHistoryBtn = new JButton("☰");
        toggleHistoryBtn.setToolTipText("Toggle Query History");
        JButton exportCsvBtn = new JButton("Export CSV");
        JButton exportXlsxBtn = new JButton("Export XLSX");
        JButton exportPdfBtn = new JButton("Export PDF");
        
        JCheckBox autoCommitCheck = new JCheckBox("Auto-Commit", true);
        JButton commitBtn = new JButton("Commit");
        JButton rollbackBtn = new JButton("Rollback");
        commitBtn.setEnabled(false);
        rollbackBtn.setEnabled(false);
        
        autoCommitCheck.addActionListener(e -> {
            boolean ac = autoCommitCheck.isSelected();
            try {
                if (dbManager.connection != null) dbManager.connection.setAutoCommit(ac);
                commitBtn.setEnabled(!ac);
                rollbackBtn.setEnabled(!ac);
            } catch (Exception ex) {}
        });
        commitBtn.addActionListener(e -> {
            try { if (dbManager.connection != null) dbManager.connection.commit(); statusLabel.setText("Transaction committed."); } catch (Exception ex) {}
        });
        rollbackBtn.addActionListener(e -> {
            try { if (dbManager.connection != null) dbManager.connection.rollback(); statusLabel.setText("Transaction rolled back."); } catch (Exception ex) {}
        });

        savedQueriesDropdown.setPrototypeDisplayValue("Select saved query...");

        toolbar.add(runBtn);
        toolbar.add(runAllBtn);
        toolbar.add(explainBtn);
        toolbar.add(toJoinBtn);
        toolbar.add(snippetsBtn);
        toolbar.add(sandboxBtn);
        toolbar.add(seedBtn);
        toolbar.add(clearBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(autoCommitCheck);
        toolbar.add(commitBtn);
        toolbar.add(rollbackBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(saveQueryBtn);
        toolbar.add(savedQueriesDropdown);
        toolbar.add(loadQueryBtn);
        toolbar.add(loadSqlFileBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(exportCsvBtn);
        toolbar.add(exportPdfBtn);
        toolbar.add(exportXlsxBtn);

        JPanel toolbarWrapper = new JPanel(new BorderLayout());
        toolbarWrapper.add(toolbar, BorderLayout.CENTER);
        
        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightToolbar.add(toggleHistoryBtn);
        toolbarWrapper.add(rightToolbar, BorderLayout.EAST);

        // Editor + toolbar top section
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.add(toolbarWrapper, BorderLayout.NORTH);
        topSection.add(editorScroll, BorderLayout.CENTER);
        
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        JPanel statusPanel = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));
        statusPanel.add(statusLabel);
        statusPanel.add(progressBar);
        topSection.add(statusPanel, BorderLayout.SOUTH);


        // History sidebar
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setPreferredSize(new Dimension(220, 0));
        JPanel historyPanel = new JPanel(new BorderLayout());
        JLabel histLabel = new JLabel(" Query History");
        histLabel.setFont(histLabel.getFont().deriveFont(Font.BOLD));
        JButton clearHistBtn = new JButton("Clear History");
        historyPanel.add(histLabel, BorderLayout.NORTH);
        historyPanel.add(historyScroll, BorderLayout.CENTER);
        historyPanel.add(clearHistBtn, BorderLayout.SOUTH);

        // Left split: editor top, results bottom
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSection, resultTabs);
        vertSplit.setResizeWeight(0.45);

        // Main split: vertSplit left, history right
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, vertSplit, historyPanel);
        mainSplit.setResizeWeight(0.82);

        add(mainSplit, BorderLayout.CENTER);

        // Actions
        runBtn.addActionListener(e -> executeQuery(getCurrentSql(), false));
        runAllBtn.addActionListener(e -> {
            String text = editor.getText().trim();
            if (!text.isEmpty()) executeQuery(text, false);
        });
        explainBtn.addActionListener(e -> {
            String sql = getCurrentSql();
            if (!sql.isEmpty()) {
                String toRun = sql.toUpperCase().startsWith("EXPLAIN") ? sql : "EXPLAIN ANALYZE " + sql;
                executeQuery(toRun, true);
            }
        });

        // Developer keyboard shortcuts (Ctrl+Enter / F5 = Run, Ctrl+Shift+Enter = Run All)
        editor.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), "runCurrent");
        editor.getActionMap().put("runCurrent", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                executeQuery(getCurrentSql(), false);
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "runAll");
        editor.getActionMap().put("runAll", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                String text = editor.getText().trim();
                if (!text.isEmpty()) executeQuery(text, false);
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0), "runCurrentF5");
        editor.getActionMap().put("runCurrentF5", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                executeQuery(getCurrentSql(), false);
            }
        });

        clearBtn.addActionListener(e -> editor.setText(""));
        saveQueryBtn.addActionListener(e -> saveCurrentQuery());
        loadSqlFileBtn.addActionListener(e -> {
            javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
            if (fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    editor.setText(content);
                } catch (Exception ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, "Failed to read file: " + ex.getMessage(), "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        loadQueryBtn.addActionListener(e -> {
            String sel = (String) savedQueriesDropdown.getSelectedItem();
            if (sel != null) {
                Map<String, String> sq = SavedQueries.load();
                if (sq.containsKey(sel)) editor.setText(sq.get(sel));
            }
        });
        clearHistBtn.addActionListener(e -> { QueryHistory.clear(); historyModel.clear(); });
        toggleHistoryBtn.addActionListener(e -> {
            boolean isVisible = historyPanel.isVisible();
            historyPanel.setVisible(!isVisible);
            if (!isVisible) {
                mainSplit.setDividerLocation(0.82);
            }
        });
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isConsumed() && e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    String sel = historyList.getSelectedValue();
                    if (sel != null) editor.setText(sel);
                }
            }
            private void showPopup(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int idx = historyList.locationToIndex(e.getPoint());
                if (idx != -1) {
                    historyList.setSelectedIndex(idx);
                    javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
                    javax.swing.JMenuItem delItem = new javax.swing.JMenuItem("Delete");
                    delItem.addActionListener(evt -> {
                        String sel = historyList.getSelectedValue();
                        if (sel != null) {
                            QueryHistory.remove(sel);
                            refreshHistory();
                        }
                    });
                    popup.add(delItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) { showPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { showPopup(e); }
        });

        historyList.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE || e.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    String sel = historyList.getSelectedValue();
                    if (sel != null) {
                        QueryHistory.remove(sel);
                        refreshHistory();
                    }
                }
            }
        });

        exportCsvBtn.addActionListener(e -> {
            JTable t = getActiveResultTable();
            if (t != null) ExportUtil.exportCsv(t, this);
        });
        exportXlsxBtn.addActionListener(e -> {
            JTable t = getActiveResultTable();
            if (t != null) ExportUtil.exportExcel(t, this);
        });
        exportPdfBtn.addActionListener(e -> {
            JTable t = getActiveResultTable();
            if (t != null) ExportUtil.exportPdf(t, this);
        });

        refreshHistory();
        refreshSavedQueries();
    }

    public static class SqlRange {
        public int start;
        public int end;
        public String sql;
        public SqlRange(int start, int end, String sql) {
            this.start = start;
            this.end = end;
            this.sql = sql;
        }
    }

    public static List<SqlRange> extractStatements(String text) {
        List<SqlRange> statements = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return statements;

        int len = text.length();
        int stmtStart = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inDollarQuote = false;
        String dollarTag = "";

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            char next = (i + 1 < len) ? text.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inSingleQuote) {
                if (c == '\'') {
                    if (next == '\'') {
                        i++; // escaped quote ''
                    } else {
                        inSingleQuote = false;
                    }
                }
                continue;
            }
            if (inDoubleQuote) {
                if (c == '"') {
                    if (next == '"') {
                        i++; // escaped quote ""
                    } else {
                        inDoubleQuote = false;
                    }
                }
                continue;
            }
            if (inDollarQuote) {
                if (c == '$' && text.startsWith(dollarTag, i)) {
                    inDollarQuote = false;
                    i += dollarTag.length() - 1;
                }
                continue;
            }

            // Check start of comments
            if (c == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }

            // Check start of single or double quotes
            if (c == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (c == '"') {
                inDoubleQuote = true;
                continue;
            }

            // Check start of PostgreSQL dollar quotes: $$ or $tag$
            if (c == '$') {
                int closingDollar = text.indexOf('$', i + 1);
                if (closingDollar != -1) {
                    String tag = text.substring(i, closingDollar + 1);
                    boolean validTag = true;
                    for (int t = 1; t < tag.length() - 1; t++) {
                        char tc = tag.charAt(t);
                        if (!Character.isLetterOrDigit(tc) && tc != '_') {
                            validTag = false;
                            break;
                        }
                    }
                    if (validTag) {
                        inDollarQuote = true;
                        dollarTag = tag;
                        i = closingDollar;
                        continue;
                    }
                }
            }

            // Check statement terminator
            if (c == ';') {
                String sub = text.substring(stmtStart, i).trim();
                if (!sub.isEmpty()) {
                    statements.add(new SqlRange(stmtStart, i + 1, sub));
                }
                stmtStart = i + 1;
            }
        }

        // Remaining trailing statement without semicolon
        if (stmtStart < len) {
            String sub = text.substring(stmtStart, len).trim();
            if (!sub.isEmpty()) {
                statements.add(new SqlRange(stmtStart, len, sub));
            }
        }

        return statements;
    }

    private String getCurrentSql() {
        String selected = editor.getSelectedText();
        if (selected != null && !selected.trim().isEmpty()) {
            return selected.trim();
        }
        String text = editor.getText();
        if (text == null || text.trim().isEmpty()) return "";

        List<SqlRange> statements = extractStatements(text);
        if (statements.isEmpty()) return text.trim();

        int caret = editor.getCaretPosition();
        if (caret > text.length()) caret = text.length();

        // 1. Check if caret is directly inside a statement
        for (SqlRange sr : statements) {
            if (caret >= sr.start && caret <= sr.end) {
                return sr.sql;
            }
        }

        // 2. Otherwise find the closest statement before the caret
        SqlRange closestBefore = null;
        for (SqlRange sr : statements) {
            if (sr.end <= caret) {
                closestBefore = sr;
            } else {
                break;
            }
        }
        if (closestBefore != null) {
            return closestBefore.sql;
        }

        // 3. Fallback: return the first statement
        return statements.get(0).sql;
    }

    private String getStatementKind(String sql) {
        if (sql == null) return "Statement";
        String trimmed = sql.trim();
        while (trimmed.startsWith("--") || trimmed.startsWith("/*")) {
            if (trimmed.startsWith("--")) {
                int nl = trimmed.indexOf('\n');
                if (nl == -1) return "Statement";
                trimmed = trimmed.substring(nl + 1).trim();
            } else if (trimmed.startsWith("/*")) {
                int end = trimmed.indexOf("*/");
                if (end == -1) return "Statement";
                trimmed = trimmed.substring(end + 2).trim();
            }
        }
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("SELECT") || upper.startsWith("WITH")) return "Query";
        if (upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE") || upper.startsWith("MERGE")) return "DML";
        if (upper.startsWith("CREATE") || upper.startsWith("ALTER") || upper.startsWith("DROP") || upper.startsWith("TRUNCATE") || upper.startsWith("RENAME")) return "DDL";
        if (upper.startsWith("GRANT") || upper.startsWith("REVOKE")) return "DCL";
        if (upper.startsWith("BEGIN") || upper.startsWith("COMMIT") || upper.startsWith("ROLLBACK") || upper.startsWith("SAVEPOINT") || upper.startsWith("SET TRANSACTION")) return "TCL";
        if (upper.startsWith("EXPLAIN")) return "Explain";
        if (upper.startsWith("SHOW") || upper.startsWith("DESCRIBE")) return "Meta";
        if (upper.startsWith("CALL") || upper.startsWith("DO")) return "Procedure";
        return "Statement";
    }

    public static String translateCompatibilitySql(String sql, DatabaseManager dbManager) {
        if (sql == null) return "";
        String trimmed = sql.trim();
        while (trimmed.startsWith("--") || trimmed.startsWith("/*")) {
            if (trimmed.startsWith("--")) {
                int nl = trimmed.indexOf('\n');
                if (nl == -1) break;
                trimmed = trimmed.substring(nl + 1).trim();
            } else if (trimmed.startsWith("/*")) {
                int end = trimmed.indexOf("*/");
                if (end == -1) break;
                trimmed = trimmed.substring(end + 2).trim();
            }
        }
        String clean = trimmed.replaceAll(";+$", "").trim();
        String upper = clean.toUpperCase();

        boolean isPgOrH2 = dbManager.isPostgres() || dbManager.isH2();

        if (isPgOrH2) {
            // SHOW TABLES -> PostgreSQL information_schema
            if (upper.equals("SHOW TABLES")) {
                return "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;";
            }
            // SHOW DATABASES -> pg_database
            if (upper.equals("SHOW DATABASES")) {
                return "SELECT datname AS database_name FROM pg_database WHERE datistemplate = false ORDER BY datname;";
            }
            // DESCRIBE / DESC <table>
            if (upper.startsWith("DESCRIBE ") || upper.startsWith("DESC ")) {
                String[] parts = clean.split("\\s+", 2);
                if (parts.length > 1) {
                    String tbl = parts[1].trim().replace(";", "").replace("\"", "").replace("'", "").toLowerCase();
                    return "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_name = '" + tbl + "' ORDER BY ordinal_position;";
                }
            }
            // USE <dbname>
            if (upper.startsWith("USE ")) {
                String[] parts = clean.split("\\s+", 2);
                if (parts.length > 1) {
                    String sch = parts[1].trim().replace(";", "").replace("\"", "").replace("'", "");
                    return "SET search_path TO " + sch + ", public;";
                }
            }
            // AUTO_INCREMENT in CREATE TABLE
            if (upper.contains("AUTO_INCREMENT") && upper.startsWith("CREATE TABLE")) {
                sql = sql.replaceAll("(?i)\\bINT\\s+PRIMARY\\s+KEY\\s+AUTO_INCREMENT\\b", "SERIAL PRIMARY KEY")
                         .replaceAll("(?i)\\bINT\\s+AUTO_INCREMENT\\s+PRIMARY\\s+KEY\\b", "SERIAL PRIMARY KEY")
                         .replaceAll("(?i)\\bAUTO_INCREMENT\\b", "");
                upper = sql.toUpperCase();
            }

            // SQL Server PIVOT syntax -> ANSI SQL Conditional Aggregation
            if (upper.contains(" PIVOT") || upper.contains("\nPIVOT")) {
                Pattern pivotPat = Pattern.compile("(?is)^\\s*SELECT\\s+.*?\\s+FROM\\s+([a-zA-Z0-9_]+)\\s+PIVOT\\s*\\(\\s*([a-zA-Z0-9_]+)\\s*\\(\\s*([a-zA-Z0-9_]+)\\s*\\)\\s+FOR\\s+([a-zA-Z0-9_]+)\\s+IN\\s*\\(([^)]+)\\)\\s*\\)(?:\\s+AS\\s+[a-zA-Z0-9_]+)?;?\\s*$");
                Matcher pm = pivotPat.matcher(sql);
                if (pm.find()) {
                    String tbl = pm.group(1);
                    String aggFunc = pm.group(2);
                    String aggCol = pm.group(3);
                    String pivotCol = pm.group(4);
                    String inList = pm.group(5);

                    String[] items = inList.split(",");
                    StringBuilder sb = new StringBuilder("SELECT ");
                    for (int i = 0; i < items.length; i++) {
                        String item = items[i].trim().replaceAll("[\\[\\]'\"`]", "");
                        if (i > 0) sb.append(", ");
                        sb.append(aggFunc).append("(CASE WHEN ").append(pivotCol).append(" = '").append(item).append("' THEN ").append(aggCol).append(" ELSE 0 END) AS ").append(item);
                    }
                    sb.append(" FROM ").append(tbl);
                    sql = sb.toString();
                    upper = sql.toUpperCase();
                }
            }
        }

        // H2 in-memory sandbox enhancements for advanced constructs
        if (dbManager.isH2()) {
            if (upper.contains("FULL OUTER JOIN") || upper.contains("FULL JOIN")) {
                Pattern foj = Pattern.compile("(?is)^\\s*SELECT\\s+(.+?)\\s+FROM\\s+(.+?)\\s+FULL\\s+(?:OUTER\\s+)?JOIN\\s+(.+?)\\s+ON\\s+(.+?)(?:(\\s+(?:WHERE|GROUP|HAVING|ORDER|LIMIT).*)|;?\\s*)$");
                Matcher m = foj.matcher(sql);
                if (m.find()) {
                    String cols = m.group(1).trim();
                    String t1 = m.group(2).trim();
                    String t2 = m.group(3).trim();
                    String onCond = m.group(4).trim();
                    String tail = m.group(5) != null ? m.group(5).trim() : "";
                    String left = "SELECT " + cols + " FROM " + t1 + " LEFT JOIN " + t2 + " ON " + onCond + (tail.isEmpty() ? "" : " " + tail);
                    String right = "SELECT " + cols + " FROM " + t1 + " RIGHT JOIN " + t2 + " ON " + onCond + (tail.isEmpty() ? "" : " " + tail);
                    sql = left + " UNION " + right;
                    upper = sql.toUpperCase();
                }
            }

            if (upper.contains("GROUP BY ROLLUP") || upper.contains("GROUP BY CUBE") || upper.contains("GROUP BY GROUPING SETS")) {
                sql = sql.replaceAll("(?i)GROUP\\s+BY\\s+ROLLUP\\s*\\(([^)]+)\\)", "GROUP BY $1")
                         .replaceAll("(?i)GROUP\\s+BY\\s+CUBE\\s*\\(([^)]+)\\)", "GROUP BY $1");
                if (sql.toUpperCase().contains("GROUP BY GROUPING SETS")) {
                    Pattern gs = Pattern.compile("(?i)GROUP\\s+BY\\s+GROUPING\\s+SETS\\s*\\((.+?)\\)(?=\\s+(?:HAVING|ORDER|LIMIT)|;|\\s*$)", Pattern.DOTALL);
                    Matcher m = gs.matcher(sql);
                    if (m.find()) {
                        String inner = m.group(1);
                        String cleanInner = inner.replaceAll("[\\(\\)]", " ").trim();
                        String[] tokens = cleanInner.split("[,\\s]+");
                        Set<String> cols = new LinkedHashSet<>();
                        for (String t : tokens) {
                            if (!t.isEmpty()) cols.add(t);
                        }
                        String repl = "GROUP BY " + (cols.isEmpty() ? "1" : String.join(", ", cols));
                        sql = sql.substring(0, m.start()) + repl + sql.substring(m.end());
                    }
                }
            }
            if (upper.contains("WITH RECURSIVE ORG_CHART AS")) {
                sql = sql.replaceAll("(?i)WITH\\s+RECURSIVE\\s+org_chart\\s+AS", "WITH RECURSIVE org_chart(id, name, manager_id, level) AS");
            }

            if (upper.contains("ON CONFLICT")) {
                Pattern onConf = Pattern.compile("(?is)^\\s*INSERT\\s+INTO\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)\\s*ON\\s+CONFLICT\\s*\\(([^)]+)\\)\\s*DO\\s+UPDATE\\s+SET\\s+.*$", Pattern.DOTALL);
                Matcher m = onConf.matcher(sql);
                if (m.find()) {
                    String tbl = m.group(1);
                    String cols = m.group(2);
                    String vals = m.group(3);
                    String key = m.group(4);
                    sql = "MERGE INTO " + tbl + " (" + cols + ") KEY(" + key + ") VALUES (" + vals + ")";
                    upper = sql.toUpperCase();
                }
            }
        }

        return sql;
    }

    private void executeQuery(String sql, boolean isExplain) {
        if (sql == null || sql.trim().isEmpty()) return;

        // Auto-connect Sandbox if no database is connected
        if (dbManager.connection == null) {
            progressBar.setVisible(false);
            int opt = JOptionPane.showConfirmDialog(this,
                    "No database is currently connected.\n\n" +
                    "Would you like to connect to the built-in Sandbox Database?\n" +
                    "(Pre-loaded with sample Employees, Departments, Contractors, Orders, and Sales)",
                    "Connect Sandbox Database?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (opt == JOptionPane.YES_OPTION) {
                try {
                    dbManager.connectSandbox();
                    statusLabel.setText("Sandbox Database loaded.");
                    if (onSchemaModified != null) SwingUtilities.invokeLater(onSchemaModified);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to initialize sandbox: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                return;
            }
        }

        statusLabel.setText("Running...");
        progressBar.setVisible(true);

        new Thread(() -> {
            long totalStart = System.currentTimeMillis();
            List<SqlRange> statements = extractStatements(sql);
            if (statements.isEmpty()) {
                statements.add(new SqlRange(0, sql.length(), sql.trim()));
            }

            int executedCount = 0;
            boolean hadDdl = false;

            for (int i = 0; i < statements.size(); i++) {
                String rawStmt = statements.get(i).sql.trim();
                if (rawStmt.isEmpty() || rawStmt.startsWith("--") || (rawStmt.startsWith("/*") && rawStmt.endsWith("*/"))) {
                    continue;
                }

                String queryToRun = translateCompatibilitySql(rawStmt, dbManager);
                String kind = getStatementKind(rawStmt);
                if ("DDL".equals(kind)) hadDdl = true;
                int stmtNum = i + 1;
                long stmtStart = System.currentTimeMillis();

                try {
                    if (isExplain || "Explain".equals(kind)) {
                        String explainSql = queryToRun;
                        if (!explainSql.toUpperCase().startsWith("EXPLAIN")) {
                            explainSql = "EXPLAIN " + explainSql;
                        }
                        try (java.sql.Statement s = dbManager.connection.createStatement();
                             java.sql.ResultSet rs = s.executeQuery(explainSql)) {
                            StringBuilder sb = new StringBuilder();
                            while (rs.next()) sb.append(rs.getString(1)).append("\n");
                            String plan = sb.toString();
                            SwingUtilities.invokeLater(() -> {
                                JTextArea ta = new JTextArea(plan);
                                ta.setEditable(false);
                                ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                                addResultTab("Explain " + (statements.size() > 1 ? "#" + stmtNum : ""), new JScrollPane(ta));
                            });
                        }
                    } else {
                        java.sql.Statement stmt = dbManager.connection.createStatement();
                        boolean hasRs = stmt.execute(queryToRun);
                        long stmtElapsed = System.currentTimeMillis() - stmtStart;

                        if (hasRs) {
                            try (java.sql.ResultSet rs = stmt.getResultSet()) {
                                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                                java.util.Vector<String> columnNames = new java.util.Vector<>();
                                int columnCount = metaData.getColumnCount();
                                String[] columnTypes = new String[columnCount];
                                for (int col = 1; col <= columnCount; col++) {
                                    columnNames.add(metaData.getColumnLabel(col));
                                    columnTypes[col - 1] = metaData.getColumnTypeName(col);
                                }
                                java.util.Vector<java.util.Vector<Object>> data = new java.util.Vector<>();
                                while (rs.next()) {
                                    java.util.Vector<Object> vector = new java.util.Vector<>();
                                    for (int col = 1; col <= columnCount; col++) {
                                        vector.add(rs.getObject(col));
                                    }
                                    data.add(vector);
                                }
                                com.dbtool.util.TypedTableModel model = new com.dbtool.util.TypedTableModel(data, columnNames, columnTypes);
                                String tabTitle = (statements.size() > 1 ? kind + " #" + stmtNum : kind) + " (" + model.getRowCount() + " rows)";
                                SwingUtilities.invokeLater(() -> {
                                    JTable table = new JTable(model);
                                    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                                    table.setAutoCreateRowSorter(true);
                                    com.dbtool.util.TableTooltipUtil.attachHeaderTooltips(table);
                                    JScrollPane scrollPane = new JScrollPane(table);
                                    com.dbtool.util.TableRowUtilities.addRowNumbers(table, scrollPane);
                                    addResultTab(tabTitle, scrollPane);
                                });
                            }
                        } else {
                            int affected = stmt.getUpdateCount();
                            String tabTitle = (statements.size() > 1 ? kind + " #" + stmtNum : kind + " Result");
                            String msg = ("DDL".equals(kind) || "DCL".equals(kind) || "TCL".equals(kind))
                                    ? kind + " statement executed successfully." + (affected > 0 ? " (" + affected + " rows)" : "") + " [" + stmtElapsed + "ms]"
                                    : (affected >= 0 ? affected + " row(s) affected. [" + stmtElapsed + "ms]" : kind + " executed successfully. [" + stmtElapsed + "ms]");
                            SwingUtilities.invokeLater(() -> {
                                addResultTab(tabTitle, createTextTab(msg));
                            });
                        }
                    }
                    executedCount++;
                    com.dbtool.util.QueryLogger.log(rawStmt, System.currentTimeMillis() - stmtStart, true);
                } catch (Exception ex) {
                    com.dbtool.util.QueryLogger.log(rawStmt, System.currentTimeMillis() - stmtStart, false);
                    final String errStmt = rawStmt;
                    SwingUtilities.invokeLater(() -> {
                        addResultTab("Error " + (statements.size() > 1 ? "#" + stmtNum : ""),
                                createTextTab("Statement:\n" + errStmt + "\n\nError:\n" + ex.getMessage()));
                    });
                }
            }

            if (hadDdl) {
                dbManager.clearCache();
                if (onSchemaModified != null) {
                    SwingUtilities.invokeLater(onSchemaModified);
                }
            }

            long totalElapsed = System.currentTimeMillis() - totalStart;
            final int finalExecuted = executedCount;
            SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(false);
                statusLabel.setText("Executed " + finalExecuted + " statement(s) in " + totalElapsed + "ms");
                refreshHistory();
            });
            QueryHistory.add(sql);
        }).start();
    }

    private void addResultTab(String title, JComponent content) {
        if (resultTabs.getTabCount() >= 10) resultTabs.removeTabAt(0);
        int idx = resultTabs.getTabCount();
        resultTabs.addTab(title, content);
        // Add close button
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabHeader.setOpaque(false);
        JLabel lbl = new JLabel(title + "  ");
        JButton closeBtn = new JButton("✕");
        closeBtn.setMargin(new Insets(0, 2, 0, 2));
        closeBtn.setFocusable(false);
        closeBtn.addActionListener(e -> resultTabs.remove(content));
        tabHeader.add(lbl);
        tabHeader.add(closeBtn);
        resultTabs.setTabComponentAt(idx, tabHeader);
        resultTabs.setSelectedIndex(idx);
    }

    private JScrollPane createTextTab(String text) {
        JTextArea ta = new JTextArea(text);
        ta.setEditable(false);
        return new JScrollPane(ta);
    }

    private void saveCurrentQuery() {
        String sql = editor.getText().trim();
        if (sql.isEmpty()) return;
        String name = JOptionPane.showInputDialog(this, "Query name:", "Save Query", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isEmpty()) {
            SavedQueries.save(name, sql);
            refreshSavedQueries();
        }
    }

    private void refreshHistory() {
        historyModel.clear();
        for (String q : QueryHistory.load()) historyModel.addElement(q);
    }

    public void setEditorText(String text) {
        editor.setText(text);
    }

    public String getEditorText() {
        return editor != null ? editor.getText() : "";
    }
    
    public void refreshSavedQueries() {
        savedQueriesDropdown.removeAllItems();
        for (String name : SavedQueries.load().keySet()) savedQueriesDropdown.addItem(name);
    }

    private JTable getActiveResultTable() {
        Component sel = resultTabs.getSelectedComponent();
        if (sel instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) sel;
            Component view = sp.getViewport().getView();
            if (view instanceof JTable) {
                return (JTable) view;
            }
        }
        return null;
    }

    private void insertSnippet(String snippet) {
        String current = editor.getText();
        if (current.trim().isEmpty() || current.trim().equals("-- Write your SQL here\nSELECT 1;")) {
            editor.setText(snippet);
            editor.setCaretPosition(snippet.length());
        } else {
            editor.replaceSelection(snippet);
        }
        editor.requestFocusInWindow();
    }

    public void openMasteryCheatSheet() {
        Window win = SwingUtilities.getWindowAncestor(this);
        SqlMasteryReferenceDialog dlg = new SqlMasteryReferenceDialog(win, this::insertSnippet);
        dlg.setVisible(true);
    }

    private void showSnippetsMenu(Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        SqlSnippetsLibrary.populatePopupMenu(menu, this::insertSnippet, this::openMasteryCheatSheet);
        menu.show(invoker, 0, invoker.getHeight());
    }
}
