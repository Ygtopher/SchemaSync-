package com.dbtool.diff;

import com.dbtool.core.diff.NumericComparator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NumericComparatorTest {
    @Test
    void testEpsilonComparison() {
        assertTrue(NumericComparator.equalsWithEpsilon(1.0000001, 1.0000002, 1e-5));
        assertFalse(NumericComparator.equalsWithEpsilon(1.0, 1.1, 1e-5));
        assertTrue(NumericComparator.equalsWithEpsilon(null, null, 1e-5));
        assertFalse(NumericComparator.equalsWithEpsilon(1.0, null, 1e-5));
    }
}
