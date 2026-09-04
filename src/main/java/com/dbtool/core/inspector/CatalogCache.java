package com.dbtool.core.inspector;

import com.dbtool.core.model.TableMetadata;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CatalogCache {
    private final Map<String, TableMetadata> cache = new ConcurrentHashMap<>();

    public void put(String key, TableMetadata table) {
        if (key != null && table != null) cache.put(key.toUpperCase(), table);
    }

    public TableMetadata get(String key) {
        return key != null ? cache.get(key.toUpperCase()) : null;
    }

    public void clear() {
        cache.clear();
    }
}
