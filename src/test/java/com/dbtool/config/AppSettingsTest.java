package com.dbtool.config;

import com.dbtool.core.config.AppSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AppSettingsTest {
    @Test
    void testDefaults() {
        AppSettings s = new AppSettings();
        assertEquals("Dark", s.getTheme());
        assertEquals(1000, s.getMaxQueryRows());
    }
}
