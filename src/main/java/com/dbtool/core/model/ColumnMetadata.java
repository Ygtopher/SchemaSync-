package com.dbtool.core.model;

import java.io.Serializable;

public class ColumnMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String columnName;
    private String typeName;
    private int jdbcType;
    private int columnSize;
    private int decimalDigits;
    private boolean nullable;
    private String defaultValue;
    private boolean primaryKey;
    private int ordinalPosition;

    public ColumnMetadata() {}

    public ColumnMetadata(String columnName, String typeName, int jdbcType) {
        this.columnName = columnName;
        this.typeName = typeName;
        this.jdbcType = jdbcType;
    }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public int getJdbcType() { return jdbcType; }
    public void setJdbcType(int jdbcType) { this.jdbcType = jdbcType; }

    public int getColumnSize() { return columnSize; }
    public void setColumnSize(int columnSize) { this.columnSize = columnSize; }

    public int getDecimalDigits() { return decimalDigits; }
    public void setDecimalDigits(int decimalDigits) { this.decimalDigits = decimalDigits; }

    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }

    public int getOrdinalPosition() { return ordinalPosition; }
    public void setOrdinalPosition(int ordinalPosition) { this.ordinalPosition = ordinalPosition; }
}
