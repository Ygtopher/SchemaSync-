package com.dbtool;

import com.dbtool.core.diff.SchemaComparator;
import com.dbtool.core.model.TableMetadata;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EndToEndDiffTest {
    @Test
    void testPipeline() {
        SchemaComparator sc = new SchemaComparator();
        assertNotNull(sc.compareTables(new TableMetadata("s", "t1"), new TableMetadata("s", "t2")));
    }
}
