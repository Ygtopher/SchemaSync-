package com.dbtool.core.dialect;

import java.util.EnumMap;
import java.util.Map;

public class DialectRegistry {
    private static final DialectRegistry INSTANCE = new DialectRegistry();
    private final Map<DialectType, DatabaseDialect> registry = new EnumMap<>(DialectType.class);

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
}
