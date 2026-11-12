package com.dbtool.core.diff;

import com.dbtool.core.query.RowData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Deterministic 32-bit and 256-bit (SHA-256) canonical row & chunk digest utility.
 */
public class RowHasher {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * Computes a canonical 32-bit hash for a row using normalized cell values.
     */
    public static int hashRow(RowData row) {
        if (row == null) return 0;
        int h = 1;
        for (int i = 0; i < row.size(); i++) {
            h = 31 * h + CanonicalValueNormalizer.canonicalEncode(row.getValue(i)).hashCode();
        }
        return h;
    }

    /**
     * Computes a collision-resistant 256-bit SHA-256 canonical digest over all columns of {@code row}.
     */
    public static String hashRowSha256(RowData row) {
        if (row == null) return sha256Hex("ROW:NULL");
        MessageDigest md = newSha256Digest();
        for (int i = 0; i < row.size(); i++) {
            String encoded = CanonicalValueNormalizer.canonicalEncode(row.getValue(i));
            md.update(encoded.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0x1F); // ASCII Unit Separator between columns
        }
        return toHex(md.digest());
    }

    /**
     * Computes a block-level 256-bit SHA-256 digest over an ordered list of row hashes in a chunk.
     */
    public static String hashChunkSha256(List<String> orderedRowHashes) {
        MessageDigest md = newSha256Digest();
        if (orderedRowHashes != null) {
            for (String rowHash : orderedRowHashes) {
                if (rowHash != null) {
                    md.update(rowHash.getBytes(StandardCharsets.UTF_8));
                }
                md.update((byte) '\n');
            }
        }
        return toHex(md.digest());
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest unavailable", e);
        }
    }

    private static String sha256Hex(String input) {
        MessageDigest md = newSha256Digest();
        return toHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX_CHARS[v >>> 4];
            out[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(out);
    }
}
