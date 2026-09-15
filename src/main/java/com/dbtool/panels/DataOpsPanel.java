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
        "UPDATE", "UPDATE + JOIN", "DELETE", "ALTER TABLE", "TRUNCATE", "CREATE INDEX"
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
    private final JComboBox<String> juTypeCb   = new JComboBox<>(new String[]{"INNER JOIN", "LEFT JOIN"});
    private final JComboBox<String> juOnMainCb = new JComboBox<>();   // target.col
    private final JComboBox<String> juOnJoinCb = new JComboBox<>();   // join.col
    private final JTextField        juWhereFld = new JTextField(22);

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
    private final JTextField        indexNameFld  = new JTextField(16);
    private final JComboBox<String> indexColCb    = new JComboBox<>();
    private final JCheckBox         indexUniqueCb = new JCheckBox("UNIQUE");

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
        juJoinCb.addActionListener(e -> refreshCols((String) juJoinCb.getSelectedItem(), juOnJoinCb));

        juSetRowsCtr.setLayout(new BoxLayout(juSetRowsCtr, BoxLayout.Y_AXIS));
        sqlPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sqlPreview.setEditable(false);

        // Assemble north toolbar stack
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(buildMainToolbar());      // Operation + Table
        buildCardPanel();
        north.add(cardPanel);               // Per-operation controls
        buildSetSection();
        north.add(setSection);              // SET rows (UPDATE and UPDATE+JOIN)
        north.add(buildRunToolbar());       // Preview / Run / Copy / Commit / Rollback

        // Center: SQL Preview above, History below
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(new TitledBorder("Generated SQL"));
        previewPanel.add(new JScrollPane(sqlPreview), BorderLayout.CENTER);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, previewPanel, buildHistoryPanel());
        centerSplit.setResizeWeight(0.38);
        centerSplit.setDividerSize(6);

        add(north, BorderLayout.NORTH);
        add(centerSplit, BorderLayout.CENTER);

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
        tbJ.add(new JLabel("WHERE: "));    tbJ.add(juWhereFld);
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

        // CREATE INDEX
        JToolBar tbI = tb();
        tbI.add(new JLabel(" Index Name: ")); tbI.add(indexNameFld);
        tbI.addSeparator();
        tbI.add(new JLabel("Column: "));      tbI.add(indexColCb);
        tbI.add(indexUniqueCb);
        cardPanel.add(tbI, "CREATE INDEX");
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

        tb.add(previewBtn);
        tb.add(runBtn);
        tb.add(copyBtn);
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
        refreshMainTableColumns();
        revalidate();
        repaint();
    }

    private void refreshMainTableColumns() {
        String tbl = (String) mainTableCb.getSelectedItem();
        if (tbl == null) return;
        refreshCols(tbl, updateWhereColCb);
        refreshCols(tbl, deleteColCb);
        refreshCols(tbl, alterOldColCb);
        refreshCols(tbl, indexColCb);
        refreshCols(tbl, juOnMainCb);
        refreshSetRowCols();
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
        List<String> tables = dbManager.getTableNames();
        if (tables.isEmpty()) return;
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
        if (tbl == null) { setStatus("Select a table.", false); return null; }

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
                String where   = juWhereFld.getText().trim();
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
                if (!where.isEmpty()) sql.append("\n  AND ").append(where);
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
                return "CREATE " + (indexUniqueCb.isSelected() ? "UNIQUE " : "") + "INDEX " + q(name)
                     + " ON " + q(tbl) + " (" + q(col) + ")";
            }
            default: return null;
        }
    }

    // ── SQL Execution ─────────────────────────────────────────────────

    private void runSql(String sql) {
        if (dbManager.connection == null) { setStatus("Not connected.", false); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
                "SQL to execute:\n\n" + sql + "\n\nProceed?",
                "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try (Statement st = dbManager.connection.createStatement()) {
            int rows = st.executeUpdate(sql);
            setStatus("Done — " + rows + " row(s) affected.", true);
            String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
            rawHistory.add(0, sql);
            historyModel.add(0, "[" + ts + "]  " + sql.replaceAll("\\s+", " ").trim());
        } catch (Exception ex) {
            setStatus("Error: " + ex.getMessage(), false);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "SQL Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

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
        tb.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 3));
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
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 1));
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
