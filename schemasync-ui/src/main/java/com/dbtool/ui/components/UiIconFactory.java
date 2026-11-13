package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-DPI procedural vector icon factory for SchemaSync.
 * Renders razor-sharp icons at any display resolution or UI scale factor without bitmap pixelation.
 */
public class UiIconFactory {

    public enum IconType {
        DASHBOARD,
        BROWSE,
        JOIN,
        DATA_OPS,
        SQL_EDITOR,
        ACTIVITY,
        SCHEMA_GRAPH,
        ER_DIAGRAM,
        SCRIPT,
        COMPARE,
        DUAL_DB,
        SSH,
        AI,
        SEARCH,
        CONNECT,
        DISCONNECT,
        DATABASE,
        TABLE,
        SETTINGS,
        REFRESH,
        RUN,
        STOP,
        CHIP_RAM,
        LIGHTNING,
        LOCK,
        CHART,
        EXPORT,
        KEY,
        LINK,
        DETACH,
        ATTACH
    }

    private static final Map<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();

    public static Icon getIcon(IconType type) {
        return getIcon(type, 16);
    }

    public static Icon getIcon(IconType type, int size) {
        return getIcon(type, size, null);
    }

    public static Icon getIcon(IconType type, int size, Color customColor) {
        boolean dark = ThemeManager.isDarkMode();
        Color c = customColor != null ? customColor : (dark ? new Color(203, 213, 225) : new Color(51, 65, 85));
        String cacheKey = type.name() + "_" + size + "_" + c.getRGB();
        return ICON_CACHE.computeIfAbsent(cacheKey, k -> new VectorIcon(type, size, c));
    }

    public static Icon getAccentIcon(IconType type, int size) {
        Color accent = ThemeManager.getPrimaryActionColor();
        String cacheKey = type.name() + "_accent_" + size + "_" + accent.getRGB();
        return ICON_CACHE.computeIfAbsent(cacheKey, k -> new VectorIcon(type, size, accent));
    }

    public static void clearCache() {
        ICON_CACHE.clear();
    }

    private static class VectorIcon implements Icon {
        private final IconType type;
        private final int size;
        private final Color color;

        VectorIcon(IconType type, int size, Color color) {
            this.type = type;
            this.size = size;
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);

                double scale = size / 16.0;
                g2.scale(scale, scale);

                Color drawColor = color;
                if (c != null && !c.isEnabled()) {
                    drawColor = ThemeManager.getMutedTextColor();
                }
                g2.setColor(drawColor);

                paintVector(g2, type, drawColor);
            } finally {
                g2.dispose();
            }
        }

        private void paintVector(Graphics2D g2, IconType type, Color col) {
            float strokeW = 1.35f;
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (type) {
                case DASHBOARD -> {
                    // 4-quadrant layout tiles
                    g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, 5.5f, 5.5f, 2, 2));
                    g2.draw(new RoundRectangle2D.Float(9f, 1.5f, 5.5f, 4f, 2, 2));
                    g2.draw(new RoundRectangle2D.Float(1.5f, 9f, 5.5f, 5.5f, 2, 2));
                    g2.draw(new RoundRectangle2D.Float(9f, 7.5f, 5.5f, 7f, 2, 2));
                }
                case BROWSE -> {
                    // Table grid + search
                    g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, 13f, 13f, 3, 3));
                    g2.drawLine(1, 5, 14, 5);
                    g2.drawLine(5, 5, 5, 14);
                    g2.drawLine(10, 5, 10, 14);
                }
                case JOIN -> {
                    // Two connected entity boxes with linking line
                    g2.draw(new RoundRectangle2D.Float(1f, 2f, 5f, 5f, 2, 2));
                    g2.draw(new RoundRectangle2D.Float(10f, 9f, 5f, 5f, 2, 2));
                    Path2D path = new Path2D.Float();
                    path.moveTo(6, 4.5f);
                    path.curveTo(9, 4.5f, 7, 11.5f, 10, 11.5f);
                    g2.draw(path);
                }
                case DATA_OPS -> {
                    // Lightning bolt with gear/action tick
                    Path2D bolt = new Path2D.Float();
                    bolt.moveTo(9f, 1f);
                    bolt.lineTo(3.5f, 8.5f);
                    bolt.lineTo(8f, 8.5f);
                    bolt.lineTo(7f, 15f);
                    bolt.lineTo(12.5f, 7.5f);
                    bolt.lineTo(8f, 7.5f);
                    bolt.closePath();
                    g2.draw(bolt);
                }
                case SQL_EDITOR -> {
                    // Terminal prompt >_ or code brackets
                    Path2D code = new Path2D.Float();
                    code.moveTo(2.5f, 4.5f);
                    code.lineTo(5.5f, 8f);
                    code.lineTo(2.5f, 11.5f);
                    g2.draw(code);
                    g2.drawLine(7, 11, 13, 11);
                }
                case ACTIVITY -> {
                    // Pulse / heartbeat graph
                    Path2D pulse = new Path2D.Float();
                    pulse.moveTo(1.5f, 8.5f);
                    pulse.lineTo(4.5f, 8.5f);
                    pulse.lineTo(6.5f, 3f);
                    pulse.lineTo(9f, 13.5f);
                    pulse.lineTo(11.5f, 7f);
                    pulse.lineTo(12.5f, 8.5f);
                    pulse.lineTo(14.5f, 8.5f);
                    g2.draw(pulse);
                }
                case SCHEMA_GRAPH -> {
                    // Connected nodes
                    g2.draw(new Ellipse2D.Float(6.5f, 1.5f, 3f, 3f));
                    g2.draw(new Ellipse2D.Float(2f, 11.5f, 3f, 3f));
                    g2.draw(new Ellipse2D.Float(11f, 11.5f, 3f, 3f));
                    drawLine(g2, 7, 4.5f, 4, 11.5f);
                    drawLine(g2, 9, 4.5f, 12, 11.5f);
                }
                case ER_DIAGRAM -> {
                    // Database entity card
                    g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, 13f, 13f, 2, 2));
                    g2.drawLine(1, 6, 14, 6);
                    g2.drawLine(3, 9, 7, 9);
                    g2.drawLine(3, 12, 9, 12);
                }
                case SCRIPT -> {
                    // Document scroll with lines
                    g2.draw(new RoundRectangle2D.Float(2.5f, 1.5f, 11f, 13f, 2, 2));
                    g2.drawLine(5, 5, 11, 5);
                    g2.drawLine(5, 8, 11, 8);
                    g2.drawLine(5, 11, 9, 11);
                }
                case COMPARE -> {
                    // Side-by-side split compare
                    g2.draw(new RoundRectangle2D.Float(1.5f, 2f, 5.5f, 12f, 2, 2));
                    g2.draw(new RoundRectangle2D.Float(9f, 2f, 5.5f, 12f, 2, 2));
                    drawLine(g2, 3, 5, 5.5f, 5);
                    drawLine(g2, 10.5f, 5, 13, 5);
                    drawLine(g2, 3, 8, 5.5f, 8);
                    drawLine(g2, 10.5f, 8, 13, 8);
                }
                case DUAL_DB -> {
                    // Twin synchronized database cylinders
                    paintCylinder(g2, 1.5f, 3f, 5.5f, 10f);
                    paintCylinder(g2, 9f, 3f, 5.5f, 10f);
                    // Sync arrow in center
                    Path2D arrow = new Path2D.Float();
                    arrow.moveTo(6.5f, 5f);
                    arrow.lineTo(8.5f, 5f);
                    arrow.moveTo(8.5f, 10f);
                    arrow.lineTo(6.5f, 10f);
                    g2.draw(arrow);
                }
                case SSH -> {
                    // Terminal window
                    g2.draw(new RoundRectangle2D.Float(1.5f, 2f, 13f, 12f, 2, 2));
                    drawLine(g2, 1, 5.5f, 14, 5.5f);
                    Path2D p = new Path2D.Float();
                    p.moveTo(4, 8);
                    p.lineTo(6, 9.5f);
                    p.lineTo(4, 11);
                    g2.draw(p);
                    drawLine(g2, 7.5f, 11, 11, 11);
                }
                case AI -> {
                    // Generative AI 4-pointed sparkle star
                    Path2D star = new Path2D.Float();
                    star.moveTo(8f, 1.5f);
                    star.curveTo(8f, 5.5f, 10.5f, 8f, 14.5f, 8f);
                    star.curveTo(10.5f, 8f, 8f, 10.5f, 8f, 14.5f);
                    star.curveTo(8f, 10.5f, 5.5f, 8f, 1.5f, 8f);
                    star.curveTo(5.5f, 8f, 8f, 5.5f, 8f, 1.5f);
                    star.closePath();
                    g2.draw(star);
                }
                case SEARCH -> {
                    // Crisp magnifying glass
                    g2.draw(new Ellipse2D.Float(2f, 2f, 8.5f, 8.5f));
                    drawLine(g2, 8.5f, 8.5f, 14f, 14f);
                }
                case CONNECT -> {
                    // Database cylinder with plus badge
                    paintCylinder(g2, 2f, 2f, 8.5f, 12f);
                    g2.drawLine(13, 7, 13, 13);
                    g2.drawLine(10, 10, 16, 10);
                }
                case DISCONNECT -> {
                    // Unlink / eject
                    g2.draw(new Ellipse2D.Float(3f, 3f, 10f, 10f));
                    g2.drawLine(2, 2, 14, 14);
                }
                case DATABASE -> {
                    paintCylinder(g2, 2.5f, 2f, 11f, 12f);
                }
                case TABLE -> {
                    g2.draw(new RoundRectangle2D.Float(2f, 2f, 12f, 12f, 2, 2));
                    g2.drawLine(2, 6, 14, 6);
                    g2.drawLine(6, 6, 6, 14);
                }
                case SETTINGS -> {
                    g2.draw(new Ellipse2D.Float(5.5f, 5.5f, 5f, 5f));
                    g2.drawLine(8, 1, 8, 4);
                    g2.drawLine(8, 12, 8, 15);
                    g2.drawLine(1, 8, 4, 8);
                    g2.drawLine(12, 8, 15, 8);
                }
                case REFRESH -> {
                    Path2D arc = new Path2D.Float();
                    arc.append(new Arc2D.Float(2f, 2f, 12f, 12f, 45, 270, Arc2D.OPEN), false);
                    g2.draw(arc);
                    Path2D tip = new Path2D.Float();
                    tip.moveTo(11, 2);
                    tip.lineTo(14, 4.5f);
                    tip.lineTo(10.5f, 6);
                    g2.draw(tip);
                }
                case RUN -> {
                    // Play triangle
                    Path2D play = new Path2D.Float();
                    play.moveTo(4f, 2.5f);
                    play.lineTo(13.5f, 8f);
                    play.lineTo(4f, 13.5f);
                    play.closePath();
                    g2.draw(play);
                }
                case STOP -> {
                    g2.draw(new RoundRectangle2D.Float(3f, 3f, 10f, 10f, 2, 2));
                }
                case CHIP_RAM -> {
                    g2.draw(new RoundRectangle2D.Float(3.5f, 3.5f, 9f, 9f, 2, 2));
                    drawLine(g2, 6, 1, 6, 3.5f);
                    drawLine(g2, 10, 1, 10, 3.5f);
                    drawLine(g2, 6, 12.5f, 6, 15);
                    drawLine(g2, 10, 12.5f, 10, 15);
                    drawLine(g2, 1, 6, 3.5f, 6);
                    drawLine(g2, 1, 10, 3.5f, 10);
                    drawLine(g2, 12.5f, 6, 15, 6);
                    drawLine(g2, 12.5f, 10, 15, 10);
                }
                case LIGHTNING -> {
                    Path2D l = new Path2D.Float();
                    l.moveTo(9, 1);
                    l.lineTo(4, 8);
                    l.lineTo(8, 8);
                    l.lineTo(7, 15);
                    l.lineTo(12, 7);
                    l.lineTo(8, 7);
                    l.closePath();
                    g2.draw(l);
                }
                case LOCK -> {
                    g2.draw(new RoundRectangle2D.Float(2.5f, 6.5f, 11f, 8f, 2, 2));
                    Path2D shackle = new Path2D.Float();
                    shackle.moveTo(5, 6.5f);
                    shackle.lineTo(5, 4);
                    shackle.curveTo(5, 2, 11, 2, 11, 4);
                    shackle.lineTo(11, 6.5f);
                    g2.draw(shackle);
                }
                case CHART -> {
                    g2.drawLine(2, 14, 14, 14);
                    g2.drawLine(2, 2, 2, 14);
                    g2.draw(new Rectangle2D.Float(4f, 8f, 2.5f, 6f));
                    g2.draw(new Rectangle2D.Float(8f, 4f, 2.5f, 10f));
                    g2.draw(new Rectangle2D.Float(12f, 6f, 2.5f, 8f));
                }
                case EXPORT -> {
                    g2.drawLine(8, 2, 8, 10);
                    Path2D arrow = new Path2D.Float();
                    arrow.moveTo(5, 5);
                    arrow.lineTo(8, 2);
                    arrow.lineTo(11, 5);
                    g2.draw(arrow);
                    g2.drawLine(2, 9, 2, 14);
                    g2.drawLine(2, 14, 14, 14);
                    g2.drawLine(14, 14, 14, 9);
                }
                case KEY -> {
                    g2.draw(new Ellipse2D.Float(2f, 2f, 6f, 6f));
                    drawLine(g2, 6.5f, 6.5f, 14f, 14f);
                    g2.drawLine(12, 12, 14, 10);
                    g2.drawLine(10, 10, 12, 8);
                }
                case LINK -> {
                    g2.draw(new RoundRectangle2D.Float(2f, 6f, 7f, 4f, 3, 3));
                    g2.draw(new RoundRectangle2D.Float(7f, 6f, 7f, 4f, 3, 3));
                }
                case DETACH -> {
                    // Window frame with arrow escaping to top-right
                    g2.draw(new RoundRectangle2D.Float(1.5f, 4.5f, 9.5f, 9.5f, 2, 2));
                    g2.drawLine(8, 8, 14, 2);
                    g2.drawLine(10, 2, 14, 2);
                    g2.drawLine(14, 2, 14, 6);
                }
                case ATTACH -> {
                    // Window frame with arrow docking back to bottom-left
                    g2.draw(new RoundRectangle2D.Float(1.5f, 4.5f, 9.5f, 9.5f, 2, 2));
                    g2.drawLine(14, 2, 8, 8);
                    g2.drawLine(12, 8, 8, 8);
                    g2.drawLine(8, 4, 8, 8);
                }
            }
        }

        private void drawLine(Graphics2D g2, float x1, float y1, float x2, float y2) {
            g2.draw(new Line2D.Float(x1, y1, x2, y2));
        }

        private void paintCylinder(Graphics2D g2, float x, float y, float w, float h) {
            float topH = h * 0.28f;
            g2.draw(new Ellipse2D.Float(x, y, w, topH));
            g2.drawLine((int)x, (int)(y + topH / 2), (int)x, (int)(y + h - topH / 2));
            g2.drawLine((int)(x + w), (int)(y + topH / 2), (int)(x + w), (int)(y + h - topH / 2));
            Path2D bottomArc = new Path2D.Float();
            bottomArc.append(new Arc2D.Float(x, y + h - topH, w, topH, 180, 180, Arc2D.OPEN), false);
            g2.draw(bottomArc);
        }
    }
}
