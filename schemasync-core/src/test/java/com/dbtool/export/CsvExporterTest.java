package com.dbtool.export;

import com.dbtool.core.export.CsvExporter;
import com.dbtool.core.export.ExportOptions;
import com.dbtool.core.export.ExportResult;
import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class CsvExporterTest {
    @Test
    void testCsvExport() throws Exception {
        QueryResult qr = new QueryResult();
        qr.setColumnNames(Arrays.asList("id", "note"));
        qr.addRow(new RowData(Arrays.asList(1, "Hello, World!")));

        File tmp = File.createTempFile("test_export", ".csv");
        tmp.deleteOnExit();

        CsvExporter exporter = new CsvExporter();
        ExportResult res = exporter.export(qr, tmp, new ExportOptions());
        assertEquals(1, res.getRowCount());
        assertTrue(tmp.length() > 0);
    }
}
