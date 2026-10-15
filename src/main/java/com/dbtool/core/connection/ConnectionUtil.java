package com.dbtool.core.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.TimeZone;

/**
 * Robust database connection utility with automatic PostgreSQL/MySQL timezone fallback handling.
 * Resolves "FATAL: invalid value for parameter TimeZone" on Linux desktops (e.g. Africa/Kigali on Ubuntu).
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
     * Proactively normalizes client timezone at startup if it is known to be missing from
     * common or older PostgreSQL timezone catalogs (such as Africa/Kigali on Ubuntu).
     */
    public static void ensureSafeTimeZone() {
        try {
            TimeZone current = TimeZone.getDefault();
            String id = current != null ? current.getID() : "";
            if (isPotentiallyProblematicZone(id)) {
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                System.setProperty("user.timezone", "UTC");
            }
        } catch (Throwable ignored) {
        }
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
     * Opens a JDBC connection with automatic fallback retry if the server rejects the client's timezone.
     */
    public static Connection getConnection(String url, String user, String pass) throws SQLException {
        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            if (isTimeZoneError(e)) {
                // Server rejected client JVM timezone. Fallback JVM timezone to UTC and retry immediately.
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                System.setProperty("user.timezone", "UTC");
                return DriverManager.getConnection(url, user, pass);
            }
            throw e;
        }
    }

    /**
     * Opens a JDBC connection with automatic fallback retry if the server rejects the client's timezone.
     */
    public static Connection getConnection(String url) throws SQLException {
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            if (isTimeZoneError(e)) {
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                System.setProperty("user.timezone", "UTC");
                return DriverManager.getConnection(url);
            }
            throw e;
        }
    }
}
