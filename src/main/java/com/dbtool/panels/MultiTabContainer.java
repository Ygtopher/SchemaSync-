package com.dbtool.panels;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MultiTabContainer<T extends JPanel> extends JPanel {
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JPanel centerCardPanel = new JPanel(new CardLayout());
    private final JPanel emptyStatePanel = new JPanel(new GridBagLayout());
    private final Supplier<T> tabFactory;
    private final String baseTitle;
    private int tabCount = 1;
    private final List<T> tabs = new ArrayList<>();
    private final JButton closeAllBtn;

    private static final String CARD_TABS = "TABS";
    private static final String CARD_EMPTY = "EMPTY";

    public MultiTabContainer(String baseTitle, Supplier<T> tabFactory) {
        this.baseTitle = baseTitle;
        this.tabFactory = tabFactory;
        setLayout(new BorderLayout());

        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");

        JPanel topPanel = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 2));
        JButton addBtn = new JButton("+ New " + baseTitle);
        addBtn.addActionListener(e -> addNewTab());
        topPanel.add(addBtn);

        closeAllBtn = new JButton("✕ Close All");
        closeAllBtn.setToolTipText("Close all open " + baseTitle + " tabs");
        closeAllBtn.addActionListener(e -> clearAllTabs());
        topPanel.add(closeAllBtn);

        buildEmptyStatePanel();
        centerCardPanel.add(tabbedPane, CARD_TABS);
        centerCardPanel.add(emptyStatePanel, CARD_EMPTY);

        add(topPanel, BorderLayout.NORTH);
        add(centerCardPanel, BorderLayout.CENTER);

        addNewTab(); // Start with one tab
    }

    private void buildEmptyStatePanel() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);

        JLabel msgLabel = new JLabel("No open " + baseTitle + " tabs.");
        msgLabel.setFont(ThemeManager.getUiFont(Font.BOLD, 14));
        msgLabel.setForeground(ThemeManager.getMutedTextColor());
        msgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton openNewBtn = new JButton("+ New " + baseTitle + " Tab");
        openNewBtn.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        openNewBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        openNewBtn.addActionListener(e -> addNewTab());

        box.add(msgLabel);
        box.add(Box.createVerticalStrut(10));
        box.add(openNewBtn);

        emptyStatePanel.add(box);
    }

    public T addNewTabAndGet() {
        addNewTab();
        return tabs.get(tabs.size() - 1);
    }

    public void addNewTab() {
        if (tabs.isEmpty()) {
            tabCount = 1;
        }
        T newPanel = tabFactory.get();
        tabs.add(newPanel);
        String title = baseTitle + " " + (tabCount++);

        JPanel tabPanel = new JPanel(new BorderLayout());
        tabPanel.add(newPanel, BorderLayout.CENTER);

        tabbedPane.addTab(title, tabPanel);
        int index = tabbedPane.indexOfComponent(tabPanel);

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel(title + "  ");
        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        closeBtn.setFocusPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setOpaque(false);
        closeBtn.setToolTipText("Close this " + baseTitle + " tab");

        closeBtn.addActionListener(e -> closeTab(newPanel));

        closeBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent evt) {
                closeBtn.setForeground(Color.RED);
            }
            @Override
            public void mouseExited(MouseEvent evt) {
                closeBtn.setForeground(UIManager.getColor("Button.foreground"));
            }
        });

        // Right-click context menu on tab header (Close, Close Others, Close All)
        JPopupMenu tabMenu = new JPopupMenu();
        JMenuItem mClose = new JMenuItem("Close Tab");
        mClose.addActionListener(e -> closeTab(newPanel));
        JMenuItem mCloseOthers = new JMenuItem("Close Other Tabs");
        mCloseOthers.addActionListener(e -> closeOtherTabs(newPanel));
        JMenuItem mCloseAll = new JMenuItem("Close All Tabs");
        mCloseAll.addActionListener(e -> clearAllTabs());
        tabMenu.add(mClose);
        tabMenu.add(mCloseOthers);
        tabMenu.addSeparator();
        tabMenu.add(mCloseAll);

        MouseAdapter popupTrigger = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    closeTab(newPanel);
                } else if (e.isPopupTrigger()) {
                    tabMenu.show(e.getComponent(), e.getX(), e.getY());
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    int idx = tabbedPane.indexOfComponent(tabPanel);
                    if (idx >= 0) tabbedPane.setSelectedIndex(idx);
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    tabMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        headerPanel.addMouseListener(popupTrigger);
        titleLabel.addMouseListener(popupTrigger);

        headerPanel.add(titleLabel);
        headerPanel.add(closeBtn);

        tabbedPane.setTabComponentAt(index, headerPanel);
        tabbedPane.setSelectedIndex(index);
        updateEmptyState();
    }

    public void closeTab(T panel) {
        int idx = tabs.indexOf(panel);
        if (idx >= 0) {
            tabs.remove(idx);
            if (idx < tabbedPane.getTabCount()) {
                tabbedPane.removeTabAt(idx);
            }
            if (tabs.isEmpty()) {
                tabCount = 1;
            }
            updateEmptyState();
        }
    }

    public void closeTabAt(int index) {
        if (index >= 0 && index < tabs.size()) {
            closeTab(tabs.get(index));
        }
    }

    public void closeOtherTabs(T keepPanel) {
        for (T p : new ArrayList<>(tabs)) {
            if (p != keepPanel) {
                closeTab(p);
            }
        }
    }

    private void updateEmptyState() {
        CardLayout cl = (CardLayout) centerCardPanel.getLayout();
        if (tabs.isEmpty()) {
            cl.show(centerCardPanel, CARD_EMPTY);
            if (closeAllBtn != null) closeAllBtn.setEnabled(false);
        } else {
            cl.show(centerCardPanel, CARD_TABS);
            if (closeAllBtn != null) closeAllBtn.setEnabled(true);
        }
        centerCardPanel.revalidate();
        centerCardPanel.repaint();
    }

    public List<T> getTabs() {
        return tabs;
    }

    public T getActiveTab() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx >= 0 && idx < tabs.size()) {
            return tabs.get(idx);
        }
        return tabs.isEmpty() ? null : tabs.get(0);
    }

    public void clearAllTabs() {
        tabbedPane.removeAll();
        tabs.clear();
        tabCount = 1;
        updateEmptyState();
    }
}
