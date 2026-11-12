package com.dbtool.util;

import com.dbtool.DatabaseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static SQL Query Linter, Pre-Execution Optimizer, Parameterized Query Engine,
 * Context-Aware Autocomplete Resolver, and Dry-Run DML Preview Generator.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class SqlQueryAnalyzer {

    public enum Severity {
        CRITICAL,
        WARNING,
        INFO
    }

    public static class LintIssue {
        public final Severity severity;
        public final String code;
        public final String title;
        public final String message;
        public final String suggestedSql;

        public LintIssue(Severity severity, String code, String title, String message, String suggestedSql) {
            this.severity = severity;
            this.code = code;
            this.title = title;
            this.message = message;
            this.suggestedSql = suggestedSql;
        }

        @Override
        public String toString() {
            return "[" + severity + "] " + title + ": " + message;
        }
    }

    private static final Map<String, String> LAST_PARAMETER_VALUES = new ConcurrentHashMap<>();

    public static Map<String, String> getLastParameterValues() {
        return LAST_PARAMETER_VALUES;
    }

    /**
     * Analyzes a SQL statement or script for anti-patterns, performance bottlenecks,
     * and destructive operations without a WHERE clause.
     */
    public static List<LintIssue> analyze(String sql, DatabaseManager dbManager) {
        List<LintIssue> issues = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) {
            return issues;
        }

        String stripped = stripComments(sql).trim();
        if (stripped.isEmpty()) return issues;

        String clean = stripped.replaceAll(";+\\s*$", "").trim();
        String upper = clean.toUpperCase();

        // 1. Destructive UPDATE / DELETE without WHERE clause
        if (isUnfilteredDestructiveDml(clean)) {
            String kind = upper.startsWith("DELETE") ? "DELETE" : "UPDATE";
            String dryRun = buildDryRunSelectSql(clean);
            issues.add(new LintIssue(
                    Severity.CRITICAL,
                    "UNFILTERED_DML",
                    "Unfiltered " + kind + " Statement (No WHERE Clause)",
                    "This " + kind + " statement has no WHERE filter (or uses a tautology like 1=1) and will modify or delete ALL rows in the target table.",
                    dryRun
            ));
        }

        // 2. SELECT * detection and column expansion suggestion
        if (upper.startsWith("SELECT") && Pattern.compile("(?is)^SELECT\\s+(?:DISTINCT\\s+)?\\*\\s+FROM\\b").matcher(clean).find()) {
            String expanded = expandSelectStar(clean, dbManager);
            String suggestion = !expanded.equals(clean) ? expanded : null;
            issues.add(new LintIssue(
                    Severity.INFO,
                    "SELECT_STAR",
                    "SELECT * Used Instead of Explicit Columns",
                    "Fetching all columns increases network I/O and prevents index-only covering scans. Expand '*' to explicit column names.",
                    suggestion
            ));
        }

        // 3. Non-Sargable WHERE predicates (functions wrapping columns in WHERE comparisons)
        Matcher nonSargable = Pattern.compile(
                "(?is)\\bWHERE\\b.*\\b(UPPER|LOWER|YEAR|MONTH|DAY|DATE|TRIM|LTRIM|RTRIM|SUBSTR|SUBSTRING|COALESCE|CAST)\\s*\\(\\s*([a-zA-Z0-9_.]+)\\s*\\)\\s*(=|!=|<>|<=|>=|<|>|\\bI?LIKE\\b)"
        ).matcher(clean);
        if (nonSargable.find()) {
            String fn = nonSargable.group(1).toUpperCase();
            String col = nonSargable.group(2);
            issues.add(new LintIssue(
                    Severity.WARNING,
                    "NON_SARGABLE_WHERE",
                    "Non-Sargable Function " + fn + "(" + col + ") in WHERE Clause",
                    "Wrapping column '" + col + "' in " + fn + "() disables standard B-Tree index seeks and forces a full table scan. Consider range predicates or a functional index.",
                    null
            ));
        }

        // 4. Leading wildcard LIKE '%value' preventing index prefix scan
        Matcher leadingWildcard = Pattern.compile("(?is)\\bWHERE\\b.*\\b([a-zA-Z0-9_.]+)\\s+(?:NOT\\s+)?I?LIKE\\s+'%[^']+").matcher(clean);
        if (leadingWildcard.find()) {
            String col = leadingWildcard.group(1);
            issues.add(new LintIssue(
                    Severity.WARNING,
                    "LEADING_WILDCARD_LIKE",
                    "Leading Wildcard LIKE '%...' on '" + col + "'",
                    "A leading '%' wildcard prevents standard B-Tree index prefix lookup on '" + col + "'.",
                    null
            ));
        }

        // 5. Implicit Cartesian Join (FROM tableA, tableB without WHERE)
        Matcher commaJoin = Pattern.compile("(?is)\\bFROM\\s+([a-zA-Z0-9_\"]+\\s*(?:AS\\s+[a-zA-Z0-9_]+|[a-zA-Z0-9_]+)?\\s*,\\s*[a-zA-Z0-9_\"]+)").matcher(clean);
        if (commaJoin.find() && !upper.contains(" WHERE ")) {
            issues.add(new LintIssue(
                    Severity.WARNING,
                    "CARTESIAN_JOIN",
                    "Potential Accidental Cartesian Product (Cross Join)",
                    "Multiple tables are listed in the FROM clause separated by commas without a WHERE join condition. Use explicit INNER/LEFT JOIN ... ON syntax.",
                    null
            ));
        }

        // 6. Unbounded SELECT without LIMIT
        if (upper.startsWith("SELECT")
                && !Pattern.compile("(?is)\\b(LIMIT|FETCH\\s+FIRST|TOP)\\b").matcher(clean).find()
                && !Pattern.compile("(?is)\\b(COUNT|SUM|AVG|MIN|MAX)\\s*\\(").matcher(clean).find()
                && !upper.contains("GROUP BY")) {
            issues.add(new LintIssue(
                    Severity.INFO,
                    "MISSING_LIMIT",
                    "Unbounded SELECT Query (No LIMIT Clause)",
                    "Running an unbounded SELECT on large tables can fetch excessive rows into memory. Consider adding a LIMIT clause.",
                    clean + " LIMIT 100;"
            ));
        }

        return issues;
    }

    /**
     * Returns true if the SQL statement is an UPDATE or DELETE without a meaningful WHERE clause.
     */
    public static boolean isUnfilteredDestructiveDml(String sql) {
        if (sql == null) return false;
        String clean = stripComments(sql).trim().replaceAll(";+\\s*$", "").trim();
        String upper = clean.toUpperCase();

        if (!upper.startsWith("DELETE") && !upper.startsWith("UPDATE")) {
            return false;
        }

        int whereIdx = indexOfKeywordOutsideQuotes(clean, "WHERE");
        if (whereIdx == -1) {
            return true;
        }

        String whereClause = clean.substring(whereIdx + 5).trim();
        if (whereClause.isEmpty()) {
            return true;
        }

        // Check trivial tautologies like WHERE 1=1 or WHERE true
        String normWhere = whereClause.replaceAll("\\s+", "").toUpperCase();
        return normWhere.equals("1=1") || normWhere.equals("TRUE") || normWhere.equals("'1'='1'");
    }

    /**
     * Converts an UPDATE or DELETE statement into a safe read-only SELECT preview query
     * so users can inspect the exact rows that would be affected before executing the mutation.
     */
    public static String buildDryRunSelectSql(String dmlSql) {
        if (dmlSql == null) return "";
        String clean = stripComments(dmlSql).trim().replaceAll(";+\\s*$", "").trim();
        String upper = clean.toUpperCase();

        // Case 1: DELETE FROM <table> [alias] [WHERE ...]
        Matcher delMatcher = Pattern.compile("(?is)^DELETE\\s+FROM\\s+([a-zA-Z0-9_.\"`\\[\\]]+)(?:\\s+(?:AS\\s+)?([a-zA-Z0-9_]+))?(?:\\s+WHERE\\s+(.+))?$").matcher(clean);
        if (delMatcher.find()) {
            String table = delMatcher.group(1);
            String alias = delMatcher.group(2);
            String where = delMatcher.group(3);
            if (alias != null && alias.equalsIgnoreCase("WHERE")) {
                alias = null;
            }
            StringBuilder sb = new StringBuilder("SELECT * FROM ").append(table);
            if (alias != null && !alias.isEmpty() && !alias.equalsIgnoreCase("WHERE")) {
                sb.append(" ").append(alias);
            }
            if (where != null && !where.trim().isEmpty()) {
                sb.append(" WHERE ").append(where.trim());
            }
            return sb.append(";").toString();
        }

        // Fallback simpler DELETE parser when no alias
        Matcher simpleDel = Pattern.compile("(?is)^DELETE\\s+FROM\\s+([a-zA-Z0-9_.\"`\\[\\]]+)\\s*(.*)$").matcher(clean);
        if (simpleDel.find()) {
            String table = simpleDel.group(1);
            String rest = simpleDel.group(2).trim();
            return "SELECT * FROM " + table + (rest.isEmpty() ? "" : " " + rest) + ";";
        }

        // Case 2: UPDATE <table> SET ... [WHERE ...]
        if (upper.startsWith("UPDATE ")) {
            int setIdx = indexOfKeywordOutsideQuotes(clean, "SET");
            if (setIdx != -1) {
                String tablePart = clean.substring(6, setIdx).trim();
                int whereIdx = indexOfKeywordOutsideQuotes(clean, "WHERE");
                if (whereIdx != -1 && whereIdx > setIdx) {
                    String wherePart = clean.substring(whereIdx).trim();
                    return "SELECT * FROM " + tablePart + " " + wherePart + ";";
                } else {
                    return "SELECT * FROM " + tablePart + ";";
                }
            }
        }

        return clean;
    }

    /**
     * Expands "SELECT * FROM <table> ..." into "SELECT col1, col2, ... FROM <table> ..."
     * using live schema metadata from DatabaseManager.
     */
    public static String expandSelectStar(String sql, DatabaseManager dbManager) {
        if (sql == null || dbManager == null || dbManager.connection == null) return sql;
        Matcher m = Pattern.compile("(?is)^(\\s*SELECT\\s+(?:DISTINCT\\s+)?)\\*(\\s+FROM\\s+([a-zA-Z0-9_.\"`\\[\\]]+)(?:\\s+(?:AS\\s+)?([a-zA-Z0-9_]+))?.*)$").matcher(sql);
        if (!m.find()) {
            return sql;
        }
        String prefix = m.group(1);
        String suffix = m.group(2);
        String rawTable = m.group(3).replace("\"", "").replace("`", "").replace("[", "").replace("]", "");
        String alias = m.group(4);

        // Reserved keywords are not aliases
        if (alias != null && alias.toUpperCase().matches("WHERE|ORDER|GROUP|HAVING|LIMIT|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|ON|UNION|OFFSET")) {
            alias = null;
        }

        try {
            List<String> cols = dbManager.getColumnNames(rawTable);
            if (cols == null || cols.isEmpty()) {
                for (String t : dbManager.getTableNames()) {
                    if (t.equalsIgnoreCase(rawTable)) {
                        cols = dbManager.getColumnNames(t);
                        break;
                    }
                }
            }
            if (cols != null && !cols.isEmpty()) {
                StringBuilder colList = new StringBuilder();
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) colList.append(", ");
                    if (alias != null && !alias.isEmpty()) {
                        colList.append(alias).append(".");
                    }
                    colList.append(dbManager.quoteColumnName(cols.get(i)));
                }
                return prefix + colList + suffix;
            }
        } catch (Exception ignored) {
        }
        return sql;
    }

    /**
     * Extracts named parameters (:paramName or ${paramName}) from a SQL query,
     * ignoring string literals, comments, and PostgreSQL ::type casts.
     */
    public static List<String> extractNamedParameters(String sql) {
        if (sql == null || sql.isEmpty()) return Collections.emptyList();
        Set<String> params = new LinkedHashSet<>();
        int len = sql.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < len) ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inSingleQuote) {
                if (c == '\'') {
                    if (next == '\'') i++;
                    else inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (c == '"') {
                    if (next == '"') i++;
                    else inDoubleQuote = false;
                }
                continue;
            }

            if (c == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (c == '"') {
                inDoubleQuote = true;
                continue;
            }

            // Skip PostgreSQL type cast ::
            if (c == ':' && next == ':') {
                i++;
                continue;
            }

            // Check :param_name (only when not preceded by another ':' or alphanumeric)
            if (c == ':' && (Character.isLetter(next) || next == '_')) {
                char prev = (i > 0) ? sql.charAt(i - 1) : ' ';
                if (!Character.isLetterOrDigit(prev) && prev != ':') {
                    int start = i + 1;
                    int end = start;
                    while (end < len && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_')) {
                        end++;
                    }
                    params.add(sql.substring(start, end));
                    i = end - 1;
                    continue;
                }
            }

            // Check ${param_name}
            if (c == '$' && next == '{') {
                int close = sql.indexOf('}', i + 2);
                if (close > i + 2) {
                    String name = sql.substring(i + 2, close).trim();
                    if (name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        params.add(name);
                        i = close;
                    }
                }
            }
        }
        return new ArrayList<>(params);
    }

    /**
     * Binds user-supplied parameter values into a SQL string safely, formatting numeric/boolean/null
     * literals unquoted and string literals with escaped single quotes.
     */
    public static String bindParameters(String sql, Map<String, String> paramValues) {
        if (sql == null || paramValues == null || paramValues.isEmpty()) return sql;
        for (Map.Entry<String, String> entry : paramValues.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                LAST_PARAMETER_VALUES.put(entry.getKey(), entry.getValue());
            }
        }

        StringBuilder out = new StringBuilder();
        int len = sql.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < len) ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                out.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                out.append(c);
                if (c == '*' && next == '/') {
                    out.append(next);
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inSingleQuote) {
                out.append(c);
                if (c == '\'') {
                    if (next == '\'') {
                        out.append(next);
                        i++;
                    } else {
                        inSingleQuote = false;
                    }
                }
                continue;
            }
            if (inDoubleQuote) {
                out.append(c);
                if (c == '"') {
                    if (next == '"') {
                        out.append(next);
                        i++;
                    } else {
                        inDoubleQuote = false;
                    }
                }
                continue;
            }

            if (c == '-' && next == '-') {
                out.append(c).append(next);
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                out.append(c).append(next);
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                out.append(c);
                inSingleQuote = true;
                continue;
            }
            if (c == '"') {
                out.append(c);
                inDoubleQuote = true;
                continue;
            }

            if (c == ':' && next == ':') {
                out.append("::");
                i++;
                continue;
            }

            if (c == ':' && (Character.isLetter(next) || next == '_')) {
                char prev = (i > 0) ? sql.charAt(i - 1) : ' ';
                if (!Character.isLetterOrDigit(prev) && prev != ':') {
                    int start = i + 1;
                    int end = start;
                    while (end < len && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_')) {
                        end++;
                    }
                    String name = sql.substring(start, end);
                    if (paramValues.containsKey(name)) {
                        out.append(formatParameterLiteral(paramValues.get(name)));
                    } else {
                        out.append(':').append(name);
                    }
                    i = end - 1;
                    continue;
                }
            }

            if (c == '$' && next == '{') {
                int close = sql.indexOf('}', i + 2);
                if (close > i + 2) {
                    String name = sql.substring(i + 2, close).trim();
                    if (paramValues.containsKey(name)) {
                        out.append(formatParameterLiteral(paramValues.get(name)));
                        i = close;
                        continue;
                    }
                }
            }

            out.append(c);
        }

        return out.toString();
    }

    private static String formatParameterLiteral(String raw) {
        if (raw == null || raw.trim().equalsIgnoreCase("NULL")) {
            return "NULL";
        }
        String trimmed = raw.trim();
        // Already quoted string literal
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed;
        }
        // Numeric literal
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return trimmed;
        }
        // Boolean literal
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return trimmed.toUpperCase();
        }
        return "'" + raw.replace("'", "''") + "'";
    }

    /**
     * Resolves table aliases in a SQL statement (e.g., "FROM employees e JOIN departments d" -> {e=employees, d=departments})
     * and generates context-aware completion suggestions for the current cursor position.
     */
    public static Map<String, String> extractTableAliases(String sql) {
        Map<String, String> aliases = new LinkedHashMap<>();
        if (sql == null || sql.isEmpty()) return aliases;
        Matcher m = Pattern.compile("(?is)\\b(?:FROM|JOIN)\\s+([a-zA-Z0-9_\"`]+)(?:\\s+(?:AS\\s+)?([a-zA-Z0-9_]+))?").matcher(sql);
        while (m.find()) {
            String table = m.group(1).replace("\"", "").replace("`", "");
            String alias = m.group(2);
            aliases.put(table.toLowerCase(), table);
            if (alias != null && !alias.toUpperCase().matches("ON|WHERE|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|GROUP|ORDER|HAVING|LIMIT|SET|VALUES")) {
                aliases.put(alias.toLowerCase(), table);
            }
        }
        return aliases;
    }

    /**
     * Computes context-sensitive SQL completion items at the given caret position.
     */
    public static List<String> suggestCompletions(String fullText, int caretPos, DatabaseManager dbManager) {
        List<String> suggestions = new ArrayList<>();
        if (fullText == null) return suggestions;
        int safeCaret = Math.max(0, Math.min(caretPos, fullText.length()));
        String beforeCaret = fullText.substring(0, safeCaret);

        Map<String, String> aliases = extractTableAliases(fullText);

        // 1. Check if typing "alias.prefix"
        Matcher dotMatcher = Pattern.compile("([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]*)$").matcher(beforeCaret);
        if (dotMatcher.find() && dbManager != null && dbManager.connection != null) {
            String qualifier = dotMatcher.group(1).toLowerCase();
            String prefix = dotMatcher.group(2).toLowerCase();
            String targetTable = aliases.getOrDefault(qualifier, qualifier);
            try {
                List<String> cols = dbManager.getColumnNames(targetTable);
                if (cols == null || cols.isEmpty()) {
                    for (String t : dbManager.getTableNames()) {
                        if (t.equalsIgnoreCase(targetTable)) {
                            cols = dbManager.getColumnNames(t);
                            break;
                        }
                    }
                }
                if (cols != null) {
                    for (String col : cols) {
                        if (col.toLowerCase().startsWith(prefix)) {
                            suggestions.add(dotMatcher.group(1) + "." + col);
                        }
                    }
                }
            } catch (Exception ignored) {}
            if (!suggestions.isEmpty()) return suggestions;
        }

        // 2. Check if right after "JOIN <table> [alias] ON " -> suggest Foreign Key join condition!
        Matcher joinOnMatcher = Pattern.compile("(?is)\\bJOIN\\s+([a-zA-Z0-9_\"`]+)(?:\\s+(?:AS\\s+)?([a-zA-Z0-9_]+))?\\s+ON\\s*$").matcher(beforeCaret);
        if (joinOnMatcher.find() && dbManager != null && dbManager.connection != null) {
            String joinedTable = joinOnMatcher.group(1).replace("\"", "").replace("`", "");
            String joinedAlias = joinOnMatcher.group(2);
            if (joinedAlias == null || joinedAlias.equalsIgnoreCase("ON")) {
                joinedAlias = joinedTable;
            }
            try {
                for (Map.Entry<String, String> entry : aliases.entrySet()) {
                    String otherAlias = entry.getKey();
                    String otherTable = entry.getValue();
                    if (otherTable.equalsIgnoreCase(joinedTable)) continue;

                    Map<String, String[]> fks1 = dbManager.getForeignKeys(joinedTable);
                    if (fks1 != null) {
                        for (Map.Entry<String, String[]> fk : fks1.entrySet()) {
                            String[] target = fk.getValue();
                            if (target != null && target.length >= 2 && target[0] != null && target[0].equalsIgnoreCase(otherTable)) {
                                suggestions.add(joinedAlias + "." + fk.getKey() + " = " + otherAlias + "." + target[1]);
                            }
                        }
                    }
                    Map<String, String[]> fks2 = dbManager.getForeignKeys(otherTable);
                    if (fks2 != null) {
                        for (Map.Entry<String, String[]> fk : fks2.entrySet()) {
                            String[] target = fk.getValue();
                            if (target != null && target.length >= 2 && target[0] != null && target[0].equalsIgnoreCase(joinedTable)) {
                                suggestions.add(joinedAlias + "." + target[1] + " = " + otherAlias + "." + fk.getKey());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            if (!suggestions.isEmpty()) return suggestions;
        }

        // 3. General word prefix matching (tables, columns of referenced tables, and SQL keywords)
        Matcher wordMatcher = Pattern.compile("([a-zA-Z0-9_]*)$").matcher(beforeCaret);
        String prefix = wordMatcher.find() ? wordMatcher.group(1).toLowerCase() : "";

        Set<String> candidates = new LinkedHashSet<>();
        if (dbManager != null && dbManager.connection != null) {
            try {
                for (String t : dbManager.getTableNames()) {
                    if (t.toLowerCase().startsWith(prefix)) candidates.add(t);
                }
                for (String refTable : new LinkedHashSet<>(aliases.values())) {
                    List<String> cols = dbManager.getColumnNames(refTable);
                    if (cols != null) {
                        for (String c : cols) {
                            if (c.toLowerCase().startsWith(prefix)) candidates.add(c);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        String[] keywords = {
            "SELECT", "FROM", "WHERE", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL OUTER JOIN",
            "GROUP BY", "HAVING", "ORDER BY", "LIMIT", "OFFSET", "INSERT INTO", "VALUES",
            "UPDATE", "SET", "DELETE FROM", "CREATE TABLE", "ALTER TABLE", "DROP TABLE",
            "DISTINCT", "COUNT(*)", "SUM()", "AVG()", "MIN()", "MAX()", "COALESCE()", "CASE WHEN"
        };
        for (String kw : keywords) {
            if (kw.toLowerCase().startsWith(prefix)) {
                candidates.add(kw);
            }
        }

        suggestions.addAll(candidates);
        return suggestions;
    }

    private static String stripComments(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("(?m)--.*$", "")
                  .replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static int indexOfKeywordOutsideQuotes(String sql, String keyword) {
        Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(keyword) + "\\b");
        Matcher m = p.matcher(sql);
        while (m.find()) {
            int pos = m.start();
            if (!isInsideQuotes(sql, pos)) {
                return pos;
            }
        }
        return -1;
    }

    private static boolean isInsideQuotes(String sql, int targetPos) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < targetPos && i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
        }
        return inSingle || inDouble;
    }
}
