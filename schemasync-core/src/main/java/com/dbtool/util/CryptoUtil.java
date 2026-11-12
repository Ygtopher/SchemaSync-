package com.dbtool.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

/**
 * Enterprise Cryptographic Utility supporting AES-256-GCM authenticated encryption,
 * PBKDF2-HMAC-SHA256 key derivation, OS Keyring / DPAPI local wrapping keys via {@link SecretStore},
 * Master Password protection, and explicit V1 -> V2 legacy migration support.
 */
public final class CryptoUtil {

    private static final byte XOR_KEY = 0x5A;
    private static final String AES_PREFIX = "AES256:";
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12; // Standard 96-bit IV for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int KEY_LENGTH_BITS = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String VERIFIER_TOKEN = "SCHEMASYNC_VAULT_OK_v1";

    // Active session master key (in-memory only, cleared on lock)
    private static byte[] activeMasterKey = null;
    private static boolean isUnlocked = false;
    private static volatile Path configDirOverride = null;

    private CryptoUtil() {}

    public static void setConfigDirOverrideForTesting(Path dir) {
        configDirOverride = dir;
        lockVault();
    }

    public static boolean isConfigDirOverriddenForTesting() {
        return configDirOverride != null;
    }

    public static Path getConfigDir() {
        Path override = configDirOverride;
        return override != null ? override : SecretStore.getDefaultConfigDir();
    }

    // ==================== Legacy Methods (Explicit Migration Use Only) ====================

    public static String obfuscate(String input) {
        if (input == null) return null;
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= XOR_KEY;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String deobfuscate(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] ^= XOR_KEY;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded;
        }
    }

    // ==================== Enterprise AES-256-GCM Vault Encryption ====================

    /**
     * Encrypts a plaintext password using AES-256-GCM.
     * Output format: AES256:&lt;salt_base64&gt;:&lt;iv_base64&gt;:&lt;ciphertext_base64&gt;
     */
    public static String encryptPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) return "";
        try {
            byte[] currentMaster = snapshotActiveMasterKey();
            try {
                if (!hasMasterPassword() || currentMaster != null) {
                    return encryptWithMode(plainPassword, currentMaster);
                }
                throw new SecurityException("Credential vault is locked.");
            } finally {
                if (currentMaster != null) Arrays.fill(currentMaster, (byte) 0);
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret using AES-256-GCM", e);
        }
    }

    public static String encryptWithMode(String plainPassword, byte[] explicitMasterKeyOrNull) throws Exception {
        if (plainPassword == null || plainPassword.isEmpty()) return "";
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        byte[] keyBytes = explicitMasterKeyOrNull != null ? explicitMasterKeyOrNull : getDeviceKeyForSalt(salt);
        byte[] ciphertext = encryptAesGcm(plainPassword.getBytes(StandardCharsets.UTF_8), keyBytes, iv);

        return AES_PREFIX +
                Base64.getEncoder().encodeToString(salt) + ":" +
                Base64.getEncoder().encodeToString(iv) + ":" +
                Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * Decrypts a modern AES-256-GCM password.
     * Fails closed on locked vaults, wrong master keys, tampered ciphertexts, or legacy unencrypted Base64/XOR blobs.
     */
    public static String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) return "";
        if (!encryptedPassword.startsWith(AES_PREFIX)) {
            throw new IllegalArgumentException("Legacy or unauthenticated credential format rejected; run V1->V2 migration.");
        }
        return decryptAesPayload(encryptedPassword, null, false);
    }

    public static String decryptWithMode(String encryptedPassword, byte[] explicitMasterKeyOrNull) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) return "";
        if (!encryptedPassword.startsWith(AES_PREFIX)) {
            throw new IllegalArgumentException("Expected AES256 payload.");
        }
        return decryptAesPayload(encryptedPassword, explicitMasterKeyOrNull, true);
    }

    private static String decryptAesPayload(String encryptedPassword, byte[] overrideMasterKey, boolean strictExplicitMode) {
        String payload = encryptedPassword.substring(AES_PREFIX.length());
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed AES256 credential payload.");
        }
        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);

            byte[] keyBytes;
            if (strictExplicitMode) {
                keyBytes = overrideMasterKey != null ? overrideMasterKey : getDeviceKeyForSalt(salt);
            } else {
                if (hasMasterPassword() && activeMasterKey == null) {
                    throw new SecurityException("Credential vault is locked.");
                }
                keyBytes = getActiveOrDeviceKey(salt);
            }
            byte[] plaintext = decryptAesGcm(ciphertext, keyBytes, iv);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Failed to authenticate/decrypt credential payload.", e);
        }
    }

    /**
     * Explicit V1 -> V2 migration decoder ONLY. Attempts AES256 (current key/keyring or legacy .device_seed),
     * then legacy Base64, then legacy XOR deobfuscation. Never called during normal runtime reads.
     */
    public static String decryptLegacyForMigration(String legacyValue, Path legacySeedDir) {
        if (legacyValue == null || legacyValue.isEmpty()) return "";
        if (legacyValue.startsWith(AES_PREFIX)) {
            try {
                return decryptAesPayload(legacyValue, null, false);
            } catch (Exception ignored) {
                byte[] legacySeed = legacySeedDir != null ? SecretStore.readLegacyDeviceSeedIfPresent(legacySeedDir) : null;
                if (legacySeed != null) {
                    try {
                        String payload = legacyValue.substring(AES_PREFIX.length());
                        String[] parts = payload.split(":");
                        byte[] salt = Base64.getDecoder().decode(parts[0]);
                        byte[] iv = Base64.getDecoder().decode(parts[1]);
                        byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
                        char[] seedChars = Base64.getEncoder().encodeToString(legacySeed).toCharArray();
                        byte[] keyBytes = deriveKey(seedChars, salt, 10000, KEY_LENGTH_BITS);
                        byte[] plaintext = decryptAesGcm(ciphertext, keyBytes, iv);
                        return new String(plaintext, StandardCharsets.UTF_8);
                    } catch (Exception ignoredLegacy) {
                    }
                }
            }
            return "";
        }

        // Legacy V1 SSH profiles used unencrypted Base64; legacy DB fallback used XOR or Base64
        try {
            byte[] decoded = Base64.getDecoder().decode(legacyValue);
            String asUtf8 = new String(decoded, StandardCharsets.UTF_8);
            // If XOR obfuscated printable ASCII became non-printable when raw-decoded, check deobfuscate too
            boolean printable = asUtf8.chars().allMatch(ch -> ch >= 32 && ch < 127);
            if (printable) return asUtf8;
            return deobfuscate(legacyValue);
        } catch (Exception ex) {
            return legacyValue;
        }
    }

    // ==================== Master Password Management ====================

    private static File getVaultConfigFile() {
        try {
            Path configDir = getConfigDir();
            Files.createDirectories(configDir);
            return configDir.resolve("vault_config.properties").toFile();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create vault config directory", e);
        }
    }

    public static synchronized boolean hasMasterPassword() {
        File file = getVaultConfigFile();
        if (!file.exists()) return false;
        try (FileInputStream in = new FileInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("vault_verifier") != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized boolean isVaultUnlocked() {
        if (!hasMasterPassword()) return true;
        return isUnlocked && activeMasterKey != null;
    }

    public static synchronized boolean unlockVault(char[] password) {
        if (!hasMasterPassword()) {
            isUnlocked = true;
            return true;
        }
        File file = getVaultConfigFile();
        try (FileInputStream in = new FileInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            byte[] salt = Base64.getDecoder().decode(p.getProperty("vault_salt"));
            byte[] iv = Base64.getDecoder().decode(p.getProperty("vault_iv"));
            byte[] verifierCipher = Base64.getDecoder().decode(p.getProperty("vault_verifier"));

            byte[] key = deriveKey(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            byte[] decrypted = decryptAesGcm(verifierCipher, key, iv);
            String verified = new String(decrypted, StandardCharsets.UTF_8);

            if (VERIFIER_TOKEN.equals(verified)) {
                activeMasterKey = key;
                isUnlocked = true;
                return true;
            }
        } catch (Exception e) {
            // Decryption / tag mismatch = wrong password
        }
        return false;
    }

    public static synchronized void setMasterPassword(char[] newPassword) throws Exception {
        if (newPassword == null || newPassword.length == 0) {
            removeMasterPassword();
            return;
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        byte[] key = deriveKey(newPassword, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        byte[] verifierCipher = encryptAesGcm(VERIFIER_TOKEN.getBytes(StandardCharsets.UTF_8), key, iv);

        File file = getVaultConfigFile();
        Properties p = new Properties();
        p.setProperty("vault_salt", Base64.getEncoder().encodeToString(salt));
        p.setProperty("vault_iv", Base64.getEncoder().encodeToString(iv));
        p.setProperty("vault_verifier", Base64.getEncoder().encodeToString(verifierCipher));

        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "SchemaSync Master Password Configuration");
        }

        activeMasterKey = key;
        isUnlocked = true;
    }

    public static synchronized void removeMasterPassword() {
        File file = getVaultConfigFile();
        if (file.exists()) {
            file.delete();
        }
        activeMasterKey = null;
        isUnlocked = true;
    }

    public static synchronized void lockVault() {
        if (activeMasterKey != null) {
            // Overwrite memory
            for (int i = 0; i < activeMasterKey.length; i++) {
                activeMasterKey[i] = 0;
            }
            activeMasterKey = null;
        }
        isUnlocked = false;
    }

    // ==================== Encrypted Vault Export & Import ====================

    /**
     * Exports connection profiles into an AES-256-GCM encrypted file (.schemasync.vault).
     */
    public static void exportVault(File targetFile, char[] exportPassword, Properties connections) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        byte[] key = deriveKey(exportPassword, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);

        // Serialize connections to simple JSON-like properties payload
        StringBuilder sb = new StringBuilder();
        for (String name : connections.stringPropertyNames()) {
            String val = connections.getProperty(name);
            sb.append(name.replace("\n", "\\n")).append("=").append(val.replace("\n", "\\n")).append("\n");
        }

        byte[] cipher = encryptAesGcm(sb.toString().getBytes(StandardCharsets.UTF_8), key, iv);

        Properties vaultProps = new Properties();
        vaultProps.setProperty("format", "SCHEMASYNC_VAULT_V1");
        vaultProps.setProperty("salt", Base64.getEncoder().encodeToString(salt));
        vaultProps.setProperty("iv", Base64.getEncoder().encodeToString(iv));
        vaultProps.setProperty("payload", Base64.getEncoder().encodeToString(cipher));

        try (FileOutputStream out = new FileOutputStream(targetFile)) {
            vaultProps.store(out, "SchemaSync Encrypted Connection Vault");
        }
    }

    /**
     * Imports connection profiles from an AES-256-GCM encrypted file (.schemasync.vault).
     */
    public static Properties importVault(File sourceFile, char[] importPassword) throws Exception {
        Properties vaultProps = new Properties();
        try (FileInputStream in = new FileInputStream(sourceFile)) {
            vaultProps.load(in);
        }

        byte[] salt = Base64.getDecoder().decode(vaultProps.getProperty("salt"));
        byte[] iv = Base64.getDecoder().decode(vaultProps.getProperty("iv"));
        byte[] cipher = Base64.getDecoder().decode(vaultProps.getProperty("payload"));

        byte[] key = deriveKey(importPassword, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        byte[] decrypted = decryptAesGcm(cipher, key, iv);

        String text = new String(decrypted, StandardCharsets.UTF_8);
        Properties imported = new Properties();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int idx = line.indexOf('=');
            if (idx > 0) {
                String k = line.substring(0, idx).replace("\\n", "\n");
                String v = line.substring(idx + 1).replace("\\n", "\n");
                imported.setProperty(k, v);
            }
        }
        return imported;
    }

    // ==================== Cryptographic Primitives ====================

    public static byte[] encryptAesGcm(byte[] plaintext, byte[] keyBytes, byte[] iv) throws Exception {
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(plaintext);
    }

    public static byte[] decryptAesGcm(byte[] ciphertext, byte[] keyBytes, byte[] iv) throws Exception {
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertext);
    }

    public static byte[] deriveKey(char[] password, byte[] salt, int iterations, int keyLengthBits) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
        return factory.generateSecret(spec).getEncoded();
    }

    public static synchronized byte[] snapshotActiveMasterKey() {
        return activeMasterKey != null ? Arrays.copyOf(activeMasterKey, activeMasterKey.length) : null;
    }

    public static byte[] getDeviceKeyForSalt(byte[] salt) throws Exception {
        byte[] wrappingKey = SecretStore.getOrCreateWrappingKey(getConfigDir());
        try {
            char[] seedChars = Base64.getEncoder().encodeToString(wrappingKey).toCharArray();
            return deriveKey(seedChars, salt, 10000, KEY_LENGTH_BITS);
        } finally {
            Arrays.fill(wrappingKey, (byte) 0);
        }
    }

    private static synchronized byte[] getActiveOrDeviceKey(byte[] salt) throws Exception {
        if (activeMasterKey != null) {
            return activeMasterKey;
        }
        return getDeviceKeyForSalt(salt);
    }
}
