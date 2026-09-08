package com.dbtool.diff;

import com.dbtool.core.diff.RowHasher;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class RowHasherTest {
    @Test
    void testHashConsistency() {
        RowData r1 = new RowData(Arrays.asList(1, "Test"));
        RowData r2 = new RowData(Arrays.asList(1, "Test"));
        assertEquals(RowHasher.hashRow(r1), RowHasher.hashRow(r2));
    }
}
