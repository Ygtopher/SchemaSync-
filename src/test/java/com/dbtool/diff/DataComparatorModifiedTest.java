package com.dbtool.diff;

import com.dbtool.core.diff.DataComparator;
import com.dbtool.core.diff.DataDiffResult;
import com.dbtool.core.diff.DifferenceType;
import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class DataComparatorModifiedTest {
    @Test
    void testModifiedCell() {
        QueryResult q1 = new QueryResult();
        q1.setColumnNames(Arrays.asList("id", "name"));
        q1.addRow(new RowData(Arrays.asList(1, "Alice")));

        QueryResult q2 = new QueryResult();
        q2.setColumnNames(Arrays.asList("id", "name"));
        q2.addRow(new RowData(Arrays.asList(1, "Alicia")));

        DataComparator comp = new DataComparator();
        DataDiffResult res = comp.compare("users", q1, q2, 0);
        assertEquals(1, res.getRowDiffs().size());
        assertEquals(DifferenceType.MODIFIED, res.getRowDiffs().get(0).getType());
        assertEquals(1, res.getRowDiffs().get(0).getCellDiffs().size());
    }
}
