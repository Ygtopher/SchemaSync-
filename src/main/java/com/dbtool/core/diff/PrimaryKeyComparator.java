package com.dbtool.core.diff;

import com.dbtool.core.model.PrimaryKeyMetadata;
import java.util.Objects;

public class PrimaryKeyComparator {
    public static boolean areEqual(PrimaryKeyMetadata pk1, PrimaryKeyMetadata pk2) {
        if (pk1 == null && pk2 == null) return true;
        if (pk1 == null || pk2 == null) return false;
        return Objects.equals(pk1.getColumns(), pk2.getColumns());
    }
}
