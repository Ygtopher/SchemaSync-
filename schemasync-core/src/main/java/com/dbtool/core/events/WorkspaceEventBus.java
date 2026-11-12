package com.dbtool.core.events;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight, synchronous/thread-safe publish-subscribe event bus decoupling core services
 * (connection changes, schema invalidations, SQL execution metrics, table mutations) from UI panels.
 */
public class WorkspaceEventBus {

    private static final WorkspaceEventBus SHARED = new WorkspaceEventBus();

    public static WorkspaceEventBus shared() {
        return SHARED;
    }

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> AutoCloseable subscribe(Class<T> eventType, Consumer<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        Consumer<Object> wrapped = event -> listener.accept((T) event);
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(wrapped);
        return () -> {
            List<Consumer<Object>> list = listeners.get(eventType);
            if (list != null) {
                list.remove(wrapped);
            }
        };
    }

    public void publish(Object event) {
        if (event == null) return;
        List<Consumer<Object>> exact = listeners.get(event.getClass());
        if (exact == null || exact.isEmpty()) return;
        for (Consumer<Object> consumer : exact) {
            try {
                consumer.accept(event);
            } catch (RuntimeException ignored) {
                // Isolate listener failures so other subscribers still receive the event.
            }
        }
    }

    public int subscriberCount(Class<?> eventType) {
        List<Consumer<Object>> list = listeners.get(eventType);
        return list == null ? 0 : list.size();
    }

    public void clear() {
        listeners.clear();
    }
}
