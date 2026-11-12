package com.dbtool.core.diff;

import com.dbtool.core.model.ColumnMetadata;
import java.io.Serializable;

public class ColumnDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    private String columnName;
    private DifferenceType type;
    private ColumnMetadata sourceColumn;
    private ColumnMetadata targetColumn;
    private String description;

    public ColumnDiff(String columnName, DifferenceType type, ColumnMetadata source, ColumnMetadata target, String desc) {
        this.columnName = columnName;
        this.type = type;
        this.sourceColumn = source;
        this.targetColumn = target;
        this.description = desc;
    }

    public String getColumnName() { return columnName; }
    public DifferenceType getType() { return type; }
    public ColumnMetadata getSourceColumn() { return sourceColumn; }
    public ColumnMetadata getTargetColumn() { return targetColumn; }
    public String getDescription() { return description; }
}
