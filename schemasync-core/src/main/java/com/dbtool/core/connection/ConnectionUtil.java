package com.dbtool.core.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
}
