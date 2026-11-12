package com.dbtool.core.connection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionManager {
    private static final List<ConnectionProvider> ACTIVE_PROVIDERS = new CopyOnWriteArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (ConnectionProvider provider : ACTIVE_PROVIDERS) {
                try { provider.close(); } catch (Exception ignored) {}
            }
        }));
    }

    public static void register(ConnectionProvider provider) {
        ACTIVE_PROVIDERS.add(provider);
    }

    public static void unregister(ConnectionProvider provider) {
        ACTIVE_PROVIDERS.remove(provider);
    }
}
