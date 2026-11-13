package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * Enterprise IDE-grade bottom status bar for SchemaSync (DataGrip / VS Code inspired).
 * Displays real-time database engine, ping latency, transaction mode, active task status,
 * live JVM heap memory with 1-click GC, zoom level, and query log toggle.
 */
public class StatusBar extends JPanel {

    private final JLabel lblEngine = new JLabel("Offline");
    private final JLabel lblLatency = new JLabel("⚡ -- ms");
    private final JLabel lblSchema = new JLabel("Schema: -");
    private final JButton btnAutoCommit = new JButton("Auto-Commit: ON");
    private final JLabel lblStatus = new JLabel("Ready");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton btnRam = new JButton("RAM: -- MB");
    private final JButton btnZoom = new JButton("100%");
    private final JButton btnQueryLog = new JButton("📜 Log: 0");
    private final JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

    private Consumer<Boolean> autoCommitToggleCallback;
    private Runnable queryLogToggleCallback;
    private Runnable zoomChangeCallback;
    private boolean isAutoCommitOn = true;
    private final Timer memoryTimer;

    public StatusBar() {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 55, 72)),
            new EmptyBorder(3, 12, 3, 12)
        ));
        setPreferredSize(new Dimension(800, 28));

        // Left Section: Engine, Latency, Schema, AutoCommit
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);

        stylePillLabel(lblEngine, new Color(100, 116, 139));
        stylePillLabel(lblLatency, new Color(16, 185, 129));
        stylePillLabel(lblSchema, new Color(148, 163, 184));
        stylePillButton(btnAutoCommit, new Color(59, 130, 246));
        btnAutoCommit.setToolTipText("Click to toggle JDBC Auto-Commit mode");
        btnAutoCommit.addActionListener(e -> {
            isAutoCommitOn = !isAutoCommitOn;
            updateAutoCommitVisuals();
            if (autoCommitToggleCallback != null) {
                autoCommitToggleCallback.accept(isAutoCommitOn);
            }
        });

        leftPanel.add(lblEngine);
        leftPanel.add(lblLatency);
        leftPanel.add(lblSchema);
        leftPanel.add(btnAutoCommit);
        add(leftPanel, BorderLayout.WEST);

        // Center Section: Dynamic Status & Progress Bar
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerPanel.setOpaque(false);
        lblStatus.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
        lblStatus.setForeground(ThemeManager.getMutedTextColor());

        progressBar.setPreferredSize(new Dimension(100, 10));
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        centerPanel.add(progressBar);
        centerPanel.add(lblStatus);
        add(centerPanel, BorderLayout.CENTER);

        // Right Section: RAM with 1-click GC, Zoom, Query Log Drawer
        rightPanel.setOpaque(false);

        stylePillButton(btnRam, new Color(139, 92, 246));
        btnRam.setToolTipText("Click to trigger Garbage Collection and optimize memory");
        btnRam.addActionListener(e -> optimizeMemory());

        stylePillButton(btnZoom, new Color(100, 116, 139));
        btnZoom.setToolTipText("Current UI Zoom / Scale (Click to adjust)");
        btnZoom.addActionListener(e -> {
            if (zoomChangeCallback != null) zoomChangeCallback.run();
        });

        stylePillButton(btnQueryLog, new Color(14, 165, 233));
        btnQueryLog.setToolTipText("Toggle Query History & Execution Log drawer");
        btnQueryLog.addActionListener(e -> {
            if (queryLogToggleCallback != null) queryLogToggleCallback.run();
        });

        rightPanel.add(btnRam);
        rightPanel.add(btnZoom);
        rightPanel.add(btnQueryLog);
        add(rightPanel, BorderLayout.EAST);

        // Periodic Memory Poller
        memoryTimer = new Timer(3000, e -> updateRamMetric());
        memoryTimer.start();
        updateRamMetric();

        ThemeManager.addThemeChangeListener(isDark -> applyThemeColors());
        applyThemeColors();
    }

    private void stylePillLabel(JLabel lbl, Color fg) {
        lbl.setFont(ThemeManager.getUiFont(Font.BOLD, 10.5f));
        lbl.setForeground(fg);
    }

    private void stylePillButton(JButton btn, Color fg) {
        btn.setFont(ThemeManager.getUiFont(Font.BOLD, 10.5f));
        btn.setForeground(fg);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(1, 4, 1, 4));
    }

    public void applyThemeColors() {
        boolean dark = ThemeManager.isDarkMode();
        setBackground(dark ? new Color(18, 22, 31) : new Color(241, 245, 249));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, dark ? new Color(38, 48, 64) : new Color(203, 213, 225)),
            new EmptyBorder(3, 12, 3, 12)
        ));
        lblStatus.setForeground(dark ? new Color(203, 213, 225) : new Color(51, 65, 85));
        int zoomPct = Math.round(ThemeManager.getUiScaleFactor() * 100);
        btnZoom.setText("🔍 " + zoomPct + "%");
        repaint();
    }

    public void setDatabaseInfo(String engineName, String host, String dbName) {
        if (engineName == null || engineName.isBlank()) {
            lblEngine.setText("⚪ Offline");
            lblEngine.setForeground(new Color(148, 163, 184));
            lblSchema.setText("Schema: -");
            lblLatency.setText("⚡ -- ms");
        } else {
            lblEngine.setText("🟢 " + engineName + " (" + dbName + ")");
            lblEngine.setForeground(ThemeManager.getSuccessColor());
            lblSchema.setText("Host: " + (host != null && !host.isBlank() ? host : "local"));
        }
    }

    public void setPingLatency(long latencyMs) {
        lblLatency.setText("⚡ " + latencyMs + " ms");
        if (latencyMs < 50) {
            lblLatency.setForeground(new Color(16, 185, 129)); // Green
        } else if (latencyMs < 200) {
            lblLatency.setForeground(new Color(245, 158, 11)); // Amber
        } else {
            lblLatency.setForeground(new Color(239, 68, 68)); // Red
        }
    }

    public void setStatus(String text) {
        lblStatus.setText(text);
    }

    public void setTaskRunning(boolean running, String taskText) {
        progressBar.setVisible(running);
        if (running) {
            lblStatus.setText(taskText != null ? taskText : "Working...");
            lblStatus.setForeground(ThemeManager.getPrimaryActionColor());
        } else {
            lblStatus.setText("Ready");
            boolean dark = ThemeManager.isDarkMode();
            lblStatus.setForeground(dark ? new Color(203, 213, 225) : new Color(51, 65, 85));
        }
    }

    public void setAutoCommit(boolean autoCommit) {
        this.isAutoCommitOn = autoCommit;
        updateAutoCommitVisuals();
    }

    private void updateAutoCommitVisuals() {
        if (isAutoCommitOn) {
            btnAutoCommit.setText("Auto-Commit: ON");
            btnAutoCommit.setForeground(ThemeManager.getSuccessColor());
        } else {
            btnAutoCommit.setText("Auto-Commit: MANUAL");
            btnAutoCommit.setForeground(new Color(245, 158, 11));
        }
    }

    public void setQueryCount(int count) {
        btnQueryLog.setText("📜 Log: " + count);
    }

    private void updateRamMetric() {
        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        long maxBytes = rt.maxMemory();
        int usedMb = (int) (usedBytes / (1024 * 1024));
        int maxMb = (int) (maxBytes / (1024 * 1024));

        btnRam.setText("RAM: " + usedMb + "/" + maxMb + " MB");
        float ratio = (float) usedBytes / maxBytes;
        if (ratio > 0.85f) {
            btnRam.setForeground(new Color(239, 68, 68)); // Red
        } else if (ratio > 0.65f) {
            btnRam.setForeground(new Color(245, 158, 11)); // Amber
        } else {
            btnRam.setForeground(ThemeManager.isDarkMode() ? new Color(192, 132, 252) : new Color(126, 34, 206));
        }
    }

    private void optimizeMemory() {
        long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.gc();
        long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long freedMb = Math.max(0, (before - after) / (1024 * 1024));
        updateRamMetric();
        NotificationToast.showSuccess(this, "Memory Optimized! Reclaimed " + freedMb + " MB RAM.");
    }

    public void setOnAutoCommitToggle(Consumer<Boolean> callback) {
        this.autoCommitToggleCallback = callback;
    }

    public void setOnQueryLogToggle(Runnable callback) {
        this.queryLogToggleCallback = callback;
    }

    public void setOnZoomChange(Runnable callback) {
        this.zoomChangeCallback = callback;
    }

    public void addRightComponent(JComponent comp) {
        rightPanel.add(comp, 0);
        rightPanel.revalidate();
        rightPanel.repaint();
    }

    public void setQueryLogCount(int count) {
        btnQueryLog.setText("📜 Log: " + count);
    }
}
