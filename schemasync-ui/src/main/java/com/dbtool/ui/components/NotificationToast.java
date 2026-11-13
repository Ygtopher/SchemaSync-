package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Modern floating glassmorphic notification toast for SchemaSync.
 * Renders non-intrusive feedback badges that smoothly appear and dismiss.
 */
public class NotificationToast {

    public enum Type {
        SUCCESS("✔", new Color(16, 185, 129)),
        INFO("ℹ", new Color(56, 189, 248)),
        WARNING("⚠", new Color(245, 158, 11)),
        ERROR("✕", new Color(239, 68, 68));

        private final String symbol;
        private final Color accentColor;

        Type(String symbol, Color accentColor) {
            this.symbol = symbol;
            this.accentColor = accentColor;
        }

        public String getSymbol() {
            return symbol;
        }

        public Color getAccentColor() {
            return accentColor;
        }
    }

    public static void show(Component parent, String message) {
        show(parent, message, Type.INFO);
    }

    public static void showSuccess(Component parent, String message) {
        show(parent, message, Type.SUCCESS);
    }

    public static void showWarning(Component parent, String message) {
        show(parent, message, Type.WARNING);
    }

    public static void showError(Component parent, String message) {
        show(parent, message, Type.ERROR);
    }

    public static void show(Component parent, String message, Type type) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(parent, message, type));
            return;
        }

        Window window = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        if (window == null || !window.isShowing()) return;

        JWindow toastWindow = new JWindow(window);
        toastWindow.setBackground(new Color(0, 0, 0, 0));

        JPanel panel = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean dark = ThemeManager.isDarkMode();
                    Color bg = dark ? new Color(24, 30, 42, 240) : new Color(255, 255, 255, 245);
                    Color border = dark ? new Color(60, 75, 100) : new Color(203, 213, 225);

                    RoundRectangle2D rect = new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
                    g2.setColor(bg);
                    g2.fill(rect);
                    g2.setColor(border);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.draw(rect);

                    // Accent pill indicator on the left
                    g2.setColor(type.getAccentColor());
                    g2.fill(new RoundRectangle2D.Float(3, 3, 4, getHeight() - 6, 4, 4));
                } finally {
                    g2.dispose();
                }
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(8, 16, 8, 14));

        JLabel iconLabel = new JLabel(type.getSymbol());
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        iconLabel.setForeground(type.getAccentColor());
        panel.add(iconLabel, BorderLayout.WEST);

        JLabel msgLabel = new JLabel("<html>" + message + "</html>");
        msgLabel.setFont(ThemeManager.getUiFont(Font.PLAIN, 12));
        boolean dark = ThemeManager.isDarkMode();
        msgLabel.setForeground(dark ? new Color(241, 245, 249) : new Color(15, 23, 42));
        panel.add(msgLabel, BorderLayout.CENTER);

        toastWindow.setContentPane(panel);
        toastWindow.pack();

        // Position at bottom-right of parent window
        Point winLoc = window.getLocationOnScreen();
        int x = winLoc.x + window.getWidth() - toastWindow.getWidth() - 24;
        int y = winLoc.y + window.getHeight() - toastWindow.getHeight() - 40;
        toastWindow.setLocation(Math.max(winLoc.x, x), Math.max(winLoc.y, y));

        toastWindow.setVisible(true);

        // Auto-dismiss timer
        Timer dismissTimer = new Timer(3200, e -> {
            toastWindow.setVisible(false);
            toastWindow.dispose();
        });
        dismissTimer.setRepeats(false);
        dismissTimer.start();

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dismissTimer.stop();
                toastWindow.setVisible(false);
                toastWindow.dispose();
            }
        });
    }
}
