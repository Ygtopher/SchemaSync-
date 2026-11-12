package com.dbtool.util;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class VectorIconGenerator2 {

    public static BufferedImage generatePrismHexSync(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double s = size / 1024.0;
        double pad = 64 * s;
        double tileSize = size - 2 * pad;
        double corner = 220 * s;

        // Squircle drop shadow
        for (int i = 1; i <= 24; i++) {
            float alpha = (float) (0.02 * (25 - i) / 25.0);
            g2.setColor(new Color(0f, 0f, 0f, alpha));
            RoundRectangle2D shadow = new RoundRectangle2D.Double(
                pad - i * 1.5 * s, pad + i * 2.0 * s, tileSize + i * 3.0 * s, tileSize + i * 3.0 * s, corner + i * 2 * s, corner + i * 2 * s
            );
            g2.fill(shadow);
        }

        // Base tile
        RoundRectangle2D tile = new RoundRectangle2D.Double(pad, pad, tileSize, tileSize, corner, corner);
        Paint tilePaint = new LinearGradientPaint(
            (float)(pad), (float)(pad), (float)(pad + tileSize), (float)(pad + tileSize),
            new float[]{0.0f, 0.5f, 1.0f},
            new Color[]{new Color(0x0F, 0x17, 0x2A), new Color(0x0B, 0x0F, 0x19), new Color(0x03, 0x07, 0x12)}
        );
        g2.setPaint(tilePaint);
        g2.fill(tile);

        // Tile border rim
        g2.setStroke(new BasicStroke((float)(4 * s)));
        Paint rimPaint = new LinearGradientPaint(
            (float)pad, (float)pad, (float)(pad + tileSize), (float)(pad + tileSize),
            new float[]{0.0f, 0.5f, 1.0f},
            new Color[]{new Color(0x06, 0xB6, 0xD4, 150), new Color(0x3B, 0x82, 0xF6, 80), new Color(0x8B, 0x5C, 0xF6, 120)}
        );
        g2.setPaint(rimPaint);
        g2.draw(tile);

        // Center ambient radial glow
        RadialGradientPaint centerGlow = new RadialGradientPaint(
            (float)(size / 2.0), (float)(size / 2.0), (float)(380 * s),
            new float[]{0.0f, 0.4f, 0.8f, 1.0f},
            new Color[]{new Color(0x06, 0xB6, 0xD4, 60), new Color(0x3B, 0x82, 0xF6, 30), new Color(0x8B, 0x5C, 0xF6, 10), new Color(0, 0, 0, 0)}
        );
        g2.setPaint(centerGlow);
        g2.fill(tile);

        // Isometric Hexagon Center
        double cx = size / 2.0;
        double cy = size / 2.0;
        double r = 220 * s;

        // Draw 3 layers of isometric database prism
        double[] yOffsets = {cy + 70 * s, cy, cy - 70 * s};
        for (int l = 0; l < yOffsets.length; l++) {
            double curY = yOffsets[l];
            double h = 55 * s;

            // Top diamond
            Path2D top = new Path2D.Double();
            top.moveTo(cx, curY - r * 0.55);
            top.lineTo(cx + r * 0.95, curY);
            top.lineTo(cx, curY + r * 0.55);
            top.lineTo(cx - r * 0.95, curY);
            top.closePath();

            // Left side
            Path2D leftSide = new Path2D.Double();
            leftSide.moveTo(cx - r * 0.95, curY);
            leftSide.lineTo(cx, curY + r * 0.55);
            leftSide.lineTo(cx, curY + r * 0.55 + h);
            leftSide.lineTo(cx - r * 0.95, curY + h);
            leftSide.closePath();

            // Right side
            Path2D rightSide = new Path2D.Double();
            rightSide.moveTo(cx, curY + r * 0.55);
            rightSide.lineTo(cx + r * 0.95, curY);
            rightSide.lineTo(cx + r * 0.95, curY + h);
            rightSide.lineTo(cx, curY + r * 0.55 + h);
            rightSide.closePath();

            // Fill sides
            g2.setPaint(new LinearGradientPaint((float)(cx - r), (float)curY, (float)cx, (float)(curY + h),
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(0x0E, 0x74, 0x90), new Color(0x16, 0x4E, 0x63)}));
            g2.fill(leftSide);

            g2.setPaint(new LinearGradientPaint((float)cx, (float)curY, (float)(cx + r), (float)(curY + h),
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(0x1D, 0x4E, 0xD8), new Color(0x1E, 0x3A, 0x8A)}));
            g2.fill(rightSide);

            // Fill top
            Paint topPaint;
            if (l == yOffsets.length - 1) {
                topPaint = new LinearGradientPaint((float)(cx - r), (float)(curY - r * 0.5), (float)(cx + r), (float)(curY + r * 0.5),
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{new Color(0x22, 0xD3, 0xEE), new Color(0x38, 0xBD, 0xF8), new Color(0x81, 0x8C, 0xF8)});
            } else {
                topPaint = new LinearGradientPaint((float)(cx - r), (float)(curY - r * 0.5), (float)(cx + r), (float)(curY + r * 0.5),
                    new float[]{0.0f, 1.0f},
                    new Color[]{new Color(0x1E, 0x29, 0x3B), new Color(0x33, 0x41, 0x55)});
            }
            g2.setPaint(topPaint);
            g2.fill(top);

            // Glowing outline
            g2.setStroke(new BasicStroke((float)(3 * s)));
            g2.setColor(new Color(0x38, 0xBD, 0xF8, 160));
            g2.draw(top);
            g2.draw(leftSide);
            g2.draw(rightSide);
        }

        // Orbital Neon Sync Rings around the prism
        drawOrbitalRing(g2, cx, cy, 320 * s, 130 * s, -28, s);

        g2.dispose();
        return img;
    }

    private static void drawOrbitalRing(Graphics2D g2, double cx, double cy, double rx, double ry, double rotDeg, double s) {
        AffineTransform oldTx = g2.getTransform();
        g2.translate(cx, cy);
        g2.rotate(Math.toRadians(rotDeg));

        // Glow pass
        g2.setStroke(new BasicStroke((float)(20 * s)));
        g2.setColor(new Color(6, 182, 212, 50));
        g2.draw(new Ellipse2D.Double(-rx, -ry, rx * 2, ry * 2));

        // Core bright line
        g2.setStroke(new BasicStroke((float)(6 * s)));
        Paint ringPaint = new LinearGradientPaint(
            (float)(-rx), (float)(-ry), (float)(rx), (float)(ry),
            new float[]{0.0f, 0.5f, 1.0f},
            new Color[]{new Color(0x34, 0xD3, 0x99), new Color(0x22, 0xD3, 0xEE), new Color(0xA8, 0x55, 0xF7)}
        );
        g2.setPaint(ringPaint);
        g2.draw(new Ellipse2D.Double(-rx, -ry, rx * 2, ry * 2));

        // Sync Arrow head on ring
        double arrowX = rx * Math.cos(Math.toRadians(45));
        double arrowY = ry * Math.sin(Math.toRadians(45));
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(arrowX + 25 * s, arrowY - 15 * s);
        arrow.lineTo(arrowX - 10 * s, arrowY - 20 * s);
        arrow.lineTo(arrowX - 2 * s, arrowY);
        arrow.lineTo(arrowX - 18 * s, arrowY + 15 * s);
        arrow.closePath();
        g2.setColor(new Color(0x22, 0xD3, 0xEE));
        g2.fill(arrow);

        g2.setTransform(oldTx);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hexImg = generatePrismHexSync(1024);
        File outDir = new File("C:/Users/CHRISTOPHE/.gemini/antigravity/brain/452c91c6-5676-46e5-9238-c057a8809e4a");
        File outFile = new File(outDir, "schemasync_icon_concept7_prism_hex.png");
        ImageIO.write(hexImg, "PNG", outFile);
        System.out.println("Generated: " + outFile.getAbsolutePath());
    }
}
