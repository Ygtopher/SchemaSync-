package com.dbtool.core.query;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class ValueFormatter {
    private static final ThreadLocal<SimpleDateFormat> ISO_DATE = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    private ValueFormatter() {}

    public static String format(Object val) {
        if (val == null) return "NULL";
        if (val instanceof byte[]) {
            byte[] bytes = (byte[]) val;
            StringBuilder sb = new StringBuilder("0x");
            for (byte b : bytes) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        }
        if (val instanceof Date) {
            return ISO_DATE.get().format((Date) val);
        }
        return val.toString();
    }
}
