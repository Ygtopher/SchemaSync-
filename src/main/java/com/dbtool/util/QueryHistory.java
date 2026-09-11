package com.dbtool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class QueryHistory {

    private static final File FILE = new File(System.getProperty("user.home"), ".dbtool_query_history.json");
    private static final int MAX = 50;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<String> load() {
        try {
            if (!FILE.exists()) return new ArrayList<>();
            CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
            return mapper.readValue(FILE, type);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void add(String query) {
        List<String> history = load();
        history.remove(query); // avoid duplicates
        history.add(0, query);
        if (history.size() > MAX) history = history.subList(0, MAX);
        save(history);
    }

    public static void save(List<String> history) {
        try { mapper.writeValue(FILE, history); } catch (Exception ignored) {}
    }

    public static void clear() {
        try { mapper.writeValue(FILE, new ArrayList<>()); } catch (Exception ignored) {}
    }
}
