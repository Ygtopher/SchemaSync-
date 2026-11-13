package com.dbtool.session;

import com.dbtool.DatabaseManager;
import com.dbtool.Main;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end Swing rendering verification test for multi-connection workspaces and detached windows.
 * Created & Developed by: NSENGIYUMVA Christophe
 */
public class MultiConnectionWindowRenderTest {

    @Test
    public void testRenderMultiConnectionAndDetachedWindow() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        SwingUtilities.invokeAndWait(() -> {
            try {
                // Initialize Main window
                Main main = new Main();
                main.setSize(1280, 800);
                main.addNotify();
                main.validate();

                // 1. First Database Session: Sandbox
                DatabaseManager db1 = new DatabaseManager();
                db1.connectSandbox();
                DatabaseSession s1 = SessionManager.getInstance().registerSession("ecommerce_prod", "POSTGRESQL", db1);
                main.activateSession(s1);

                // 2. Second Database Session: Analytics
                DatabaseManager db2 = new DatabaseManager();
                db2.connectSandbox();
                DatabaseSession s2 = SessionManager.getInstance().registerSession("analytics_warehouse", "MYSQL", db2);

                // Update UI layout
                main.validate();
                main.repaint();

                // Render Main window with session tabs to image
                BufferedImage mainImg = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = mainImg.createGraphics();
                g2.setColor(new Color(20, 24, 33));
                g2.fillRect(0, 0, 1280, 800);
                main.getContentPane().paint(g2);
                g2.dispose();

                File outDir = new File("C:/Users/CHRISTOPHE/.gemini/antigravity/brain/452c91c6-5676-46e5-9238-c057a8809e4a");
                File mainFile = new File(outDir, "multi_connection_workspace_tabs.png");
                ImageIO.write(mainImg, "png", mainFile);
                System.out.println("Saved main workspace tabs render: " + mainFile.getAbsolutePath());

                // 3. Test Detach s2 into floating window
                Main detachedWin = new Main(s2, main);
                s2.setDetached(true);
                s2.setDetachedWindow(detachedWin);
                detachedWin.setSize(1000, 700);
                detachedWin.addNotify();
                detachedWin.validate();
                detachedWin.repaint();

                BufferedImage detImg = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gDet = detImg.createGraphics();
                gDet.setColor(new Color(20, 24, 33));
                gDet.fillRect(0, 0, 1000, 700);
                detachedWin.getContentPane().paint(gDet);
                gDet.dispose();

                File detFile = new File(outDir, "detached_workspace_window.png");
                ImageIO.write(detImg, "png", detFile);
                System.out.println("Saved detached workspace window render: " + detFile.getAbsolutePath());

                // Clean up
                detachedWin.dispose();
                main.dispose();
                SessionManager.getInstance().closeAllSessions();

                assertTrue(mainFile.exists());
                assertTrue(detFile.exists());
            } catch (Exception ex) {
                ex.printStackTrace();
                fail(ex);
            }
        });
    }
}
