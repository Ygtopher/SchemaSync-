package com.dbtool.diff;

import com.dbtool.core.diff.SchemaComparator;
import com.dbtool.core.diff.TableDiff;
import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;
import org.junit.jupiter.api.Test;
import java.sql.Types;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaComparatorTest {
    @Test
    void testIdenticalTables() {
        TableMetadata t1 = new TableMetadata("public", "orders");
        t1.addColumn(new ColumnMetadata("id", "INT", Types.INTEGER));
        TableMetadata t2 = new TableMetadata("public", "orders");
        t2.addColumn(new ColumnMetadata("id", "INT", Types.INTEGER));

        SchemaComparator comp = new SchemaComparator();
        TableDiff diff = comp.compareTables(t1, t2);
        assertTrue(diff.getColumnDiffs().isEmpty());
    }

    @Test
    void testNullTypeAndNullColumnSafety() {
        TableMetadata t1 = new TableMetadata("public", "users");
        ColumnMetadata c1 = new ColumnMetadata();
        c1.setColumnName("username");
        c1.setTypeName(null);
        t1.addColumn(c1);

        TableMetadata t2 = new TableMetadata("public", "users");
        ColumnMetadata c2 = new ColumnMetadata();
        c2.setColumnName("username");
        c2.setTypeName("VARCHAR");
        t2.addColumn(c2);

        SchemaComparator comp = new SchemaComparator();
        assertDoesNotThrow(() -> {
            TableDiff diff = comp.compareTables(t1, t2);
            assertNotNull(diff);
            assertFalse(diff.getColumnDiffs().isEmpty(), "Should detect type difference without throwing NullPointerException");
        });
    }
}
