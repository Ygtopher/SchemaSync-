package com.dbtool.core.diff;

import com.dbtool.core.model.ColumnMetadata;
import java.util.ArrayList;
import java.util.List;

public class DdlMigrationGenerator {
    public List<String> generateMigrationSql(TableDiff diff) {
        List<String> sqls = new ArrayList<>();
        String tbl = diff.getTableName();
        for (ColumnDiff cDiff : diff.getColumnDiffs()) {
            if (cDiff.getType() == DifferenceType.ADDED) {
                ColumnMetadata col = cDiff.getTargetColumn();
                String def = "ALTER TABLE " + tbl + " ADD COLUMN " + cDiff.getColumnName() + " " + col.getTypeName();
                if (!col.isNullable()) def += " NOT NULL";
                sqls.add(def + ";");
            } else if (cDiff.getType() == DifferenceType.REMOVED) {
                sqls.add("ALTER TABLE " + tbl + " DROP COLUMN " + cDiff.getColumnName() + ";");
            }
        }
        return sqls;
    }
}
