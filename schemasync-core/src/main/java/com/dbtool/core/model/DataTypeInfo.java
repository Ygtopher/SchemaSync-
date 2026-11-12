package com.dbtool.core.model;

import java.io.Serializable;

/**
 * Immutable Java 21 record describing JDBC column type metadata.
 */
public record DataTypeInfo(String typeName, int dataType, int precision, int scale) implements Serializable {
    private static final long serialVersionUID = 2L;

    public String getTypeName() { return typeName; }
    public int getDataType() { return dataType; }
    public int getPrecision() { return precision; }
    public int getScale() { return scale; }
}
