package com.dbtool.core.export;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ExportOptions implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean includeHeaders = true;
    private char delimiter = ',';
    private Charset charset = StandardCharsets.UTF_8;
    private String tableName = "exported_data";
    private int batchSize = 1000;

    public boolean isIncludeHeaders() { return includeHeaders; }
    public void setIncludeHeaders(boolean includeHeaders) { this.includeHeaders = includeHeaders; }
    public char getDelimiter() { return delimiter; }
    public void setDelimiter(char delimiter) { this.delimiter = delimiter; }
    public Charset getCharset() { return charset; }
    public void setCharset(Charset charset) { this.charset = charset; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
