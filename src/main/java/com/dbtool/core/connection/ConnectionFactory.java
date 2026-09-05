package com.dbtool.core.connection;

import com.dbtool.core.model.ConnectionConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public class ConnectionFactory {
    public static Connection createConnection(ConnectionConfig config) throws SQLException {
        Objects.requireNonNull(config, "ConnectionConfig must not be null");
        config.validate();
        String url = JdbcUrlBuilder.buildUrl(config);
        return DriverManager.getConnection(url, config.getUsername(), config.getPassword());
    }
}
