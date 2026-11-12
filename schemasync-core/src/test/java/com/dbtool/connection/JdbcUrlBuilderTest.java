package com.dbtool.connection;

import com.dbtool.core.connection.JdbcUrlBuilder;
import com.dbtool.core.model.ConnectionConfig;
import com.dbtool.core.model.DatabaseType;
import com.dbtool.core.model.SslMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JdbcUrlBuilderTest {
    @Test
    void testPostgresUrl() {
        ConnectionConfig cfg = new ConnectionConfig();
        cfg.setType(DatabaseType.POSTGRESQL);
        cfg.setHost("dbserver");
        cfg.setPort(5433);
        cfg.setDatabaseName("analytics");
        assertEquals("jdbc:postgresql://dbserver:5433/analytics?tcpKeepAlive=true&gssEncMode=disable", JdbcUrlBuilder.buildUrl(cfg));

        cfg.setSslMode(SslMode.VERIFY_FULL);
        assertEquals("jdbc:postgresql://dbserver:5433/analytics?tcpKeepAlive=true&gssEncMode=disable&sslmode=verify-full", JdbcUrlBuilder.buildUrl(cfg));
    }

    @Test
    void testMySqlDefaultsToTlsPreferredAndRespectsSslMode() {
        ConnectionConfig cfg = new ConnectionConfig();
        cfg.setType(DatabaseType.MYSQL);
        cfg.setHost("mysql.internal");
        cfg.setPort(3306);
        cfg.setDatabaseName("ledger");
        cfg.setTimeoutSeconds(10);
        String url = JdbcUrlBuilder.buildUrl(cfg);
        assertTrue(url.contains("sslMode=PREFERRED"), "MySQL must default to TLS PREFERRED: " + url);
        assertFalse(url.contains("useSSL=false"), "Unsafe useSSL=false must never be defaulted");

        cfg.setSslMode(SslMode.VERIFY_FULL);
        assertTrue(JdbcUrlBuilder.buildUrl(cfg).contains("sslMode=VERIFY_IDENTITY"));
    }

    @Test
    void testSqliteAndH2Urls() {
        ConnectionConfig sqlite = new ConnectionConfig();
        sqlite.setType(DatabaseType.SQLITE);
        sqlite.setFilePath("/tmp/local.db");
        assertEquals("jdbc:sqlite:/tmp/local.db", JdbcUrlBuilder.buildUrl(sqlite));

        ConnectionConfig cfg = new ConnectionConfig();
        cfg.setType(DatabaseType.H2);
        cfg.setDatabaseName("test_db");
        assertEquals("jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1", JdbcUrlBuilder.buildUrl(cfg));
    }
}
