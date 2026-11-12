package com.dbtool.core.script;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableReplacer {
    private static final Pattern QUOTED_VAR = Pattern.compile(":'([a-zA-Z0-9_]+)'");
    private static final Pattern UNQUOTED_VAR = Pattern.compile(":([a-zA-Z0-9_]+)");

    public static String replaceVariables(String sql, VariableContext context) {
        if (sql == null || context == null) return sql;

        // Replace quoted variables :'var' -> 'val'
        Matcher m1 = QUOTED_VAR.matcher(sql);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) {
            String varName = m1.group(1);
            Object val = context.get(varName);
            String replacement = val != null ? "'" + val.toString().replace("'", "''") + "'" : "NULL";
            m1.appendReplacement(sb1, Matcher.quoteReplacement(replacement));
        }
        m1.appendTail(sb1);

        // Replace unquoted variables :var -> val
        Matcher m2 = UNQUOTED_VAR.matcher(sb1.toString());
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            String varName = m2.group(1);
            Object val = context.get(varName);
            String replacement = val != null ? val.toString() : "NULL";
            m2.appendReplacement(sb2, Matcher.quoteReplacement(replacement));
        }
        m2.appendTail(sb2);

        return sb2.toString();
    }
}
