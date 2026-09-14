package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ThemeManagerTest {
    @Test
    void testTheme() {
        assertFalse(ThemeManager.isDarkMode());
    }
}
