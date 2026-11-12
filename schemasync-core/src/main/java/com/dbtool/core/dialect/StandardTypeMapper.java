package com.dbtool.core.dialect;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

public class StandardTypeMapper implements TypeMapper {
    private static final Map<Integer, String> TYPE_NAMES = new HashMap<>();

    static {
        TYPE_NAMES.put(Types.VARCHAR, "VARCHAR");
        TYPE_NAMES.put(Types.INTEGER, "INTEGER");
        TYPE_NAMES.put(Types.BIGINT, "BIGINT");
        TYPE_NAMES.put(Types.BOOLEAN, "BOOLEAN");
        TYPE_NAMES.put(Types.TIMESTAMP, "TIMESTAMP");
        TYPE_NAMES.put(Types.DECIMAL, "DECIMAL");
    }

    @Override
    public String getTypeName(int jdbcType, int precision, int scale) {
        String base = TYPE_NAMES.getOrDefault(jdbcType, "OTHER");
        if (jdbcType == Types.VARCHAR && precision > 0) return base + "(" + precision + ")";
        if (jdbcType == Types.DECIMAL && precision > 0) return base + "(" + precision + "," + scale + ")";
        return base;
    }

    @Override
    public int getJdbcType(String dataTypeName) {
        return Types.OTHER;
    }
}
