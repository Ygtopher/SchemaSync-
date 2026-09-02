package com.dbtool.core.model;

import com.dbtool.core.exception.ConfigurationException;
import com.dbtool.util.StringUtil;
import java.io.Serializable;

public class ConnectionConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private DatabaseType type;
    private String host = "localhost";
    private int port = 5432;
    private String databaseName;
    private String username = "postgres";
    private String password = "";
    private String filePath;
    private int timeoutSeconds = 30;

    public ConnectionConfig() {}

    public void validate() {
        if (type == null) {
            throw new ConfigurationException("DatabaseType must not be null.");
        }
        if (type == DatabaseType.POSTGRESQL) {
            if (StringUtil.isBlank(host)) throw new ConfigurationException("Host cannot be empty for PostgreSQL.");
            if (port <= 0 || port > 65535) throw new ConfigurationException("Invalid port number: " + port);
        } else if (type == DatabaseType.MS_ACCESS) {
            if (StringUtil.isBlank(filePath)) throw new ConfigurationException("File path must be specified for MS Access.");
        }
    }

    public DatabaseType getType() { return type; }
    public void setType(DatabaseType type) { this.type = type; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
