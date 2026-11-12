package com.dbtool.core.inspector;

import java.util.regex.Pattern;

public class SchemaFilter {
    private Pattern includePattern;
    private Pattern excludePattern;

    public SchemaFilter(String includeRegex, String excludeRegex) {
        if (includeRegex != null && !includeRegex.trim().isEmpty()) {
            this.includePattern = Pattern.compile(includeRegex, Pattern.CASE_INSENSITIVE);
        }
        if (excludeRegex != null && !excludeRegex.trim().isEmpty()) {
            this.excludePattern = Pattern.compile(excludeRegex, Pattern.CASE_INSENSITIVE);
        }
    }

    public boolean matches(String tableName) {
        if (tableName == null) return false;
        if (excludePattern != null && excludePattern.matcher(tableName).matches()) return false;
        if (includePattern != null && !includePattern.matcher(tableName).matches()) return false;
        return true;
    }
}
