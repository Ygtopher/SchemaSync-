package com.dbtool.core.connection;

import com.dbtool.core.model.ConnectionConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PooledConnectionProvider implements ConnectionProvider {
    private final ConnectionConfig config;
    private final BlockingQueue<Connection> pool = new LinkedBlockingQueue<>(10);
    private volatile boolean closed = false;

    public PooledConnectionProvider(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (closed) throw new SQLException("Connection pool is closed.");
        Connection conn = pool.poll();
        if (conn == null || conn.isClosed()) {
            String url = JdbcUrlBuilder.buildUrl(config);
            conn = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
        }
        return conn;
    }

    @Override
    public void releaseConnection(Connection connection) throws SQLException {
        if (connection != null && !connection.isClosed() && !closed) {
            pool.offer(connection);
        } else if (connection != null) {
            connection.close();
        }
    }

    @Override
    public boolean isAlive() {
        return !closed;
    }

    @Override
    public void close() {
        closed = true;
        Connection conn;
        while ((conn = pool.poll()) != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}
