package com.dbtool.core.diff;

import com.dbtool.core.model.IndexMetadata;
import java.util.Objects;

public class IndexComparator {
    public static boolean areEqual(IndexMetadata idx1, IndexMetadata idx2) {
        if (idx1 == null && idx2 == null) return true;
        if (idx1 == null || idx2 == null) return false;
        return idx1.isUnique() == idx2.isUnique() && Objects.equals(idx1.getColumns(), idx2.getColumns());
    }
}
