package com.dbtool.connection;

import com.dbtool.core.model.ConnectionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConnectionStatusTest {
    @Test
    void testStatusDescriptions() {
        assertEquals("Connected", ConnectionStatus.CONNECTED.getDescription());
        assertEquals("Connecting...", ConnectionStatus.CONNECTING.getDescription());
    }
}
