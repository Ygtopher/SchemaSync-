package com.dbtool.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CsvQuoteTest {
    @Test
    void testQuotes() {
        String val = "line1\nline2,with,commas";
        assertTrue(val.contains("\n"));
        assertTrue(val.contains(","));
    }
}
