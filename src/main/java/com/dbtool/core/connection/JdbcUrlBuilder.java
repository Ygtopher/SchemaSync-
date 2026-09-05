package com.dbtool.core.connection;

import com.dbtool.core.model.ConnectionConfig;

public class JdbcUrlBuilder {
    public static String buildUrl(ConnectionConfig config) {
        if (config == null || config.getType() == null) {
            throw new IllegalArgumentException("Config and type must not be null");
        }
        switch (config.getType()) {
            case POSTGRESQL:
                return "jdbc:postgresql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabaseName();
            case H2:
                return "jdbc:h2:mem:" + config.getDatabaseName() + ";DB_CLOSE_DELAY=-1";
            case MS_ACCESS:
                return "jdbc:ucanaccess://" + config.getFilePath();
            default:
                return "";
        }
    }
}
