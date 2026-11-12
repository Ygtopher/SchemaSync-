package com.dbtool.core.connection;

import com.dbtool.core.model.ConnectionConfig;
import com.dbtool.core.model.DatabaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class SessionPoolManagerTest {

    @Test
    void testHikariPoolBorrowReturnAndPinnedTransactionIsolation() throws Exception {
        ConnectionConfig cfg = new ConnectionConfig();
        cfg.setType(DatabaseType.H2);
        cfg.setDatabaseName("pool_test_" + System.nanoTime());
        cfg.setUsername("sa");
        cfg.setPassword("");

        try (SessionPoolManager pool = new SessionPoolManager(cfg)) {
            assertTrue(pool.isAlive());
            try (Connection init = pool.borrowConnection(); Statement st = init.createStatement()) {
                st.execute("CREATE TABLE items (id INT PRIMARY KEY, name VARCHAR(50))");
            }

            // Pinned session turns off autoCommit and performs an uncommitted insert
            try (Connection pinned = pool.openPinnedSession()) {
                pinned.setAutoCommit(false);
                try (Statement st = pinned.createStatement()) {
                    st.executeUpdate("INSERT INTO items VALUES (1, 'uncommitted')");
                }

                // Pooled connection must NOT share the pinned session's uncommitted state or autoCommit=false
                try (Connection pooled = pool.borrowConnection()) {
                    assertTrue(pooled.getAutoCommit(), "Pooled connection must retain autoCommit=true");
                    try (Statement st = pooled.createStatement();
                         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM items")) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Uncommitted pinned insert must not leak into pooled read");
                    }
                }

                pinned.rollback();
            }
        }
    }

    @Test
    void testSqliteSerializedLeaseReuse(@TempDir Path tempDir) throws Exception {
        ConnectionConfig cfg = new ConnectionConfig();
        cfg.setType(DatabaseType.SQLITE);
        cfg.setFilePath(tempDir.resolve("lease_test.db").toString());

        try (SessionPoolManager pool = new SessionPoolManager(cfg)) {
            assertTrue(pool.isAlive());
            assertEquals(1, pool.getMaxPoolSize());

            try (Connection c1 = pool.borrowConnection(); Statement st = c1.createStatement()) {
                assertEquals(1, pool.getActiveCount());
                st.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT)");
                st.executeUpdate("INSERT INTO notes VALUES (10, 'hello')");
            }
            assertEquals(0, pool.getActiveCount());

            try (Connection c2 = pool.borrowConnection();
                 Statement st = c2.createStatement();
                 ResultSet rs = st.executeQuery("SELECT body FROM notes WHERE id = 10")) {
                assertTrue(rs.next());
                assertEquals("hello", rs.getString(1));
            }
        }
    }
}
