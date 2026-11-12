package com.dbtool.util;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class TableTooltipUtil {
    public static void attachHeaderTooltips(JTable table) {
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int colIndex = header.columnAtPoint(e.getPoint());
                    if (colIndex >= 0) {
                        int modelIndex = table.convertColumnIndexToModel(colIndex);
                        TableModel m = table.getModel();
                        if (m instanceof TypedTableModel) {
                            String type = ((TypedTableModel) m).getColumnType(modelIndex);
                            if (type != null) {
                                header.setToolTipText(m.getColumnName(modelIndex) + " (" + type + ")");
                                return;
                            }
                        }
                    }
                    header.setToolTipText(null);
                }
            });
        }
    }
}
