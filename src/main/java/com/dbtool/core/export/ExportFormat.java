package com.dbtool.core.export;

public enum ExportFormat {
    CSV("Comma Separated Values (*.csv)", "csv"),
    EXCEL_XLSX("Microsoft Excel Workbook (*.xlsx)", "xlsx"),
    SQL_INSERTS("SQL Insert Statements (*.sql)", "sql"),
    JSON("JSON Array of Objects (*.json)", "json"),
    MARKDOWN("Markdown Table (*.md)", "md");

    private final String description;
    private final String fileExtension;

    ExportFormat(String description, String fileExtension) {
        this.description = description;
        this.fileExtension = fileExtension;
    }

    public String getDescription() { return description; }
    public String getFileExtension() { return fileExtension; }
}
