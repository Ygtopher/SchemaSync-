package com.dbtool.diff;

import com.dbtool.core.diff.DifferenceType;
import com.dbtool.core.diff.SchemaComparator;
import com.dbtool.core.diff.TableDiff;
import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;
import org.junit.jupiter.api.Test;
import java.sql.Types;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaComparatorMissingColTest {
    @Test
    void testMissingColumn() {
        TableMetadata t1 = new TableMetadata("public", "users");
        t1.addColumn(new ColumnMetadata("id", "INT", Types.INTEGER));
        t1.addColumn(new ColumnMetadata("phone", "VARCHAR", Types.VARCHAR));

        TableMetadata t2 = new TableMetadata("public", "users");
        t2.addColumn(new ColumnMetadata("id", "INT", Types.INTEGER));

        SchemaComparator comp = new SchemaComparator();
        TableDiff diff = comp.compareTables(t1, t2);
        assertEquals(1, diff.getColumnDiffs().size());
        assertEquals(DifferenceType.REMOVED, diff.getColumnDiffs().get(0).getType());
    }
}
