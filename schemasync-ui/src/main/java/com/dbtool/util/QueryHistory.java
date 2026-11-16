package com.dbtool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryHistory {

    public static class HistoryMetadata {
        public long timestamp;
        public long durationMs;
        public int rowCount;
        public boolean pinned;

        public HistoryMetadata() {}

        public HistoryMetadata(long timestamp, long durationMs, int rowCount, boolean pinned) {
            this.timestamp = timestamp;
            this.durationMs = durationMs;
            this.rowCount = rowCount;
            this.pinned = pinned;
        }
    }

    private static File customFile = null;
    public static final int MAX = 100;
    private static final ObjectMapper mapper = new ObjectMapper();

    private static List<String> cachedHistory = null;
    private static Map<String, HistoryMetadata> cachedMetaMap = null;
    private static final java.util.concurrent.ExecutorService asyncSaveExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "QueryHistory-AsyncSave");
                t.setDaemon(true);
                return t;
            });

    public static synchronized void setHistoryFileForTesting(File file) {
        flush();
        customFile = file;
        cachedHistory = null;
        cachedMetaMap = null;
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

    private static synchronized File getMetaFile() {
        File mainFile = getFile();
        return new File(mainFile.getParentFile(), mainFile.getName() + ".meta.json");
    }

    public static synchronized List<String> load() {
        if (cachedHistory != null) {
            return new ArrayList<>(cachedHistory);
        }
        try {
            File file = getFile();
            if (!file.exists()) {
                cachedHistory = new ArrayList<>();
                return new ArrayList<>();
            }
            CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
            List<String> list = mapper.readValue(file, type);
            cachedHistory = list != null ? new ArrayList<>(list) : new ArrayList<>();
            return new ArrayList<>(cachedHistory);
        } catch (Exception e) {
            cachedHistory = new ArrayList<>();
            return new ArrayList<>();
        }
    }

    public static synchronized Map<String, HistoryMetadata> loadMetadataMap() {
        if (cachedMetaMap != null) {
            return new HashMap<>(cachedMetaMap);
        }
        try {
            File metaFile = getMetaFile();
            if (!metaFile.exists()) {
                cachedMetaMap = new HashMap<>();
                return new HashMap<>();
            }
            MapType type = mapper.getTypeFactory().constructMapType(HashMap.class, String.class, HistoryMetadata.class);
            Map<String, HistoryMetadata> map = mapper.readValue(metaFile, type);
            cachedMetaMap = map != null ? new HashMap<>(map) : new HashMap<>();
            return new HashMap<>(cachedMetaMap);
        } catch (Exception e) {
            cachedMetaMap = new HashMap<>();
            return new HashMap<>();
        }
    }

    private static synchronized void saveMetadataMap(Map<String, HistoryMetadata> map) {
        cachedMetaMap = map != null ? new HashMap<>(map) : new HashMap<>();
        final Map<String, HistoryMetadata> copy = new HashMap<>(cachedMetaMap);
        final File metaFile = getMetaFile();
        if (customFile != null) {
            try {
                mapper.writeValue(metaFile, copy);
            } catch (Exception ignored) {}
            return;
        }
        asyncSaveExecutor.submit(() -> {
            try {
                mapper.writeValue(metaFile, copy);
            } catch (Exception ignored) {}
        });
    }

    public static synchronized void add(String query) {
        addWithMetadata(query, -1L, -1);
    }

    public static synchronized void addWithMetadata(String query, long durationMs, int rowCount) {
        if (query == null || query.trim().isEmpty()) return;
        List<String> history = load();
        history.remove(query); // avoid duplicates
        history.add(0, query);
        if (history.size() > MAX) {
            history = new ArrayList<>(history.subList(0, MAX));
        }
        save(history);

        Map<String, HistoryMetadata> metaMap = loadMetadataMap();
        HistoryMetadata existing = metaMap.get(query);
        boolean wasPinned = existing != null && existing.pinned;
        long finalDuration = durationMs >= 0 ? durationMs : (existing != null ? existing.durationMs : -1L);
        int finalRows = rowCount >= 0 ? rowCount : (existing != null ? existing.rowCount : -1);
        metaMap.put(query, new HistoryMetadata(System.currentTimeMillis(), finalDuration, finalRows, wasPinned));
        saveMetadataMap(metaMap);
    }

    public static synchronized boolean togglePin(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        Map<String, HistoryMetadata> metaMap = loadMetadataMap();
        HistoryMetadata meta = metaMap.get(query);
        if (meta == null) {
            meta = new HistoryMetadata(System.currentTimeMillis(), -1L, -1, true);
        } else {
            meta.pinned = !meta.pinned;
        }
        metaMap.put(query, meta);
        saveMetadataMap(metaMap);
        return meta.pinned;
    }

    public static synchronized boolean isPinned(String query) {
        if (query == null) return false;
        HistoryMetadata meta = loadMetadataMap().get(query);
        return meta != null && meta.pinned;
    }

    public static synchronized HistoryMetadata getMetadata(String query) {
        if (query == null) return null;
        return loadMetadataMap().get(query);
    }

    public static synchronized List<String> search(String keyword, boolean pinnedOnly) {
        List<String> all = load();
        Map<String, HistoryMetadata> metaMap = loadMetadataMap();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();

        List<String> pinnedMatches = new ArrayList<>();
        List<String> normalMatches = new ArrayList<>();

        for (String q : all) {
            if (!kw.isEmpty() && !q.toLowerCase().contains(kw)) {
                continue;
            }
            HistoryMetadata m = metaMap.get(q);
            boolean isPin = m != null && m.pinned;
            if (pinnedOnly && !isPin) {
                continue;
            }
            if (isPin) {
                pinnedMatches.add(q);
            } else {
                normalMatches.add(q);
            }
        }

        List<String> result = new ArrayList<>(pinnedMatches);
        result.addAll(normalMatches);
        return result;
    }

    public static synchronized void remove(String query) {
        List<String> history = load();
        history.remove(query);
        save(history);
        Map<String, HistoryMetadata> metaMap = loadMetadataMap();
        if (metaMap.remove(query) != null) {
            saveMetadataMap(metaMap);
        }
    }

    public static synchronized void save(List<String> history) {
        cachedHistory = history != null ? new ArrayList<>(history) : new ArrayList<>();
        final List<String> copyToPersist = new ArrayList<>(cachedHistory);
        final File file = getFile();
        if (customFile != null) {
            try {
                mapper.writeValue(file, copyToPersist);
            } catch (Exception ignored) {}
            return;
        }
        asyncSaveExecutor.submit(() -> {
            try {
                mapper.writeValue(file, copyToPersist);
            } catch (Exception ignored) {}
        });
    }

    public static synchronized void clear() {
        cachedHistory = new ArrayList<>();
        cachedMetaMap = new HashMap<>();
        final File file = getFile();
        final File metaFile = getMetaFile();
        if (customFile != null) {
            try {
                mapper.writeValue(file, new ArrayList<>());
                if (metaFile.exists()) {
                    metaFile.delete();
                }
            } catch (Exception ignored) {}
            return;
        }
        asyncSaveExecutor.submit(() -> {
            try {
                mapper.writeValue(file, new ArrayList<>());
                if (metaFile.exists()) {
                    metaFile.delete();
                }
            } catch (Exception ignored) {}
        });
    }

    public static void flush() {
        try {
            asyncSaveExecutor.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }
}
