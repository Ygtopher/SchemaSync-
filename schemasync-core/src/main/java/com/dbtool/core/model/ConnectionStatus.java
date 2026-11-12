package com.dbtool.core.model;

public enum ConnectionStatus {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting..."),
    CONNECTED("Connected"),
    FAILED("Connection Failed");

    private final String description;
    ConnectionStatus(String description) { this.description = description; }
    public String getDescription() { return description; }
}
