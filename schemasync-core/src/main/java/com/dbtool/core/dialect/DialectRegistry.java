package com.dbtool.core.dialect;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ServiceLoader-driven registry of {@link DatabaseDialect} providers plus external plugin JAR
 * dialect registration.
 */
public class DialectRegistry {
    private static final DialectRegistry INSTANCE = new DialectRegistry();
    private final Map<DialectType, DatabaseDialect> registry = new EnumMap<>(DialectType.class);
    private final List<DatabaseDialect> orderedDialects = new CopyOnWriteArrayList<>();
    private final Map<String, DatabaseDialect> urlCache = new ConcurrentHashMap<>();

    private DialectRegistry() {
        reloadFromClassLoader(DialectRegistry.class.getClassLoader());
        // Guarantee baseline fallback if invoked from a minimal test classloader
        if (!registry.containsKey(DialectType.GENERIC)) {
            register(new GenericSqlDialect());
        }
    }

    public static DialectRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Discovers and registers all {@link DatabaseDialect} services visible to {@code classLoader}.
     */
    public synchronized void reloadFromClassLoader(ClassLoader classLoader) {
        ClassLoader effective = classLoader != null ? classLoader : DialectRegistry.class.getClassLoader();
        ServiceLoader<DatabaseDialect> loader = ServiceLoader.load(DatabaseDialect.class, effective);
        for (DatabaseDialect dialect : loader) {
            register(dialect);
        }
    }

    public synchronized void register(DatabaseDialect dialect) {
        if (dialect == null) return;
        registry.put(dialect.getDialectType(), dialect);
        orderedDialects.removeIf(d -> d.getClass().equals(dialect.getClass()));
        orderedDialects.add(dialect);
        urlCache.clear();
    }

    public synchronized DatabaseDialect getDialect(DialectType type) {
        if (type == null) return registry.getOrDefault(DialectType.GENERIC, new GenericSqlDialect());
        return registry.getOrDefault(type, registry.getOrDefault(DialectType.GENERIC, new GenericSqlDialect()));
    }

    public Collection<DatabaseDialect> getRegisteredDialects() {
        return Collections.unmodifiableList(orderedDialects);
    }

    public DatabaseDialect resolveFromJdbcUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return getDialect(DialectType.GENERIC);
        }
        return urlCache.computeIfAbsent(url, u -> {
            String lower = u.toLowerCase(Locale.ROOT);
            for (DatabaseDialect dialect : orderedDialects) {
                for (String prefix : dialect.getJdbcUrlPrefixes()) {
                    if (prefix != null && lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                        return dialect;
                    }
                }
            }
            if (lower.startsWith("jdbc:postgresql:")) return getDialect(DialectType.POSTGRESQL);
            if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) return getDialect(DialectType.MYSQL);
            if (lower.startsWith("jdbc:sqlite:")) return getDialect(DialectType.SQLITE);
            if (lower.startsWith("jdbc:h2:")) return getDialect(DialectType.H2);
            if (lower.startsWith("jdbc:ucanaccess:")) return getDialect(DialectType.ACCESS);
            return getDialect(DialectType.GENERIC);
        });
    }
}
