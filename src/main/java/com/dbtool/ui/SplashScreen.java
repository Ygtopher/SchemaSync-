package com.dbtool.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;

/**
 * Animated startup splash screen displaying the SchemaSync logo,
 * "Made by NSENGIYUMVA Christophe", and an animated progress bar.
 */
public class SplashScreen extends JWindow {
    private float opacity = 0.0f;
    private int progress = 0; // 0 to 100
    private String statusText = "Loading components & themes...";
    private Image logoImage;
    private Runnable onComplete;
    private Timer animTimer;
    private boolean finished = false;

    public SplashScreen(Runnable onComplete) {
        this.onComplete = onComplete;
        setSize(480, 310);
        setLocationRelativeTo(null);
        try {
            setBackground(new Color(0, 0, 0, 0));
        } catch (Exception ignored) {
            setBackground(new Color(13, 18, 30));
        }

        // Load application icon logo
        URL iconUrl = getClass().getResource("/icons/app-icon.png");
        if (iconUrl != null) {
            logoImage = new ImageIcon(iconUrl).getImage();
        }

        // Click to skip splash screen immediately
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                finishAndLaunch();
            }
        });

        // Content panel with custom graphics
        setContentPane(new JPanel() {
            {
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int w = getWidth();
                int h = getHeight();

                // 1. Dark Card background with smooth rounded corners
                RoundRectangle2D card = new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 28, 28);
                GradientPaint bg = new GradientPaint(0, 0, new Color(13, 18, 30), 0, h, new Color(9, 12, 22));
                g2.setPaint(bg);
                g2.fill(card);

                // 2. Glowing cyber border (Emerald Green to Cyan)
                g2.setStroke(new BasicStroke(2f));
                GradientPaint border = new GradientPaint(0, 0, new Color(16, 185, 129, 210), w, h, new Color(6, 182, 212, 210));
                g2.setPaint(border);
                g2.draw(card);

                // 3. Logo (Geometric Hexagonal Data Core, centered)
                int logoSize = 92;
                int logoX = (w - logoSize) / 2;
                int logoY = 30;
                if (logoImage != null) {
                    g2.drawImage(logoImage, logoX, logoY, logoSize, logoSize, null);
                }

                // 4. Title: SchemaSync
                g2.setFont(new Font("Segoe UI", Font.BOLD, 24));
                FontMetrics fmTitle = g2.getFontMetrics();
                String title = "SchemaSync";
                int titleX = (w - fmTitle.stringWidth(title)) / 2;
                int titleY = logoY + logoSize + 32;
                g2.setColor(Color.WHITE);
                g2.drawString(title, titleX, titleY);

                // 5. Creator Credit: Made by NSENGIYUMVA Christophe
                g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
                FontMetrics fmCreator = g2.getFontMetrics();
                String creator = "Made by NSENGIYUMVA Christophe";
                int creatorX = (w - fmCreator.stringWidth(creator)) / 2;
                int creatorY = titleY + 24;
                g2.setColor(new Color(16, 185, 129)); // Glowing emerald
                g2.drawString(creator, creatorX, creatorY);

                // 6. Status text
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                FontMetrics fmStatus = g2.getFontMetrics();
                int statusX = (w - fmStatus.stringWidth(statusText)) / 2;
                int statusY = creatorY + 26;
                g2.setColor(new Color(148, 163, 184)); // Slate gray
                g2.drawString(statusText, statusX, statusY);

                // 7. Animated Neon Progress Bar
                int barMargin = 36;
                int barY = h - 20;
                int barW = w - (barMargin * 2);
                int barH = 4;

                // Track background
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(barMargin, barY, barW, barH, 4, 4);

                // Animated filling
                int fillW = (int) (barW * (progress / 100.0));
                if (fillW > 0) {
                    GradientPaint barGrad = new GradientPaint(barMargin, 0, new Color(6, 182, 212), barMargin + barW, 0, new Color(16, 185, 129));
                    g2.setPaint(barGrad);
                    g2.fillRoundRect(barMargin, barY, fillW, barH, 4, 4);
                }

                g2.dispose();
            }
        });
    }

    public void start() {
        setVisible(true);
        try {
            setOpacity(0.0f);
        } catch (Exception ignored) {}

        final long startTime = System.currentTimeMillis();
        final int totalDuration = 2100; // ~2.1 seconds for smooth professional intro

        animTimer = new Timer(16, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            float ratio = Math.min(1.0f, (float) elapsed / totalDuration);

            // Fade-in during first 350ms, fade-out during last 300ms
            if (elapsed < 350) {
                opacity = elapsed / 350.0f;
            } else if (elapsed > totalDuration - 300) {
                opacity = Math.max(0.0f, (totalDuration - elapsed) / 300.0f);
            } else {
                opacity = 1.0f;
            }

            try {
                setOpacity(Math.min(1.0f, Math.max(0.0f, opacity)));
            } catch (Exception ignored) {}

            progress = (int) (ratio * 100);

            if (ratio < 0.35f) {
                statusText = "Loading components & themes...";
            } else if (ratio < 0.70f) {
                statusText = "Initializing database engine...";
            } else if (ratio < 0.90f) {
                statusText = "Preparing workspace...";
            } else {
                statusText = "Ready!";
            }

            repaint();

            if (elapsed >= totalDuration) {
                finishAndLaunch();
            }
        });
        animTimer.start();
    }

    private void finishAndLaunch() {
        if (finished) return;
        finished = true;
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
        dispose();
        if (onComplete != null) {
            SwingUtilities.invokeLater(onComplete);
            onComplete = null;
        }
    }
}
