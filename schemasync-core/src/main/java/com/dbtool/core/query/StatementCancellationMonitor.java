package com.dbtool.core.query;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

public class StatementCancellationMonitor {
    private final AtomicReference<Statement> currentStatement = new AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);

    public void register(Statement stmt) {
        cancelled.set(false);
        currentStatement.set(stmt);
    }

    public void reset() {
        cancelled.set(false);
        currentStatement.set(null);
    }

    public void unregister() {
        currentStatement.set(null);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean cancel() {
        cancelled.set(true);
        Statement stmt = currentStatement.getAndSet(null);
        if (stmt != null) {
            try {
                stmt.cancel();
                return true;
            } catch (SQLException ignored) {}
        }
        return false;
    }
}
