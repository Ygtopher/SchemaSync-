package com.dbtool.packaging;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;

/**
 * Builds standard, production-grade Debian packages (.deb) for Ubuntu Desktop (20.04, 22.04, 24.04 LTS+).
 * Pure Java implementation using Apache Commons Compress - no native Linux CLI tools required.
 *
 * Configures:
 * - /usr/bin/schemasync (executable launcher script)
 * - /usr/share/schemasync/SchemaSync.jar (executable application fat JAR)
 * - /usr/share/applications/schemasync.desktop (GNOME/XDG desktop menu integration)
 * - /usr/share/pixmaps/schemasync.png (Universal pixmap icon)
 * - /usr/share/icons/hicolor/{16x16,32x32,64x64,512x512}/apps/schemasync.png (High-DPI icon theme assets)
 *
 * Creator: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class UbuntuDebPackager {

    public static final String PACKAGE_NAME = "schemasync";
    public static final String VERSION = "1.6.6";
    public static final String ARCHITECTURE = "all";

    public static class PackageEntry {
        final String path; // e.g. "usr/bin/schemasync"
        final File file;   // non-null if streaming from file
        final byte[] memoryContent; // non-null if small in-memory payload
        final int mode;    // e.g. 0755 or 0644
        final long size;

        public PackageEntry(String path, byte[] content, int mode) {
            this.path = path.startsWith("/") ? path.substring(1) : path;
            this.memoryContent = content;
            this.file = null;
            this.mode = mode;
            this.size = content.length;
        }

        public PackageEntry(String path, File file, int mode) {
            this.path = path.startsWith("/") ? path.substring(1) : path;
            this.file = file;
            this.memoryContent = null;
            this.mode = mode;
            this.size = file.length();
        }

        public InputStream openStream() throws Exception {
            if (file != null) {
                return new BufferedInputStream(new FileInputStream(file), 65536);
            }
            return new java.io.ByteArrayInputStream(memoryContent);
        }
    }

    public static File buildDeb(File baseDir, File outputDir) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File jarFile = new File(baseDir, "release/SchemaSync.jar");
        if (!jarFile.exists()) {
            jarFile = new File(baseDir, "target/SchemaSync.jar");
        }
        if (!jarFile.exists()) {
            throw new IllegalStateException("SchemaSync.jar not found at " + jarFile.getAbsolutePath() + "! Please run 'mvn package' first.");
        }

        File stagingDir = new File(outputDir, "deb_staging_" + System.currentTimeMillis());
        stagingDir.mkdirs();

        try {
            // 1. Launcher Shell Script (/usr/bin/schemasync)
            String launcherScript = "#!/bin/bash\n"
                    + "# SchemaSync Ubuntu Desktop Launcher\n"
                    + "# Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.\n\n"
                    + "if [ -n \"$JAVA_HOME\" ] && [ -x \"$JAVA_HOME/bin/java\" ]; then\n"
                    + "    JAVA_CMD=\"$JAVA_HOME/bin/java\"\n"
                    + "elif command -v java >/dev/null 2>&1; then\n"
                    + "    JAVA_CMD=\"java\"\n"
                    + "else\n"
                    + "    echo \"Error: Java 11 or newer is required to run SchemaSync.\" >&2\n"
                    + "    echo \"Please install OpenJDK on Ubuntu: sudo apt install default-jre\" >&2\n"
                    + "    exit 1\n"
                    + "fi\n\n"
                    + "# FlatLaf / Linux Wayland, TimeZone, and HiDPI font rendering settings\n"
                    + "export _JAVA_AWT_WM_NONREPARENTING=1\n"
                    + "JAVA_OPTS=\"--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED -Duser.timezone=UTC -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -Dsun.java2d.uiScale.enabled=true -Djdk.gtk.version=3\"\n\n"
                    + "JAR_PATH=\"/usr/share/schemasync/SchemaSync.jar\"\n\n"
                    + "if [ ! -f \"$JAR_PATH\" ]; then\n"
                    + "    echo \"Error: SchemaSync JAR not found at $JAR_PATH\" >&2\n"
                    + "    exit 1\n"
                    + "fi\n\n"
                    + "exec \"$JAVA_CMD\" $JAVA_OPTS -jar \"$JAR_PATH\" \"$@\"\n";

            // 2. Desktop Launcher (/usr/share/applications/schemasync.desktop)
            String desktopContent = "[Desktop Entry]\n"
                    + "Version=1.0\n"
                    + "Type=Application\n"
                    + "Name=SchemaSync\n"
                    + "GenericName=Database Management Tool\n"
                    + "Comment=Modern, lightweight database management and synchronization tool\n"
                    + "Exec=/usr/bin/schemasync %F\n"
                    + "Icon=schemasync\n"
                    + "Terminal=false\n"
                    + "StartupNotify=true\n"
                    + "StartupWMClass=schemasync\n"
                    + "Categories=Development;Database;IDE;\n"
                    + "MimeType=application/sql;application/x-sqlite3;\n"
                    + "Keywords=database;sql;postgres;mysql;sqlite;schema;diff;sync;\n";

            // Icons
            File icon512 = new File(baseDir, "src/main/resources/icons/app-icon.png");
            File icon64 = new File(baseDir, "src/main/resources/icons/app-icon-64.png");
            File icon32 = new File(baseDir, "src/main/resources/icons/app-icon-32.png");
            File icon16 = new File(baseDir, "src/main/resources/icons/app-icon-16.png");

            List<PackageEntry> dataFiles = new ArrayList<>();
            dataFiles.add(new PackageEntry("usr/bin/schemasync", launcherScript.getBytes(StandardCharsets.UTF_8), 0755));
            dataFiles.add(new PackageEntry("usr/share/schemasync/SchemaSync.jar", jarFile, 0644));
            dataFiles.add(new PackageEntry("usr/share/applications/schemasync.desktop", desktopContent.getBytes(StandardCharsets.UTF_8), 0644));

            if (icon512.exists()) {
                dataFiles.add(new PackageEntry("usr/share/icons/hicolor/512x512/apps/schemasync.png", icon512, 0644));
                dataFiles.add(new PackageEntry("usr/share/pixmaps/schemasync.png", icon512, 0644));
            }
            if (icon64.exists()) {
                dataFiles.add(new PackageEntry("usr/share/icons/hicolor/64x64/apps/schemasync.png", icon64, 0644));
            }
            if (icon32.exists()) {
                dataFiles.add(new PackageEntry("usr/share/icons/hicolor/32x32/apps/schemasync.png", icon32, 0644));
            }
            if (icon16.exists()) {
                dataFiles.add(new PackageEntry("usr/share/icons/hicolor/16x16/apps/schemasync.png", icon16, 0644));
            }

            // Stream data.tar.gz directly to staging file
            File dataTarGzFile = new File(stagingDir, "data.tar.gz");
            StringBuilder md5sumsBuilder = new StringBuilder();
            long totalInstalledBytes = writeDataTarGz(dataFiles, dataTarGzFile, md5sumsBuilder);
            long installedSizeKb = (totalInstalledBytes + 1023) / 1024;

            // 3. Control file
            String controlContent = "Package: " + PACKAGE_NAME + "\n"
                    + "Version: " + VERSION + "\n"
                    + "Architecture: " + ARCHITECTURE + "\n"
                    + "Maintainer: NSENGIYUMVA Christophe <https://github.com/Ygtopher/SchemaSync->\n"
                    + "Installed-Size: " + installedSizeKb + "\n"
                    + "Depends: default-jre (>= 2:1.11) | openjdk-11-jre | openjdk-17-jre | openjdk-21-jre | openjdk-24-jre\n"
                    + "Section: devel\n"
                    + "Priority: optional\n"
                    + "Homepage: https://github.com/Ygtopher/SchemaSync-\n"
                    + "Description: Modern, lightweight database management and synchronization tool\n"
                    + " SchemaSync is a high-performance, lightweight database synchronization and\n"
                    + " management tool for PostgreSQL, MySQL, SQLite, MS Access, and H2.\n"
                    + " Features include 1-Click Schema Migrations, Live Health Telemetry, Data Masking Studio,\n"
                    + " AES-256 Vault, Query Profiler, Visual Join Builder, and Dark Mode UI.\n";

            // 4. Post-install script
            String postinstScript = "#!/bin/sh\n"
                    + "set -e\n\n"
                    + "if [ -x /usr/bin/update-desktop-database ]; then\n"
                    + "    /usr/bin/update-desktop-database /usr/share/applications || true\n"
                    + "fi\n\n"
                    + "if [ -x /usr/bin/gtk-update-icon-cache ]; then\n"
                    + "    /usr/bin/gtk-update-icon-cache -f -q -t /usr/share/icons/hicolor || true\n"
                    + "fi\n\n"
                    + "exit 0\n";

            // 5. Post-remove script
            String postrmScript = "#!/bin/sh\n"
                    + "set -e\n\n"
                    + "if [ -x /usr/bin/update-desktop-database ]; then\n"
                    + "    /usr/bin/update-desktop-database /usr/share/applications || true\n"
                    + "fi\n\n"
                    + "if [ -x /usr/bin/gtk-update-icon-cache ]; then\n"
                    + "    /usr/bin/gtk-update-icon-cache -f -q -t /usr/share/icons/hicolor || true\n"
                    + "fi\n\n"
                    + "exit 0\n";

            List<PackageEntry> controlFiles = new ArrayList<>();
            controlFiles.add(new PackageEntry("control", controlContent.getBytes(StandardCharsets.UTF_8), 0644));
            controlFiles.add(new PackageEntry("postinst", postinstScript.getBytes(StandardCharsets.UTF_8), 0755));
            controlFiles.add(new PackageEntry("postrm", postrmScript.getBytes(StandardCharsets.UTF_8), 0755));
            controlFiles.add(new PackageEntry("md5sums", md5sumsBuilder.toString().getBytes(StandardCharsets.UTF_8), 0644));

            File controlTarGzFile = new File(stagingDir, "control.tar.gz");
            writeControlTarGz(controlFiles, controlTarGzFile);

            // 6. Assemble ar archive (.deb)
            byte[] debianBinary = "2.0\n".getBytes(StandardCharsets.US_ASCII);
            String debFileName = PACKAGE_NAME + "_" + VERSION + "_" + ARCHITECTURE + ".deb";
            File debFile = new File(outputDir, debFileName);

            long nowSeconds = System.currentTimeMillis() / 1000;

            try (FileOutputStream fos = new FileOutputStream(debFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, 65536);
                 ArArchiveOutputStream ar = new ArArchiveOutputStream(bos)) {

                ar.setLongFileMode(ArArchiveOutputStream.LONGFILE_BSD);

                // Entry 1: debian-binary
                ArArchiveEntry entry1 = new ArArchiveEntry("debian-binary", debianBinary.length, 0, 0, 0100644, nowSeconds);
                ar.putArchiveEntry(entry1);
                ar.write(debianBinary);
                ar.closeArchiveEntry();

                // Entry 2: control.tar.gz
                ArArchiveEntry entry2 = new ArArchiveEntry("control.tar.gz", controlTarGzFile.length(), 0, 0, 0100644, nowSeconds);
                ar.putArchiveEntry(entry2);
                copyFileToStream(controlTarGzFile, ar);
                ar.closeArchiveEntry();

                // Entry 3: data.tar.gz
                ArArchiveEntry entry3 = new ArArchiveEntry("data.tar.gz", dataTarGzFile.length(), 0, 0, 0100644, nowSeconds);
                ar.putArchiveEntry(entry3);
                copyFileToStream(dataTarGzFile, ar);
                ar.closeArchiveEntry();
            }

            System.out.println("✅ Ubuntu DEB successfully built: " + debFile.getAbsolutePath() + " (" + debFile.length() + " bytes)");
            return debFile;

        } finally {
            deleteRecursively(stagingDir);
        }
    }

    private static long writeDataTarGz(List<PackageEntry> files, File targetFile, StringBuilder md5sumsBuilder) throws Exception {
        long totalInstalledBytes = 0;
        GzipParameters gzipParams = new GzipParameters();
        gzipParams.setCompressionLevel(Deflater.BEST_SPEED);

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 65536);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos, gzipParams);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzos)) {

            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            // Collect all parent directories to ensure proper directory entries
            Set<String> dirs = new HashSet<>();
            dirs.add("./");
            for (PackageEntry file : files) {
                String[] parts = file.path.split("/");
                StringBuilder dirPath = new StringBuilder("./");
                for (int i = 0; i < parts.length - 1; i++) {
                    dirPath.append(parts[i]).append("/");
                    dirs.add(dirPath.toString());
                }
            }

            List<String> sortedDirs = new ArrayList<>(dirs);
            Collections.sort(sortedDirs);

            long nowSeconds = System.currentTimeMillis() / 1000;

            // Write directory entries
            for (String dir : sortedDirs) {
                TarArchiveEntry dirEntry = new TarArchiveEntry(dir);
                dirEntry.setMode(0755);
                dirEntry.setUserId(0);
                dirEntry.setGroupId(0);
                dirEntry.setUserName("root");
                dirEntry.setGroupName("root");
                dirEntry.setModTime(nowSeconds * 1000);
                tar.putArchiveEntry(dirEntry);
                tar.closeArchiveEntry();
            }

            // Write file entries
            byte[] buf = new byte[65536];
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            for (PackageEntry file : files) {
                totalInstalledBytes += file.size;
                String fullPath = "./" + file.path;
                TarArchiveEntry fileEntry = new TarArchiveEntry(fullPath);
                fileEntry.setSize(file.size);
                fileEntry.setMode(file.mode);
                fileEntry.setUserId(0);
                fileEntry.setGroupId(0);
                fileEntry.setUserName("root");
                fileEntry.setGroupName("root");
                fileEntry.setModTime(nowSeconds * 1000);

                tar.putArchiveEntry(fileEntry);

                md5.reset();
                try (InputStream in = file.openStream();
                     DigestInputStream dis = new DigestInputStream(in, md5)) {
                    int n;
                    while ((n = dis.read(buf)) != -1) {
                        tar.write(buf, 0, n);
                    }
                }
                tar.closeArchiveEntry();

                byte[] hash = md5.digest();
                for (byte b : hash) {
                    md5sumsBuilder.append(String.format("%02x", b));
                }
                md5sumsBuilder.append("  ").append(file.path).append("\n");
            }

            tar.finish();
        }
        return totalInstalledBytes;
    }

    private static void writeControlTarGz(List<PackageEntry> files, File targetFile) throws Exception {
        GzipParameters gzipParams = new GzipParameters();
        gzipParams.setCompressionLevel(Deflater.BEST_SPEED);

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 65536);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos, gzipParams);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzos)) {

            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            long nowSeconds = System.currentTimeMillis() / 1000;

            // Root dir
            TarArchiveEntry rootDirEntry = new TarArchiveEntry("./");
            rootDirEntry.setMode(0755);
            rootDirEntry.setUserId(0);
            rootDirEntry.setGroupId(0);
            rootDirEntry.setUserName("root");
            rootDirEntry.setGroupName("root");
            rootDirEntry.setModTime(nowSeconds * 1000);
            tar.putArchiveEntry(rootDirEntry);
            tar.closeArchiveEntry();

            byte[] buf = new byte[8192];
            for (PackageEntry file : files) {
                String fullPath = "./" + file.path;
                TarArchiveEntry fileEntry = new TarArchiveEntry(fullPath);
                fileEntry.setSize(file.size);
                fileEntry.setMode(file.mode);
                fileEntry.setUserId(0);
                fileEntry.setGroupId(0);
                fileEntry.setUserName("root");
                fileEntry.setGroupName("root");
                fileEntry.setModTime(nowSeconds * 1000);

                tar.putArchiveEntry(fileEntry);
                try (InputStream in = file.openStream()) {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        tar.write(buf, 0, n);
                    }
                }
                tar.closeArchiveEntry();
            }

            tar.finish();
        }
    }

    private static void copyFileToStream(File file, OutputStream out) throws Exception {
        byte[] buf = new byte[65536];
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), 65536)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    public static void main(String[] args) throws Exception {
        File baseDir = new File(".").getAbsoluteFile();
        if (args.length > 0) {
            baseDir = new File(args[0]).getAbsoluteFile();
        }
        File releaseDir = new File(baseDir, "release");
        buildDeb(baseDir, releaseDir);
    }
}
