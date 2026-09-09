package com.dbtool.core.script;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.RowData;

public class GsetHandler {
    public static void applyGset(QueryResult qr, String prefix, VariableContext context) {
        if (qr == null || qr.getRows().isEmpty()) return;
        RowData firstRow = qr.getRows().get(0);
        for (int i = 0; i < qr.getColumnNames().size(); i++) {
            String colName = qr.getColumnNames().get(i);
            String varName = (prefix != null && !prefix.isEmpty()) ? prefix + colName : colName;
            context.set(varName, firstRow.getValue(i));
        }
    }
}
