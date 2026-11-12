package com.dbtool.core.diff;

public final class NumericComparator {
    public static final double DEFAULT_EPSILON = 1e-6;

    private NumericComparator() {}

    public static boolean equalsWithEpsilon(Object a, Object b, double epsilon) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            double d1 = ((Number) a).doubleValue();
            double d2 = ((Number) b).doubleValue();
            return Math.abs(d1 - d2) <= epsilon;
        }
        return a.equals(b);
    }
}
