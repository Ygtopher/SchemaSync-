package com.dbtool.ui.components;

import javax.swing.JTable;
import javax.swing.table.TableModel;

public class CustomTable extends JTable {
    public CustomTable(TableModel model) {
        super(model);
        setRowHeight(24);
        setShowGrid(true);
    }
}
