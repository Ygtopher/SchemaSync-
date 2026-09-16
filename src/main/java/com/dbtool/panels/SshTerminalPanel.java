package com.dbtool.panels;

import com.dbtool.DatabaseManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class SshTerminalPanel extends JPanel {

    private final DatabaseManager dbManager;
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final List<SshSessionPanel> allSessions = new ArrayList<>();
    private final JPanel emptyPanel = new JPanel(new GridBagLayout());
    private int sessionCounter = 0;

    public SshTerminalPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // Top control bar
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton newTabBtn = new JButton("➕ New Terminal Tab");
        newTabBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        newTabBtn.setToolTipText("Open another SSH terminal session (concurrent connection)");
        newTabBtn.addActionListener(e -> addNewSession());
        topBar.add(newTabBtn);

        JButton redockAllBtn = new JButton("🗖 Re-dock All");
        redockAllBtn.setToolTipText("Bring all detached floating terminals back into tabs");
        redockAllBtn.addActionListener(e -> redockAllSessions());
        topBar.add(redockAllBtn);

        JLabel hintLabel = new JLabel(" | Connect to multiple servers or the same server simultaneously (use 🗗 to detach)");
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        topBar.add(hintLabel);

        add(topBar, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        // Setup empty placeholder view
        setupEmptyPanel();

        // Start with 1 default terminal tab
        addNewSession();
    }

    private void setupEmptyPanel() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel info = new JLabel("All terminal sessions are currently open in detached floating windows.");
        info.setFont(new Font("Segoe UI", Font.BOLD, 13));
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton addBtn = new JButton("➕ Open New Terminal Tab");
        addBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        addBtn.addActionListener(e -> addNewSession());

        JButton redockBtn = new JButton("🗖 Re-dock All Terminals");
        redockBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        redockBtn.addActionListener(e -> redockAllSessions());

        box.add(info);
        box.add(Box.createVerticalStrut(12));
        box.add(addBtn);
        box.add(Box.createVerticalStrut(8));
        box.add(redockBtn);

        emptyPanel.add(box);
    }

    public void addNewSession() {
        sessionCounter++;
        String title = "Terminal " + sessionCounter;
        SshSessionPanel session = new SshSessionPanel(dbManager, this, title);
        allSessions.add(session);

        tabbedPane.addTab(title, session);
        int idx = tabbedPane.indexOfComponent(session);
        tabbedPane.setTabComponentAt(idx, createTabHeader(session, title));
        tabbedPane.setSelectedComponent(session);

        checkEmptyState();
    }

    private JPanel createTabHeader(SshSessionPanel session, String title) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);

        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton detachBtn = new JButton("🗗");
        detachBtn.setToolTipText("Detach to standalone window");
        detachBtn.setMargin(new Insets(0, 3, 0, 3));
        detachBtn.setBorderPainted(false);
        detachBtn.setContentAreaFilled(false);
        detachBtn.setFocusable(false);
        detachBtn.addActionListener(e -> detachSession(session));

        JButton closeBtn = new JButton("✕");
        closeBtn.setToolTipText("Close & disconnect this terminal");
        closeBtn.setMargin(new Insets(0, 3, 0, 3));
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusable(false);
        closeBtn.addActionListener(e -> closeSession(session));

        header.add(lbl);
        header.add(detachBtn);
        header.add(closeBtn);
        return header;
    }

    public void detachSession(SshSessionPanel session) {
        if (session.isDetached()) return;

        tabbedPane.remove(session);

        JFrame frame = new JFrame("SchemaSync Terminal - " + session.getSessionTitle());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(1020, 680);
        frame.setLocationRelativeTo(this);
        frame.getContentPane().add(session, BorderLayout.CENTER);

        session.setDetached(true, frame);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                redockSession(session);
            }
        });

        frame.setVisible(true);
        checkEmptyState();
    }

    public void redockSession(SshSessionPanel session) {
        if (!session.isDetached()) return;

        JFrame frame = session.getDetachedFrame();
        if (frame != null) {
            frame.getContentPane().remove(session);
            frame.dispose();
        }

        session.setDetached(false, null);
        tabbedPane.addTab(session.getSessionTitle(), session);
        int idx = tabbedPane.indexOfComponent(session);
        tabbedPane.setTabComponentAt(idx, createTabHeader(session, session.getSessionTitle()));
        tabbedPane.setSelectedComponent(session);

        checkEmptyState();
        session.revalidate();
        session.repaint();
    }

    public void redockAllSessions() {
        for (SshSessionPanel session : new ArrayList<>(allSessions)) {
            if (session.isDetached()) {
                redockSession(session);
            }
        }
    }

    public void closeSession(SshSessionPanel session) {
        if (session.isConnected()) {
            int ans = JOptionPane.showConfirmDialog(
                    this,
                    "SSH Session '" + session.getSessionTitle() + "' is still active. Disconnect and close?",
                    "Close Terminal",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (ans != JOptionPane.YES_OPTION) return;
        }

        session.disconnect();

        if (session.isDetached()) {
            JFrame frame = session.getDetachedFrame();
            if (frame != null) {
                frame.dispose();
            }
        } else {
            tabbedPane.remove(session);
        }

        allSessions.remove(session);

        if (allSessions.isEmpty()) {
            addNewSession();
        } else {
            checkEmptyState();
        }
    }

    public void updateSessionTitle(SshSessionPanel session, String newTitle) {
        session.setSessionTitle(newTitle);
        int idx = tabbedPane.indexOfComponent(session);
        if (idx != -1) {
            tabbedPane.setTabComponentAt(idx, createTabHeader(session, newTitle));
        }
        if (session.isDetached() && session.getDetachedFrame() != null) {
            session.getDetachedFrame().setTitle("SchemaSync Terminal - " + newTitle);
        }
    }

    private void checkEmptyState() {
        if (tabbedPane.getTabCount() == 0) {
            remove(tabbedPane);
            add(emptyPanel, BorderLayout.CENTER);
        } else {
            remove(emptyPanel);
            add(tabbedPane, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (allSessions != null) {
            for (SshSessionPanel s : allSessions) {
                s.updateUI();
            }
        }
    }
}
