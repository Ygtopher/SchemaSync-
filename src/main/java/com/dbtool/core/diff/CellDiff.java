package com.dbtool.core.diff;

import java.io.Serializable;

public class CellDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    private String columnName;
    private Object sourceValue;
    private Object targetValue;

    public CellDiff(String columnName, Object sourceValue, Object targetValue) {
        this.columnName = columnName;
        this.sourceValue = sourceValue;
        this.targetValue = targetValue;
    }

    public String getColumnName() { return columnName; }
    public Object getSourceValue() { return sourceValue; }
    public Object getTargetValue() { return targetValue; }
}
