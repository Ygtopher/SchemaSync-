package com.dbtool.config;

import com.dbtool.core.config.SettingsManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SettingsManagerTest {
    @Test
    void testSingleton() {
        assertNotNull(SettingsManager.getInstance().getSettings());
    }
}
