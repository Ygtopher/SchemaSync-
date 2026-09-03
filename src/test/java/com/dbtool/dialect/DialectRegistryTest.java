package com.dbtool.dialect;

import com.dbtool.core.dialect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DialectRegistryTest {
    @Test
    void testUrlResolution() {
        DialectRegistry registry = DialectRegistry.getInstance();
        assertEquals(DialectType.POSTGRESQL, registry.resolveFromJdbcUrl("jdbc:postgresql://localhost:5432/mydb").getDialectType());
        assertEquals(DialectType.H2, registry.resolveFromJdbcUrl("jdbc:h2:mem:testdb").getDialectType());
        assertEquals(DialectType.ACCESS, registry.resolveFromJdbcUrl("jdbc:ucanaccess://C:/data/db.accdb").getDialectType());
        assertEquals(DialectType.GENERIC, registry.resolveFromJdbcUrl("jdbc:unknown://server").getDialectType());
    }
}
