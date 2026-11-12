package com.dbtool.core.query;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StatementRunner implements QueryExecutor {
    private int maxRows = 10000;

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    @Override
    public QueryResult executeQuery(Connection connection, String sql) throws SQLException {
        long start = System.currentTimeMillis();
        QueryResult result = new QueryResult();
        try (Statement stmt = connection.createStatement()) {
            stmt.setMaxRows(maxRows);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<String> headers = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    headers.add(meta.getColumnLabel(i));
                }
                result.setColumnNames(headers);

                while (rs.next()) {
                    Object[] rowVals = new Object[colCount];
                    for (int i = 1; i <= colCount; i++) {
                        rowVals[i - 1] = rs.getObject(i);
                    }
                    result.addRow(new RowData(rowVals));
                }
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
