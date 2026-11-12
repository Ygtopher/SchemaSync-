package com.dbtool.util;

import com.dbtool.core.config.ProfileStoreV2;
import com.dbtool.core.model.SslMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

public class CryptoUtilTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupOverride() {
        CryptoUtil.setConfigDirOverrideForTesting(null);
    }

    @Test
    void testObfuscationRoundTripForLegacyMigrationOnly() {
        String original = "p@ssw0rd!123";
        String encoded = CryptoUtil.obfuscate(original);
        assertNotEquals(original, encoded);
        String decoded = CryptoUtil.deobfuscate(encoded);
        assertEquals(original, decoded);
    }

    @Test
    void testAesGcmEncryptionViaOsSecretStoreWithoutPlaintextDeviceSeed() {
        CryptoUtil.setConfigDirOverrideForTesting(tempDir);
        String secret = "SuperSecretPostgresPassword#2026!";
        String encrypted = CryptoUtil.encryptPassword(secret);
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("AES256:"));
        assertFalse(Files.exists(tempDir.resolve(".device_seed")), "Plaintext .device_seed must never be created");

        String decrypted = CryptoUtil.decryptPassword(encrypted);
        assertEquals(secret, decrypted);
    }

    @Test
    void testLegacyBase64RejectedAtRuntimeFailClosed() {
        CryptoUtil.setConfigDirOverrideForTesting(tempDir);
        String secret = "legacyPassword123";
        String legacyBase64 = Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.decryptPassword(legacyBase64));

        // Explicit V1 -> V2 migration helper still decodes legacy Base64 safely during migration
        assertEquals(secret, CryptoUtil.decryptLegacyForMigration(legacyBase64, tempDir));
    }

    @Test
    void testMasterPasswordLockAndFullProfileReencryptionLifecycle() throws Exception {
        CryptoUtil.setConfigDirOverrideForTesting(tempDir);

        ProfileStoreV2.saveDbProfile("prod", "POSTGRESQL", "db.local", "5432", "admin", "DbSecret!1", "ledger", SslMode.VERIFY_FULL);
        ProfileStoreV2.saveSshProfile("bastion", "ssh.local", "22", "ops", "SshSecret!2", "");

        // 1. Set Master Password & re-encrypt V2 secrets
        byte[] prevKey = CryptoUtil.snapshotActiveMasterKey();
        CryptoUtil.setMasterPassword("MasterPass#2026".toCharArray());
        byte[] nextKey = CryptoUtil.snapshotActiveMasterKey();
        ProfileStoreV2.reencryptAllSecrets(prevKey, nextKey);

        ProfileStoreV2.StoreData afterSet = ProfileStoreV2.load();
        assertEquals("DbSecret!1", CryptoUtil.decryptPassword(afterSet.dbProfiles.get("prod").encryptedPassword));
        assertEquals("SshSecret!2", CryptoUtil.decryptPassword(afterSet.sshProfiles.get("bastion").encryptedPassword));

        // 2. Locking vault must block decryption
        CryptoUtil.lockVault();
        assertThrows(SecurityException.class, () -> CryptoUtil.decryptPassword(afterSet.dbProfiles.get("prod").encryptedPassword));

        // 3. Unlocking with wrong password fails, right password succeeds
        assertFalse(CryptoUtil.unlockVault("WrongMaster".toCharArray()));
        assertTrue(CryptoUtil.unlockVault("MasterPass#2026".toCharArray()));
        assertEquals("DbSecret!1", CryptoUtil.decryptPassword(afterSet.dbProfiles.get("prod").encryptedPassword));

        // 4. Remove Master Password & re-encrypt back under OS Keyring
        byte[] beforeRemoveKey = CryptoUtil.snapshotActiveMasterKey();
        CryptoUtil.removeMasterPassword();
        ProfileStoreV2.reencryptAllSecrets(beforeRemoveKey, null);

        ProfileStoreV2.StoreData afterRemove = ProfileStoreV2.load();
        assertEquals("DbSecret!1", CryptoUtil.decryptPassword(afterRemove.dbProfiles.get("prod").encryptedPassword));
        assertEquals("SshSecret!2", CryptoUtil.decryptPassword(afterRemove.sshProfiles.get("bastion").encryptedPassword));
    }

    @Test
    void testVaultExportAndImport() throws Exception {
        File tempVault = File.createTempFile("test_vault", ".schemasync.vault");
        tempVault.deleteOnExit();

        Properties props = new Properties();
        props.setProperty("prod-db", "postgres@prod.internal:5432/main");
        props.setProperty("staging-db", "root@staging.internal:3306/test");

        char[] pass = "VaultMasterPass123!".toCharArray();
        CryptoUtil.exportVault(tempVault, pass, props);
        assertTrue(tempVault.length() > 0);

        Properties imported = CryptoUtil.importVault(tempVault, pass);
        assertEquals("postgres@prod.internal:5432/main", imported.getProperty("prod-db"));
        assertEquals("root@staging.internal:3306/test", imported.getProperty("staging-db"));

        // Wrong password should fail
        assertThrows(Exception.class, () -> {
            CryptoUtil.importVault(tempVault, "WrongPassword!".toCharArray());
        });
    }
}
