package com.dbtool.session;

import com.dbtool.DatabaseManager;
import com.dbtool.Main;

import java.awt.Color;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an active, isolated database connection session in SchemaSync.
 * Enables simultaneous multi-database connections with zero reconnect latency.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 */
public class DatabaseSession {
    private final String id;
    private String name;
    private String dbType; // POSTGRESQL, MYSQL, SQLITE, ACCESS, SANDBOX, etc.
    private final DatabaseManager dbManager;
    private final long connectedAt;
    private volatile boolean isDetached;
    private volatile Main detachedWindow;
    private Color badgeColor;

    public DatabaseSession(String name, String dbType, DatabaseManager dbManager) {
        this(UUID.randomUUID().toString(), name, dbType, dbManager);
    }

    public DatabaseSession(String id, String name, String dbType, DatabaseManager dbManager) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name != null && !name.isBlank() ? name : "Database Session";
        this.dbType = dbType != null && !dbType.isBlank() ? dbType.toUpperCase() : "DATABASE";
        this.dbManager = dbManager != null ? dbManager : new DatabaseManager();
        this.connectedAt = System.currentTimeMillis();
        this.isDetached = false;
        this.badgeColor = generateColor(this.name);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.badgeColor = generateColor(name);
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public boolean isDetached() {
        return isDetached;
    }

    public void setDetached(boolean detached) {
        this.isDetached = detached;
    }

    public Main getDetachedWindow() {
        return detachedWindow;
    }

    public void setDetachedWindow(Main detachedWindow) {
        this.detachedWindow = detachedWindow;
    }

    public Color getBadgeColor() {
        return badgeColor;
    }

    public void setBadgeColor(Color badgeColor) {
        this.badgeColor = badgeColor;
    }

    public boolean isAlive() {
        return dbManager != null && dbManager.isConnected();
    }

    public void close() {
        if (dbManager != null) {
            dbManager.closePool();
        }
        if (detachedWindow != null) {
            try {
                detachedWindow.dispose();
            } catch (Exception ignored) {}
            detachedWindow = null;
        }
    }

    private static Color generateColor(String seed) {
        if (seed == null) return new Color(59, 130, 246);
        int hash = Math.abs(seed.hashCode());
        Color[] palette = new Color[] {
            new Color(59, 130, 246),  // Blue
            new Color(16, 185, 129),  // Emerald
            new Color(139, 92, 246),  // Purple
            new Color(245, 158, 11),  // Amber
            new Color(236, 72, 153),  // Pink
            new Color(14, 165, 233),  // Sky
            new Color(99, 102, 241)   // Indigo
        };
        return palette[hash % palette.length];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseSession that = (DatabaseSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " [" + dbType + "]" + (isDetached ? " (Detached)" : "");
    }
}
