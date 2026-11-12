package com.dbtool.dialect;

import com.dbtool.core.dialect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DialectRegistryTest {
    @Test
    void testUrlResolutionAndServiceLoaderRegistration() {
        DialectRegistry registry = DialectRegistry.getInstance();
        assertEquals(DialectType.POSTGRESQL, registry.resolveFromJdbcUrl("jdbc:postgresql://localhost:5432/mydb").getDialectType());
        assertEquals(DialectType.MYSQL, registry.resolveFromJdbcUrl("jdbc:mysql://localhost:3306/shop").getDialectType());
        assertEquals(DialectType.MYSQL, registry.resolveFromJdbcUrl("jdbc:mariadb://localhost:3306/shop").getDialectType());
        assertEquals(DialectType.SQLITE, registry.resolveFromJdbcUrl("jdbc:sqlite:test.db").getDialectType());
        assertEquals(DialectType.H2, registry.resolveFromJdbcUrl("jdbc:h2:mem:testdb").getDialectType());
        assertEquals(DialectType.ACCESS, registry.resolveFromJdbcUrl("jdbc:ucanaccess://C:/data/db.accdb").getDialectType());
        assertEquals(DialectType.GENERIC, registry.resolveFromJdbcUrl("jdbc:unknown://server").getDialectType());

        assertTrue(registry.getRegisteredDialects().size() >= 6,
                "ServiceLoader should discover all 6 built-in dialects");
        assertEquals("`User Name`", registry.getDialect(DialectType.MYSQL).quoteIdentifier("User Name"));
        assertEquals("id", registry.getDialect(DialectType.MYSQL).quoteIdentifier("id"));
    }
}
