package com.dbtool.packaging;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

public class UbuntuDebPackagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testBuildUbuntuDeb() throws Exception {
        File baseDir = tempDir.resolve("base").toFile();
        File releaseDir = new File(baseDir, "release");
        releaseDir.mkdirs();

        // Create dummy JAR with manifest
        File dummyJar = new File(releaseDir, "SchemaSync.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.dbtool.Main");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(dummyJar), manifest)) {
            // empty jar
        }

        File outputDir = tempDir.resolve("output").toFile();
        File deb = UbuntuDebPackager.buildDeb(baseDir, outputDir);

        assertNotNull(deb);
        assertTrue(deb.exists());
        assertTrue(deb.length() > 0);
        assertEquals("schemasync_1.7.1_all.deb", deb.getName());

        // Verify standard 'ar' magic header: "!<arch>\n" (0x21 0x3c 0x61 0x72 0x63 0x68 0x3e 0x0a)
        try (FileInputStream fis = new FileInputStream(deb)) {
            byte[] magic = new byte[8];
            int read = fis.read(magic);
            assertEquals(8, read);
            assertEquals("!<arch>\n", new String(magic, StandardCharsets.US_ASCII));
        }

        // Read ar archive entries
        List<String> arEntryNames = new ArrayList<>();
        byte[] controlTarGzBytes = null;
        byte[] dataTarGzBytes = null;

        try (FileInputStream fis = new FileInputStream(deb);
             ArArchiveInputStream arIn = new ArArchiveInputStream(fis)) {
            ArArchiveEntry entry;
            while ((entry = arIn.getNextArEntry()) != null) {
                arEntryNames.add(entry.getName());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = arIn.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }

                if ("debian-binary".equals(entry.getName())) {
                    assertEquals("2.0\n", new String(baos.toByteArray(), StandardCharsets.US_ASCII));
                } else if ("control.tar.gz".equals(entry.getName())) {
                    controlTarGzBytes = baos.toByteArray();
                } else if ("data.tar.gz".equals(entry.getName())) {
                    dataTarGzBytes = baos.toByteArray();
                }
            }
        }

        assertEquals(List.of("debian-binary", "control.tar.gz", "data.tar.gz"), arEntryNames);
        assertNotNull(controlTarGzBytes);
        assertNotNull(dataTarGzBytes);

        // Verify control.tar.gz contents
        List<String> controlEntries = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(controlTarGzBytes);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(bais);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzis)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                controlEntries.add(entry.getName());
                if (entry.getName().endsWith("control")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int n;
                    while ((n = tarIn.read(buf)) != -1) baos.write(buf, 0, n);
                    String controlStr = baos.toString(StandardCharsets.UTF_8);
                    assertTrue(controlStr.contains("Package: schemasync"));
                    assertTrue(controlStr.contains("Version: 1.7.1"));
                    assertTrue(controlStr.contains("Architecture: all"));
                    assertTrue(controlStr.contains("Depends: default-jre"));
                }
            }
        }
        assertTrue(controlEntries.contains("./control"));
        assertTrue(controlEntries.contains("./postinst"));
        assertTrue(controlEntries.contains("./postrm"));
        assertTrue(controlEntries.contains("./md5sums"));

        // Verify data.tar.gz contents
        List<String> dataEntries = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dataTarGzBytes);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(bais);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzis)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                dataEntries.add(entry.getName());
            }
        }

        assertTrue(dataEntries.contains("./usr/bin/schemasync"));
        assertTrue(dataEntries.contains("./usr/share/schemasync/SchemaSync.jar"));
        assertTrue(dataEntries.contains("./usr/share/applications/schemasync.desktop"));
    }

    @Test
    void testVerifyBuiltProductionDeb() throws Exception {
        File productionDeb = new File("release/schemasync_1.7.1_all.deb");
        if (!productionDeb.exists()) {
            productionDeb = new File("release/schemasync_1.6.4_all.deb");
        }
        if (!productionDeb.exists()) {
            return; // Skip if release hasn't been built yet in this environment
        }

        assertTrue(productionDeb.length() > 50_000_000, "Production DEB should be > 50MB");

        List<String> entries = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(productionDeb);
             ArArchiveInputStream arIn = new ArArchiveInputStream(fis)) {
            ArArchiveEntry entry;
            while ((entry = arIn.getNextArEntry()) != null) {
                entries.add(entry.getName());
                if ("data.tar.gz".equals(entry.getName())) {
                    InputStream nonClosing = new InputStream() {
                        @Override public int read() throws java.io.IOException { return arIn.read(); }
                        @Override public int read(byte[] b, int off, int len) throws java.io.IOException { return arIn.read(b, off, len); }
                        @Override public void close() {}
                    };
                    try (GzipCompressorInputStream gzis = new GzipCompressorInputStream(nonClosing);
                         TarArchiveInputStream tarIn = new TarArchiveInputStream(gzis)) {
                        TarArchiveEntry tarEntry;
                        List<String> dataList = new ArrayList<>();
                        while ((tarEntry = tarIn.getNextTarEntry()) != null) {
                            dataList.add(tarEntry.getName());
                        }
                        assertTrue(dataList.contains("./usr/bin/schemasync"));
                        assertTrue(dataList.contains("./usr/share/schemasync/SchemaSync.jar"));
                        assertTrue(dataList.contains("./usr/share/applications/schemasync.desktop"));
                        assertTrue(dataList.contains("./usr/share/pixmaps/schemasync.png"));
                        assertTrue(dataList.contains("./usr/share/icons/hicolor/512x512/apps/schemasync.png"));
                    }
                }
            }
        }
        assertEquals(List.of("debian-binary", "control.tar.gz", "data.tar.gz"), entries);
    }
}
