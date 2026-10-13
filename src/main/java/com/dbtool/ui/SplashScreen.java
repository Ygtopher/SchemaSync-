package com.dbtool.ui;

import com.dbtool.util.ThemeManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;

/**
 * Animated startup splash screen styled to seamlessly match the FlatMac / Windows 11
 * system theme, featuring the application logo, "Made by NSENGIYUMVA Christophe",
 * and a smooth modern progress bar with ambient lighting.
 */
public class SplashScreen extends JWindow {
    private float opacity = 0.0f;
    private int progress = 0; // 0 to 100
    private String statusText = "Loading components & themes...";
    private Image logoImage;
    private Runnable onComplete;
    private Timer animTimer;
    private boolean finished = false;
    private float shimmerOffset = 0.0f;

    public SplashScreen(Runnable onComplete) {
        this.onComplete = onComplete;
        setSize(500, 320);
        setLocationRelativeTo(null);

        try {
            setBackground(new Color(0, 0, 0, 0)); // Translucent window for drop shadow and rounded corners
        } catch (Exception ignored) {
            setBackground(new Color(24, 25, 28));
        }

        // Load application icon logo
        URL iconUrl = getClass().getResource("/icons/app-icon.png");
        if (iconUrl != null) {
            logoImage = new ImageIcon(iconUrl).getImage();
        }

        // Click or press any key to skip splash immediately
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                finishAndLaunch();
            }
        });

        // Content panel matching system FlatMac theme
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
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

                int w = getWidth();
                int h = getHeight();

                // Outer margins for soft drop shadow
                int shadowMargin = 12;
                int cardX = shadowMargin;
                int cardY = shadowMargin;
                int cardW = w - (shadowMargin * 2);
                int cardH = h - (shadowMargin * 2);
                int cornerArc = 22; // Windows 11 / FlatMac standard corner radius

                // 1. Soft Ambient Drop Shadow
                for (int i = shadowMargin; i > 0; i--) {
                    int alpha = (int) (18 * (1.0f - (float) i / shadowMargin));
                    g2.setColor(new Color(0, 0, 0, Math.max(0, alpha)));
                    g2.fill(new RoundRectangle2D.Double(cardX - i, cardY - i + 2, cardW + (i * 2), cardH + (i * 2), cornerArc + i, cornerArc + i));
                }

                // 2. Card Background (FlatMac Dark surface gradient)
                RoundRectangle2D card = new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, cornerArc, cornerArc);
                boolean isDark = ThemeManager.isDarkMode();
                
                Color bgTop = isDark ? new Color(34, 36, 41) : new Color(255, 255, 255);
                Color bgBottom = isDark ? new Color(24, 25, 29) : new Color(244, 245, 248);
                GradientPaint cardBg = new GradientPaint(0, cardY, bgTop, 0, cardY + cardH, bgBottom);
                g2.setPaint(cardBg);
                g2.fill(card);

                // 3. Subtle System Border (matching FlatMac / Win11 window border)
                Color borderColor = isDark ? new Color(65, 68, 77, 180) : new Color(210, 214, 222, 220);
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(borderColor);
                g2.draw(card);

                // 4. Logo (Centered, optimal 80x80 crisp display)
                int logoSize = 82;
                int logoX = (w - logoSize) / 2;
                int logoY = cardY + 24;
                if (logoImage != null) {
                    g2.drawImage(logoImage, logoX, logoY, logoSize, logoSize, null);
                }

                // 5. Title: "SchemaSync" (System Segoe UI Bold)
                g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
                FontMetrics fmTitle = g2.getFontMetrics();
                String title = "SchemaSync";
                int titleX = (w - fmTitle.stringWidth(title)) / 2;
                int titleY = logoY + logoSize + 28;
                g2.setColor(isDark ? new Color(245, 247, 250) : new Color(24, 26, 30));
                g2.drawString(title, titleX, titleY);

                // 6. Creator Credit: "Made by NSENGIYUMVA Christophe"
                // Styled with a subtle badge-like background to look integrated into modern OS design
                String creatorText = "Made by NSENGIYUMVA Christophe";
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fmCreator = g2.getFontMetrics();
                int creatorW = fmCreator.stringWidth(creatorText);
                int creatorX = (w - creatorW) / 2;
                int creatorY = titleY + 22;

                // Subtle capsule backing behind creator name
                int pillPadX = 12;
                int pillPadY = 4;
                int pillX = creatorX - pillPadX;
                int pillY = creatorY - fmCreator.getAscent() - pillPadY / 2;
                int pillW = creatorW + (pillPadX * 2);
                int pillH = fmCreator.getHeight() + pillPadY;

                Color pillBg = isDark ? new Color(16, 185, 129, 28) : new Color(16, 185, 129, 35);
                Color pillBorder = isDark ? new Color(16, 185, 129, 70) : new Color(16, 185, 129, 90);
                g2.setColor(pillBg);
                g2.fillRoundRect(pillX, pillY, pillW, pillH, 12, 12);
                g2.setColor(pillBorder);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(pillX, pillY, pillW, pillH, 12, 12);

                // Text inside pill
                Color creatorColor = isDark ? new Color(52, 211, 153) : new Color(5, 150, 105);
                g2.setColor(creatorColor);
                g2.drawString(creatorText, creatorX, creatorY);

                // 7. Status Text (Secondary muted typography)
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                FontMetrics fmStatus = g2.getFontMetrics();
                int statusX = (w - fmStatus.stringWidth(statusText)) / 2;
                int statusY = creatorY + 26;
                g2.setColor(isDark ? new Color(148, 163, 184) : new Color(100, 116, 139));
                g2.drawString(statusText, statusX, statusY);

                // 8. Modern Progress Bar (Fluent Rounded Track + Smooth Gradient Shimmer)
                int barMargin = cardX + 36;
                int barY = cardY + cardH - 24;
                int barW = cardW - 72;
                int barH = 4;

                // Track
                Color trackColor = isDark ? new Color(42, 45, 52) : new Color(226, 232, 240);
                g2.setColor(trackColor);
                g2.fillRoundRect(barMargin, barY, barW, barH, 4, 4);

                // Animated filling with Fluent cyan-to-emerald gradient
                int fillW = (int) (barW * (progress / 100.0));
                if (fillW > 0) {
                    GradientPaint barGrad = new GradientPaint(
                            barMargin, 0, new Color(14, 165, 233),
                            barMargin + barW, 0, new Color(16, 185, 129)
                    );
                    g2.setPaint(barGrad);
                    g2.fillRoundRect(barMargin, barY, fillW, barH, 4, 4);

                    // Shimmer highlight sweeping across
                    int shimmerW = 40;
                    int sX = (int) (barMargin + (shimmerOffset * (barW - shimmerW)));
                    if (sX + shimmerW <= barMargin + fillW) {
                        GradientPaint shimmer = new GradientPaint(
                                sX, 0, new Color(255, 255, 255, 0),
                                sX + (shimmerW / 2), 0, new Color(255, 255, 255, 140),
                                true
                        );
                        g2.setPaint(shimmer);
                        g2.fillRoundRect(sX, barY, shimmerW, barH, 4, 4);
                    }
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
        final int totalDuration = 2000; // 2 seconds optimal snappy launch duration

        animTimer = new Timer(16, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            float ratio = Math.min(1.0f, (float) elapsed / totalDuration);

            // Shimmer animation offset (0.0 to 1.0 repeating)
            shimmerOffset = (float) ((elapsed % 1000) / 1000.0);

            // Smooth cubic ease-in-out opacity
            if (elapsed < 300) {
                float t = elapsed / 300.0f;
                opacity = t * t * (3 - 2 * t);
            } else if (elapsed > totalDuration - 280) {
                float t = (totalDuration - elapsed) / 280.0f;
                opacity = Math.max(0.0f, t * t * (3 - 2 * t));
            } else {
                opacity = 1.0f;
            }

            try {
                setOpacity(Math.min(1.0f, Math.max(0.0f, opacity)));
            } catch (Exception ignored) {}

            progress = (int) (ratio * 100);

            if (ratio < 0.30f) {
                statusText = "Loading components & themes...";
            } else if (ratio < 0.65f) {
                statusText = "Initializing database engine...";
            } else if (ratio < 0.88f) {
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
