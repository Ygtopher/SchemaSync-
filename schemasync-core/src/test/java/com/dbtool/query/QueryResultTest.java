package com.dbtool.query;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class QueryResultTest {
    @Test
    void testQueryResult() {
        QueryResult qr = new QueryResult();
        qr.setColumnNames(Arrays.asList("id", "email"));
        qr.addRow(new RowData(Arrays.asList(1, "test@example.com")));
        assertEquals(2, qr.getColumnNames().size());
        assertEquals(1, qr.getRows().size());
    }
}
