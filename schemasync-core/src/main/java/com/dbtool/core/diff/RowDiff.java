package com.dbtool.core.diff;

import com.dbtool.core.query.RowData;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RowDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    private Object primaryKey;
    private DifferenceType type;
    private RowData sourceRow;
    private RowData targetRow;
    private List<CellDiff> cellDiffs = new ArrayList<>();

    public RowDiff(Object primaryKey, DifferenceType type, RowData sourceRow, RowData targetRow) {
        this.primaryKey = primaryKey;
        this.type = type;
        this.sourceRow = sourceRow;
        this.targetRow = targetRow;
    }

    public Object getPrimaryKey() { return primaryKey; }
    public DifferenceType getType() { return type; }
    public RowData getSourceRow() { return sourceRow; }
    public RowData getTargetRow() { return targetRow; }
    public List<CellDiff> getCellDiffs() { return cellDiffs; }
    public void addCellDiff(CellDiff diff) { cellDiffs.add(diff); }
}
