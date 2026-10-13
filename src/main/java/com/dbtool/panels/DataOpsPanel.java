package com.dbtool.panels;

import com.dbtool.DatabaseManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Data Operations Panel — toolbar-based layout matching Browse & Search style.
 * Operation selector at top + CardLayout for per-operation controls + shared history at bottom.
 */
public class DataOpsPanel extends JPanel {

    private final DatabaseManager dbManager;

    // ── Operation / table selectors ───────────────────────────────────
    private final JComboBox<String> operationCb = new JComboBox<>(new String[]{
        "UPDATE", "UPDATE + JOIN", "DELETE", "ALTER TABLE", "TRUNCATE", "CREATE INDEX",
        "INSERT / UPSERT", "REFRESH MATERIALIZED VIEW", "VACUUM / ANALYZE",
        "TRANSACTION ISOLATION", "ADVISORY LOCK", "LISTEN / NOTIFY"
    });
    private final JComboBox<String> mainTableCb = new JComboBox<>();   // target table (universal)
    private final JComboBox<String> juJoinCb    = new JComboBox<>();   // join table (UPDATE+JOIN only)

    // ── Shared SET rows (for UPDATE and UPDATE+JOIN) ──────────────────
    private final JPanel            juSetRowsCtr = new JPanel();
    private final List<SetRow>      juSetRows    = new ArrayList<>();

    // ── UPDATE simple ─────────────────────────────────────────────────
    private final JComboBox<String> updateWhereColCb = new JComboBox<>();
    private final JTextField        updateWhereValFld = new JTextField(16);

    // ── UPDATE + JOIN ─────────────────────────────────────────────────
    private final JComboBox<String> juTypeCb   = new JComboBox<>(new String[]{"JOIN", "INNER JOIN", "LEFT JOIN", "CROSS APPLY", "LATERAL"});
    private final JComboBox<String> juOnMainCb = new JComboBox<>();   // target.col
    private final JComboBox<String> juOnJoinCb = new JComboBox<>();   // join.col
    private final JComboBox<String> juWhereTableCb = new JComboBox<>();
    private final JComboBox<String> juWhereColCb = new JComboBox<>();
    private final JTextField        juWhereValFld = new JTextField(16);

    // ── DELETE ────────────────────────────────────────────────────────
    private final JComboBox<String> deleteColCb = new JComboBox<>();
    private final JTextField        deletePkVal = new JTextField(16);

    // ── ALTER TABLE ───────────────────────────────────────────────────
    private final JComboBox<String> alterOpCb = new JComboBox<>(new String[]{
        "Add Column", "Rename Column", "Drop Column", "Rename Table", "Change Column Type"
    });
    private final JComboBox<String> alterOldColCb  = new JComboBox<>();
    private final JTextField        alterNewNameFld = new JTextField(16);
    private final JTextField        alterTypeFld    = new JTextField(12);

    // ── CREATE INDEX ──────────────────────────────────────────────────
    private final JTextField        indexNameFld    = new JTextField(14);
    private final JComboBox<String> indexColCb      = new JComboBox<>();
    private final JCheckBox         indexUniqueCb   = new JCheckBox("UNIQUE");
    private final JComboBox<String> indexTypeCb     = new JComboBox<>(new String[]{
        "BTREE (Default)", "GIN (Full-Text/JSONB)", "BRIN (Large Time-Series)", "HASH", "GiST", "SP-GiST"
    });
    private final JTextField        indexIncludeFld = new JTextField(12);
    private final JTextField        indexWhereFld   = new JTextField(16);

    // ── INSERT / UPSERT ───────────────────────────────────────────────
    private final JTextField        upsertValuesFld     = new JTextField("(1, 'Sample', 100)", 20);
    private final JComboBox<String> upsertConflictColCb = new JComboBox<>();
    private final JComboBox<String> upsertActionCb      = new JComboBox<>(new String[]{
        "DO UPDATE (Upsert)", "DO NOTHING", "ON DUPLICATE KEY UPDATE (MySQL)"
    });
    private final JTextField        upsertSetFld        = new JTextField("updated_at = NOW()", 20);

    // ── REFRESH MATERIALIZED VIEW ─────────────────────────────────────
    private final JTextField        matViewNameFld        = new JTextField(16);
    private final JCheckBox         matViewConcurrentlyCb = new JCheckBox("CONCURRENTLY");
    private final JCheckBox         matViewWithDataCb     = new JCheckBox("WITH DATA", true);

    // ── VACUUM / ANALYZE ──────────────────────────────────────────────
    private final JComboBox<String> vacuumModeCb  = new JComboBox<>(new String[]{
        "VACUUM", "VACUUM ANALYZE", "VACUUM FULL", "VACUUM (FREEZE, VERBOSE)", "ANALYZE"
    });
    private final JComboBox<String> vacuumScopeCb = new JComboBox<>(new String[]{
        "Selected Table", "Entire Database"
    });

    // ── TRANSACTION ISOLATION ─────────────────────────────────────────
    private final JComboBox<String> isolationLevelCb  = new JComboBox<>(new String[]{
        "READ COMMITTED (Default)", "REPEATABLE READ", "SERIALIZABLE", "READ UNCOMMITTED"
    });
    private final JComboBox<String> isolationAccessCb = new JComboBox<>(new String[]{
        "READ WRITE", "READ ONLY"
    });
    private final JComboBox<String> isolationScopeCb  = new JComboBox<>(new String[]{
        "TRANSACTION", "SESSION"
    });

    // ── ADVISORY LOCK ─────────────────────────────────────────────────
    private final JComboBox<String> advisoryFuncCb = new JComboBox<>(new String[]{
        "pg_advisory_lock", "pg_try_advisory_lock", "pg_advisory_unlock", "pg_advisory_xact_lock", "pg_try_advisory_xact_lock"
    });
    private final JTextField        advisoryKeyFld = new JTextField("42", 10);

    // ── LISTEN / NOTIFY ───────────────────────────────────────────────
    private final JComboBox<String> pubsubOpCb      = new JComboBox<>(new String[]{
        "NOTIFY", "LISTEN", "UNLISTEN", "UNLISTEN *"
    });
    private final JTextField        pubsubChanFld   = new JTextField("orders_changed", 14);
    private final JTextField        pubsubPayloadFld = new JTextField("order 123 updated", 16);

    // ── SQL preview ───────────────────────────────────────────────────
    private final JTextArea sqlPreview = new JTextArea(5, 80);

    // ── Query history ─────────────────────────────────────────────────
    private final List<String>             rawHistory   = new ArrayList<>();
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String>            historyJList = new JList<>(historyModel);

    // ── Status bar ────────────────────────────────────────────────────
    private final JLabel statusLbl = new JLabel("Connect to a database to use this panel.");

    // ── CardLayout ────────────────────────────────────────────────────
    private final CardLayout cardLayout  = new CardLayout();
    private final JPanel     cardPanel   = new JPanel(cardLayout);
    private final JPanel     setSection  = new JPanel(new BorderLayout(4, 0));

    public DataOpsPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout(0, 0));

        // Wire listeners BEFORE building UI
        operationCb.addActionListener(e -> onOperationChanged());
        mainTableCb.addActionListener(e -> refreshMainTableColumns());
        alterOpCb.addActionListener(e -> refreshAlterFields());
        juJoinCb.addActionListener(e -> {
            refreshCols((String) juJoinCb.getSelectedItem(), juOnJoinCb);
            refreshJuWhereTables();
        });
        juWhereTableCb.addActionListener(e -> refreshCols((String) juWhereTableCb.getSelectedItem(), juWhereColCb));

        juSetRowsCtr.setLayout(new BoxLayout(juSetRowsCtr, BoxLayout.Y_AXIS));
        sqlPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sqlPreview.setEditable(false);

        // Assemble north toolbar stack
        // Assemble north toolbar stack wrapped in scroll pane to prevent vertical/horizontal overflow
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(buildMainToolbar());      // Operation + Table
        buildCardPanel();
        north.add(cardPanel);               // Per-operation controls
        buildSetSection();
        north.add(setSection);              // SET rows (UPDATE and UPDATE+JOIN)
        north.add(buildRunToolbar());       // Preview / Run / Copy / Commit / Rollback

        JScrollPane northScroll = new JScrollPane(north);
        northScroll.setBorder(BorderFactory.createEmptyBorder());
        northScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        northScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        northScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Center: SQL Preview above, History below
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(new TitledBorder("Generated SQL"));
        previewPanel.add(new JScrollPane(sqlPreview), BorderLayout.CENTER);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, previewPanel, buildHistoryPanel());
        centerSplit.setResizeWeight(0.38);
        centerSplit.setDividerSize(6);

        JSplitPane dataOpsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, northScroll, centerSplit);
        dataOpsSplit.setResizeWeight(0.32);
        dataOpsSplit.setDividerSize(5);
        dataOpsSplit.setBorder(BorderFactory.createEmptyBorder());
        add(dataOpsSplit, BorderLayout.CENTER);

        statusLbl.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        add(statusLbl, BorderLayout.SOUTH);

        refreshAlterFields();
        addSetRow();
        onOperationChanged();

        // Lazy-load on first show
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                if (dbManager.connection != null) refreshTables();
            }
        });
    }

    // ── Toolbar builders ──────────────────────────────────────────────

    private JToolBar buildMainToolbar() {
        JToolBar tb = tb();
        tb.add(new JLabel(" Operation: "));
        operationCb.setPreferredSize(new Dimension(160, 26));
        tb.add(operationCb);
        tb.addSeparator();
        tb.add(new JLabel(" Target Table: "));
        mainTableCb.setPreferredSize(new Dimension(190, 26));
        tb.add(mainTableCb);
        return tb;
    }

    private void buildCardPanel() {
        // UPDATE
        JToolBar tbU = tb();
        tbU.add(new JLabel(" WHERE Column: ")); tbU.add(updateWhereColCb);
        tbU.add(new JLabel(" = Value: "));      tbU.add(updateWhereValFld);
        cardPanel.add(tbU, "UPDATE");

        // UPDATE + JOIN
        JToolBar tbJ = tb();
        tbJ.add(juTypeCb);
        juJoinCb.setPreferredSize(new Dimension(160, 26));
        tbJ.add(juJoinCb);
        tbJ.add(new JLabel(" ON target.")); tbJ.add(juOnMainCb);
        tbJ.add(new JLabel(" = join."));   tbJ.add(juOnJoinCb);
        tbJ.addSeparator();
        tbJ.add(new JLabel(" WHERE Table: ")); tbJ.add(juWhereTableCb);
        tbJ.add(new JLabel(" Col: "));      tbJ.add(juWhereColCb);
        tbJ.add(new JLabel(" = Value: "));      tbJ.add(juWhereValFld);
        cardPanel.add(tbJ, "UPDATE + JOIN");

        // DELETE
        JToolBar tbD = tb();
        tbD.add(new JLabel(" WHERE Column: ")); tbD.add(deleteColCb);
        tbD.add(new JLabel(" = Value: "));      tbD.add(deletePkVal);
        cardPanel.add(tbD, "DELETE");

        // ALTER TABLE (two rows to avoid crowding)
        JPanel tbAPanel = new JPanel();
        tbAPanel.setLayout(new BoxLayout(tbAPanel, BoxLayout.Y_AXIS));
        JToolBar tbA1 = tb();
        tbA1.add(new JLabel(" Operation: ")); tbA1.add(alterOpCb);
        tbA1.addSeparator();
        tbA1.add(new JLabel("Column: "));     tbA1.add(alterOldColCb);
        JToolBar tbA2 = tb();
        tbA2.add(new JLabel(" New Name: "));  tbA2.add(alterNewNameFld);
        tbA2.addSeparator();
        tbA2.add(new JLabel("Data Type: ")); tbA2.add(alterTypeFld);
        alterTypeFld.setToolTipText("e.g. VARCHAR(255), INTEGER, TEXT, BOOLEAN");
        tbAPanel.add(tbA1);
        tbAPanel.add(tbA2);
        cardPanel.add(tbAPanel, "ALTER TABLE");

        // TRUNCATE
        JToolBar tbT = tb();
        tbT.add(new JLabel("<html><font color='red'><b>⚠ Warning:</b> TRUNCATE permanently deletes ALL rows from the selected table.</font></html>"));
        cardPanel.add(tbT, "TRUNCATE");

        // CREATE INDEX (two rows to accommodate advanced indexing options)
        JPanel tbIPanel = new JPanel();
        tbIPanel.setLayout(new BoxLayout(tbIPanel, BoxLayout.Y_AXIS));
        JToolBar tbI1 = tb();
        tbI1.add(new JLabel(" Index Name: ")); tbI1.add(indexNameFld);
        tbI1.addSeparator();
        tbI1.add(new JLabel("Column: "));      tbI1.add(indexColCb);
        tbI1.add(indexUniqueCb);
        tbI1.addSeparator();
        tbI1.add(new JLabel("Method: "));      tbI1.add(indexTypeCb);

        JToolBar tbI2 = tb();
        tbI2.add(new JLabel(" Covering INCLUDE: ")); tbI2.add(indexIncludeFld);
        indexIncludeFld.setToolTipText("Covering payload columns (e.g. total_amount, order_date)");
        tbI2.addSeparator();
        tbI2.add(new JLabel(" Partial WHERE: "));    tbI2.add(indexWhereFld);
        indexWhereFld.setToolTipText("Predicate for partial index (e.g. status = 'open' or active = true)");
        tbIPanel.add(tbI1);
        tbIPanel.add(tbI2);
        cardPanel.add(tbIPanel, "CREATE INDEX");

        // INSERT / UPSERT
        JPanel tbUpPanel = new JPanel();
        tbUpPanel.setLayout(new BoxLayout(tbUpPanel, BoxLayout.Y_AXIS));
        JToolBar tbUp1 = tb();
        tbUp1.add(new JLabel(" Values: ")); tbUp1.add(upsertValuesFld);
        upsertValuesFld.setToolTipText("e.g. (1, 'Alice', 100000) or multi-row: (1, 'A'), (2, 'B')");
        tbUp1.addSeparator();
        tbUp1.add(new JLabel("On Conflict Key: ")); tbUp1.add(upsertConflictColCb);
        tbUp1.add(new JLabel(" Action: ")); tbUp1.add(upsertActionCb);

        JToolBar tbUp2 = tb();
        tbUp2.add(new JLabel(" DO UPDATE SET: ")); tbUp2.add(upsertSetFld);
        upsertSetFld.setToolTipText("e.g. salary = EXCLUDED.salary, updated_at = NOW()");
        tbUpPanel.add(tbUp1);
        tbUpPanel.add(tbUp2);
        cardPanel.add(tbUpPanel, "INSERT / UPSERT");

        // REFRESH MATERIALIZED VIEW
        JToolBar tbM = tb();
        tbM.add(new JLabel(" View Name: ")); tbM.add(matViewNameFld);
        matViewNameFld.setToolTipText("Name of the materialized view to refresh");
        tbM.add(matViewConcurrentlyCb);
        matViewConcurrentlyCb.setToolTipText("CONCURRENTLY refreshes without locking concurrent reads");
        tbM.add(matViewWithDataCb);
        cardPanel.add(tbM, "REFRESH MATERIALIZED VIEW");

        // VACUUM / ANALYZE
        JToolBar tbV = tb();
        tbV.add(new JLabel(" Operation: ")); tbV.add(vacuumModeCb);
        tbV.addSeparator();
        tbV.add(new JLabel("Target Scope: ")); tbV.add(vacuumScopeCb);
        cardPanel.add(tbV, "VACUUM / ANALYZE");

        // TRANSACTION ISOLATION
        JToolBar tbIso = tb();
        tbIso.add(new JLabel(" Scope: ")); tbIso.add(isolationScopeCb);
        tbIso.addSeparator();
        tbIso.add(new JLabel("Isolation Level: ")); tbIso.add(isolationLevelCb);
        tbIso.addSeparator();
        tbIso.add(new JLabel("Access Mode: ")); tbIso.add(isolationAccessCb);
        cardPanel.add(tbIso, "TRANSACTION ISOLATION");

        // ADVISORY LOCK
        JToolBar tbAdv = tb();
        tbAdv.add(new JLabel(" Function: ")); tbAdv.add(advisoryFuncCb);
        advisoryFuncCb.setToolTipText("PostgreSQL advisory locking primitives for distributed synchronization");
        tbAdv.addSeparator();
        tbAdv.add(new JLabel("Lock Key (64-bit int): ")); tbAdv.add(advisoryKeyFld);
        cardPanel.add(tbAdv, "ADVISORY LOCK");

        // LISTEN / NOTIFY
        JToolBar tbPub = tb();
        tbPub.add(new JLabel(" Operation: ")); tbPub.add(pubsubOpCb);
        pubsubOpCb.setToolTipText("PostgreSQL pub-sub messaging");
        tbPub.addSeparator();
        tbPub.add(new JLabel("Channel: ")); tbPub.add(pubsubChanFld);
        tbPub.addSeparator();
        tbPub.add(new JLabel("Payload: ")); tbPub.add(pubsubPayloadFld);
        cardPanel.add(tbPub, "LISTEN / NOTIFY");
    }

    private void buildSetSection() {
        setSection.setBorder(new TitledBorder("SET — columns to update (click '+' to add more)"));
        JScrollPane scroll = new JScrollPane(juSetRowsCtr);
        scroll.setPreferredSize(new Dimension(600, 90));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        JButton addBtn = new JButton("+ Add Column");
        addBtn.addActionListener(e -> addSetRow());
        setSection.add(scroll, BorderLayout.CENTER);
        setSection.add(addBtn, BorderLayout.EAST);
        setSection.setVisible(false);
    }

    private JToolBar buildRunToolbar() {
        JToolBar tb = tb();
        JButton previewBtn = new JButton("Preview SQL");
        JButton runBtn     = colorBtn("  Run  ", new Color(40, 100, 190));
        JButton copyBtn    = new JButton("Copy SQL");
        JCheckBox autoCommitCb = new JCheckBox("Auto-Commit", true);
        JButton commitBtn   = new JButton("Commit");
        JButton rollbackBtn = new JButton("Rollback");
        commitBtn.setEnabled(false);
        rollbackBtn.setEnabled(false);

        autoCommitCb.addActionListener(e -> {
            boolean ac = autoCommitCb.isSelected();
            try { if (dbManager.connection != null) dbManager.connection.setAutoCommit(ac); } catch (Exception ex) { /* ignore */ }
            commitBtn.setEnabled(!ac);
            rollbackBtn.setEnabled(!ac);
        });
        commitBtn.addActionListener(e -> {
            try { dbManager.connection.commit(); setStatus("Transaction committed.", true); }
            catch (Exception ex) { setStatus("Commit error: " + ex.getMessage(), false); }
        });
        rollbackBtn.addActionListener(e -> {
            try { dbManager.connection.rollback(); setStatus("Transaction rolled back.", true); }
            catch (Exception ex) { setStatus("Rollback error: " + ex.getMessage(), false); }
        });
        previewBtn.addActionListener(e -> { String s = buildSql(); if (s != null) sqlPreview.setText(s); });
        runBtn.addActionListener(e -> { String s = buildSql(); if (s != null) { sqlPreview.setText(s); runSql(s); } });
        copyBtn.addActionListener(e -> {
            String txt = sqlPreview.getText().trim();
            if (!txt.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(txt), null);
                setStatus("SQL copied to clipboard.", true);
            }
        });

        JButton templatesBtn = new JButton("📝 SQL Templates ▾");
        templatesBtn.setToolTipText("Browse and insert advanced SQL templates (CTEs, Window frames, Indexes, Advisory locks...)");
        templatesBtn.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            com.dbtool.util.SqlSnippetsLibrary.populatePopupMenu(menu, sql -> {
                sqlPreview.setText(sql);
                setStatus("Template loaded into editor.", true);
            });
            menu.show(templatesBtn, 0, templatesBtn.getHeight());
        });

        JButton cheatSheetBtn = new JButton("💡 Cheat Sheet");
        cheatSheetBtn.setToolTipText("Open interactive Advanced SQL Mastery Cheat Sheet");
        cheatSheetBtn.addActionListener(e -> {
            new com.dbtool.panels.SqlMasteryReferenceDialog(SwingUtilities.getWindowAncestor(this), sql -> {
                sqlPreview.setText(sql);
                setStatus("Template loaded into editor.", true);
            }).setVisible(true);
        });

        tb.add(previewBtn);
        tb.add(runBtn);
        tb.add(copyBtn);
        tb.addSeparator();
        tb.add(templatesBtn);
        tb.add(cheatSheetBtn);
        tb.addSeparator();
        tb.add(autoCommitCb);
        tb.add(commitBtn);
        tb.add(rollbackBtn);
        return tb;
    }

    private JPanel buildHistoryPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(new TitledBorder("Query History"));

        historyJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyJList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPopupMenu popup = new JPopupMenu();
        JMenuItem menuCopy  = new JMenuItem("Copy SQL");
        JMenuItem menuReRun = new JMenuItem("Re-run");
        popup.add(menuCopy);
        popup.add(menuReRun);
        historyJList.setComponentPopupMenu(popup);

        JToolBar tb = tb();
        JButton copySql   = new JButton("Copy SQL");
        JButton reRun     = new JButton("Re-run");
        JButton exportCsv = new JButton("Export CSV");
        JButton clear     = new JButton("Clear");

        Runnable copyAct  = () -> {
            int i = historyJList.getSelectedIndex();
            if (i < 0) return;
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(rawHistory.get(i)), null);
            setStatus("SQL copied.", true);
        };
        Runnable reRunAct = () -> {
            int i = historyJList.getSelectedIndex();
            if (i >= 0) runSql(rawHistory.get(i));
        };

        copySql.addActionListener(e -> copyAct.run());
        menuCopy.addActionListener(e -> copyAct.run());
        reRun.addActionListener(e -> reRunAct.run());
        menuReRun.addActionListener(e -> reRunAct.run());
        historyJList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) copyAct.run();
            }
        });
        exportCsv.addActionListener(e -> {
            if (rawHistory.isEmpty()) { setStatus("History is empty.", false); return; }
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("query_history.csv"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try (PrintWriter pw = new PrintWriter(new FileWriter(fc.getSelectedFile()))) {
                pw.println("\"Timestamp\",\"SQL\"");
                for (int i = 0; i < historyModel.size(); i++) {
                    String entry = historyModel.getElementAt(i);
                    String ts    = entry.substring(0, entry.indexOf(']') + 1);
                    pw.printf("\"%s\",\"%s\"%n", ts, rawHistory.get(i).replace("\"", "\"\""));
                }
                setStatus("History exported.", true);
            } catch (Exception ex) {
                setStatus("Export failed: " + ex.getMessage(), false);
            }
        });
        clear.addActionListener(e -> { rawHistory.clear(); historyModel.clear(); setStatus("History cleared.", true); });

        tb.add(copySql);
        tb.add(reRun);
        tb.add(exportCsv);
        tb.add(clear);

        p.add(new JScrollPane(historyJList), BorderLayout.CENTER);
        p.add(tb, BorderLayout.SOUTH);
        return p;
    }

    // ── Event handlers ────────────────────────────────────────────────

    private void onOperationChanged() {
        String op = (String) operationCb.getSelectedItem();
        if (op == null) return;
        cardLayout.show(cardPanel, op);
        setSection.setVisible("UPDATE".equals(op) || "UPDATE + JOIN".equals(op));
        boolean tableRequired = !"TRANSACTION ISOLATION".equals(op) && !"ADVISORY LOCK".equals(op) && !"LISTEN / NOTIFY".equals(op);
        mainTableCb.setEnabled(tableRequired);
        refreshMainTableColumns();
        revalidate();
        repaint();
    }

    private void refreshMainTableColumns() {
        String tbl = (String) mainTableCb.getSelectedItem();
        if (tbl == null || dbManager.connection == null) return;
        
        java.util.List<String> cols = dbManager.getColumnNames(tbl);
        
        refreshCols(cols, updateWhereColCb);
        refreshCols(cols, deleteColCb);
        refreshCols(cols, alterOldColCb);
        refreshCols(cols, indexColCb);
        refreshCols(cols, juOnMainCb);
        refreshCols(cols, upsertConflictColCb);
        refreshJuWhereTables();
        
        // Also update set rows here directly instead of querying DB again
        for (SetRow sr : juSetRows) {
            Object prev = sr.colCb.getSelectedItem();
            sr.colCb.removeAllItems();
            for (String c : cols) sr.colCb.addItem(c);
            if (prev != null) sr.colCb.setSelectedItem(prev);
        }
    }

    private void refreshJuWhereTables() {
        String mainTbl = (String) mainTableCb.getSelectedItem();
        String joinTbl = (String) juJoinCb.getSelectedItem();
        Object prev = juWhereTableCb.getSelectedItem();
        juWhereTableCb.removeAllItems();
        if (mainTbl != null) juWhereTableCb.addItem(mainTbl);
        if (joinTbl != null) juWhereTableCb.addItem(joinTbl);
        if (prev != null) juWhereTableCb.setSelectedItem(prev);
    }

    private void refreshAlterFields() {
        if (alterOpCb.getSelectedItem() == null) return;
        String op = (String) alterOpCb.getSelectedItem();
        alterOldColCb.setEnabled(!op.equals("Add Column") && !op.equals("Rename Table"));
        alterNewNameFld.setEnabled(!op.equals("Drop Column") && !op.equals("Change Column Type"));
        alterTypeFld.setEnabled(op.equals("Add Column") || op.equals("Change Column Type"));
    }

    private void refreshSetRowCols() {
        String tbl = (String) mainTableCb.getSelectedItem();
        if (tbl == null || dbManager.connection == null) return;
        List<String> cols = dbManager.getColumnNames(tbl);
        for (SetRow sr : juSetRows) {
            Object prev = sr.colCb.getSelectedItem();
            sr.colCb.removeAllItems();
            for (String c : cols) sr.colCb.addItem(c);
            if (prev != null) sr.colCb.setSelectedItem(prev);
        }
    }

    // ── Public refresh ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void refreshTables() {
        if (dbManager == null || dbManager.connection == null) {
            for (JComboBox<String> cb : new JComboBox[]{mainTableCb, juJoinCb, updateWhereColCb, deleteColCb, alterOldColCb, indexColCb, juOnMainCb, juOnJoinCb, juWhereTableCb, juWhereColCb, upsertConflictColCb}) {
                cb.removeAllItems();
            }
            setStatus("Disconnected.", false);
            return;
        }
        List<String> tables = dbManager.getTableNames();
        for (JComboBox<String> cb : new JComboBox[]{mainTableCb, juJoinCb}) {
            cb.removeAllItems();
            for (String t : tables) cb.addItem(t);
        }
        refreshMainTableColumns();
        refreshCols((String) juJoinCb.getSelectedItem(), juOnJoinCb);
        setStatus("Ready — " + tables.size() + " tables loaded.", true);
    }

    // ── SQL builder ───────────────────────────────────────────────────

    private String buildSql() {
        String op  = (String) operationCb.getSelectedItem();
        String tbl = (String) mainTableCb.getSelectedItem();
        boolean tableRequired = !"TRANSACTION ISOLATION".equals(op) && !"ADVISORY LOCK".equals(op) && !"LISTEN / NOTIFY".equals(op);
        if (tableRequired && tbl == null) { setStatus("Select a table.", false); return null; }

        switch (op) {
            case "UPDATE": {
                String whereCol = (String) updateWhereColCb.getSelectedItem();
                String whereVal = updateWhereValFld.getText().trim();
                
                StringBuilder set = new StringBuilder();
                boolean first = true;
                for (SetRow sr : juSetRows) {
                    String col = (String) sr.colCb.getSelectedItem();
                    String val = sr.valFld.getText().trim();
                    if (col == null || val.isEmpty()) continue;
                    if (!first) set.append(", ");
                    set.append(q(col)).append(" = '").append(val.replace("'", "''")).append("'");
                    first = false;
                }
                if (first) { setStatus("Fill in at least one SET column value.", false); return null; }
                
                String sql = "UPDATE " + q(tbl) + " SET " + set;
                if (whereCol != null && !whereVal.isEmpty()) {
                    sql += " WHERE " + q(whereCol) + " = '" + whereVal.replace("'", "''") + "'";
                }
                return sql;
            }
            case "UPDATE + JOIN": {
                String joinTbl = (String) juJoinCb.getSelectedItem();
                String onMain  = (String) juOnMainCb.getSelectedItem();
                String onJoin  = (String) juOnJoinCb.getSelectedItem();
                String whereTable = (String) juWhereTableCb.getSelectedItem();
                String whereCol = (String) juWhereColCb.getSelectedItem();
                String whereVal = juWhereValFld.getText().trim();
                if (joinTbl == null || onMain == null || onJoin == null) {
                    setStatus("Fill in JOIN table and ON columns.", false); return null;
                }
                StringBuilder set = new StringBuilder();
                boolean first = true;
                for (SetRow sr : juSetRows) {
                    String col = (String) sr.colCb.getSelectedItem();
                    String val = sr.valFld.getText().trim();
                    if (col == null || val.isEmpty()) continue;
                    if (!first) set.append(",\n    ");
                    set.append(q(col)).append(" = '").append(val.replace("'", "''")).append("'");
                    first = false;
                }
                if (first) { setStatus("Fill in at least one SET column value.", false); return null; }
                StringBuilder sql = new StringBuilder("UPDATE " + q(tbl) + "\nSET " + set);
                sql.append("\nFROM ").append(q(joinTbl));
                sql.append("\nWHERE ").append(q(tbl)).append(".").append(q(onMain))
                   .append(" = ").append(q(joinTbl)).append(".").append(q(onJoin));
                if (whereTable != null && whereCol != null && !whereVal.isEmpty()) {
                    sql.append("\n  AND ").append(q(whereTable)).append(".").append(q(whereCol))
                       .append(" = '").append(whereVal.replace("'", "''")).append("'");
                }
                return sql.toString();
            }
            case "DELETE": {
                String col = (String) deleteColCb.getSelectedItem();
                String val = deletePkVal.getText().trim();
                if (col == null || val.isEmpty()) { setStatus("Fill in WHERE column and value.", false); return null; }
                return "DELETE FROM " + q(tbl) + " WHERE " + q(col) + " = '" + val.replace("'", "''") + "'";
            }
            case "ALTER TABLE": {
                String altOp   = (String) alterOpCb.getSelectedItem();
                String oldCol  = (String) alterOldColCb.getSelectedItem();
                String newName = alterNewNameFld.getText().trim();
                String type    = alterTypeFld.getText().trim();
                switch (altOp) {
                    case "Add Column":
                        if (newName.isEmpty() || type.isEmpty()) { setStatus("Enter column name and data type.", false); return null; }
                        return "ALTER TABLE " + q(tbl) + " ADD COLUMN " + q(newName) + " " + type;
                    case "Rename Column":
                        if (oldCol == null || newName.isEmpty()) { setStatus("Select column and enter new name.", false); return null; }
                        return "ALTER TABLE " + q(tbl) + " RENAME COLUMN " + q(oldCol) + " TO " + q(newName);
                    case "Drop Column":
                        if (oldCol == null) { setStatus("Select a column to drop.", false); return null; }
                        return "ALTER TABLE " + q(tbl) + " DROP COLUMN " + q(oldCol);
                    case "Rename Table":
                        if (newName.isEmpty()) { setStatus("Enter new table name.", false); return null; }
                        return "ALTER TABLE " + q(tbl) + " RENAME TO " + q(newName);
                    case "Change Column Type":
                        if (oldCol == null || type.isEmpty()) { setStatus("Select column and enter new type.", false); return null; }
                        return "ALTER TABLE " + q(tbl) + " ALTER COLUMN " + q(oldCol) + " TYPE " + type;
                    default: return null;
                }
            }
            case "TRUNCATE":
                return "TRUNCATE TABLE " + q(tbl);
            case "CREATE INDEX": {
                String name = indexNameFld.getText().trim();
                String col  = (String) indexColCb.getSelectedItem();
                if (name.isEmpty() || col == null) { setStatus("Fill in index name and select column.", false); return null; }
                StringBuilder sb = new StringBuilder("CREATE ");
                if (indexUniqueCb.isSelected()) sb.append("UNIQUE ");
                sb.append("INDEX ").append(q(name)).append(" ON ").append(q(tbl));
                String idxType = (String) indexTypeCb.getSelectedItem();
                if (idxType != null && !idxType.startsWith("BTREE")) {
                    String method = idxType.split(" ")[0];
                    sb.append(" USING ").append(method);
                }
                sb.append(" (").append(q(col)).append(")");
                String inc = indexIncludeFld.getText().trim();
                if (!inc.isEmpty()) {
                    if (!inc.startsWith("(") && !inc.endsWith(")")) inc = "(" + inc + ")";
                    sb.append(" INCLUDE ").append(inc);
                }
                String whr = indexWhereFld.getText().trim();
                if (!whr.isEmpty()) {
                    if (!whr.toUpperCase().startsWith("WHERE ")) sb.append(" WHERE ");
                    else sb.append(" ");
                    sb.append(whr);
                }
                return sb.toString();
            }
            case "INSERT / UPSERT": {
                String vals = upsertValuesFld.getText().trim();
                if (vals.isEmpty()) { setStatus("Enter VALUES to insert.", false); return null; }
                if (!vals.startsWith("(")) vals = "(" + vals + ")";
                String conflictCol = (String) upsertConflictColCb.getSelectedItem();
                String action = (String) upsertActionCb.getSelectedItem();
                StringBuilder sb = new StringBuilder();
                sb.append("INSERT INTO ").append(q(tbl)).append(" VALUES ").append(vals);
                if (action != null && action.startsWith("DO UPDATE")) {
                    String setClause = upsertSetFld.getText().trim();
                    if (setClause.isEmpty()) setClause = "updated_at = NOW()";
                    sb.append("\nON CONFLICT (").append(conflictCol != null ? q(conflictCol) : "id").append(") DO UPDATE SET ").append(setClause);
                } else if (action != null && action.startsWith("DO NOTHING")) {
                    sb.append("\nON CONFLICT (").append(conflictCol != null ? q(conflictCol) : "id").append(") DO NOTHING");
                } else if (action != null && action.startsWith("ON DUPLICATE")) {
                    String setClause = upsertSetFld.getText().trim();
                    sb.append("\nON DUPLICATE KEY UPDATE ").append(setClause.isEmpty() ? "updated_at = NOW()" : setClause);
                }
                return sb.toString();
            }
            case "REFRESH MATERIALIZED VIEW": {
                String vName = matViewNameFld.getText().trim();
                if (vName.isEmpty()) vName = tbl;
                if (vName == null || vName.isEmpty()) { setStatus("Enter materialized view name.", false); return null; }
                StringBuilder sb = new StringBuilder("REFRESH MATERIALIZED VIEW ");
                if (matViewConcurrentlyCb.isSelected()) sb.append("CONCURRENTLY ");
                sb.append(q(vName));
                if (!matViewWithDataCb.isSelected()) sb.append(" WITH NO DATA");
                return sb.toString();
            }
            case "VACUUM / ANALYZE": {
                String mode = (String) vacuumModeCb.getSelectedItem();
                String scope = (String) vacuumScopeCb.getSelectedItem();
                StringBuilder sb = new StringBuilder(mode != null ? mode : "VACUUM");
                if ("Selected Table".equals(scope) && tbl != null) {
                    sb.append(" ").append(q(tbl));
                }
                return sb.toString();
            }
            case "TRANSACTION ISOLATION": {
                String lvl = (String) isolationLevelCb.getSelectedItem();
                if (lvl != null && lvl.contains(" ")) lvl = lvl.substring(0, lvl.indexOf(" "));
                String access = (String) isolationAccessCb.getSelectedItem();
                String scope = (String) isolationScopeCb.getSelectedItem();
                if ("SESSION".equals(scope)) {
                    return "SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL " + lvl + " " + access + ";";
                } else {
                    return "SET TRANSACTION ISOLATION LEVEL " + lvl + " " + access + ";";
                }
            }
            case "ADVISORY LOCK": {
                String func = (String) advisoryFuncCb.getSelectedItem();
                String key = advisoryKeyFld.getText().trim();
                if (key.isEmpty()) key = "42";
                return "SELECT " + func + "(" + key + ");";
            }
            case "LISTEN / NOTIFY": {
                String pop = (String) pubsubOpCb.getSelectedItem();
                String ch = pubsubChanFld.getText().trim();
                if (ch.isEmpty()) ch = "events";
                if ("NOTIFY".equals(pop)) {
                    String p = pubsubPayloadFld.getText().trim();
                    return "NOTIFY " + ch + ", '" + p.replace("'", "''") + "';";
                } else if ("LISTEN".equals(pop)) {
                    return "LISTEN " + ch + ";";
                } else if ("UNLISTEN".equals(pop)) {
                    return "UNLISTEN " + ch + ";";
                } else {
                    return "UNLISTEN *;";
                }
            }
            default: return null;
        }
    }

    // ── SQL Execution ─────────────────────────────────────────────────

    private void runSql(String sql) {
        if (dbManager == null || dbManager.connection == null) { setStatus("Not connected.", false); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
                "SQL to execute:\n\n" + sql + "\n\nProceed?",
                "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        setStatus("Executing SQL...", true);
        new Thread(() -> {
            try {
                int rows = dbManager.executeUpdate(sql);
                SwingUtilities.invokeLater(() -> {
                    setStatus("Done — " + rows + " row(s) affected.", true);
                    String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    rawHistory.add(0, sql);
                    historyModel.add(0, "[" + ts + "]  " + sql.replaceAll("\\s+", " ").trim());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Error: " + ex.getMessage(), false);
                    JOptionPane.showMessageDialog(DataOpsPanel.this, ex.getMessage(), "SQL Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void refreshCols(java.util.List<String> cols, JComboBox<String> target) {
        Object prev = target.getSelectedItem();
        target.removeAllItems();
        for (String col : cols) target.addItem(col);
        if (prev != null) target.setSelectedItem(prev);
    }

    private void refreshCols(String table, JComboBox<String> target) {
        if (table == null || dbManager.connection == null) return;
        Object prev = target.getSelectedItem();
        target.removeAllItems();
        for (String col : dbManager.getColumnNames(table)) target.addItem(col);
        if (prev != null) target.setSelectedItem(prev);
    }

    private void addSetRow() {
        SetRow row = new SetRow();
        juSetRows.add(row);
        juSetRowsCtr.add(row);
        juSetRowsCtr.revalidate();
        juSetRowsCtr.repaint();
    }

    private void setStatus(String msg, boolean ok) {
        statusLbl.setText(msg);
        statusLbl.setForeground(ok ? new Color(0, 130, 0) : Color.RED);
    }

    private static final java.util.Set<String> SQL_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "user", "order", "group", "select", "where", "from", "table", "update", "delete", "insert", "into", "values", "set", "limit", "offset", "all", "any", "as", "asc", "desc", "between", "case", "cast", "check", "column", "constraint", "create", "cross", "current_date", "current_time", "current_timestamp", "current_user", "default", "distinct", "drop", "else", "end", "except", "false", "for", "foreign", "full", "grant", "having", "in", "inner", "intersect", "is", "join", "left", "like", "natural", "not", "null", "on", "or", "outer", "primary", "references", "right", "table", "then", "to", "true", "union", "unique", "using", "when", "with"
    ));

    private String q(String name) {
        if (name == null) return "";
        if (name.matches("^[a-z_][a-z0-9_]*$") && !SQL_KEYWORDS.contains(name)) {
            return name;
        }
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private JToolBar tb() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setLayout(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 5, 3));
        return tb;
    }

    private JButton colorBtn(String label, Color bg) {
        JButton b = new JButton(label);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        return b;
    }

    // ── SetRow inner class ────────────────────────────────────────────

    private class SetRow extends JPanel {
        final JComboBox<String> colCb  = new JComboBox<>();
        final JTextField        valFld = new JTextField(16);

        SetRow() {
            setLayout(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 1));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            String tbl = (String) mainTableCb.getSelectedItem();
            if (tbl != null && dbManager.connection != null)
                for (String col : dbManager.getColumnNames(tbl)) colCb.addItem(col);

            JButton removeBtn = new JButton("✕");
            removeBtn.setPreferredSize(new Dimension(26, 22));
            removeBtn.addActionListener(e -> {
                juSetRows.remove(this);
                juSetRowsCtr.remove(this);
                juSetRowsCtr.revalidate();
                juSetRowsCtr.repaint();
            });

            add(new JLabel("SET "));
            add(colCb);
            add(new JLabel(" = "));
            add(valFld);
            add(removeBtn);
        }
    }
}
