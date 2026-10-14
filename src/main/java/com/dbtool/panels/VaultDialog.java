package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.util.CryptoUtil;
import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.Properties;

/**
 * Enterprise Credential Vault Dialog.
 * Allows managing Master Password protection, locking/unlocking the vault,
 * and exporting/importing AES-256-GCM encrypted .schemasync.vault profile bundles.
 */
public class VaultDialog extends JDialog {

    private final DatabaseManager dbManager;
    private final Runnable onProfilesChanged;

    private JLabel statusLabel;
    private JButton lockToggleBtn;
    private JButton masterPasswordBtn;

    public VaultDialog(Window parent, DatabaseManager dbManager, Runnable onProfilesChanged) {
        super(parent, "🛡️ Encrypted Credential Vault (AES-256-GCM)", ModalityType.APPLICATION_MODAL);
        this.dbManager = dbManager;
        this.onProfilesChanged = onProfilesChanged;

        setSize(560, 480);
        setLocationRelativeTo(parent);
        setResizable(false);
        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLbl = new JLabel("🛡️ Credential Vault Security");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
        JLabel subtitleLbl = new JLabel("<html>Military-grade AES-256-GCM encryption with PBKDF2 key derivation for saved database passwords.</html>");
        subtitleLbl.setForeground(ThemeManager.isDarkMode() ? new Color(170, 170, 170) : new Color(100, 100, 100));
        headerPanel.add(titleLbl, BorderLayout.NORTH);
        headerPanel.add(subtitleLbl, BorderLayout.CENTER);
        root.add(headerPanel, BorderLayout.NORTH);

        // Center Content Tabs
        JTabbedPane tabs = new JTabbedPane();

        // Tab 1: Master Password & Status
        JPanel statusPanel = createStatusPanel();
        tabs.addTab("Vault Status & Master Password", statusPanel);

        // Tab 2: Export Vault
        JPanel exportPanel = createExportPanel();
        tabs.addTab("Export Vault (.schemasync.vault)", exportPanel);

        // Tab 3: Import Vault
        JPanel importPanel = createImportPanel();
        tabs.addTab("Import Vault", importPanel);

        root.add(tabs, BorderLayout.CENTER);

        // Bottom Close Button
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn);
        root.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(root);
        updateStatusDisplay();
    }

    private JPanel createStatusPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(16, 12, 16, 12));

        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        p.add(statusLabel);
        p.add(Box.createVerticalStrut(10));

        JLabel infoDesc = new JLabel("<html>By default, SchemaSync encrypts all credentials at rest using a device-bound 256-bit key. " +
                "Setting a <b>Master Password</b> adds an additional layer of zero-knowledge protection, requiring authentication " +
                "before passwords can be decrypted or used.</html>");
        infoDesc.setForeground(ThemeManager.isDarkMode() ? new Color(180, 180, 180) : new Color(80, 80, 80));
        p.add(infoDesc);
        p.add(Box.createVerticalStrut(20));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lockToggleBtn = new JButton();
        lockToggleBtn.addActionListener(e -> toggleLock());
        btnRow.add(lockToggleBtn);

        masterPasswordBtn = new JButton();
        masterPasswordBtn.addActionListener(e -> configureMasterPassword());
        btnRow.add(masterPasswordBtn);

        p.add(btnRow);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private void updateStatusDisplay() {
        boolean hasMaster = CryptoUtil.hasMasterPassword();
        boolean unlocked = CryptoUtil.isVaultUnlocked();

        if (hasMaster) {
            if (unlocked) {
                statusLabel.setText("🔓 Vault Status: Unlocked (Master Password Active)");
                statusLabel.setForeground(new Color(16, 185, 129));
                lockToggleBtn.setText("🔒 Lock Vault Now");
                lockToggleBtn.setEnabled(true);
            } else {
                statusLabel.setText("🔒 Vault Status: Locked");
                statusLabel.setForeground(new Color(239, 68, 68));
                lockToggleBtn.setText("🔓 Unlock Vault...");
                lockToggleBtn.setEnabled(true);
            }
            masterPasswordBtn.setText("Change / Remove Master Password...");
        } else {
            statusLabel.setText("🛡️ Vault Status: Device-Bound AES-256 Active");
            statusLabel.setForeground(new Color(59, 130, 246));
            lockToggleBtn.setText("🔒 Lock Vault (N/A)");
            lockToggleBtn.setEnabled(false);
            masterPasswordBtn.setText("🔑 Set Master Password...");
        }
    }

    private void toggleLock() {
        if (!CryptoUtil.hasMasterPassword()) return;

        if (CryptoUtil.isVaultUnlocked()) {
            CryptoUtil.lockVault();
            JOptionPane.showMessageDialog(this, "Vault locked. Database passwords cannot be accessed without the Master Password.", "Vault Locked", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JPasswordField passField = new JPasswordField();
            int res = JOptionPane.showConfirmDialog(this, new Object[]{"Enter Master Password to unlock:", passField}, "Unlock Vault", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                boolean ok = CryptoUtil.unlockVault(passField.getPassword());
                if (ok) {
                    JOptionPane.showMessageDialog(this, "Vault successfully unlocked!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Incorrect Master Password.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        updateStatusDisplay();
        if (onProfilesChanged != null) onProfilesChanged.run();
    }

    private void configureMasterPassword() {
        boolean hasMaster = CryptoUtil.hasMasterPassword();
        if (!hasMaster) {
            // Set new password
            JPasswordField p1 = new JPasswordField();
            JPasswordField p2 = new JPasswordField();
            Object[] msg = {
                    "Enter new Master Password:", p1,
                    "Confirm Master Password:", p2
            };
            int res = JOptionPane.showConfirmDialog(this, msg, "Set Master Password", JOptionPane.OK_CANCEL_OPTION);
            if (res == JOptionPane.OK_OPTION) {
                String pass1 = new String(p1.getPassword());
                String pass2 = new String(p2.getPassword());
                if (pass1.length() < 6) {
                    JOptionPane.showMessageDialog(this, "Master Password must be at least 6 characters long.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (!pass1.equals(pass2)) {
                    JOptionPane.showMessageDialog(this, "Passwords do not match.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try {
                    CryptoUtil.setMasterPassword(p1.getPassword());
                    JOptionPane.showMessageDialog(this, "Master Password configured successfully!\nVault is now protected with AES-256-GCM PBKDF2 encryption.", "Vault Protected", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to set master password: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            // Change or Remove
            String[] options = {"Change Master Password", "Remove Master Password (Revert to Device Key)", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this, "Select an action for your Master Password:", "Master Password Options", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            if (choice == 0) {
                JPasswordField oldPass = new JPasswordField();
                JPasswordField newPass1 = new JPasswordField();
                JPasswordField newPass2 = new JPasswordField();
                Object[] msg = {
                        "Current Master Password:", oldPass,
                        "New Master Password:", newPass1,
                        "Confirm New Password:", newPass2
                };
                int res = JOptionPane.showConfirmDialog(this, msg, "Change Master Password", JOptionPane.OK_CANCEL_OPTION);
                if (res == JOptionPane.OK_OPTION) {
                    if (!CryptoUtil.unlockVault(oldPass.getPassword())) {
                        JOptionPane.showMessageDialog(this, "Current Master Password was incorrect.", "Authentication Failed", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    String p1 = new String(newPass1.getPassword());
                    String p2 = new String(newPass2.getPassword());
                    if (p1.length() < 6) {
                        JOptionPane.showMessageDialog(this, "Password must be at least 6 characters long.", "Error", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    if (!p1.equals(p2)) {
                        JOptionPane.showMessageDialog(this, "New passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    try {
                        CryptoUtil.setMasterPassword(newPass1.getPassword());
                        JOptionPane.showMessageDialog(this, "Master Password successfully updated!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else if (choice == 1) {
                JPasswordField curPass = new JPasswordField();
                int res = JOptionPane.showConfirmDialog(this, new Object[]{"Enter Current Master Password to confirm removal:", curPass}, "Remove Master Password", JOptionPane.OK_CANCEL_OPTION);
                if (res == JOptionPane.OK_OPTION) {
                    if (CryptoUtil.unlockVault(curPass.getPassword())) {
                        CryptoUtil.removeMasterPassword();
                        JOptionPane.showMessageDialog(this, "Master Password removed. Credentials will use local device-bound AES-256 encryption.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Incorrect password.", "Authentication Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
        updateStatusDisplay();
        if (onProfilesChanged != null) onProfilesChanged.run();
    }

    private JPanel createExportPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(16, 12, 16, 12));

        JLabel desc = new JLabel("<html>Export all saved connection profiles into a single password-protected " +
                "<b>.schemasync.vault</b> file. You can securely transfer this file to teammates or other machines.</html>");
        p.add(desc);
        p.add(Box.createVerticalStrut(16));

        JPasswordField exportPass = new JPasswordField();
        JPasswordField exportPassConfirm = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Encryption Password:"));
        form.add(exportPass);
        form.add(new JLabel("Confirm Password:"));
        form.add(exportPassConfirm);
        p.add(form);
        p.add(Box.createVerticalStrut(16));

        JButton doExportBtn = new JButton("📤 Export Encrypted Vault File...");
        doExportBtn.addActionListener(e -> {
            String p1 = new String(exportPass.getPassword());
            String p2 = new String(exportPassConfirm.getPassword());
            if (p1.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter an encryption password for the export file.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!p1.equals(p2)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Validation", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("SchemaSync_Connections.schemasync.vault"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File target = chooser.getSelectedFile();
                if (!target.getName().endsWith(".schemasync.vault")) {
                    target = new File(target.getParentFile(), target.getName() + ".schemasync.vault");
                }
                try {
                    Properties saved = dbManager.getSavedConnections();
                    CryptoUtil.exportVault(target, exportPass.getPassword(), saved);
                    JOptionPane.showMessageDialog(this, "Successfully exported " + saved.size() + " connection profiles to:\n" + target.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                    exportPass.setText("");
                    exportPassConfirm.setText("");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        p.add(doExportBtn);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel createImportPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(16, 12, 16, 12));

        JLabel desc = new JLabel("<html>Import connection profiles from an encrypted <b>.schemasync.vault</b> file. " +
                "Imported profiles will be merged with your current connection catalog.</html>");
        p.add(desc);
        p.add(Box.createVerticalStrut(16));

        JTextField fileField = new JTextField();
        fileField.setEditable(false);
        JButton browseBtn = new JButton("Browse...");
        JPanel fileRow = new JPanel(new BorderLayout(6, 0));
        fileRow.add(fileField, BorderLayout.CENTER);
        fileRow.add(browseBtn, BorderLayout.EAST);

        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                fileField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        p.add(new JLabel("Vault File:"));
        p.add(Box.createVerticalStrut(4));
        p.add(fileRow);
        p.add(Box.createVerticalStrut(12));

        JPasswordField importPass = new JPasswordField();
        JPanel passRow = new JPanel(new BorderLayout(6, 0));
        passRow.add(new JLabel("Decryption Password: "), BorderLayout.WEST);
        passRow.add(importPass, BorderLayout.CENTER);
        p.add(passRow);
        p.add(Box.createVerticalStrut(16));

        JButton doImportBtn = new JButton("📥 Decrypt & Import Profiles");
        doImportBtn.addActionListener(e -> {
            String path = fileField.getText().trim();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select a .schemasync.vault file.", "Notice", JOptionPane.WARNING_MESSAGE);
                return;
            }
            File vaultFile = new File(path);
            if (!vaultFile.exists()) {
                JOptionPane.showMessageDialog(this, "Selected file does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                Properties imported = CryptoUtil.importVault(vaultFile, importPass.getPassword());
                int count = 0;
                for (String name : imported.stringPropertyNames()) {
                    String val = imported.getProperty(name);
                    String[] parts = val.split(";");
                    if (parts.length >= 5) {
                        String h = parts[0];
                        String port = parts[1];
                        String u = parts[2];
                        String pw = CryptoUtil.decryptPassword(parts[3]);
                        String d = parts[4];
                        dbManager.saveConnectionProfile(name, h, port, u, pw, d);
                        count++;
                    }
                }
                JOptionPane.showMessageDialog(this, "Successfully imported and merged " + count + " connection profiles!", "Import Success", JOptionPane.INFORMATION_MESSAGE);
                if (onProfilesChanged != null) onProfilesChanged.run();
                fileField.setText("");
                importPass.setText("");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to import vault. Please check password and file format.\nError: " + ex.getMessage(), "Import Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
        p.add(doImportBtn);
        p.add(Box.createVerticalGlue());
        return p;
    }
}
