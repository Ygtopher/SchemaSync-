package com.dbtool.core.diff;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import java.util.*;

public class DataComparator {
    public DataDiffResult compare(String tableName, QueryResult source, QueryResult target, int pkIndex) {
        DataDiffResult result = new DataDiffResult(tableName);
        result.setColumnNames(source.getColumnNames());

        Map<Object, RowData> targetMap = new HashMap<>();
        for (RowData r : target.getRows()) {
            targetMap.put(r.getValue(pkIndex), r);
        }

        for (RowData sRow : source.getRows()) {
            Object pk = sRow.getValue(pkIndex);
            RowData tRow = targetMap.remove(pk);
            if (tRow == null) {
                result.addRowDiff(new RowDiff(pk, DifferenceType.REMOVED, sRow, null));
            } else {
                RowDiff diff = compareRows(pk, sRow, tRow, source.getColumnNames());
                if (diff.getType() != DifferenceType.IDENTICAL) {
                    result.addRowDiff(diff);
                }
            }
        }

        for (Map.Entry<Object, RowData> entry : targetMap.entrySet()) {
            result.addRowDiff(new RowDiff(entry.getKey(), DifferenceType.ADDED, null, entry.getValue()));
        }

        return result;
    }

    private RowDiff compareRows(Object pk, RowData sRow, RowData tRow, List<String> colNames) {
        RowDiff diff = new RowDiff(pk, DifferenceType.IDENTICAL, sRow, tRow);
        for (int i = 0; i < sRow.size() && i < tRow.size(); i++) {
            Object sVal = sRow.getValue(i);
            Object tVal = tRow.getValue(i);
            if (!Objects.equals(sVal, tVal)) {
                diff.addCellDiff(new CellDiff(colNames.get(i), sVal, tVal));
            }
        }
        if (!diff.getCellDiffs().isEmpty()) {
            return new RowDiff(pk, DifferenceType.MODIFIED, sRow, tRow);
        }
        return diff;
    }
}
