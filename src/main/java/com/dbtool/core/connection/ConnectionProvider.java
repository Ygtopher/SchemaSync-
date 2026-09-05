package com.dbtool.core.connection;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider extends AutoCloseable {
    Connection getConnection() throws SQLException;
    void releaseConnection(Connection connection) throws SQLException;
    boolean isAlive();
}
