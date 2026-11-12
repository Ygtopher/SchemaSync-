package com.dbtool.core.model;

import java.io.Serializable;

public class TableRelationship implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sourceTable;
    private String sourceColumn;
    private String targetTable;
    private String targetColumn;

    public TableRelationship(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
        this.sourceTable = sourceTable;
        this.sourceColumn = sourceColumn;
        this.targetTable = targetTable;
        this.targetColumn = targetColumn;
    }

    public String getSourceTable() { return sourceTable; }
    public String getSourceColumn() { return sourceColumn; }
    public String getTargetTable() { return targetTable; }
    public String getTargetColumn() { return targetColumn; }
}
