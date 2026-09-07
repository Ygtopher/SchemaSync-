package com.dbtool.core.diff;

import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;
import java.util.Objects;

public class SchemaComparator {
    private final DiffOptions options;

    public SchemaComparator() {
        this(new DiffOptions());
    }

    public SchemaComparator(DiffOptions options) {
        this.options = options != null ? options : new DiffOptions();
    }

    public TableDiff compareTables(TableMetadata source, TableMetadata target) {
        String tableName = source != null ? source.getTableName() : (target != null ? target.getTableName() : "UNKNOWN");
        if (source == null && target == null) return new TableDiff(tableName, DifferenceType.IDENTICAL);
        if (source == null) return new TableDiff(tableName, DifferenceType.ADDED);
        if (target == null) return new TableDiff(tableName, DifferenceType.REMOVED);

        TableDiff tableDiff = new TableDiff(tableName, DifferenceType.IDENTICAL);
        for (ColumnMetadata sCol : source.getColumns()) {
            ColumnMetadata tCol = target.findColumn(sCol.getColumnName());
            if (tCol == null) {
                tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.REMOVED, sCol, null, "Column missing in target"));
            } else {
                if (!sCol.getTypeName().equalsIgnoreCase(tCol.getTypeName())) {
                    tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.MODIFIED, sCol, tCol, "Type mismatch: " + sCol.getTypeName() + " vs " + tCol.getTypeName()));
                }
                if (sCol.isNullable() != tCol.isNullable()) {
                    tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.MODIFIED, sCol, tCol, "Nullability changed: " + sCol.isNullable() + " -> " + tCol.isNullable()));
                }
                if (!options.isIgnoreDefaultValues() && !Objects.equals(sCol.getDefaultValue(), tCol.getDefaultValue())) {
                    tableDiff.addColumnDiff(new ColumnDiff(sCol.getColumnName(), DifferenceType.MODIFIED, sCol, tCol, "Default value changed: " + sCol.getDefaultValue() + " -> " + tCol.getDefaultValue()));
                }
            }
        }
        for (ColumnMetadata tCol : target.getColumns()) {
            if (source.findColumn(tCol.getColumnName()) == null) {
                tableDiff.addColumnDiff(new ColumnDiff(tCol.getColumnName(), DifferenceType.ADDED, null, tCol, "New column in target"));
            }
        }
        return tableDiff;
    }
}
