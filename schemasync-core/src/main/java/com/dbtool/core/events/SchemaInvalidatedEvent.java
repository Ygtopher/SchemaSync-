package com.dbtool.core.events;

public record SchemaInvalidatedEvent(String connectionId, String schemaName) {
    public String getConnectionId() { return connectionId; }
    public String getSchemaName() { return schemaName; }
}
