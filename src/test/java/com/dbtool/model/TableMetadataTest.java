package com.dbtool.model;

import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;
import org.junit.jupiter.api.Test;
import java.sql.Types;
import static org.junit.jupiter.api.Assertions.*;

public class TableMetadataTest {
    @Test
    void testAddColumn() {
        TableMetadata table = new TableMetadata("public", "users");
        table.addColumn(new ColumnMetadata("id", "INT", Types.INTEGER));
        table.addColumn(new ColumnMetadata("username", "VARCHAR", Types.VARCHAR));
        assertEquals(2, table.getColumns().size());
        assertEquals("users", table.getTableName());
    }
}
