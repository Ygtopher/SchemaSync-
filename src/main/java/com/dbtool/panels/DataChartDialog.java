package com.dbtool.panels;

import com.dbtool.util.ThemeManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class DataChartDialog extends JDialog {

    public enum ChartType {
        BAR_CHART("📊 Bar Chart"),
        LINE_CHART("📈 Line / Trend Chart"),
        DONUT_CHART("🍩 Donut / Pie Chart");

        public final String label;
        ChartType(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum AggregationType {
        SUM("Sum"),
        AVERAGE("Average"),
        COUNT("Count (Row Frequency)"),
        MAX("Maximum"),
        MIN("Minimum");

        public final String label;
        AggregationType(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final TableModel tableModel;
    private final JComboBox<ChartType> chartTypeCombo = new JComboBox<>(ChartType.values());
    private final JComboBox<String> xColumnCombo = new JComboBox<>();
    private final JComboBox<String> yColumnCombo = new JComboBox<>();
    private final JComboBox<AggregationType> aggCombo = new JComboBox<>(AggregationType.values());
    private final JComboBox<String> topNCombo = new JComboBox<>(new String[]{"Top 10", "Top 20", "Top 50", "All"});
    private final ChartCanvas canvas;

    private static final Color[] PALETTE = {
        new Color(0, 229, 255),   // Cyan
        new Color(59, 130, 246),  // Royal Blue
        new Color(16, 185, 129),  // Emerald
        new Color(168, 85, 247),  // Purple
        new Color(245, 158, 11),  // Amber / Gold
        new Color(239, 68, 68),   // Rose
        new Color(20, 184, 166),  // Teal
        new Color(236, 72, 153),  // Pink
        new Color(99, 102, 241),  // Indigo
        new Color(132, 204, 22)   // Lime
    };

    public DataChartDialog(Frame parent, JTable table, String defaultTitle) {
        super(parent, "📊 Visual Data Chart - " + (defaultTitle != null ? defaultTitle : "Query Results"), true);
        this.tableModel = table.getModel();

        setSize(960, 680);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // Top Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        toolbar.add(new JLabel("Type:"));
        toolbar.add(chartTypeCombo);

        toolbar.add(new JLabel("Category (X):"));
        toolbar.add(xColumnCombo);

        toolbar.add(new JLabel("Metric (Y):"));
        toolbar.add(yColumnCombo);

        toolbar.add(new JLabel("Aggregation:"));
        toolbar.add(aggCombo);

        toolbar.add(new JLabel("Limit:"));
        toolbar.add(topNCombo);

        add(toolbar, BorderLayout.NORTH);

        // Populate column dropdowns
        int colCount = tableModel.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            String cName = tableModel.getColumnName(i);
            xColumnCombo.addItem(cName);
            yColumnCombo.addItem(cName);
        }

        // Try to pick sensible defaults: first column for X, first numeric column for Y
        if (colCount > 0) xColumnCombo.setSelectedIndex(0);
        for (int i = 0; i < colCount; i++) {
            if (isColumnNumeric(i)) {
                yColumnCombo.setSelectedIndex(i);
                break;
            }
        }

        canvas = new ChartCanvas();
        add(canvas, BorderLayout.CENTER);

        // Listeners for live redraw
        chartTypeCombo.addActionListener(e -> {
            boolean isCount = aggCombo.getSelectedItem() == AggregationType.COUNT;
            yColumnCombo.setEnabled(!isCount);
            canvas.repaint();
        });
        xColumnCombo.addActionListener(e -> canvas.repaint());
        yColumnCombo.addActionListener(e -> canvas.repaint());
        aggCombo.addActionListener(e -> {
            boolean isCount = aggCombo.getSelectedItem() == AggregationType.COUNT;
            yColumnCombo.setEnabled(!isCount);
            canvas.repaint();
        });
        topNCombo.addActionListener(e -> canvas.repaint());

        // Bottom Bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton exportPngBtn = new JButton("📥 Export Chart (PNG)");
        exportPngBtn.addActionListener(e -> exportChartPng());
        bottomBar.add(exportPngBtn);

        JButton copyBtn = new JButton("📋 Copy Chart Image");
        copyBtn.addActionListener(e -> copyChartImage());
        bottomBar.add(copyBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private boolean isColumnNumeric(int colIdx) {
        int rows = Math.min(tableModel.getRowCount(), 20);
        for (int r = 0; r < rows; r++) {
            Object v = tableModel.getValueAt(r, colIdx);
            if (v instanceof Number) return true;
            if (v != null) {
                try {
                    Double.parseDouble(v.toString());
                    return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    private class ChartCanvas extends JPanel {
        private String hoveredText = null;

        public ChartCanvas() {
            setBackground(new Color(15, 23, 42)); // Slate 900
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    // Tooltip repaint if needed
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth();
            int h = getHeight();

            // Background subtle gradient
            g2.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[]{0f, 1f},
                new Color[]{new Color(15, 23, 42), new Color(11, 15, 25)}));
            g2.fillRect(0, 0, w, h);

            Map<String, Double> data = aggregateData();
            if (data.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 16));
                g2.drawString("No data available to plot.", w / 2 - 80, h / 2);
                g2.dispose();
                return;
            }

            ChartType type = (ChartType) chartTypeCombo.getSelectedItem();
            if (type == ChartType.BAR_CHART) {
                renderBarChart(g2, data, w, h);
            } else if (type == ChartType.LINE_CHART) {
                renderLineChart(g2, data, w, h);
            } else {
                renderDonutChart(g2, data, w, h);
            }

            g2.dispose();
        }

        private void renderBarChart(Graphics2D g2, Map<String, Double> data, int w, int h) {
            int padLeft = 80, padRight = 40, padTop = 50, padBottom = 80;
            int plotW = w - padLeft - padRight;
            int plotH = h - padTop - padBottom;

            double maxVal = 0.0001;
            for (double v : data.values()) if (v > maxVal) maxVal = v;

            // Gridlines
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
            g2.setColor(new Color(51, 65, 85, 120));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            DecimalFormat df = new DecimalFormat("#,##0.##");

            int gridSteps = 5;
            for (int i = 0; i <= gridSteps; i++) {
                int y = padTop + plotH - (int) ((i / (double) gridSteps) * plotH);
                g2.drawLine(padLeft, y, padLeft + plotW, y);
                double tickVal = (maxVal / gridSteps) * i;
                g2.setColor(new Color(148, 163, 184));
                g2.drawString(df.format(tickVal), padLeft - 65, y + 4);
                g2.setColor(new Color(51, 65, 85, 120));
            }

            int n = data.size();
            double barSlotW = (double) plotW / n;
            double barW = Math.max(8, barSlotW * 0.65);

            int idx = 0;
            for (Map.Entry<String, Double> entry : data.entrySet()) {
                double val = entry.getValue();
                int barH = (int) ((val / maxVal) * plotH);
                int x = (int) (padLeft + idx * barSlotW + (barSlotW - barW) / 2);
                int y = padTop + plotH - barH;

                Color c = PALETTE[idx % PALETTE.length];
                Paint barPaint = new LinearGradientPaint(x, y, x, y + barH,
                    new float[]{0f, 1f},
                    new Color[]{c, new Color(c.getRed() / 2, c.getGreen() / 2, c.getBlue() / 2, 220)});
                g2.setPaint(barPaint);
                g2.fill(new RoundRectangle2D.Double(x, y, barW, barH, 6, 6));

                // Value on top
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                String valStr = df.format(val);
                int strW = g2.getFontMetrics().stringWidth(valStr);
                g2.drawString(valStr, (int) (x + (barW - strW) / 2), y - 6);

                // Category Label on Bottom (Rotated if needed)
                g2.setColor(new Color(203, 213, 225));
                String cat = entry.getKey();
                if (cat.length() > 14) cat = cat.substring(0, 12) + "..";
                int catW = g2.getFontMetrics().stringWidth(cat);
                g2.drawString(cat, (int) (x + (barW - catW) / 2), padTop + plotH + 20);

                idx++;
            }
        }

        private void renderLineChart(Graphics2D g2, Map<String, Double> data, int w, int h) {
            int padLeft = 80, padRight = 40, padTop = 50, padBottom = 80;
            int plotW = w - padLeft - padRight;
            int plotH = h - padTop - padBottom;

            double maxVal = 0.0001;
            for (double v : data.values()) if (v > maxVal) maxVal = v;

            DecimalFormat df = new DecimalFormat("#,##0.##");

            // Gridlines
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            int gridSteps = 5;
            for (int i = 0; i <= gridSteps; i++) {
                int y = padTop + plotH - (int) ((i / (double) gridSteps) * plotH);
                g2.setColor(new Color(51, 65, 85, 120));
                g2.drawLine(padLeft, y, padLeft + plotW, y);
                g2.setColor(new Color(148, 163, 184));
                g2.drawString(df.format((maxVal / gridSteps) * i), padLeft - 65, y + 4);
            }

            int n = data.size();
            if (n < 2) {
                renderBarChart(g2, data, w, h);
                return;
            }

            double stepX = (double) plotW / (n - 1);
            Point2D.Double[] pts = new Point2D.Double[n];
            String[] labels = new String[n];

            int idx = 0;
            for (Map.Entry<String, Double> entry : data.entrySet()) {
                double val = entry.getValue();
                double x = padLeft + idx * stepX;
                double y = padTop + plotH - ((val / maxVal) * plotH);
                pts[idx] = new Point2D.Double(x, y);
                labels[idx] = entry.getKey();
                idx++;
            }

            // Shaded Area under Curve
            Path2D area = new Path2D.Double();
            area.moveTo(pts[0].x, padTop + plotH);
            for (Point2D.Double p : pts) area.lineTo(p.x, p.y);
            area.lineTo(pts[n - 1].x, padTop + plotH);
            area.closePath();

            g2.setPaint(new LinearGradientPaint(0, padTop, 0, padTop + plotH,
                new float[]{0f, 1f},
                new Color[]{new Color(6, 182, 212, 100), new Color(6, 182, 212, 5)}));
            g2.fill(area);

            // Glowing Line
            g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(6, 182, 212));
            Path2D line = new Path2D.Double();
            line.moveTo(pts[0].x, pts[0].y);
            for (int i = 1; i < n; i++) line.lineTo(pts[i].x, pts[i].y);
            g2.draw(line);

            // Dots & Labels
            for (int i = 0; i < n; i++) {
                Point2D.Double p = pts[i];
                g2.setColor(new Color(6, 182, 212));
                g2.fill(new Ellipse2D.Double(p.x - 5, p.y - 5, 10, 10));
                g2.setColor(Color.WHITE);
                g2.fill(new Ellipse2D.Double(p.x - 2.5, p.y - 2.5, 5, 5));

                g2.setColor(new Color(203, 213, 225));
                String cat = labels[i];
                if (cat.length() > 10) cat = cat.substring(0, 8) + "..";
                int catW = g2.getFontMetrics().stringWidth(cat);
                g2.drawString(cat, (int) (p.x - catW / 2), padTop + plotH + 20);
            }
        }

        private void renderDonutChart(Graphics2D g2, Map<String, Double> data, int w, int h) {
            double total = 0;
            for (double v : data.values()) total += v;
            if (total <= 0) return;

            int cx = w / 2 - 80;
            int cy = h / 2;
            int outerR = Math.min(w, h) / 3;
            int innerR = outerR / 2;

            double startAngle = 90;
            int idx = 0;
            DecimalFormat df = new DecimalFormat("#,##0.##");

            // Legend Panel on Right
            int legX = cx + outerR + 40;
            int legY = cy - outerR + 20;

            for (Map.Entry<String, Double> entry : data.entrySet()) {
                double val = entry.getValue();
                double arcAngle = (val / total) * 360.0;
                Color c = PALETTE[idx % PALETTE.length];

                // Donut Slice
                Arc2D.Double outerArc = new Arc2D.Double(cx - outerR, cy - outerR, outerR * 2, outerR * 2, startAngle, -arcAngle, Arc2D.PIE);
                g2.setColor(c);
                g2.fill(outerArc);

                startAngle -= arcAngle;

                // Legend Item
                if (legY + 20 < h - 40) {
                    g2.fillRect(legX, legY, 12, 12);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    double pct = (val / total) * 100.0;
                    String itemText = String.format("%s: %s (%.1f%%)", entry.getKey(), df.format(val), pct);
                    g2.drawString(itemText, legX + 20, legY + 11);
                    legY += 24;
                }
                idx++;
            }

            // Donut Center Hole
            g2.setColor(new Color(15, 23, 42));
            g2.fill(new Ellipse2D.Double(cx - innerR, cy - innerR, innerR * 2, innerR * 2));

            // Center Label
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            String totalStr = "Total: " + df.format(total);
            int tw = g2.getFontMetrics().stringWidth(totalStr);
            g2.drawString(totalStr, cx - tw / 2, cy + 5);
        }
    }

    private Map<String, Double> aggregateData() {
        Map<String, Double> map = new LinkedHashMap<>();
        int xIdx = xColumnCombo.getSelectedIndex();
        int yIdx = yColumnCombo.getSelectedIndex();
        AggregationType agg = (AggregationType) aggCombo.getSelectedItem();
        if (xIdx < 0 || agg == null) return map;

        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        int rowCount = tableModel.getRowCount();

        for (int r = 0; r < rowCount; r++) {
            Object xVal = tableModel.getValueAt(r, xIdx);
            String cat = xVal != null ? xVal.toString().trim() : "(Null)";
            if (cat.isEmpty()) cat = "(Empty)";

            double val = 1.0;
            if (agg != AggregationType.COUNT && yIdx >= 0) {
                Object yVal = tableModel.getValueAt(r, yIdx);
                if (yVal != null) {
                    try {
                        val = Double.parseDouble(yVal.toString());
                    } catch (Exception ignored) {
                        val = 0.0;
                    }
                } else {
                    val = 0.0;
                }
            }
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(val);
        }

        for (Map.Entry<String, List<Double>> entry : grouped.entrySet()) {
            List<Double> list = entry.getValue();
            double result = 0;
            switch (agg) {
                case COUNT:
                    result = list.size();
                    break;
                case SUM:
                    for (double d : list) result += d;
                    break;
                case AVERAGE:
                    double sum = 0;
                    for (double d : list) sum += d;
                    result = list.isEmpty() ? 0 : sum / list.size();
                    break;
                case MAX:
                    result = Collections.max(list);
                    break;
                case MIN:
                    result = Collections.min(list);
                    break;
            }
            map.put(entry.getKey(), result);
        }

        // Limit Top N
        String limit = (String) topNCombo.getSelectedItem();
        if (limit != null && !limit.equals("All")) {
            int top = 10;
            if (limit.contains("20")) top = 20;
            else if (limit.contains("50")) top = 50;

            List<Map.Entry<String, Double>> sorted = new ArrayList<>(map.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            Map<String, Double> topMap = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(top, sorted.size()); i++) {
                topMap.put(sorted.get(i).getKey(), sorted.get(i).getValue());
            }
            return topMap;
        }

        return map;
    }

    private BufferedImage createChartImage() {
        BufferedImage img = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        canvas.paint(g2);
        g2.dispose();
        return img;
    }

    private void exportChartPng() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("chart.png"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage img = createChartImage();
                ImageIO.write(img, "PNG", fc.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Chart saved successfully as PNG!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save chart: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void copyChartImage() {
        BufferedImage img = createChartImage();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(img), null);
        JOptionPane.showMessageDialog(this, "Chart image copied to clipboard!");
    }

    private static class TransferableImage implements Transferable {
        private final Image image;
        public TransferableImage(Image image) { this.image = image; }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }
}
