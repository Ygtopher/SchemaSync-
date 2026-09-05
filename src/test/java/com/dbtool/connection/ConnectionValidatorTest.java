package com.dbtool.connection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConnectionValidatorTest {
    @Test
    void testNullConnection() {
        assertFalse(ConnectionValidator.isValid(null, 1));
    }
}
