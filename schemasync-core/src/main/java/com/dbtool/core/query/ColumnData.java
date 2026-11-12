package com.dbtool.core.query;

import java.io.Serializable;

public class ColumnData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final int jdbcType;
    private final String typeName;

    public ColumnData(String name, int jdbcType, String typeName) {
        this.name = name;
        this.jdbcType = jdbcType;
        this.typeName = typeName;
    }

    public String getName() { return name; }
    public int getJdbcType() { return jdbcType; }
    public String getTypeName() { return typeName; }
}
