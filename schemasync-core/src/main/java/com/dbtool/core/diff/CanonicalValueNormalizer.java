package com.dbtool.core.diff;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;

/**
 * Normalizes heterogeneous JDBC cell and primary-key values across database engines
 * (PostgreSQL, MySQL, SQLite, H2, MS Access) into canonical representations for
 * collision-free key matching and SHA-256 row/chunk hashing.
 */
public final class CanonicalValueNormalizer {

    private CanonicalValueNormalizer() {}

    /**
     * Converts any JDBC object into an immutable, value-comparable canonical object suitable
     * for {@link CompositeKey} hashing and equality checks.
     */
    public static Object canonicalizeKeyValue(Object val) {
        if (val == null) return null;
        if (val instanceof Byte || val instanceof Short || val instanceof Integer || val instanceof Long) {
            return Long.valueOf(((Number) val).longValue());
        }
        if (val instanceof Float || val instanceof Double) {
            double d = ((Number) val).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return Double.valueOf(d);
            if (d == 0.0d) d = 0.0d; // normalize -0.0
            return BigDecimal.valueOf(d).stripTrailingZeros();
        }
        if (val instanceof BigDecimal) {
            BigDecimal bd = ((BigDecimal) val).stripTrailingZeros();
            return bd.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : bd;
        }
        if (val instanceof Boolean) {
            return val;
        }
        if (val instanceof byte[]) {
            return Base64.getEncoder().encodeToString((byte[]) val);
        }
        if (val instanceof Timestamp || val instanceof Instant || val instanceof OffsetDateTime || val instanceof ZonedDateTime) {
            return formatTimestampIsoUtc(val);
        }
        if (val instanceof java.sql.Date || val instanceof LocalDate) {
            return val.toString();
        }
        return val.toString();
    }

    /**
     * Produces a deterministic, type-tagged string encoding for canonical SHA-256 hashing so
     * that values across SQL engines (e.g. {@code 10.50} vs {@code 10.5}, {@code 42} vs {@code 42L})
     * digest identically without delimiter ambiguity.
     */
    public static String canonicalEncode(Object val) {
        if (val == null) {
            return "N:";
        }
        if (val instanceof Boolean) {
            return "B:" + ((Boolean) val ? "1" : "0");
        }
        if (val instanceof Byte || val instanceof Short || val instanceof Integer || val instanceof Long) {
            return "I:" + ((Number) val).longValue();
        }
        if (val instanceof Float || val instanceof Double) {
            double d = ((Number) val).doubleValue();
            if (Double.isNaN(d)) return "F:NaN";
            if (Double.isInfinite(d)) return "F:" + (d > 0 ? "+Inf" : "-Inf");
            if (d == 0.0d) return "D:0";
            BigDecimal bd = BigDecimal.valueOf(d).stripTrailingZeros();
            return "D:" + bd.toPlainString();
        }
        if (val instanceof BigDecimal) {
            BigDecimal bd = ((BigDecimal) val).stripTrailingZeros();
            return "D:" + (bd.compareTo(BigDecimal.ZERO) == 0 ? "0" : bd.toPlainString());
        }
        if (val instanceof Timestamp || val instanceof Instant || val instanceof OffsetDateTime || val instanceof ZonedDateTime) {
            return "T:" + formatTimestampIsoUtc(val);
        }
        if (val instanceof LocalDateTime) {
            return "T:" + ((LocalDateTime) val).toInstant(ZoneOffset.UTC).toString();
        }
        if (val instanceof java.sql.Date || val instanceof LocalDate) {
            return "Y:" + val.toString();
        }
        if (val instanceof Time) {
            return "H:" + val.toString();
        }
        if (val instanceof Date) {
            return "T:" + Instant.ofEpochMilli(((Date) val).getTime()).toString();
        }
        if (val instanceof byte[]) {
            return "X:" + Base64.getEncoder().encodeToString((byte[]) val);
        }
        if (val instanceof Blob) {
            try {
                Blob b = (Blob) val;
                byte[] bytes = b.getBytes(1, (int) Math.min(b.length(), Integer.MAX_VALUE));
                return "X:" + Base64.getEncoder().encodeToString(bytes);
            } catch (SQLException e) {
                return "S:" + val.toString();
            }
        }
        if (val instanceof Clob) {
            try {
                Clob c = (Clob) val;
                String s = c.getSubString(1, (int) Math.min(c.length(), Integer.MAX_VALUE));
                return "S:" + s.length() + ":" + s;
            } catch (SQLException e) {
                return "S:" + val.toString();
            }
        }
        String str = val.toString();
        return "S:" + str.getBytes(StandardCharsets.UTF_8).length + ":" + str;
    }

    public static boolean areValuesEquivalent(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (Objects.equals(a, b)) return true;
        return canonicalEncode(a).equals(canonicalEncode(b));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compareCanonical(Object a, Object b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        Object ca = canonicalizeKeyValue(a);
        Object cb = canonicalizeKeyValue(b);
        if (Objects.equals(ca, cb)) return 0;
        if (ca instanceof Number && cb instanceof Number) {
            BigDecimal da = toBigDecimal((Number) ca);
            BigDecimal db = toBigDecimal((Number) cb);
            return da.compareTo(db);
        }
        if (ca.getClass().equals(cb.getClass()) && ca instanceof Comparable) {
            return ((Comparable) ca).compareTo(cb);
        }
        return String.valueOf(ca).compareTo(String.valueOf(cb));
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal) return (BigDecimal) n;
        if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte) {
            return BigDecimal.valueOf(n.longValue());
        }
        return BigDecimal.valueOf(n.doubleValue());
    }

    private static String formatTimestampIsoUtc(Object val) {
        Instant instant;
        if (val instanceof Timestamp) {
            instant = ((Timestamp) val).toInstant();
        } else if (val instanceof Instant) {
            instant = (Instant) val;
        } else if (val instanceof OffsetDateTime) {
            instant = ((OffsetDateTime) val).toInstant();
        } else {
            instant = ((ZonedDateTime) val).toInstant();
        }
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
