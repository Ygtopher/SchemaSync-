package com.dbtool.util;

import java.util.ArrayList;
import java.util.List;

public class QueryLogger {
    public static class LogEntry {
        public String query;
        public long durationMs;
        public boolean success;
        public String timestamp;
        
        public LogEntry(String query, long durationMs, boolean success, String timestamp) {
            this.query = query;
            this.durationMs = durationMs;
            this.success = success;
            this.timestamp = timestamp;
        }
    }
    
    private static final List<LogEntry> logs = new ArrayList<>();
    private static final List<Runnable> listeners = new ArrayList<>();
    
    public static synchronized void log(String query, long durationMs, boolean success) {
        String ts = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        logs.add(new LogEntry(query, durationMs, success, ts));
        for (Runnable r : listeners) {
            r.run();
        }
    }
    
    public static synchronized List<LogEntry> getLogs() {
        return new ArrayList<>(logs);
    }
    
    public static synchronized void addListener(Runnable r) {
        listeners.add(r);
    }
}
