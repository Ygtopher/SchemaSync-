package com.dbtool.util;

import javax.swing.table.DefaultTableModel;
import java.util.Vector;

public class TypedTableModel extends DefaultTableModel {
    private String[] columnTypes;

    public TypedTableModel() {
        super();
    }

    public TypedTableModel(Vector<Vector<Object>> data, Vector<String> columnNames, String[] columnTypes) {
        super(data, columnNames);
        this.columnTypes = columnTypes;
    }

    public String getColumnType(int colIndex) {
        if (columnTypes != null && colIndex >= 0 && colIndex < columnTypes.length) {
            return columnTypes[colIndex];
        }
        return null;
    }

    public void setColumnTypes(String[] columnTypes) {
        this.columnTypes = columnTypes;
    }
}
