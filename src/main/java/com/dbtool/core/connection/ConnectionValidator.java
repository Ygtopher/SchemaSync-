package com.dbtool.core.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectionValidator {
    public static boolean isValid(Connection connection, int timeoutSeconds) {
        if (connection == null) return false;
        try {
            if (connection.isClosed()) return false;
            return connection.isValid(timeoutSeconds);
        } catch (SQLException e) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SELECT 1");
                return true;
            } catch (SQLException ex) {
                return false;
            }
        }
    }
}
