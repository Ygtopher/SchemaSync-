package com.dbtool.ui;

import com.dbtool.ui.components.StatusBar;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StatusBarTest {
    @Test
    void testStatus() {
        StatusBar sb = new StatusBar();
        sb.setStatus("Ready");
        assertNotNull(sb);
    }
}
