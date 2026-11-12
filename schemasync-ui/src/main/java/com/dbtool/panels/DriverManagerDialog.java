package com.dbtool.panels;

import com.dbtool.core.concurrency.TaskCoordinator;
import com.dbtool.core.dialect.DialectType;
import com.dbtool.core.driver.DriverClassLoaderManager;
import com.dbtool.core.driver.DriverDescriptor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * UI dialog for inspecting built-in/external JDBC drivers stored under {@code ~/.schemasync/drivers/},
 * registering custom JARs via isolated {@link java.net.URLClassLoader}s, and downloading drivers
 * on demand from Maven Central.
 */
public class DriverManagerDialog extends JDialog {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel = new JLabel("Drivers directory: " + DriverClassLoaderManager.getDriversDirectory());
    private List<DriverDescriptor> currentList;

    public DriverManagerDialog(Frame owner) {
        super(owner, "External JDBC Driver Manager", true);
        setSize(820, 440);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        String[] cols = {"ID", "Database", "Dialect", "Driver Class", "Maven Artifact", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton addCustomBtn = new JButton("+ Add Custom JAR...");
        JButton downloadBtn = new JButton("Download from Maven Central");
        JButton removeBtn = new JButton("Remove Custom");
        JButton closeBtn = new JButton("Close");

        addCustomBtn.addActionListener(e -> onAddCustomJar());
        downloadBtn.addActionListener(e -> onDownloadSelected());
        removeBtn.addActionListener(e -> onRemoveSelected());
        closeBtn.addActionListener(e -> dispose());

        btnPanel.add(addCustomBtn);
        btnPanel.add(downloadBtn);
        btnPanel.add(removeBtn);
        btnPanel.add(closeBtn);

        JPanel bottomPanel = new JPanel(new BorderLayout(6, 2));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));
        bottomPanel.add(btnPanel, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        refreshTable();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        DriverClassLoaderManager mgr = DriverClassLoaderManager.getInstance();
        currentList = mgr.getDrivers();
        for (DriverDescriptor d : currentList) {
            String coords = d.getMavenGroupId() != null
                    ? d.getMavenArtifactId() + ":" + d.getMavenVersion()
                    : "-";
            String status = mgr.isDriverAvailable(d)
                    ? (d.isBuiltIn() ? "Available (Built-In / JAR)" : "Installed (Isolated JAR)")
                    : "Not Downloaded";
            tableModel.addRow(new Object[]{
                    d.getId(),
                    d.getDisplayName(),
                    d.getDialectType(),
                    d.getDriverClassName(),
                    coords,
                    status
            });
        }
    }

    private void onDownloadSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentList.size()) {
            JOptionPane.showMessageDialog(this, "Select a driver row first.");
            return;
        }
        DriverDescriptor desc = currentList.get(row);
        if (desc.buildMavenCentralJarUrl() == null) {
            JOptionPane.showMessageDialog(this, "No Maven Central coordinates configured for " + desc.getDisplayName());
            return;
        }
        statusLabel.setText("Downloading " + desc.getDisplayName() + "...");
        TaskCoordinator.shared().submitRunnable("Download JDBC Driver", () -> {
            try {
                DriverClassLoaderManager.getInstance().downloadFromMavenCentral(desc,
                        msg -> SwingUtilities.invokeLater(() -> statusLabel.setText(msg)));
                SwingUtilities.invokeLater(() -> {
                    refreshTable();
                    statusLabel.setText("Loaded " + desc.getDisplayName() + " into isolated URLClassLoader.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Download failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Download failed:\n" + ex.getMessage(), "Driver Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void onAddCustomJar() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select JDBC Driver JAR");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File jarFile = fc.getSelectedFile();

        JTextField idField = new JTextField(jarFile.getName().replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase());
        JTextField nameField = new JTextField(jarFile.getName());
        JTextField classField = new JTextField();
        JTextField prefixField = new JTextField("jdbc:");
        JComboBox<DialectType> dialectCombo = new JComboBox<>(DialectType.values());

        JPanel form = new JPanel(new GridLayout(5, 2, 6, 6));
        form.add(new JLabel("Driver ID:")); form.add(idField);
        form.add(new JLabel("Display Name:")); form.add(nameField);
        form.add(new JLabel("Driver Class Name:")); form.add(classField);
        form.add(new JLabel("JDBC URL Prefix:")); form.add(prefixField);
        form.add(new JLabel("SQL Dialect:")); form.add(dialectCombo);

        if (JOptionPane.showConfirmDialog(this, form, "Register Custom JDBC Driver", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            DriverDescriptor desc = new DriverDescriptor(
                    idField.getText().trim(),
                    nameField.getText().trim(),
                    (DialectType) dialectCombo.getSelectedItem(),
                    classField.getText().trim(),
                    prefixField.getText().trim(),
                    -1,
                    null, null, null,
                    jarFile.getName(),
                    false
            );
            DriverClassLoaderManager.getInstance().installCustomDriverJar(desc, jarFile.toPath());
            refreshTable();
            statusLabel.setText("Registered custom driver: " + desc.getDisplayName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load custom driver JAR:\n" + ex.getMessage(), "Driver Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRemoveSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentList.size()) return;
        DriverDescriptor desc = currentList.get(row);
        if (desc.isBuiltIn()) {
            JOptionPane.showMessageDialog(this, "Built-in fallback descriptors cannot be deleted.");
            return;
        }
        try {
            DriverClassLoaderManager.getInstance().removeCustomDriver(desc.getId());
            refreshTable();
            statusLabel.setText("Removed custom driver: " + desc.getDisplayName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to remove driver: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
