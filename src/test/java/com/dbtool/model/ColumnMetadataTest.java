package com.dbtool.model;

import com.dbtool.core.model.ColumnMetadata;
import org.junit.jupiter.api.Test;
import java.sql.Types;
import static org.junit.jupiter.api.Assertions.*;

public class ColumnMetadataTest {
    @Test
    void testColumnAttributes() {
        ColumnMetadata col = new ColumnMetadata("id", "BIGINT", Types.BIGINT);
        col.setPrimaryKey(true);
        col.setNullable(false);
        assertTrue(col.isPrimaryKey());
        assertFalse(col.isNullable());
        assertEquals("id", col.getColumnName());
    }
}
