package com.dbtool.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for In-App UpdateChecker SemVer evaluation and Delta Patch Engine.
 */
public class UpdateCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void testNewerPatchVersion() {
        assertTrue(UpdateChecker.isNewerVersion("1.6.1", "1.6.0"));
        assertTrue(UpdateChecker.isNewerVersion("v1.6.1", "1.6.0"));
        assertTrue(UpdateChecker.isNewerVersion("1.6.1", "v1.6.0"));
        assertTrue(UpdateChecker.isNewerVersion("v1.6.1", "v1.6.0"));
    }

    @Test
    void testNewerMinorAndMajorVersion() {
        assertTrue(UpdateChecker.isNewerVersion("1.7.0", "1.6.1"));
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.6.1"));
        assertTrue(UpdateChecker.isNewerVersion("1.10.0", "1.9.5"));
    }

    @Test
    void testSameVersion() {
        assertFalse(UpdateChecker.isNewerVersion("1.6.0", "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("v1.6.0", "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.6.1", "1.6.1"));
    }

    @Test
    void testOlderVersion() {
        assertFalse(UpdateChecker.isNewerVersion("1.5.1", "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("v1.4.0", "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.6.0", "1.6.1"));
    }

    @Test
    void testPreReleaseSuffixes() {
        assertTrue(UpdateChecker.isNewerVersion("1.7.0-beta1", "1.6.1"));
        assertFalse(UpdateChecker.isNewerVersion("1.6.1-rc1", "1.6.1"));
    }

    @Test
    void testNullOrEmpty() {
        assertFalse(UpdateChecker.isNewerVersion(null, "1.6.0"));
        assertFalse(UpdateChecker.isNewerVersion("", "1.6.0"));
        assertTrue(UpdateChecker.isNewerVersion("1.6.1", null));
        assertTrue(UpdateChecker.isNewerVersion("1.6.1", ""));
    }

    @Test
    void testEnsureLinuxDesktopIntegrationNonFatal() {
        assertDoesNotThrow(() -> UpdateChecker.ensureLinuxDesktopIntegration());
    }

    @Test
    void testApplyDeltaPatchToJarAndLaunch4jExe() throws Exception {
        // 1. Create a base Fat JAR containing a 3rd-party library entry + old com/dbtool/Main.class + old stale class
        File baseJar = tempDir.resolve("SchemaSync.jar").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(baseJar))) {
            writeEntry(zos, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMain-Class: com.dbtool.Main\r\n\r\n");
            writeEntry(zos, "org/postgresql/Driver.class", "MOCK_POSTGRES_DRIVER_PAYLOAD_1234567890_ABCDEFGHIJ");
            writeEntry(zos, "com/dbtool/Main.class", "OLD_MAIN_V1");
            writeEntry(zos, "com/dbtool/OldRemovedClass.class", "STALE_CLASS");
        }

        // 2. Create a lightweight Delta Patch JAR containing updated com/dbtool/Main.class + new feature class
        File patchJar = tempDir.resolve("SchemaSync-patch.jar").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(patchJar))) {
            writeEntry(zos, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n");
            writeEntry(zos, "com/dbtool/Main.class", "NEW_MAIN_V2_UPDATED");
            writeEntry(zos, "com/dbtool/NewFeature.class", "BRAND_NEW_FEATURE");
        }

        // 3. Apply delta patch to baseJar -> stagedJar
        File stagedJar = tempDir.resolve("staged_SchemaSync.jar").toFile();
        assertEquals(0L, UpdateChecker.findEmbeddedZipOffset(baseJar));
        assertTrue(UpdateChecker.applyDeltaPatch(baseJar, patchJar, stagedJar));
        assertTrue(stagedJar.exists());

        Map<String, String> entries = readZipEntries(stagedJar, 0);
        assertEquals("MOCK_POSTGRES_DRIVER_PAYLOAD_1234567890_ABCDEFGHIJ", entries.get("org/postgresql/Driver.class"));
        assertEquals("NEW_MAIN_V2_UPDATED", entries.get("com/dbtool/Main.class"));
        assertEquals("BRAND_NEW_FEATURE", entries.get("com/dbtool/NewFeature.class"));
        assertFalse(entries.containsKey("com/dbtool/OldRemovedClass.class"), "Stale removed com/dbtool class must be cleaned up");
        assertTrue(entries.get("META-INF/MANIFEST.MF").contains("Main-Class: com.dbtool.Main"));

        // 4. Create a simulated Launch4j .exe wrapper (PE stub prefix + baseJar bytes) and patch it in-place
        File baseExe = tempDir.resolve("SchemaSync.exe").toFile();
        byte[] fakePeHeader = new byte[512];
        fakePeHeader[0] = 'M';
        fakePeHeader[1] = 'Z';
        for (int i = 2; i < fakePeHeader.length; i++) fakePeHeader[i] = (byte) (i & 0x7F);

        try (FileOutputStream fos = new FileOutputStream(baseExe)) {
            fos.write(fakePeHeader);
            fos.write(java.nio.file.Files.readAllBytes(baseJar.toPath()));
        }

        assertEquals(512L, UpdateChecker.findEmbeddedZipOffset(baseExe));
        File stagedExe = tempDir.resolve("staged_SchemaSync.exe").toFile();
        assertTrue(UpdateChecker.applyDeltaPatch(baseExe, patchJar, stagedExe));
        assertEquals(512L, UpdateChecker.findEmbeddedZipOffset(stagedExe));

        Map<String, String> exeEntries = readZipEntries(stagedExe, 512);
        assertEquals("MOCK_POSTGRES_DRIVER_PAYLOAD_1234567890_ABCDEFGHIJ", exeEntries.get("org/postgresql/Driver.class"));
        assertEquals("NEW_MAIN_V2_UPDATED", exeEntries.get("com/dbtool/Main.class"));
        assertEquals("BRAND_NEW_FEATURE", exeEntries.get("com/dbtool/NewFeature.class"));
    }

    private static void writeEntry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static Map<String, String> readZipEntries(File file, long offset) throws Exception {
        Map<String, String> map = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            long skipped = 0;
            while (skipped < offset) {
                skipped += fis.skip(offset - skipped);
            }
            try (ZipInputStream zis = new ZipInputStream(fis)) {
                ZipEntry e;
                byte[] buf = new byte[4096];
                while ((e = zis.getNextEntry()) != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int r;
                    while ((r = zis.read(buf)) != -1) baos.write(buf, 0, r);
                    map.put(e.getName(), baos.toString(StandardCharsets.UTF_8));
                }
            }
        }
        return map;
    }
}
