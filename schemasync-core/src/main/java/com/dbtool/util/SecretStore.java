package com.dbtool.util;

import com.sun.jna.platform.win32.Crypt32Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Platform-native OS Keyring / DPAPI wrapper for SchemaSync's local vault wrapping key.
 *
 * - Windows: Encrypts a random 256-bit wrapping key using Windows DPAPI (Crypt32Util.cryptProtectData)
 *   bound to the logged-in Windows user account.
 * - macOS: Stores the 256-bit wrapping key inside the macOS login Keychain via `security`.
 * - Linux desktop: Stores the wrapping key in Secret Service (`secret-tool`) when available over D-Bus,
 *   falling back to an owner-only (0600) restricted keyfile in headless environments.
 */
public final class SecretStore {

    private static final String KEYCHAIN_SERVICE = "com.dbtool.schemasync.vault.v2";
    private static final String KEYCHAIN_ACCOUNT = "SchemaSyncLocalKey";
    private static final String DPAPI_FILENAME = "keyring.dpapi";
    private static final String FALLBACK_FILENAME = ".keyring_local";
    private static final String LEGACY_SEED_FILENAME = ".device_seed";
    private static final int KEY_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecretStore() {}

    public static Path getDefaultConfigDir() {
        return new File(System.getProperty("user.home"), ".schemasync").toPath();
    }

    /**
     * Loads or creates the 256-bit local wrapping key protected by the OS keyring / DPAPI.
     */
    public static byte[] getOrCreateWrappingKey(Path configDir) throws Exception {
        Files.createDirectories(configDir);
        boolean isDefaultHome = isSameDirectory(configDir, getDefaultConfigDir());
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            return loadOrCreateWindowsDpapiKey(configDir);
        }

        if (isDefaultHome) {
            if (os.contains("mac")) {
                byte[] keychainKey = tryMacOsKeychain();
                if (keychainKey != null) return keychainKey;
            } else {
                byte[] secretServiceKey = tryLinuxSecretService();
                if (secretServiceKey != null) return secretServiceKey;
            }
        }

        return loadOrCreateRestrictedKeyFile(configDir);
    }

    /**
     * Reads legacy v1 `.device_seed` bytes if present for one-time V1 -> V2 migration.
     */
    public static byte[] readLegacyDeviceSeedIfPresent(Path configDir) {
        try {
            Path legacyFile = configDir.resolve(LEGACY_SEED_FILENAME);
            if (Files.exists(legacyFile) && Files.size(legacyFile) == KEY_BYTES) {
                return Files.readAllBytes(legacyFile);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Securely overwrites and deletes the legacy plaintext `.device_seed` after V1 -> V2 migration.
     */
    public static void deleteLegacyDeviceSeed(Path configDir) {
        try {
            Path legacyFile = configDir.resolve(LEGACY_SEED_FILENAME);
            if (Files.exists(legacyFile)) {
                byte[] zeros = new byte[(int) Math.min(Files.size(legacyFile), 1024)];
                Files.write(legacyFile, zeros);
                Files.deleteIfExists(legacyFile);
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] loadOrCreateWindowsDpapiKey(Path configDir) throws Exception {
        Path dpapiFile = configDir.resolve(DPAPI_FILENAME);
        if (Files.exists(dpapiFile)) {
            byte[] protectedBytes = Files.readAllBytes(dpapiFile);
            if (protectedBytes.length > 0) {
                byte[] unwrapped = Crypt32Util.cryptUnprotectData(protectedBytes);
                if (unwrapped.length == KEY_BYTES) {
                    return unwrapped;
                }
            }
        }

        byte[] raw = new byte[KEY_BYTES];
        SECURE_RANDOM.nextBytes(raw);
        byte[] protectedBytes = Crypt32Util.cryptProtectData(raw);
        Files.write(dpapiFile, protectedBytes);
        return raw;
    }

    private static byte[] tryMacOsKeychain() {
        try {
            Process find = new ProcessBuilder(
                    "security", "find-generic-password",
                    "-a", KEYCHAIN_ACCOUNT, "-s", KEYCHAIN_SERVICE, "-w"
            ).start();
            if (find.waitFor(3, TimeUnit.SECONDS) && find.exitValue() == 0) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(find.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = br.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        byte[] decoded = Base64.getDecoder().decode(line.trim());
                        if (decoded.length == KEY_BYTES) return decoded;
                    }
                }
            }

            byte[] raw = new byte[KEY_BYTES];
            SECURE_RANDOM.nextBytes(raw);
            String encoded = Base64.getEncoder().encodeToString(raw);
            Process add = new ProcessBuilder(
                    "security", "add-generic-password", "-U",
                    "-a", KEYCHAIN_ACCOUNT, "-s", KEYCHAIN_SERVICE, "-w", encoded
            ).start();
            if (add.waitFor(3, TimeUnit.SECONDS) && add.exitValue() == 0) {
                return raw;
            }
            Arrays.fill(raw, (byte) 0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] tryLinuxSecretService() {
        String dbus = System.getenv("DBUS_SESSION_BUS_ADDRESS");
        if (dbus == null || dbus.trim().isEmpty()) {
            return null;
        }
        try {
            Process lookup = new ProcessBuilder(
                    "secret-tool", "lookup", "application", "schemasync", "key", KEYCHAIN_SERVICE
            ).start();
            if (lookup.waitFor(3, TimeUnit.SECONDS) && lookup.exitValue() == 0) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(lookup.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = br.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        byte[] decoded = Base64.getDecoder().decode(line.trim());
                        if (decoded.length == KEY_BYTES) return decoded;
                    }
                }
            }

            byte[] raw = new byte[KEY_BYTES];
            SECURE_RANDOM.nextBytes(raw);
            String encoded = Base64.getEncoder().encodeToString(raw);
            Process store = new ProcessBuilder(
                    "secret-tool", "store",
                    "--label=SchemaSync Vault Key",
                    "application", "schemasync",
                    "key", KEYCHAIN_SERVICE
            ).start();
            try (OutputStream os = store.getOutputStream()) {
                os.write(encoded.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            if (store.waitFor(3, TimeUnit.SECONDS) && store.exitValue() == 0) {
                return raw;
            }
            Arrays.fill(raw, (byte) 0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] loadOrCreateRestrictedKeyFile(Path configDir) throws Exception {
        Path keyFile = configDir.resolve(FALLBACK_FILENAME);
        if (Files.exists(keyFile) && Files.size(keyFile) == KEY_BYTES) {
            return Files.readAllBytes(keyFile);
        }
        byte[] raw = new byte[KEY_BYTES];
        SECURE_RANDOM.nextBytes(raw);
        Files.write(keyFile, raw);
        applyOwnerOnlyPermissions(keyFile);
        return raw;
    }

    private static void applyOwnerOnlyPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | SecurityException ignored) {
            File f = path.toFile();
            f.setReadable(false, false);
            f.setReadable(true, true);
            f.setWritable(false, false);
            f.setWritable(true, true);
        } catch (Exception ignored) {
        }
    }

    private static boolean isSameDirectory(Path a, Path b) {
        try {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }
}
