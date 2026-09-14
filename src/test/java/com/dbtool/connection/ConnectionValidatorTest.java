package com.dbtool.connection;

import com.dbtool.core.connection.ConnectionValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConnectionValidatorTest {
    @Test
    void testNullConnection() {
        assertFalse(ConnectionValidator.isValid(null, 1));
    }
}
