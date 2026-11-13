package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance smooth spline area chart with gradient fill, interactive hover crosshair,
 * metric switching, and time range filtering.
 *
 * Implements pure Java 2D Catmull-Rom cubic bezier curve interpolation, dynamic gradient fill,
 * and high-DPI anti-aliased rendering matching modern IDE and Cloud Console aesthetics.
 */
public class SplineAreaChartPanel extends JPanel {

    public enum MetricMode {
        QUERY_VOLUME("Query Volume", new Color(59, 130, 246), "QPS"),
        AVG_LATENCY("Avg Latency (ms)", new Color(16, 185, 129), "ms"),
        STORAGE_GROWTH("Storage Growth", new Color(139, 92, 246), "MB");

        public final String title;
        public final Color accentColor;
        public final String unit;

        MetricMode(String title, Color accentColor, String unit) {
            this.title = title;
            this.accentColor = accentColor;
            this.unit = unit;
        }
    }

    public enum TimeRange {
        HOURS_24("24h"),
        DAYS_7("7d"),
        DAYS_30("30d"),
        MONTHS_12("12m");

        public final String label;
        TimeRange(String label) { this.label = label; }
    }

    public static class DataPoint {
        public final String label;
        public final double value;
        public final String detail;

        public DataPoint(String label, double value, String detail) {
            this.label = label;
            this.value = value;
            this.detail = detail;
        }
    }

    private MetricMode currentMode = MetricMode.QUERY_VOLUME;
    private TimeRange currentRange = TimeRange.MONTHS_12;
    private final List<DataPoint> dataPoints = new ArrayList<>();

    // Interactive state
    private int hoveredPointIndex = -1;
    private Point mousePoint = null;

    // UI Buttons
    private JButton btnVolume;
    private JButton btnLatency;
    private JButton btnStorage;

    private JButton btn24h;
    private JButton btn7d;
    private JButton btn30d;
    private JButton btn12m;

    // Telemetry metric labels underneath the chart
    private JLabel peakValueLabel;
    private JLabel lowValueLabel;
    private JLabel p99ValueLabel;
    private JLabel rateValueLabel;

    public SplineAreaChartPanel() {
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 16, 12, 16));

        loadDefaultData();
        initHeaderControls();
        initCanvas();
        initTelemetryRow();
    }

    private void loadDefaultData() {
        dataPoints.clear();
        if (currentRange == TimeRange.MONTHS_12) {
            if (currentMode == MetricMode.QUERY_VOLUME) {
                dataPoints.add(new DataPoint("Jan 26", 120, "Avg: 4.2ms"));
                dataPoints.add(new DataPoint("Feb 26", 140, "Avg: 3.8ms"));
                dataPoints.add(new DataPoint("Mar 26", 190, "Avg: 4.1ms"));
                dataPoints.add(new DataPoint("Apr 26", 240, "Avg: 5.0ms"));
                dataPoints.add(new DataPoint("May 26", 310, "Avg: 4.8ms"));
                dataPoints.add(new DataPoint("Jun 26", 520, "Avg: 6.2ms"));
                dataPoints.add(new DataPoint("Jul 26", 1850, "Avg: 8.5ms"));
                dataPoints.add(new DataPoint("Aug 26", 5420, "Peak • Avg: 11.4ms"));
                dataPoints.add(new DataPoint("Sep 26", 2100, "Avg: 9.1ms"));
                dataPoints.add(new DataPoint("Oct 26", 450, "Avg: 5.3ms"));
                dataPoints.add(new DataPoint("Nov 26", 280, "Avg: 4.6ms"));
            } else if (currentMode == MetricMode.AVG_LATENCY) {
                dataPoints.add(new DataPoint("Jan 26", 3.2, "P95: 8ms"));
                dataPoints.add(new DataPoint("Feb 26", 3.5, "P95: 9ms"));
                dataPoints.add(new DataPoint("Mar 26", 4.1, "P95: 11ms"));
                dataPoints.add(new DataPoint("Apr 26", 4.5, "P95: 12ms"));
                dataPoints.add(new DataPoint("May 26", 4.8, "P95: 14ms"));
                dataPoints.add(new DataPoint("Jun 26", 6.2, "P95: 18ms"));
                dataPoints.add(new DataPoint("Jul 26", 8.9, "P95: 28ms"));
                dataPoints.add(new DataPoint("Aug 26", 14.8, "Peak load • P95: 42ms"));
                dataPoints.add(new DataPoint("Sep 26", 9.2, "P95: 26ms"));
                dataPoints.add(new DataPoint("Oct 26", 5.1, "P95: 15ms"));
                dataPoints.add(new DataPoint("Nov 26", 4.3, "P95: 12ms"));
            } else {
                dataPoints.add(new DataPoint("Jan 26", 420, "+12 MB"));
                dataPoints.add(new DataPoint("Feb 26", 480, "+60 MB"));
                dataPoints.add(new DataPoint("Mar 26", 550, "+70 MB"));
                dataPoints.add(new DataPoint("Apr 26", 640, "+90 MB"));
                dataPoints.add(new DataPoint("May 26", 750, "+110 MB"));
                dataPoints.add(new DataPoint("Jun 26", 890, "+140 MB"));
                dataPoints.add(new DataPoint("Jul 26", 1080, "+190 MB"));
                dataPoints.add(new DataPoint("Aug 26", 1340, "+260 MB"));
                dataPoints.add(new DataPoint("Sep 26", 1410, "+70 MB"));
                dataPoints.add(new DataPoint("Oct 26", 1460, "+50 MB"));
                dataPoints.add(new DataPoint("Nov 26", 1490, "+30 MB"));
            }
        } else if (currentRange == TimeRange.DAYS_30) {
            for (int i = 1; i <= 30; i += 3) {
                double val = 300 + Math.sin(i * 0.4) * 200 + (i == 15 ? 1800 : 0);
                dataPoints.add(new DataPoint("Day " + i, Math.max(80, val), "30d Window"));
            }
        } else if (currentRange == TimeRange.DAYS_7) {
            String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            double[] vals = {620, 840, 1450, 1980, 2450, 920, 540};
            for (int i = 0; i < days.length; i++) {
                dataPoints.add(new DataPoint(days[i], vals[i], "7-Day Load"));
            }
        } else {
            for (int h = 0; h < 24; h += 3) {
                double val = 120 + Math.cos(h * 0.3) * 80 + (h == 15 ? 950 : 0);
                dataPoints.add(new DataPoint(String.format("%02d:00", h), Math.max(30, val), "Hourly"));
            }
        }
    }

    private void initHeaderControls() {
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setOpaque(false);

        // Title and subtitle on the left
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("📈 Database Workload & Execution Throughput");
        titleLabel.setFont(ThemeManager.getUiFont(Font.BOLD, 14));
        titleLabel.setForeground(ThemeManager.getTextColor());

        JLabel subLabel = new JLabel("Continuous execution volume, latency distribution, and throughput trend");
        subLabel.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
        subLabel.setForeground(ThemeManager.getMutedTextColor());

        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(2));
        titlePanel.add(subLabel);
        headerPanel.add(titlePanel, BorderLayout.WEST);

        // Right side: Metric tabs + Time filters
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controlsPanel.setOpaque(false);

        // Metric switch buttons
        JPanel metricBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
        metricBox.setOpaque(false);
        metricBox.setBorder(BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1));

        btnVolume = createFilterButton("Query Volume", true);
        btnLatency = createFilterButton("Avg Latency", false);
        btnStorage = createFilterButton("Storage", false);

        btnVolume.addActionListener(e -> setMetricMode(MetricMode.QUERY_VOLUME));
        btnLatency.addActionListener(e -> setMetricMode(MetricMode.AVG_LATENCY));
        btnStorage.addActionListener(e -> setMetricMode(MetricMode.STORAGE_GROWTH));

        metricBox.add(btnVolume);
        metricBox.add(btnLatency);
        metricBox.add(btnStorage);
        controlsPanel.add(metricBox);

        // Time range buttons
        JPanel timeBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
        timeBox.setOpaque(false);
        timeBox.setBorder(BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1));

        btn24h = createFilterButton("24h", false);
        btn7d = createFilterButton("7d", false);
        btn30d = createFilterButton("30d", false);
        btn12m = createFilterButton("12m", true);

        btn24h.addActionListener(e -> setTimeRange(TimeRange.HOURS_24));
        btn7d.addActionListener(e -> setTimeRange(TimeRange.DAYS_7));
        btn30d.addActionListener(e -> setTimeRange(TimeRange.DAYS_30));
        btn12m.addActionListener(e -> setTimeRange(TimeRange.MONTHS_12));

        timeBox.add(btn24h);
        timeBox.add(btn7d);
        timeBox.add(btn30d);
        timeBox.add(btn12m);
        controlsPanel.add(timeBox);

        headerPanel.add(controlsPanel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);
    }

    private JButton createFilterButton(String text, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(ThemeManager.getUiFont(active ? Font.BOLD : Font.PLAIN, 11));
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(4, 9, 4, 9));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        styleButtonState(btn, active);
        return btn;
    }

    private void styleButtonState(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(ThemeManager.getAccentColor());
            btn.setForeground(Color.WHITE);
        } else {
            btn.setBackground(ThemeManager.isDark() ? new Color(30, 35, 48) : new Color(240, 242, 246));
            btn.setForeground(ThemeManager.getMutedTextColor());
        }
    }

    private void setMetricMode(MetricMode mode) {
        this.currentMode = mode;
        styleButtonState(btnVolume, mode == MetricMode.QUERY_VOLUME);
        styleButtonState(btnLatency, mode == MetricMode.AVG_LATENCY);
        styleButtonState(btnStorage, mode == MetricMode.STORAGE_GROWTH);
        loadDefaultData();
        updateTelemetryValues();
        repaint();
    }

    private void setTimeRange(TimeRange range) {
        this.currentRange = range;
        styleButtonState(btn24h, range == TimeRange.HOURS_24);
        styleButtonState(btn7d, range == TimeRange.DAYS_7);
        styleButtonState(btn30d, range == TimeRange.DAYS_30);
        styleButtonState(btn12m, range == TimeRange.MONTHS_12);
        loadDefaultData();
        updateTelemetryValues();
        repaint();
    }

    private void initCanvas() {
        ChartCanvas canvas = new ChartCanvas();
        canvas.setPreferredSize(new Dimension(800, 210));
        add(canvas, BorderLayout.CENTER);
    }

    private void initTelemetryRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 10, 0));
        row.setOpaque(false);

        peakValueLabel = new JLabel("5,420 QPS");
        lowValueLabel = new JLabel("2.8 ms");
        p99ValueLabel = new JLabel("48.0 ms");
        rateValueLabel = new JLabel("0.02%");

        row.add(createTelemetryCard("Peak Throughput", peakValueLabel, ThemeManager.getTextColor()));
        row.add(createTelemetryCard("Lowest Latency", lowValueLabel, new Color(16, 185, 129)));
        row.add(createTelemetryCard("99th Percentile", p99ValueLabel, new Color(245, 158, 11)));
        row.add(createTelemetryCard("Query Error Rate", rateValueLabel, new Color(16, 185, 129)));

        add(row, BorderLayout.SOUTH);
        updateTelemetryValues();
    }

    private JPanel createTelemetryCard(String title, JLabel valueLabel, Color valueColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ThemeManager.isDark() ? new Color(24, 28, 40) : new Color(245, 247, 250));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
        titleLbl.setForeground(ThemeManager.getMutedTextColor());

        valueLabel.setFont(ThemeManager.getUiFont(Font.BOLD, 13));
        valueLabel.setForeground(valueColor);

        card.add(titleLbl);
        card.add(Box.createVerticalStrut(2));
        card.add(valueLabel);
        return card;
    }

    private void updateTelemetryValues() {
        if (currentMode == MetricMode.QUERY_VOLUME) {
            peakValueLabel.setText("5,420 QPS");
            lowValueLabel.setText("120 QPS");
            p99ValueLabel.setText("4,980 QPS");
            rateValueLabel.setText("0.02% Error");
        } else if (currentMode == MetricMode.AVG_LATENCY) {
            peakValueLabel.setText("14.8 ms (Peak)");
            lowValueLabel.setText("2.8 ms (Min)");
            p99ValueLabel.setText("42.0 ms (P95)");
            rateValueLabel.setText("99.98% OK");
        } else {
            peakValueLabel.setText("1.49 GB Total");
            lowValueLabel.setText("+12 MB/mo");
            p99ValueLabel.setText("+260 MB (Aug)");
            rateValueLabel.setText("Optimal");
        }
    }

    /**
     * Set external live query and throughput points dynamically from AppUsageTracker or DatabaseManager.
     */
    public void setChartData(List<DataPoint> points) {
        if (points != null && !points.isEmpty()) {
            this.dataPoints.clear();
            this.dataPoints.addAll(points);
            repaint();
        }
    }

    /**
     * Internal Canvas performing Java 2D spline curve interpolation and gradient area rendering.
     */
    private class ChartCanvas extends JComponent {

        public ChartCanvas() {
            setOpaque(false);
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePoint = e.getPoint();
                    updateHoveredIndex(e.getX());
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    mousePoint = null;
                    hoveredPointIndex = -1;
                    repaint();
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        private void updateHoveredIndex(int mouseX) {
            if (dataPoints.isEmpty()) {
                hoveredPointIndex = -1;
                return;
            }
            int width = getWidth() - 80;
            if (width <= 0) return;

            int n = dataPoints.size();
            double step = (double) width / Math.max(1, n - 1);
            int closest = -1;
            double minDist = Double.MAX_VALUE;

            for (int i = 0; i < n; i++) {
                double px = 40 + i * step;
                double dist = Math.abs(px - mouseX);
                if (dist < minDist) {
                    minDist = dist;
                    closest = i;
                }
            }
            hoveredPointIndex = (minDist <= step * 0.8) ? closest : -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (dataPoints.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            int padLeft = 45;
            int padRight = 40;
            int padTop = 25;
            int padBottom = 35;

            int plotWidth = width - padLeft - padRight;
            int plotHeight = height - padTop - padBottom;
            int baselineY = padTop + plotHeight;

            if (plotWidth <= 0 || plotHeight <= 0) {
                g2.dispose();
                return;
            }

            // Draw horizontal gridlines (4 levels)
            g2.setColor(ThemeManager.isDark() ? new Color(255, 255, 255, 18) : new Color(0, 0, 0, 16));
            Stroke gridStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0);
            g2.setStroke(gridStroke);

            for (int i = 0; i <= 3; i++) {
                int gy = padTop + (plotHeight * i) / 3;
                g2.drawLine(padLeft, gy, padLeft + plotWidth, gy);
            }

            // Find Max Value for scaling
            double maxVal = 1.0;
            for (DataPoint dp : dataPoints) {
                if (dp.value > maxVal) maxVal = dp.value;
            }
            maxVal *= 1.15; // headroom

            int n = dataPoints.size();
            Point2D.Double[] pixelPoints = new Point2D.Double[n];
            for (int i = 0; i < n; i++) {
                double px = padLeft + (n == 1 ? plotWidth / 2.0 : (double) i * plotWidth / (n - 1));
                double py = baselineY - (dataPoints.get(i).value / maxVal) * plotHeight;
                pixelPoints[i] = new Point2D.Double(px, py);
            }

            // Calculate Catmull-Rom to Cubic Bezier spline paths
            Path2D.Double splinePath = new Path2D.Double();
            Path2D.Double areaPath = new Path2D.Double();

            splinePath.moveTo(pixelPoints[0].x, pixelPoints[0].y);
            areaPath.moveTo(pixelPoints[0].x, baselineY);
            areaPath.lineTo(pixelPoints[0].x, pixelPoints[0].y);

            for (int i = 0; i < n - 1; i++) {
                Point2D.Double p0 = (i > 0) ? pixelPoints[i - 1] : pixelPoints[i];
                Point2D.Double p1 = pixelPoints[i];
                Point2D.Double p2 = pixelPoints[i + 1];
                Point2D.Double p3 = (i + 2 < n) ? pixelPoints[i + 2] : p2;

                double c1x = p1.x + (p2.x - p0.x) / 6.0;
                double c1y = p1.y + (p2.y - p0.y) / 6.0;
                double c2x = p2.x - (p3.x - p1.x) / 6.0;
                double c2y = p2.y - (p3.y - p1.y) / 6.0;

                splinePath.curveTo(c1x, c1y, c2x, c2y, p2.x, p2.y);
                areaPath.curveTo(c1x, c1y, c2x, c2y, p2.x, p2.y);
            }

            areaPath.lineTo(pixelPoints[n - 1].x, baselineY);
            areaPath.closePath();

            // 1. Fill translucent vertical gradient
            Color accent = currentMode.accentColor;
            GradientPaint areaPaint = new GradientPaint(
                    0, padTop, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 125),
                    0, baselineY, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)
            );
            g2.setPaint(areaPaint);
            g2.fill(areaPath);

            // 2. Stroke glowing spline line
            g2.setPaint(accent);
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(splinePath);

            // 3. Highlight Peak Dot (August / max point)
            int peakIdx = 0;
            double highest = 0;
            for (int i = 0; i < n; i++) {
                if (dataPoints.get(i).value > highest) {
                    highest = dataPoints.get(i).value;
                    peakIdx = i;
                }
            }

            // Draw glowing peak halo
            Point2D.Double peakPt = pixelPoints[peakIdx];
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
            g2.fillOval((int) peakPt.x - 7, (int) peakPt.y - 7, 14, 14);
            g2.setColor(Color.WHITE);
            g2.fillOval((int) peakPt.x - 4, (int) peakPt.y - 4, 8, 8);
            g2.setColor(accent);
            g2.drawOval((int) peakPt.x - 4, (int) peakPt.y - 4, 8, 8);

            // 4. Draw X-axis Timeline Labels along bottom
            g2.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < n; i++) {
                String lbl = dataPoints.get(i).label;
                int strW = fm.stringWidth(lbl);
                int lx = (int) pixelPoints[i].x - strW / 2;
                int ly = baselineY + 18;

                if (i == peakIdx) {
                    g2.setColor(accent);
                    g2.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
                } else {
                    g2.setColor(ThemeManager.getMutedTextColor());
                    g2.setFont(ThemeManager.getUiFont(Font.PLAIN, 11));
                }
                g2.drawString(lbl, lx, ly);
            }

            // 5. Draw Interactive Hover Crosshair & Tooltip
            if (hoveredPointIndex >= 0 && hoveredPointIndex < n) {
                Point2D.Double hPt = pixelPoints[hoveredPointIndex];
                DataPoint dp = dataPoints.get(hoveredPointIndex);

                // Vertical dashed crosshair
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 160));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3, 3}, 0));
                g2.drawLine((int) hPt.x, padTop, (int) hPt.x, baselineY);

                // Glowing indicator dot
                g2.setColor(accent);
                g2.fillOval((int) hPt.x - 6, (int) hPt.y - 6, 12, 12);
                g2.setColor(Color.WHITE);
                g2.fillOval((int) hPt.x - 3, (int) hPt.y - 3, 6, 6);

                // Tooltip Box
                String valStr = String.format("%.0f %s", dp.value, currentMode.unit);
                String line1 = dp.label;
                String line2 = valStr + (dp.detail != null && !dp.detail.isEmpty() ? " • " + dp.detail : "");

                int tipW = Math.max(fm.stringWidth(line1), fm.stringWidth(line2)) + 24;
                int tipH = 44;
                int tipX = (int) hPt.x - tipW / 2;
                tipX = Math.max(padLeft, Math.min(tipX, padLeft + plotWidth - tipW));
                int tipY = (int) hPt.y - tipH - 12;
                if (tipY < padTop - 10) tipY = (int) hPt.y + 14;

                RoundRectangle2D.Double tipRect = new RoundRectangle2D.Double(tipX, tipY, tipW, tipH, 10, 10);
                g2.setColor(ThemeManager.isDark() ? new Color(18, 22, 34, 235) : new Color(255, 255, 255, 240));
                g2.fill(tipRect);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(tipRect);

                g2.setFont(ThemeManager.getUiFont(Font.BOLD, 10));
                g2.setColor(ThemeManager.getMutedTextColor());
                g2.drawString(line1, tipX + 12, tipY + 16);

                g2.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
                g2.setColor(ThemeManager.getTextColor());
                g2.drawString(line2, tipX + 12, tipY + 32);
            }

            g2.dispose();
        }
    }
}
