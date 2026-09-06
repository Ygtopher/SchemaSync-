package com.dbtool.core.query;

import java.sql.SQLException;

public class SqlExceptionHelper {
    public static String formatMessage(SQLException e, String sql) {
        return String.format("[Error %d][%s] %s\nSQL: %s", e.getErrorCode(), e.getSQLState(), e.getMessage(), sql);
    }
}
