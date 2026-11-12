package com.dbtool.core.config;

import com.dbtool.core.model.DatabaseType;
import com.dbtool.core.model.SslMode;
import com.dbtool.util.CryptoUtil;
import com.dbtool.util.SecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Version 2 JSON Credential & Connection Profile Store (`profiles.v2.json`).
 * Replaces legacy semicolon-delimited `.properties` files and raw Base64 SSH storage
 * with unified AES-256-GCM authenticated encryption backed by OS Keyring / Master Password.
 */
public final class ProfileStoreV2 {

    public static final int CURRENT_FORMAT_VERSION = 2;
    private static final String STORE_FILENAME = "profiles.v2.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static class StoreData {
        public int version = CURRENT_FORMAT_VERSION;
        public Map<String, DbProfile> dbProfiles = new LinkedHashMap<>();
        public Map<String, SshProfile> sshProfiles = new LinkedHashMap<>();
        public Map<String, String> aiSecrets = new LinkedHashMap<>();
    }

    public static class DbProfile {
        public String name;
        public String type = DatabaseType.POSTGRESQL.name();
        public String host = "";
        public String port = "";
        public String databaseName = "";
        public String username = "";
        public String encryptedPassword = "";
        public String sslMode = SslMode.PREFER.name();

        public DbProfile() {}

        public DbProfile(String name, String type, String host, String port, String databaseName, String username, String encryptedPassword, String sslMode) {
            this.name = name;
            this.type = type != null ? type : DatabaseType.POSTGRESQL.name();
            this.host = host != null ? host : "";
            this.port = port != null ? port : "";
            this.databaseName = databaseName != null ? databaseName : "";
            this.username = username != null ? username : "";
            this.encryptedPassword = encryptedPassword != null ? encryptedPassword : "";
            this.sslMode = sslMode != null ? sslMode : SslMode.PREFER.name();
        }
    }

    public static class SshProfile {
        public String name;
        public String host = "";
        public String port = "22";
        public String username = "";
        public String encryptedPassword = "";
        public String keyPath = "";

        public SshProfile() {}

        public SshProfile(String name, String host, String port, String username, String encryptedPassword, String keyPath) {
            this.name = name;
            this.host = host != null ? host : "";
            this.port = port != null ? port : "22";
            this.username = username != null ? username : "";
            this.encryptedPassword = encryptedPassword != null ? encryptedPassword : "";
            this.keyPath = keyPath != null ? keyPath : "";
        }
    }

    private ProfileStoreV2() {}

    public static Path getStorePath() {
        return CryptoUtil.getConfigDir().resolve(STORE_FILENAME);
    }

    public static synchronized StoreData load() {
        Path storePath = getStorePath();
        if (!Files.exists(storePath)) {
            return new StoreData();
        }
        try {
            StoreData data = MAPPER.readValue(storePath.toFile(), StoreData.class);
            if (data == null) return new StoreData();
            if (data.dbProfiles == null) data.dbProfiles = new LinkedHashMap<>();
            if (data.sshProfiles == null) data.sshProfiles = new LinkedHashMap<>();
            if (data.aiSecrets == null) data.aiSecrets = new LinkedHashMap<>();
            return data;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read V2 profile store at " + storePath, e);
        }
    }

    public static synchronized void save(StoreData data) {
        if (data == null) return;
        try {
            Path storePath = getStorePath();
            Files.createDirectories(storePath.getParent());
            data.version = CURRENT_FORMAT_VERSION;
            Path temp = storePath.resolveSibling(STORE_FILENAME + ".tmp");
            MAPPER.writeValue(temp.toFile(), data);
            try {
                Files.move(temp, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temp, storePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save V2 profile store", e);
        }
    }

    /**
     * One-time V1 -> V2 migration for legacy `saved_connections.properties` and
     * `saved_ssh_connections.properties`. Migrated V1 files are renamed to `*.v1.migrated`
     * and any legacy `.device_seed` is wiped once migration finishes.
     */
    public static synchronized int migrateLegacyIfNeeded(Path legacyDbPropsFile, Path legacySshPropsFile, Path legacySeedDir) {
        int migratedCount = 0;
        boolean hasDb = legacyDbPropsFile != null && Files.exists(legacyDbPropsFile);
        boolean hasSsh = legacySshPropsFile != null && Files.exists(legacySshPropsFile);
        if (!hasDb && !hasSsh) {
            return 0;
        }

        StoreData store = load();

        if (hasDb) {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(legacyDbPropsFile.toFile())) {
                props.load(in);
                for (String name : props.stringPropertyNames()) {
                    String val = props.getProperty(name);
                    if (val == null) continue;
                    String[] parts = val.split(";");
                    if (parts.length >= 5) {
                        String host = parts[0].trim();
                        String port = parts[1].trim();
                        String user = parts[2].trim();
                        String rawPass = parts[3];
                        String dbName = parts[4].trim();
                        String type = (parts.length >= 6 && !parts[5].trim().isEmpty())
                                ? parts[5].trim()
                                : inferDatabaseType(host, port, dbName);
                        String sslMode = (parts.length >= 7 && !parts[6].trim().isEmpty())
                                ? parts[6].trim()
                                : SslMode.PREFER.name();

                        String plainPass = CryptoUtil.decryptLegacyForMigration(rawPass, legacySeedDir);
                        String v2Cipher = CryptoUtil.encryptPassword(plainPass);
                        if (!store.dbProfiles.containsKey(name)) {
                            store.dbProfiles.put(name, new DbProfile(name, type, host, port, dbName, user, v2Cipher, sslMode));
                            migratedCount++;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (hasSsh) {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(legacySshPropsFile.toFile())) {
                props.load(in);
                for (String name : props.stringPropertyNames()) {
                    String val = props.getProperty(name);
                    if (val == null) continue;
                    String[] parts = val.split(";");
                    if (parts.length >= 4) {
                        String host = parts[0].trim();
                        String port = parts[1].trim();
                        String user = parts[2].trim();
                        String rawPass = parts[3];
                        String keyPath = parts.length >= 5 ? parts[4].trim() : "";

                        String plainPass = CryptoUtil.decryptLegacyForMigration(rawPass, legacySeedDir);
                        String v2Cipher = CryptoUtil.encryptPassword(plainPass);
                        if (!store.sshProfiles.containsKey(name)) {
                            store.sshProfiles.put(name, new SshProfile(name, host, port, user, v2Cipher, keyPath));
                            migratedCount++;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (migratedCount > 0 || Files.exists(getStorePath())) {
            save(store);
        }

        archiveLegacyFile(legacyDbPropsFile);
        archiveLegacyFile(legacySshPropsFile);
        if (legacySeedDir != null) {
            SecretStore.deleteLegacyDeviceSeed(legacySeedDir);
        }
        return migratedCount;
    }

    public static synchronized void saveDbProfile(String profileName, String type, String host, String port, String user, String plainPass, String dbName, SslMode sslMode) {
        StoreData data = load();
        String cipher = CryptoUtil.encryptPassword(plainPass);
        String resolvedType = (type != null && !type.trim().isEmpty()) ? type.trim() : inferDatabaseType(host, port, dbName);
        String resolvedSsl = sslMode != null ? sslMode.name() : SslMode.PREFER.name();
        data.dbProfiles.put(profileName, new DbProfile(profileName, resolvedType, host, port, dbName, user, cipher, resolvedSsl));
        save(data);
    }

    public static synchronized void deleteDbProfile(String profileName) {
        StoreData data = load();
        if (data.dbProfiles.remove(profileName) != null) {
            save(data);
        }
    }

    public static synchronized void saveSshProfile(String profileName, String host, String port, String user, String plainPass, String keyPath) {
        StoreData data = load();
        String cipher = CryptoUtil.encryptPassword(plainPass);
        data.sshProfiles.put(profileName, new SshProfile(profileName, host, port, user, cipher, keyPath));
        save(data);
    }

    public static synchronized void deleteSshProfile(String profileName) {
        StoreData data = load();
        if (data.sshProfiles.remove(profileName) != null) {
            save(data);
        }
    }

    public static synchronized void saveAiSecret(String providerKey, String plainApiKey) {
        StoreData data = load();
        if (plainApiKey == null || plainApiKey.isEmpty()) {
            data.aiSecrets.remove(providerKey);
        } else {
            data.aiSecrets.put(providerKey, CryptoUtil.encryptPassword(plainApiKey));
        }
        save(data);
    }

    public static synchronized String getAiSecret(String providerKey) {
        StoreData data = load();
        String cipher = data.aiSecrets.get(providerKey);
        return (cipher == null || cipher.isEmpty()) ? "" : CryptoUtil.decryptPassword(cipher);
    }

    /**
     * Atomically re-encrypts all DB, SSH, and AI secrets when the Master Password is set, changed, or removed.
     */
    public static synchronized void reencryptAllSecrets(byte[] previousMasterKeyOrNull, byte[] nextMasterKeyOrNull) throws Exception {
        StoreData data = load();
        for (DbProfile p : data.dbProfiles.values()) {
            if (p.encryptedPassword != null && !p.encryptedPassword.isEmpty()) {
                String plain = CryptoUtil.decryptWithMode(p.encryptedPassword, previousMasterKeyOrNull);
                p.encryptedPassword = CryptoUtil.encryptWithMode(plain, nextMasterKeyOrNull);
            }
        }
        for (SshProfile s : data.sshProfiles.values()) {
            if (s.encryptedPassword != null && !s.encryptedPassword.isEmpty()) {
                String plain = CryptoUtil.decryptWithMode(s.encryptedPassword, previousMasterKeyOrNull);
                s.encryptedPassword = CryptoUtil.encryptWithMode(plain, nextMasterKeyOrNull);
            }
        }
        for (Map.Entry<String, String> entry : data.aiSecrets.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                String plain = CryptoUtil.decryptWithMode(entry.getValue(), previousMasterKeyOrNull);
                entry.setValue(CryptoUtil.encryptWithMode(plain, nextMasterKeyOrNull));
            }
        }
        save(data);
    }

    public static synchronized Properties exportDbProfilesAsProperties() {
        StoreData data = load();
        Properties props = new Properties();
        for (Map.Entry<String, DbProfile> e : data.dbProfiles.entrySet()) {
            DbProfile p = e.getValue();
            props.setProperty(e.getKey(),
                    safe(p.host) + ";" + safe(p.port) + ";" + safe(p.username) + ";" + safe(p.encryptedPassword) + ";"
                            + safe(p.databaseName) + ";" + safe(p.type) + ";" + safe(p.sslMode));
        }
        return props;
    }

    public static synchronized Properties exportSshProfilesAsProperties() {
        StoreData data = load();
        Properties props = new Properties();
        for (Map.Entry<String, SshProfile> e : data.sshProfiles.entrySet()) {
            SshProfile s = e.getValue();
            props.setProperty(e.getKey(),
                    safe(s.host) + ";" + safe(s.port) + ";" + safe(s.username) + ";" + safe(s.encryptedPassword) + ";" + safe(s.keyPath));
        }
        return props;
    }

    public static String inferDatabaseType(String host, String port, String dbName) {
        String lowerDb = dbName != null ? dbName.toLowerCase() : "";
        if (lowerDb.endsWith(".accdb") || lowerDb.endsWith(".mdb")) {
            return DatabaseType.MS_ACCESS.name();
        }
        if (lowerDb.endsWith(".sqlite") || lowerDb.endsWith(".sqlite3") || lowerDb.endsWith(".db") || (host == null || host.trim().isEmpty())) {
            if ("3306".equals(port)) return DatabaseType.MYSQL.name();
            if ("5432".equals(port)) return DatabaseType.POSTGRESQL.name();
            if (!lowerDb.isEmpty() && (lowerDb.endsWith(".sqlite") || lowerDb.endsWith(".sqlite3") || lowerDb.endsWith(".db"))) {
                return DatabaseType.SQLITE.name();
            }
            if (host == null || host.trim().isEmpty()) return DatabaseType.SQLITE.name();
        }
        if ("3306".equals(port)) return DatabaseType.MYSQL.name();
        return DatabaseType.POSTGRESQL.name();
    }

    private static void archiveLegacyFile(Path legacyFile) {
        if (legacyFile == null || !Files.exists(legacyFile)) return;
        try {
            File target = new File(legacyFile.toFile().getAbsolutePath() + ".v1.migrated");
            Files.move(legacyFile, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
