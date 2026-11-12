package com.dbtool.query;

import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class RowDataTest {
    @Test
    void testRowValues() {
        RowData row = new RowData(Arrays.asList(101, "Alice", true));
        assertEquals(3, row.size());
        assertEquals(101, row.getValue(0));
        assertEquals("Alice", row.getValue(1));
        assertEquals(true, row.getValue(2));
        assertNull(row.getValue(99));
    }
}
