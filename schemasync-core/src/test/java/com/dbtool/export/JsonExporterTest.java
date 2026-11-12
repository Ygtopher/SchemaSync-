package com.dbtool.export;

import com.dbtool.core.export.ExportOptions;
import com.dbtool.core.export.ExportResult;
import com.dbtool.core.export.JsonExporter;
import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class JsonExporterTest {
    @Test
    void testJsonExport() throws Exception {
        QueryResult qr = new QueryResult();
        qr.setColumnNames(Arrays.asList("id", "role"));
        qr.addRow(new RowData(Arrays.asList(1, "ADMIN")));

        File tmp = File.createTempFile("test_json", ".json");
        tmp.deleteOnExit();

        JsonExporter exporter = new JsonExporter();
        ExportResult res = exporter.export(qr, tmp, new ExportOptions());
        assertEquals(1, res.getRowCount());
        assertTrue(tmp.length() > 0);
    }
}
