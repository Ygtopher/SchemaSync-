package com.dbtool.core.dialect;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class DialectRegistry {
    private static final DialectRegistry INSTANCE = new DialectRegistry();
    private final Map<DialectType, DatabaseDialect> registry = Collections.synchronizedMap(new EnumMap<>(DialectType.class));

    private DialectRegistry() {
        register(new GenericSqlDialect());
        register(new PostgreSqlDialect());
        register(new H2Dialect());
        register(new AccessDialect());
    }

    public static DialectRegistry getInstance() { return INSTANCE; }

    public void register(DatabaseDialect dialect) {
        registry.put(dialect.getDialectType(), dialect);
    }

    public DatabaseDialect getDialect(DialectType type) {
        return registry.getOrDefault(type, registry.get(DialectType.GENERIC));
    }

    public DatabaseDialect resolveFromJdbcUrl(String url) {
        if (url == null) return getDialect(DialectType.GENERIC);
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:postgresql:")) return getDialect(DialectType.POSTGRESQL);
        if (lower.startsWith("jdbc:h2:")) return getDialect(DialectType.H2);
        if (lower.startsWith("jdbc:ucanaccess:")) return getDialect(DialectType.ACCESS);
        return getDialect(DialectType.GENERIC);
    }
}
