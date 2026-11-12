package com.dbtool.util;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class VectorIconGenerator {

    public static BufferedImage generateQuantumSyncIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double s = size / 1024.0;

        // 1. Base Squircle / Rounded Tile
        double pad = 64 * s;
        double tileSize = size - 2 * pad;
        double corner = 220 * s;
        RoundRectangle2D tile = new RoundRectangle2D.Double(pad, pad, tileSize, tileSize, corner, corner);

        // Squircle drop shadow
        for (int i = 1; i <= 24; i++) {
            float alpha = (float) (0.02 * (25 - i) / 25.0);
            g2.setColor(new Color(0f, 0f, 0f, alpha));
            RoundRectangle2D shadow = new RoundRectangle2D.Double(
                pad - i * 1.5 * s, pad + i * 2.0 * s, tileSize + i * 3.0 * s, tileSize + i * 3.0 * s, corner + i * 2 * s, corner + i * 2 * s
            );
            g2.fill(shadow);
        }

        // Dark gradient tile
        Paint tilePaint = new LinearGradientPaint(
            (float)(pad), (float)(pad), (float)(pad + tileSize), (float)(pad + tileSize),
            new float[]{0.0f, 0.5f, 1.0f},
            new Color[]{new Color(0x13, 0x18, 0x24), new Color(0x0C, 0x0E, 0x17), new Color(0x07, 0x09, 0x0E)}
        );
        g2.setPaint(tilePaint);
        g2.fill(tile);

        // Tile inner glow / rim
        g2.setStroke(new BasicStroke((float)(4 * s)));
        Paint rimPaint = new LinearGradientPaint(
            (float)pad, (float)pad, (float)(pad + tileSize), (float)(pad + tileSize),
            new float[]{0.0f, 0.4f, 0.8f, 1.0f},
            new Color[]{new Color(0x00, 0xE5, 0xFF, 120), new Color(0x3B, 0x82, 0xF6, 80), new Color(0x8B, 0x5C, 0xF6, 40), new Color(0x00, 0xE5, 0xFF, 90)}
        );
        g2.setPaint(rimPaint);
        g2.draw(tile);

        // Center ambient radial glow
        RadialGradientPaint centerGlow = new RadialGradientPaint(
            (float)(size / 2.0), (float)(size / 2.0), (float)(380 * s),
            new float[]{0.0f, 0.4f, 0.8f, 1.0f},
            new Color[]{new Color(0x00, 0xD2, 0xFF, 55), new Color(0x3B, 0x82, 0xF6, 30), new Color(0x8B, 0x5C, 0xF6, 12), new Color(0, 0, 0, 0)}
        );
        g2.setPaint(centerGlow);
        g2.fill(tile);

        // 2. Central Database Cylinder Stacks
        // Left Database Cylinder
        drawCylinderStack(g2, 360 * s, 470 * s, 140 * s, 64 * s, 56 * s, 3, new Color(0x00, 0xF5, 0xD4), new Color(0x00, 0xBB, 0xF9), s);

        // Right Database Cylinder
        drawCylinderStack(g2, 664 * s, 554 * s, 140 * s, 64 * s, 56 * s, 3, new Color(0x00, 0xBB, 0xF9), new Color(0x72, 0x09, 0xB7), s);

        // 3. Dynamic Curved Sync Arrows
        drawSyncArrow(g2, 370 * s, 360 * s, 530 * s, 260 * s, 660 * s, 440 * s, true, new Color(0x00, 0xF5, 0xD4), new Color(0x00, 0xBB, 0xF9), s);
        drawSyncArrow(g2, 650 * s, 670 * s, 490 * s, 760 * s, 360 * s, 590 * s, false, new Color(0x00, 0xBB, 0xF9), new Color(0x4C, 0xC9, 0xF0), s);

        // 4. Subtle Data Nodes
        drawDataNodes(g2, s);

        g2.dispose();
        return img;
    }

    private static void drawCylinderStack(Graphics2D g2, double cx, double cy, double rx, double ry, double layerH, int layers, Color cTop, Color cBot, double s) {
        for (int i = layers - 1; i >= 0; i--) {
            double yOffset = cy - (i * layerH);

            // Cylinder body
            Path2D body = new Path2D.Double();
            body.moveTo(cx - rx, yOffset);
            body.lineTo(cx - rx, yOffset + layerH * 0.7);
            body.curveTo(cx - rx, yOffset + layerH * 0.7 + ry, cx + rx, yOffset + layerH * 0.7 + ry, cx + rx, yOffset + layerH * 0.7);
            body.lineTo(cx + rx, yOffset);
            body.curveTo(cx + rx, yOffset + ry, cx - rx, yOffset + ry, cx - rx, yOffset);
            body.closePath();

            // Cylinder body gradient
            Paint bodyPaint = new LinearGradientPaint(
                (float)(cx - rx), (float)yOffset, (float)(cx + rx), (float)yOffset,
                new float[]{0.0f, 0.3f, 0.7f, 1.0f},
                new Color[]{new Color(0x1E, 0x29, 0x3B), new Color(0x33, 0x41, 0x55), new Color(0x47, 0x55, 0x69), new Color(0x1E, 0x29, 0x3B)}
            );
            g2.setPaint(bodyPaint);
            g2.fill(body);

            // Glowing data seam ring
            g2.setStroke(new BasicStroke((float)(3.5 * s)));
            g2.setColor(new Color(cTop.getRed(), cTop.getGreen(), cTop.getBlue(), 180));
            Arc2D seam = new Arc2D.Double(cx - rx, yOffset + layerH * 0.7 - ry, rx * 2, ry * 2, 180, 180, Arc2D.OPEN);
            g2.draw(seam);

            // Cylinder Top Cap
            Ellipse2D topCap = new Ellipse2D.Double(cx - rx, yOffset - ry, rx * 2, ry * 2);
            Paint capPaint = new RadialGradientPaint(
                (float)(cx - rx * 0.3), (float)(yOffset - ry * 0.3), (float)(rx * 1.5),
                new float[]{0.0f, 0.6f, 1.0f},
                new Color[]{new Color(0x64, 0x74, 0x8B), new Color(0x33, 0x41, 0x55), new Color(0x1E, 0x29, 0x3B)}
            );
            g2.setPaint(capPaint);
            g2.fill(topCap);

            // Glowing neon rim on top cap
            g2.setStroke(new BasicStroke((float)(4.0 * s)));
            Paint rimPaint = new LinearGradientPaint(
                (float)(cx - rx), (float)yOffset, (float)(cx + rx), (float)yOffset,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{cTop, cBot, cTop}
            );
            g2.setPaint(rimPaint);
            g2.draw(topCap);
        }
    }

    private static void drawSyncArrow(Graphics2D g2, double x1, double y1, double cx, double cy, double x2, double y2, boolean leftToRight, Color c1, Color c2, double s) {
        Path2D arc = new Path2D.Double();
        arc.moveTo(x1, y1);
        arc.quadTo(cx, cy, x2, y2);

        // Glow pass
        g2.setStroke(new BasicStroke((float)(18 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 60));
        g2.draw(arc);

        g2.setStroke(new BasicStroke((float)(10 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 140));
        g2.draw(arc);

        // Main core beam
        g2.setStroke(new BasicStroke((float)(5 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Paint beamPaint = new LinearGradientPaint(
            (float)x1, (float)y1, (float)x2, (float)y2,
            new float[]{0.0f, 1.0f},
            new Color[]{c1, c2}
        );
        g2.setPaint(beamPaint);
        g2.draw(arc);

        // Arrow head
        double angle = Math.atan2(y2 - cy, x2 - cx);
        double arrowLen = 32 * s;
        double arrowWidth = 20 * s;

        Path2D head = new Path2D.Double();
        head.moveTo(x2, y2);
        head.lineTo(x2 - arrowLen * Math.cos(angle) + arrowWidth * Math.sin(angle),
                    y2 - arrowLen * Math.sin(angle) - arrowWidth * Math.cos(angle));
        head.lineTo(x2 - (arrowLen * 0.7) * Math.cos(angle),
                    y2 - (arrowLen * 0.7) * Math.sin(angle));
        head.lineTo(x2 - arrowLen * Math.cos(angle) - arrowWidth * Math.sin(angle),
                    y2 - arrowLen * Math.sin(angle) + arrowWidth * Math.cos(angle));
        head.closePath();

        g2.setColor(c2);
        g2.fill(head);
    }

    private static void drawDataNodes(Graphics2D g2, double s) {
        double[][] points = {
            {512 * s, 320 * s, 6 * s},
            {580 * s, 380 * s, 4 * s},
            {450 * s, 650 * s, 5 * s},
            {520 * s, 710 * s, 7 * s},
            {320 * s, 680 * s, 4 * s},
            {700 * s, 360 * s, 5 * s}
        };

        for (double[] pt : points) {
            double x = pt[0], y = pt[1], r = pt[2];
            g2.setColor(new Color(0, 245, 212, 100));
            g2.fill(new Ellipse2D.Double(x - r * 2, y - r * 2, r * 4, r * 4));
            g2.setColor(new Color(255, 255, 255, 230));
            g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedImage quantumImg = generateQuantumSyncIcon(1024);
        File outDir = new File("C:/Users/CHRISTOPHE/.gemini/antigravity/brain/452c91c6-5676-46e5-9238-c057a8809e4a");
        File outFile = new File(outDir, "schemasync_icon_concept6_quantum_sync.png");
        ImageIO.write(quantumImg, "PNG", outFile);
        System.out.println("Generated: " + outFile.getAbsolutePath());
    }
}
