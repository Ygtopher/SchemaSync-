package com.dbtool.core.query;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StatementRunner implements QueryExecutor {
    @Override
    public QueryResult executeQuery(Connection connection, String sql) throws SQLException {
        long start = System.currentTimeMillis();
        QueryResult result = new QueryResult();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> headers = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                headers.add(meta.getColumnLabel(i));
            }
            result.setColumnNames(headers);

            while (rs.next()) {
                RowData row = new RowData();
                for (int i = 1; i <= colCount; i++) {
                    row.addValue(rs.getObject(i));
                }
                result.addRow(row);
            }
        }
        result.setExecutionTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    @Override
    public int executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }
}
