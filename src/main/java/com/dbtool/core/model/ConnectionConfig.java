package com.dbtool.core.model;

import java.io.Serializable;
import java.util.Objects;

public class ConnectionConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private DatabaseType type;
    private String host;
    private int port;
    private String databaseName;
    private String username;
    private String password;
    private String filePath;
    private int timeoutSeconds = 30;

    public ConnectionConfig() {}

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
