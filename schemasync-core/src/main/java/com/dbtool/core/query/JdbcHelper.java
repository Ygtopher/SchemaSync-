package com.dbtool.core.query;

public final class JdbcHelper {
    private JdbcHelper() {}

    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) {}
        }
    }
}
