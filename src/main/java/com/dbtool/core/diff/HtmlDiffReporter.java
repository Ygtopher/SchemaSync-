package com.dbtool.core.diff;

public class HtmlDiffReporter implements DiffReporter {
    @Override
    public String generateReport(SchemaDiffResult schemaDiff) {
        StringBuilder sb = new StringBuilder("<html><body><h1>Schema Diff</h1><ul>");
        for (TableDiff td : schemaDiff.getTableDiffs()) {
            sb.append("<li><b>").append(td.getTableName()).append("</b></li>");
        }
        sb.append("</ul></body></html>");
        return sb.toString();
    }

    @Override
    public String generateReport(DataDiffResult dataDiff) {
        return "<html><body><h1>Data Diff: " + dataDiff.getTableName() + "</h1></body></html>";
    }
}
