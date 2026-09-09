package com.dbtool.core.script;

import java.sql.Connection;

public class ScriptExecutionContext {
    private final Connection connection;
    private final VariableContext variableContext = new VariableContext();
    private final ScriptOutputLogger outputLogger = new ScriptOutputLogger();
    private boolean autoCommit = true;

    public ScriptExecutionContext(Connection connection) {
        this.connection = connection;
    }

    public Connection getConnection() { return connection; }
    public VariableContext getVariableContext() { return variableContext; }
    public ScriptOutputLogger getOutputLogger() { return outputLogger; }
    public boolean isAutoCommit() { return autoCommit; }
    public void setAutoCommit(boolean autoCommit) { this.autoCommit = autoCommit; }
}
