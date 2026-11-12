package com.dbtool.core.events;

public record TableMutatedEvent(String tableName, String operation) {
    public String getTableName() { return tableName; }
    public String getOperation() { return operation; }
}
