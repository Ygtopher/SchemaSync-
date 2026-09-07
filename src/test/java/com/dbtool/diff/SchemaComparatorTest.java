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
}
