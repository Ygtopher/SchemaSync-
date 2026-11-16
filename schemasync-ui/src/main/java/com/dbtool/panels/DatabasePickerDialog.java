package com.dbtool.panels;

import com.dbtool.ui.components.UiIconFactory;
import com.dbtool.util.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Interactive database selector dialog that displays all databases discovered
 * on a live PostgreSQL / MySQL server and allows the user to filter, browse,
 * and pick which database to connect to.
 *
 * Creator: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class DatabasePickerDialog extends JDialog {

    private final List<String> allDatabases = new ArrayList<>();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private JList<String> dbList;
    private JTextField searchField;
    private JLabel countLabel;
    private String selectedDatabase = null;
    private boolean approved = false;

    public DatabasePickerDialog(Window owner, String host, int port, String user, List<String> databases, String initialSelection) {
        super(owner, "Select Database — SchemaSync", ModalityType.APPLICATION_MODAL);
        if (databases != null) {
            this.allDatabases.addAll(databases);
        }
        
        setSize(480, 480);
        setMinimumSize(new Dimension(380, 360));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // --- 1. Top Header Banner ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        
        JLabel iconLabel = new JLabel(UiIconFactory.getIcon(UiIconFactory.IconType.DATABASE, 26, ThemeManager.getPrimaryActionColor()));
        
        JPanel textPanel = new JPanel(new BorderLayout(0, 3));
        textPanel.setOpaque(false);
        
        String hostPortText = (host != null && !host.isEmpty()) ? (host + ":" + (port > 0 ? port : 5432)) : "Database Server";
        JLabel titleLabel = new JLabel("Databases on " + hostPortText);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        
        String userText = (user != null && !user.isEmpty()) ? user : "authenticated user";
        JLabel subtitleLabel = new JLabel("Discovered " + this.allDatabases.size() + " database(s) accessible by '" + userText + "'");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(11.5f));
        subtitleLabel.setForeground(Color.GRAY);
        
        textPanel.add(titleLabel, BorderLayout.NORTH);
        textPanel.add(subtitleLabel, BorderLayout.SOUTH);
        
        headerPanel.add(iconLabel, BorderLayout.WEST);
        headerPanel.add(textPanel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // --- 2. Center Panel: Search & List ---
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));

        // Filter search field
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Filter databases (type to search)...");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterList(); }
            @Override public void removeUpdate(DocumentEvent e) { filterList(); }
            @Override public void changedUpdate(DocumentEvent e) { filterList(); }
        });
        searchField.addActionListener(e -> confirmSelection());
        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    dbList.requestFocusInWindow();
                }
            }
        });
        centerPanel.add(searchField, BorderLayout.NORTH);

        // Database JList
        dbList = new JList<>(listModel);
        dbList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dbList.setCellRenderer(new DatabaseCellRenderer(initialSelection));
        dbList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    confirmSelection();
                }
            }
        });
        dbList.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    confirmSelection();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(dbList);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeManager.isDarkTheme(this) ? new Color(55, 65, 81) : new Color(215, 220, 228), 1));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Count label below list
        countLabel = new JLabel(" ");
        countLabel.setFont(countLabel.getFont().deriveFont(11f));
        countLabel.setForeground(Color.GRAY);
        centerPanel.add(countLabel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        // --- 3. Bottom Action Buttons ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(6, 16, 12, 16));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> {
            approved = false;
            setVisible(false);
        });

        JButton selectBtn = new JButton("Select & Connect");
        selectBtn.setFont(selectBtn.getFont().deriveFont(Font.BOLD));
        selectBtn.setIcon(UiIconFactory.getIcon(UiIconFactory.IconType.CONNECT, 12, ThemeManager.getPrimaryActionColor()));
        selectBtn.addActionListener(e -> confirmSelection());

        buttonsPanel.add(cancelBtn);
        buttonsPanel.add(selectBtn);
        bottomPanel.add(buttonsPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Populate and select initial
        filterList();
        if (initialSelection != null && !initialSelection.isEmpty() && listModel.contains(initialSelection)) {
            dbList.setSelectedValue(initialSelection, true);
            this.selectedDatabase = initialSelection;
        } else if (listModel.size() > 0) {
            // Prefer custom user database over "postgres" if available
            String preferred = listModel.get(0);
            for (int i = 0; i < listModel.size(); i++) {
                String candidate = listModel.get(i);
                if (!"postgres".equalsIgnoreCase(candidate) && !"template1".equalsIgnoreCase(candidate)) {
                    preferred = candidate;
                    break;
                }
            }
            dbList.setSelectedValue(preferred, true);
            this.selectedDatabase = preferred;
        }

        // Keybindings: ENTER to select, ESCAPE to close
        getRootPane().setDefaultButton(selectBtn);
        getRootPane().registerKeyboardAction(
                (ActionEvent e) -> { approved = false; setVisible(false); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void filterList() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        String currentSelected = dbList != null ? dbList.getSelectedValue() : null;
        
        listModel.clear();
        for (String db : allDatabases) {
            if (q.isEmpty() || db.toLowerCase().contains(q)) {
                listModel.addElement(db);
            }
        }

        if (currentSelected != null && listModel.contains(currentSelected)) {
            dbList.setSelectedValue(currentSelected, true);
        } else if (listModel.size() > 0 && dbList.getSelectedIndex() < 0) {
            dbList.setSelectedIndex(0);
        }

        if (countLabel != null) {
            if (q.isEmpty()) {
                countLabel.setText("Showing all " + listModel.size() + " database(s)");
            } else {
                countLabel.setText("Matching " + listModel.size() + " of " + allDatabases.size() + " database(s)");
            }
        }
    }

    private void confirmSelection() {
        String sel = dbList.getSelectedValue();
        if (sel != null && !sel.trim().isEmpty()) {
            this.selectedDatabase = sel.trim();
            this.approved = true;
            setVisible(false);
        }
    }

    public boolean isApproved() {
        return approved;
    }

    public String getSelectedDatabase() {
        return selectedDatabase;
    }

    public JTextField getSearchField() { return searchField; }
    public DefaultListModel<String> getListModel() { return listModel; }
    public JList<String> getDbList() { return dbList; }

    /**
     * Helper to show the dialog and return the selected database, or null if cancelled.
     */
    public static String showPicker(Window owner, String host, int port, String user, List<String> databases, String initialSelection) {
        DatabasePickerDialog dialog = new DatabasePickerDialog(owner, host, port, user, databases, initialSelection);
        dialog.setVisible(true);
        return dialog.isApproved() ? dialog.getSelectedDatabase() : null;
    }

    /**
     * Clean custom cell renderer for discovered databases.
     */
    private static class DatabaseCellRenderer extends DefaultListCellRenderer {
        private final String initialSelection;

        public DatabaseCellRenderer(String initialSelection) {
            this.initialSelection = initialSelection;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value != null) {
                String dbName = value.toString();
                label.setText(dbName);
                label.setIcon(UiIconFactory.getIcon(UiIconFactory.IconType.DATABASE, 14, isSelected ? Color.WHITE : ThemeManager.getPrimaryActionColor()));
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                
                if ("postgres".equalsIgnoreCase(dbName)) {
                    label.setText(dbName + "  (maintenance)");
                } else if (initialSelection != null && initialSelection.equalsIgnoreCase(dbName)) {
                    label.setText(dbName + "  ★ current");
                }
            }
            return label;
        }
    }
}
