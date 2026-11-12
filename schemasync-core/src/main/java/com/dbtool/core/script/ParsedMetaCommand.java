package com.dbtool.core.script;

public class ParsedMetaCommand {
    private final PsqlMetaCommand command;
    private final String argument;
    private final String value;

    public ParsedMetaCommand(PsqlMetaCommand command, String argument, String value) {
        this.command = command;
        this.argument = argument;
        this.value = value;
    }

    public PsqlMetaCommand getCommand() { return command; }
    public String getArgument() { return argument; }
    public String getValue() { return value; }
}
