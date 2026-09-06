package com.dbtool.core.query;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

public class StatementCancellationMonitor {
    private final AtomicReference<Statement> currentStatement = new AtomicReference<>();

    public void register(Statement stmt) {
        currentStatement.set(stmt);
    }

    public void unregister() {
        currentStatement.set(null);
    }

    public boolean cancel() {
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
