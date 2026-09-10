package com.dbtool.export;

import com.dbtool.core.export.ExportOptions;
import com.dbtool.core.export.ExportResult;
import com.dbtool.core.export.MarkdownExporter;
import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class MarkdownExporterTest {
    @Test
    void testMdExport() throws Exception {
        QueryResult qr = new QueryResult();
        qr.setColumnNames(Arrays.asList("col1", "col2"));
        qr.addRow(new RowData(Arrays.asList("A", "B")));

        File tmp = File.createTempFile("test_md", ".md");
        tmp.deleteOnExit();

        MarkdownExporter exporter = new MarkdownExporter();
        ExportResult res = exporter.export(qr, tmp, new ExportOptions());
        assertTrue(res.getFileSize() > 0);
    }
}
