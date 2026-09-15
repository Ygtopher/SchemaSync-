package com.dbtool.panels;

import com.jcraft.jsch.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class SshTerminalPanel extends JPanel {

    private final com.dbtool.DatabaseManager dbManager;

    private JComboBox<String> profileDropdown = new JComboBox<>();
    private JTextField hostField = new JTextField(12);
    private JTextField portField = new JTextField("22", 3);
    private JTextField userField = new JTextField(8);
    private JPasswordField passField = new JPasswordField(8);
    private JCheckBox saveProfileCheck = new JCheckBox("Save", true);
    private JCheckBox showPassCheck = new JCheckBox("👁");
    private JButton connectBtn = new JButton("Connect");
    private JButton disconnectBtn = new JButton("Disconnect");

    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    private com.jediterm.terminal.ui.JediTermWidget terminalArea;
    private JTextField commandField = new JTextField();

    private JSch jsch;
    private Session session;
    private ChannelShell shellChannel;
    private ChannelSftp sftpChannel;
    
    private OutputStream shellOut;

    public SshTerminalPanel(com.dbtool.DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // Setup profiles
        java.util.Properties savedProfiles = dbManager.getSavedSshProfiles();
        profileDropdown.setEditable(true);
        JTextField editor = (JTextField) profileDropdown.getEditor().getEditorComponent();
        
        profileDropdown.addItem("-- Select --");
        for (String key : savedProfiles.stringPropertyNames()) {
            profileDropdown.addItem(key);
        }

        editor.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent e) {
                char ch = e.getKeyChar();
                if (Character.isLetterOrDigit(ch) || Character.isSpaceChar(ch) || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    String filter = editor.getText();
                    String filterLower = filter.toLowerCase();
                    profileDropdown.hidePopup();
                    profileDropdown.removeAllItems();
                    profileDropdown.addItem("-- Select --");
                    for (String key : savedProfiles.stringPropertyNames()) {
                        if (key.toLowerCase().contains(filterLower)) {
                            profileDropdown.addItem(key);
                        }
                    }
                    editor.setText(filter);
                    profileDropdown.showPopup();
                }
            }
        });

        profileDropdown.addActionListener(e -> {
            String selected = (String) profileDropdown.getSelectedItem();
            if (selected != null && !selected.equals("-- Select --")) {
                String val = savedProfiles.getProperty(selected);
                if (val != null) {
                    String[] parts = val.split(";");
                    if (parts.length >= 4) {
                        hostField.setText(parts[0]);
                        portField.setText(parts[1]);
                        userField.setText(parts[2]);
                        try {
                            String decodedPass = new String(java.util.Base64.getDecoder().decode(parts[3]));
                            passField.setText(decodedPass);
                        } catch (Exception ex) {}
                    }
                }
            }
        });

        showPassCheck.setToolTipText("Show Password");
        showPassCheck.addActionListener(e -> {
            if (showPassCheck.isSelected()) {
                passField.setEchoChar((char) 0);
            } else {
                passField.setEchoChar('•');
            }
        });

        // Top connection bar
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        topPanel.add(new JLabel("Profile:"));
        profileDropdown.setPreferredSize(new Dimension(130, 24));
        topPanel.add(profileDropdown);
        topPanel.add(new JLabel("Host:"));
        topPanel.add(hostField);
        topPanel.add(new JLabel("Port:"));
        topPanel.add(portField);
        topPanel.add(new JLabel("User:"));
        topPanel.add(userField);
        topPanel.add(new JLabel("Pass:"));
        topPanel.add(passField);
        topPanel.add(showPassCheck);
        topPanel.add(saveProfileCheck);
        topPanel.add(connectBtn);
        topPanel.add(disconnectBtn);
        disconnectBtn.setEnabled(false);



        // Center split pane
        rootNode = new DefaultMutableTreeNode("Not Connected");
        treeModel = new DefaultTreeModel(rootNode);
        fileTree = new JTree(treeModel);
        
        fileTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
            if (node == null) return;
            if (node.getUserObject() instanceof SftpFileNode) {
                SftpFileNode fileNode = (SftpFileNode) node.getUserObject();
                if (fileNode.isDir && node.getChildCount() == 1 && node.getChildAt(0).toString().equals("...")) {
                    loadDirectory(node, fileNode.fullPath);
                }
            }
        });
        
        fileTree.addMouseListener(new java.awt.event.MouseAdapter() {
            private void showPopup(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = fileTree.getClosestRowForLocation(e.getX(), e.getY());
                if (row == -1) return;
                fileTree.setSelectionRow(row);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
                if (node == null || !(node.getUserObject() instanceof SftpFileNode)) return;
                SftpFileNode fileNode = (SftpFileNode) node.getUserObject();
                
                JPopupMenu popup = new JPopupMenu();
                
                if (!fileNode.isDir) {
                    JMenuItem downloadItem = new JMenuItem("Download to PC");
                    downloadItem.addActionListener(evt -> downloadFile(fileNode));
                    popup.add(downloadItem);
                }
                
                JMenuItem deleteItem = new JMenuItem("Delete from Server");
                deleteItem.addActionListener(evt -> deleteFile(fileNode, node));
                popup.add(deleteItem);
                
                JMenuItem refreshItem = new JMenuItem("Refresh Directory");
                refreshItem.addActionListener(evt -> {
                    if (fileNode.isDir) loadDirectory(node, fileNode.fullPath);
                    else {
                        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                        if (parent != null && parent.getUserObject() instanceof SftpFileNode) {
                            loadDirectory(parent, ((SftpFileNode)parent.getUserObject()).fullPath);
                        }
                    }
                });
                popup.add(refreshItem);
                
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) { showPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { showPopup(e); }
        });

        fileTree.setDragEnabled(true);
        fileTree.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
                JTree tree = (JTree) c;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (node == null || !(node.getUserObject() instanceof SftpFileNode)) return null;
                SftpFileNode fileNode = (SftpFileNode) node.getUserObject();
                if (fileNode.isDir) return null; // Skip dragging entire folders for safety

                return new java.awt.datatransfer.Transferable() {
                    @Override
                    public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                        return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.javaFileListFlavor};
                    }

                    @Override
                    public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                        return flavor.equals(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    }

                    @Override
                    public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException, java.io.IOException {
                        if (!isDataFlavorSupported(flavor)) throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                        
                        try {
                            java.io.File tempFile = new java.io.File(System.getProperty("java.io.tmpdir"), fileNode.name);
                            if (sftpChannel != null) {
                                sftpChannel.get(fileNode.fullPath, tempFile.getAbsolutePath());
                            }
                            return java.util.Collections.singletonList(tempFile);
                        } catch (Exception e) {
                            throw new java.io.IOException(e);
                        }
                    }
                };
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) return false;
                if (!support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) return false;
                
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                javax.swing.tree.TreePath path = dl.getPath();
                if (path == null) return false;
                
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (!(node.getUserObject() instanceof SftpFileNode)) return false;
                return ((SftpFileNode) node.getUserObject()).isDir;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();
                SftpFileNode dirNode = (SftpFileNode) node.getUserObject();
                
                try {
                    java.awt.datatransfer.Transferable t = support.getTransferable();
                    java.util.List<java.io.File> files = (java.util.List<java.io.File>) t.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    
                    uploadFiles(files, dirNode, node);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        terminalArea = new com.jediterm.terminal.ui.JediTermWidget(new com.dbtool.panels.terminal.DynamicSettingsProvider());
        
        
        
        
        
        JPanel terminalPanel = new JPanel(new BorderLayout());
        terminalPanel.add(terminalArea, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(fileTree), terminalPanel);
        splitPane.setDividerLocation(250);

        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        connectBtn.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());
    }

    private void connect() {
        String host = hostField.getText();
        String user = userField.getText();
        String pass = new String(passField.getPassword());
        int port = 22;
        try { port = Integer.parseInt(portField.getText()); } catch (Exception ignored) {}

        if (host.isEmpty() || user.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Host and User are required.");
            return;
        }

        connectBtn.setEnabled(false);
        

        int finalPort = port;
        new Thread(() -> {
            try {
                jsch = new JSch();
                session = jsch.getSession(user, host, finalPort);
                session.setPassword(pass);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect(10000);

                // Setup Shell
                shellChannel = (ChannelShell) session.openChannel("shell");
                shellChannel.setPtyType("xterm");
                
                if (saveProfileCheck.isSelected()) {
                    String profileName = user + "@" + host + ":" + finalPort;
                    dbManager.saveSshProfile(profileName, host, String.valueOf(finalPort), user, pass);
                }

                com.dbtool.panels.terminal.JSchTtyConnector connector = new com.dbtool.panels.terminal.JSchTtyConnector(shellChannel);
                terminalArea.setTtyConnector(connector);
                terminalArea.start();

                // Setup SFTP
                sftpChannel = (ChannelSftp) session.openChannel("sftp");
                sftpChannel.connect(5000);
                
                SwingUtilities.invokeLater(() -> {
                    disconnectBtn.setEnabled(true);
                    rootNode.setUserObject(new SftpFileNode("Root (/)", "/", true));
                    treeModel.nodeChanged(rootNode);
                    loadDirectory(rootNode, "/");
                    terminalArea.requestFocus();
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    
                    connectBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private void disconnect() {
        if (sftpChannel != null && sftpChannel.isConnected()) sftpChannel.disconnect();
        if (shellChannel != null && shellChannel.isConnected()) shellChannel.disconnect();
        if (session != null && session.isConnected()) session.disconnect();
        
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        
        
        rootNode.removeAllChildren();
        rootNode.setUserObject("Not Connected");
        treeModel.reload();
    }

    private void loadDirectory(DefaultMutableTreeNode parentNode, String path) {
        if (sftpChannel == null) return;
        new Thread(() -> {
            try {
                Vector<ChannelSftp.LsEntry> entriesVector = sftpChannel.ls(path);
                List<ChannelSftp.LsEntry> entries = new ArrayList<>(entriesVector);
                entries.sort((a, b) -> {
                    if (a.getAttrs().isDir() && !b.getAttrs().isDir()) return -1;
                    if (!a.getAttrs().isDir() && b.getAttrs().isDir()) return 1;
                    return a.getFilename().compareToIgnoreCase(b.getFilename());
                });

                SwingUtilities.invokeLater(() -> {
                    parentNode.removeAllChildren();
                    for (ChannelSftp.LsEntry entry : entries) {
                        if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) continue;
                        String fullPath = path.endsWith("/") ? path + entry.getFilename() : path + "/" + entry.getFilename();
                        boolean isDir = entry.getAttrs().isDir();
                        SftpFileNode fn = new SftpFileNode(entry.getFilename(), fullPath, isDir);
                        DefaultMutableTreeNode child = new DefaultMutableTreeNode(fn);
                        if (isDir) {
                            child.add(new DefaultMutableTreeNode("...")); // dummy for expansion
                        }
                        parentNode.add(child);
                    }
                    treeModel.reload(parentNode);
                });
            } catch (Exception e) {
                System.err.println("Error listing dir: " + e.getMessage());
            }
        }).start();
    }

    private void uploadFiles(java.util.List<java.io.File> files, SftpFileNode dirNode, DefaultMutableTreeNode node) {
        if (sftpChannel == null) return;
        new Thread(() -> {
            for (java.io.File file : files) {
                if (file.isDirectory()) continue; // Skip folders for now
                try {
                    String remoteDest = dirNode.fullPath;
                    if (!remoteDest.endsWith("/")) remoteDest += "/";
                    remoteDest += file.getName();
                    
                    sftpChannel.put(file.getAbsolutePath(), remoteDest);
                    SwingUtilities.invokeLater(() -> System.out.println("Uploaded"));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Failed to upload " + file.getName() + ": " + e.getMessage()));
                }
            }
            loadDirectory(node, dirNode.fullPath);
        }).start();
    }
    
    private void downloadFile(SftpFileNode fileNode) {
        if (sftpChannel == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(fileNode.name));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File dest = chooser.getSelectedFile();
            new Thread(() -> {
                try {
                    sftpChannel.get(fileNode.fullPath, dest.getAbsolutePath());
                    SwingUtilities.invokeLater(() -> System.out.println("Downloaded"));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Failed to download: " + e.getMessage()));
                }
            }).start();
        }
    }
    
    private void deleteFile(SftpFileNode fileNode, DefaultMutableTreeNode node) {
        if (sftpChannel == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "Delete " + fileNode.name + " from server?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    if (fileNode.isDir) {
                        sftpChannel.rmdir(fileNode.fullPath);
                    } else {
                        sftpChannel.rm(fileNode.fullPath);
                    }
                    SwingUtilities.invokeLater(() -> {
                        treeModel.removeNodeFromParent(node);
                        System.out.println("Deleted");
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Failed to delete: " + e.getMessage()));
                }
            }).start();
        }
    }

    private static class SftpFileNode {
        String name;
        String fullPath;
        boolean isDir;

        SftpFileNode(String name, String fullPath, boolean isDir) {
            this.name = name;
            this.fullPath = fullPath;
            this.isDir = isDir;
        }

        @Override
        public String toString() {
            return (isDir ? "📁 " : "📄 ") + name;
        }
    }
}
