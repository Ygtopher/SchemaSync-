package com.dbtool.panels;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class WorkspacesPanel extends JPanel {
    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private File workspaceDir;

    public WorkspacesPanel() {
        setLayout(new BorderLayout());
        
        workspaceDir = new File(System.getProperty("user.home"), ".dbtool/workspaces");
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }
        
        rootNode = new DefaultMutableTreeNode("Workspaces");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        
        refreshTree();
        
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshTree());
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topPanel.add(refreshBtn);
        
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        
        // Double click to open feature could be added here or in Main.java
        // tree.addMouseListener(...) 
    }
    
    public void refreshTree() {
        rootNode.removeAllChildren();
        if (workspaceDir.exists() && workspaceDir.isDirectory()) {
            File[] files = workspaceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".json") || file.getName().endsWith(".sql")) {
                        rootNode.add(new DefaultMutableTreeNode(file.getName()));
                    }
                }
            }
        }
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
    
    public JTree getTree() {
        return tree;
    }
    
    public File getWorkspaceDir() {
        return workspaceDir;
    }
}
