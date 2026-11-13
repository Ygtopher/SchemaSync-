package com.dbtool.session;

import com.dbtool.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseSession model and lifecycle.
 * Created & Developed by: NSENGIYUMVA Christophe
 */
public class DatabaseSessionTest {

    private DatabaseManager dbManager;

    @BeforeEach
    public void setup() {
        dbManager = new DatabaseManager();
    }

    @Test
    public void testSessionInitialization() {
        DatabaseSession session = new DatabaseSession("ecommerce_prod", "POSTGRESQL", dbManager);

        assertNotNull(session.getId());
        assertEquals("ecommerce_prod", session.getName());
        assertEquals("POSTGRESQL", session.getDbType());
        assertSame(dbManager, session.getDbManager());
        assertFalse(session.isDetached());
        assertNull(session.getDetachedWindow());
        assertNotNull(session.getBadgeColor());
        assertTrue(session.getConnectedAt() > 0);
    }

    @Test
    public void testSessionDetachedState() {
        DatabaseSession session = new DatabaseSession("analytics_db", "MYSQL", dbManager);

        assertFalse(session.isDetached());
        session.setDetached(true);
        assertTrue(session.isDetached());

        session.setDetached(false);
        assertFalse(session.isDetached());
    }

    @Test
    public void testEqualsAndHashCode() {
        DatabaseSession s1 = new DatabaseSession("id-123", "db_one", "SQLITE", dbManager);
        DatabaseSession s2 = new DatabaseSession("id-123", "db_one_renamed", "SQLITE", dbManager);
        DatabaseSession s3 = new DatabaseSession("id-456", "db_two", "SQLITE", dbManager);

        assertEquals(s1, s2);
        assertNotEquals(s1, s3);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    public void testSessionClose() {
        DatabaseSession session = new DatabaseSession("sandbox", "SANDBOX", dbManager);
        assertDoesNotThrow(session::close);
        assertFalse(session.isAlive());
    }
}
