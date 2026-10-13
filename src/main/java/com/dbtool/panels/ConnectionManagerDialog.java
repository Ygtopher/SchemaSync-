package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class ConnectionManagerDialog extends JDialog {
    private DatabaseManager dbManager;
    private DefaultListModel<String> profileListModel;
    private JList<String> profileList;
    private JTextField searchField;
    private JLabel countLabel;
    private final List<String> allProfileNames = new ArrayList<>();
    private JTextField hostField;
    private JTextField portField;
    private JTextField userField;
    private JPasswordField passField;
    private JTextField dbNameField;
    
    private boolean isApproved = false;
    private String selectedHost;
    private String selectedPort;
    private String selectedUser;
    private String selectedPass;
    private String selectedDbName;

    public ConnectionManagerDialog(JFrame parent, DatabaseManager dbManager) {
        super(parent, "Connection Profile Manager", true);
        this.dbManager = dbManager;
        
        setSize(760, 480);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // --- Left Panel: Profile List & Search ---
        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));
        leftPanel.setPreferredSize(new Dimension(280, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));
        
        // Search bar
        JPanel searchBoxPanel = new JPanel(new BorderLayout(4, 0));
        searchBoxPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        
        JLabel searchIcon = new JLabel("🔍 ");
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search profiles...");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.setToolTipText("Filter by name, host IP, database, or username (Esc to clear, ↓ to select list)");
        
        JButton clearSearchBtn = new JButton("✕");
        clearSearchBtn.setMargin(new Insets(1, 5, 1, 5));
        clearSearchBtn.setFocusable(false);
        clearSearchBtn.setToolTipText("Clear search filter");
        clearSearchBtn.addActionListener(e -> {
            searchField.setText("");
            searchField.requestFocusInWindow();
        });
        
        searchBoxPanel.add(searchIcon, BorderLayout.WEST);
        searchBoxPanel.add(searchField, BorderLayout.CENTER);
        searchBoxPanel.add(clearSearchBtn, BorderLayout.EAST);
        
        leftPanel.add(searchBoxPanel, BorderLayout.NORTH);
        
        profileListModel = new DefaultListModel<>();
        profileList = new JList<>(profileListModel);
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        leftPanel.add(new JScrollPane(profileList), BorderLayout.CENTER);
        
        JPanel listButtonsPanel = new JPanel(new BorderLayout(5, 5));
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addBtn = new JButton("+");
        addBtn.setToolTipText("Add New Profile");
        JButton delBtn = new JButton("-");
        delBtn.setToolTipText("Delete Selected Profile");
        leftButtons.add(addBtn);
        leftButtons.add(delBtn);
        listButtonsPanel.add(leftButtons, BorderLayout.WEST);
        
        countLabel = new JLabel("0 profiles");
        countLabel.setFont(countLabel.getFont().deriveFont(11f));
        countLabel.setForeground(Color.GRAY);
        JPanel rightCountPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        rightCountPanel.add(countLabel);
        listButtonsPanel.add(rightCountPanel, BorderLayout.EAST);
        
        leftPanel.add(listButtonsPanel, BorderLayout.SOUTH);
        
        // --- Right Panel: Details Form ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10));
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        
        hostField = new JTextField();
        portField = new JTextField();
        userField = new JTextField();
        passField = new JPasswordField();
        dbNameField = new JTextField();
        
        JToggleButton showPassBtn = new JToggleButton("\uD83D\uDC41"); // Eye icon
        showPassBtn.setMargin(new Insets(0, 4, 0, 4));
        showPassBtn.setFocusable(false);
        showPassBtn.addActionListener(e -> {
            if (showPassBtn.isSelected()) {
                passField.setEchoChar((char) 0);
            } else {
                passField.setEchoChar('•');
            }
        });
        JPanel passPanel = new JPanel(new BorderLayout());
        passPanel.add(passField, BorderLayout.CENTER);
        passPanel.add(showPassBtn, BorderLayout.EAST);

        int row = 0;
        addFormRow(formPanel, "Host:", hostField, gbc, row++);
        addFormRow(formPanel, "Port:", portField, gbc, row++);
        addFormRow(formPanel, "Database Name:", dbNameField, gbc, row++);
        addFormRow(formPanel, "Username:", userField, gbc, row++);
        addFormRow(formPanel, "Password:", passPanel, gbc, row++);
        
        // Push everything up
        gbc.gridy = row;
        gbc.weighty = 1.0;
        formPanel.add(new JPanel(), gbc);
        
        JPanel formButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save Profile");
        JButton testBtn = new JButton("Test Connection");
        formButtonsPanel.add(testBtn);
        formButtonsPanel.add(saveBtn);
        
        rightPanel.add(new JLabel("Connection Details", SwingConstants.CENTER), BorderLayout.NORTH);
        rightPanel.add(formPanel, BorderLayout.CENTER);
        rightPanel.add(formButtonsPanel, BorderLayout.SOUTH);

        // --- Bottom Panel: Action Buttons ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton connectBtn = new JButton("Connect");
        connectBtn.setFont(connectBtn.getFont().deriveFont(Font.BOLD));
        JButton cancelBtn = new JButton("Cancel");
        bottomPanel.add(cancelBtn);
        bottomPanel.add(connectBtn);
        
        // --- Add to Split Pane ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(280);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- Logic ---
        loadProfiles();
        
        // Search filtering listener
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterProfiles(); }
            @Override public void removeUpdate(DocumentEvent e) { filterProfiles(); }
            @Override public void changedUpdate(DocumentEvent e) { filterProfiles(); }
        });

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (profileListModel.getSize() > 0) {
                        profileList.requestFocusInWindow();
                        if (profileList.getSelectedIndex() < 0) {
                            profileList.setSelectedIndex(0);
                        }
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.setText("");
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (profileList.getSelectedValue() != null) {
                        profileList.requestFocusInWindow();
                    }
                }
            }
        });

        // Double-click profile to connect directly
        profileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (profileList.getSelectedValue() != null) {
                        connectBtn.doClick();
                    }
                }
            }
        });

        profileList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.requestFocusInWindow();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (profileList.getSelectedValue() != null) {
                        connectBtn.doClick();
                    }
                }
            }
        });
        
        profileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedProfile();
            }
        });
        
        addBtn.addActionListener(e -> {
            profileList.clearSelection();
            hostField.setText("localhost");
            portField.setText("5432");
            dbNameField.setText("postgres");
            userField.setText("postgres");
            passField.setText("postgres");
            hostField.requestFocusInWindow();
        });
        
        delBtn.addActionListener(e -> {
            String selected = profileList.getSelectedValue();
            if (selected != null) {
                int ans = JOptionPane.showConfirmDialog(this, "Delete profile '" + selected + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    dbManager.deleteConnectionProfile(selected);
                    loadProfiles();
                }
            }
        });
        
        saveBtn.addActionListener(e -> {
            String h = hostField.getText().trim();
            String p = portField.getText().trim();
            String d = dbNameField.getText().trim();
            String u = userField.getText().trim();
            String pw = new String(passField.getPassword());
            if (h.isEmpty() || d.isEmpty() || u.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Host, Database, and User cannot be empty.");
                return;
            }
            String profileName = u + "@" + h + ":" + p + "/" + d;
            
            dbManager.saveConnectionProfile(profileName, h, p, u, pw, d);
            if (searchField != null && !searchField.getText().trim().isEmpty()) {
                if (!profileName.toLowerCase().contains(searchField.getText().trim().toLowerCase())) {
                    searchField.setText("");
                }
            }
            loadProfiles();
            profileList.setSelectedValue(profileName, true);
            JOptionPane.showMessageDialog(this, "Profile saved as: " + profileName);
        });
        
        testBtn.addActionListener(e -> {
            String h = hostField.getText().trim();
            String p = portField.getText().trim();
            String d = dbNameField.getText().trim();
            String u = userField.getText().trim();
            String pw = new String(passField.getPassword());
            
            testBtn.setEnabled(false);
            testBtn.setText("Testing...");
            
            new Thread(() -> {
                try {
                    // Test Postgres Connection
                    String url = "jdbc:postgresql://" + h + ":" + p + "/" + d + "?connectTimeout=10";
                    java.sql.Connection conn = java.sql.DriverManager.getConnection(url, u, pw);
                    conn.close();
                    SwingUtilities.invokeLater(() -> {
                        testBtn.setEnabled(true);
                        testBtn.setText("Test Connection");
                        JOptionPane.showMessageDialog(this, "Connection Successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        testBtn.setEnabled(true);
                        testBtn.setText("Test Connection");
                        JOptionPane.showMessageDialog(this, "Connection Failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        });
        
        connectBtn.addActionListener(e -> {
            selectedHost = hostField.getText().trim();
            selectedPort = portField.getText().trim();
            selectedDbName = dbNameField.getText().trim();
            selectedUser = userField.getText().trim();
            selectedPass = new String(passField.getPassword());
            isApproved = true;
            setVisible(false);
        });
        
        cancelBtn.addActionListener(e -> {
            isApproved = false;
            setVisible(false);
        });
        
        // Select first if available
        if (profileListModel.getSize() > 0) {
            profileList.setSelectedIndex(0);
        }
    }
    
    private void addFormRow(JPanel panel, String label, JComponent field, GridBagConstraints gbc, int y) {
        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(field, gbc);
    }
    
    private void loadProfiles() {
        allProfileNames.clear();
        Properties props = dbManager.getSavedConnections();
        List<String> sortedKeys = new ArrayList<>(props.stringPropertyNames());
        Collections.sort(sortedKeys, String.CASE_INSENSITIVE_ORDER);
        allProfileNames.addAll(sortedKeys);
        filterProfiles();
    }

    public void filterProfiles() {
        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        String previouslySelected = profileList != null ? profileList.getSelectedValue() : null;
        
        profileListModel.clear();
        Properties props = dbManager.getSavedConnections();
        
        for (String name : allProfileNames) {
            if (query.isEmpty()) {
                profileListModel.addElement(name);
            } else {
                boolean matches = name.toLowerCase().contains(query);
                if (!matches) {
                    String val = props.getProperty(name);
                    if (val != null) {
                        String[] parts = val.split(";");
                        if (parts.length >= 5) {
                            String host = parts[0].toLowerCase();
                            String port = parts[1].toLowerCase();
                            String user = parts[2].toLowerCase();
                            String db = parts[4].toLowerCase();
                            if (host.contains(query) || db.contains(query) || user.contains(query) || port.contains(query)) {
                                matches = true;
                            }
                        }
                    }
                }
                if (matches) {
                    profileListModel.addElement(name);
                }
            }
        }
        
        if (countLabel != null) {
            if (query.isEmpty()) {
                countLabel.setText(allProfileNames.size() + " profiles");
            } else {
                countLabel.setText(profileListModel.size() + " of " + allProfileNames.size());
            }
        }
        
        if (previouslySelected != null && profileListModel.contains(previouslySelected)) {
            profileList.setSelectedValue(previouslySelected, true);
        } else if (profileListModel.getSize() > 0) {
            profileList.setSelectedIndex(0);
        } else {
            profileList.clearSelection();
        }
    }
    
    private void loadSelectedProfile() {
        String selected = profileList.getSelectedValue();
        if (selected != null) {
            Properties props = dbManager.getSavedConnections();
            String val = props.getProperty(selected);
            if (val != null) {
                String[] parts = val.split(";");
                if (parts.length >= 5) {
                    hostField.setText(parts[0]);
                    portField.setText(parts[1]);
                    userField.setText(parts[2]);
                    try {
                        String decodedPass = new String(java.util.Base64.getDecoder().decode(parts[3]));
                        passField.setText(decodedPass);
                    } catch (Exception ex) {}
                    dbNameField.setText(parts[4]);
                }
            }
        }
    }
    
    public boolean isApproved() { return isApproved; }
    public String getHost() { return selectedHost; }
    public String getPort() { return selectedPort; }
    public String getUser() { return selectedUser; }
    public String getPass() { return selectedPass; }
    public String getDbName() { return selectedDbName; }
    public JTextField getSearchField() { return searchField; }
    public JList<String> getProfileList() { return profileList; }
    public DefaultListModel<String> getProfileListModel() { return profileListModel; }
    public void setSearchQuery(String query) {
        if (searchField != null) {
            searchField.setText(query);
        }
        filterProfiles();
    }
}
