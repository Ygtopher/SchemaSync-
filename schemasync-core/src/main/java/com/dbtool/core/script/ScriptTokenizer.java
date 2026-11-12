package com.dbtool.core.script;

import java.util.ArrayList;
import java.util.List;

public class ScriptTokenizer {
    public List<ScriptToken> tokenize(String script) {
        List<ScriptToken> tokens = new ArrayList<>();
        if (script == null) return tokens;

        String[] lines = script.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("\\")) {
                tokens.add(new ScriptToken(TokenType.META_COMMAND, line, i + 1));
            } else if (line.startsWith("--")) {
                tokens.add(new ScriptToken(TokenType.COMMENT, line, i + 1));
            } else {
                tokens.add(new ScriptToken(TokenType.SQL_STATEMENT, line, i + 1));
            }
        }
        return tokens;
    }
}
