package com.dbtool.core.query;

import java.sql.Connection;
import java.sql.SQLException;

public interface QueryExecutor {
    QueryResult executeQuery(Connection connection, String sql) throws SQLException;
    int executeUpdate(Connection connection, String sql) throws SQLException;
}
