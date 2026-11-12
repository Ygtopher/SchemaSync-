package com.dbtool.dialect;

import com.dbtool.core.dialect.StandardTypeMapper;
import org.junit.jupiter.api.Test;
import java.sql.Types;
import static org.junit.jupiter.api.Assertions.*;

public class StandardTypeMapperTest {
    @Test
    void testTypeNames() {
        StandardTypeMapper mapper = new StandardTypeMapper();
        assertEquals("VARCHAR(255)", mapper.getTypeName(Types.VARCHAR, 255, 0));
        assertEquals("DECIMAL(10,2)", mapper.getTypeName(Types.DECIMAL, 10, 2));
        assertEquals("INTEGER", mapper.getTypeName(Types.INTEGER, 0, 0));
    }
}
