package com.dbtool.core.diff;

public class MarkdownDiffReporter implements DiffReporter {
    @Override
    public String generateReport(SchemaDiffResult schemaDiff) {
        StringBuilder sb = new StringBuilder("# Schema Diff Report\n\n");
        for (TableDiff td : schemaDiff.getTableDiffs()) {
            sb.append("## Table: ").append(td.getTableName()).append(" (").append(td.getType()).append(")\n");
            for (ColumnDiff cd : td.getColumnDiffs()) {
                sb.append("- ").append(cd.getType()).append(": ").append(cd.getColumnName()).append(" (").append(cd.getDescription()).append(")\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String generateReport(DataDiffResult dataDiff) {
        StringBuilder sb = new StringBuilder("# Data Diff Report: " + dataDiff.getTableName() + "\n\n");
        sb.append("Total differences: ").append(dataDiff.getRowDiffs().size()).append("\n");
        return sb.toString();
    }
}
