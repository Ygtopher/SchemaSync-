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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionManagerDialog extends JDialog {
    public enum ConnectionStatus {
        UNKNOWN,
        CHECKING,
        ONLINE,
        OFFLINE
    }

    public static class StatusInfo {
        public final ConnectionStatus status;
        public final String message;
        public final long latencyMs;

        public StatusInfo(ConnectionStatus status, String message, long latencyMs) {
            this.status = status;
            this.message = message != null ? message : "";
            this.latencyMs = latencyMs;
        }
    }

    public static class StatusDotIcon implements Icon {
        private final Color color;
        private final Color borderColor;
        private final int size;

        public StatusDotIcon(Color color, Color borderColor, int size) {
            this.color = color;
            this.borderColor = borderColor;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int drawY = y + (getIconHeight() - size) / 2;
            g2.setColor(color);
            g2.fillOval(x + 2, drawY, size, size);
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(x + 2, drawY, size, size);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return size + 5; }

        @Override
        public int getIconHeight() { return size + 2; }
    }

    private static final Icon ICON_ONLINE = new StatusDotIcon(new Color(40, 167, 69), new Color(28, 120, 48), 10);
    private static final Icon ICON_OFFLINE = new StatusDotIcon(new Color(220, 53, 69), new Color(178, 30, 44), 10);
    private static final Icon ICON_CHECKING = new StatusDotIcon(new Color(245, 175, 0), new Color(195, 140, 0), 10);
    private static final Icon ICON_UNKNOWN = new StatusDotIcon(new Color(175, 175, 175), new Color(130, 130, 130), 10);

    public static Icon getStatusIcon(ConnectionStatus status) {
        if (status == null) return ICON_UNKNOWN;
        switch (status) {
            case ONLINE: return ICON_ONLINE;
            case OFFLINE: return ICON_OFFLINE;
            case CHECKING: return ICON_CHECKING;
            default: return ICON_UNKNOWN;
        }
    }

    private DatabaseManager dbManager;
    private DefaultListModel<String> profileListModel;
    private JList<String> profileList;
    private JTextField searchField;
    private JLabel countLabel;
    private JLabel statusBadgeLabel;
    private final List<String> allProfileNames = new ArrayList<>();
    private final Map<String, StatusInfo> statusMap = new ConcurrentHashMap<>();
    private ExecutorService pingExecutor;
    private JTextField hostField;
    private JTextField portField;
    private JTextField userField;
    private JPasswordField passField;
    private JTextField dbNameField;
    private JComboBox<String> dbTypeCombo = new JComboBox<>(new String[]{"PostgreSQL", "MySQL", "SQLite"});
    
    private boolean isApproved = false;
    private String selectedType = "PostgreSQL";
    private String selectedHost;
    private String selectedPort;
    private String selectedUser;
    private String selectedPass;
    private String selectedDbName;

    public String getSelectedType() { return selectedType != null ? selectedType : "PostgreSQL"; }

    public ConnectionManagerDialog(JFrame parent, DatabaseManager dbManager) {
        super(parent, "Connection Profile Manager", true);
        this.dbManager = dbManager;
        
        setSize(800, 500);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // --- Left Panel: Profile List & Search ---
        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));
        
        // Search bar
        JPanel searchBoxPanel = new JPanel(new BorderLayout(4, 0));
        searchBoxPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        
        JLabel searchIcon = new JLabel("🔍 ");
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search profiles (or 'online' / 'offline')...");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.setToolTipText("Filter by name, host IP, database, username, or status ('online' / 'offline')");
        
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
        profileList = new JList<String>(profileListModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int index = locationToIndex(e.getPoint());
                if (index >= 0 && index < getModel().getSize()) {
                    Rectangle bounds = getCellBounds(index, index);
                    if (bounds != null && bounds.contains(e.getPoint())) {
                        String profileName = getModel().getElementAt(index);
                        StatusInfo info = statusMap.get(profileName);
                        if (info != null) {
                            if (info.status == ConnectionStatus.ONLINE) {
                                return "<html><b style='color:#28a745;'>● Online</b> (latency: " + info.latencyMs + " ms)<br/>" + escapeHtml(profileName) + "</html>";
                            } else if (info.status == ConnectionStatus.OFFLINE) {
                                return "<html><b style='color:#dc3545;'>● Offline / Error</b><br/>" + escapeHtml(info.message) + "<br/>" + escapeHtml(profileName) + "</html>";
                            } else if (info.status == ConnectionStatus.CHECKING) {
                                return "<html><b style='color:#f39c12;'>● Testing...</b><br/>" + escapeHtml(profileName) + "</html>";
                            }
                        }
                        return "<html><b style='color:gray;'>● Status Unverified</b><br/>Click '⚡ Ping All' or right-click to test.<br/>" + escapeHtml(profileName) + "</html>";
                    }
                }
                return super.getToolTipText(e);
            }
        };
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setCellRenderer(new ProfileCellRenderer());
        ToolTipManager.sharedInstance().registerComponent(profileList);
        
        leftPanel.add(new JScrollPane(profileList), BorderLayout.CENTER);
        
        JPanel listButtonsPanel = new JPanel(new BorderLayout(5, 5));
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        JButton addBtn = new JButton("+");
        addBtn.setToolTipText("Add New Profile");
        JButton delBtn = new JButton("-");
        delBtn.setToolTipText("Delete Selected Profile");
        JButton pingAllBtn = new JButton("⚡ Ping All");
        pingAllBtn.setFont(pingAllBtn.getFont().deriveFont(11f));
        pingAllBtn.setMargin(new Insets(1, 5, 1, 5));
        pingAllBtn.setToolTipText("Test connectivity for all saved profiles in the background");
        pingAllBtn.addActionListener(e -> pingAllProfiles());

        JButton vaultBtn = new JButton("🛡️ Vault");
        vaultBtn.setFont(vaultBtn.getFont().deriveFont(11f));
        vaultBtn.setMargin(new Insets(1, 5, 1, 5));
        vaultBtn.setToolTipText("Manage Master Password, lock/unlock vault, export/import AES-256 encrypted profiles");
        vaultBtn.addActionListener(e -> new VaultDialog(this, dbManager, this::loadProfiles).setVisible(true));

        leftButtons.add(addBtn);
        leftButtons.add(delBtn);
        leftButtons.add(pingAllBtn);
        leftButtons.add(vaultBtn);
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
        
        JPanel headerPanel = new JPanel(new BorderLayout(0, 2));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JLabel titleLabel = new JLabel("Connection Details", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusBadgeLabel = new JLabel("● Status: Unverified", SwingConstants.CENTER);
        statusBadgeLabel.setFont(statusBadgeLabel.getFont().deriveFont(11f));
        statusBadgeLabel.setForeground(Color.GRAY);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(statusBadgeLabel, BorderLayout.SOUTH);
        
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
        addFormRow(formPanel, "Database Type:", dbTypeCombo, gbc, row++);
        addFormRow(formPanel, "Host:", hostField, gbc, row++);
        addFormRow(formPanel, "Port:", portField, gbc, row++);
        addFormRow(formPanel, "Database Name:", dbNameField, gbc, row++);
        addFormRow(formPanel, "Username:", userField, gbc, row++);
        addFormRow(formPanel, "Password:", passPanel, gbc, row++);

        dbTypeCombo.addActionListener(e -> {
            String t = (String) dbTypeCombo.getSelectedItem();
            if ("MySQL".equalsIgnoreCase(t)) {
                if (portField.getText().isEmpty() || portField.getText().equals("5432")) portField.setText("3306");
                if (userField.getText().isEmpty() || userField.getText().equals("postgres")) userField.setText("root");
                hostField.setEnabled(true);
                portField.setEnabled(true);
                userField.setEnabled(true);
                passField.setEnabled(true);
            } else if ("SQLite".equalsIgnoreCase(t)) {
                hostField.setEnabled(false);
                portField.setEnabled(false);
                userField.setEnabled(false);
                passField.setEnabled(false);
            } else {
                if (portField.getText().isEmpty() || portField.getText().equals("3306")) portField.setText("5432");
                if (userField.getText().isEmpty() || userField.getText().equals("root")) userField.setText("postgres");
                hostField.setEnabled(true);
                portField.setEnabled(true);
                userField.setEnabled(true);
                passField.setEnabled(true);
            }
        });
        
        // Push everything up
        gbc.gridy = row;
        gbc.weighty = 1.0;
        formPanel.add(new JPanel(), gbc);
        
        JPanel formButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save Profile");
        JButton testBtn = new JButton("Test Connection");
        formButtonsPanel.add(testBtn);
        formButtonsPanel.add(saveBtn);
        
        rightPanel.add(headerPanel, BorderLayout.NORTH);
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
        splitPane.setDividerLocation(300);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- Context Menu for Profile List ---
        JPopupMenu profilePopupMenu = new JPopupMenu();
        JMenuItem pingItem = new JMenuItem("⚡ Check Connection (Ping)");
        JMenuItem connectItem = new JMenuItem("Connect");
        JMenuItem deleteItem = new JMenuItem("Delete Profile");

        pingItem.addActionListener(e -> {
            String sel = profileList.getSelectedValue();
            if (sel != null) checkSingleProfile(sel);
        });

        connectItem.addActionListener(e -> {
            if (profileList.getSelectedValue() != null) connectBtn.doClick();
        });

        deleteItem.addActionListener(e -> {
            delBtn.doClick();
        });

        profilePopupMenu.add(pingItem);
        profilePopupMenu.addSeparator();
        profilePopupMenu.add(connectItem);
        profilePopupMenu.add(deleteItem);

        profileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }
            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int idx = profileList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        profileList.setSelectedIndex(idx);
                        profilePopupMenu.show(profileList, e.getX(), e.getY());
                    }
                }
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (profileList.getSelectedValue() != null) {
                        connectBtn.doClick();
                    }
                }
            }
        });

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
            updateStatusBadge(null);
        });
        
        delBtn.addActionListener(e -> {
            String selected = profileList.getSelectedValue();
            if (selected != null) {
                int ans = JOptionPane.showConfirmDialog(this, "Delete profile '" + selected + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    statusMap.remove(selected);
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
            checkSingleProfile(profileName);
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
                long start = System.currentTimeMillis();
                try {
                    String type = (String) dbTypeCombo.getSelectedItem();
                    java.sql.Connection conn;
                    if ("MySQL".equalsIgnoreCase(type)) {
                        String url = "jdbc:mysql://" + h + ":" + p + "/" + d + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000";
                        conn = java.sql.DriverManager.getConnection(url, u, pw);
                    } else if ("SQLite".equalsIgnoreCase(type)) {
                        String url = "jdbc:sqlite:" + d;
                        conn = java.sql.DriverManager.getConnection(url);
                    } else {
                        String url = "jdbc:postgresql://" + h + ":" + p + "/" + d + "?connectTimeout=10&socketTimeout=10&tcpKeepAlive=true&gssEncMode=disable";
                        conn = com.dbtool.core.connection.ConnectionUtil.getConnection(url, u, pw);
                    }
                    conn.close();
                    long elapsed = System.currentTimeMillis() - start;
                    SwingUtilities.invokeLater(() -> {
                        testBtn.setEnabled(true);
                        testBtn.setText("Test Connection");
                        String selected = profileList.getSelectedValue();
                        if (selected != null) {
                            statusMap.put(selected, new StatusInfo(ConnectionStatus.ONLINE, "Connected successfully", elapsed));
                            profileList.repaint();
                            updateCountLabel();
                            updateStatusBadge(selected);
                        }
                        JOptionPane.showMessageDialog(this, "Connection Successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception ex) {
                    long elapsed = System.currentTimeMillis() - start;
                    String errMsg = ex.getMessage() != null ? ex.getMessage() : "Error";
                    SwingUtilities.invokeLater(() -> {
                        testBtn.setEnabled(true);
                        testBtn.setText("Test Connection");
                        String selected = profileList.getSelectedValue();
                        if (selected != null) {
                            statusMap.put(selected, new StatusInfo(ConnectionStatus.OFFLINE, errMsg, elapsed));
                            profileList.repaint();
                            updateCountLabel();
                            updateStatusBadge(selected);
                        }
                        JOptionPane.showMessageDialog(this, "Connection Failed:\n" + errMsg, "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        });
        
        connectBtn.addActionListener(e -> {
            selectedType = (String) dbTypeCombo.getSelectedItem();
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

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                shutdownExecutor();
            }
        });
        
        // --- Initial Load ---
        loadProfiles();
        
        // Select first if available
        if (profileListModel.getSize() > 0) {
            profileList.setSelectedIndex(0);
        }

        // Auto-ping saved profiles in the background
        pingAllProfiles();
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
                boolean matches = false;
                StatusInfo info = statusMap.get(name);
                ConnectionStatus st = info != null ? info.status : ConnectionStatus.UNKNOWN;

                if (query.equals("online") || query.equals("green") || query.equals(":online")) {
                    matches = (st == ConnectionStatus.ONLINE);
                } else if (query.equals("offline") || query.equals("red") || query.equals(":offline")) {
                    matches = (st == ConnectionStatus.OFFLINE);
                } else {
                    matches = name.toLowerCase().contains(query);
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
                }
                if (matches) {
                    profileListModel.addElement(name);
                }
            }
        }
        
        updateCountLabel();
        
        if (previouslySelected != null && profileListModel.contains(previouslySelected)) {
            profileList.setSelectedValue(previouslySelected, true);
        } else if (profileListModel.getSize() > 0) {
            profileList.setSelectedIndex(0);
        } else {
            profileList.clearSelection();
            updateStatusBadge(null);
        }
    }

    private void updateCountLabel() {
        if (countLabel == null) return;
        String query = searchField != null ? searchField.getText().trim() : "";
        String baseText = query.isEmpty() ? (allProfileNames.size() + " profiles")
                : (profileListModel.size() + " of " + allProfileNames.size());

        int online = 0;
        int offline = 0;
        int checking = 0;
        for (int i = 0; i < profileListModel.size(); i++) {
            String name = profileListModel.get(i);
            StatusInfo info = statusMap.get(name);
            if (info != null) {
                if (info.status == ConnectionStatus.ONLINE) online++;
                else if (info.status == ConnectionStatus.OFFLINE) offline++;
                else if (info.status == ConnectionStatus.CHECKING) checking++;
            }
        }

        StringBuilder sb = new StringBuilder(baseText);
        if (online > 0 || offline > 0 || checking > 0) {
            sb.append("  •  ");
            if (online > 0) sb.append("🟢 ").append(online).append(" ");
            if (offline > 0) sb.append("🔴 ").append(offline).append(" ");
            if (checking > 0) sb.append("🟡 ").append(checking);
        }
        countLabel.setText(sb.toString().trim());
    }

    private void updateStatusBadge(String profileName) {
        if (statusBadgeLabel == null) return;
        if (profileName == null || profileName.isEmpty()) {
            statusBadgeLabel.setText("● Status: No profile selected");
            statusBadgeLabel.setForeground(Color.GRAY);
            statusBadgeLabel.setToolTipText(null);
            return;
        }
        StatusInfo info = statusMap.get(profileName);
        if (info == null || info.status == ConnectionStatus.UNKNOWN) {
            statusBadgeLabel.setText("● Status: Unverified");
            statusBadgeLabel.setForeground(Color.GRAY);
            statusBadgeLabel.setToolTipText("Click '⚡ Ping All' or 'Test Connection' to verify.");
        } else if (info.status == ConnectionStatus.CHECKING) {
            statusBadgeLabel.setText("● Status: Checking connection...");
            statusBadgeLabel.setForeground(new Color(230, 160, 0));
            statusBadgeLabel.setToolTipText("Testing connection...");
        } else if (info.status == ConnectionStatus.ONLINE) {
            statusBadgeLabel.setText("● Status: Online (" + info.latencyMs + " ms)");
            statusBadgeLabel.setForeground(new Color(40, 167, 69));
            statusBadgeLabel.setToolTipText("Connected successfully (" + info.latencyMs + " ms)");
        } else if (info.status == ConnectionStatus.OFFLINE) {
            String msg = info.message != null && !info.message.isEmpty() ? info.message : "Unreachable";
            if (msg.length() > 40) {
                msg = msg.substring(0, 37) + "...";
            }
            statusBadgeLabel.setText("● Status: Offline (" + msg + ")");
            statusBadgeLabel.setForeground(new Color(220, 53, 69));
            statusBadgeLabel.setToolTipText("<html><b>Offline:</b> " + escapeHtml(info.message) + "</html>");
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
                        String decodedPass = com.dbtool.util.CryptoUtil.decryptPassword(parts[3]);
                        passField.setText(decodedPass);
                    } catch (Exception ex) {}
                    dbNameField.setText(parts[4]);
                }
            }
            updateStatusBadge(selected);
        } else {
            updateStatusBadge(null);
        }
    }

    public synchronized void pingAllProfiles() {
        List<String> toCheck = new ArrayList<>(allProfileNames);
        if (toCheck.isEmpty()) return;

        for (String name : toCheck) {
            statusMap.put(name, new StatusInfo(ConnectionStatus.CHECKING, "Testing...", 0));
        }
        profileList.repaint();
        updateCountLabel();
        updateStatusBadge(profileList.getSelectedValue());

        ExecutorService exec = getOrCreateExecutor();
        for (String name : toCheck) {
            exec.submit(() -> {
                if (Thread.currentThread().isInterrupted()) return;
                StatusInfo info = testProfileConnectivity(name);
                if (Thread.interrupted()) return;
                statusMap.put(name, info);
                try {
                    SwingUtilities.invokeLater(() -> {
                        profileList.repaint();
                        updateCountLabel();
                        String curr = profileList.getSelectedValue();
                        if (name.equals(curr)) {
                            updateStatusBadge(curr);
                        }
                    });
                } catch (Throwable ignored) {}
            });
        }
    }

    public void checkSingleProfile(String profileName) {
        if (profileName == null || profileName.isEmpty()) return;
        statusMap.put(profileName, new StatusInfo(ConnectionStatus.CHECKING, "Testing...", 0));
        profileList.repaint();
        updateCountLabel();
        updateStatusBadge(profileName);

        getOrCreateExecutor().submit(() -> {
            if (Thread.currentThread().isInterrupted()) return;
            StatusInfo info = testProfileConnectivity(profileName);
            if (Thread.interrupted()) return;
            statusMap.put(profileName, info);
            try {
                SwingUtilities.invokeLater(() -> {
                    profileList.repaint();
                    updateCountLabel();
                    String curr = profileList.getSelectedValue();
                    if (profileName.equals(curr)) {
                        updateStatusBadge(curr);
                    }
                });
            } catch (Throwable ignored) {}
        });
    }

    public StatusInfo testProfileConnectivity(String profileName) {
        Properties props = dbManager.getSavedConnections();
        String val = props.getProperty(profileName);
        if (val == null) {
            return new StatusInfo(ConnectionStatus.UNKNOWN, "Profile not found", 0);
        }
        String[] parts = val.split(";");
        if (parts.length < 5) {
            return new StatusInfo(ConnectionStatus.UNKNOWN, "Invalid profile configuration", 0);
        }
        String host = parts[0].trim();
        String port = parts[1].trim();
        String user = parts[2].trim();
        String pass = "";
        try {
            pass = com.dbtool.util.CryptoUtil.decryptPassword(parts[3]);
        } catch (Exception ignored) {}
        String db = parts[4].trim();

        // Check if MS Access / file path
        if (host.isEmpty() || db.toLowerCase().endsWith(".accdb") || db.toLowerCase().endsWith(".mdb")) {
            File f = new File(db);
            if (f.exists()) {
                return new StatusInfo(ConnectionStatus.ONLINE, "Database file exists", 0);
            } else {
                return new StatusInfo(ConnectionStatus.OFFLINE, "Database file not found", 0);
            }
        }

        int portNum = 5432;
        try {
            portNum = Integer.parseInt(port);
        } catch (Exception ignored) {}

        long startTime = System.currentTimeMillis();

        // 1. Fast socket check (1500 ms)
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, portNum), 1500);
        } catch (SocketTimeoutException e) {
            return new StatusInfo(ConnectionStatus.OFFLINE, "Timed out connecting to " + host + ":" + portNum, System.currentTimeMillis() - startTime);
        } catch (ConnectException e) {
            return new StatusInfo(ConnectionStatus.OFFLINE, "Connection refused at " + host + ":" + portNum, System.currentTimeMillis() - startTime);
        } catch (UnknownHostException e) {
            return new StatusInfo(ConnectionStatus.OFFLINE, "Unknown host: " + host, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return new StatusInfo(ConnectionStatus.OFFLINE, e.getMessage() != null ? e.getMessage() : "Network unreachable", System.currentTimeMillis() - startTime);
        }

        // 2. Fast JDBC login check (connectTimeout=3s, socketTimeout=3s)
        String url = "jdbc:postgresql://" + host + ":" + portNum + "/" + db + "?connectTimeout=3&socketTimeout=3&tcpKeepAlive=true&gssEncMode=disable";
        try (Connection conn = com.dbtool.core.connection.ConnectionUtil.getConnection(url, user, pass)) {
            long elapsed = System.currentTimeMillis() - startTime;
            return new StatusInfo(ConnectionStatus.ONLINE, "Connected successfully", elapsed);
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            String err = e.getMessage();
            if (err != null && err.contains("\n")) {
                err = err.split("\n")[0];
            }
            return new StatusInfo(ConnectionStatus.OFFLINE, err != null ? err : "Authentication / DB error", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            return new StatusInfo(ConnectionStatus.OFFLINE, e.getMessage() != null ? e.getMessage() : "Failed to connect", elapsed);
        }
    }

    private synchronized ExecutorService getOrCreateExecutor() {
        if (pingExecutor == null || pingExecutor.isShutdown()) {
            pingExecutor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "profile-ping-worker");
                t.setDaemon(true);
                return t;
            });
        }
        return pingExecutor;
    }

    public synchronized void shutdownExecutor() {
        if (pingExecutor != null && !pingExecutor.isShutdown()) {
            pingExecutor.shutdownNow();
        }
    }

    @Override
    public void dispose() {
        shutdownExecutor();
        super.dispose();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private class ProfileCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String) {
                String profileName = (String) value;
                StatusInfo info = statusMap.get(profileName);
                ConnectionStatus st = info != null ? info.status : ConnectionStatus.UNKNOWN;
                label.setIcon(getStatusIcon(st));
                label.setIconTextGap(6);
                label.setText(profileName);
            }
            return label;
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
    public ConnectionStatus getProfileStatus(String profileName) {
        StatusInfo info = statusMap.get(profileName);
        return info != null ? info.status : ConnectionStatus.UNKNOWN;
    }
    public StatusInfo getProfileStatusInfo(String profileName) {
        return statusMap.get(profileName);
    }
    public Map<String, StatusInfo> getStatusMap() {
        return statusMap;
    }
    public void setSearchQuery(String query) {
        if (searchField != null) {
            searchField.setText(query);
        }
        filterProfiles();
    }
}

