package com.dbtool.diff;

import com.dbtool.core.dialect.GenericSqlDialect;
import com.dbtool.core.diff.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class DataSyncSqlGeneratorTest {
    @Test
    void testSyncGen() {
        DataDiffResult res = new DataDiffResult("users");
        res.addRowDiff(new RowDiff(10, DifferenceType.REMOVED, null, null));
        DataSyncSqlGenerator gen = new DataSyncSqlGenerator(new GenericSqlDialect());
        List<String> sqls = gen.generateSyncSql(res);
        assertEquals(1, sqls.size());
        assertTrue(sqls.get(0).contains("DELETE FROM \"users\" WHERE id = 10"));
    }
}
