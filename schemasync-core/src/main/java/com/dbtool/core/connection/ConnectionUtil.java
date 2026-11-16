package com.dbtool.core.connection;

import com.dbtool.core.model.ConnectionConfig;
import com.dbtool.core.model.DatabaseType;
import com.dbtool.core.model.SslMode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

/**
 * Robust database connection utility with connection-scoped PostgreSQL/MySQL timezone fallback
 * handling (never mutates global JVM TimeZone.setDefault).
 *
 * Creator: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class ConnectionUtil {

    /**
     * Checks if the throwable or any cause is a PostgreSQL timezone rejection.
     */
    public static boolean isTimeZoneError(Throwable t) {
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("invalid value for parameter \"timezone\"")
                        || lower.contains("invalid value for parameter 'timezone'")
                        || (lower.contains("parameter \"timezone\"") && lower.contains("invalid"))
                        || lower.contains("unknown timezone")
                        || lower.contains("unrecognized timezone")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Legacy method retained as a no-op compatibility stub.
     * Global JVM timezone mutation was removed in v2.0 so UI formatting and unrelated connections
     * remain unaffected; timezone safety is now applied per connection via JDBC session options.
     */
    @Deprecated
    public static void ensureSafeTimeZone() {
        // Intentionally does NOT call TimeZone.setDefault(...)
    }

    public static boolean isPotentiallyProblematicZone(String tzId) {
        if (tzId == null) return false;
        String trimmed = tzId.trim();
        return trimmed.equalsIgnoreCase("Africa/Kigali")
                || trimmed.equalsIgnoreCase("Asia/Calcutta")
                || trimmed.equalsIgnoreCase("Asia/Saigon")
                || trimmed.equalsIgnoreCase("Europe/Kiev");
    }

    /**
     * Builds connection-scoped Properties carrying optional UTC session timezone settings without
     * mutating TimeZone.getDefault().
     */
    public static Properties buildConnectionProperties(String url, String user, String pass, boolean forceUtcTimeZone) {
        Properties props = new Properties();
        if (user != null) props.setProperty("user", user);
        if (pass != null) props.setProperty("password", pass);

        boolean needsUtc = forceUtcTimeZone || isPotentiallyProblematicZone(TimeZone.getDefault().getID());
        if (needsUtc && url != null) {
            String lower = url.toLowerCase();
            if (lower.startsWith("jdbc:postgresql:")) {
                props.setProperty("options", "-c TimeZone=UTC");
            } else if (lower.startsWith("jdbc:mysql:")) {
                props.setProperty("connectionTimeZone", "UTC");
            }
        }
        return props;
    }

    /**
     * Opens a JDBC connection with automatic connection-scoped UTC retry if the server rejects
     * the client's timezone. Never calls TimeZone.setDefault.
     */
    public static Connection getConnection(String url, String user, String pass) throws SQLException {
        com.dbtool.core.driver.DriverClassLoaderManager.getInstance().ensureDriverForUrl(url);
        Properties initialProps = buildConnectionProperties(url, user, pass, false);
        try {
            return DriverManager.getConnection(url, initialProps);
        } catch (SQLException e) {
            if (isTimeZoneError(e)) {
                Properties retryProps = buildConnectionProperties(url, user, pass, true);
                return DriverManager.getConnection(url, retryProps);
            }
            throw e;
        }
    }

    /**
     * Opens a JDBC connection with automatic connection-scoped UTC retry if the server rejects
     * the client's timezone. Never calls TimeZone.setDefault.
     */
    public static Connection getConnection(String url) throws SQLException {
        return getConnection(url, null, null);
    }

    /**
     * Discovers all connectable databases on a PostgreSQL server using credentials.
     * Connects to candidate default maintenance catalogs ("postgres", "template1", or the username)
     * and queries pg_database for all non-template databases the user has CONNECT privilege on.
     *
     * @param host Target host IP or hostname (defaults to "localhost" if null/empty)
     * @param port Target port (defaults to 5432 if <= 0)
     * @param user Authenticating database username (defaults to "postgres" if null/empty)
     * @param pass Database password
     * @param sslMode SSL encryption preference
     * @param timeoutSeconds Connection timeout in seconds (minimum 1 second)
     * @return Ordered list of discovered database names
     * @throws SQLException If authentication fails or host is unreachable
     */
    public static List<String> listPostgresDatabases(String host, int port, String user, String pass, SslMode sslMode, int timeoutSeconds) throws SQLException {
        String effectiveHost = (host == null || host.trim().isEmpty()) ? "localhost" : host.trim();
        int effectivePort = port > 0 ? port : 5432;
        String effectiveUser = (user == null || user.trim().isEmpty()) ? "postgres" : user.trim();
        int effectiveTimeout = Math.max(1, timeoutSeconds);
        SslMode effectiveSsl = sslMode != null ? sslMode : SslMode.PREFER;

        String[] candidateDbs = new String[]{"postgres", "template1", effectiveUser};
        SQLException lastEx = null;

        for (String initialDb : candidateDbs) {
            ConnectionConfig cfg = new ConnectionConfig();
            cfg.setType(DatabaseType.POSTGRESQL);
            cfg.setHost(effectiveHost);
            cfg.setPort(effectivePort);
            cfg.setDatabaseName(initialDb);
            cfg.setSslMode(effectiveSsl);
            cfg.setTimeoutSeconds(effectiveTimeout);

            String url = JdbcUrlBuilder.buildUrl(cfg) + "&connectTimeout=" + effectiveTimeout + "&socketTimeout=" + (effectiveTimeout * 2);
            try (Connection conn = getConnection(url, effectiveUser, pass)) {
                List<String> databases = new ArrayList<>();
                // 1. Query pg_database filtered by CONNECT privilege
                String queryWithPriv = "SELECT datname FROM pg_database WHERE datistemplate = false AND has_database_privilege(current_user, datname, 'CONNECT') ORDER BY datname;";
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(effectiveTimeout);
                    try (ResultSet rs = stmt.executeQuery(queryWithPriv)) {
                        while (rs.next()) {
                            String name = rs.getString(1);
                            if (name != null && !name.trim().isEmpty() && !databases.contains(name.trim())) {
                                databases.add(name.trim());
                            }
                        }
                    }
                } catch (SQLException privEx) {
                    // 2. Fallback to querying pg_database without privilege function
                    try (Statement stmt2 = conn.createStatement()) {
                        stmt2.setQueryTimeout(effectiveTimeout);
                        try (ResultSet rs2 = stmt2.executeQuery("SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname;")) {
                            while (rs2.next()) {
                                String name = rs2.getString(1);
                                if (name != null && !name.trim().isEmpty() && !databases.contains(name.trim())) {
                                    databases.add(name.trim());
                                }
                            }
                        }
                    }
                }
                if (!databases.isEmpty()) {
                    return databases;
                }
            } catch (SQLException ex) {
                lastEx = ex;
                String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                // If it is an auth error, connection refused, or timeout, fail fast rather than trying template1
                if (msg.contains("password authentication failed") || msg.contains("connection refused") || msg.contains("timed out")) {
                    throw ex;
                }
            }
        }

        if (lastEx != null) {
            throw lastEx;
        }
        return Collections.emptyList();
    }

    /**
     * Discovers all available databases/schemas on a MySQL/MariaDB server using credentials.
     *
     * @param host Target host IP or hostname (defaults to "localhost" if null/empty)
     * @param port Target port (defaults to 3306 if <= 0)
     * @param user Authenticating database username (defaults to "root" if null/empty)
     * @param pass Database password
     * @param sslMode SSL encryption preference
     * @param timeoutSeconds Connection timeout in seconds (minimum 1 second)
     * @return Ordered list of discovered database names
     * @throws SQLException If authentication fails or host is unreachable
     */
    public static List<String> listMySQLDatabases(String host, int port, String user, String pass, SslMode sslMode, int timeoutSeconds) throws SQLException {
        String effectiveHost = (host == null || host.trim().isEmpty()) ? "localhost" : host.trim();
        int effectivePort = port > 0 ? port : 3306;
        String effectiveUser = (user == null || user.trim().isEmpty()) ? "root" : user.trim();
        int effectiveTimeout = Math.max(1, timeoutSeconds);
        SslMode effectiveSsl = sslMode != null ? sslMode : SslMode.PREFER;

        ConnectionConfig cfg = new ConnectionConfig();
        cfg.setType(DatabaseType.MYSQL);
        cfg.setHost(effectiveHost);
        cfg.setPort(effectivePort);
        cfg.setDatabaseName("");
        cfg.setSslMode(effectiveSsl);
        cfg.setTimeoutSeconds(effectiveTimeout);

        String url = JdbcUrlBuilder.buildUrl(cfg);
        try (Connection conn = getConnection(url, effectiveUser, pass)) {
            List<String> databases = new ArrayList<>();
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(effectiveTimeout);
                try (ResultSet rs = stmt.executeQuery("SHOW DATABASES;")) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.trim().isEmpty() && !databases.contains(name.trim())) {
                            databases.add(name.trim());
                        }
                    }
                }
            }
            return databases;
        }
    }

    /**
     * Unified database auto-discovery dispatcher based on database type.
     */
    public static List<String> listDatabases(DatabaseType type, String host, int port, String user, String pass, SslMode sslMode, int timeoutSeconds) throws SQLException {
        if (type == DatabaseType.POSTGRESQL) {
            return listPostgresDatabases(host, port, user, pass, sslMode, timeoutSeconds);
        } else if (type == DatabaseType.MYSQL) {
            return listMySQLDatabases(host, port, user, pass, sslMode, timeoutSeconds);
        }
        return Collections.emptyList();
    }
}
