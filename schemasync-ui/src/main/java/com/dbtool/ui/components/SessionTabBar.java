package com.dbtool.ui.components;

import com.dbtool.Main;
import com.dbtool.session.DatabaseSession;
import com.dbtool.session.SessionListener;
import com.dbtool.session.SessionManager;
import com.dbtool.util.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Modern, interactive horizontal tab bar for multi-connection database workspaces.
 * Supports switching between connected databases with zero latency, detaching databases
 * into separate floating windows (↗ Detach), and connecting additional databases (+ Connect).
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 */
public class SessionTabBar extends JPanel implements SessionListener {

    private final Main main;
    private final JPanel tabsContainer;

    public SessionTabBar(Main main) {
        this.main = main;
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);

        tabsContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabsContainer.setOpaque(false);
        add(tabsContainer, BorderLayout.CENTER);

        SessionManager.getInstance().addListener(this);
        rebuildTabs();
    }

    public void rebuildTabs() {
        tabsContainer.removeAll();

        List<DatabaseSession> sessions = SessionManager.getInstance().getAllSessions();
        DatabaseSession activeSession = SessionManager.getInstance().getActiveSession();

        if (sessions.isEmpty()) {
            // Empty state: clickable "No Database Connected" pill
            JPanel emptyPill = createEmptyPill();
            tabsContainer.add(emptyPill);
        } else {
            for (DatabaseSession session : sessions) {
                boolean isActive = (session == activeSession);
                JPanel tabPill = createSessionTabPill(session, isActive);
                tabsContainer.add(tabPill);
            }

            // Quick "+ Connect" button to open additional connections
            JButton addConnBtn = createAddConnectionButton();
            tabsContainer.add(addConnBtn);
        }

        tabsContainer.revalidate();
        tabsContainer.repaint();
        revalidate();
        repaint();
    }

    private JPanel createEmptyPill() {
        JPanel pill = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean dark = ThemeManager.isDarkMode();
                    g2.setColor(dark ? new Color(28, 35, 48) : new Color(236, 242, 249));
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                    g2.setColor(dark ? new Color(48, 60, 82) : new Color(203, 213, 225));
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 16, 16));
                } finally {
                    g2.dispose();
                }
            }
        };
        pill.setOpaque(false);
        pill.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pill.setToolTipText("No Database Connected. Click to connect or manage connection profiles.");
        pill.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (main != null) {
                    main.connectExistingDatabase();
                }
            }
        });

        JLabel dot = new JLabel("○");
        dot.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        dot.setForeground(new Color(148, 163, 184));
        pill.add(dot);

        JLabel text = new JLabel("No Database Connected");
        text.setFont(ThemeManager.getUiFont(Font.PLAIN, 11.5f));
        pill.add(text);

        return pill;
    }

    private JPanel createSessionTabPill(DatabaseSession session, boolean isActive) {
        boolean isDetached = session.isDetached();

        JPanel pill = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean dark = ThemeManager.isDarkMode();

                    if (isActive && !isDetached) {
                        // Active tab highlight
                        g2.setColor(dark ? new Color(30, 48, 77) : new Color(224, 238, 255));
                        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                        g2.setColor(ThemeManager.getPrimaryActionColor());
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, 16, 16));
                    } else if (isDetached) {
                        // Detached floating state
                        g2.setColor(dark ? new Color(25, 30, 40) : new Color(241, 245, 249));
                        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                        g2.setColor(dark ? new Color(75, 85, 99) : new Color(203, 213, 225));
                        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f));
                        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 16, 16));
                    } else {
                        // Inactive tab
                        g2.setColor(dark ? new Color(28, 35, 48) : new Color(236, 242, 249));
                        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                        g2.setColor(dark ? new Color(48, 60, 82) : new Color(203, 213, 225));
                        g2.setStroke(new BasicStroke(1f));
                        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 16, 16));
                    }
                } finally {
                    g2.dispose();
                }
            }
        };

        pill.setOpaque(false);
        pill.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 6));
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Tooltip
        String tooltip = isDetached
            ? "Detached Workspace: " + session.getName() + " (Running in separate floating window - click to focus)"
            : "Database Session: " + session.getName() + " (" + session.getDbType() + ") - Click to switch";
        pill.setToolTipText(tooltip);

        // Click on tab switches active session or brings detached window forward
        pill.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isDetached) {
                    if (session.getDetachedWindow() != null) {
                        session.getDetachedWindow().toFront();
                        session.getDetachedWindow().requestFocus();
                    }
                } else {
                    if (main != null) {
                        main.activateSession(session);
                    }
                }
            }
        });

        // 1. Connection status dot
        JLabel statusDot = new JLabel("●");
        statusDot.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        statusDot.setForeground(isDetached ? new Color(148, 163, 184) : ThemeManager.getSuccessColor());
        pill.add(statusDot);

        // 2. Engine icon
        JLabel engineIcon = new JLabel(UiIconFactory.getIcon(UiIconFactory.IconType.DATABASE, 13,
            isActive ? ThemeManager.getPrimaryActionColor() : session.getBadgeColor()));
        pill.add(engineIcon);

        // 3. Name label
        JLabel nameLabel = new JLabel(session.getName());
        nameLabel.setFont(ThemeManager.getUiFont(isActive && !isDetached ? Font.BOLD : Font.PLAIN, 11.5f));
        if (isDetached) {
            nameLabel.setForeground(ThemeManager.getMutedTextColor());
        }
        pill.add(nameLabel);

        // 4. Detached badge OR Detach button
        if (isDetached) {
            JLabel detachedBadge = new JLabel(" [↗ Detached] ");
            detachedBadge.setFont(ThemeManager.getUiFont(Font.ITALIC, 10f));
            detachedBadge.setForeground(ThemeManager.getPrimaryActionColor());
            pill.add(detachedBadge);

            // Re-attach quick button on detached tab pill
            JButton reattachBtn = new JButton("↙");
            reattachBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            reattachBtn.setForeground(ThemeManager.getPrimaryActionColor());
            reattachBtn.setContentAreaFilled(false);
            reattachBtn.setBorderPainted(false);
            reattachBtn.setFocusPainted(false);
            reattachBtn.setMargin(new Insets(0, 2, 0, 2));
            reattachBtn.setToolTipText("Re-attach " + session.getName() + " back to Main Window");
            reattachBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            reattachBtn.addActionListener(e -> SessionManager.getInstance().reattachSession(session));
            pill.add(reattachBtn);
        } else {
            // Detach button (↗)
            JButton detachBtn = new JButton("↗");
            detachBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            detachBtn.setForeground(ThemeManager.isDarkMode() ? new Color(148, 163, 184) : new Color(100, 116, 139));
            detachBtn.setContentAreaFilled(false);
            detachBtn.setBorderPainted(false);
            detachBtn.setFocusPainted(false);
            detachBtn.setMargin(new Insets(0, 2, 0, 2));
            detachBtn.setToolTipText("Detach '" + session.getName() + "' into an independent floating window (Multi-monitor friendly)");
            detachBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            detachBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    detachBtn.setForeground(ThemeManager.getPrimaryActionColor());
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    detachBtn.setForeground(ThemeManager.isDarkMode() ? new Color(148, 163, 184) : new Color(100, 116, 139));
                }
            });
            detachBtn.addActionListener(e -> {
                if (main != null) {
                    main.detachSession(session);
                }
            });
            pill.add(detachBtn);
        }

        // 5. Close / Disconnect button (✕)
        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        closeBtn.setForeground(new Color(239, 68, 68));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setMargin(new Insets(0, 2, 0, 2));
        closeBtn.setToolTipText("Disconnect '" + session.getName() + "'");
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                main,
                "Disconnect from " + session.getName() + "?\nActive queries in this session will be terminated.",
                "Disconnect Database Session",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                if (main != null) {
                    main.closeSession(session);
                } else {
                    SessionManager.getInstance().closeSession(session);
                }
            }
        });
        pill.add(closeBtn);

        return pill;
    }

    private JButton createAddConnectionButton() {
        JButton btn = new JButton("+ Connect") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean dark = ThemeManager.isDarkMode();
                    g2.setColor(dark ? new Color(30, 41, 59) : new Color(241, 245, 249));
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                    g2.setColor(dark ? new Color(51, 65, 85) : new Color(203, 213, 225));
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 16, 16));
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };

        btn.setFont(ThemeManager.getUiFont(Font.BOLD, 11f));
        btn.setForeground(ThemeManager.getPrimaryActionColor());
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(2, 8, 2, 8));
        btn.setToolTipText("Connect another database simultaneously in a new workspace tab");
        btn.addActionListener(e -> {
            if (main != null) {
                main.connectNewDatabaseSession();
            }
        });

        return btn;
    }

    // --- SessionListener Callbacks ---

    @Override
    public void onSessionAdded(DatabaseSession session) {
        SwingUtilities.invokeLater(this::rebuildTabs);
    }

    @Override
    public void onSessionActivated(DatabaseSession session) {
        SwingUtilities.invokeLater(this::rebuildTabs);
    }

    @Override
    public void onSessionClosed(DatabaseSession session) {
        SwingUtilities.invokeLater(this::rebuildTabs);
    }

    @Override
    public void onSessionDetached(DatabaseSession session, Main detachedWindow) {
        SwingUtilities.invokeLater(this::rebuildTabs);
    }

    @Override
    public void onSessionReattached(DatabaseSession session) {
        SwingUtilities.invokeLater(this::rebuildTabs);
    }
}
