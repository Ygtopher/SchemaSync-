package com.dbtool.core.connection;

import java.sql.Connection;
import java.sql.SQLException;

public class AccessConnectionHelper {
    public static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                if (!connection.isClosed()) connection.close();
            } catch (SQLException ignored) {}
        }
    }
}
