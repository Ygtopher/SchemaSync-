package com.dbtool.core.query;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class BatchStatementRunner {
    public static int[] executeBatch(Connection connection, List<String> statements) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            for (String sql : statements) {
                if (sql != null && !sql.trim().isEmpty()) {
                    stmt.addBatch(sql);
                }
            }
            return stmt.executeBatch();
        }
    }
}
