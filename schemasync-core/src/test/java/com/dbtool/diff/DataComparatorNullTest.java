package com.dbtool.diff;

import com.dbtool.core.diff.DataComparator;
import com.dbtool.core.diff.DataDiffResult;
import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class DataComparatorNullTest {
    @Test
    void testNullMatching() {
        QueryResult q1 = new QueryResult();
        q1.setColumnNames(Arrays.asList("id", "bio"));
        q1.addRow(new RowData(Arrays.asList(1, null)));

        QueryResult q2 = new QueryResult();
        q2.setColumnNames(Arrays.asList("id", "bio"));
        q2.addRow(new RowData(Arrays.asList(1, null)));

        DataComparator comp = new DataComparator();
        DataDiffResult res = comp.compare("users", q1, q2, 0);
        assertTrue(res.getRowDiffs().isEmpty());
    }
}
