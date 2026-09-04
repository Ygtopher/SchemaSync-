package com.dbtool.core.model;

import java.io.Serializable;

public class DataTypeInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String typeName;
    private final int dataType;
    private final int precision;
    private final int scale;

    public DataTypeInfo(String typeName, int dataType, int precision, int scale) {
        this.typeName = typeName;
        this.dataType = dataType;
        this.precision = precision;
        this.scale = scale;
    }

    public String getTypeName() { return typeName; }
    public int getDataType() { return dataType; }
    public int getPrecision() { return precision; }
    public int getScale() { return scale; }
}
