package com.dbtool.ui;

import com.dbtool.core.query.PagedResultWindow;
import com.dbtool.util.TypedTableModel;

/**
 * Bounded virtualized Swing {@link javax.swing.table.TableModel} extending {@link TypedTableModel}
 * ({@link javax.swing.table.DefaultTableModel}) so existing grid renderers, sorters, exporters, and
 * editors work seamlessly while keeping at most {@link PagedResultWindow#DEFAULT_MAX_CACHED_ROWS}
 * rows in heap memory.
 */
public class VirtualResultTableModel extends TypedTableModel {

    private final PagedResultWindow window;

    public VirtualResultTableModel(PagedResultWindow window, String[] columnTypes) {
        super();
        if (window == null) {
            throw new IllegalArgumentException("PagedResultWindow must not be null");
        }
        this.window = window;
        setColumnIdentifiers(window.getColumnNames().toArray());
        setColumnTypes(columnTypes);
    }

    @Override
    public int getRowCount() {
        return window != null ? window.getTotalRowCount() : 0;
    }

    @Override
    public int getColumnCount() {
        return window != null ? window.getColumnCount() : 0;
    }

    @Override
    public String getColumnName(int column) {
        if (window == null || column < 0 || column >= window.getColumnCount()) {
            return super.getColumnName(column);
        }
        return window.getColumnNames().get(column);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return window.getValueAt(rowIndex, columnIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        window.updateCachedCell(rowIndex, columnIndex, aValue);
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    public int getCachedRowCount() {
        return window.getCachedRowCount();
    }

    public int getCachedPageCount() {
        return window.getCachedPageCount();
    }

    public void invalidateCache() {
        window.invalidateCache();
        fireTableDataChanged();
    }

    public PagedResultWindow getWindow() {
        return window;
    }
}
