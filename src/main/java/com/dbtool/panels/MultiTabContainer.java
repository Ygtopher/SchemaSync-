package com.dbtool.panels;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MultiTabContainer<T extends JPanel> extends JPanel {
    private JTabbedPane tabbedPane = new JTabbedPane();
    private Supplier<T> tabFactory;
    private String baseTitle;
    private int tabCount = 1;
    private List<T> tabs = new ArrayList<>();

    public MultiTabContainer(String baseTitle, Supplier<T> tabFactory) {
        this.baseTitle = baseTitle;
        this.tabFactory = tabFactory;
        setLayout(new BorderLayout());
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("+ New " + baseTitle);
        addBtn.addActionListener(e -> addNewTab());
        topPanel.add(addBtn);
        
        add(topPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        
        addNewTab(); // Start with one tab
    }

    public T addNewTabAndGet() {
        addNewTab();
        return tabs.get(tabs.size() - 1);
    }
    
    public void addNewTab() {
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
        JButton closeBtn = new JButton("x");
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setBorder(BorderFactory.createEmptyBorder());
        closeBtn.setFocusPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setOpaque(false);
        closeBtn.setToolTipText("Close tab");
        
        closeBtn.addActionListener(e -> {
            if (tabbedPane.getTabCount() > 1) {
                tabbedPane.remove(tabPanel);
                tabs.remove(newPanel);
            }
        });
        
        closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                closeBtn.setForeground(Color.RED);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                closeBtn.setForeground(UIManager.getColor("Button.foreground"));
            }
        });
        
        headerPanel.add(titleLabel);
        headerPanel.add(closeBtn);
        
        tabbedPane.setTabComponentAt(index, headerPanel);
        tabbedPane.setSelectedIndex(index);
    }
    
    public List<T> getTabs() {
        return tabs;
    }
}
