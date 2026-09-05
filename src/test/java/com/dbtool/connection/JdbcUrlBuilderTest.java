package com.dbtool.connection;

import com.dbtool.core.connection.JdbcUrlBuilder;
import com.dbtool.core.model.ConnectionConfig;
import com.dbtool.core.model.DatabaseType;
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
        assertEquals("jdbc:postgresql://dbserver:5433/analytics", JdbcUrlBuilder.buildUrl(cfg));
    }

    @Test
    void testH2Url() {
        ConnectionConfig cfg = new ConnectionConfig();
        cfg.setType(DatabaseType.H2);
        cfg.setDatabaseName("test_db");
        assertEquals("jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1", JdbcUrlBuilder.buildUrl(cfg));
    }
}
