package com.dbtool.core.query;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

public class TransactionContext implements AutoCloseable {
    private final Connection connection;
    private final boolean originalAutoCommit;
    private boolean committed = false;

    public TransactionContext(Connection connection) throws SQLException {
        this.connection = connection;
        this.originalAutoCommit = connection.getAutoCommit();
        this.connection.setAutoCommit(false);
    }

    public void commit() throws SQLException {
        connection.commit();
        committed = true;
    }

    public void rollback() throws SQLException {
        connection.rollback();
    }

    public Savepoint setSavepoint(String name) throws SQLException {
        return connection.setSavepoint(name);
    }

    public void rollbackToSavepoint(Savepoint savepoint) throws SQLException {
        connection.rollback(savepoint);
    }

    @Override
    public void close() throws SQLException {
        if (!committed) {
            try { connection.rollback(); } catch (SQLException ignored) {}
        }
        connection.setAutoCommit(originalAutoCommit);
    }
}
