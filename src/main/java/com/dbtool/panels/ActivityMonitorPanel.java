package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Vector;

public class ActivityMonitorPanel extends JPanel {
    private DatabaseManager dbManager;
    private JTable table;
    private DefaultTableModel tableModel;
    private Timer refreshTimer;
    private JCheckBox autoRefreshCheck;
    private JButton killBtn;
    private JLabel statusLabel;

    public ActivityMonitorPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton refreshBtn = new JButton("Refresh Now");
        autoRefreshCheck = new JCheckBox("Auto-Refresh (3s)", true);
        killBtn = new JButton("Kill Selected Process");
        killBtn.setForeground(Color.RED);
        killBtn.setEnabled(false);
        statusLabel = new JLabel(" Ready");

        toolbar.add(refreshBtn);
        toolbar.addSeparator();
        toolbar.add(autoRefreshCheck);
        toolbar.addSeparator();
        toolbar.add(killBtn);
        toolbar.addSeparator();
        toolbar.add(statusLabel);

        add(toolbar, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getSelectionModel().addListSelectionListener(e -> {
            killBtn.setEnabled(table.getSelectedRow() >= 0);
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Actions
        refreshBtn.addActionListener(e -> refreshData());
        
        killBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                // Determine which column is PID/ID
                int pidCol = -1;
                for (int i = 0; i < tableModel.getColumnCount(); i++) {
                    String colName = tableModel.getColumnName(i).toLowerCase();
                    if (colName.equals("pid") || colName.equals("id")) {
                        pidCol = i;
                        break;
                    }
                }
                
                if (pidCol != -1) {
                    String pid = tableModel.getValueAt(row, pidCol).toString();
                    int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to kill process " + pid + "?", "Kill Process", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirm == JOptionPane.YES_OPTION) {
                        try {
                            killProcess(pid);
                            refreshData();
                            JOptionPane.showMessageDialog(this, "Process killed successfully.");
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(this, "Failed to kill process: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Could not identify PID column.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Timer
        refreshTimer = new Timer(3000, e -> {
            if (autoRefreshCheck.isSelected() && this.dbManager != null && this.dbManager.connection != null) {
                refreshData();
            }
        });
        refreshTimer.start();
    }

    public void stopTimer() {
        if (refreshTimer != null) refreshTimer.stop();
    }

    private void refreshData() {
        if (dbManager == null || dbManager.connection == null) return;
        
        SwingWorker<DefaultTableModel, Void> worker = new SwingWorker<DefaultTableModel, Void>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                String dbName = dbManager.connection.getMetaData().getDatabaseProductName().toLowerCase();
                String sql = "";
                
                if (dbName.contains("postgresql")) {
                    sql = "SELECT pid, usename AS user, datname AS database, state, " +
                          "EXTRACT(EPOCH FROM (now() - query_start))::numeric(10,2) AS duration_sec, " +
                          "query FROM pg_stat_activity WHERE datname IS NOT NULL ORDER BY duration_sec DESC NULLS LAST";
                } else if (dbName.contains("mysql") || dbName.contains("mariadb")) {
                    sql = "SHOW FULL PROCESSLIST";
                } else if (dbName.contains("h2")) {
                    sql = "SELECT SESSION_ID AS ID, USER_NAME AS USER, STATEMENT AS QUERY FROM INFORMATION_SCHEMA.SESSIONS";
                } else {
                    throw new Exception("Activity Monitor is not supported for this database type (" + dbName + ").");
                }

                try (java.sql.Statement stmt = dbManager.connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    
                    Vector<String> columnNames = new Vector<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columnNames.add(metaData.getColumnName(i));
                    }
                    
                    Vector<Vector<Object>> data = new Vector<>();
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(rs.getObject(i));
                        }
                        data.add(row);
                    }
                    return new DefaultTableModel(data, columnNames) {
                        @Override
                        public boolean isCellEditable(int row, int column) { return false; }
                    };
                }
            }

            @Override
            protected void done() {
                try {
                    DefaultTableModel newModel = get();
                    int selectedRow = table.getSelectedRow();
                    String selectedPid = null;
                    int pidCol = -1;
                    
                    if (selectedRow >= 0) {
                        for (int i = 0; i < tableModel.getColumnCount(); i++) {
                            String colName = tableModel.getColumnName(i).toLowerCase();
                            if (colName.equals("pid") || colName.equals("id")) {
                                pidCol = i;
                                selectedPid = tableModel.getValueAt(selectedRow, pidCol).toString();
                                break;
                            }
                        }
                    }

                    tableModel = newModel;
                    table.setModel(tableModel);
                    
                    // Restore selection if process still exists
                    if (selectedPid != null && pidCol != -1) {
                        for (int i = 0; i < table.getRowCount(); i++) {
                            if (table.getValueAt(i, pidCol).toString().equals(selectedPid)) {
                                table.setRowSelectionInterval(i, i);
                                break;
                            }
                        }
                    }
                    
                    // Adjust column widths
                    for (int i = 0; i < table.getColumnCount(); i++) {
                        String colName = table.getColumnName(i).toLowerCase();
                        if (colName.contains("query") || colName.contains("info") || colName.contains("statement")) {
                            table.getColumnModel().getColumn(i).setPreferredWidth(400);
                        } else {
                            table.getColumnModel().getColumn(i).setPreferredWidth(100);
                        }
                    }
                    
                    statusLabel.setText(" Last refreshed: " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
                } catch (Exception ex) {
                    statusLabel.setText(" Error: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void killProcess(String pid) throws Exception {
        String dbName = dbManager.connection.getMetaData().getDatabaseProductName().toLowerCase();
        String sql = "";
        
        if (dbName.contains("postgresql")) {
            sql = "SELECT pg_terminate_backend(" + pid + ")";
        } else if (dbName.contains("mysql") || dbName.contains("mariadb")) {
            sql = "KILL " + pid;
        } else if (dbName.contains("h2")) {
            // H2 process termination is limited, might require admin rights or specific version syntax
            sql = "CANCEL SESSION " + pid; 
        } else {
            throw new Exception("Kill Process not supported for this database type.");
        }
        
        try (java.sql.Statement stmt = dbManager.connection.createStatement()) {
            if (sql.startsWith("SELECT")) {
                stmt.executeQuery(sql);
            } else {
                stmt.execute(sql);
            }
        }
    }
}
