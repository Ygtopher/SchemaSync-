package com.dbtool.core.diff;

import com.dbtool.core.dialect.DatabaseDialect;
import java.util.ArrayList;
import java.util.List;

public class DataSyncSqlGenerator {
    private final DatabaseDialect dialect;

    public DataSyncSqlGenerator(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    public List<String> generateSyncSql(DataDiffResult diff) {
        List<String> statements = new ArrayList<>();
        String tbl = dialect.quoteIdentifier(diff.getTableName());
        for (RowDiff r : diff.getRowDiffs()) {
            if (r.getType() == DifferenceType.REMOVED) {
                statements.add("DELETE FROM " + tbl + " WHERE " + r.getPrimaryKey() + ";");
            }
        }
        return statements;
    }
}
