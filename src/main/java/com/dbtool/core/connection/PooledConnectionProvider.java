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
    private int maxPoolSize = 5;
    private int activeCount = 0;

    public PooledConnectionProvider(ConnectionConfig config) {
        this.config = config;
    }

    public synchronized int getActiveCount() { return activeCount; }
    public int getIdleCount() { return pool.size(); }
    public int getMaxPoolSize() { return maxPoolSize; }

    @Override
    public Connection getConnection() throws SQLException {
        if (closed) throw new SQLException("Connection pool is closed.");
        Connection conn = pool.poll();
        if (conn == null || conn.isClosed() || !ConnectionValidator.isValid(conn, 2)) {
            String url = JdbcUrlBuilder.buildUrl(config);
            DriverManager.setLoginTimeout(config.getTimeoutSeconds());
            conn = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
        }
        synchronized (this) { activeCount++; }
        return conn;
    }

    @Override
    public void releaseConnection(Connection connection) throws SQLException {
        synchronized (this) { if (activeCount > 0) activeCount--; }
        if (connection != null && !connection.isClosed() && !closed && pool.size() < maxPoolSize) {
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
