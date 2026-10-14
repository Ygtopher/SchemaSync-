package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for In-App UpdateChecker SemVer evaluation.
 */
public class UpdateCheckerTest {

    @Test
    void testNewerPatchVersion() {
        assertTrue(UpdateChecker.isNewerVersion("1.6.1", "1.6.0"));
        assertTrue(UpdateChecker.isNewerVersion("v1.6.1", "1.6.0"));
        assertTrue(UpdateChecker.isNewerVersion("1.6.1", "v1.6.0"));
        assertTrue(UpdateChecker.isNewerVersion("v1.6.1", "v1.6.0"));
    }

    @Test
    void testNewerMinorAndMajorVersion() {
        assertTrue(UpdateChecker.isNewerVersion("1.7.0", "1.6.1"));
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.6.1"));
        assertTrue(UpdateChecker.isNewerVersion("1.10.0", "1.9.5"));
    }

    @Test
    void testSameVersion() {
        assertFalse(UpdateChecker.isNewerVersion("1.6.0", "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("v1.6.0", "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.6.1", "1.6.1"));
    }

    @Test
    void testOlderVersion() {
        assertFalse(UpdateChecker.isNewerVersion("1.5.1", "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("v1.4.0", "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.6.0", "1.6.1"));
    }

    @Test
    void testPreReleaseSuffixes() {
        assertTrue(UpdateChecker.isNewerVersion("1.7.0-beta1", "1.6.1"));
        assertFalse(UpdateChecker.isNewerVersion("1.6.1-rc1", "1.6.1"));
    }

    @Test
    void testNullOrEmpty() {
        assertFalse(UpdateChecker.isNewerVersion(null, "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("", "1.6.0"));
        assertTrue(UpdateChecker.isNewerVersion("1.6.1", null));
        assertTrue(UpdateChecker.isNewerVersion("1.6.1", ""));
    }
}
