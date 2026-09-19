package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Properties;

public class ConnectionManagerDialog extends JDialog {
    private DatabaseManager dbManager;
    private DefaultListModel<String> profileListModel;
    private JList<String> profileList;
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
        
        setSize(700, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // --- Left Panel: Profile List ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(250, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));
        
        profileListModel = new DefaultListModel<>();
        profileList = new JList<>(profileListModel);
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        leftPanel.add(new JScrollPane(profileList), BorderLayout.CENTER);
        
        JPanel listButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JButton addBtn = new JButton("+");
        addBtn.setToolTipText("Add New Profile");
        JButton delBtn = new JButton("-");
        delBtn.setToolTipText("Delete Selected Profile");
        listButtonsPanel.add(addBtn);
        listButtonsPanel.add(delBtn);
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
        splitPane.setDividerLocation(250);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- Logic ---
        loadProfiles();
        
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
            
            // If they modify an existing profile and the name changes, delete the old one?
            // Simple approach: just save as the new name.
            dbManager.saveConnectionProfile(profileName, h, p, u, pw, d);
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
            try {
                // Test Postgres Connection
                String url = "jdbc:postgresql://" + h + ":" + p + "/" + d;
                java.sql.Connection conn = java.sql.DriverManager.getConnection(url, u, pw);
                conn.close();
                JOptionPane.showMessageDialog(this, "Connection Successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Connection Failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
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
        profileListModel.clear();
        Properties props = dbManager.getSavedConnections();
        for (String key : props.stringPropertyNames()) {
            profileListModel.addElement(key);
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
}
