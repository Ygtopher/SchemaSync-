package com.dbtool.util;

import java.io.*;
import java.util.zip.GZIPInputStream;

public final class FileUtil {
    private FileUtil() {}

    public static String getExtension(String path) {
        if (path == null) return "";
        int dot = path.lastIndexOf('.');
        return (dot == -1) ? "" : path.substring(dot + 1).toLowerCase();
    }

    public static boolean hasExtension(String path, String... exts) {
        String ext = getExtension(path);
        for (String candidate : exts) {
            if (ext.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    public static InputStream getDecompressingInputStream(String filePath) throws IOException {
        InputStream is = new FileInputStream(filePath);
        if (filePath.toLowerCase().endsWith(".gz")) {
            return new GZIPInputStream(is);
        }
        return is;
    }

    public static boolean ensureDirectoryExists(File dir) {
        return dir != null && (dir.exists() || dir.mkdirs());
    }

    public static File getSchemaSyncSavesDir() {
        File docs = null;
        try {
            docs = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory();
        } catch (Throwable ignored) {}
        if (docs == null || !docs.exists()) {
            docs = new File(System.getProperty("user.home"), "Documents");
        }
        if (!docs.exists()) {
            docs = new File(System.getProperty("user.home"));
        }
        File savesDir = new File(docs, "SchemaSyncSaves");
        if (!savesDir.exists()) {
            savesDir.mkdirs();
        }
        return savesDir;
    }

    public static File getSaveFile(String filename) {
        return new File(getSchemaSyncSavesDir(), filename);
    }
}
