package com.dbtool.dialect;

import com.dbtool.core.dialect.AccessDialect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AccessDialectTest {
    private final AccessDialect dialect = new AccessDialect();

    @Test
    void testBracketQuoting() {
        assertEquals("[User Data]", dialect.quoteIdentifier("User Data"));
        assertEquals("[Field]]Name]", dialect.quoteIdentifier("Field]Name"));
    }
}
