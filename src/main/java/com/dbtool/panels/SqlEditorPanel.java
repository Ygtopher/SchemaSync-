package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ExportUtil;
import com.dbtool.util.FileUtil;
import com.dbtool.util.QueryHistory;
import com.dbtool.util.SavedQueries;
import com.dbtool.util.SqlFormatter;
import com.dbtool.util.SqlInsertGenerator;
import com.dbtool.util.SqlQueryAnalyzer;
import com.dbtool.util.SqlSnippetsLibrary;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlEditorPanel extends JPanel {

    private static File customDraftFile = null;
    private final javax.swing.Timer draftSaveTimer = new javax.swing.Timer(600, e -> saveEditorDraft());

    private final DatabaseManager dbManager;
    private final RSyntaxTextArea editor;
    private final JTabbedPane resultTabs = new JTabbedPane();
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private final JTextField historySearchField = new JTextField();
    private final JCheckBox historyPinnedOnlyCheck = new JCheckBox("⭐ Pinned");
    private final JLabel statusLabel = new JLabel("Ready.");
    private final javax.swing.JProgressBar progressBar = new javax.swing.JProgressBar();
    private final JComboBox<String> savedQueriesDropdown = new JComboBox<>();
    private java.util.function.Consumer<String> sendToVisualBuilderAction;
    private Runnable onSchemaModified;

    // Multi-Tab SQL Scratchpads state
    private final List<String> scratchpadTitles = new ArrayList<>();
    private final List<String> scratchpadContents = new ArrayList<>();
    private int activeScratchpadIndex = 0;
    private boolean suppressScratchpadSync = false;
    private final JPanel scratchpadTabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

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
        loadEditorDraft();

        // Initialize Scratchpad 1 with loaded draft
        scratchpadTitles.add("Query 1");
        scratchpadContents.add(editor.getText());
        activeScratchpadIndex = 0;
        rebuildScratchpadTabBar();

        RTextScrollPane editorScroll = new RTextScrollPane(editor);
        editorScroll.setLineNumbersEnabled(true);

        draftSaveTimer.setRepeats(false);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            private void onChange() {
                if (!suppressScratchpadSync && activeScratchpadIndex >= 0 && activeScratchpadIndex < scratchpadContents.size()) {
                    scratchpadContents.set(activeScratchpadIndex, editor.getText());
                }
                draftSaveTimer.restart();
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange();
            }
        });

        // Right-click context menu integration
        JPopupMenu editorPopup = editor.getPopupMenu();
        if (editorPopup != null) {
            editorPopup.addSeparator();
            JMenuItem formatMenuItem = new JMenuItem("✨ Format & Beautify SQL (Ctrl+Shift+F)");
            formatMenuItem.addActionListener(e -> formatCurrentEditorSql());
            editorPopup.add(formatMenuItem);

            JMenuItem expandStarItem = new JMenuItem("⭐ Expand SELECT * to Columns");
            expandStarItem.addActionListener(e -> expandSelectStarInEditor());
            editorPopup.add(expandStarItem);

            JMenuItem lintMenuItem = new JMenuItem("🔍 Lint & Analyze Query...");
            lintMenuItem.addActionListener(e -> runQueryLinterDialog());
            editorPopup.add(lintMenuItem);

            JMenuItem dryRunMenuItem = new JMenuItem("🛡️ Dry-Run Preview Affected Rows");
            dryRunMenuItem.addActionListener(e -> runDryRunPreview());
            editorPopup.add(dryRunMenuItem);

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
        JButton dryRunBtn = new JButton("🛡️ Dry-Run");
        dryRunBtn.setToolTipText("Preview exact rows affected by an UPDATE or DELETE query without modifying data");
        dryRunBtn.addActionListener(e -> runDryRunPreview());

        JButton lintBtn = new JButton("🔍 Lint");
        lintBtn.setToolTipText("Analyze SQL query for performance anti-patterns, missing indexes, and safety risks");
        lintBtn.addActionListener(e -> runQueryLinterDialog());

        JButton formatBtn = new JButton("✨ Format");
        formatBtn.setToolTipText("Format & beautify SQL query (Ctrl+Shift+F) or expand SELECT *");
        formatBtn.addActionListener(e -> formatCurrentEditorSql());

        JButton expandStarBtn = new JButton("⭐ Expand *");
        expandStarBtn.setToolTipText("Replace SELECT * with explicit table column names");
        expandStarBtn.addActionListener(e -> expandSelectStarInEditor());

        JButton profilerBtn = new JButton("⏱️ Profiler");
        profilerBtn.setToolTipText("Open SQL Query Profiler & Latency Bottleneck Analyzer");
        profilerBtn.addActionListener(e -> new QueryProfilerDialog(SwingUtilities.getWindowAncestor(this), dbManager, this::setEditorText).setVisible(true));
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
        JButton chartBtn = new JButton("📊 Chart");
        chartBtn.setToolTipText("Visualize active query results as Bar, Line, or Donut Chart");

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
        toolbar.add(dryRunBtn);
        toolbar.add(lintBtn);
        toolbar.add(formatBtn);
        toolbar.add(expandStarBtn);
        toolbar.add(profilerBtn);
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
        toolbar.add(chartBtn);

        JPanel toolbarWrapper = new JPanel(new BorderLayout());
        toolbarWrapper.add(toolbar, BorderLayout.CENTER);

        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightToolbar.add(toggleHistoryBtn);
        toolbarWrapper.add(rightToolbar, BorderLayout.EAST);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(toolbarWrapper, BorderLayout.NORTH);
        northContainer.add(scratchpadTabBar, BorderLayout.SOUTH);

        // Editor + toolbar top section
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.add(northContainer, BorderLayout.NORTH);
        topSection.add(editorScroll, BorderLayout.CENTER);

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        JPanel statusPanel = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));
        statusPanel.add(statusLabel);
        statusPanel.add(progressBar);
        topSection.add(statusPanel, BorderLayout.SOUTH);

        // Upgraded History sidebar with Search & Pin filter
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String q = value != null ? value.toString() : "";
                String oneLine = q.replaceAll("\\s+", " ").trim();
                if (oneLine.length() > 65) oneLine = oneLine.substring(0, 62) + "...";
                QueryHistory.HistoryMetadata meta = QueryHistory.getMetadata(q);
                String prefix = (meta != null && meta.pinned) ? "⭐ " : "";
                String suffix = "";
                if (meta != null && meta.durationMs >= 0) {
                    suffix = " [" + meta.durationMs + "ms" + (meta.rowCount >= 0 ? ", " + meta.rowCount + "r" : "") + "]";
                }
                lbl.setText(prefix + oneLine + suffix);
                lbl.setToolTipText("<html><pre>" + q.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</pre></html>");
                return lbl;
            }
        });

        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setPreferredSize(new Dimension(250, 0));
        JPanel historyPanel = new JPanel(new BorderLayout(2, 2));

        JPanel histHeader = new JPanel(new BorderLayout(2, 2));
        JPanel histTitleRow = new JPanel(new BorderLayout());
        JLabel histLabel = new JLabel(" Query History");
        histLabel.setFont(histLabel.getFont().deriveFont(Font.BOLD));
        histTitleRow.add(histLabel, BorderLayout.WEST);
        historyPinnedOnlyCheck.setToolTipText("Show only pinned favorite queries");
        historyPinnedOnlyCheck.addActionListener(e -> refreshHistory());
        histTitleRow.add(historyPinnedOnlyCheck, BorderLayout.EAST);

        historySearchField.setToolTipText("Search query history...");
        historySearchField.putClientProperty("JTextField.placeholderText", "🔍 Search history...");
        historySearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshHistory(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshHistory(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshHistory(); }
        });

        histHeader.add(histTitleRow, BorderLayout.NORTH);
        histHeader.add(historySearchField, BorderLayout.SOUTH);

        JButton clearHistBtn = new JButton("Clear History");
        historyPanel.add(histHeader, BorderLayout.NORTH);
        historyPanel.add(historyScroll, BorderLayout.CENTER);
        historyPanel.add(clearHistBtn, BorderLayout.SOUTH);

        // Left split: editor top, results bottom
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSection, resultTabs);
        vertSplit.setResizeWeight(0.45);

        // Main split: vertSplit left, history right
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, vertSplit, historyPanel);
        mainSplit.setResizeWeight(0.80);

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

        // Keyboard Shortcuts:
        // Ctrl+Enter / F5 = Run, Ctrl+Shift+Enter = Run All, Ctrl+Shift+F = Format SQL, Ctrl+Space = Autocomplete
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

        editor.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "formatSql");
        editor.getActionMap().put("formatSql", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                formatCurrentEditorSql();
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, java.awt.event.InputEvent.CTRL_DOWN_MASK), "sqlAutocomplete");
        editor.getActionMap().put("sqlAutocomplete", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showContextAutocompletePopup();
            }
        });

        clearBtn.addActionListener(e -> {
            editor.setText("");
            saveEditorDraft();
        });
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
                mainSplit.setDividerLocation(0.80);
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
                    String sel = historyList.getSelectedValue();
                    if (sel == null) return;
                    javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();

                    JMenuItem loadItem = new JMenuItem("📥 Load into Editor");
                    loadItem.addActionListener(evt -> editor.setText(sel));
                    popup.add(loadItem);

                    boolean isPinned = QueryHistory.isPinned(sel);
                    JMenuItem pinItem = new JMenuItem(isPinned ? "☆ Unpin Favorite" : "⭐ Pin to Top (Favorite)");
                    pinItem.addActionListener(evt -> {
                        QueryHistory.togglePin(sel);
                        refreshHistory();
                    });
                    popup.add(pinItem);

                    JMenuItem copyItem = new JMenuItem("📋 Copy SQL");
                    copyItem.addActionListener(evt -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sel), null));
                    popup.add(copyItem);

                    JMenuItem saveNamedItem = new JMenuItem("💾 Save to Named Queries...");
                    saveNamedItem.addActionListener(evt -> {
                        String name = JOptionPane.showInputDialog(SqlEditorPanel.this, "Query name:", "Save Query", JOptionPane.PLAIN_MESSAGE);
                        if (name != null && !name.trim().isEmpty()) {
                            SavedQueries.save(name.trim(), sel);
                            refreshSavedQueries();
                        }
                    });
                    popup.add(saveNamedItem);

                    popup.addSeparator();
                    javax.swing.JMenuItem delItem = new javax.swing.JMenuItem("🗑 Delete");
                    delItem.addActionListener(evt -> {
                        QueryHistory.remove(sel);
                        refreshHistory();
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
        chartBtn.addActionListener(e -> {
            JTable t = getActiveResultTable();
            if (t != null && t.getRowCount() > 0) {
                new DataChartDialog((Frame) SwingUtilities.getWindowAncestor(this), t, "SQL Query Results").setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "No query results to chart. Run a query first.");
            }
        });

        refreshHistory();
        refreshSavedQueries();
    }

    // --- Multi-Tab SQL Scratchpad Management ---
    private void rebuildScratchpadTabBar() {
        scratchpadTabBar.removeAll();
        JLabel label = new JLabel(" 📑 Scratchpads: ");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        scratchpadTabBar.add(label);

        for (int i = 0; i < scratchpadTitles.size(); i++) {
            final int idx = i;
            String title = scratchpadTitles.get(i);
            JButton tabBtn = new JButton(title);
            tabBtn.setMargin(new Insets(1, 8, 1, 8));
            if (i == activeScratchpadIndex) {
                tabBtn.setFont(tabBtn.getFont().deriveFont(Font.BOLD));
                tabBtn.setBackground(new Color(52, 101, 164));
                tabBtn.setForeground(Color.WHITE);
            }
            tabBtn.addActionListener(e -> switchToScratchpad(idx));
            scratchpadTabBar.add(tabBtn);

            if (scratchpadTitles.size() > 1) {
                JButton closeTabBtn = new JButton("×");
                closeTabBtn.setMargin(new Insets(1, 4, 1, 4));
                closeTabBtn.setToolTipText("Close " + title);
                closeTabBtn.addActionListener(e -> closeScratchpad(idx));
                scratchpadTabBar.add(closeTabBtn);
            }
        }

        JButton addTabBtn = new JButton("+ New Tab");
        addTabBtn.setMargin(new Insets(1, 8, 1, 8));
        addTabBtn.setToolTipText("Open a new SQL scratchpad tab");
        addTabBtn.addActionListener(e -> addNewScratchpad());
        scratchpadTabBar.add(addTabBtn);

        scratchpadTabBar.revalidate();
        scratchpadTabBar.repaint();
    }

    private void switchToScratchpad(int index) {
        if (index < 0 || index >= scratchpadContents.size() || index == activeScratchpadIndex) return;
        scratchpadContents.set(activeScratchpadIndex, editor.getText());
        activeScratchpadIndex = index;
        suppressScratchpadSync = true;
        try {
            editor.setText(scratchpadContents.get(index));
            editor.setCaretPosition(0);
        } finally {
            suppressScratchpadSync = false;
        }
        rebuildScratchpadTabBar();
        editor.requestFocusInWindow();
    }

    private void addNewScratchpad() {
        scratchpadContents.set(activeScratchpadIndex, editor.getText());
        int nextNum = scratchpadTitles.size() + 1;
        scratchpadTitles.add("Query " + nextNum);
        scratchpadContents.add("-- Scratchpad Query " + nextNum + "\nSELECT 1;");
        activeScratchpadIndex = scratchpadTitles.size() - 1;
        suppressScratchpadSync = true;
        try {
            editor.setText(scratchpadContents.get(activeScratchpadIndex));
            editor.setCaretPosition(0);
        } finally {
            suppressScratchpadSync = false;
        }
        rebuildScratchpadTabBar();
        editor.requestFocusInWindow();
    }

    private void closeScratchpad(int index) {
        if (scratchpadTitles.size() <= 1 || index < 0 || index >= scratchpadTitles.size()) return;
        scratchpadTitles.remove(index);
        scratchpadContents.remove(index);
        if (activeScratchpadIndex >= scratchpadTitles.size()) {
            activeScratchpadIndex = scratchpadTitles.size() - 1;
        }
        suppressScratchpadSync = true;
        try {
            editor.setText(scratchpadContents.get(activeScratchpadIndex));
            editor.setCaretPosition(0);
        } finally {
            suppressScratchpadSync = false;
        }
        rebuildScratchpadTabBar();
    }

    // --- Smart Editor Actions (Format, Expand *, Autocomplete, Lint, Dry-Run) ---
    public void formatCurrentEditorSql() {
        String selected = editor.getSelectedText();
        if (selected != null && !selected.trim().isEmpty()) {
            editor.replaceSelection(SqlFormatter.format(selected));
        } else {
            String full = editor.getText();
            if (full != null && !full.trim().isEmpty()) {
                editor.setText(SqlFormatter.format(full));
            }
        }
        statusLabel.setText("SQL formatted.");
    }

    public void expandSelectStarInEditor() {
        String selected = editor.getSelectedText();
        if (selected != null && !selected.trim().isEmpty()) {
            String expanded = SqlQueryAnalyzer.expandSelectStar(selected, dbManager);
            editor.replaceSelection(expanded);
        } else {
            String currentStmt = getCurrentSql();
            String expanded = SqlQueryAnalyzer.expandSelectStar(currentStmt, dbManager);
            if (!expanded.equals(currentStmt)) {
                editor.setText(editor.getText().replace(currentStmt, expanded));
                statusLabel.setText("Expanded SELECT * to explicit columns.");
            } else {
                statusLabel.setText("Connect to a database and place cursor on a 'SELECT * FROM <table>' query to expand.");
            }
        }
    }

    private void showContextAutocompletePopup() {
        String text = editor.getText();
        int caret = editor.getCaretPosition();
        List<String> suggestions = SqlQueryAnalyzer.suggestCompletions(text, caret, dbManager);
        if (suggestions.isEmpty()) return;

        JPopupMenu popup = new JPopupMenu();
        int limit = Math.min(suggestions.size(), 20);
        for (int i = 0; i < limit; i++) {
            String itemText = suggestions.get(i);
            JMenuItem item = new JMenuItem(itemText);
            item.addActionListener(e -> applyAutocompleteSuggestion(itemText));
            popup.add(item);
        }

        try {
            Rectangle rect = editor.modelToView(caret);
            if (rect != null) {
                popup.show(editor, rect.x, rect.y + rect.height);
            } else {
                popup.show(editor, 20, 20);
            }
        } catch (Exception ex) {
            popup.show(editor, 20, 20);
        }
    }

    private void applyAutocompleteSuggestion(String suggestion) {
        int caret = editor.getCaretPosition();
        String text = editor.getText();
        int start = caret;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                start--;
            } else {
                break;
            }
        }
        String token = text.substring(start, caret);
        if (token.contains(".") && !suggestion.contains(".")) {
            start = start + token.lastIndexOf('.') + 1;
        }
        editor.select(start, caret);
        editor.replaceSelection(suggestion);
        editor.requestFocusInWindow();
    }

    public void runQueryLinterDialog() {
        String sql = getCurrentSql();
        if (sql == null || sql.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Write or select a SQL query first.", "SQL Linter", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<SqlQueryAnalyzer.LintIssue> issues = SqlQueryAnalyzer.analyze(sql, dbManager);
        if (issues.isEmpty()) {
            JOptionPane.showMessageDialog(this, "✅ No anti-patterns or safety issues detected in the query!", "SQL Linter — Clean", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        com.dbtool.util.AppUsageTracker.recordLinterIssues(issues.size());

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        DefaultListModel<String> listModel = new DefaultListModel<>();
        String suggestedRewrite = null;
        for (SqlQueryAnalyzer.LintIssue issue : issues) {
            String icon = issue.severity == SqlQueryAnalyzer.Severity.CRITICAL ? "🛑 CRITICAL"
                    : (issue.severity == SqlQueryAnalyzer.Severity.WARNING ? "⚠️ WARNING" : "ℹ️ INFO");
            listModel.addElement(icon + " — " + issue.title + ": " + issue.message);
            if (issue.suggestedSql != null && suggestedRewrite == null) {
                suggestedRewrite = issue.suggestedSql;
            }
        }

        JList<String> issueList = new JList<>(listModel);
        JScrollPane sp = new JScrollPane(issueList);
        sp.setPreferredSize(new Dimension(620, 180));
        panel.add(new JLabel("Detected " + issues.size() + " query optimization / safety notice(s):"), BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);

        if (suggestedRewrite != null) {
            final String fix = suggestedRewrite;
            Object[] options = {"✨ Apply Suggested Fix", "Close"};
            int choice = JOptionPane.showOptionDialog(this, panel, "SQL Query Linter & Optimizer",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                editor.setText(fix);
                com.dbtool.util.AppUsageTracker.recordLinterFixApplied();
                statusLabel.setText("Applied suggested SQL optimization.");
            }
        } else {
            JOptionPane.showMessageDialog(this, panel, "SQL Query Linter & Optimizer", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void runDryRunPreview() {
        String sql = getCurrentSql();
        if (sql == null || sql.trim().isEmpty()) return;
        com.dbtool.util.AppUsageTracker.recordDestructiveQueryIntercepted();
        String previewSelect = SqlQueryAnalyzer.buildDryRunSelectSql(sql);
        statusLabel.setText("Running Dry-Run Preview: " + previewSelect);
        executeQueryInternal(previewSelect, false, true);
    }

    private Map<String, String> promptForQueryParameters(List<String> paramNames) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Map<String, JTextField> fields = new LinkedHashMap<>();
        Map<String, String> lastVals = SqlQueryAnalyzer.getLastParameterValues();

        for (int i = 0; i < paramNames.size(); i++) {
            String p = paramNames.get(i);
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0.0;
            form.add(new JLabel(":" + p + " ="), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            JTextField tf = new JTextField(lastVals.getOrDefault(p, ""), 22);
            fields.put(p, tf);
            form.add(tf, gbc);
        }

        int res = JOptionPane.showConfirmDialog(this, form, "Enter Query Parameters",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (res != JOptionPane.OK_OPTION) {
            return null;
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getText());
        }
        return values;
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
            if (upper.equals("SHOW TABLES")) {
                return "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;";
            }
            if (upper.equals("SHOW DATABASES")) {
                return "SELECT datname AS database_name FROM pg_database WHERE datistemplate = false ORDER BY datname;";
            }
            if (upper.startsWith("DESCRIBE ") || upper.startsWith("DESC ")) {
                String[] parts = clean.split("\\s+", 2);
                if (parts.length > 1) {
                    String tbl = parts[1].trim().replace(";", "").replace("\"", "").replace("'", "").toLowerCase();
                    return "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_name = '" + tbl + "' ORDER BY ordinal_position;";
                }
            }
            if (upper.startsWith("USE ")) {
                String[] parts = clean.split("\\s+", 2);
                if (parts.length > 1) {
                    String sch = parts[1].trim().replace(";", "").replace("\"", "").replace("'", "");
                    return "SET search_path TO " + sch + ", public;";
                }
            }
            if (upper.contains("AUTO_INCREMENT") && upper.startsWith("CREATE TABLE")) {
                sql = sql.replaceAll("(?i)\\bINT\\s+PRIMARY\\s+KEY\\s+AUTO_INCREMENT\\b", "SERIAL PRIMARY KEY")
                         .replaceAll("(?i)\\bINT\\s+AUTO_INCREMENT\\s+PRIMARY\\s+KEY\\b", "SERIAL PRIMARY KEY")
                         .replaceAll("(?i)\\bAUTO_INCREMENT\\b", "");
                upper = sql.toUpperCase();
            }

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
        executeQueryInternal(sql, isExplain, false);
    }

    private void executeQueryInternal(String sql, boolean isExplain, boolean isDryRun) {
        if (sql == null || sql.trim().isEmpty()) return;
        saveEditorDraft();

        // 1. Named Parameter Detection (:param or ${param})
        List<String> params = SqlQueryAnalyzer.extractNamedParameters(sql);
        if (!params.isEmpty()) {
            Map<String, String> boundValues = promptForQueryParameters(params);
            if (boundValues == null) {
                statusLabel.setText("Execution cancelled.");
                return;
            }
            sql = SqlQueryAnalyzer.bindParameters(sql, boundValues);
        }

        // 2. Destructive Unfiltered UPDATE / DELETE Guardrail
        if (!isExplain && !isDryRun && SqlQueryAnalyzer.isUnfilteredDestructiveDml(sql)) {
            com.dbtool.util.AppUsageTracker.recordDestructiveQueryIntercepted();
            Object[] options = {"🔍 Preview Affected Rows (Dry-Run)", "⚠️ Execute Anyway", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this,
                    "⚠️ SAFETY WARNING: Unfiltered UPDATE / DELETE Detected!\n\n" +
                    "The statement below has no WHERE filter (or uses a tautology like 1=1) and will affect EVERY row in the table:\n\n" +
                    sql + "\n\nWould you like to preview the affected rows safely first?",
                    "Destructive Query Guardrail",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]);
            if (choice == 0) {
                String previewSelect = SqlQueryAnalyzer.buildDryRunSelectSql(sql);
                executeQueryInternal(previewSelect, false, true);
                return;
            } else if (choice != 1) {
                statusLabel.setText("Destructive query cancelled.");
                return;
            }
        }

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

        final String finalSqlToExecute = sql;
        statusLabel.setText(isDryRun ? "Running Dry-Run Preview..." : "Running...");
        progressBar.setVisible(true);

        new Thread(() -> {
            long totalStart = System.currentTimeMillis();
            List<SqlRange> statements = extractStatements(finalSqlToExecute);
            if (statements.isEmpty()) {
                statements.add(new SqlRange(0, finalSqlToExecute.length(), finalSqlToExecute.trim()));
            }

            int executedCount = 0;
            int lastRowCount = -1;
            boolean hadDdl = false;

            for (int i = 0; i < statements.size(); i++) {
                String rawStmt = statements.get(i).sql.trim();
                if (rawStmt.isEmpty() || rawStmt.startsWith("--") || (rawStmt.startsWith("/*") && rawStmt.endsWith("*/"))) {
                    continue;
                }

                String queryToRun = translateCompatibilitySql(rawStmt, dbManager);
                String kind = isDryRun ? "Dry-Run Preview" : getStatementKind(rawStmt);
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
                                lastRowCount = model.getRowCount();
                                com.dbtool.util.AppUsageTracker.recordRowsFetched(lastRowCount);
                                String tabTitle = (statements.size() > 1 ? kind + " #" + stmtNum : kind) + " (" + model.getRowCount() + " rows)";
                                final String finalTableQuery = queryToRun;
                                SwingUtilities.invokeLater(() -> {
                                    JComponent resultContainer = buildInteractiveResultTab(model, finalTableQuery, stmtElapsed);
                                    addResultTab(tabTitle, resultContainer);
                                });
                            }
                        } else {
                            int affected = stmt.getUpdateCount();
                            lastRowCount = affected;
                            if (affected > 0) {
                                com.dbtool.util.AppUsageTracker.recordRowsMutated(affected);
                            }
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
            QueryHistory.addWithMetadata(finalSqlToExecute, totalElapsed, lastRowCount);
            SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(false);
                statusLabel.setText("Executed " + finalExecuted + " statement(s) in " + totalElapsed + "ms");
                refreshHistory();
            });
        }).start();
    }

    /**
     * Builds an interactive query result panel featuring:
     * 1. Instant client-side regex row filter bar
     * 2. Sortable JTable with row numbers & column type tooltips
     * 3. Live Selection Statistics footer (Selected Rows, Cells, Nulls, Numeric Count, Sum, Avg, Min, Max)
     */
    private JComponent buildInteractiveResultTab(com.dbtool.util.TypedTableModel model, String querySql, long elapsedMs) {
        JPanel container = new JPanel(new BorderLayout(0, 2));

        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(true);
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        com.dbtool.util.TableTooltipUtil.attachHeaderTooltips(table);
        attachResultTableContextMenu(table, querySql);

        // Top client-side filter bar
        JPanel filterBar = new JPanel(new BorderLayout(6, 0));
        filterBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JLabel filterLbl = new JLabel("🔍 Filter Results:");
        JTextField filterField = new JTextField();
        filterField.setToolTipText("Filter returned rows instantly (case-insensitive substring or regex)");
        JLabel matchCountLbl = new JLabel(" " + model.getRowCount() + " rows [" + elapsedMs + "ms] ");

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            private void applyFilter() {
                String text = filterField.getText().trim();
                if (text.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    try {
                        sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
                    } catch (Exception ignored) {
                        sorter.setRowFilter(null);
                    }
                }
                matchCountLbl.setText(" Showing " + table.getRowCount() + " / " + model.getRowCount() + " rows [" + elapsedMs + "ms] ");
            }
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        filterBar.add(filterLbl, BorderLayout.WEST);
        filterBar.add(filterField, BorderLayout.CENTER);
        filterBar.add(matchCountLbl, BorderLayout.EAST);

        // Center scroll pane with row numbers
        JScrollPane scrollPane = new JScrollPane(table);
        com.dbtool.util.TableRowUtilities.addRowNumbers(table, scrollPane);

        // Bottom live selection statistics footer
        JLabel statsFooter = new JLabel(" " + SqlInsertGenerator.calculateSelectionStats(table));
        statsFooter.setFont(statsFooter.getFont().deriveFont(Font.PLAIN, 11f));
        statsFooter.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        Runnable updateStats = () -> statsFooter.setText(" " + SqlInsertGenerator.calculateSelectionStats(table));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateStats.run();
        });
        table.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateStats.run();
        });

        container.add(filterBar, BorderLayout.NORTH);
        container.add(scrollPane, BorderLayout.CENTER);
        container.add(statsFooter, BorderLayout.SOUTH);
        container.putClientProperty("resultTable", table);
        return container;
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
        String kw = historySearchField != null ? historySearchField.getText() : "";
        boolean pinnedOnly = historyPinnedOnlyCheck != null && historyPinnedOnlyCheck.isSelected();
        for (String q : QueryHistory.search(kw, pinnedOnly)) {
            historyModel.addElement(q);
        }
    }

    public void setEditorText(String text) {
        editor.setText(text);
        if (activeScratchpadIndex >= 0 && activeScratchpadIndex < scratchpadContents.size()) {
            scratchpadContents.set(activeScratchpadIndex, text);
        }
        saveEditorDraft();
    }

    public String getEditorText() {
        return editor != null ? editor.getText() : "";
    }

    public void refreshSavedQueries() {
        savedQueriesDropdown.removeAllItems();
        for (String name : SavedQueries.load().keySet()) savedQueriesDropdown.addItem(name);
    }

    public static synchronized void setDraftFileForTesting(File file) {
        customDraftFile = file;
    }

    public static synchronized File getDraftFile() {
        if (customDraftFile != null) return customDraftFile;
        return FileUtil.getSaveFile("sql_editor_draft.sql");
    }

    public void loadEditorDraft() {
        try {
            File draftFile = getDraftFile();
            if (draftFile.exists() && draftFile.length() > 0) {
                String content = new String(Files.readAllBytes(draftFile.toPath()), StandardCharsets.UTF_8);
                if (!content.trim().isEmpty()) {
                    editor.setText(content);
                    editor.setCaretPosition(0);
                    return;
                }
            }
        } catch (Exception ignored) {}
        editor.setText("-- Write your SQL here\nSELECT 1;");
        editor.setCaretPosition(0);
    }

    public void saveEditorDraft() {
        if (editor == null) return;
        try {
            File draftFile = getDraftFile();
            String text = editor.getText();
            Files.write(draftFile.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private JTable getActiveResultTable() {
        Component sel = resultTabs.getSelectedComponent();
        if (sel instanceof JComponent) {
            Object prop = ((JComponent) sel).getClientProperty("resultTable");
            if (prop instanceof JTable) {
                return (JTable) prop;
            }
        }
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

    private void attachResultTableContextMenu(JTable table, String querySql) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem copyCell = new JMenuItem("Copy Cell");
        copyCell.addActionListener(e -> {
            int r = table.getSelectedRow();
            int c = table.getSelectedColumn();
            if (r >= 0 && c >= 0) {
                Object val = table.getValueAt(r, c);
                if (val != null) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(val.toString()), null);
                }
            }
        });
        popup.add(copyCell);

        String detectedTable = extractTableNameFromQuery(querySql);
        if (detectedTable != null && !detectedTable.isEmpty()) {
            JMenuItem copyInsert = new JMenuItem("Copy Row as SQL INSERT");
            copyInsert.addActionListener(e -> {
                int[] rows = table.getSelectedRows();
                if (rows.length == 0) return;
                String insertSql = SqlInsertGenerator.generateInsertStatements(table, rows, detectedTable, dbManager, true);
                if (!insertSql.isEmpty()) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(insertSql), null);
                }
            });
            popup.add(copyInsert);

            JMenuItem copyInsertWithPk = new JMenuItem("Copy Row as SQL INSERT (Include Primary Key)");
            copyInsertWithPk.addActionListener(e -> {
                int[] rows = table.getSelectedRows();
                if (rows.length == 0) return;
                String insertSql = SqlInsertGenerator.generateInsertStatements(table, rows, detectedTable, dbManager, false);
                if (!insertSql.isEmpty()) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(insertSql), null);
                }
            });
            popup.add(copyInsertWithPk);

            JMenuItem copyUpdate = new JMenuItem("Copy Row as SQL UPDATE");
            copyUpdate.addActionListener(e -> {
                int[] rows = table.getSelectedRows();
                if (rows.length == 0) return;
                String updateSql = SqlInsertGenerator.generateUpdateStatements(table, rows, detectedTable, dbManager);
                if (!updateSql.isEmpty()) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(updateSql), null);
                }
            });
            popup.add(copyUpdate);
        }

        JMenuItem copyJson = new JMenuItem("Copy Selected as JSON Array");
        copyJson.addActionListener(e -> {
            int[] rows = table.getSelectedRows();
            if (rows.length == 0) return;
            String json = SqlInsertGenerator.generateJsonArray(table, rows);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(json), null);
        });
        popup.add(copyJson);

        JMenuItem copyMarkdown = new JMenuItem("Copy Selected as Markdown Table");
        copyMarkdown.addActionListener(e -> {
            int[] rows = table.getSelectedRows();
            if (rows.length == 0) return;
            String md = SqlInsertGenerator.generateMarkdownTable(table, rows);
            if (!md.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(md), null);
            }
        });
        popup.add(copyMarkdown);

        JMenuItem exportCsv = new JMenuItem("Export Selected to CSV");
        exportCsv.addActionListener(e -> {
            int[] rows = table.getSelectedRows();
            if (rows.length == 0) return;
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < table.getColumnCount(); c++) {
                sb.append("\"").append(table.getColumnName(c)).append("\"").append(c < table.getColumnCount() - 1 ? "," : "");
            }
            sb.append("\n");
            for (int row : rows) {
                for (int c = 0; c < table.getColumnCount(); c++) {
                    Object val = table.getValueAt(row, c);
                    String s = val == null ? "" : val.toString().replace("\"", "\"\"");
                    sb.append("\"").append(s).append("\"").append(c < table.getColumnCount() - 1 ? "," : "");
                }
                sb.append("\n");
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(sb.toString()), null);
        });
        popup.add(exportCsv);

        table.setComponentPopupMenu(popup);
    }

    private static String extractTableNameFromQuery(String sql) {
        if (sql == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\bFROM\\s+([a-zA-Z0-9_.\"]+)").matcher(sql);
        if (m.find()) {
            String t = m.group(1).trim().replace("\"", "");
            if (!t.isEmpty() && !t.equalsIgnoreCase("SELECT")) {
                return t;
            }
        }
        return null;
    }
}
