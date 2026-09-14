package com.dbtool.core.script;

public class PsqlMetaCommandParser {
    public static ParsedMetaCommand parse(String line) {
        if (line == null || !line.trim().startsWith("\\")) return null;
        String trimmed = line.trim();
        if (trimmed.startsWith("\\set ")) {
            String rest = trimmed.substring(5).trim();
            int eq = rest.indexOf('=');
            if (eq != -1) {
                String var = rest.substring(0, eq).trim();
                String val = rest.substring(eq + 1).trim();
                return new ParsedMetaCommand(PsqlMetaCommand.SET, var, val);
            } else {
                String[] parts = rest.split("\\s+", 2);
                return new ParsedMetaCommand(PsqlMetaCommand.SET, parts[0], parts.length > 1 ? parts[1] : "");
            }
        } else if (trimmed.startsWith("\\echo ")) {
            return new ParsedMetaCommand(PsqlMetaCommand.ECHO, null, trimmed.substring(6).trim());
        } else if (trimmed.startsWith("\\gset")) {
            String prefix = trimmed.length() > 5 ? trimmed.substring(5).trim() : "";
            return new ParsedMetaCommand(PsqlMetaCommand.GSET, prefix, null);
        }
        return null;
    }
}
