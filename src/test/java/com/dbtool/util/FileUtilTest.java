package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FileUtilTest {
    @Test
    void testGetExtension() {
        assertEquals("sql", FileUtil.getExtension("dump.sql"));
        assertEquals("gz", FileUtil.getExtension("dump.sql.gz"));
        assertEquals("", FileUtil.getExtension("no_extension"));
    }

    @Test
    void testHasExtension() {
        assertTrue(FileUtil.hasExtension("db.accdb", "accdb", "mdb"));
        assertTrue(FileUtil.hasExtension("db.MDB", "accdb", "mdb"));
        assertFalse(FileUtil.hasExtension("db.sqlite", "accdb", "mdb"));
    }
}
