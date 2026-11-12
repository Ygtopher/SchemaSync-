package com.dbtool.core.script;

public enum PsqlMetaCommand {
    SET("\\set"),
    GSET("\\gset"),
    ECHO("\\echo"),
    INCLUDE("\\i"),
    QUIT("\\q");

    private final String prefix;
    PsqlMetaCommand(String prefix) { this.prefix = prefix; }
    public String getPrefix() { return prefix; }
}
