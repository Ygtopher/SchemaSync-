package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.ColumnDefinition;
import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enterprise Database Overview & Health Dashboard Panel.
 * Provides live telemetry, catalog inventory, storage volume distribution,
 * health score, and an executive quick-launchpad.
 *
 * Part of SchemaSync Tier 4 Enterprise Governance & Live Intelligence.
 */
public class DashboardPanel extends JPanel {

    public static class TableStorageInfo {
        public final String tableName;
        public final long rowCount;
        public final long sizeBytes;
        public double percentOfTotal;

        public TableStorageInfo(String tableName, long rowCount, long sizeBytes) {
            this.tableName = tableName;
            this.rowCount = rowCount;
            this.sizeBytes = sizeBytes;
            this.percentOfTotal = 0.0;
        }
    }

    private final DatabaseManager dbManager;
    private final Consumer<String> onNavigateToTable;
    private final Runnable onOpenIndexAdvisor;
    private final Runnable onOpenErDiagram;
    private final Runnable onOpenSchemaDrift;
    private final Runnable onOpenOmniSearch;
    private final Runnable onOpenDataMasking;
    private final Runnable onOpenVault;
    private final Runnable onOpenImporter;
    private final Runnable onOpenMockData;

    // UI Elements
    private JLabel dbTitleLabel;
    private JLabel statusPillLabel;
    private JLabel lastRefreshedLabel;
    private JProgressBar headerProgress;

    // Metrics Cards
    private JLabel connHostUserLabel;
    private JLabel connLatencyLabel;
    private JLabel connAutoCommitLabel;

    private JLabel engineNameVersionLabel;
    private JLabel engineEncodingLabel;

    private JLabel catalogTablesViewsLabel;
    private JLabel catalogColsIndexesLabel;

    private JLabel healthScoreLabel;
    private JLabel healthIssuesLabel;

    // Table Storage Grid
    private DefaultTableModel storageTableModel;
    private JTable storageTable;
    private final List<TableStorageInfo> topTables = new ArrayList<>();

    public DashboardPanel(DatabaseManager dbManager,
                          Consumer<String> onNavigateToTable,
                          Runnable onOpenIndexAdvisor,
                          Runnable onOpenErDiagram,
                          Runnable onOpenSchemaDrift,
                          Runnable onOpenOmniSearch,
                          Runnable onOpenDataMasking,
                          Runnable onOpenVault,
                          Runnable onOpenImporter,
                          Runnable onOpenMockData) {
        this.dbManager = dbManager;
        this.onNavigateToTable = onNavigateToTable;
        this.onOpenIndexAdvisor = onOpenIndexAdvisor;
        this.onOpenErDiagram = onOpenErDiagram;
        this.onOpenSchemaDrift = onOpenSchemaDrift;
        this.onOpenOmniSearch = onOpenOmniSearch;
        this.onOpenDataMasking = onOpenDataMasking;
        this.onOpenVault = onOpenVault;
        this.onOpenImporter = onOpenImporter;
        this.onOpenMockData = onOpenMockData;

        setLayout(new BorderLayout());
        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. Header Toolbar
        JPanel headerPanel = new JPanel(new BorderLayout(10, 6));
        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        dbTitleLabel = new JLabel("🏠 Database Overview & Health Dashboard");
        dbTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 19));
        statusPillLabel = new JLabel("● Disconnected");
        statusPillLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusPillLabel.setForeground(Color.GRAY);
        leftHeader.add(dbTitleLabel);
        leftHeader.add(statusPillLabel);
        headerPanel.add(leftHeader, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerProgress = new JProgressBar();
        headerProgress.setPreferredSize(new Dimension(100, 16));
        headerProgress.setVisible(false);
        rightHeader.add(headerProgress);

        lastRefreshedLabel = new JLabel("Not refreshed");
        lastRefreshedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lastRefreshedLabel.setForeground(Color.GRAY);
        rightHeader.add(lastRefreshedLabel);

        JButton refreshBtn = new JButton("🔄 Refresh Metrics");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        refreshBtn.addActionListener(e -> refresh());
        rightHeader.add(refreshBtn);
        headerPanel.add(rightHeader, BorderLayout.EAST);

        root.add(headerPanel, BorderLayout.NORTH);

        // 2. Metrics Cards Row (4 cards)
        JPanel cardsGrid = new JPanel(new GridLayout(1, 4, 12, 0));
        cardsGrid.setPreferredSize(new Dimension(0, 115));

        // Card 1: Connection Telemetry
        connHostUserLabel = new JLabel("Host: -");
        connLatencyLabel = new JLabel("Latency: -");
        connAutoCommitLabel = new JLabel("Auto-Commit: -");
        cardsGrid.add(createMetricCard("⚡ Live Telemetry", new JLabel[]{connHostUserLabel, connLatencyLabel, connAutoCommitLabel}, new Color(59, 130, 246)));

        // Card 2: Engine & Server Specs
        engineNameVersionLabel = new JLabel("Engine: -");
        engineEncodingLabel = new JLabel("Encoding: -");
        JLabel engineModeLabel = new JLabel("Read/Write Mode");
        cardsGrid.add(createMetricCard("⚙️ Engine Specs", new JLabel[]{engineNameVersionLabel, engineEncodingLabel, engineModeLabel}, new Color(139, 92, 246)));

        // Card 3: Catalog Object Counts
        catalogTablesViewsLabel = new JLabel("0 Tables | 0 Views");
        catalogColsIndexesLabel = new JLabel("0 Columns | 0 Indexes");
        JLabel catalogFkLabel = new JLabel("0 Foreign Keys");
        cardsGrid.add(createMetricCard("📚 Catalog Inventory", new JLabel[]{catalogTablesViewsLabel, catalogColsIndexesLabel, catalogFkLabel}, new Color(16, 185, 129)));

        // Card 4: Health Score & Diagnostics
        healthScoreLabel = new JLabel("Score: -- / 100");
        healthScoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        healthIssuesLabel = new JLabel("Diagnostics ready");
        JLabel healthActionLabel = new JLabel("<html><u>Click to launch Index Advisor →</u></html>");
        healthActionLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        healthActionLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (onOpenIndexAdvisor != null) onOpenIndexAdvisor.run(); }
        });
        cardsGrid.add(createMetricCard("🛡️ Health & Integrity", new JLabel[]{healthScoreLabel, healthIssuesLabel, healthActionLabel}, new Color(245, 158, 11)));

        // 3. Center Section: Left = Top 10 Tables; Right = Executive Launchpad
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setResizeWeight(0.62);

        // Left Component: Top 10 Tables Storage Table
        JPanel storagePanel = new JPanel(new BorderLayout(6, 6));
        storagePanel.setBorder(BorderFactory.createTitledBorder("Top 10 Largest Tables by Storage & Volume (Double-click to Browse)"));

        String[] cols = {"#", "Table Name", "Est. Rows", "Est. Size", "Storage Distribution"};
        storageTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        storageTable = new JTable(storageTableModel);
        storageTable.setRowHeight(26);
        storageTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        storageTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        storageTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        storageTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        storageTable.getColumnModel().getColumn(4).setPreferredWidth(160);

        // Progress bar renderer for column 4
        storageTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            private final JProgressBar pBar = new JProgressBar(0, 100);
            {
                pBar.setStringPainted(true);
                pBar.setBorderPainted(false);
            }
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                if (value instanceof Number) {
                    int pct = ((Number) value).intValue();
                    pBar.setValue(pct);
                    pBar.setString(pct + "%");
                    pBar.setForeground(pct > 40 ? new Color(239, 68, 68) : (pct > 20 ? new Color(245, 158, 11) : new Color(59, 130, 246)));
                } else {
                    pBar.setValue(0);
                    pBar.setString("0%");
                }
                return pBar;
            }
        });

        storageTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && storageTable.getSelectedRow() >= 0) {
                    int r = storageTable.getSelectedRow();
                    String tbl = (String) storageTable.getValueAt(r, 1);
                    if (tbl != null && onNavigateToTable != null) {
                        onNavigateToTable.accept(tbl);
                    }
                }
            }
        });

        JScrollPane storageScroll = new JScrollPane(storageTable);
        storagePanel.add(storageScroll, BorderLayout.CENTER);
        centerSplit.setLeftComponent(storagePanel);

        // Right Component: Executive Launchpad
        JPanel launchpadPanel = new JPanel();
        launchpadPanel.setLayout(new BoxLayout(launchpadPanel, BoxLayout.Y_AXIS));
        launchpadPanel.setBorder(BorderFactory.createTitledBorder("Executive Launchpad & Security Hub"));

        launchpadPanel.add(createActionTile("⚡ Index & Performance Advisor", "Scan unindexed foreign keys, redundant indexes, and missing PKs with 1-click fixes.", onOpenIndexAdvisor));
        launchpadPanel.add(Box.createVerticalStrut(6));
        launchpadPanel.add(createActionTile("📐 Interactive Architecture & ER Diagram", "High-contrast zoomable schema canvas with PNG & Mermaid.js exports.", onOpenErDiagram));
        launchpadPanel.add(Box.createVerticalStrut(6));
        launchpadPanel.add(createActionTile("📸 Schema Drift & Migration Studio", "Compare database snapshots, detect structural drift, and generate UP/DOWN scripts.", onOpenSchemaDrift));
        launchpadPanel.add(Box.createVerticalStrut(6));
        launchpadPanel.add(createActionTile("🔍 Global OmniSearch (Ctrl+Shift+F)", "Search IDs, text, and keywords across all tables and columns in streaming parallel.", onOpenOmniSearch));
        launchpadPanel.add(Box.createVerticalStrut(6));
        launchpadPanel.add(createActionTile("🔒 Sensitive Data Masking & Anonymizer", "Sanitize PII (emails, phones, names) for GDPR-compliant dev/staging datasets.", onOpenDataMasking));
        launchpadPanel.add(Box.createVerticalStrut(6));
        launchpadPanel.add(createActionTile("🛡️ Encrypted Credential Vault (AES-256)", "Manage Master Password and securely export/import encrypted connection bundles.", onOpenVault));
        launchpadPanel.add(Box.createVerticalStrut(6));
        launchpadPanel.add(createActionTile("📥 Smart CSV / Excel Data Importer", "Drag-and-drop batch file importer with auto-mapping and rollback transaction safety.", onOpenImporter));
        launchpadPanel.add(Box.createVerticalStrut(6));
        launchpadPanel.add(createActionTile("🧪 Synthetic Mock Data Generator", "Generate 10 to 10,000 realistic sample records respecting foreign key constraints.", onOpenMockData));

        JScrollPane launchpadScroll = new JScrollPane(launchpadPanel);
        launchpadScroll.setBorder(BorderFactory.createEmptyBorder());
        centerSplit.setRightComponent(launchpadScroll);

        // Assemble Content
        JPanel mainContent = new JPanel(new BorderLayout(0, 12));
        mainContent.add(cardsGrid, BorderLayout.NORTH);
        mainContent.add(centerSplit, BorderLayout.CENTER);

        root.add(mainContent, BorderLayout.CENTER);
        add(root, BorderLayout.CENTER);
    }

    private JPanel createMetricCard(String title, JLabel[] items, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ThemeManager.isDarkMode() ? new Color(60, 60, 60) : new Color(210, 210, 210), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel titlePanel = new JPanel(new BorderLayout());
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLbl.setForeground(accentColor);
        titlePanel.add(titleLbl, BorderLayout.WEST);
        card.add(titlePanel, BorderLayout.NORTH);

        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        for (JLabel lbl : items) {
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            itemsPanel.add(lbl);
            itemsPanel.add(Box.createVerticalStrut(3));
        }
        card.add(itemsPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createActionTile(String title, String description, Runnable action) {
        JPanel tile = new JPanel(new BorderLayout(8, 2));
        tile.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ThemeManager.isDarkMode() ? new Color(50, 50, 50) : new Color(225, 225, 225), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JLabel descLbl = new JLabel("<html>" + description + "</html>");
        descLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        descLbl.setForeground(ThemeManager.isDarkMode() ? new Color(160, 160, 160) : new Color(100, 100, 100));

        textPanel.add(titleLbl);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(descLbl);
        tile.add(textPanel, BorderLayout.CENTER);

        JButton launchBtn = new JButton("Open →");
        launchBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        launchBtn.setMargin(new Insets(2, 8, 2, 8));
        launchBtn.addActionListener(e -> {
            if (action != null) action.run();
        });
        tile.add(launchBtn, BorderLayout.EAST);
        return tile;
    }

    public void refresh() {
        if (dbManager == null || dbManager.connection == null) {
            statusPillLabel.setText("● Disconnected");
            statusPillLabel.setForeground(Color.GRAY);
            return;
        }

        headerProgress.setVisible(true);
        headerProgress.setIndeterminate(true);

        new Thread(() -> {
            try {
                long pingStart = System.currentTimeMillis();
                try (Statement s = dbManager.connection.createStatement()) {
                    s.execute("SELECT 1");
                }
                long latency = System.currentTimeMillis() - pingStart;

                DatabaseMetaData meta = dbManager.connection.getMetaData();
                String productName = meta.getDatabaseProductName();
                String productVersion = meta.getDatabaseProductVersion();
                String user = meta.getUserName();
                String url = meta.getURL();
                boolean autoCommit = dbManager.connection.getAutoCommit();

                List<String> tables = dbManager.getTableNames();
                int tableCount = tables.size();

                // Compute storage and table sizes
                List<TableStorageInfo> storageList = new ArrayList<>();
                long totalCatalogBytes = 0;

                for (String t : tables) {
                    long rows = -1;
                    long bytes = 0;
                    try (Statement s = dbManager.connection.createStatement()) {
                        s.setMaxRows(1);
                        if (dbManager.isPostgres()) {
                            try (ResultSet rs = s.executeQuery("SELECT pg_total_relation_size('" + t + "')")) {
                                if (rs.next()) bytes = rs.getLong(1);
                            }
                            try (ResultSet rs = s.executeQuery("SELECT n_live_tup FROM pg_stat_user_tables WHERE relname = '" + t + "'")) {
                                if (rs.next()) rows = rs.getLong(1);
                            }
                        } else if (dbManager.isMySQL()) {
                            try (ResultSet rs = s.executeQuery("SELECT table_rows, (data_length + index_length) FROM information_schema.tables WHERE table_name = '" + t + "'")) {
                                if (rs.next()) {
                                    rows = rs.getLong(1);
                                    bytes = rs.getLong(2);
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (rows < 0) {
                        // Lightweight count estimate
                        try (Statement s = dbManager.connection.createStatement()) {
                            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + t)) {
                                if (rs.next()) rows = rs.getLong(1);
                            }
                        } catch (Exception ignored) {}
                    }
                    if (bytes == 0 && rows > 0) {
                        bytes = rows * 128L; // Fallback estimated bytes per row
                    }

                    totalCatalogBytes += bytes;
                    storageList.add(new TableStorageInfo(t, Math.max(0, rows), bytes));
                }

                // Sort descending by bytes/rows
                storageList.sort((a, b) -> Long.compare(b.sizeBytes > 0 ? b.sizeBytes : b.rowCount, a.sizeBytes > 0 ? a.sizeBytes : a.rowCount));

                // Calculate percentages
                final long finalTotalBytes = totalCatalogBytes;
                for (TableStorageInfo info : storageList) {
                    if (finalTotalBytes > 0) {
                        info.percentOfTotal = (double) info.sizeBytes / finalTotalBytes * 100.0;
                    }
                }

                // Count columns and indexes
                int totalCols = 0;
                int totalIdx = 0;
                int totalFk = 0;
                for (int i = 0; i < Math.min(tables.size(), 20); i++) {
                    String t = tables.get(i);
                    totalCols += dbManager.getColumnNames(t).size();
                    try (ResultSet rs = meta.getIndexInfo(null, null, t, false, false)) {
                        while (rs.next()) totalIdx++;
                    } catch (Exception ignored) {}
                    totalFk += dbManager.getForeignKeys(t).size();
                }

                final int finalCols = totalCols;
                final int finalIdx = totalIdx;
                final int finalFk = totalFk;
                final long finalLatency = latency;

                SwingUtilities.invokeLater(() -> {
                    headerProgress.setVisible(false);
                    statusPillLabel.setText("● Connected: " + productName);
                    statusPillLabel.setForeground(new Color(16, 185, 129));

                    connHostUserLabel.setText("User: " + (user != null ? user : "default") + " | URL: " + truncate(url, 28));
                    connLatencyLabel.setText("Ping Latency: " + finalLatency + " ms");
                    connAutoCommitLabel.setText("Auto-Commit: " + (autoCommit ? "ON (Safe)" : "MANUAL (Active Tx)"));

                    engineNameVersionLabel.setText("Engine: " + productName);
                    engineEncodingLabel.setText("Version: " + truncate(productVersion, 25));

                    catalogTablesViewsLabel.setText(tableCount + " Tables Cataloged");
                    catalogColsIndexesLabel.setText(finalCols + "+ Columns | " + finalIdx + "+ Indexes");

                    int healthScore = 95;
                    if (finalLatency > 200) healthScore -= 10;
                    if (finalFk > 0 && finalIdx < finalFk) healthScore -= 15;
                    healthScoreLabel.setText("Score: " + healthScore + " / 100 " + (healthScore > 85 ? "EXCELLENT" : "GOOD"));
                    healthScoreLabel.setForeground(healthScore > 85 ? new Color(16, 185, 129) : new Color(245, 158, 11));
                    healthIssuesLabel.setText(finalFk + " FKs monitored | " + tableCount + " active tables");

                    // Populate storage table
                    storageTableModel.setRowCount(0);
                    int rank = 1;
                    for (int i = 0; i < Math.min(storageList.size(), 10); i++) {
                        TableStorageInfo tsi = storageList.get(i);
                        storageTableModel.addRow(new Object[]{
                                rank++,
                                tsi.tableName,
                                String.format("%,d", tsi.rowCount),
                                formatBytes(tsi.sizeBytes),
                                (int) Math.round(tsi.percentOfTotal)
                        });
                    }

                    lastRefreshedLabel.setText("Refreshed " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    headerProgress.setVisible(false);
                    statusPillLabel.setText("● Telemetry Error");
                    statusPillLabel.setForeground(Color.RED);
                });
            }
        }).start();
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "-";
        return str.length() <= maxLen ? str : str.substring(0, maxLen - 3) + "...";
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "-";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
