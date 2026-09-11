package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SqlFormatterTest {
    @Test
    void testFormat() {
        String formatted = SqlFormatter.format("select * from users where id = 1");
        assertTrue(formatted.contains("SELECT"));
        assertTrue(formatted.contains("\nFROM"));
    }
}
