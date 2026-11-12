package com.dbtool.query;

import com.dbtool.core.query.ValueFormatter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ValueFormatterTest {
    @Test
    void testBinaryFormatting() {
        byte[] bytes = new byte[]{0x0A, 0x1F};
        assertEquals("0x0A1F", ValueFormatter.format(bytes));
        assertEquals("NULL", ValueFormatter.format(null));
        assertEquals("hello", ValueFormatter.format("hello"));
    }
}
