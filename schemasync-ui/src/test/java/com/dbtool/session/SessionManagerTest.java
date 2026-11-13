package com.dbtool.session;

import com.dbtool.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionManager coordinator and event dispatching.
 * Created & Developed by: NSENGIYUMVA Christophe
 */
public class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    public void setup() {
        sessionManager = SessionManager.getInstance();
        sessionManager.closeAllSessions();
    }

    @Test
    public void testRegisterAndActiveSession() {
        assertEquals(0, sessionManager.getSessionCount());
        assertNull(sessionManager.getActiveSession());

        DatabaseManager mgr1 = new DatabaseManager();
        DatabaseSession s1 = sessionManager.registerSession("prod_db", "POSTGRESQL", mgr1);

        assertEquals(1, sessionManager.getSessionCount());
        assertSame(s1, sessionManager.getActiveSession());

        DatabaseManager mgr2 = new DatabaseManager();
        DatabaseSession s2 = sessionManager.registerSession("staging_db", "POSTGRESQL", mgr2);

        assertEquals(2, sessionManager.getSessionCount());
        // s1 should remain active until explicit switch
        assertSame(s1, sessionManager.getActiveSession());

        sessionManager.setActiveSession(s2);
        assertSame(s2, sessionManager.getActiveSession());
    }

    @Test
    public void testCloseSession() {
        DatabaseManager mgr1 = new DatabaseManager();
        DatabaseManager mgr2 = new DatabaseManager();

        DatabaseSession s1 = sessionManager.registerSession("db1", "SQLITE", mgr1);
        DatabaseSession s2 = sessionManager.registerSession("db2", "SQLITE", mgr2);

        sessionManager.setActiveSession(s1);
        sessionManager.closeSession(s1);

        assertEquals(1, sessionManager.getSessionCount());
        assertSame(s2, sessionManager.getActiveSession());

        sessionManager.closeSession(s2);
        assertEquals(0, sessionManager.getSessionCount());
        assertNull(sessionManager.getActiveSession());
    }

    @Test
    public void testSessionListenerCallbacks() {
        AtomicBoolean added = new AtomicBoolean(false);
        AtomicBoolean activated = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);

        SessionListener listener = new SessionListener() {
            @Override
            public void onSessionAdded(DatabaseSession session) {
                added.set(true);
            }

            @Override
            public void onSessionActivated(DatabaseSession session) {
                activated.set(true);
            }

            @Override
            public void onSessionClosed(DatabaseSession session) {
                closed.set(true);
            }
        };

        sessionManager.addListener(listener);

        try {
            DatabaseSession s = sessionManager.registerSession("test_db", "POSTGRESQL", new DatabaseManager());
            assertTrue(added.get());
            assertTrue(activated.get());

            sessionManager.closeSession(s);
            assertTrue(closed.get());
        } finally {
            sessionManager.removeListener(listener);
        }
    }

    @Test
    public void testAttachedAndDetachedFiltering() {
        DatabaseSession s1 = sessionManager.registerSession("main_db", "POSTGRESQL", new DatabaseManager());
        DatabaseSession s2 = sessionManager.registerSession("side_db", "MYSQL", new DatabaseManager());

        assertEquals(2, sessionManager.getAttachedSessions().size());

        s2.setDetached(true);
        assertEquals(1, sessionManager.getAttachedSessions().size());
        assertEquals(s1, sessionManager.getAttachedSessions().get(0));

        s2.setDetached(false);
        assertEquals(2, sessionManager.getAttachedSessions().size());
    }
}
