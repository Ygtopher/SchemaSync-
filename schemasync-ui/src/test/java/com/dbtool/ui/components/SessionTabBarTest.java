package com.dbtool.ui.components;

import com.dbtool.DatabaseManager;
import com.dbtool.session.DatabaseSession;
import com.dbtool.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionTabBar component.
 * Created & Developed by: NSENGIYUMVA Christophe
 */
public class SessionTabBarTest {

    @BeforeEach
    public void setup() {
        SessionManager.getInstance().closeAllSessions();
    }

    @Test
    public void testTabBarRebuildsOnSessionChanges() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            SessionTabBar tabBar = new SessionTabBar(null);
            assertNotNull(tabBar);

            // Empty state
            tabBar.rebuildTabs();
            assertTrue(tabBar.getComponentCount() > 0);

            // Register session
            DatabaseSession s1 = SessionManager.getInstance().registerSession("prod_db", "POSTGRESQL", new DatabaseManager());
            tabBar.rebuildTabs();
            assertTrue(tabBar.getComponentCount() > 0);

            // Detach session
            s1.setDetached(true);
            tabBar.rebuildTabs();
            assertTrue(tabBar.getComponentCount() > 0);

            // Re-attach
            s1.setDetached(false);
            tabBar.rebuildTabs();
            assertTrue(tabBar.getComponentCount() > 0);

            // Close session
            SessionManager.getInstance().closeSession(s1);
            tabBar.rebuildTabs();
            assertTrue(tabBar.getComponentCount() > 0);
        });
    }
}
