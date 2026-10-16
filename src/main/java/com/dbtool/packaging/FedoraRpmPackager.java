package com.dbtool.packaging;

import org.redline_rpm.Builder;
import org.redline_rpm.header.Architecture;
import org.redline_rpm.header.Os;
import org.redline_rpm.header.RpmType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Builds standard, production-grade RPM packages for Fedora 44 (and compatible RPM distributions).
 * Configures /usr/bin/schemasync, /usr/share/schemasync/SchemaSync.jar,
 * /usr/share/applications/schemasync.desktop, and system hicolor icons.
 *
 * Creator: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class FedoraRpmPackager {

    public static final String VERSION = "1.6.7";
    public static final String RELEASE = "1.fc44";
    public static final String PACKAGE_NAME = "schemasync";

    public static File buildRpm(File baseDir, File outputDir) throws Exception {
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

        File tempDir = new File(outputDir, "rpm_staging_" + System.currentTimeMillis());
        tempDir.mkdirs();

        try {
            // 1. Create Fedora launcher shell script
            File launcherFile = new File(tempDir, "schemasync");
            String launcherScript = "#!/bin/bash\n"
                    + "# SchemaSync Fedora 44 Launcher\n"
                    + "# Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.\n\n"
                    + "if [ -n \"$JAVA_HOME\" ] && [ -x \"$JAVA_HOME/bin/java\" ]; then\n"
                    + "    JAVA_CMD=\"$JAVA_HOME/bin/java\"\n"
                    + "elif command -v java >/dev/null 2>&1; then\n"
                    + "    JAVA_CMD=\"java\"\n"
                    + "else\n"
                    + "    echo \"Error: Java 11 or newer is required to run SchemaSync.\" >&2\n"
                    + "    echo \"Please install OpenJDK on Fedora: sudo dnf install java-latest-openjdk\" >&2\n"
                    + "    exit 1\n"
                    + "fi\n\n"
                    + "# FlatLaf / Linux Wayland, TimeZone, and HiDPI font rendering settings\n"
                    + "export _JAVA_AWT_WM_NONREPARENTING=1\n"
                    + "JAVA_OPTS=\"--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED -Duser.timezone=UTC -Dsun.java2d.pmoffscreen=false -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -Dsun.java2d.uiScale.enabled=true -Djdk.gtk.version=3\"\n\n"
                    + "JAR_PATH=\"/usr/share/schemasync/SchemaSync.jar\"\n\n"
                    + "if [ ! -f \"$JAR_PATH\" ]; then\n"
                    + "    echo \"Error: SchemaSync JAR not found at $JAR_PATH\" >&2\n"
                    + "    exit 1\n"
                    + "fi\n\n"
                    + "exec \"$JAVA_CMD\" $JAVA_OPTS -jar \"$JAR_PATH\" \"$@\"\n";

            try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(launcherFile), StandardCharsets.UTF_8)) {
                out.write(launcherScript);
            }

            // 2. Create .desktop file
            File desktopFile = new File(tempDir, "schemasync.desktop");
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

            try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(desktopFile), StandardCharsets.UTF_8)) {
                out.write(desktopContent);
            }

            // Icons
            File icon512 = new File(baseDir, "src/main/resources/icons/app-icon.png");
            File icon64 = new File(baseDir, "src/main/resources/icons/app-icon-64.png");
            File icon32 = new File(baseDir, "src/main/resources/icons/app-icon-32.png");
            File icon16 = new File(baseDir, "src/main/resources/icons/app-icon-16.png");

            // Post-install script: refresh desktop & icon caches in Fedora GNOME / KDE
            String postInstallScript = "if [ -x /usr/bin/update-desktop-database ]; then\n"
                    + "    /usr/bin/update-desktop-database /usr/share/applications &> /dev/null || :\n"
                    + "fi\n"
                    + "if [ -x /usr/bin/gtk-update-icon-cache ]; then\n"
                    + "    /usr/bin/gtk-update-icon-cache -f -t /usr/share/icons/hicolor &> /dev/null || :\n"
                    + "fi\n";

            // Post-uninstall script
            String postUninstallScript = "if [ -x /usr/bin/update-desktop-database ]; then\n"
                    + "    /usr/bin/update-desktop-database /usr/share/applications &> /dev/null || :\n"
                    + "fi\n"
                    + "if [ -x /usr/bin/gtk-update-icon-cache ]; then\n"
                    + "    /usr/bin/gtk-update-icon-cache -f -t /usr/share/icons/hicolor &> /dev/null || :\n"
                    + "fi\n";

            // Configure Redline RPM Builder
            Builder builder = new Builder();
            builder.setPackage(PACKAGE_NAME, VERSION, RELEASE);
            builder.setType(RpmType.BINARY);
            builder.setPlatform(Architecture.NOARCH, Os.LINUX);
            builder.setSummary("Modern database management and synchronization tool");
            builder.setDescription("SchemaSync is a high-performance, lightweight database synchronization and "
                    + "management tool for PostgreSQL, MySQL, SQLite, MS Access, and H2.\n"
                    + "Features include 1-Click Schema Migrations, Live Health Telemetry, Data Masking Studio, "
                    + "AES-256 Vault, Query Profiler, Visual Join Builder, and Dark Mode UI.");
            builder.setLicense("Proprietary");
            builder.setGroup("Development/Tools");
            builder.setDistribution("Fedora 44");
            builder.setVendor("NSENGIYUMVA Christophe");
            builder.setPackager("NSENGIYUMVA Christophe <https://github.com/Ygtopher/SchemaSync->");
            builder.setUrl("https://github.com/Ygtopher/SchemaSync-");

            // Require Java 11 or higher
            builder.addDependencyMore("java-headless", "1:11");

            // Add scripts
            builder.setPostInstallScript(postInstallScript);
            builder.setPostUninstallScript(postUninstallScript);

            // Add files with standard Linux permissions (0755 for executable, 0644 for data)
            builder.addFile("/usr/bin/schemasync", launcherFile, 0755);
            builder.addFile("/usr/share/schemasync/SchemaSync.jar", jarFile, 0644);
            builder.addFile("/usr/share/applications/schemasync.desktop", desktopFile, 0644);

            if (icon512.exists()) {
                builder.addFile("/usr/share/icons/hicolor/512x512/apps/schemasync.png", icon512, 0644);
                builder.addFile("/usr/share/pixmaps/schemasync.png", icon512, 0644);
            }
            if (icon64.exists()) {
                builder.addFile("/usr/share/icons/hicolor/64x64/apps/schemasync.png", icon64, 0644);
            }
            if (icon32.exists()) {
                builder.addFile("/usr/share/icons/hicolor/32x32/apps/schemasync.png", icon32, 0644);
            }
            if (icon16.exists()) {
                builder.addFile("/usr/share/icons/hicolor/16x16/apps/schemasync.png", icon16, 0644);
            }

            String rpmFilename = builder.build(outputDir);
            File finalRpm = new File(outputDir, rpmFilename);
            System.out.println("✅ Fedora RPM successfully built: " + finalRpm.getAbsolutePath() + " (" + finalRpm.length() + " bytes)");
            return finalRpm;

        } finally {
            deleteRecursively(tempDir);
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
        buildRpm(baseDir, releaseDir);
    }
}
