package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StringUtilTest {
    @Test
    void testIsBlank() {
        assertTrue(StringUtil.isBlank(null));
        assertTrue(StringUtil.isBlank(""));
        assertTrue(StringUtil.isBlank("   "));
        assertFalse(StringUtil.isBlank("abc"));
    }

    @Test
    void testStripTrailingSemicolon() {
        assertEquals("SELECT 1", StringUtil.stripTrailingSemicolon("SELECT 1;"));
        assertEquals("SELECT 1", StringUtil.stripTrailingSemicolon("SELECT 1;;;  "));
        assertEquals("SELECT 1", StringUtil.stripTrailingSemicolon("SELECT 1"));
    }
}
