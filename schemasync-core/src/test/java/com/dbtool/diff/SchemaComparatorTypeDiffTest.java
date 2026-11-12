package com.dbtool.diff;

import com.dbtool.core.diff.DifferenceType;
import com.dbtool.core.diff.SchemaComparator;
import com.dbtool.core.diff.TableDiff;
import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;
import org.junit.jupiter.api.Test;
import java.sql.Types;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaComparatorTypeDiffTest {
    @Test
    void testTypeMismatch() {
        TableMetadata t1 = new TableMetadata("public", "items");
        t1.addColumn(new ColumnMetadata("price", "INT", Types.INTEGER));

        TableMetadata t2 = new TableMetadata("public", "items");
        t2.addColumn(new ColumnMetadata("price", "DECIMAL", Types.DECIMAL));

        SchemaComparator comp = new SchemaComparator();
        TableDiff diff = comp.compareTables(t1, t2);
        assertEquals(1, diff.getColumnDiffs().size());
        assertEquals(DifferenceType.MODIFIED, diff.getColumnDiffs().get(0).getType());
    }
}
