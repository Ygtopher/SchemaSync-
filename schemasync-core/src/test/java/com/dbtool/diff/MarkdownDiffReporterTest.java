package com.dbtool.diff;

import com.dbtool.core.diff.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MarkdownDiffReporterTest {
    @Test
    void testReporter() {
        SchemaDiffResult res = new SchemaDiffResult();
        res.addTableDiff(new TableDiff("orders", DifferenceType.MODIFIED));
        MarkdownDiffReporter reporter = new MarkdownDiffReporter();
        String md = reporter.generateReport(res);
        assertTrue(md.contains("Table: orders"));
    }
}
