package com.dbtool.diff;

import com.dbtool.core.diff.*;
import com.dbtool.core.model.ColumnMetadata;
import org.junit.jupiter.api.Test;
import java.sql.Types;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class DdlMigrationGeneratorTest {
    @Test
    void testMigrationGeneration() {
        TableDiff diff = new TableDiff("customers", DifferenceType.MODIFIED);
        ColumnMetadata col = new ColumnMetadata("address", "VARCHAR(255)", Types.VARCHAR);
        col.setNullable(true);
        diff.addColumnDiff(new ColumnDiff("address", DifferenceType.ADDED, null, col, "added"));

        DdlMigrationGenerator gen = new DdlMigrationGenerator();
        List<String> ddl = gen.generateMigrationSql(diff);
        assertEquals(1, ddl.size());
        assertTrue(ddl.get(0).startsWith("ALTER TABLE customers ADD COLUMN address"));
    }
}
