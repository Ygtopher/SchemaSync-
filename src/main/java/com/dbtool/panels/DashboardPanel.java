package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.AppUsageTracker;
import com.dbtool.util.QueryHistory;
import com.dbtool.util.SavedQueries;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enterprise Database Overview, Health & Comprehensive Application Usage Statistics Dashboard Panel.
 * Provides live database telemetry, deep schema quality & column type distribution,
 * storage volume distribution, health score, persistent application usage analytics
 * (latency percentiles, daily trends, hot tables, safety/linter governance, data movement,
 * JVM runtime memory), and an executive quick-launchpad.
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

    // Row 1: Live Database, Runtime Memory & Schema Quality Cards
    private JLabel connHostUserLabel;
    private JLabel connLatencyLabel;
    private JLabel connAutoCommitLabel;

    private JLabel engineNameVersionLabel;
    private JLabel runtimeMemoryLabel;

    private JLabel catalogTablesViewsLabel;
    private JLabel catalogColsIndexesLabel;
    private JLabel catalogCacheStatsLabel;

    private JLabel healthScoreLabel;
    private JLabel healthIssuesLabel;

    // Row 2: Sessions, Query Performance, Percentiles & Workload Cards
    private JLabel usageSessionsLabel;
    private JLabel usageUptimeLabel;
    private JLabel usageStreakLabel;

    private JLabel usageQueriesTotalLabel;
    private JLabel usageDailyTrendLabel;
    private JLabel usageSuccessRateLabel;

    private JLabel usageLatencyPercentilesLabel;
    private JLabel usageSlowQueriesLabel;
    private JLabel usageRowVolumeLabel;

    private JLabel usageSelectBreakdownLabel;
    private JLabel usageDmlDdlBreakdownLabel;
    private JLabel usageHotTableLabel;

    // Row 3: Governance, Data Movement, Column Types & Library Cards
    private JLabel usageGuardrailLabel;
    private JLabel usageLinterStatsLabel;
    private JLabel usagePrivacyDriftLabel;

    private JLabel usageConnectionsExportsLabel;
    private JLabel usageIngestVolumeLabel;
    private JLabel usageSyncCodegenLabel;

    private JLabel schemaTypeTextNumLabel;
    private JLabel schemaTypeDateBoolLabel;
    private JLabel schemaTypeJsonOtherLabel;

    private JLabel usageLibraryCountsLabel;
    private JLabel usageTopFeatureLabel;
    private JLabel usageTopEngineLabel;

    // Live Schema Type & Quality Counters (from connected DB)
    private volatile int lastTextCols = 0;
    private volatile int lastNumericCols = 0;
    private volatile int lastDateCols = 0;
    private volatile int lastBoolCols = 0;
    private volatile int lastOtherCols = 0;
    private volatile int lastMissingPkTables = 0;
    private volatile int lastOrphanTables = 0;
    private volatile int lastEmptyTables = 0;
    private volatile int lastPopulatedTables = 0;

    // Table Storage Grid
    private DefaultTableModel storageTableModel;
    private JTable storageTable;

    // Application Usage Breakdown Grid + Category Filter
    private DefaultTableModel usageBreakdownTableModel;
    private JTable usageBreakdownTable;
    private JComboBox<String> usageCategoryFilterCombo;

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
        refreshUsageStatistics();

        // Automatically refresh usage stats when telemetry updates or periodically for live session uptime & JVM RAM
        AppUsageTracker.addListener(() -> SwingUtilities.invokeLater(this::refreshUsageStatistics));
        javax.swing.Timer liveUptimeTimer = new javax.swing.Timer(5000, e -> {
            if (isShowing()) {
                refreshUsageStatistics();
            }
        });
        liveUptimeTimer.start();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 16, 10, 16));

        // 1. Header Toolbar
        JPanel headerPanel = new JPanel(new BorderLayout(10, 4));
        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        dbTitleLabel = new JLabel("🏠 Database Overview & Application Usage Dashboard");
        dbTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        statusPillLabel = new JLabel("● Disconnected");
        statusPillLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusPillLabel.setForeground(Color.GRAY);
        leftHeader.add(dbTitleLabel);
        leftHeader.add(statusPillLabel);
        headerPanel.add(leftHeader, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerProgress = new JProgressBar();
        headerProgress.setPreferredSize(new Dimension(100, 16));
        headerProgress.setVisible(false);
        rightHeader.add(headerProgress);

        lastRefreshedLabel = new JLabel("Ready");
        lastRefreshedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lastRefreshedLabel.setForeground(Color.GRAY);
        rightHeader.add(lastRefreshedLabel);

        JButton resetUsageBtn = new JButton("🧹 Reset Usage Stats");
        resetUsageBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        resetUsageBtn.setToolTipText("Reset cumulative application usage statistics");
        resetUsageBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to reset all cumulative application usage statistics?",
                    "Reset Application Usage Statistics",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                AppUsageTracker.resetStats();
                refreshUsageStatistics();
            }
        });
        rightHeader.add(resetUsageBtn);

        JButton refreshBtn = new JButton("🔄 Refresh Metrics");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        refreshBtn.addActionListener(e -> refresh());
        rightHeader.add(refreshBtn);
        headerPanel.add(rightHeader, BorderLayout.EAST);

        root.add(headerPanel, BorderLayout.NORTH);

        // 2. Top Metrics Container (3 rows of 4 cards = 12 telemetry cards)
        JPanel allCardsContainer = new JPanel(new GridLayout(3, 4, 8, 6));

        // --- ROW 1: Live Database, Runtime RAM & Schema Integrity ---
        connHostUserLabel = new JLabel("Host: -");
        connLatencyLabel = new JLabel("Ping Latency: -");
        connAutoCommitLabel = new JLabel("Auto-Commit: -");
        allCardsContainer.add(createMetricCard("⚡ Live DB Telemetry",
                new JLabel[]{connHostUserLabel, connLatencyLabel, connAutoCommitLabel}, new Color(59, 130, 246)));

        engineNameVersionLabel = new JLabel("Engine: -");
        runtimeMemoryLabel = new JLabel("JVM RAM: -");
        JLabel gcActionLabel = new JLabel("<html><u>⚡ Optimize Memory (Run GC) →</u></html>");
        gcActionLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        gcActionLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                System.gc();
                refreshUsageStatistics();
            }
        });
        allCardsContainer.add(createMetricCard("⚙️ Engine & Runtime Health",
                new JLabel[]{engineNameVersionLabel, runtimeMemoryLabel, gcActionLabel}, new Color(139, 92, 246)));

        catalogTablesViewsLabel = new JLabel("0 Tables (0 Populated / 0 Empty)");
        catalogColsIndexesLabel = new JLabel("0 Columns | 0 Indexes");
        catalogCacheStatsLabel = new JLabel("Cache: 0 Tbls | 0 Cols | 0 FKs");
        allCardsContainer.add(createMetricCard("📚 Catalog & Cache Inventory",
                new JLabel[]{catalogTablesViewsLabel, catalogColsIndexesLabel, catalogCacheStatsLabel}, new Color(16, 185, 129)));

        healthScoreLabel = new JLabel("Score: -- / 100");
        healthScoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        healthIssuesLabel = new JLabel("Missing PKs: - | Orphan Tbls: -");
        JLabel healthActionLabel = new JLabel("<html><u>Click to launch Index Advisor →</u></html>");
        healthActionLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        healthActionLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (onOpenIndexAdvisor != null) onOpenIndexAdvisor.run(); }
        });
        allCardsContainer.add(createMetricCard("🛡️ Schema Health & Integrity",
                new JLabel[]{healthScoreLabel, healthIssuesLabel, healthActionLabel}, new Color(245, 158, 11)));

        // --- ROW 2: App Sessions, Query Trends, Latency Percentiles & Workload ---
        usageSessionsLabel = new JLabel("Launches: 1 | Session: 0s");
        usageUptimeLabel = new JLabel("Cumulative Time: 0s");
        usageStreakLabel = new JLabel("Active Days: 1 | Streak: 🔥 1d");
        allCardsContainer.add(createMetricCard("🚀 Sessions & Activity Streak",
                new JLabel[]{usageSessionsLabel, usageUptimeLabel, usageStreakLabel}, new Color(14, 165, 233)));

        usageQueriesTotalLabel = new JLabel("Queries Run: 0 (Session: 0)");
        usageDailyTrendLabel = new JLabel("Today: 0 | Yesterday: 0 | 7d: 0");
        usageSuccessRateLabel = new JLabel("Success: 100.0% (0 OK / 0 Err)");
        allCardsContainer.add(createMetricCard("📈 Query Volume & Daily Trend",
                new JLabel[]{usageQueriesTotalLabel, usageDailyTrendLabel, usageSuccessRateLabel}, new Color(16, 185, 129)));

        usageLatencyPercentilesLabel = new JLabel("Avg: 0ms | P50: 0ms | P95: 0ms");
        usageSlowQueriesLabel = new JLabel("Max: 0ms | Slow (>500ms): 0");
        usageRowVolumeLabel = new JLabel("Rows Read: 0 | Mutated: 0");
        allCardsContainer.add(createMetricCard("⏱️ Latency Percentiles & Rows",
                new JLabel[]{usageLatencyPercentilesLabel, usageSlowQueriesLabel, usageRowVolumeLabel}, new Color(249, 115, 22)));

        usageSelectBreakdownLabel = new JLabel("SELECT: 0 (0%) | EXPLAIN: 0");
        usageDmlDdlBreakdownLabel = new JLabel("DML (INS/UPD/DEL): 0 | DDL: 0");
        usageHotTableLabel = new JLabel("🔥 Hot Table: -");
        allCardsContainer.add(createMetricCard("📊 SQL Workload & Hot Tables",
                new JLabel[]{usageSelectBreakdownLabel, usageDmlDdlBreakdownLabel, usageHotTableLabel}, new Color(168, 85, 247)));

        // --- ROW 3: Safety Governance, Data Movement, Column Types & Library ---
        usageGuardrailLabel = new JLabel("Destructive DML Guarded: 0");
        usageLinterStatsLabel = new JLabel("Linter Warnings: 0 | Fixes: 0");
        usagePrivacyDriftLabel = new JLabel("PII Masked: 0 | Drift Ops: 0");
        allCardsContainer.add(createMetricCard("🛡️ Safety & Linter Governance",
                new JLabel[]{usageGuardrailLabel, usageLinterStatsLabel, usagePrivacyDriftLabel}, new Color(239, 68, 68)));

        usageConnectionsExportsLabel = new JLabel("DB Connections: 0 | Exports: 0");
        usageIngestVolumeLabel = new JLabel("Imported: 0 | Mock Rows: 0");
        usageSyncCodegenLabel = new JLabel("DB Compares: 0 | ORM Models: 0");
        allCardsContainer.add(createMetricCard("🔄 Data Movement & CodeGen",
                new JLabel[]{usageConnectionsExportsLabel, usageIngestVolumeLabel, usageSyncCodegenLabel}, new Color(6, 182, 212)));

        schemaTypeTextNumLabel = new JLabel("Text/VARCHAR: - | Numeric: -");
        schemaTypeDateBoolLabel = new JLabel("Date/Time: - | Boolean: -");
        schemaTypeJsonOtherLabel = new JLabel("JSON/Binary/Other: -");
        allCardsContainer.add(createMetricCard("🗄️ Column Type Distribution",
                new JLabel[]{schemaTypeTextNumLabel, schemaTypeDateBoolLabel, schemaTypeJsonOtherLabel}, new Color(99, 102, 241)));

        usageLibraryCountsLabel = new JLabel("Saved: 0 | History: 0 (⭐ 0 Pinned)");
        usageTopFeatureLabel = new JLabel("Top Tool: -");
        usageTopEngineLabel = new JLabel("Top Engine: -");
        allCardsContainer.add(createMetricCard("🔖 Workbench & Library Stats",
                new JLabel[]{usageLibraryCountsLabel, usageTopFeatureLabel, usageTopEngineLabel}, new Color(236, 72, 153)));

        // 3. Center Section: Left = Vertically Split (Usage Breakdown Table + Top 10 Largest Tables); Right = Executive Launchpad
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setResizeWeight(0.65);

        // Left Top: Application Usage Breakdown Table with Category Filter
        JPanel usageTablePanel = new JPanel(new BorderLayout(4, 4));
        usageTablePanel.setBorder(BorderFactory.createTitledBorder("📊 Comprehensive Usage & Telemetry Breakdown (Tools, Hot Tables, SQL Mix, Daily Activity & Schema)"));

        JPanel usageFilterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        usageFilterBar.add(new JLabel("Filter Category:"));
        usageCategoryFilterCombo = new JComboBox<>(new String[]{
                "All Categories",
                "🛠️ Workbench Tools",
                "🔥 Hot Queried Tables",
                "⚡ SQL Workload",
                "📅 Daily Activity (7d)",
                "🛡️ Safety & Data Movement",
                "🗄️ DB Engines & Column Types"
        });
        usageCategoryFilterCombo.addActionListener(e -> refreshUsageStatistics());
        usageFilterBar.add(usageCategoryFilterCombo);
        usageTablePanel.add(usageFilterBar, BorderLayout.NORTH);

        String[] usageCols = {"Category", "Feature / Metric / Table", "Volume / Count", "Share / Intensity"};
        usageBreakdownTableModel = new DefaultTableModel(usageCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        usageBreakdownTable = new JTable(usageBreakdownTableModel);
        usageBreakdownTable.setRowHeight(23);
        usageBreakdownTable.getColumnModel().getColumn(0).setPreferredWidth(135);
        usageBreakdownTable.getColumnModel().getColumn(1).setPreferredWidth(215);
        usageBreakdownTable.getColumnModel().getColumn(2).setPreferredWidth(95);
        usageBreakdownTable.getColumnModel().getColumn(3).setPreferredWidth(145);

        usageBreakdownTable.getColumnModel().getColumn(3).setCellRenderer(createProgressCellRenderer(new Color(14, 165, 233)));
        usageTablePanel.add(new JScrollPane(usageBreakdownTable), BorderLayout.CENTER);

        // Left Bottom: Top 10 Largest Tables by Storage & Volume
        JPanel storagePanel = new JPanel(new BorderLayout(4, 4));
        storagePanel.setBorder(BorderFactory.createTitledBorder("Top 10 Largest Tables by Storage & Volume (Double-click to Browse)"));

        String[] cols = {"#", "Table Name", "Est. Rows", "Est. Size", "Storage Distribution"};
        storageTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        storageTable = new JTable(storageTableModel);
        storageTable.setRowHeight(23);
        storageTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        storageTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        storageTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        storageTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        storageTable.getColumnModel().getColumn(4).setPreferredWidth(145);

        storageTable.getColumnModel().getColumn(4).setCellRenderer(createProgressCellRenderer(new Color(59, 130, 246)));

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

        storagePanel.add(new JScrollPane(storageTable), BorderLayout.CENTER);

        JSplitPane leftVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, usageTablePanel, storagePanel);
        leftVerticalSplit.setResizeWeight(0.55);
        leftVerticalSplit.setBorder(BorderFactory.createEmptyBorder());
        centerSplit.setLeftComponent(leftVerticalSplit);

        // Right Component: Executive Launchpad
        JPanel launchpadPanel = new JPanel();
        launchpadPanel.setLayout(new BoxLayout(launchpadPanel, BoxLayout.Y_AXIS));
        launchpadPanel.setBorder(BorderFactory.createTitledBorder("Executive Launchpad & Security Hub"));

        launchpadPanel.add(createActionTile("⚡ Index & Performance Advisor", "Scan unindexed foreign keys, redundant indexes, and missing PKs with 1-click fixes.", onOpenIndexAdvisor));
        launchpadPanel.add(Box.createVerticalStrut(5));
        launchpadPanel.add(createActionTile("📐 Interactive Architecture & ER Diagram", "High-contrast zoomable schema canvas with PNG & Mermaid.js exports.", onOpenErDiagram));
        launchpadPanel.add(Box.createVerticalStrut(5));
        launchpadPanel.add(createActionTile("📸 Schema Drift & Migration Studio", "Compare database snapshots, detect structural drift, and generate UP/DOWN scripts.", onOpenSchemaDrift));
        launchpadPanel.add(Box.createVerticalStrut(5));
        launchpadPanel.add(createActionTile("🔍 Global OmniSearch (Ctrl+Shift+F)", "Search IDs, text, and keywords across all tables and columns in streaming parallel.", onOpenOmniSearch));
        launchpadPanel.add(Box.createVerticalStrut(5));
        launchpadPanel.add(createActionTile("🔒 Sensitive Data Masking & Anonymizer", "Sanitize PII (emails, phones, names) for GDPR-compliant dev/staging datasets.", onOpenDataMasking));
        launchpadPanel.add(Box.createVerticalStrut(5));
        launchpadPanel.add(createActionTile("🛡️ Encrypted Credential Vault (AES-256)", "Manage Master Password and securely export/import encrypted connection bundles.", onOpenVault));
        launchpadPanel.add(Box.createVerticalStrut(5));
        launchpadPanel.add(createActionTile("📥 Smart CSV / Excel Data Importer", "Drag-and-drop batch file importer with auto-mapping and rollback transaction safety.", onOpenImporter));
        launchpadPanel.add(Box.createVerticalStrut(5));
        launchpadPanel.add(createActionTile("🧪 Synthetic Mock Data Generator", "Generate 10 to 10,000 realistic sample records respecting foreign key constraints.", onOpenMockData));

        JScrollPane launchpadScroll = new JScrollPane(launchpadPanel);
        launchpadScroll.setBorder(BorderFactory.createEmptyBorder());
        centerSplit.setRightComponent(launchpadScroll);

        // Assemble Content
        JPanel mainContent = new JPanel(new BorderLayout(0, 8));
        mainContent.add(allCardsContainer, BorderLayout.NORTH);
        mainContent.add(centerSplit, BorderLayout.CENTER);

        root.add(mainContent, BorderLayout.CENTER);
        add(root, BorderLayout.CENTER);
    }

    private DefaultTableCellRenderer createProgressCellRenderer(Color baseColor) {
        return new DefaultTableCellRenderer() {
            private final JProgressBar pBar = new JProgressBar(0, 100);
            {
                pBar.setStringPainted(true);
                pBar.setBorderPainted(false);
            }
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                if (value instanceof Number) {
                    int pct = Math.max(0, Math.min(100, ((Number) value).intValue()));
                    pBar.setValue(pct);
                    pBar.setString(pct + "%");
                    pBar.setForeground(pct > 60 ? new Color(16, 185, 129) : (pct > 25 ? baseColor : new Color(139, 92, 246)));
                } else {
                    pBar.setValue(0);
                    pBar.setString("0%");
                }
                return pBar;
            }
        };
    }

    private JPanel createMetricCard(String title, JLabel[] items, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ThemeManager.isDarkMode() ? new Color(60, 60, 60) : new Color(210, 210, 210), 1, true),
                new EmptyBorder(5, 9, 5, 9)
        ));

        JPanel titlePanel = new JPanel(new BorderLayout());
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(accentColor);
        titlePanel.add(titleLbl, BorderLayout.WEST);
        card.add(titlePanel, BorderLayout.NORTH);

        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        for (JLabel lbl : items) {
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            itemsPanel.add(lbl);
            itemsPanel.add(Box.createVerticalStrut(1));
        }
        card.add(itemsPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createActionTile(String title, String description, Runnable action) {
        JPanel tile = new JPanel(new BorderLayout(8, 2));
        tile.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ThemeManager.isDarkMode() ? new Color(50, 50, 50) : new Color(225, 225, 225), 1, true),
                new EmptyBorder(5, 10, 5, 10)
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

    /**
     * Updates all Application Usage & Activity Statistics cards, Runtime Memory, and the Breakdown Table.
     * Works both when disconnected and when connected to a database.
     */
    public void refreshUsageStatistics() {
        AppUsageTracker.UsageStatsData d = AppUsageTracker.getStats();
        long sessionSec = AppUsageTracker.getCurrentSessionUptimeSeconds();
        long totalSec = AppUsageTracker.getTotalUptimeSeconds();

        // Runtime Memory & Cache
        long usedMb = AppUsageTracker.getJvmUsedMemoryMb();
        long maxMb = AppUsageTracker.getJvmMaxMemoryMb();
        int memPct = AppUsageTracker.getJvmMemoryUsagePercent();
        runtimeMemoryLabel.setText(String.format("JVM Heap: %,d / %,d MB (%d%%)", usedMb, maxMb, memPct));

        if (dbManager != null) {
            catalogCacheStatsLabel.setText(String.format("Cache: %,d Tbls | %,d Cols | %,d FKs",
                    dbManager.getCachedTableCount(), dbManager.getCachedColumnCount(), dbManager.getCachedFkCount()));
        }

        // Card 5: Sessions, Uptime & Streak
        long sessions = Math.max(1L, d.totalSessions);
        usageSessionsLabel.setText(String.format("Launches: %,d | Session: %s",
                sessions, AppUsageTracker.formatDuration(sessionSec)));
        usageUptimeLabel.setText("Cumulative Time: " + AppUsageTracker.formatDuration(totalSec));
        usageStreakLabel.setText(String.format("Active Days: %,d | Streak: 🔥 %,dd",
                AppUsageTracker.getActiveDaysCount(), AppUsageTracker.getCurrentDayStreak()));

        // Card 6: Query Volume & Daily Trend
        long totalQ = d.totalQueriesExecuted;
        long sessQ = AppUsageTracker.getSessionQueriesExecuted();
        long todayQ = AppUsageTracker.getTodayQueriesCount();
        long yestQ = AppUsageTracker.getYesterdayQueriesCount();
        long last7Q = AppUsageTracker.getLast7DaysQueriesCount();
        double successRate = AppUsageTracker.getSuccessRatePercent();

        usageQueriesTotalLabel.setText(String.format("Queries Run: %,d (Session: %,d)", totalQ, sessQ));
        usageDailyTrendLabel.setText(String.format("Today: %,d | Yest: %,d | 7d: %,d", todayQ, yestQ, last7Q));
        usageSuccessRateLabel.setText(String.format("Success: %.1f%% (%,d OK / %,d Err)",
                successRate, d.successfulQueries, d.failedQueries));

        // Card 7: Latency Percentiles, Slow Queries & Row Volume
        long avgLat = AppUsageTracker.getAverageQueryDurationMs();
        long p50Lat = AppUsageTracker.getP50LatencyMs();
        long p95Lat = AppUsageTracker.getP95LatencyMs();
        usageLatencyPercentilesLabel.setText(String.format("Avg: %,dms | P50: %,dms | P95: %,dms", avgLat, p50Lat, p95Lat));
        usageSlowQueriesLabel.setText(String.format("Max: %,dms | Slow (>500ms): %,d", d.maxQueryDurationMs, d.slowQueriesCount));
        usageSlowQueriesLabel.setToolTipText(d.slowestQuerySql != null && !d.slowestQuerySql.isEmpty()
                ? "Slowest Query: " + d.slowestQuerySql
                : "No slow queries recorded");
        usageRowVolumeLabel.setText(String.format("Rows Read: %,d | Mutated: %,d", d.totalRowsFetched, d.totalRowsMutated));

        // Card 8: SQL Workload & Hot Tables
        int selectPct = totalQ > 0 ? (int) Math.round((d.selectQueries * 100.0) / totalQ) : 0;
        usageSelectBreakdownLabel.setText(String.format("SELECT: %,d (%d%%) | EXPLAIN: %,d", d.selectQueries, selectPct, d.explainQueries));
        usageDmlDdlBreakdownLabel.setText(String.format("DML (INS/UPD/DEL): %,d | DDL: %,d", d.dmlQueries, d.ddlQueries));
        usageHotTableLabel.setText("🔥 Hot Table: " + truncate(AppUsageTracker.getTopEntry(d.queriedTableCounts, "None yet"), 25));

        // Card 9: Safety & Linter Governance
        usageGuardrailLabel.setText(String.format("Destructive DML Guarded: %,d", d.destructiveQueriesIntercepted));
        usageLinterStatsLabel.setText(String.format("Linter Warnings: %,d | Fixes: %,d", d.linterIssuesDetected, d.linterFixesApplied));
        usagePrivacyDriftLabel.setText(String.format("PII Masked: %,d | Drift Ops: %,d", d.piiRowsMasked, d.schemaSnapshotsAndMigrations));

        // Card 10: Data Movement & CodeGen
        usageConnectionsExportsLabel.setText(String.format("DB Connections: %,d | Exports: %,d", d.totalConnectionsOpened, d.totalExportsPerformed));
        usageIngestVolumeLabel.setText(String.format("Imported: %,d | Mock Rows: %,d", d.rowsImported, d.mockRowsGenerated));
        usageSyncCodegenLabel.setText(String.format("DB Compares: %,d | ORM Models: %,d", d.dualDbComparisonsRun, d.ormModelsGenerated));

        // Card 12: Workbench & Library Stats
        int savedCount = 0;
        int historyCount = 0;
        int pinnedCount = 0;
        try {
            savedCount = SavedQueries.load().size();
            List<String> historyList = QueryHistory.load();
            historyCount = historyList.size();
            Map<String, QueryHistory.HistoryMetadata> meta = QueryHistory.loadMetadataMap();
            for (QueryHistory.HistoryMetadata e : meta.values()) {
                if (e != null && e.pinned) pinnedCount++;
            }
        } catch (Exception ignored) {}

        usageLibraryCountsLabel.setText(String.format("Saved: %,d | History: %,d (⭐ %,d Pinned)",
                savedCount, historyCount, pinnedCount));
        usageTopFeatureLabel.setText("Top Tool: " + truncate(AppUsageTracker.getTopEntry(d.featureUsageCounts, "Dashboard"), 26));
        usageTopEngineLabel.setText("Top Engine: " + truncate(AppUsageTracker.getTopEntry(d.engineConnections, "None yet"), 24));

        // Populate Usage Breakdown Table
        if (usageBreakdownTableModel != null) {
            usageBreakdownTableModel.setRowCount(0);
            String filter = usageCategoryFilterCombo != null && usageCategoryFilterCombo.getSelectedItem() != null
                    ? usageCategoryFilterCombo.getSelectedItem().toString()
                    : "All Categories";
            boolean showAll = "All Categories".equals(filter);

            // 1. Workbench Tools / Features
            if ((showAll || filter.contains("Workbench Tools")) && d.featureUsageCounts != null && !d.featureUsageCounts.isEmpty()) {
                populateSortedMapRows("🛠️ Workbench Tool", d.featureUsageCounts);
            }

            // 2. Hot Queried Tables
            if ((showAll || filter.contains("Hot Queried Tables")) && d.queriedTableCounts != null && !d.queriedTableCounts.isEmpty()) {
                populateSortedMapRows("🔥 Hot Table", d.queriedTableCounts);
            }

            // 3. SQL Workload
            if ((showAll || filter.contains("SQL Workload")) && totalQ > 0) {
                addBreakdownRow("⚡ SQL Workload", "SELECT / DQL Queries", d.selectQueries, totalQ);
                addBreakdownRow("⚡ SQL Workload", "DML (INSERT / UPDATE / DELETE)", d.dmlQueries, totalQ);
                addBreakdownRow("⚡ SQL Workload", "DDL (CREATE / ALTER / DROP)", d.ddlQueries, totalQ);
                addBreakdownRow("⚡ SQL Workload", "EXPLAIN Execution Plans", d.explainQueries, totalQ);
                addBreakdownRow("⚡ SQL Workload", "Slow Queries (> 500 ms)", d.slowQueriesCount, totalQ);
                if (d.otherQueries > 0) {
                    addBreakdownRow("⚡ SQL Workload", "Other SQL Statements", d.otherQueries, totalQ);
                }
            }

            // 4. Daily Activity (Last 7 Days)
            if (showAll || filter.contains("Daily Activity")) {
                long maxDayQ = Math.max(1L, last7Q);
                LocalDate today = LocalDate.now();
                for (int i = 0; i < 7; i++) {
                    LocalDate day = today.minusDays(i);
                    String key = day.format(DateTimeFormatter.ISO_LOCAL_DATE);
                    long cnt = d.dailyQueryCounts.getOrDefault(key, 0L);
                    if (cnt > 0 || !showAll) {
                        String dayLabel = (i == 0 ? "Today (" + key + ")" : (i == 1 ? "Yesterday (" + key + ")" : key));
                        int share = (int) Math.round((cnt * 100.0) / maxDayQ);
                        usageBreakdownTableModel.addRow(new Object[]{
                                "📅 Daily Activity",
                                dayLabel,
                                String.format("%,d queries", cnt),
                                share
                        });
                    }
                }
            }

            // 5. Safety, Governance & Data Movement
            if (showAll || filter.contains("Safety & Data Movement")) {
                long govTotal = Math.max(1L, d.destructiveQueriesIntercepted + d.linterIssuesDetected + d.linterFixesApplied
                        + d.piiRowsMasked + d.rowsImported + d.mockRowsGenerated + d.dualDbComparisonsRun + d.ormModelsGenerated);
                addBreakdownRow("🛡️ Governance & Ops", "Unfiltered DML Guarded / Dry-Run", d.destructiveQueriesIntercepted, govTotal);
                addBreakdownRow("🛡️ Governance & Ops", "Static Linter Issues Detected", d.linterIssuesDetected, govTotal);
                addBreakdownRow("🛡️ Governance & Ops", "Static Linter Auto-Fixes Applied", d.linterFixesApplied, govTotal);
                addBreakdownRow("🛡️ Governance & Ops", "PII Rows Masked & Anonymized", d.piiRowsMasked, govTotal);
                addBreakdownRow("🛡️ Governance & Ops", "Schema Drift Snapshots & Migrations", d.schemaSnapshotsAndMigrations, govTotal);
                addBreakdownRow("🔄 Data & CodeGen", "CSV / Excel Rows Imported", d.rowsImported, govTotal);
                addBreakdownRow("🔄 Data & CodeGen", "Synthetic Mock Rows Generated", d.mockRowsGenerated, govTotal);
                addBreakdownRow("🔄 Data & CodeGen", "Dual DB Comparisons Executed", d.dualDbComparisonsRun, govTotal);
                addBreakdownRow("🔄 Data & CodeGen", "ORM Entity Models Generated", d.ormModelsGenerated, govTotal);
            }

            // 6. Database Engines & Column Data Types
            if (showAll || filter.contains("DB Engines & Column Types")) {
                if (d.engineConnections != null && !d.engineConnections.isEmpty()) {
                    populateSortedMapRows("🗄️ DB Engine", d.engineConnections);
                }
                int totalSchemaCols = lastTextCols + lastNumericCols + lastDateCols + lastBoolCols + lastOtherCols;
                if (totalSchemaCols > 0) {
                    addBreakdownRow("🗄️ Schema Col Type", "Text / VARCHAR / CHAR", lastTextCols, totalSchemaCols);
                    addBreakdownRow("🗄️ Schema Col Type", "Numeric / INT / DECIMAL", lastNumericCols, totalSchemaCols);
                    addBreakdownRow("🗄️ Schema Col Type", "Date / Time / Timestamp", lastDateCols, totalSchemaCols);
                    addBreakdownRow("🗄️ Schema Col Type", "Boolean / Bit", lastBoolCols, totalSchemaCols);
                    addBreakdownRow("🗄️ Schema Col Type", "JSON / Binary / Other", lastOtherCols, totalSchemaCols);
                }
            }
        }
    }

    private void populateSortedMapRows(String category, Map<String, Long> map) {
        long total = 0L;
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(map.entrySet());
        sorted.sort((a, b) -> Long.compare(
                b.getValue() != null ? b.getValue() : 0L,
                a.getValue() != null ? a.getValue() : 0L));
        for (Map.Entry<String, Long> e : sorted) {
            if (e.getValue() != null && e.getValue() > 0) {
                total += e.getValue();
            }
        }
        for (Map.Entry<String, Long> e : sorted) {
            long count = e.getValue() != null ? e.getValue() : 0L;
            if (count <= 0) continue;
            int share = total > 0 ? (int) Math.round((count * 100.0) / total) : 0;
            usageBreakdownTableModel.addRow(new Object[]{
                    category,
                    e.getKey(),
                    String.format("%,d", count),
                    share
            });
        }
    }

    private void addBreakdownRow(String category, String label, long count, long denominator) {
        if (count <= 0) return;
        int pct = denominator > 0 ? (int) Math.min(100L, Math.round((count * 100.0) / denominator)) : 0;
        usageBreakdownTableModel.addRow(new Object[]{
                category,
                label,
                String.format("%,d", count),
                pct
        });
    }

    private final java.util.concurrent.atomic.AtomicBoolean isRefreshing = new java.util.concurrent.atomic.AtomicBoolean(false);

    public void refresh() {
        refreshUsageStatistics();

        if (dbManager == null || dbManager.connection == null) {
            statusPillLabel.setText("● Disconnected");
            statusPillLabel.setForeground(Color.GRAY);
            lastRefreshedLabel.setText("Usage Updated " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
            return;
        }
        if (!isRefreshing.compareAndSet(false, true)) {
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

                dbManager.preloadAllColumns();
                dbManager.preloadAllForeignKeys();

                List<String> tables = dbManager.getTableNames();
                int tableCount = tables.size();

                // Compute storage and table sizes in a SINGLE batch query instead of N*3 queries
                List<TableStorageInfo> storageList = new ArrayList<>();
                long totalCatalogBytes = 0;
                java.util.Map<String, long[]> statsMap = new java.util.HashMap<>();
                int batchIndexCount = 0;

                if (dbManager.isPostgres()) {
                    try (Statement s = dbManager.connection.createStatement()) {
                        try (ResultSet rs = s.executeQuery(
                                "SELECT CASE WHEN schemaname = 'public' THEN relname ELSE schemaname || '.' || relname END, " +
                                "COALESCE(n_live_tup, 0), COALESCE(pg_total_relation_size(relid), 0) " +
                                "FROM pg_stat_user_tables")) {
                            while (rs.next()) {
                                String tName = rs.getString(1);
                                long rCount = Math.max(0, rs.getLong(2));
                                long bSize = Math.max(0, rs.getLong(3));
                                if (tName != null) {
                                    statsMap.put(tName, new long[]{rCount, bSize});
                                }
                            }
                        } catch (Exception ignored) {}
                        try (ResultSet rs = s.executeQuery(
                                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname NOT IN ('pg_catalog', 'information_schema')")) {
                            if (rs.next()) batchIndexCount = rs.getInt(1);
                        } catch (Exception ignored) {}
                    } catch (Exception ignored) {}
                } else if (dbManager.isMySQL()) {
                    try (Statement s = dbManager.connection.createStatement()) {
                        try (ResultSet rs = s.executeQuery(
                                "SELECT table_name, COALESCE(table_rows, 0), COALESCE(data_length + index_length, 0) " +
                                "FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                            while (rs.next()) {
                                String tName = rs.getString(1);
                                long rCount = Math.max(0, rs.getLong(2));
                                long bSize = Math.max(0, rs.getLong(3));
                                if (tName != null) {
                                    statsMap.put(tName, new long[]{rCount, bSize});
                                }
                            }
                        } catch (Exception ignored) {}
                    } catch (Exception ignored) {}
                } else if (tables.size() <= 20) {
                    // Local embedded DB (SQLite / H2 Sandbox) with few tables
                    for (String t : tables) {
                        try (Statement s = dbManager.connection.createStatement();
                             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + dbManager.quoteTableName(t))) {
                            if (rs.next()) {
                                long r = Math.max(0, rs.getLong(1));
                                statsMap.put(t, new long[]{r, r * 128L});
                            }
                        } catch (Exception ignored) {}
                    }
                }

                int populatedCount = 0;
                int emptyCount = 0;
                for (String t : tables) {
                    long[] st = statsMap.get(t);
                    long rows = st != null ? st[0] : 0;
                    long bytes = st != null ? st[1] : 0;
                    if (rows > 0) populatedCount++;
                    else emptyCount++;
                    if (bytes == 0 && rows > 0) {
                        bytes = rows * 128L;
                    }
                    totalCatalogBytes += bytes;
                    storageList.add(new TableStorageInfo(t, rows, bytes));
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

                // Count columns, foreign keys, orphan tables, missing PKs, and column type distribution
                int totalCols = 0;
                int totalFk = 0;
                int missingPkCount = 0;
                Set<String> referencedTables = new HashSet<>();
                Set<String> tablesWithOutgoingFk = new HashSet<>();

                for (String t : tables) {
                    totalCols += dbManager.getColumnNames(t).size();
                    Map<String, String[]> fks = dbManager.getCachedForeignKeys(t);
                    totalFk += fks.size();
                    if (!fks.isEmpty()) {
                        tablesWithOutgoingFk.add(t.toLowerCase(Locale.ROOT));
                        for (String[] target : fks.values()) {
                            if (target != null && target.length > 0 && target[0] != null) {
                                referencedTables.add(target[0].toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                    if (tables.size() <= 40) {
                        try {
                            if (dbManager.getPrimaryKeys(t).isEmpty()) {
                                missingPkCount++;
                            }
                        } catch (Exception ignored) {}
                    }
                }

                int orphanCount = 0;
                for (String t : tables) {
                    String lower = t.toLowerCase(Locale.ROOT);
                    String bare = lower.contains(".") ? lower.substring(lower.indexOf('.') + 1) : lower;
                    if (!tablesWithOutgoingFk.contains(lower)
                            && !referencedTables.contains(lower)
                            && !referencedTables.contains(bare)) {
                        orphanCount++;
                    }
                }

                // Column Data-Type Distribution from DatabaseMetaData in one pass
                int textCols = 0, numCols = 0, dateCols = 0, boolCols = 0, otherCols = 0;
                Set<String> validTableSet = new HashSet<>();
                for (String t : tables) {
                    validTableSet.add(t.toLowerCase(Locale.ROOT));
                    if (t.contains(".")) {
                        validTableSet.add(t.substring(t.indexOf('.') + 1).toLowerCase(Locale.ROOT));
                    }
                }
                try (ResultSet rs = meta.getColumns(null, null, "%", "%")) {
                    while (rs.next()) {
                        String tblName = rs.getString("TABLE_NAME");
                        if (tblName == null || !validTableSet.contains(tblName.toLowerCase(Locale.ROOT))) {
                            continue;
                        }
                        String typeName = rs.getString("TYPE_NAME");
                        String u = typeName != null ? typeName.toUpperCase(Locale.ROOT) : "";
                        if (u.contains("CHAR") || u.contains("TEXT") || u.contains("CLOB") || u.contains("STRING")) {
                            textCols++;
                        } else if (u.contains("INT") || u.contains("NUMERIC") || u.contains("DECIMAL") || u.contains("FLOAT")
                                || u.contains("DOUBLE") || u.contains("REAL") || u.contains("SERIAL") || u.contains("NUMBER") || u.contains("MONEY")) {
                            numCols++;
                        } else if (u.contains("DATE") || u.contains("TIME") || u.contains("YEAR") || u.contains("INTERVAL")) {
                            dateCols++;
                        } else if (u.contains("BOOL") || u.equals("BIT")) {
                            boolCols++;
                        } else {
                            otherCols++;
                        }
                    }
                } catch (Exception ignored) {}

                lastTextCols = textCols;
                lastNumericCols = numCols;
                lastDateCols = dateCols;
                lastBoolCols = boolCols;
                lastOtherCols = otherCols;
                lastMissingPkTables = missingPkCount;
                lastOrphanTables = orphanCount;
                lastPopulatedTables = populatedCount;
                lastEmptyTables = emptyCount;

                int totalIdx = batchIndexCount > 0 ? batchIndexCount : Math.max(tableCount, totalFk);

                final int finalCols = totalCols;
                final int finalIdx = totalIdx;
                final int finalFk = totalFk;
                final int finalMissingPk = missingPkCount;
                final int finalOrphan = orphanCount;
                final int finalPopulated = populatedCount;
                final int finalEmpty = emptyCount;
                final int fText = textCols, fNum = numCols, fDate = dateCols, fBool = boolCols, fOther = otherCols;
                final long finalLatency = latency;

                SwingUtilities.invokeLater(() -> {
                    headerProgress.setVisible(false);
                    statusPillLabel.setText("● Connected: " + productName);
                    statusPillLabel.setForeground(new Color(16, 185, 129));

                    connHostUserLabel.setText("User: " + (user != null ? user : "default") + " | URL: " + truncate(url, 26));
                    connLatencyLabel.setText("Ping Latency: " + finalLatency + " ms");
                    connAutoCommitLabel.setText("Auto-Commit: " + (autoCommit ? "ON (Safe)" : "MANUAL (Active Tx)"));

                    engineNameVersionLabel.setText("Engine: " + productName + " (" + truncate(productVersion, 16) + ")");

                    catalogTablesViewsLabel.setText(String.format("%d Tables (%d Populated / %d Empty)",
                            tableCount, finalPopulated, finalEmpty));
                    catalogColsIndexesLabel.setText(finalCols + "+ Columns | " + finalIdx + "+ Indexes");

                    int healthScore = 98;
                    if (finalLatency > 200) healthScore -= 10;
                    if (finalFk > 0 && finalIdx < finalFk) healthScore -= 12;
                    if (finalMissingPk > 0) healthScore -= Math.min(20, finalMissingPk * 5);
                    healthScoreLabel.setText("Score: " + healthScore + " / 100 " + (healthScore > 85 ? "EXCELLENT" : "GOOD"));
                    healthScoreLabel.setForeground(healthScore > 85 ? new Color(16, 185, 129) : new Color(245, 158, 11));
                    healthIssuesLabel.setText(String.format("Missing PKs: %d | Orphan Tbls: %d | FKs: %d",
                            finalMissingPk, finalOrphan, finalFk));

                    schemaTypeTextNumLabel.setText(String.format("Text/VARCHAR: %,d | Numeric: %,d", fText, fNum));
                    schemaTypeDateBoolLabel.setText(String.format("Date/Time: %,d | Boolean: %,d", fDate, fBool));
                    schemaTypeJsonOtherLabel.setText(String.format("JSON / Binary / Other: %,d", fOther));

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

                    refreshUsageStatistics();
                    lastRefreshedLabel.setText("Refreshed " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    headerProgress.setVisible(false);
                    statusPillLabel.setText("● Telemetry Error");
                    statusPillLabel.setForeground(Color.RED);
                });
            } finally {
                isRefreshing.set(false);
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
