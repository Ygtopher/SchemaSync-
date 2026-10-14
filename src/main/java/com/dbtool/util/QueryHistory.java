package com.dbtool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class QueryHistory {

    private static File customFile = null;
    public static final int MAX = 100;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static synchronized void setHistoryFileForTesting(File file) {
        customFile = file;
    }

    public static synchronized File getFile() {
        if (customFile != null) return customFile;
        File target = FileUtil.getSaveFile("query_history.json");
        // Seamless migration: migrate legacy ~/.dbtool_query_history.json if present
        if (!target.exists()) {
            File legacy = new File(System.getProperty("user.home"), ".dbtool_query_history.json");
            if (legacy.exists() && legacy.length() > 0) {
                try {
                    Files.copy(legacy.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
        return target;
    }

    public static synchronized List<String> load() {
        try {
            File file = getFile();
            if (!file.exists()) return new ArrayList<>();
            CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
            List<String> list = mapper.readValue(file, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void add(String query) {
        if (query == null || query.trim().isEmpty()) return;
        List<String> history = load();
        history.remove(query); // avoid duplicates
        history.add(0, query);
        if (history.size() > MAX) {
            history = new ArrayList<>(history.subList(0, MAX));
        }
        save(history);
    }
    
    public static synchronized void remove(String query) {
        List<String> history = load();
        history.remove(query);
        save(history);
    }

    public static synchronized void save(List<String> history) {
        try { 
            File file = getFile();
            mapper.writeValue(file, history != null ? history : new ArrayList<>()); 
        } catch (Exception ignored) {}
    }

    public static synchronized void clear() {
        try { 
            File file = getFile();
            mapper.writeValue(file, new ArrayList<>()); 
        } catch (Exception ignored) {}
    }
}
