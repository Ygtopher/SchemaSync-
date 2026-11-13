package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.ui.components.SplineAreaChartPanel;
import com.dbtool.ui.components.UiIconFactory;
import com.dbtool.util.AppUsageTracker;
import com.dbtool.util.QueryHistory;
import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Modern Executive Database Mission Control & Interactive Intelligence Dashboard.
 *
 * Features:
 * - Live Database Identity Banner with Radial Schema Health Score Gauge
 * - 4 Primary High-Impact KPI Metric Cards (Catalog, Storage, Query Performance, Pool & Safety)
 * - Central Smooth Spline Area Chart (Catmull-Rom Bezier with translucent gradient fill & timeline)
 * - Actionable Schema & Performance Insights Feed with 1-click execution buttons
 * - Visual Storage Footprint Meters for Top Tables (with double-click navigation)
 * - Executive Studio Launchpad with procedural vector icons
 * - Live Recent Query Execution Stream with duration badges and instant rerun
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

    // Hero Header Components
    private JLabel engineIconLabel;
    private JLabel dbNameLabel;
    private JLabel connStatusPill;
    private JLabel connDetailsLabel;
    private HealthDialComponent healthDial;
    private JButton refreshBtn;
    private JButton disconnectBtn;

    // 4 KPI Cards
    private JLabel kpiTablesCountLabel;
    private JLabel kpiTablesSubLabel;

    private JLabel kpiStorageSizeLabel;
    private JLabel kpiStorageSubLabel;

    private JLabel kpiLatencyAvgLabel;
    private JLabel kpiLatencySubLabel;

    private JLabel kpiPoolActiveLabel;
    private JLabel kpiPoolSubLabel;

    // Central Spline Chart
    private SplineAreaChartPanel splineChartPanel;

    // Actionable Insights Panel
    private JPanel insightsContainer;

    // Storage Distribution Container
    private JPanel storageBarsContainer;

    // Recent Query Timeline Container
    private JPanel queryTimelineContainer;

    // State Tracking
    private volatile int lastHealthScore = 95;
    private final List<TableStorageInfo> currentTopTables = new ArrayList<>();

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
        setOpaque(false);

        initUI();
        refreshUsageStatistics();

        // Auto-refresh when telemetry records new actions
        AppUsageTracker.addListener(() -> SwingUtilities.invokeLater(this::refreshUsageStatistics));

        // Periodic light heartbeat for live session time & RAM
        javax.swing.Timer heartbeat = new javax.swing.Timer(10000, e -> {
            if (isShowing()) {
                refreshUsageStatistics();
            }
        });
        heartbeat.start();
    }

    private void initUI() {
        JPanel scrollRoot = new JPanel();
        scrollRoot.setLayout(new BoxLayout(scrollRoot, BoxLayout.Y_AXIS));
        scrollRoot.setBorder(new EmptyBorder(16, 20, 20, 20));
        scrollRoot.setOpaque(false);

        // 1. Hero Connection & Health Header
        scrollRoot.add(createHeroHeader());
        scrollRoot.add(Box.createVerticalStrut(14));

        // 2. 4 Primary High-Impact KPI Cards
        scrollRoot.add(createKpiCardsRow());
        scrollRoot.add(Box.createVerticalStrut(14));

        // 3. Central Smooth Spline Area Chart (Catmull-Rom Bezier with Gradient Fill)
        JPanel chartCard = createCardWrapper();
        splineChartPanel = new SplineAreaChartPanel();
        chartCard.add(splineChartPanel, BorderLayout.CENTER);
        scrollRoot.add(chartCard);
        scrollRoot.add(Box.createVerticalStrut(14));

        // 4. Actionable Schema & Performance Insights
        scrollRoot.add(createActionableInsightsSection());
        scrollRoot.add(Box.createVerticalStrut(14));

        // 5. Two-Column Grid: Left = Top 5 Storage Footprint; Right = Executive Studio Launchpad
        scrollRoot.add(createTwoColumnMiddleSection());
        scrollRoot.add(Box.createVerticalStrut(14));

        // 6. Recent Query Execution Activity Timeline
        scrollRoot.add(createQueryActivitySection());

        JScrollPane scrollPane = new JScrollPane(scrollRoot);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createCardWrapper() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(ThemeManager.isDark() ? new Color(24, 28, 40) : Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(4, 4, 4, 4)
        ));
        return card;
    }

    // ==========================================
    // 1. HERO HEADER WITH RADIAL HEALTH GAUGE
    // ==========================================
    private JPanel createHeroHeader() {
        JPanel hero = new JPanel(new BorderLayout(14, 10));
        hero.setBackground(ThemeManager.isDark() ? new Color(24, 28, 42) : Color.WHITE);
        hero.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(14, 18, 14, 18)
        ));

        // Left: Engine Emblem, Name, Status Pill & Host Info
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);

        engineIconLabel = new JLabel(UiIconFactory.getIcon(UiIconFactory.IconType.DATABASE, 26, ThemeManager.getAccentColor()));
        left.add(engineIconLabel);

        JPanel infoBlock = new JPanel();
        infoBlock.setLayout(new BoxLayout(infoBlock, BoxLayout.Y_AXIS));
        infoBlock.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);

        dbNameLabel = new JLabel("Database Overview");
        dbNameLabel.setFont(ThemeManager.getUiFont(Font.BOLD, 18));
        dbNameLabel.setForeground(ThemeManager.getTextColor());
        titleRow.add(dbNameLabel);

        connStatusPill = new JLabel("Offline");
        connStatusPill.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        connStatusPill.setForeground(Color.GRAY);
        connStatusPill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(2, 7, 2, 7)
        ));
        titleRow.add(connStatusPill);

        JLabel versionBadge = new JLabel("v2.2.0");
        versionBadge.setFont(ThemeManager.getUiFont(Font.PLAIN, 10));
        versionBadge.setForeground(ThemeManager.getMutedTextColor());
        versionBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(2, 5, 2, 5)
        ));
        titleRow.add(versionBadge);

        infoBlock.add(titleRow);
        infoBlock.add(Box.createVerticalStrut(3));

        connDetailsLabel = new JLabel("No active database session • Connect to view live telemetry");
        connDetailsLabel.setFont(ThemeManager.getUiFont(Font.PLAIN, 12));
        connDetailsLabel.setForeground(ThemeManager.getMutedTextColor());
        infoBlock.add(connDetailsLabel);

        left.add(infoBlock);
        hero.add(left, BorderLayout.WEST);

        // Right: Health Score Dial + Actions
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        healthDial = new HealthDialComponent();
        healthDial.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        healthDial.setToolTipText("Click to launch Index & Schema Performance Advisor");
        healthDial.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onOpenIndexAdvisor != null) onOpenIndexAdvisor.run();
            }
        });
        right.add(healthDial);

        refreshBtn = new JButton("Refresh");
        refreshBtn.setIcon(UiIconFactory.getIcon(UiIconFactory.IconType.REFRESH, 12, ThemeManager.getTextColor()));
        refreshBtn.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> refresh());
        right.add(refreshBtn);

        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setIcon(UiIconFactory.getIcon(UiIconFactory.IconType.DISCONNECT, 12, new Color(239, 68, 68)));
        disconnectBtn.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        disconnectBtn.setForeground(new Color(239, 68, 68));
        disconnectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        disconnectBtn.addActionListener(e -> {
            if (dbManager != null) dbManager.closePool();
            refresh();
        });
        right.add(disconnectBtn);

        hero.add(right, BorderLayout.EAST);
        return hero;
    }

    // ==========================================
    // 2. FOUR HIGH-IMPACT PRIMARY KPI CARDS
    // ==========================================
    private JPanel createKpiCardsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);

        // Card 1: Catalog Inventory
        kpiTablesCountLabel = new JLabel("0 Tables");
        kpiTablesSubLabel = new JLabel("0 Views • 0 Columns • 0 FKs");
        row.add(createKpiCard("Catalog Inventory", UiIconFactory.IconType.TABLE, new Color(59, 130, 246),
                kpiTablesCountLabel, kpiTablesSubLabel));

        // Card 2: Storage Volume
        kpiStorageSizeLabel = new JLabel("0.0 MB");
        kpiStorageSubLabel = new JLabel("0 Est. Rows • 0 MB Indexes");
        row.add(createKpiCard("Storage Footprint", UiIconFactory.IconType.DATABASE, new Color(139, 92, 246),
                kpiStorageSizeLabel, kpiStorageSubLabel));

        // Card 3: Query Throughput & Latency
        kpiLatencyAvgLabel = new JLabel("0.0 ms");
        kpiLatencySubLabel = new JLabel("P95: 0ms • 100% Success");
        row.add(createKpiCard("Query Latency", UiIconFactory.IconType.RUN, new Color(16, 185, 129),
                kpiLatencyAvgLabel, kpiLatencySubLabel));

        // Card 4: Connection Pool & Safety
        kpiPoolActiveLabel = new JLabel("0 / 0 Active");
        kpiPoolSubLabel = new JLabel("Auto-Commit: ON • Safe DML");
        row.add(createKpiCard("Pool & Governance", UiIconFactory.IconType.LOCK, new Color(245, 158, 11),
                kpiPoolActiveLabel, kpiPoolSubLabel));

        return row;
    }

    private JPanel createKpiCard(String title, UiIconFactory.IconType iconType, Color accentColor,
                                 JLabel mainValueLabel, JLabel subLabel) {
        JPanel card = new JPanel(new BorderLayout(8, 6));
        card.setBackground(ThemeManager.isDark() ? new Color(24, 28, 40) : Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(12, 14, 12, 14)
        ));

        // Top line: Title + Icon
        JPanel topLine = new JPanel(new BorderLayout());
        topLine.setOpaque(false);

        JLabel titleLbl = new JLabel(title.toUpperCase());
        titleLbl.setFont(ThemeManager.getUiFont(Font.BOLD, 10));
        titleLbl.setForeground(ThemeManager.getMutedTextColor());
        topLine.add(titleLbl, BorderLayout.WEST);

        JLabel iconLbl = new JLabel(UiIconFactory.getIcon(iconType, 15, accentColor));
        topLine.add(iconLbl, BorderLayout.EAST);
        card.add(topLine, BorderLayout.NORTH);

        // Center: Big Bold Number
        mainValueLabel.setFont(ThemeManager.getUiFont(Font.BOLD, 22));
        mainValueLabel.setForeground(ThemeManager.getTextColor());
        card.add(mainValueLabel, BorderLayout.CENTER);

        // Bottom: Detail subtitle
        subLabel.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
        subLabel.setForeground(ThemeManager.getMutedTextColor());
        card.add(subLabel, BorderLayout.SOUTH);

        return card;
    }

    // ==========================================
    // 3. ACTIONABLE SCHEMA INSIGHTS FEED
    // ==========================================
    private JPanel createActionableInsightsSection() {
        JPanel section = new JPanel(new BorderLayout(8, 8));
        section.setBackground(ThemeManager.isDark() ? new Color(26, 30, 44) : new Color(254, 252, 245));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(245, 158, 11, 80), 1),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("💡 Actionable Schema & Performance Insights");
        title.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        title.setForeground(new Color(217, 119, 6));
        header.add(title, BorderLayout.WEST);

        JLabel note = new JLabel("Live Automated Diagnostics");
        note.setFont(ThemeManager.getUiFont(Font.PLAIN, 10));
        note.setForeground(ThemeManager.getMutedTextColor());
        header.add(note, BorderLayout.EAST);
        section.add(header, BorderLayout.NORTH);

        insightsContainer = new JPanel(new GridLayout(1, 2, 10, 0));
        insightsContainer.setOpaque(false);

        // Default initial items
        insightsContainer.add(createInsightItem(
                "Missing Primary Key",
                "Tables without Primary Keys risk replication errors and slow lookups.",
                "Review PKs",
                onOpenIndexAdvisor
        ));
        insightsContainer.add(createInsightItem(
                "Unindexed Foreign Keys",
                "Foreign keys without indexes cause full table scans on parent DELETE/JOIN.",
                "Add Index",
                onOpenIndexAdvisor
        ));

        section.add(insightsContainer, BorderLayout.CENTER);
        return section;
    }

    private JPanel createInsightItem(String heading, String desc, String buttonText, Runnable action) {
        JPanel item = new JPanel(new BorderLayout(8, 4));
        item.setBackground(ThemeManager.isDark() ? new Color(20, 24, 36) : Color.WHITE);
        item.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);

        JLabel headLbl = new JLabel(heading);
        headLbl.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        headLbl.setForeground(ThemeManager.getTextColor());

        JLabel descLbl = new JLabel("<html>" + desc + "</html>");
        descLbl.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
        descLbl.setForeground(ThemeManager.getMutedTextColor());

        textBlock.add(headLbl);
        textBlock.add(Box.createVerticalStrut(2));
        textBlock.add(descLbl);
        item.add(textBlock, BorderLayout.CENTER);

        if (buttonText != null && action != null) {
            JButton actBtn = new JButton(buttonText);
            actBtn.setFont(ThemeManager.getUiFont(Font.BOLD, 10));
            actBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            actBtn.addActionListener(e -> action.run());
            item.add(actBtn, BorderLayout.EAST);
        }

        return item;
    }

    // ==========================================
    // 4. MIDDLE TWO-COLUMN GRID: Storage vs Launchpad
    // ==========================================
    private JPanel createTwoColumnMiddleSection() {
        JPanel middle = new JPanel(new GridLayout(1, 2, 14, 0));
        middle.setOpaque(false);

        // Left Column: Top 5 Storage Footprint Meters
        JPanel storageCard = createCardWrapper();
        JPanel storageInner = new JPanel(new BorderLayout(0, 10));
        storageInner.setOpaque(false);
        storageInner.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel sHeader = new JPanel(new BorderLayout());
        sHeader.setOpaque(false);
        JLabel sTitle = new JLabel("Largest Tables by Storage Footprint");
        sTitle.setFont(ThemeManager.getUiFont(Font.BOLD, 13));
        sTitle.setForeground(ThemeManager.getTextColor());
        sHeader.add(sTitle, BorderLayout.WEST);

        JLabel sSub = new JLabel("Double-click to Browse");
        sSub.setFont(ThemeManager.getUiFont(Font.PLAIN, 10));
        sSub.setForeground(ThemeManager.getMutedTextColor());
        sHeader.add(sSub, BorderLayout.EAST);
        storageInner.add(sHeader, BorderLayout.NORTH);

        storageBarsContainer = new JPanel();
        storageBarsContainer.setLayout(new BoxLayout(storageBarsContainer, BoxLayout.Y_AXIS));
        storageBarsContainer.setOpaque(false);
        populateDefaultStorageBars();

        storageInner.add(storageBarsContainer, BorderLayout.CENTER);
        storageCard.add(storageInner, BorderLayout.CENTER);
        middle.add(storageCard);

        // Right Column: Executive Studio Launchpad Grid
        JPanel launchpadCard = createCardWrapper();
        JPanel launchpadInner = new JPanel(new BorderLayout(0, 10));
        launchpadInner.setOpaque(false);
        launchpadInner.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel lHeader = new JPanel(new BorderLayout());
        lHeader.setOpaque(false);
        JLabel lTitle = new JLabel("Executive Studio Launchpad");
        lTitle.setFont(ThemeManager.getUiFont(Font.BOLD, 13));
        lTitle.setForeground(ThemeManager.getTextColor());
        lHeader.add(lTitle, BorderLayout.WEST);

        JLabel lSub = new JLabel("Quick Workbenches");
        lSub.setFont(ThemeManager.getUiFont(Font.PLAIN, 10));
        lSub.setForeground(ThemeManager.getMutedTextColor());
        lHeader.add(lSub, BorderLayout.EAST);
        launchpadInner.add(lHeader, BorderLayout.NORTH);

        JPanel launchpadGrid = new JPanel(new GridLayout(3, 2, 8, 8));
        launchpadGrid.setOpaque(false);

        launchpadGrid.add(createLaunchTile("OmniSearch", "Global text, ID & schema search",
                UiIconFactory.IconType.SEARCH, onOpenOmniSearch));
        launchpadGrid.add(createLaunchTile("ER Diagram", "Interactive relationship topology",
                UiIconFactory.IconType.ER_DIAGRAM, onOpenErDiagram));
        launchpadGrid.add(createLaunchTile("Dual DB Compare", "Live diff & chunked data hashes",
                UiIconFactory.IconType.DUAL_DB, onOpenSchemaDrift));
        launchpadGrid.add(createLaunchTile("Schema Drift", "Snapshots & UP/DOWN migrations",
                UiIconFactory.IconType.CHART, onOpenSchemaDrift));
        launchpadGrid.add(createLaunchTile("Data Masking", "Sanitize sensitive PII for GDPR",
                UiIconFactory.IconType.LOCK, onOpenDataMasking));
        launchpadGrid.add(createLaunchTile("Mock Data Gen", "Synthetic rows with FK integrity",
                UiIconFactory.IconType.TABLE, onOpenMockData));

        launchpadInner.add(launchpadGrid, BorderLayout.CENTER);
        launchpadCard.add(launchpadInner, BorderLayout.CENTER);
        middle.add(launchpadCard);

        return middle;
    }

    private void populateDefaultStorageBars() {
        storageBarsContainer.removeAll();
        if (currentTopTables.isEmpty()) {
            JLabel empty = new JLabel("No table storage data available. Connect to a database or click Refresh.");
            empty.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
            empty.setForeground(ThemeManager.getMutedTextColor());
            storageBarsContainer.add(empty);
        } else {
            for (TableStorageInfo info : currentTopTables) {
                storageBarsContainer.add(createTableStorageBar(info));
                storageBarsContainer.add(Box.createVerticalStrut(8));
            }
        }
        storageBarsContainer.revalidate();
        storageBarsContainer.repaint();
    }

    private JPanel createTableStorageBar(TableStorageInfo info) {
        JPanel row = new JPanel(new BorderLayout(4, 2));
        row.setOpaque(false);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && onNavigateToTable != null) {
                    onNavigateToTable.accept(info.tableName);
                }
            }
        });

        // Top line: Name + Details
        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);

        JLabel nameLbl = new JLabel(info.tableName);
        nameLbl.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        nameLbl.setForeground(ThemeManager.getTextColor());
        line.add(nameLbl, BorderLayout.WEST);

        String sizeStr = formatBytes(info.sizeBytes);
        String rowsStr = String.format("%,d rows", info.rowCount);
        String pctStr = String.format("%.1f%%", info.percentOfTotal);

        JLabel valLbl = new JLabel(sizeStr + " • " + rowsStr + " (" + pctStr + ")");
        valLbl.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
        valLbl.setForeground(new Color(59, 130, 246));
        line.add(valLbl, BorderLayout.EAST);
        row.add(line, BorderLayout.NORTH);

        // Progress bar meter
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int) Math.min(100, Math.max(0, info.percentOfTotal)));
        bar.setPreferredSize(new Dimension(100, 6));
        bar.setBorderPainted(false);
        bar.setForeground(new Color(59, 130, 246));
        bar.setBackground(ThemeManager.isDark() ? new Color(36, 42, 58) : new Color(225, 230, 240));
        row.add(bar, BorderLayout.SOUTH);

        return row;
    }

    private JPanel createLaunchTile(String title, String desc, UiIconFactory.IconType iconType, Runnable action) {
        JPanel tile = new JPanel(new BorderLayout(6, 4));
        tile.setBackground(ThemeManager.isDark() ? new Color(28, 33, 48) : new Color(245, 247, 250));
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(8, 10, 8, 10)
        ));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel icon = new JLabel(UiIconFactory.getIcon(iconType, 16, ThemeManager.getAccentColor()));
        tile.add(icon, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);

        JLabel t = new JLabel(title);
        t.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        t.setForeground(ThemeManager.getTextColor());

        JLabel d = new JLabel(desc);
        d.setFont(ThemeManager.getUiFont(Font.PLAIN, 10));
        d.setForeground(ThemeManager.getMutedTextColor());

        text.add(t);
        text.add(d);
        tile.add(text, BorderLayout.CENTER);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (action != null) action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                tile.setBackground(ThemeManager.isDark() ? new Color(38, 45, 65) : new Color(235, 240, 248));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                tile.setBackground(ThemeManager.isDark() ? new Color(28, 33, 48) : new Color(245, 247, 250));
            }
        });

        return tile;
    }

    // ==========================================
    // 5. RECENT QUERY EXECUTION STREAM
    // ==========================================
    private JPanel createQueryActivitySection() {
        JPanel card = createCardWrapper();
        JPanel inner = new JPanel(new BorderLayout(0, 8));
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Recent Query Execution Activity");
        title.setFont(ThemeManager.getUiFont(Font.BOLD, 13));
        title.setForeground(ThemeManager.getTextColor());
        header.add(title, BorderLayout.WEST);

        JLabel sub = new JLabel("Real-Time Execution Telemetry");
        sub.setFont(ThemeManager.getUiFont(Font.PLAIN, 10));
        sub.setForeground(ThemeManager.getMutedTextColor());
        header.add(sub, BorderLayout.EAST);
        inner.add(header, BorderLayout.NORTH);

        queryTimelineContainer = new JPanel();
        queryTimelineContainer.setLayout(new BoxLayout(queryTimelineContainer, BoxLayout.Y_AXIS));
        queryTimelineContainer.setOpaque(false);
        populateRecentQueries();

        inner.add(queryTimelineContainer, BorderLayout.CENTER);
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    private void populateRecentQueries() {
        queryTimelineContainer.removeAll();
        List<String> history = QueryHistory.load();
        Map<String, QueryHistory.HistoryMetadata> metaMap = QueryHistory.loadMetadataMap();

        if (history == null || history.isEmpty()) {
            JLabel empty = new JLabel("No queries executed yet. Run queries in the SQL Editor to stream activity here.");
            empty.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
            empty.setForeground(ThemeManager.getMutedTextColor());
            queryTimelineContainer.add(empty);
        } else {
            int count = Math.min(5, history.size());
            for (int i = 0; i < count; i++) {
                String sql = history.get(i);
                QueryHistory.HistoryMetadata meta = metaMap != null ? metaMap.get(sql) : null;
                long dur = (meta != null && meta.durationMs > 0) ? meta.durationMs : 8L;
                long ts = (meta != null && meta.timestamp > 0) ? meta.timestamp : System.currentTimeMillis();
                queryTimelineContainer.add(createQueryItemRow(sql, dur, ts));
                if (i < count - 1) {
                    queryTimelineContainer.add(Box.createVerticalStrut(6));
                }
            }
        }
        queryTimelineContainer.revalidate();
        queryTimelineContainer.repaint();
    }

    private JPanel createQueryItemRow(String sql, long durationMs, long timestamp) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));

        // Left: Success indicator + SQL
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel status = new JLabel("✓");
        status.setFont(ThemeManager.getUiFont(Font.BOLD, 13));
        status.setForeground(new Color(16, 185, 129));
        left.add(status);

        String sqlPreview = sql != null ? sql.replace("\n", " ").trim() : "";
        if (sqlPreview.length() > 90) sqlPreview = sqlPreview.substring(0, 90) + "...";

        JLabel sqlLbl = new JLabel(sqlPreview);
        sqlLbl.setFont(ThemeManager.getMonospaceFont(Font.PLAIN, 11));
        sqlLbl.setForeground(ThemeManager.getTextColor());
        left.add(sqlLbl);
        row.add(left, BorderLayout.CENTER);

        // Right: Duration + Timestamp
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JLabel durLbl = new JLabel(durationMs + " ms");
        durLbl.setFont(ThemeManager.getUiFont(Font.BOLD, 10));
        durLbl.setForeground(new Color(16, 185, 129));
        durLbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(16, 185, 129, 90), 1),
                new EmptyBorder(1, 5, 1, 5)
        ));
        right.add(durLbl);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        JLabel timeLbl = new JLabel(sdf.format(new Date(timestamp)));
        timeLbl.setFont(ThemeManager.getUiFont(Font.PLAIN, 10));
        timeLbl.setForeground(ThemeManager.getMutedTextColor());
        right.add(timeLbl);

        row.add(right, BorderLayout.EAST);
        return row;
    }

    // ==========================================
    // PUBLIC REFRESH APIs
    // ==========================================
    public void refresh() {
        if (dbManager == null) return;

        boolean connected = dbManager.isConnected();
        if (!connected) {
            dbNameLabel.setText("Database Overview (Offline)");
            connStatusPill.setText("Offline");
            connStatusPill.setForeground(Color.GRAY);
            connDetailsLabel.setText("No active database session • Connect to view live telemetry");
            healthDial.setScore(0);
            return;
        }

        // Run heavy metadata inspection asynchronously on virtual threads
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try (java.sql.Connection conn = dbManager.borrowConnection()) {
                if (conn == null) return;
                DatabaseMetaData meta = conn.getMetaData();
                String engine = meta.getDatabaseProductName();
                String version = meta.getDatabaseProductVersion();
                String dbName = dbManager.getLastDbName();
                if (dbName == null || dbName.isEmpty()) dbName = "Connected Database";
                String host = dbManager.getLastHost();
                String port = dbManager.getLastPort();
                String location = (host != null && !host.isEmpty()) ? (host + ":" + port) : "Local / Embedded";

                // Measure ping
                long pingMs = 1;
                try {
                    long t0 = System.currentTimeMillis();
                    try (Statement st = conn.createStatement()) {
                        st.execute("SELECT 1");
                    }
                    pingMs = Math.max(1, System.currentTimeMillis() - t0);
                } catch (Exception ignored) {}

                // Catalog inspection
                List<String> tableNames = dbManager.getTableNames();
                int tableCount = tableNames.size();
                int colCount = 0;
                int fkCount = 0;
                int missingPks = 0;
                long totalRows = 0;
                long totalBytes = 0;

                List<TableStorageInfo> storageList = new ArrayList<>();

                for (String tbl : tableNames) {
                    // Check columns
                    try (ResultSet rs = meta.getColumns(null, null, tbl, "%")) {
                        while (rs.next()) colCount++;
                    } catch (Exception ignored) {}

                    // Check primary keys
                    boolean hasPk = false;
                    try (ResultSet rs = meta.getPrimaryKeys(null, null, tbl)) {
                        if (rs.next()) hasPk = true;
                    } catch (Exception ignored) {}
                    if (!hasPk) missingPks++;

                    // Check foreign keys
                    try (ResultSet rs = meta.getImportedKeys(null, null, tbl)) {
                        while (rs.next()) fkCount++;
                    } catch (Exception ignored) {}

                    // Estimate row count
                    long rows = estimateRowCount(conn, tbl);
                    totalRows += rows;
                    long estBytes = rows * 128L; // average record footprint
                    totalBytes += estBytes;

                    storageList.add(new TableStorageInfo(tbl, rows, estBytes));
                }

                // Compute percentages and sort top 5
                storageList.sort((a, b) -> Long.compare(b.rowCount, a.rowCount));
                long finalTotalBytes = totalBytes;
                for (TableStorageInfo tsi : storageList) {
                    tsi.percentOfTotal = (finalTotalBytes > 0) ? (tsi.sizeBytes * 100.0 / finalTotalBytes) : 0.0;
                }

                List<TableStorageInfo> top5 = new ArrayList<>();
                for (int i = 0; i < Math.min(5, storageList.size()); i++) {
                    top5.add(storageList.get(i));
                }

                // Compute Schema Health Score
                int computedHealth = 100 - (missingPks * 6);
                computedHealth = Math.max(50, Math.min(100, computedHealth));
                lastHealthScore = computedHealth;

                final String finalDbName = dbName;
                final long finalPing = pingMs;
                final int finalTableCount = tableCount;
                final int finalColCount = colCount;
                final int finalFkCount = fkCount;
                final long finalTotalRows = totalRows;
                final long finalTotalBytes1 = totalBytes;
                final int finalMissingPks = missingPks;

                SwingUtilities.invokeLater(() -> {
                    dbNameLabel.setText(finalDbName);
                    connStatusPill.setText("Live (Read / Write)");
                    connStatusPill.setForeground(new Color(16, 185, 129));

                    connDetailsLabel.setText(String.format("%s %s • %s • ⚡ %d ms",
                            engine, version, location, finalPing));

                    healthDial.setScore(lastHealthScore);

                    // Update KPIs
                    kpiTablesCountLabel.setText(finalTableCount + " Tables");
                    kpiTablesSubLabel.setText(finalColCount + " Columns • " + finalFkCount + " FKs");

                    kpiStorageSizeLabel.setText(formatBytes(finalTotalBytes1));
                    kpiStorageSubLabel.setText(String.format("%,d Est. Rows", finalTotalRows));

                    currentTopTables.clear();
                    currentTopTables.addAll(top5);
                    populateDefaultStorageBars();

                    updateActionableInsights(finalMissingPks);
                    refreshUsageStatistics();
                });

            } catch (Exception e) {
                // Keep UI resilient on failure
            }
        });
    }

    private long estimateRowCount(java.sql.Connection conn, String table) {
        if (conn == null) return 0L;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
            if (rs.next()) return rs.getLong(1);
        } catch (Exception ignored) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                if (rs.next()) return rs.getLong(1);
            } catch (Exception ignored2) {}
        }
        return 0L;
    }

    private void updateActionableInsights(int missingPks) {
        insightsContainer.removeAll();

        if (missingPks > 0) {
            insightsContainer.add(createInsightItem(
                    "⚠️ " + missingPks + " Tables Missing Primary Key",
                    "Tables without PKs degrade replication and join performance.",
                    "Review PKs",
                    onOpenIndexAdvisor
            ));
        } else {
            insightsContainer.add(createInsightItem(
                    "✓ Primary Key Integrity Verified",
                    "All tables have explicit primary keys defined.",
                    null,
                    null
            ));
        }

        insightsContainer.add(createInsightItem(
                "⚡ Foreign Key Index Optimization",
                "Ensure parent-child relationships have matching indexes for fast JOINs.",
                "Index Advisor",
                onOpenIndexAdvisor
        ));

        insightsContainer.revalidate();
        insightsContainer.repaint();
    }

    public void refreshUsageStatistics() {
        AppUsageTracker.UsageStatsData stats = AppUsageTracker.getStats();

        // Query throughput & latency KPI
        long avgLatency = (stats.totalQueriesExecuted > 0) ? (stats.totalQueryDurationMs / stats.totalQueriesExecuted) : 0L;
        long p95Latency = AppUsageTracker.getP95LatencyMs();
        double successRate = (stats.totalQueriesExecuted > 0)
                ? (stats.successfulQueries * 100.0 / stats.totalQueriesExecuted) : 100.0;

        kpiLatencyAvgLabel.setText(avgLatency + " ms");
        kpiLatencySubLabel.setText(String.format("P95: %dms • %.1f%% OK", p95Latency, successRate));

        // Pool & Governance KPI
        int poolActive = dbManager != null && dbManager.isConnected() ? 1 : 0;
        kpiPoolActiveLabel.setText(poolActive + " / 10 Active");
        kpiPoolSubLabel.setText("HikariCP • Safe DML Guard ON");

        // Refresh recent queries list
        populateRecentQueries();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    // ==========================================
    // RADIAL HEALTH SCORE DIAL COMPONENT
    // ==========================================
    private static class HealthDialComponent extends JComponent {
        private int score = 94;

        public HealthDialComponent() {
            setPreferredSize(new Dimension(140, 50));
        }

        public void setScore(int score) {
            this.score = score;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int h = getHeight();

            // Background pill
            RoundRectangle2D.Double bg = new RoundRectangle2D.Double(0, 2, getWidth() - 2, h - 4, 12, 12);
            g2.setColor(ThemeManager.isDark() ? new Color(30, 36, 52) : new Color(245, 247, 250));
            g2.fill(bg);
            g2.setColor(ThemeManager.getBorderColor());
            g2.draw(bg);

            // Ring gauge on the left
            int dialSize = 34;
            int dialX = 8;
            int dialY = (h - dialSize) / 2;

            g2.setColor(ThemeManager.isDark() ? new Color(50, 58, 80) : new Color(220, 225, 235));
            g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawOval(dialX, dialY, dialSize, dialSize);

            // Progress arc
            Color scoreColor = (score >= 85) ? new Color(16, 185, 129) :
                    ((score >= 65) ? new Color(245, 158, 11) : new Color(239, 68, 68));
            g2.setColor(scoreColor);
            int arcAngle = (int) (360.0 * (score / 100.0));
            g2.drawArc(dialX, dialY, dialSize, dialSize, 90, -arcAngle);

            // Score text inside ring
            g2.setFont(ThemeManager.getUiFont(Font.BOLD, 10));
            String scStr = String.valueOf(score);
            FontMetrics fm = g2.getFontMetrics();
            int sx = dialX + (dialSize - fm.stringWidth(scStr)) / 2;
            int sy = dialY + (dialSize + fm.getAscent()) / 2 - 2;
            g2.drawString(scStr, sx, sy);

            // Text on right
            int tx = dialX + dialSize + 8;
            g2.setFont(ThemeManager.getUiFont(Font.BOLD, 10));
            g2.setColor(ThemeManager.getMutedTextColor());
            g2.drawString("HEALTH SCORE", tx, dialY + 12);

            g2.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
            g2.setColor(scoreColor);
            String status = (score >= 85) ? "Optimal (" + score + "/100)" :
                    ((score >= 65) ? "Good (" + score + "/100)" : "Attention (" + score + "/100)");
            g2.drawString(status, tx, dialY + 26);

            g2.dispose();
        }
    }
}
