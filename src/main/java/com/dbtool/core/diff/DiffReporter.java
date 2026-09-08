package com.dbtool.core.diff;

public interface DiffReporter {
    String generateReport(SchemaDiffResult schemaDiff);
    String generateReport(DataDiffResult dataDiff);
}
