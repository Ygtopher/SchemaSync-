package com.dbtool.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionSnapshot {
    public long timestamp = System.currentTimeMillis();
    public boolean cleanExit = false;
    public int activeTabIndex = 0;
    public String activeTabTitle = "";

    public ConnectionInfo connection = new ConnectionInfo();
    public SqlEditorState sqlEditor = new SqlEditorState();
    public List<BrowseState> browseTabs = new ArrayList<>();
    public List<VisualJoinState> joinTabs = new ArrayList<>();
    public TableCompareState tableCompare = new TableCompareState();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectionInfo {
        public String type = "NONE"; // "NONE", "SANDBOX", "POSTGRES", "FILE"
        public String host;
        public String port;
        public String dbName;
        public String user;
        public String pass;
        public String filePath;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SqlEditorState {
        public String queryText = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrowseState {
        public String tableName;
        public boolean distinct;
        public List<String> selectedColumns = new ArrayList<>();
        public String limit;
        public List<VisualJoinState.WhereState> wheres = new ArrayList<>();
        public List<VisualJoinState.OrderByState> orderBys = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableCompareState {
        public String queryA = "";
        public String queryB = "";
        public String keyCol = "";
    }
}
