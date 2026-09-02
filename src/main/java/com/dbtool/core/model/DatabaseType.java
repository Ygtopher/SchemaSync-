package com.dbtool.core.model;

public enum DatabaseType {
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", 5432),
    H2("H2 Database", "org.h2.Driver", 9092),
    MS_ACCESS("Microsoft Access", "net.ucanaccess.jdbc.UcanaccessDriver", -1),
    GENERIC("Generic SQL", null, -1);

    private final String displayName;
    private final String driverClassName;
    private final int defaultPort;

    DatabaseType(String displayName, String driverClassName, int defaultPort) {
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
    }

    public String getDisplayName() { return displayName; }
    public String getDriverClassName() { return driverClassName; }
    public int getDefaultPort() { return defaultPort; }
}
