package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.List;

/**
 * TablePlus / Supabase-inspired side-by-side Row Inspector & Quick Editor Drawer.
 * Displays all columns of the currently selected table row in a searchable vertical sidebar
 * with PK/FK badges, NULL toggles, 1-click field copy, and parameterized in-place row saving.
 */
public class RowInspectorDrawer extends JPanel {

    private static class FieldRow {
        final String colName;
        final boolean isPk;
        final boolean isFk;
        final Object originalValue;
        final JTextField valueField;
        final JCheckBox nullCheck;
        final JPanel rowContainer;

        FieldRow(String colName, boolean isPk, boolean isFk, Object originalValue,
                 JTextField valueField, JCheckBox nullCheck, JPanel rowContainer) {
            this.colName = colName;
            this.isPk = isPk;
            this.isFk = isFk;
            this.originalValue = originalValue;
            this.valueField = valueField;
            this.nullCheck = nullCheck;
            this.rowContainer = rowContainer;
        }
    }

    private final DatabaseManager dbManager;
    private final Runnable onCloseDrawer;
    private final Runnable onRowUpdated;

    private final JLabel headerTitleLabel = new JLabel("📐 Row Inspector");
    private final JTextField filterField = new JTextField();
    private final JPanel fieldsListPanel = new JPanel(new GridBagLayout());
    private final JLabel statusFooterLabel = new JLabel("Select any row in the table to inspect");
    private final JButton saveChangesBtn = new JButton("💾 Save Changes");
    private final JButton revertBtn = new JButton("↺ Revert");

    private String currentTableName;
    private JTable currentTable;
    private int currentModelRow = -1;
    private final List<FieldRow> fieldRows = new ArrayList<>();

    public RowInspectorDrawer(DatabaseManager dbManager, Runnable onCloseDrawer, Runnable onRowUpdated) {
        this.dbManager = dbManager;
        this.onCloseDrawer = onCloseDrawer;
        this.onRowUpdated = onRowUpdated;

        setLayout(new BorderLayout(0, 0));
        setPreferredSize(new Dimension(340, 400));
        setMinimumSize(new Dimension(260, 200));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0,
                ThemeManager.isDarkMode() ? new Color(48, 56, 70) : new Color(203, 213, 225)));

        // 1. Header Bar
        JPanel topHeader = new JPanel(new BorderLayout(6, 6));
        topHeader.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setOpaque(false);
        headerTitleLabel.setFont(headerTitleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleRow.add(headerTitleLabel, BorderLayout.WEST);

        if (onCloseDrawer != null) {
            JButton closeBtn = new JButton("✕");
            closeBtn.setMargin(new Insets(1, 6, 1, 6));
            closeBtn.setToolTipText("Hide Row Inspector Drawer");
            closeBtn.addActionListener(e -> onCloseDrawer.run());
            titleRow.add(closeBtn, BorderLayout.EAST);
        }
        topHeader.add(titleRow, BorderLayout.NORTH);

        // Search / Filter input inside drawer
        JPanel searchBar = new JPanel(new BorderLayout(4, 0));
        searchBar.setOpaque(false);
        JLabel searchIcon = new JLabel("🔍");
        filterField.putClientProperty("JTextField.placeholderText", "Filter columns in inspector...");
        JButton clearFilterBtn = new JButton("✕");
        clearFilterBtn.setMargin(new Insets(1, 5, 1, 5));
        clearFilterBtn.addActionListener(e -> filterField.setText(""));
        searchBar.add(searchIcon, BorderLayout.WEST);
        searchBar.add(filterField, BorderLayout.CENTER);
        searchBar.add(clearFilterBtn, BorderLayout.EAST);
        topHeader.add(searchBar, BorderLayout.SOUTH);

        add(topHeader, BorderLayout.NORTH);

        // 2. Scrollable Fields Container
        JScrollPane scrollPane = new JScrollPane(fieldsListPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        add(scrollPane, BorderLayout.CENTER);

        // 3. Pinned Action Footer
        JPanel footer = new JPanel(new BorderLayout(6, 4));
        footer.setBorder(new EmptyBorder(8, 10, 8, 10));
        statusFooterLabel.setFont(statusFooterLabel.getFont().deriveFont(11.5f));
        statusFooterLabel.setForeground(ThemeManager.getMutedTextColor());
        footer.add(statusFooterLabel, BorderLayout.NORTH);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnRow.setOpaque(false);
        revertBtn.setEnabled(false);
        saveChangesBtn.setEnabled(false);
        saveChangesBtn.setFont(saveChangesBtn.getFont().deriveFont(Font.BOLD));
        saveChangesBtn.setForeground(ThemeManager.getSuccessColor());
        saveChangesBtn.setToolTipText("Commit edited fields to the database (Ctrl+S)");

        revertBtn.addActionListener(e -> revertChanges());
        saveChangesBtn.addActionListener(e -> saveRowChanges());

        btnRow.add(revertBtn);
        btnRow.add(saveChangesBtn);
        footer.add(btnRow, BorderLayout.SOUTH);

        add(footer, BorderLayout.SOUTH);

        // Filter listener
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { relayoutVisibleFields(); }
            @Override public void removeUpdate(DocumentEvent e) { relayoutVisibleFields(); }
            @Override public void changedUpdate(DocumentEvent e) { relayoutVisibleFields(); }
        });

        // Ctrl+S shortcut inside drawer
        KeyStroke ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ctrlS, "saveInspectorRow");
        getActionMap().put("saveInspectorRow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (saveChangesBtn.isEnabled()) {
                    saveRowChanges();
                }
            }
        });

        showEmptyState();
    }

    public void showEmptyState() {
        currentTableName = null;
        currentTable = null;
        currentModelRow = -1;
        fieldRows.clear();
        fieldsListPanel.removeAll();
        headerTitleLabel.setText("📐 Row Inspector");
        statusFooterLabel.setText("Click any row in the table to inspect & edit");
        saveChangesBtn.setEnabled(false);
        revertBtn.setEnabled(false);
        fieldsListPanel.revalidate();
        fieldsListPanel.repaint();
    }

    public void inspectRow(String tableName, JTable table, int viewRow) {
        if (table == null || viewRow < 0 || viewRow >= table.getRowCount()) {
            showEmptyState();
            return;
        }
        this.currentTableName = tableName;
        this.currentTable = table;
        this.currentModelRow = table.convertRowIndexToModel(viewRow);

        headerTitleLabel.setText("📐 " + (tableName != null ? tableName : "Row") + "  •  Row #" + (viewRow + 1));

        Set<String> pkSet = new HashSet<>();
        Map<String, String[]> fkMap = Collections.emptyMap();
        if (dbManager != null && tableName != null) {
            pkSet.addAll(dbManager.getPrimaryKeys(tableName));
            fkMap = dbManager.getCachedForeignKeys(tableName);
        }

        fieldRows.clear();
        int colCount = table.getModel().getColumnCount();
        for (int c = 0; c < colCount; c++) {
            String colName = table.getModel().getColumnName(c);
            Object val = table.getModel().getValueAt(currentModelRow, c);
            boolean isPk = pkSet.contains(colName);
            boolean isFk = fkMap.containsKey(colName);
            boolean isNull = (val == null);

            JPanel card = new JPanel(new BorderLayout(4, 2));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0,
                            ThemeManager.isDarkMode() ? new Color(38, 45, 58) : new Color(226, 232, 240)),
                    new EmptyBorder(6, 10, 6, 10)
            ));

            JPanel labelTop = new JPanel(new BorderLayout(4, 0));
            labelTop.setOpaque(false);
            String badge = isPk ? "🔑 " : (isFk ? "🔗 " : "");
            JLabel colLbl = new JLabel(badge + colName + (isPk ? " (PK)" : ""));
            colLbl.setFont(colLbl.getFont().deriveFont(isPk ? Font.BOLD : Font.PLAIN, 12f));
            if (isPk) {
                colLbl.setForeground(ThemeManager.getSuccessColor());
            } else if (isFk) {
                colLbl.setForeground(ThemeManager.getPrimaryActionColor());
            }
            labelTop.add(colLbl, BorderLayout.WEST);

            JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightTools.setOpaque(false);
            JCheckBox nullCb = new JCheckBox("NULL", isNull);
            nullCb.setFont(nullCb.getFont().deriveFont(10.5f));
            nullCb.setOpaque(false);

            JButton copyFieldBtn = new JButton("📋");
            copyFieldBtn.setMargin(new Insets(0, 4, 0, 4));
            copyFieldBtn.setFont(copyFieldBtn.getFont().deriveFont(10.5f));
            copyFieldBtn.setToolTipText("Copy " + colName + " value");

            rightTools.add(nullCb);
            rightTools.add(copyFieldBtn);
            labelTop.add(rightTools, BorderLayout.EAST);

            JTextField tf = new JTextField(isNull ? "" : String.valueOf(val));
            tf.setEnabled(!isNull);
            if (isNull) {
                tf.putClientProperty("JTextField.placeholderText", "<NULL>");
            }

            nullCb.addActionListener(e -> {
                boolean checked = nullCb.isSelected();
                tf.setEnabled(!checked);
                if (checked) {
                    tf.putClientProperty("JTextField.placeholderText", "<NULL>");
                }
            });

            copyFieldBtn.addActionListener(e -> {
                String toCopy = nullCb.isSelected() ? "NULL" : tf.getText();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(toCopy), null);
                statusFooterLabel.setText("Copied '" + colName + "' to clipboard.");
            });

            card.add(labelTop, BorderLayout.NORTH);
            card.add(tf, BorderLayout.CENTER);

            fieldRows.add(new FieldRow(colName, isPk, isFk, val, tf, nullCb, card));
        }

        saveChangesBtn.setEnabled(tableName != null && dbManager != null && dbManager.connection != null);
        revertBtn.setEnabled(true);
        statusFooterLabel.setText(colCount + " columns loaded  •  Press Ctrl+S to save");

        relayoutVisibleFields();
    }

    private void relayoutVisibleFields() {
        fieldsListPanel.removeAll();
        String q = filterField.getText().trim().toLowerCase(Locale.ROOT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        int rowIdx = 0;
        for (FieldRow fr : fieldRows) {
            if (!q.isEmpty() && !fr.colName.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            gbc.gridy = rowIdx++;
            fieldsListPanel.add(fr.rowContainer, gbc);
        }

        // Push rows to top
        gbc.gridy = rowIdx;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        fieldsListPanel.add(filler, gbc);

        fieldsListPanel.revalidate();
        fieldsListPanel.repaint();
    }

    private void revertChanges() {
        for (FieldRow fr : fieldRows) {
            boolean isNull = (fr.originalValue == null);
            fr.nullCheck.setSelected(isNull);
            fr.valueField.setEnabled(!isNull);
            fr.valueField.setText(isNull ? "" : String.valueOf(fr.originalValue));
        }
        statusFooterLabel.setText("Reverted unsaved field changes.");
    }

    private void saveRowChanges() {
        if (currentTableName == null || currentTable == null || currentModelRow < 0
                || dbManager == null || dbManager.connection == null) {
            return;
        }

        try {
            List<String> setClauses = new ArrayList<>();
            List<Object> setParams = new ArrayList<>();
            List<String> whereClauses = new ArrayList<>();
            List<Object> whereParams = new ArrayList<>();

            boolean hasPk = fieldRows.stream().anyMatch(r -> r.isPk);

            for (FieldRow fr : fieldRows) {
                Object newVal = fr.nullCheck.isSelected() ? null : fr.valueField.getText();
                setClauses.add(dbManager.quoteColumnName(fr.colName) + " = ?");
                setParams.add(newVal);

                if (hasPk ? fr.isPk : true) {
                    if (fr.originalValue == null) {
                        whereClauses.add(dbManager.quoteColumnName(fr.colName) + " IS NULL");
                    } else {
                        whereClauses.add(dbManager.quoteColumnName(fr.colName) + " = ?");
                        whereParams.add(fr.originalValue);
                    }
                }
            }

            String sql = "UPDATE " + dbManager.quoteTableName(currentTableName)
                    + " SET " + String.join(", ", setClauses)
                    + " WHERE " + String.join(" AND ", whereClauses);

            try (PreparedStatement pstmt = dbManager.connection.prepareStatement(sql)) {
                int idx = 1;
                for (Object v : setParams) {
                    pstmt.setObject(idx++, v);
                }
                for (Object w : whereParams) {
                    pstmt.setObject(idx++, w);
                }
                int updated = pstmt.executeUpdate();
                statusFooterLabel.setText("✅ Saved (" + updated + " row updated)");
                statusFooterLabel.setForeground(ThemeManager.getSuccessColor());
                if (onRowUpdated != null) {
                    onRowUpdated.run();
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to update row:\n" + ex.getMessage(),
                    "Row Inspector Update Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
