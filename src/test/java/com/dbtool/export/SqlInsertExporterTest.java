package com.dbtool.export;

import com.dbtool.core.export.ExportOptions;
import com.dbtool.core.export.ExportResult;
import com.dbtool.core.export.SqlInsertExporter;
import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class SqlInsertExporterTest {
    @Test
    void testSqlInsertExport() throws Exception {
        QueryResult qr = new QueryResult();
        qr.setColumnNames(Arrays.asList("id", "username"));
        qr.addRow(new RowData(Arrays.asList(1, "O'Reilly")));

        File tmp = File.createTempFile("test_inserts", ".sql");
        tmp.deleteOnExit();

        SqlInsertExporter exporter = new SqlInsertExporter();
        ExportResult res = exporter.export(qr, tmp, new ExportOptions());
        assertEquals(1, res.getRowCount());
        assertTrue(tmp.length() > 0);
    }
}
