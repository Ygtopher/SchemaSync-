package com.dbtool.core.export;

import com.dbtool.core.query.QueryResult;
import java.io.File;
import java.io.IOException;

public interface DataExporter {
    ExportFormat getFormat();
    ExportResult export(QueryResult queryResult, File destinationFile, ExportOptions options) throws IOException;
}
