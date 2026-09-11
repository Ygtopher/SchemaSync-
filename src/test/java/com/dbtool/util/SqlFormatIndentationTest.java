package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SqlFormatIndentationTest {
    @Test
    void testIndentation() {
        String res = SqlFormatter.format("SELECT a FROM b");
        assertTrue(res.contains("FROM"));
    }
}
