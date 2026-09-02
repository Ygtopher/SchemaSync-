package com.dbtool.core.model;

import java.io.Serializable;
import java.util.UUID;

public class ConnectionProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private ConnectionConfig config;
    private long lastConnectedTime;

    public ConnectionProfile() {
        this.id = UUID.randomUUID().toString();
    }

    public ConnectionProfile(String name, ConnectionConfig config) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.config = config;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ConnectionConfig getConfig() { return config; }
    public void setConfig(ConnectionConfig config) { this.config = config; }
    public long getLastConnectedTime() { return lastConnectedTime; }
    public void setLastConnectedTime(long lastConnectedTime) { this.lastConnectedTime = lastConnectedTime; }
}
