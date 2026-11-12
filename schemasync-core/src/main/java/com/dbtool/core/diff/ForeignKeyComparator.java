package com.dbtool.core.diff;

import com.dbtool.core.model.ForeignKeyMetadata;
import java.util.Objects;

public class ForeignKeyComparator {
    public static boolean areEqual(ForeignKeyMetadata fk1, ForeignKeyMetadata fk2) {
        if (fk1 == null && fk2 == null) return true;
        if (fk1 == null || fk2 == null) return false;
        return Objects.equals(fk1.getFkColumn(), fk2.getFkColumn()) &&
               Objects.equals(fk1.getPkTable(), fk2.getPkTable()) &&
               Objects.equals(fk1.getPkColumn(), fk2.getPkColumn());
    }
}
