package com.dbtool.core.connection;

import com.dbtool.core.model.ConnectionConfig;
import com.dbtool.core.model.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a per-profile database pool ({@link HikariDataSource} for server/in-memory DBs,
 * serialized lease lock for file-locked embedded DBs such as SQLite and MS Access) and
 * isolated non-pooled pinned sessions for manual transaction workflows.
 */
public class SessionPoolManager implements ConnectionProvider {

    private static final int DEFAULT_MAX_POOL_SIZE = 6;
    private static final int DEFAULT_MIN_IDLE = 1;

    private final DatabaseType databaseType;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int timeoutSeconds;
    private final int maxPoolSize;

    private HikariDataSource dataSource;
    private Connection embeddedConnection;
    private final ReentrantLock embeddedLock = new ReentrantLock();
    private final AtomicInteger leasedEmbeddedCount = new AtomicInteger(0);
    private volatile boolean closed = false;

    public SessionPoolManager(ConnectionConfig config) throws SQLException {
        this(
                config.getType(),
                JdbcUrlBuilder.buildUrl(config),
                config.getUsername(),
                config.getPassword(),
                config.getTimeoutSeconds(),
                DEFAULT_MAX_POOL_SIZE
        );
    }

    public SessionPoolManager(DatabaseType databaseType, String jdbcUrl, String username, String password, int timeoutSeconds) throws SQLException {
        this(databaseType, jdbcUrl, username, password, timeoutSeconds, DEFAULT_MAX_POOL_SIZE);
    }

    public SessionPoolManager(
            DatabaseType databaseType,
            String jdbcUrl,
            String username,
            String password,
            int timeoutSeconds,
            int maxPoolSize
    ) throws SQLException {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBC URL must not be blank.");
        }
        this.databaseType = databaseType != null ? databaseType : inferDatabaseType(jdbcUrl);
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.maxPoolSize = Math.max(1, maxPoolSize);

        initialize();
    }

    private static DatabaseType inferDatabaseType(String url) {
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:postgresql:")) return DatabaseType.POSTGRESQL;
        if (lower.startsWith("jdbc:mysql:")) return DatabaseType.MYSQL;
        if (lower.startsWith("jdbc:h2:")) return DatabaseType.H2;
        if (lower.startsWith("jdbc:sqlite:")) return DatabaseType.SQLITE;
        if (lower.startsWith("jdbc:ucanaccess:")) return DatabaseType.MS_ACCESS;
        return DatabaseType.GENERIC;
    }

    private boolean isEmbeddedLockDb() {
        return databaseType == DatabaseType.SQLITE || databaseType == DatabaseType.MS_ACCESS;
    }

    private void initialize() throws SQLException {
        com.dbtool.core.driver.DriverClassLoaderManager.getInstance().ensureDriverForUrl(jdbcUrl);
        if (isEmbeddedLockDb()) {
            embeddedConnection = ConnectionUtil.getConnection(jdbcUrl, username, password);
            return;
        }
        this.dataSource = createHikariPool(false);
    }

    private HikariDataSource createHikariPool(boolean forceUtcTimeZone) throws SQLException {
        HikariConfig hc = buildHikariConfig(forceUtcTimeZone);
        try {
            return new HikariDataSource(hc);
        } catch (RuntimeException ex) {
            if (!forceUtcTimeZone && ConnectionUtil.isTimeZoneError(ex)) {
                HikariConfig retry = buildHikariConfig(true);
                return new HikariDataSource(retry);
            }
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof SQLException) {
                throw (SQLException) cause;
            }
            throw new SQLException("Failed to initialize connection pool: " + ex.getMessage(), ex);
        }
    }

    private HikariConfig buildHikariConfig(boolean forceUtcTimeZone) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        if (username != null) hc.setUsername(username);
        if (password != null) hc.setPassword(password);
        hc.setMaximumPoolSize(maxPoolSize);
        hc.setMinimumIdle(Math.min(DEFAULT_MIN_IDLE, maxPoolSize));
        hc.setConnectionTimeout(TimeUnit.SECONDS.toMillis(timeoutSeconds));
        hc.setPoolName("SchemaSync-" + databaseType.name());
        hc.setAutoCommit(true);

        Properties connProps = ConnectionUtil.buildConnectionProperties(jdbcUrl, username, password, forceUtcTimeZone);
        for (String name : connProps.stringPropertyNames()) {
            if (!"user".equals(name) && !"password".equals(name)) {
                hc.addDataSourceProperty(name, connProps.getProperty(name));
            }
        }
        return hc;
    }

    /**
     * Borrows a pooled connection. Callers MUST close it (e.g. via try-with-resources) so the
     * underlying connection or embedded lease is returned immediately.
     */
    public Connection borrowConnection() throws SQLException {
        return getConnection();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("Session pool is closed.");
        }
        if (isEmbeddedLockDb()) {
            return borrowEmbeddedLease();
        }
        return dataSource.getConnection();
    }

    private Connection borrowEmbeddedLease() throws SQLException {
        boolean acquired;
        try {
            acquired = embeddedLock.tryLock(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for embedded database lease.", e);
        }
        if (!acquired) {
            throw new SQLException("Timed out waiting for embedded database lock (" + timeoutSeconds + "s).");
        }
        try {
            if (closed) {
                throw new SQLException("Session pool is closed.");
            }
            if (embeddedConnection == null || embeddedConnection.isClosed()) {
                embeddedConnection = ConnectionUtil.getConnection(jdbcUrl, username, password);
            }
            leasedEmbeddedCount.incrementAndGet();
            final Connection delegate = embeddedConnection;
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                            releaseEmbeddedLease();
                            return null;
                        }
                        if ("isClosed".equals(method.getName()) && (args == null || args.length == 0)) {
                            return closed || delegate.isClosed();
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (InvocationTargetException ite) {
                            throw ite.getTargetException();
                        }
                    }
            );
        } catch (Throwable t) {
            if (embeddedLock.isHeldByCurrentThread()) {
                embeddedLock.unlock();
            }
            if (t instanceof SQLException) throw (SQLException) t;
            throw new SQLException("Failed to acquire embedded database connection: " + t.getMessage(), t);
        }
    }

    private void releaseEmbeddedLease() {
        if (leasedEmbeddedCount.get() > 0) {
            leasedEmbeddedCount.decrementAndGet();
        }
        if (embeddedLock.isHeldByCurrentThread()) {
            embeddedLock.unlock();
        }
    }

    /**
     * Opens an isolated, non-pooled session suitable for stateful interactive tabs
     * (e.g. manual BEGIN/COMMIT/ROLLBACK or session-level SET commands).
     */
    public Connection openPinnedSession() throws SQLException {
        if (closed) {
            throw new SQLException("Session pool is closed.");
        }
        if (databaseType == DatabaseType.MS_ACCESS) {
            // UCanAccess holds file-level locks inside a single JVM unless opened read-only/serialized.
            return borrowConnection();
        }
        return ConnectionUtil.getConnection(jdbcUrl, username, password);
    }

    @Override
    public void releaseConnection(Connection connection) throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public boolean isAlive() {
        if (closed) return false;
        if (isEmbeddedLockDb()) {
            try {
                return embeddedConnection != null && !embeddedConnection.isClosed();
            } catch (SQLException e) {
                return false;
            }
        }
        return dataSource != null && !dataSource.isClosed();
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public int getMaxPoolSize() {
        return isEmbeddedLockDb() ? 1 : maxPoolSize;
    }

    public int getActiveCount() {
        if (closed) return 0;
        if (isEmbeddedLockDb()) {
            return leasedEmbeddedCount.get();
        }
        if (dataSource != null && dataSource.getHikariPoolMXBean() != null) {
            return dataSource.getHikariPoolMXBean().getActiveConnections();
        }
        return 0;
    }

    public int getIdleCount() {
        if (closed || isEmbeddedLockDb()) return 0;
        if (dataSource != null && dataSource.getHikariPoolMXBean() != null) {
            return dataSource.getHikariPoolMXBean().getIdleConnections();
        }
        return 0;
    }

    @Override
    public void close() {
        closed = true;
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        AccessConnectionHelper.closeQuietly(embeddedConnection);
        embeddedConnection = null;
    }
}
