package com.dbtool.diff;

import com.dbtool.core.diff.PrimaryKeyComparator;
import com.dbtool.core.model.PrimaryKeyMetadata;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class PrimaryKeyComparatorTest {
    @Test
    void testPkComparison() {
        PrimaryKeyMetadata pk1 = new PrimaryKeyMetadata("pk1", Arrays.asList("org_id", "user_id"));
        PrimaryKeyMetadata pk2 = new PrimaryKeyMetadata("pk2", Arrays.asList("org_id", "user_id"));
        assertTrue(PrimaryKeyComparator.areEqual(pk1, pk2));

        PrimaryKeyMetadata pk3 = new PrimaryKeyMetadata("pk3", Arrays.asList("user_id", "org_id"));
        assertFalse(PrimaryKeyComparator.areEqual(pk1, pk3));
    }
}
