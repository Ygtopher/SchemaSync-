package com.dbtool.core.events;

import com.dbtool.core.model.DatabaseType;

public record ConnectionChangedEvent(String connectionId, boolean connected, String databaseName, DatabaseType databaseType) {
    public String getConnectionId() { return connectionId; }
    public boolean isConnected() { return connected; }
    public String getDatabaseName() { return databaseName; }
    public DatabaseType getDatabaseType() { return databaseType; }
}
