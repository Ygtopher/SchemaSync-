package com.dbtool.core.model;

import java.io.Serializable;

public class ForeignKeyMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String fkColumn;
    private String pkTable;
    private String pkColumn;
    private short updateRule;
    private short deleteRule;

    public ForeignKeyMetadata() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFkColumn() { return fkColumn; }
    public void setFkColumn(String fkColumn) { this.fkColumn = fkColumn; }

    public String getPkTable() { return pkTable; }
    public void setPkTable(String pkTable) { this.pkTable = pkTable; }

    public String getPkColumn() { return pkColumn; }
    public void setPkColumn(String pkColumn) { this.pkColumn = pkColumn; }

    public short getUpdateRule() { return updateRule; }
    public void setUpdateRule(short updateRule) { this.updateRule = updateRule; }

    public short getDeleteRule() { return deleteRule; }
    public void setDeleteRule(short deleteRule) { this.deleteRule = deleteRule; }
}
