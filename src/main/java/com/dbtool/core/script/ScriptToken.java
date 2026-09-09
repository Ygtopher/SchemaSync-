package com.dbtool.core.script;

public class ScriptToken {
    private final TokenType type;
    private final String content;
    private final int lineNumber;

    public ScriptToken(TokenType type, String content, int lineNumber) {
        this.type = type;
        this.content = content;
        this.lineNumber = lineNumber;
    }

    public TokenType getType() { return type; }
    public String getContent() { return content; }
    public int getLineNumber() { return lineNumber; }
}
