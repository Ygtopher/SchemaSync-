package com.dbtool.util;

import com.dbtool.model.SessionSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SessionRecoveryManager {

    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static File RECOVERY_DIR = new File(System.getProperty("user.home"), ".dbtool/recovery");
    private static File AUTOSAVE_FILE = new File(RECOVERY_DIR, "session_autosave.json");
    private static File TMP_FILE = new File(RECOVERY_DIR, "session_autosave.tmp");
    private static File LOCK_FILE = new File(RECOVERY_DIR, "session.lock");
    private static File ARCHIVE_DIR = new File(RECOVERY_DIR, "archive");

    private static ScheduledExecutorService scheduler;
    private static String lastSavedJson = "";
    private static volatile boolean isCleanExit = false;

    static {
        if (!RECOVERY_DIR.exists()) {
            RECOVERY_DIR.mkdirs();
        }
    }

    public static synchronized void setRecoveryDirForTesting(File dir) {
        if (dir == null) {
            RECOVERY_DIR = new File(System.getProperty("user.home"), ".dbtool/recovery");
        } else {
            RECOVERY_DIR = dir;
        }
        AUTOSAVE_FILE = new File(RECOVERY_DIR, "session_autosave.json");
        TMP_FILE = new File(RECOVERY_DIR, "session_autosave.tmp");
        LOCK_FILE = new File(RECOVERY_DIR, "session.lock");
        ARCHIVE_DIR = new File(RECOVERY_DIR, "archive");
        if (!RECOVERY_DIR.exists()) {
            RECOVERY_DIR.mkdirs();
        }
        lastSavedJson = "";
    }

    public static File getRecoveryDir() {
        return RECOVERY_DIR;
    }

    public static File getAutosaveFile() {
        return AUTOSAVE_FILE;
    }

    /**
     * Checks if a crashed or unclosed session exists from a previous run.
     */
    public static boolean hasCrashedSession() {
        if (!AUTOSAVE_FILE.exists() || AUTOSAVE_FILE.length() == 0) {
            return false;
        }
        // If the lock file was left behind, the app crashed or was killed without clean exit
        if (LOCK_FILE.exists()) {
            return true;
        }
        // Also check if the autosave file explicitly states cleanExit == false
        try {
            SessionSnapshot snapshot = loadAutosavedSession();
            return snapshot != null && !snapshot.cleanExit;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Initializes lock file for the current running session.
     */
    public static void initializeSessionLock() {
        isCleanExit = false;
        try {
            if (!RECOVERY_DIR.exists()) RECOVERY_DIR.mkdirs();
            String pid = String.valueOf(ProcessHandle.current().pid());
            String info = "PID: " + pid + "\nStartTime: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n";
            try (FileOutputStream fos = new FileOutputStream(LOCK_FILE)) {
                fos.write(info.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Loads the autosaved session snapshot.
     */
    public static SessionSnapshot loadAutosavedSession() {
        try {
            if (AUTOSAVE_FILE.exists()) {
                return mapper.readValue(AUTOSAVE_FILE, SessionSnapshot.class);
            }
        } catch (Exception e) {
            System.err.println("Failed to read autosave session: " + e.getMessage());
        }
        return null;
    }

    /**
     * Atomically saves the snapshot to disk.
     */
    public static synchronized boolean saveSessionSnapshot(SessionSnapshot snapshot) {
        if (snapshot == null) return false;
        try {
            if (!RECOVERY_DIR.exists()) RECOVERY_DIR.mkdirs();

            String json = mapper.writeValueAsString(snapshot);
            if (json.equals(lastSavedJson)) {
                return false; // No changes since last save
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(TMP_FILE), StandardCharsets.UTF_8)) {
                writer.write(json);
                writer.flush();
            }

            // Atomic rename
            if (AUTOSAVE_FILE.exists()) {
                AUTOSAVE_FILE.delete();
            }
            boolean renamed = TMP_FILE.renameTo(AUTOSAVE_FILE);
            if (!renamed) {
                // Fallback copy if rename fails on Windows
                try (FileOutputStream fos = new FileOutputStream(AUTOSAVE_FILE)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
                TMP_FILE.delete();
            }

            lastSavedJson = json;
            return true;
        } catch (Exception e) {
            System.err.println("Error saving session snapshot: " + e.getMessage());
            return false;
        }
    }

    /**
     * Starts the background periodic auto-save daemon.
     */
    public static synchronized void startAutoSaveDaemon(Supplier<SessionSnapshot> snapshotSupplier, Consumer<String> onSavedCallback, int intervalSeconds) {
        stopAutoSaveDaemon();

        initializeSessionLock();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoSave-Daemon");
            t.setDaemon(true);
            return t;
        });

        int interval = intervalSeconds > 0 ? intervalSeconds : 15;
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (isCleanExit) return;
                SessionSnapshot snapshot = snapshotSupplier.get();
                if (snapshot != null) {
                    boolean saved = saveSessionSnapshot(snapshot);
                    if (saved && onSavedCallback != null) {
                        String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        SwingUtilities.invokeLater(() -> onSavedCallback.accept(timeStr));
                    }
                }
            } catch (Exception ignored) {}
        }, interval, interval, TimeUnit.SECONDS);
    }

    /**
     * Stops the auto-save daemon.
     */
    public static synchronized void stopAutoSaveDaemon() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Registers a JVM shutdown hook for emergency snapshot saving.
     */
    public static void registerShutdownHook(Supplier<SessionSnapshot> snapshotSupplier) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!isCleanExit) {
                try {
                    SessionSnapshot snapshot = snapshotSupplier.get();
                    if (snapshot != null) {
                        snapshot.cleanExit = false;
                        saveSessionSnapshot(snapshot);
                    }
                } catch (Exception ignored) {}
            }
        }, "AutoSave-EmergencyShutdownHook"));
    }

    /**
     * Marks clean exit upon normal closing.
     */
    public static synchronized void markCleanExit() {
        isCleanExit = true;
        stopAutoSaveDaemon();
        try {
            if (LOCK_FILE.exists()) {
                LOCK_FILE.delete();
            }
            if (AUTOSAVE_FILE.exists()) {
                SessionSnapshot snapshot = loadAutosavedSession();
                if (snapshot != null) {
                    snapshot.cleanExit = true;
                    mapper.writeValue(AUTOSAVE_FILE, snapshot);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Discards the crashed session and archives it safely.
     */
    public static synchronized void discardCrashedSession() {
        try {
            if (AUTOSAVE_FILE.exists()) {
                if (!ARCHIVE_DIR.exists()) ARCHIVE_DIR.mkdirs();
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File archiveFile = new File(ARCHIVE_DIR, "discarded_" + ts + ".json");
                AUTOSAVE_FILE.renameTo(archiveFile);
            }
            if (LOCK_FILE.exists()) {
                LOCK_FILE.delete();
            }
        } catch (Exception ignored) {}
    }
}
