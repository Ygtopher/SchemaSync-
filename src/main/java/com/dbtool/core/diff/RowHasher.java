package com.dbtool.core.diff;

import com.dbtool.core.query.RowData;
import java.util.Objects;

public class RowHasher {
    public static int hashRow(RowData row) {
        if (row == null) return 0;
        int h = 1;
        for (int i = 0; i < row.size(); i++) {
            h = 31 * h + Objects.hashCode(row.getValue(i));
        }
        return h;
    }
}
