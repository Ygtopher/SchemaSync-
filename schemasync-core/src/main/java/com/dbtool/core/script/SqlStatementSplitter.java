package com.dbtool.core.script;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SqlStatementSplitter {
    private static final Pattern DOLLAR_TAG_PATTERN = Pattern.compile("^\\$[A-Za-z0-9_]*\\$$");

    public static List<String> splitStatements(String script) {
        List<String> stmts = new ArrayList<>();
        if (script == null || script.trim().isEmpty()) {
            return stmts;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inDollarQuote = false;
        String dollarTag = "";

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = (i + 1 < script.length()) ? script.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                current.append(c);
            } else if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    current.append(c).append(next);
                    i++;
                } else {
                    current.append(c);
                }
            } else if (inSingleQuotes) {
                if (c == '\'') {
                    if (next == '\'') {
                        current.append(c).append(next);
                        i++;
                    } else {
                        inSingleQuotes = false;
                        current.append(c);
                    }
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '"') {
                    if (next == '"') {
                        current.append(c).append(next);
                        i++;
                    } else {
                        inDoubleQuotes = false;
                        current.append(c);
                    }
                } else {
                    current.append(c);
                }
            } else if (inDollarQuote) {
                if (c == '$' && script.startsWith(dollarTag, i)) {
                    inDollarQuote = false;
                    current.append(dollarTag);
                    i += dollarTag.length() - 1;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    current.append(c).append(next);
                    i++;
                } else if (c == '/' && next == '*') {
                    inBlockComment = true;
                    current.append(c).append(next);
                    i++;
                } else if (c == '\'') {
                    inSingleQuotes = true;
                    current.append(c);
                } else if (c == '"') {
                    inDoubleQuotes = true;
                    current.append(c);
                } else if (c == '$') {
                    int nextDollar = script.indexOf('$', i + 1);
                    if (nextDollar != -1) {
                        String candidate = script.substring(i, nextDollar + 1);
                        if (DOLLAR_TAG_PATTERN.matcher(candidate).matches()) {
                            inDollarQuote = true;
                            dollarTag = candidate;
                            current.append(candidate);
                            i = nextDollar;
                            continue;
                        }
                    }
                    current.append(c);
                } else if (c == ';') {
                    String stmt = current.toString().trim();
                    if (!stmt.isEmpty()) {
                        stmts.add(stmt);
                    }
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }

        String trailing = current.toString().trim();
        if (!trailing.isEmpty()) {
            stmts.add(trailing);
        }
        return stmts;
    }
}
