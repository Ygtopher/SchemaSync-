package com.dbtool.core.exception;

public class ConfigurationException extends SchemaSyncException {
    public ConfigurationException(String message) {
        super("CONFIG_ERROR", message, null);
    }
}
