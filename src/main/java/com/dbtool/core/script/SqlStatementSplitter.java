package com.dbtool.core.script;

import java.util.ArrayList;
import java.util.List;

public class SqlStatementSplitter {
    public static List<String> splitStatements(String script) {
        List<String> stmts = new ArrayList<>();
        if (script == null) return stmts;

        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        char stringChar = 0;
        boolean inDollarQuote = false;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);

            // Check for dollar quoting $$
            if (!inString && c == '$' && i + 1 < script.length() && script.charAt(i + 1) == '$') {
                inDollarQuote = !inDollarQuote;
                sb.append("$$");
                i++;
                continue;
            }

            if (!inDollarQuote) {
                if (!inString && (c == ''' || c == '"')) {
                    inString = true;
                    stringChar = c;
                    sb.append(c);
                } else if (inString && c == stringChar) {
                    if (i + 1 < script.length() && script.charAt(i + 1) == stringChar) {
                        sb.append(c).append(stringChar);
                        i++;
                    } else {
                        inString = false;
                        sb.append(c);
                    }
                } else if (!inString && c == ';') {
                    String stmt = sb.toString().trim();
                    if (!stmt.isEmpty()) stmts.add(stmt);
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        String rem = sb.toString().trim();
        if (!rem.isEmpty()) stmts.add(rem);
        return stmts;
    }
}
