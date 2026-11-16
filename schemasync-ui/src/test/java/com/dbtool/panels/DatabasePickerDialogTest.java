package com.dbtool.panels;

import org.junit.jupiter.api.Test;

import javax.swing.DefaultListModel;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabasePickerDialogTest {

    @Test
    void testPickerInitializationAndFiltering() {
        List<String> dbs = Arrays.asList("postgres", "company_prod", "analytics_dw", "staging_v2");
        DatabasePickerDialog dialog = new DatabasePickerDialog(null, "127.0.0.1", 5432, "postgres", dbs, "company_prod");

        // Initial selection should be "company_prod"
        assertEquals("company_prod", dialog.getSelectedDatabase());

        // Test filtering
        dialog.getSearchField().setText("dw");
        DefaultListModel<String> model = dialog.getListModel();
        assertEquals(1, model.getSize());
        assertEquals("analytics_dw", model.get(0));

        // Test clear filter
        dialog.getSearchField().setText("");
        assertEquals(4, model.getSize());

        dialog.dispose();
    }

    @Test
    void testConnectionManagerDialogEnteredDbName() {
        ConnectionManagerDialog dialog = new ConnectionManagerDialog(null, new com.dbtool.DatabaseManager());
        
        // Default entered db name should be set (or initialized)
        dialog.setEnteredDbName("custom_tenant_db");
        assertEquals("custom_tenant_db", dialog.getEnteredDbName());

        // Test combo box updates
        assertEquals("custom_tenant_db", dialog.getDbNameCombo().getSelectedItem());

        dialog.dispose();
    }

    @Test
    void testRenderDatabaseDiscoveryDialogs() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                // 1. Render DatabasePickerDialog
                List<String> dbs = Arrays.asList("postgres", "company_prod", "analytics_dw", "staging_v2", "customer_service", "billing_db");
                DatabasePickerDialog picker = new DatabasePickerDialog(null, "127.0.0.1", 5432, "postgres", dbs, "company_prod");
                picker.setSize(480, 480);
                picker.getRootPane().setSize(480, 480);
                picker.getRootPane().doLayout();
                picker.getContentPane().setSize(480, 480);
                picker.getContentPane().doLayout();
                picker.addNotify();
                picker.validate();

                java.awt.image.BufferedImage imgPicker = new java.awt.image.BufferedImage(480, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D gPicker = imgPicker.createGraphics();
                gPicker.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                gPicker.setColor(new java.awt.Color(25, 28, 36));
                gPicker.fillRect(0, 0, 480, 480);
                picker.getRootPane().paint(gPicker);
                gPicker.dispose();

                File outDir = new File("C:/Users/CHRISTOPHE/.gemini/antigravity/brain/452c91c6-5676-46e5-9238-c057a8809e4a");
                File pickerFile = new File(outDir, "database_picker_dialog.png");
                javax.imageio.ImageIO.write(imgPicker, "png", pickerFile);
                System.out.println("Saved DatabasePickerDialog render: " + pickerFile.getAbsolutePath());
                picker.dispose();

                // 2. Render ConnectionManagerDialog with Fetch DBs button
                ConnectionManagerDialog connDialog = new ConnectionManagerDialog(null, new com.dbtool.DatabaseManager());
                connDialog.setSize(800, 520);
                connDialog.getRootPane().setSize(800, 520);
                connDialog.getRootPane().doLayout();
                connDialog.getContentPane().setSize(800, 520);
                connDialog.getContentPane().doLayout();
                connDialog.setEnteredDbName("company_prod");
                connDialog.addNotify();
                connDialog.validate();

                java.awt.image.BufferedImage imgConn = new java.awt.image.BufferedImage(800, 520, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D gConn = imgConn.createGraphics();
                gConn.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                gConn.setColor(new java.awt.Color(25, 28, 36));
                gConn.fillRect(0, 0, 800, 520);
                connDialog.getRootPane().paint(gConn);
                gConn.dispose();

                File connFile = new File(outDir, "connection_manager_with_discover_btn.png");
                javax.imageio.ImageIO.write(imgConn, "png", connFile);
                System.out.println("Saved ConnectionManagerDialog render: " + connFile.getAbsolutePath());
                connDialog.dispose();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }
}
