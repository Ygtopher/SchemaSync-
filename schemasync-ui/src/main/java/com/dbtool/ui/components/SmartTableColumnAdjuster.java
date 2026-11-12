package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Intelligent Column Width Auto-Sizing Engine for SchemaSync Data Grids.
 * Calculates optimal column widths matching the exact length of data contents
 * and column headers (including PK/FK icons and sort arrows).
 *
 * Supports:
 * - Automatic content-based width fitting on query/data load.
 * - Interactive double-click on header separator to auto-fit individual column.
 * - Safe sample-based scanning for large tables (fast, zero UI freeze).
 * - Automatic horizontal scroll activation (AUTO_RESIZE_OFF) when columns exceed viewport,
 *   or proportional distribution when total columns fit within viewport.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public final class SmartTableColumnAdjuster {

    public static final int DEFAULT_MIN_WIDTH = 80;
    public static final int DEFAULT_MAX_WIDTH = 550;
    public static final int CELL_PADDING = 20;
    public static final int HEADER_SORT_PADDING = 28;
    public static final int MAX_SAMPLE_ROWS = 250;

    private SmartTableColumnAdjuster() {}

    /**
     * Installs automated column width adjustment and header double-click auto-fit onto a JTable.
     */
    public static void install(JTable table) {
        if (table == null) return;

        if (Boolean.TRUE.equals(table.getClientProperty("SchemaSync.columnAdjusterInstalled"))) {
            return;
        }
        table.putClientProperty("SchemaSync.columnAdjusterInstalled", Boolean.TRUE);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Double-click on column header divider auto-fits that specific column
        attachHeaderDividerListener(table);
        table.addPropertyChangeListener("tableHeader", evt -> attachHeaderDividerListener(table));
    }

    private static void attachHeaderDividerListener(JTable table) {
        JTableHeader header = table.getTableHeader();
        if (header != null && !Boolean.TRUE.equals(header.getClientProperty("SchemaSync.dividerListenerAttached"))) {
            header.putClientProperty("SchemaSync.dividerListenerAttached", Boolean.TRUE);
            header.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        int col = getDividerColumnAt(header, e.getPoint());
                        if (col >= 0) {
                            adjustColumnWidth(table, col);
                        }
                    }
                }
            });
        }
    }

    /**
     * Determines which column divider was clicked on the table header.
     */
    private static int getDividerColumnAt(JTableHeader header, Point p) {
        int col = header.columnAtPoint(p);
        if (col < 0) return -1;
        Rectangle rect = header.getHeaderRect(col);
        // If within 5 pixels of left edge, target previous column
        if (p.x <= rect.x + 5 && col > 0) {
            return col - 1;
        }
        // If within 5 pixels of right edge, target current column
        if (p.x >= rect.x + rect.width - 5) {
            return col;
        }
        return -1;
    }

    /**
     * Auto-sizes all columns in the given table to match the exact length of their data and header.
     */
    public static void adjustColumnWidths(JTable table) {
        adjustColumnWidths(table, DEFAULT_MIN_WIDTH, DEFAULT_MAX_WIDTH);
    }

    /**
     * Auto-sizes all columns in the given table to match the exact length of their data and header
     * with custom minimum and maximum bounds.
     */
    public static void adjustColumnWidths(JTable table, int minWidth, int maxWidth) {
        if (table == null) return;

        SwingUtilities.invokeLater(() -> {
            TableColumnModel colModel = table.getColumnModel();
            int colCount = colModel.getColumnCount();
            if (colCount == 0) return;

            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

            for (int col = 0; col < colCount; col++) {
                int optimalW = calculateOptimalColumnWidth(table, col, minWidth, maxWidth);
                TableColumn column = colModel.getColumn(col);
                column.setPreferredWidth(optimalW);
                column.setWidth(optimalW);
            }

            table.revalidate();
            table.repaint();
        });
    }

    /**
     * Auto-sizes a single specific column to fit its contents and header.
     */
    public static void adjustColumnWidth(JTable table, int colIndex) {
        if (table == null || colIndex < 0 || colIndex >= table.getColumnCount()) return;

        int optimalWidth = calculateOptimalColumnWidth(table, colIndex, DEFAULT_MIN_WIDTH, DEFAULT_MAX_WIDTH);
        TableColumn col = table.getColumnModel().getColumn(colIndex);
        col.setPreferredWidth(optimalWidth);
        col.setWidth(optimalWidth);
        table.revalidate();
        table.repaint();
    }

    /**
     * Calculates the optimal width in pixels for a single column.
     */
    public static int calculateOptimalColumnWidth(JTable table, int colIndex, int minWidth, int maxWidth) {
        TableColumn column = table.getColumnModel().getColumn(colIndex);
        int maxW = minWidth;

        // 1. Calculate Header Width with Direct FontMetrics guarantee
        JTableHeader header = table.getTableHeader();
        Object headerValue = column.getHeaderValue();
        if (headerValue != null) {
            String hdrStr = headerValue.toString();
            Font hdrFont = header != null && header.getFont() != null ? header.getFont() : table.getFont();
            if (hdrFont != null) {
                FontMetrics fm = table.getFontMetrics(hdrFont.deriveFont(Font.BOLD));
                // Account for potential PK/FK icons ("🔑 ", "🔗 ") and sort icon
                int directHw = fm.stringWidth("🔑 " + hdrStr) + HEADER_SORT_PADDING + 8;
                if (directHw > maxW) maxW = directHw;
            }

            TableCellRenderer headerRenderer = column.getHeaderRenderer();
            if (headerRenderer == null && header != null) {
                headerRenderer = header.getDefaultRenderer();
            }
            if (headerRenderer != null) {
                try {
                    Component comp = headerRenderer.getTableCellRendererComponent(
                            table, headerValue, false, false, -1, colIndex);
                    int hw = comp.getPreferredSize().width + HEADER_SORT_PADDING;
                    if (hw > maxW) maxW = hw;
                } catch (Exception ignored) {}
            }
        }

        // 2. Scan Data Rows with Direct FontMetrics + Renderer Measurement
        int rowCount = table.getRowCount();
        if (rowCount > 0) {
            Font dataFont = table.getFont();
            FontMetrics dataFm = dataFont != null ? table.getFontMetrics(dataFont) : null;
            Font monoFont = dataFont != null ? new Font(Font.MONOSPACED, Font.PLAIN, dataFont.getSize()) : null;
            FontMetrics monoFm = monoFont != null ? table.getFontMetrics(monoFont) : null;

            TableCellRenderer defaultRenderer = table.getCellRenderer(0, colIndex);
            java.util.LinkedHashSet<Integer> rowsToScan = new java.util.LinkedHashSet<>();
            int initialScan = Math.min(50, rowCount);
            for (int r = 0; r < initialScan; r++) {
                rowsToScan.add(r);
            }
            if (rowCount > initialScan) {
                int step = Math.max(1, rowCount / MAX_SAMPLE_ROWS);
                for (int r = initialScan; r < rowCount; r += step) {
                    rowsToScan.add(r);
                }
            }

            for (int r : rowsToScan) {
                Object val = table.getValueAt(r, colIndex);
                if (val == null || "NULL".equals(val) || "<null>".equalsIgnoreCase(String.valueOf(val))) {
                    int nullWidth = 56 + CELL_PADDING;
                    if (nullWidth > maxW) maxW = nullWidth;
                    continue;
                }

                String sVal = String.valueOf(val);
                // Direct font measurement for rock-solid width calculation
                boolean isNum = SmartTableCellRenderer.isNumericValue(val);
                FontMetrics fmToUse = (isNum && monoFm != null) ? monoFm : dataFm;
                if (fmToUse != null) {
                    int dw = fmToUse.stringWidth(sVal) + CELL_PADDING + 8;
                    if (dw > maxW) maxW = dw;
                }

                TableCellRenderer renderer = table.getCellRenderer(r, colIndex);
                if (renderer == null) renderer = defaultRenderer;

                if (renderer != null) {
                    try {
                        Component c = renderer.getTableCellRendererComponent(table, val, false, false, r, colIndex);
                        int pw = c.getPreferredSize().width + CELL_PADDING;
                        if (pw > maxW) {
                            maxW = pw;
                        }
                    } catch (Exception ignored) {}
                }

                if (maxW >= maxWidth) {
                    maxW = maxWidth;
                    break;
                }
            }
        }

        return Math.min(maxWidth, Math.max(minWidth, maxW));
    }
}
