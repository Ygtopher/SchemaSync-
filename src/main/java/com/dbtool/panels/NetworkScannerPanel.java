package com.dbtool.panels;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NetworkScannerPanel extends JPanel {

    private JTextField subnetField;
    private JButton scanBtn;
    private JToggleButton livePingBtn;
    private JCheckBox monitorModeCb;
    private JTable devicesTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    
    private ExecutorService scannerPool;
    
    private boolean isLivePingRunning = false;
    private Thread livePingThread;
    
    private javax.swing.Timer monitorTimer;
    private Set<String> knownMacs = new HashSet<>();

    public NetworkScannerPanel() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.add(new JLabel("Subnet (e.g., 192.168.1):"));
        
        String defaultSubnet = guessLocalSubnet();
        subnetField = new JTextField(defaultSubnet, 12);
        topPanel.add(subnetField);

        scanBtn = new JButton("Scan Network");
        scanBtn.addActionListener(e -> startScan());
        topPanel.add(scanBtn);
        
        livePingBtn = new JToggleButton("Start Live Ping");
        livePingBtn.addActionListener(e -> toggleLivePing());
        topPanel.add(livePingBtn);
        
        monitorModeCb = new JCheckBox("Monitor Mode (Alerts)");
        monitorModeCb.addActionListener(e -> toggleMonitorMode());
        topPanel.add(monitorModeCb);
        
        add(topPanel, BorderLayout.NORTH);

        String[] columns = {"IP Address", "Hostname", "MAC Address", "Latency", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        devicesTable = new JTable(tableModel);
        devicesTable.setRowHeight(24);
        devicesTable.getTableHeader().setReorderingAllowed(false);
        add(new JScrollPane(devicesTable), BorderLayout.CENTER);
        
        setupPopupMenu();

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        statusLabel = new JLabel("Ready. Enter your network subnet and click Scan.");
        progressBar = new JProgressBar(0, 254);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(progressBar, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private void setupPopupMenu() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem portScanItem = new JMenuItem("🚪 Scan Open Ports");
        JMenuItem wolItem = new JMenuItem("⚡ Send Wake-on-LAN (WOL)");
        
        portScanItem.addActionListener(e -> {
            int row = devicesTable.getSelectedRow();
            if (row >= 0) {
                String ip = (String) tableModel.getValueAt(row, 0);
                scanPorts(ip);
            }
        });
        
        wolItem.addActionListener(e -> {
            int row = devicesTable.getSelectedRow();
            if (row >= 0) {
                String mac = (String) tableModel.getValueAt(row, 2);
                sendWOL(mac);
            }
        });
        
        popup.add(portScanItem);
        popup.add(wolItem);
        
        devicesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int r = devicesTable.rowAtPoint(e.getPoint());
                    if (r >= 0 && r < devicesTable.getRowCount()) {
                        devicesTable.setRowSelectionInterval(r, r);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
    }

    private String guessLocalSubnet() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            if (ip != null && ip.contains(".")) {
                int lastDot = ip.lastIndexOf(".");
                if (lastDot > 0) {
                    return ip.substring(0, lastDot);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();
                    if (ip.contains(".") && !ip.startsWith("127.")) {
                        int lastDot = ip.lastIndexOf(".");
                        if (lastDot > 0) {
                            return ip.substring(0, lastDot);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return "192.168.1";
    }

    private void startScan() {
        String baseIp = subnetField.getText().trim();
        if (baseIp.endsWith(".")) baseIp = baseIp.substring(0, baseIp.length() - 1);
        
        String[] parts = baseIp.split("\\.");
        if (parts.length != 3) {
            JOptionPane.showMessageDialog(this, "Please enter a valid subnet base (e.g. 192.168.1 or 10.0.0)", "Invalid Subnet", JOptionPane.ERROR_MESSAGE);
            return;
        }

        tableModel.setRowCount(0);
        scanBtn.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        statusLabel.setText("Scanning network " + baseIp + ".0/24...");

        if (scannerPool != null && !scannerPool.isShutdown()) {
            scannerPool.shutdownNow();
        }
        
        scannerPool = Executors.newFixedThreadPool(50);
        AtomicInteger completed = new AtomicInteger(0);
        List<String> activeIps = Collections.synchronizedList(new ArrayList<>());
        
        final String subnet = baseIp;
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= 254; i++) {
            final int host = i;
            scannerPool.submit(() -> {
                String ip = subnet + "." + host;
                try {
                    InetAddress addr = InetAddress.getByName(ip);
                    if (addr.isReachable(1500)) {
                        String hostname = addr.getHostName();
                        if (hostname.equals(ip)) hostname = "Unknown";
                        
                        String finalHostname = hostname;
                        activeIps.add(ip);
                        
                        SwingUtilities.invokeLater(() -> {
                            tableModel.addRow(new Object[]{ip, finalHostname, "Resolving...", "--", "Online"});
                        });
                    }
                } catch (Exception ex) {}
                finally {
                    int c = completed.incrementAndGet();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(c);
                        if (c == 254) {
                            finishScan(activeIps, startTime);
                        }
                    });
                }
            });
        }
    }

    private void finishScan(List<String> activeIps, long startTime) {
        scannerPool.shutdown();
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> statusLabel.setText("Resolving MAC Addresses..."));
            Map<String, String> arpTable = getArpTable();
            
            SwingUtilities.invokeLater(() -> {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String ip = (String) tableModel.getValueAt(i, 0);
                    String mac = arpTable.getOrDefault(ip, "Not Found");
                    tableModel.setValueAt(mac, i, 2);
                }
                
                long duration = (System.currentTimeMillis() - startTime) / 1000;
                scanBtn.setEnabled(true);
                progressBar.setVisible(false);
                statusLabel.setText("Scan complete! Found " + activeIps.size() + " devices in " + duration + "s.");
                
                resolveVendors();
            });
        }).start();
    }
    
    private void resolveVendors() {
        new Thread(() -> {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String hostname = (String) tableModel.getValueAt(i, 1);
                String mac = (String) tableModel.getValueAt(i, 2);
                if ("Unknown".equals(hostname) && mac != null && !mac.equals("Not Found")) {
                    try {
                        java.net.URL url = new java.net.URL("https://api.maclookup.app/v2/macs/" + mac.replace("-", ":"));
                        java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
                        con.setRequestMethod("GET");
                        con.setConnectTimeout(3000);
                        con.setReadTimeout(3000);
                        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(con.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) response.append(line);
                        in.close();
                        
                        String json = response.toString();
                        if (json.contains("\"found\":true")) {
                            String company = "";
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"company\":\"([^\"]+)\"").matcher(json);
                            if (m.find()) company = m.group(1);
                            if (!company.isEmpty()) {
                                final int row = i;
                                final String vendorName = company;
                                SwingUtilities.invokeLater(() -> tableModel.setValueAt(vendorName + " Device", row, 1));
                            }
                        } else if (json.contains("\"isRand\":true") || mac.matches("^.[26aeAE].*")) {
                            final int row = i;
                            SwingUtilities.invokeLater(() -> tableModel.setValueAt("Randomized MAC (Phone/Tablet)", row, 1));
                        }
                    } catch (Exception e) {}
                    try { Thread.sleep(200); } catch (Exception e) {} 
                }
            }
        }).start();
    }

    private Map<String, String> getArpTable() {
        Map<String, String> arpMap = new HashMap<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac == null) continue;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    sb.append(String.format("%02x%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                }
                String macStr = sb.toString();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    arpMap.put(addresses.nextElement().getHostAddress(), macStr);
                }
            }
        } catch (Exception e) {}

        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("arp", "-a");
            } else {
                pb = new ProcessBuilder("arp", "-an");
            }
            
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().replaceAll(" +", " ");
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    String ip = parts[0];
                    if (ip.startsWith("(")) ip = ip.substring(1, ip.length() - 1);
                    String mac = parts[1];
                    if (os.contains("win") && line.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2}).*")) {
                        if (parts[0].matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                             ip = parts[0];
                             mac = parts[1].replace(":", "-").toLowerCase();
                        }
                    }
                    arpMap.putIfAbsent(ip, mac);
                }
            }
            p.waitFor();
        } catch (Exception e) {}
        return arpMap;
    }
    
    // --- NEW FEATURES ---
    
    private void scanPorts(String ip) {
        int[] ports = {21, 22, 23, 53, 80, 443, 445, 3306, 3389, 5432, 8080, 27017};
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> statusLabel.setText("Scanning ports for " + ip + "..."));
            List<Integer> openPorts = new ArrayList<>();
            for (int p : ports) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(ip, p), 300);
                    openPorts.add(p);
                } catch (Exception e) {}
            }
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Port scan complete for " + ip);
                if (openPorts.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No common ports open on " + ip, "Port Scan", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Open ports on " + ip + ":\n" + openPorts, "Port Scan", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        }).start();
    }
    
    private void sendWOL(String macStr) {
        if (macStr == null || macStr.equals("Not Found")) {
            JOptionPane.showMessageDialog(this, "Valid MAC address required for WOL.");
            return;
        }
        try {
            String[] hex = macStr.split("[:-]");
            if (hex.length != 6) throw new Exception("Invalid MAC format");
            byte[] macBytes = new byte[6];
            for (int i = 0; i < 6; i++) {
                macBytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
            byte[] bytes = new byte[6 + 16 * 6];
            for (int i = 0; i < 6; i++) bytes[i] = (byte) 0xff;
            for (int i = 6; i < bytes.length; i += 6) {
                System.arraycopy(macBytes, 0, bytes, i, 6);
            }
            InetAddress address = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, 9);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(packet);
            }
            JOptionPane.showMessageDialog(this, "Wake-on-LAN Magic Packet sent to " + macStr);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to send WOL: " + e.getMessage());
        }
    }
    
    private void toggleLivePing() {
        isLivePingRunning = livePingBtn.isSelected();
        if (isLivePingRunning) {
            livePingBtn.setText("Stop Live Ping");
            livePingThread = new Thread(() -> {
                while (isLivePingRunning) {
                    for (int i = 0; i < tableModel.getRowCount(); i++) {
                        if (!isLivePingRunning) break;
                        String ip = (String) tableModel.getValueAt(i, 0);
                        long start = System.currentTimeMillis();
                        long ping = -1;
                        try {
                            if (InetAddress.getByName(ip).isReachable(1000)) {
                                ping = System.currentTimeMillis() - start;
                            }
                        } catch(Exception e){}
                        
                        final long finalPing = ping;
                        final int row = i;
                        SwingUtilities.invokeLater(() -> {
                            if (finalPing >= 0) tableModel.setValueAt(finalPing + " ms", row, 3);
                            else tableModel.setValueAt("Timeout", row, 3);
                        });
                        try { Thread.sleep(100); } catch(Exception e){}
                    }
                    try { Thread.sleep(2000); } catch(Exception e){}
                }
            });
            livePingThread.start();
        } else {
            livePingBtn.setText("Start Live Ping");
        }
    }
    
    private void toggleMonitorMode() {
        if (monitorModeCb.isSelected()) {
            knownMacs.clear();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String m = (String) tableModel.getValueAt(i, 2);
                if (m != null && !m.equals("Not Found")) knownMacs.add(m);
            }
            monitorTimer = new javax.swing.Timer(30000, e -> performSilentScan());
            monitorTimer.start();
            statusLabel.setText("Monitor mode enabled. Scanning every 30s for new devices.");
            monitorModeCb.setForeground(Color.RED);
        } else {
            if (monitorTimer != null) monitorTimer.stop();
            statusLabel.setText("Monitor mode disabled.");
            monitorModeCb.setForeground(UIManager.getColor("CheckBox.foreground"));
        }
    }
    
    private void performSilentScan() {
        String baseIp = subnetField.getText().trim();
        if (baseIp.endsWith(".")) baseIp = baseIp.substring(0, baseIp.length() - 1);
        final String subnet = baseIp;
        
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<String> foundIps = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completed = new AtomicInteger(0);
        for (int i = 1; i <= 254; i++) {
            final int host = i;
            pool.submit(() -> {
                try {
                    InetAddress addr = InetAddress.getByName(subnet + "." + host);
                    if (addr.isReachable(1000)) foundIps.add(subnet + "." + host);
                } catch (Exception ex) {}
                finally {
                    if (completed.incrementAndGet() == 254) {
                        pool.shutdown();
                        Map<String, String> arpMap = getArpTable();
                        for (String ip : foundIps) {
                            String mac = arpMap.get(ip);
                            if (mac != null && !mac.equals("Not Found") && !knownMacs.contains(mac)) {
                                knownMacs.add(mac);
                                Toolkit.getDefaultToolkit().beep();
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(NetworkScannerPanel.this, 
                                        "🔔 New Device Alert!\nIP: " + ip + "\nMAC: " + mac,
                                        "Network Monitor Alert", JOptionPane.INFORMATION_MESSAGE);
                                });
                            }
                        }
                    }
                }
            });
        }
    }
}
