package com.dbtool.core.config;

import java.io.Serializable;

public class AppSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    private String theme = "Dark";
    private int maxQueryRows = 1000;
    private int queryTimeoutSeconds = 30;
    private boolean autoCommitDefault = true;
    private String exportDirectory = System.getProperty("user.home");

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public int getMaxQueryRows() { return maxQueryRows; }
    public void setMaxQueryRows(int maxQueryRows) { this.maxQueryRows = maxQueryRows; }
    public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
    public boolean isAutoCommitDefault() { return autoCommitDefault; }
    public void setAutoCommitDefault(boolean autoCommitDefault) { this.autoCommitDefault = autoCommitDefault; }
    public String getExportDirectory() { return exportDirectory; }
    public void setExportDirectory(String exportDirectory) { this.exportDirectory = exportDirectory; }
}
