package com.dbtool.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

public class FedoraRpmPackagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testBuildFedoraRpm() throws Exception {
        File baseDir = tempDir.resolve("base").toFile();
        File releaseDir = new File(baseDir, "release");
        releaseDir.mkdirs();

        // Create a dummy JAR with valid manifest
        File dummyJar = new File(releaseDir, "SchemaSync.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.dbtool.Main");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(dummyJar), manifest)) {
            // Empty dummy jar
        }

        File outputDir = tempDir.resolve("output").toFile();
        File rpm = FedoraRpmPackager.buildRpm(baseDir, outputDir);

        assertNotNull(rpm);
        assertTrue(rpm.exists());
        assertTrue(rpm.length() > 0);
        assertTrue(rpm.getName().startsWith("schemasync-"));
        assertTrue(rpm.getName().endsWith(".noarch.rpm"));

        // Verify standard RPM Lead Magic bytes: 0xed 0xab 0xee 0xdb
        try (FileInputStream fis = new FileInputStream(rpm)) {
            byte[] magic = new byte[4];
            int read = fis.read(magic);
            assertEquals(4, read);
            assertEquals((byte) 0xed, magic[0]);
            assertEquals((byte) 0xab, magic[1]);
            assertEquals((byte) 0xee, magic[2]);
            assertEquals((byte) 0xdb, magic[3]);
        }
    }
}
