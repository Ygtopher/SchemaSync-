package com.dbtool.config;

import com.dbtool.core.config.ProfileStoreV2;
import com.dbtool.core.model.DatabaseType;
import com.dbtool.util.CryptoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigMigrationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        CryptoUtil.setConfigDirOverrideForTesting(null);
    }

    @Test
    void testLegacyV1DbAndSshMigrationUpgradesSecretsToAes256AndArchivesV1() throws Exception {
        CryptoUtil.setConfigDirOverrideForTesting(tempDir);

        // Create legacy .device_seed file & V1 properties files
        Path legacySeed = tempDir.resolve(".device_seed");
        Files.write(legacySeed, new byte[32]);

        Path v1Db = tempDir.resolve("saved_connections.properties");
        Properties dbProps = new Properties();
        String legacyXorPass = CryptoUtil.obfuscate("DbLegacyPass#1");
        dbProps.setProperty("pg-prod", "10.0.0.5;5432;pgadmin;" + legacyXorPass + ";core_db");
        dbProps.setProperty("mysql-dev", "127.0.0.1;3306;root;" + Base64.getEncoder().encodeToString("MySqlPass!".getBytes(StandardCharsets.UTF_8)) + ";shop");
        try (FileOutputStream fos = new FileOutputStream(v1Db.toFile())) {
            dbProps.store(fos, "Legacy V1 DB Profiles");
        }

        Path v1Ssh = tempDir.resolve("saved_ssh_connections.properties");
        Properties sshProps = new Properties();
        String rawBase64Ssh = Base64.getEncoder().encodeToString("TopSecretSsh!99".getBytes(StandardCharsets.UTF_8));
        sshProps.setProperty("ops@10.0.0.2:22", "10.0.0.2;22;ops;" + rawBase64Ssh);
        try (FileOutputStream fos = new FileOutputStream(v1Ssh.toFile())) {
            sshProps.store(fos, "Legacy V1 SSH Profiles");
        }

        int migrated = ProfileStoreV2.migrateLegacyIfNeeded(v1Db, v1Ssh, tempDir);
        assertEquals(3, migrated);

        // V1 files must be archived and legacy .device_seed shredded/deleted
        assertFalse(Files.exists(v1Db));
        assertFalse(Files.exists(v1Ssh));
        assertTrue(Files.exists(tempDir.resolve("saved_connections.properties.v1.migrated")));
        assertTrue(Files.exists(tempDir.resolve("saved_ssh_connections.properties.v1.migrated")));
        assertFalse(Files.exists(legacySeed));

        ProfileStoreV2.StoreData v2 = ProfileStoreV2.load();
        assertEquals(2, v2.version);
        assertEquals(DatabaseType.POSTGRESQL.name(), v2.dbProfiles.get("pg-prod").type);
        assertEquals(DatabaseType.MYSQL.name(), v2.dbProfiles.get("mysql-dev").type);
        assertTrue(v2.dbProfiles.get("pg-prod").encryptedPassword.startsWith("AES256:"));
        assertTrue(v2.dbProfiles.get("mysql-dev").encryptedPassword.startsWith("AES256:"));
        assertTrue(v2.sshProfiles.get("ops@10.0.0.2:22").encryptedPassword.startsWith("AES256:"));

        assertEquals("DbLegacyPass#1", CryptoUtil.decryptPassword(v2.dbProfiles.get("pg-prod").encryptedPassword));
        assertEquals("MySqlPass!", CryptoUtil.decryptPassword(v2.dbProfiles.get("mysql-dev").encryptedPassword));
        assertEquals("TopSecretSsh!99", CryptoUtil.decryptPassword(v2.sshProfiles.get("ops@10.0.0.2:22").encryptedPassword));
    }
}
