package com.dbtool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class SavedQueries {

    private static final File FILE = new File(System.getProperty("user.home"), ".dbtool_saved_queries.json");
    private static final ObjectMapper mapper = new ObjectMapper();

    public static Map<String, String> load() {
        try {
            if (!FILE.exists()) return new LinkedHashMap<>();
            MapType type = mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class);
            return mapper.readValue(FILE, type);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static void save(String name, String sql) {
        Map<String, String> queries = load();
        queries.put(name, sql);
        persist(queries);
    }

    public static void delete(String name) {
        Map<String, String> queries = load();
        queries.remove(name);
        persist(queries);
    }

    private static void persist(Map<String, String> queries) {
        try { mapper.writeValue(FILE, queries); } catch (Exception ignored) {}
    }
}
