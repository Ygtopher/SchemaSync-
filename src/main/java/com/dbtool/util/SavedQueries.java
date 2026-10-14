package com.dbtool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class SavedQueries {

    private static File customFile = null;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static synchronized void setFileForTesting(File file) {
        customFile = file;
    }

    public static synchronized File getFile() {
        if (customFile != null) return customFile;
        File target = FileUtil.getSaveFile("saved_queries.json");
        // Seamless migration: migrate legacy ~/.dbtool_saved_queries.json if present
        if (!target.exists()) {
            File legacy = new File(System.getProperty("user.home"), ".dbtool_saved_queries.json");
            if (legacy.exists() && legacy.length() > 0) {
                try {
                    Files.copy(legacy.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
        return target;
    }

    public static synchronized Map<String, String> load() {
        try {
            File file = getFile();
            if (!file.exists()) return new LinkedHashMap<>();
            MapType type = mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class);
            Map<String, String> map = mapper.readValue(file, type);
            return map != null ? map : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static synchronized void save(String name, String sql) {
        Map<String, String> queries = load();
        queries.put(name, sql);
        persist(queries);
    }

    public static synchronized void delete(String name) {
        Map<String, String> queries = load();
        queries.remove(name);
        persist(queries);
    }

    private static synchronized void persist(Map<String, String> queries) {
        try { 
            File file = getFile();
            mapper.writeValue(file, queries != null ? queries : new LinkedHashMap<>()); 
        } catch (Exception ignored) {}
    }
}
