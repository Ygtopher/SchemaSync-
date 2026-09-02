package com.dbtool.util;

import java.io.File;

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

    public static boolean ensureDirectoryExists(File dir) {
        return dir != null && (dir.exists() || dir.mkdirs());
    }
}
