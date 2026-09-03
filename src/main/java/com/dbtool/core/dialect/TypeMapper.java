package com.dbtool.core.dialect;

import java.sql.Types;

public interface TypeMapper {
    String getTypeName(int jdbcType, int precision, int scale);
    int getJdbcType(String dataTypeName);
}
