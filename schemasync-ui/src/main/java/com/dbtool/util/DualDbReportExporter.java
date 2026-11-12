package com.dbtool.util;

import com.dbtool.Main;
import com.dbtool.core.diff.ColumnDiff;
import com.dbtool.core.diff.DifferenceType;
import com.dbtool.core.diff.SchemaDiffResult;
import com.dbtool.core.diff.TableDiff;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Generates executive HTML and Markdown comparison audit reports for Dual Database Compare.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class DualDbReportExporter {

    public static String generateHtmlReport(String sourceDbName, String targetDbName,
                                            SchemaDiffResult result, String ddlScript,
                                            String dataDiffSummary) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        int total = result != null ? result.getTableDiffs().size() : 0;
        int identical = 0, modified = 0, onlySource = 0, onlyTarget = 0;

        if (result != null) {
            for (TableDiff td : result.getTableDiffs()) {
                if (td.getType() == DifferenceType.IDENTICAL) identical++;
                else if (td.getType() == DifferenceType.MODIFIED) modified++;
                else if (td.getType() == DifferenceType.REMOVED) onlySource++;
                else if (td.getType() == DifferenceType.ADDED) onlyTarget++;
            }
        }
        int totalDiff = modified + onlySource + onlyTarget;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>SchemaSync Comparison Report - ").append(escapeHtml(sourceDbName)).append(" vs ").append(escapeHtml(targetDbName)).append("</title>\n");
        sb.append("<style>\n");
        sb.append(":root { --bg: #0f172a; --card: #1e293b; --border: #334155; --text: #f8fafc; --muted: #94a3b8; --accent: #38bdf8; --success: #22c55e; --warning: #eab308; --danger: #ef4444; }\n");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 24px; line-height: 1.5; }\n");
        sb.append(".container { max-width: 1200px; margin: 0 auto; }\n");
        sb.append(".header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 16px; margin-bottom: 24px; }\n");
        sb.append(".header h1 { margin: 0; font-size: 24px; color: var(--accent); }\n");
        sb.append(".meta { color: var(--muted); font-size: 13px; }\n");
        sb.append(".cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }\n");
        sb.append(".card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 16px; text-align: center; }\n");
        sb.append(".card .num { font-size: 28px; font-weight: bold; margin-bottom: 4px; }\n");
        sb.append(".card .lbl { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.5px; }\n");
        sb.append("table { width: 100%; border-collapse: collapse; margin-bottom: 24px; background: var(--card); border-radius: 8px; overflow: hidden; border: 1px solid var(--border); }\n");
        sb.append("th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--border); font-size: 14px; }\n");
        sb.append("th { background: #1a2234; color: var(--muted); font-weight: 600; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px; }\n");
        sb.append("tr:hover { background: #263349; }\n");
        sb.append(".badge { display: inline-block; padding: 3px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; }\n");
        sb.append(".badge-ident { background: rgba(34, 197, 94, 0.15); color: var(--success); border: 1px solid rgba(34, 197, 94, 0.3); }\n");
        sb.append(".badge-mod { background: rgba(234, 179, 8, 0.15); color: var(--warning); border: 1px solid rgba(234, 179, 8, 0.3); }\n");
        sb.append(".badge-src { background: rgba(239, 68, 68, 0.15); color: var(--danger); border: 1px solid rgba(239, 68, 68, 0.3); }\n");
        sb.append(".badge-tgt { background: rgba(56, 189, 248, 0.15); color: var(--accent); border: 1px solid rgba(56, 189, 248, 0.3); }\n");
        sb.append("pre { background: #0b1120; border: 1px solid var(--border); border-radius: 8px; padding: 16px; overflow-x: auto; font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; color: #e2e8f0; }\n");
        sb.append("h2 { color: var(--text); font-size: 18px; margin-top: 32px; margin-bottom: 12px; border-left: 4px solid var(--accent); padding-left: 10px; }\n");
        sb.append(".footer { text-align: center; color: var(--muted); font-size: 12px; margin-top: 48px; border-top: 1px solid var(--border); padding-top: 16px; }\n");
        sb.append("</style>\n</head>\n<body>\n<div class=\"container\">\n");

        sb.append("<div class=\"header\">\n");
        sb.append("  <div>\n");
        sb.append("    <h1>⚡ SchemaSync Database Comparison Report</h1>\n");
        sb.append("    <div class=\"meta\"><strong>Source:</strong> ").append(escapeHtml(sourceDbName))
                .append(" &nbsp;⇄&nbsp; <strong>Target:</strong> ").append(escapeHtml(targetDbName)).append("</div>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"meta\" style=\"text-align: right;\">Generated: ").append(timestamp).append("<br/>SchemaSync v").append(Main.APP_VERSION).append("</div>\n");
        sb.append("</div>\n");

        sb.append("<h2>Executive Comparison Summary</h2>\n");

        // Metric Cards
        sb.append("<div class=\"cards\">\n");
        sb.append("  <div class=\"card\"><div class=\"num\" style=\"color: var(--accent);\">").append(total).append("</div><div class=\"lbl\">Total Tables</div></div>\n");
        sb.append("  <div class=\"card\"><div class=\"num\" style=\"color: var(--warning);\">").append(totalDiff).append("</div><div class=\"lbl\">Tables with Differences</div></div>\n");
        sb.append("  <div class=\"card\"><div class=\"num\" style=\"color: var(--success);\">").append(identical).append("</div><div class=\"lbl\">Identical Tables</div></div>\n");
        sb.append("  <div class=\"card\"><div class=\"num\" style=\"color: var(--danger);\">").append(onlySource).append("</div><div class=\"lbl\">Missing in Target</div></div>\n");
        sb.append("  <div class=\"card\"><div class=\"num\" style=\"color: var(--accent);\">").append(onlyTarget).append("</div><div class=\"lbl\">Missing in Source</div></div>\n");
        sb.append("</div>\n");

        // Data Diff Summary if present
        if (dataDiffSummary != null && !dataDiffSummary.trim().isEmpty()) {
            sb.append("<h2>📊 Live Data Diff Summary</h2>\n");
            sb.append("<p style=\"background: var(--card); border: 1px solid var(--border); padding: 12px 16px; border-radius: 6px; font-weight: 500;\">")
                    .append(escapeHtml(dataDiffSummary)).append("</p>\n");
        }

        // Tables Overview Table
        sb.append("<h2>🏗️ Tables Structure Overview</h2>\n");
        sb.append("<table>\n<thead><tr><th>Status</th><th>Table Name</th><th>Cols (Source)</th><th>Cols (Target)</th><th>Added</th><th>Removed</th><th>Modified</th><th>Summary</th></tr></thead>\n<tbody>\n");

        if (result != null) {
            for (TableDiff td : result.getTableDiffs()) {
                String badgeClass, badgeText;
                if (td.getType() == DifferenceType.IDENTICAL) {
                    badgeClass = "badge-ident"; badgeText = "IDENTICAL";
                } else if (td.getType() == DifferenceType.MODIFIED) {
                    badgeClass = "badge-mod"; badgeText = "MODIFIED";
                } else if (td.getType() == DifferenceType.REMOVED) {
                    badgeClass = "badge-src"; badgeText = "IN SOURCE ONLY";
                } else {
                    badgeClass = "badge-tgt"; badgeText = "IN TARGET ONLY";
                }

                int cA = td.getSourceTable() != null ? td.getSourceTable().getColumns().size() : 0;
                int cB = td.getTargetTable() != null ? td.getTargetTable().getColumns().size() : 0;
                int added = td.getAddedColumnsCount();
                int removed = td.getRemovedColumnsCount();
                int mod = td.getModifiedColumnsCount();

                sb.append("<tr>\n");
                sb.append("  <td><span class=\"badge ").append(badgeClass).append("\">").append(badgeText).append("</span></td>\n");
                sb.append("  <td><strong>").append(escapeHtml(td.getTableName())).append("</strong></td>\n");
                sb.append("  <td>").append(cA).append("</td>\n");
                sb.append("  <td>").append(cB).append("</td>\n");
                sb.append("  <td>").append(added > 0 ? ("+" + added) : "0").append("</td>\n");
                sb.append("  <td>").append(removed > 0 ? ("-" + removed) : "0").append("</td>\n");
                sb.append("  <td>").append(mod > 0 ? String.valueOf(mod) : "0").append("</td>\n");
                sb.append("  <td>").append(escapeHtml(buildTableDiffSummary(td, sourceDbName, targetDbName))).append("</td>\n");
                sb.append("</tr>\n");
            }
        }
        sb.append("</tbody>\n</table>\n");

        // Column Discrepancies Details for Modified Tables
        if (result != null && modified > 0) {
            sb.append("<h2>🔍 Detailed Column Discrepancies</h2>\n");
            for (TableDiff td : result.getTableDiffs()) {
                if (td.getType() != DifferenceType.MODIFIED) continue;
                List<ColumnDiff> colDiffs = td.getColumnDiffs();
                if (colDiffs == null || colDiffs.isEmpty()) continue;

                sb.append("<h3 style=\"color: var(--accent); margin-bottom: 8px;\">Table: ").append(escapeHtml(td.getTableName())).append("</h3>\n");
                sb.append("<table>\n<thead><tr><th>Change</th><th>Column Name</th><th>Source Type</th><th>Target Type</th><th>Details</th></tr></thead>\n<tbody>\n");
                for (ColumnDiff cd : colDiffs) {
                    if (cd.getType() == DifferenceType.IDENTICAL) continue;
                    String badgeClass = cd.getType() == DifferenceType.ADDED ? "badge-tgt" : (cd.getType() == DifferenceType.REMOVED ? "badge-src" : "badge-mod");
                    String sType = cd.getSourceColumn() != null ? cd.getSourceColumn().getTypeName() : "-";
                    String tType = cd.getTargetColumn() != null ? cd.getTargetColumn().getTypeName() : "-";

                    sb.append("<tr>\n");
                    sb.append("  <td><span class=\"badge ").append(badgeClass).append("\">").append(cd.getType()).append("</span></td>\n");
                    sb.append("  <td><code>").append(escapeHtml(cd.getColumnName())).append("</code></td>\n");
                    sb.append("  <td>").append(escapeHtml(sType)).append("</td>\n");
                    sb.append("  <td>").append(escapeHtml(tType)).append("</td>\n");
                    sb.append("  <td>").append(escapeHtml(cd.getDescription() != null ? cd.getDescription() : "")).append("</td>\n");
                    sb.append("</tr>\n");
                }
                sb.append("</tbody>\n</table>\n");
            }
        }

        // Migration DDL Script
        if (ddlScript != null && !ddlScript.trim().isEmpty()) {
            sb.append("<h2>📜 Generated Target Synchronization DDL Script</h2>\n");
            sb.append("<pre><code>").append(escapeHtml(ddlScript)).append("</code></pre>\n");
        }

        sb.append("<div class=\"footer\">Created & Developed by NSENGIYUMVA Christophe &bull; Copyright &copy; 2026 SchemaSync</div>\n");
        sb.append("</div>\n</body>\n</html>");
        return sb.toString();
    }

    public static String generateMarkdownReport(String sourceDbName, String targetDbName,
                                                SchemaDiffResult result, String ddlScript,
                                                String dataDiffSummary) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        StringBuilder sb = new StringBuilder();

        sb.append("# SchemaSync — Dual Database Comparison Report\n\n");
        sb.append("**Source Database:** `").append(sourceDbName).append("`  \n");
        sb.append("**Target Database:** `").append(targetDbName).append("`  \n");
        sb.append("**Generated:** ").append(timestamp).append("  \n\n");

        int total = result != null ? result.getTableDiffs().size() : 0;
        int identical = 0, modified = 0, onlySource = 0, onlyTarget = 0;
        if (result != null) {
            for (TableDiff td : result.getTableDiffs()) {
                if (td.getType() == DifferenceType.IDENTICAL) identical++;
                else if (td.getType() == DifferenceType.MODIFIED) modified++;
                else if (td.getType() == DifferenceType.REMOVED) onlySource++;
                else if (td.getType() == DifferenceType.ADDED) onlyTarget++;
            }
        }
        sb.append("### Summary Statistics\n");
        sb.append("- **Total Tables:** ").append(total).append("\n");
        sb.append("- **Differences Found:** ").append(modified + onlySource + onlyTarget).append("\n");
        sb.append("- **Identical Tables:** ").append(identical).append("\n");
        sb.append("- **Missing in Target:** ").append(onlySource).append("\n");
        sb.append("- **Missing in Source:** ").append(onlyTarget).append("\n\n");

        if (dataDiffSummary != null && !dataDiffSummary.trim().isEmpty()) {
            sb.append("### Data Diff Summary\n> ").append(dataDiffSummary).append("\n\n");
        }

        sb.append("### Table Structure Diff\n\n");
        sb.append("| Status | Table Name | Cols (Source) | Cols (Target) | Added | Removed | Modified |\n");
        sb.append("| :--- | :--- | :---: | :---: | :---: | :---: | :---: |\n");

        if (result != null) {
            for (TableDiff td : result.getTableDiffs()) {
                String status = td.getType().toString();
                int cA = td.getSourceTable() != null ? td.getSourceTable().getColumns().size() : 0;
                int cB = td.getTargetTable() != null ? td.getTargetTable().getColumns().size() : 0;
                sb.append("| ").append(status).append(" | `").append(td.getTableName()).append("` | ")
                        .append(cA).append(" | ").append(cB).append(" | ")
                        .append(td.getAddedColumnsCount()).append(" | ")
                        .append(td.getRemovedColumnsCount()).append(" | ")
                        .append(td.getModifiedColumnsCount()).append(" |\n");
            }
        }

        if (ddlScript != null && !ddlScript.trim().isEmpty()) {
            sb.append("\n### Target Synchronization DDL\n```sql\n").append(ddlScript).append("\n```\n");
        }

        return sb.toString();
    }

    public static String generateHtmlReport(SchemaDiffResult result, String sourceDbName, String targetDbName, String dataDiffSummary) {
        return generateHtmlReport(sourceDbName, targetDbName, result, null, dataDiffSummary);
    }

    public static String generateMarkdownReport(SchemaDiffResult result, String sourceDbName, String targetDbName, String dataDiffSummary) {
        return generateMarkdownReport(sourceDbName, targetDbName, result, null, dataDiffSummary);
    }

    public static void exportHtmlReport(File file, SchemaDiffResult result, String sourceDbName, String targetDbName, String dataDiffSummary) throws IOException {
        String html = generateHtmlReport(sourceDbName, targetDbName, result, null, dataDiffSummary);
        exportToFile(file, html);
    }

    public static void exportMarkdownReport(File file, SchemaDiffResult result, String sourceDbName, String targetDbName, String dataDiffSummary) throws IOException {
        String md = generateMarkdownReport(sourceDbName, targetDbName, result, null, dataDiffSummary);
        exportToFile(file, md);
    }

    public static void exportToFile(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String buildTableDiffSummary(TableDiff td, String srcLabel, String tgtLabel) {
        if (td.getType() == DifferenceType.IDENTICAL) return "Identical structure";
        if (td.getType() == DifferenceType.REMOVED) return "Missing in " + tgtLabel;
        if (td.getType() == DifferenceType.ADDED) return "Missing in " + srcLabel;
        StringBuilder sb = new StringBuilder();
        if (td.getAddedColumnsCount() > 0) sb.append("+").append(td.getAddedColumnsCount()).append(" cols ");
        if (td.getRemovedColumnsCount() > 0) sb.append("-").append(td.getRemovedColumnsCount()).append(" cols ");
        if (td.getModifiedColumnsCount() > 0) sb.append(td.getModifiedColumnsCount()).append(" modified");
        return sb.toString().trim();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
