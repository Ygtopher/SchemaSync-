package com.dbtool.diff;

import com.dbtool.core.diff.CompositeKey;
import com.dbtool.core.diff.RowHasher;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class RowHasherTest {

    @Test
    void testHashConsistency() {
        RowData r1 = new RowData(Arrays.asList(1, "Test"));
        RowData r2 = new RowData(Arrays.asList(1, "Test"));
        assertEquals(RowHasher.hashRow(r1), RowHasher.hashRow(r2));
        assertEquals(RowHasher.hashRowSha256(r1), RowHasher.hashRowSha256(r2));
        assertEquals(64, RowHasher.hashRowSha256(r1).length());
    }

    @Test
    void testCrossEngineCanonicalEquivalenceAndDelimiterSafety() {
        RowData postgresStyle = new RowData(Arrays.asList(42, new BigDecimal("10.50"), Timestamp.from(Instant.parse("2026-01-02T03:04:05Z"))));
        RowData mysqlStyle = new RowData(Arrays.asList(42L, new BigDecimal("10.5"), Timestamp.from(Instant.parse("2026-01-02T03:04:05.000Z"))));
        assertEquals(RowHasher.hashRowSha256(postgresStyle), RowHasher.hashRowSha256(mysqlStyle));

        // Ensure length prefix prevents concatenation boundary collisions ("a|b", "c") vs ("a", "b|c")
        RowData boundaryA = new RowData(Arrays.asList("a|b", "c"));
        RowData boundaryB = new RowData(Arrays.asList("a", "b|c"));
        assertNotEquals(RowHasher.hashRowSha256(boundaryA), RowHasher.hashRowSha256(boundaryB));

        // CompositeKey normalized equality
        assertEquals(new CompositeKey(42, new BigDecimal("10.50")), new CompositeKey(42L, new BigDecimal("10.5")));
        assertTrue(new CompositeKey(1).compareTo(new CompositeKey(2L)) < 0);
    }
}
