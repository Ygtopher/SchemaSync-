package com.dbtool.core.diff;

import java.util.ArrayList;
import java.util.List;

public class DdlMigrationGenerator {
    public List<String> generateMigrationSql(TableDiff diff) {
        List<String> sqls = new ArrayList<>();
        String tbl = diff.getTableName();
        for (ColumnDiff cDiff : diff.getColumnDiffs()) {
            if (cDiff.getType() == DifferenceType.ADDED) {
                sqls.add("ALTER TABLE " + tbl + " ADD COLUMN " + cDiff.getColumnName() + " " + cDiff.getTargetColumn().getTypeName() + ";");
            } else if (cDiff.getType() == DifferenceType.REMOVED) {
                sqls.add("ALTER TABLE " + tbl + " DROP COLUMN " + cDiff.getColumnName() + ";");
            }
        }
        return sqls;
    }
}
