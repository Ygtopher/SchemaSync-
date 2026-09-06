package com.dbtool.core.query;

public final class ValueFormatter {
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
        return val.toString();
    }
}
