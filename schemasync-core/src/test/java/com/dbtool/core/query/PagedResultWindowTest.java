package com.dbtool.core.query;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class PagedResultWindowTest {

    @Test
    void testSlidingWindowEvictsOldPagesAndBoundsMemory() {
        AtomicInteger fetchCount = new AtomicInteger(0);
        // Simulate 1,000,000 virtual rows with pageSize=500 and maxCachedRows=2000 (4 pages)
        PagedResultWindow window = new PagedResultWindow(
                Arrays.asList("id", "label"),
                1_000_000,
                500,
                2000,
                (offset, limit) -> {
                    fetchCount.incrementAndGet();
                    List<RowData> rows = new ArrayList<>(limit);
                    for (int i = 0; i < limit; i++) {
                        int id = offset + i;
                        rows.add(new RowData(new Object[]{id, "row_" + id}));
                    }
                    return rows;
                }
        );

        assertEquals(1_000_000, window.getTotalRowCount());
        assertEquals(4, window.getMaxCachedPages());

        // Scroll across 10 distant pages (rows 0, 1000, ..., 9000)
        for (int p = 0; p < 10; p++) {
            int rowIdx = p * 1000 + 5;
            assertEquals(rowIdx, window.getValueAt(rowIdx, 0));
            assertEquals("row_" + rowIdx, window.getValueAt(rowIdx, 1));
        }

        assertEquals(10, fetchCount.get());
        assertTrue(window.getCachedRowCount() <= 2000,
                "Cached rows must remain bounded at <= 2000; actual=" + window.getCachedRowCount());
        assertEquals(4, window.getCachedPageCount());

        // Accessing already-cached last page must be a cache hit
        int prevFetches = fetchCount.get();
        assertEquals("row_9010", window.getValueAt(9010, 1));
        assertEquals(prevFetches, fetchCount.get());
    }
}
