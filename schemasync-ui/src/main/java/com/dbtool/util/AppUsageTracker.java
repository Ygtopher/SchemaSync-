package com.dbtool.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistent & Live Application Usage Statistics Tracker.
 * Tracks app sessions, uptime, SQL query executions, latency percentiles (P50/P95/Max),
 * slow queries, rows fetched/mutated, daily activity streaks, hot queried tables,
 * safety/linter governance events, data movement/code generation, database engine connections,
 * and workbench feature usage across sessions.
 */
public class AppUsageTracker {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UsageStatsData {
        public long totalSessions = 0;
        public long firstUsedTimestamp = 0;
        public long lastActiveTimestamp = 0;
        public long persistedUptimeSeconds = 0;

        public long totalQueriesExecuted = 0;
        public long successfulQueries = 0;
        public long failedQueries = 0;
        public long totalQueryDurationMs = 0;

        // 1. Latency Percentiles, Slow Queries & Row Volume
        public long maxQueryDurationMs = 0;
        public long slowQueriesCount = 0;       // >= 500 ms
        public long verySlowQueriesCount = 0;   // >= 1000 ms
        public String slowestQuerySql = "";
        public List<Long> recentLatenciesMs = new ArrayList<>();
        public long totalRowsFetched = 0;
        public long totalRowsMutated = 0;

        // Statement Type Breakdown
        public long selectQueries = 0;
        public long dmlQueries = 0;
        public long ddlQueries = 0;
        public long explainQueries = 0;
        public long otherQueries = 0;

        // 2. Daily Trends & Hot Tables
        public Map<String, Long> dailyQueryCounts = new LinkedHashMap<>();
        public Map<String, Long> queriedTableCounts = new LinkedHashMap<>();

        // 3. Safety, Linter & Governance Telemetry
        public long destructiveQueriesIntercepted = 0;
        public long linterIssuesDetected = 0;
        public long linterFixesApplied = 0;
        public long piiRowsMasked = 0;
        public long schemaSnapshotsAndMigrations = 0;

        // 5. Data Movement & Code Generation Counters
        public long totalConnectionsOpened = 0;
        public long totalExportsPerformed = 0;
        public long rowsImported = 0;
        public long mockRowsGenerated = 0;
        public long dualDbComparisonsRun = 0;
        public long ormModelsGenerated = 0;

        public Map<String, Long> engineConnections = new LinkedHashMap<>();
        public Map<String, Long> featureUsageCounts = new LinkedHashMap<>();
    }

    private static final int MAX_LATENCY_SAMPLES = 500;
    private static final Pattern TABLE_EXTRACT_PATTERN = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|INTO|UPDATE|TABLE|INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\"`\\[]?[a-zA-Z0-9_]+[\"`\\]]?\\s+ON)\\s+([\"`\\[]?[a-zA-Z_][a-zA-Z0-9_.]*[\"`\\]]?)"
    );
    private static final Set<String> IGNORED_TABLE_TOKENS = new HashSet<>(Arrays.asList(
            "select", "where", "set", "values", "dual", "unnest", "lateral", "only",
            "information_schema", "pg_catalog", "pg_stat_user_tables", "pg_indexes",
            "pg_database", "sqlite_master", "sys", "temp"
    ));

    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static File customFile = null;
    private static UsageStatsData stats = null;

    // Live current-session metrics
    private static long sessionStartMs = System.currentTimeMillis();
    private static long lastFlushedUptimeSeconds = 0;
    private static long sessionQueriesExecuted = 0;
    private static long sessionSuccessfulQueries = 0;
    private static long sessionFailedQueries = 0;
    private static long sessionTotalDurationMs = 0;
    private static long sessionSlowQueries = 0;

    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public static synchronized void setFileForTesting(File file) {
        customFile = file;
        stats = null;
        sessionStartMs = System.currentTimeMillis();
        lastFlushedUptimeSeconds = 0;
        sessionQueriesExecuted = 0;
        sessionSuccessfulQueries = 0;
        sessionFailedQueries = 0;
        sessionTotalDurationMs = 0;
        sessionSlowQueries = 0;
    }

    public static synchronized File getFile() {
        if (customFile != null) return customFile;
        return FileUtil.getSaveFile("app_usage_stats.json");
    }

    public static synchronized UsageStatsData getStats() {
        if (stats == null) {
            loadFromDisk();
        }
        return stats;
    }

    private static synchronized void loadFromDisk() {
        File file = getFile();
        if (file.exists() && file.length() > 0) {
            try {
                stats = mapper.readValue(file, UsageStatsData.class);
            } catch (Exception ignored) {
                stats = null;
            }
        }
        if (stats == null) {
            stats = new UsageStatsData();
            long now = System.currentTimeMillis();
            stats.firstUsedTimestamp = now;
            stats.lastActiveTimestamp = now;
            bootstrapFromExistingHistory(stats);
            persist();
        } else {
            ensureNonNullCollections(stats);
        }
    }

    private static void ensureNonNullCollections(UsageStatsData d) {
        if (d.firstUsedTimestamp <= 0) {
            d.firstUsedTimestamp = System.currentTimeMillis();
        }
        if (d.recentLatenciesMs == null) d.recentLatenciesMs = new ArrayList<>();
        if (d.dailyQueryCounts == null) d.dailyQueryCounts = new LinkedHashMap<>();
        if (d.queriedTableCounts == null) d.queriedTableCounts = new LinkedHashMap<>();
        if (d.engineConnections == null) d.engineConnections = new LinkedHashMap<>();
        if (d.featureUsageCounts == null) d.featureUsageCounts = new LinkedHashMap<>();
        if (d.slowestQuerySql == null) d.slowestQuerySql = "";
    }

    private static void bootstrapFromExistingHistory(UsageStatsData target) {
        try {
            List<String> queries = QueryHistory.load();
            Map<String, QueryHistory.HistoryMetadata> metaMap = QueryHistory.loadMetadataMap();
            if (queries != null && !queries.isEmpty()) {
                String today = getTodayKey();
                for (String sql : queries) {
                    if (sql == null || sql.trim().isEmpty()) continue;
                    QueryHistory.HistoryMetadata e = metaMap != null ? metaMap.get(sql) : null;
                    target.totalQueriesExecuted++;
                    target.successfulQueries++;
                    long dur = (e != null && e.durationMs > 0) ? e.durationMs : 0L;
                    target.totalQueryDurationMs += dur;
                    recordLatencySample(target, sql, dur);
                    if (e != null && e.rowCount > 0) {
                        target.totalRowsFetched += e.rowCount;
                    }
                    String dayKey = today;
                    if (e != null && e.timestamp > 0) {
                        try {
                            dayKey = java.time.Instant.ofEpochMilli(e.timestamp)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (Exception ignored) {}
                    }
                    incrementMap(target.dailyQueryCounts, dayKey);
                    categorizeQuery(target, sql);
                    extractAndRecordTables(target, sql);
                }
                if (target.totalSessions == 0) {
                    target.totalSessions = 1;
                }
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void persist() {
        if (stats == null) return;
        try {
            stats.lastActiveTimestamp = System.currentTimeMillis();
            mapper.writeValue(getFile(), stats);
        } catch (Exception ignored) {}
    }

    public static synchronized void recordSessionStart() {
        UsageStatsData d = getStats();
        d.totalSessions++;
        if (d.firstUsedTimestamp <= 0) {
            d.firstUsedTimestamp = System.currentTimeMillis();
        }
        sessionStartMs = System.currentTimeMillis();
        lastFlushedUptimeSeconds = 0;
        incrementMap(d.featureUsageCounts, "🏠 Dashboard");
        persist();
        notifyListeners();
    }

    public static synchronized void flushSessionUptime() {
        UsageStatsData d = getStats();
        long currentSec = Math.max(0L, (System.currentTimeMillis() - sessionStartMs) / 1000L);
        long delta = Math.max(0L, currentSec - lastFlushedUptimeSeconds);
        if (delta > 0) {
            d.persistedUptimeSeconds += delta;
            lastFlushedUptimeSeconds = currentSec;
            persist();
        }
    }

    public static synchronized void recordQuery(String sql, long durationMs, boolean success) {
        if (sql == null || sql.trim().isEmpty()) return;
        String trimmed = sql.trim();
        if ("SELECT 1".equalsIgnoreCase(trimmed)) return;

        UsageStatsData d = getStats();
        long safeDuration = Math.max(0L, durationMs);

        d.totalQueriesExecuted++;
        d.totalQueryDurationMs += safeDuration;
        if (success) {
            d.successfulQueries++;
        } else {
            d.failedQueries++;
        }

        recordLatencySample(d, trimmed, safeDuration);
        incrementMap(d.dailyQueryCounts, getTodayKey());
        pruneDailyCountsIfNeeded(d.dailyQueryCounts);
        categorizeQuery(d, trimmed);
        extractAndRecordTables(d, trimmed);

        sessionQueriesExecuted++;
        sessionTotalDurationMs += safeDuration;
        if (safeDuration >= 500L) {
            sessionSlowQueries++;
        }
        if (success) {
            sessionSuccessfulQueries++;
        } else {
            sessionFailedQueries++;
        }

        flushSessionUptime();
        persist();
        notifyListeners();
    }

    private static void recordLatencySample(UsageStatsData d, String sql, long durationMs) {
        d.recentLatenciesMs.add(durationMs);
        while (d.recentLatenciesMs.size() > MAX_LATENCY_SAMPLES) {
            d.recentLatenciesMs.remove(0);
        }
        if (durationMs >= 500L) {
            d.slowQueriesCount++;
        }
        if (durationMs >= 1000L) {
            d.verySlowQueriesCount++;
        }
        if (durationMs >= d.maxQueryDurationMs && durationMs > 0) {
            d.maxQueryDurationMs = durationMs;
            String oneLine = sql.replaceAll("\\s+", " ").trim();
            d.slowestQuerySql = oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 117) + "...";
        }
    }

    private static void extractAndRecordTables(UsageStatsData d, String sql) {
        String clean = stripLeadingComments(sql);
        Matcher m = TABLE_EXTRACT_PATTERN.matcher(clean);
        while (m.find()) {
            String raw = m.group(1);
            if (raw == null) continue;
            String tbl = raw.replaceAll("[\"`\\[\\]]", "").trim().toLowerCase(Locale.ROOT);
            if (tbl.isEmpty() || tbl.startsWith("(")) continue;
            if (tbl.startsWith("public.")) {
                tbl = tbl.substring("public.".length());
            }
            String prefix = tbl.contains(".") ? tbl.substring(0, tbl.indexOf('.')) : tbl;
            if (IGNORED_TABLE_TOKENS.contains(tbl) || IGNORED_TABLE_TOKENS.contains(prefix)) {
                continue;
            }
            incrementMap(d.queriedTableCounts, tbl);
        }
    }

    private static void pruneDailyCountsIfNeeded(Map<String, Long> dailyMap) {
        if (dailyMap == null || dailyMap.size() <= 90) return;
        Iterator<String> it = dailyMap.keySet().iterator();
        while (dailyMap.size() > 90 && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    public static synchronized void recordRowsFetched(long rows) {
        if (rows <= 0) return;
        UsageStatsData d = getStats();
        d.totalRowsFetched += rows;
        persist();
        notifyListeners();
    }

    public static synchronized void recordRowsMutated(long rows) {
        if (rows <= 0) return;
        UsageStatsData d = getStats();
        d.totalRowsMutated += rows;
        persist();
        notifyListeners();
    }

    public static synchronized void recordDestructiveQueryIntercepted() {
        UsageStatsData d = getStats();
        d.destructiveQueriesIntercepted++;
        persist();
        notifyListeners();
    }

    public static synchronized void recordLinterIssues(int issueCount) {
        if (issueCount <= 0) return;
        UsageStatsData d = getStats();
        d.linterIssuesDetected += issueCount;
        persist();
        notifyListeners();
    }

    public static synchronized void recordLinterFixApplied() {
        UsageStatsData d = getStats();
        d.linterFixesApplied++;
        persist();
        notifyListeners();
    }

    public static synchronized void recordPiiRowsMasked(long rows) {
        if (rows <= 0) return;
        UsageStatsData d = getStats();
        d.piiRowsMasked += rows;
        persist();
        notifyListeners();
    }

    public static synchronized void recordSchemaDriftAction() {
        UsageStatsData d = getStats();
        d.schemaSnapshotsAndMigrations++;
        persist();
        notifyListeners();
    }

    public static synchronized void recordRowsImported(long rows) {
        if (rows <= 0) return;
        UsageStatsData d = getStats();
        d.rowsImported += rows;
        d.totalRowsMutated += rows;
        persist();
        notifyListeners();
    }

    public static synchronized void recordMockRowsGenerated(long rows) {
        if (rows <= 0) return;
        UsageStatsData d = getStats();
        d.mockRowsGenerated += rows;
        d.totalRowsMutated += rows;
        persist();
        notifyListeners();
    }

    public static synchronized void recordDualDbComparison() {
        UsageStatsData d = getStats();
        d.dualDbComparisonsRun++;
        persist();
        notifyListeners();
    }

    public static synchronized void recordOrmModelGenerated() {
        UsageStatsData d = getStats();
        d.ormModelsGenerated++;
        persist();
        notifyListeners();
    }

    private static void categorizeQuery(UsageStatsData d, String sql) {
        String clean = stripLeadingComments(sql).toUpperCase(Locale.ROOT);
        if (clean.startsWith("EXPLAIN")) {
            d.explainQueries++;
        } else if (clean.startsWith("SELECT") || clean.startsWith("WITH") || clean.startsWith("SHOW") || clean.startsWith("DESCRIBE") || clean.startsWith("DESC ") || clean.startsWith("PRAGMA")) {
            d.selectQueries++;
        } else if (clean.startsWith("INSERT") || clean.startsWith("UPDATE") || clean.startsWith("DELETE") || clean.startsWith("MERGE") || clean.startsWith("REPLACE")) {
            d.dmlQueries++;
        } else if (clean.startsWith("CREATE") || clean.startsWith("ALTER") || clean.startsWith("DROP") || clean.startsWith("TRUNCATE") || clean.startsWith("RENAME")) {
            d.ddlQueries++;
        } else {
            d.otherQueries++;
        }
    }

    private static String stripLeadingComments(String sql) {
        if (sql == null) return "";
        String s = sql.trim();
        while (s.startsWith("--") || s.startsWith("/*")) {
            if (s.startsWith("--")) {
                int nl = s.indexOf('\n');
                if (nl == -1) return "";
                s = s.substring(nl + 1).trim();
            } else if (s.startsWith("/*")) {
                int end = s.indexOf("*/");
                if (end == -1) return "";
                s = s.substring(end + 2).trim();
            }
        }
        return s;
    }

    public static synchronized void recordConnection(String engineName) {
        if (engineName == null || engineName.trim().isEmpty()) return;
        UsageStatsData d = getStats();
        d.totalConnectionsOpened++;
        incrementMap(d.engineConnections, normalizeEngineName(engineName));
        persist();
        notifyListeners();
    }

    private static String normalizeEngineName(String raw) {
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (u.contains("POSTGRES")) return "PostgreSQL";
        if (u.contains("MYSQL") || u.contains("MARIADB")) return "MySQL / MariaDB";
        if (u.contains("SQLITE")) return "SQLite";
        if (u.contains("ACCESS") || u.contains("UCANACCESS")) return "MS Access";
        if (u.contains("SANDBOX") || u.contains("H2")) return "H2 Sandbox";
        return raw.trim();
    }

    public static synchronized void recordFeatureUse(String featureName) {
        if (featureName == null || featureName.trim().isEmpty()) return;
        UsageStatsData d = getStats();
        incrementMap(d.featureUsageCounts, featureName.trim());
        persist();
        notifyListeners();
    }

    public static synchronized void recordExport(String exportFormat) {
        UsageStatsData d = getStats();
        d.totalExportsPerformed++;
        String label = (exportFormat != null && !exportFormat.trim().isEmpty())
                ? "Export (" + exportFormat.trim() + ")"
                : "Data Export";
        incrementMap(d.featureUsageCounts, label);
        persist();
        notifyListeners();
    }

    public static synchronized void resetStats() {
        stats = new UsageStatsData();
        long now = System.currentTimeMillis();
        stats.totalSessions = 1;
        stats.firstUsedTimestamp = now;
        stats.lastActiveTimestamp = now;
        sessionStartMs = now;
        lastFlushedUptimeSeconds = 0;
        sessionQueriesExecuted = 0;
        sessionSuccessfulQueries = 0;
        sessionFailedQueries = 0;
        sessionTotalDurationMs = 0;
        sessionSlowQueries = 0;
        persist();
        notifyListeners();
    }

    private static void incrementMap(Map<String, Long> map, String key) {
        if (map == null || key == null) return;
        map.put(key, map.getOrDefault(key, 0L) + 1L);
    }

    public static synchronized long getSessionQueriesExecuted() {
        return sessionQueriesExecuted;
    }

    public static synchronized long getSessionSuccessfulQueries() {
        return sessionSuccessfulQueries;
    }

    public static synchronized long getSessionFailedQueries() {
        return sessionFailedQueries;
    }

    public static synchronized long getSessionSlowQueries() {
        return sessionSlowQueries;
    }

    public static synchronized long getCurrentSessionUptimeSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - sessionStartMs) / 1000L);
    }

    public static synchronized long getTotalUptimeSeconds() {
        UsageStatsData d = getStats();
        long currentSec = getCurrentSessionUptimeSeconds();
        long unFlushed = Math.max(0L, currentSec - lastFlushedUptimeSeconds);
        return Math.max(0L, d.persistedUptimeSeconds + unFlushed);
    }

    public static synchronized double getSuccessRatePercent() {
        UsageStatsData d = getStats();
        if (d.totalQueriesExecuted <= 0) return 100.0;
        return (d.successfulQueries * 100.0) / d.totalQueriesExecuted;
    }

    public static synchronized long getAverageQueryDurationMs() {
        UsageStatsData d = getStats();
        if (d.totalQueriesExecuted <= 0) return 0L;
        return Math.round((double) d.totalQueryDurationMs / d.totalQueriesExecuted);
    }

    public static synchronized long getPercentileLatencyMs(double percentile) {
        UsageStatsData d = getStats();
        if (d.recentLatenciesMs == null || d.recentLatenciesMs.isEmpty()) {
            return getAverageQueryDurationMs();
        }
        List<Long> sorted = new ArrayList<>(d.recentLatenciesMs);
        sorted.removeIf(Objects::isNull);
        if (sorted.isEmpty()) return 0L;
        Collections.sort(sorted);
        int idx = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    public static synchronized long getP50LatencyMs() {
        return getPercentileLatencyMs(50.0);
    }

    public static synchronized long getP95LatencyMs() {
        return getPercentileLatencyMs(95.0);
    }

    private static String getTodayKey() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    public static synchronized long getTodayQueriesCount() {
        UsageStatsData d = getStats();
        return d.dailyQueryCounts.getOrDefault(getTodayKey(), 0L);
    }

    public static synchronized long getYesterdayQueriesCount() {
        UsageStatsData d = getStats();
        String yKey = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return d.dailyQueryCounts.getOrDefault(yKey, 0L);
    }

    public static synchronized long getLast7DaysQueriesCount() {
        UsageStatsData d = getStats();
        long sum = 0L;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            String key = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            sum += d.dailyQueryCounts.getOrDefault(key, 0L);
        }
        return sum;
    }

    public static synchronized int getActiveDaysCount() {
        UsageStatsData d = getStats();
        int count = 0;
        for (Long v : d.dailyQueryCounts.values()) {
            if (v != null && v > 0) count++;
        }
        return Math.max(1, count);
    }

    public static synchronized int getCurrentDayStreak() {
        UsageStatsData d = getStats();
        if (d.dailyQueryCounts.isEmpty()) return 1;
        LocalDate cursor = LocalDate.now();
        String todayKey = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (d.dailyQueryCounts.getOrDefault(todayKey, 0L) <= 0) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (true) {
            String key = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE);
            if (d.dailyQueryCounts.getOrDefault(key, 0L) > 0) {
                streak++;
                cursor = cursor.minusDays(1);
            } else {
                break;
            }
        }
        return Math.max(1, streak);
    }

    public static synchronized String getTopEntry(Map<String, Long> map, String fallback) {
        if (map == null || map.isEmpty()) return fallback;
        String bestKey = null;
        long bestVal = -1;
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (e.getValue() != null && e.getValue() > bestVal) {
                bestVal = e.getValue();
                bestKey = e.getKey();
            }
        }
        return bestKey != null ? bestKey + " (" + bestVal + ")" : fallback;
    }

    public static long getJvmUsedMemoryMb() {
        Runtime rt = Runtime.getRuntime();
        return Math.max(1L, (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L));
    }

    public static long getJvmTotalMemoryMb() {
        return Math.max(1L, Runtime.getRuntime().totalMemory() / (1024L * 1024L));
    }

    public static long getJvmMaxMemoryMb() {
        return Math.max(1L, Runtime.getRuntime().maxMemory() / (1024L * 1024L));
    }

    public static int getJvmMemoryUsagePercent() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = Math.max(1L, rt.maxMemory());
        return (int) Math.min(100L, Math.round((used * 100.0) / max));
    }

    public static String formatDuration(long totalSeconds) {
        long sec = Math.max(0L, totalSeconds);
        long hours = sec / 3600L;
        long mins = (sec % 3600L) / 60L;
        long remSec = sec % 60L;
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, mins, remSec);
        } else if (mins > 0) {
            return String.format("%dm %ds", mins, remSec);
        } else {
            return String.format("%ds", remSec);
        }
    }

    public static void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private static void notifyListeners() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }
}
