package com.dbtool.util;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Base64;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

public class CryptoUtilTest {

    @Test
    void testObfuscationRoundTrip() {
        String original = "p@ssw0rd!123";
        String encoded = CryptoUtil.obfuscate(original);
        assertNotEquals(original, encoded);
        String decoded = CryptoUtil.deobfuscate(encoded);
        assertEquals(original, decoded);
    }

    @Test
    void testAesGcmEncryptionRoundTrip() {
        String secret = "SuperSecretPostgresPassword#2026!";
        String encrypted = CryptoUtil.encryptPassword(secret);
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("AES256:"));

        String decrypted = CryptoUtil.decryptPassword(encrypted);
        assertEquals(secret, decrypted);
    }

    @Test
    void testLegacyBase64DecryptionFallback() {
        String secret = "legacyPassword123";
        String legacyBase64 = Base64.getEncoder().encodeToString(secret.getBytes());
        String decrypted = CryptoUtil.decryptPassword(legacyBase64);
        assertEquals(secret, decrypted);
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
