package com.dbtool.export;

import com.dbtool.core.export.CsvExporter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EndToEndExportTest {
    @Test
    void testExportPipeline() {
        CsvExporter exp = new CsvExporter();
        assertNotNull(exp.getFormat());
    }
}
