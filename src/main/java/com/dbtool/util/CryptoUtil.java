package com.dbtool.util;

import java.util.Base64;

public final class CryptoUtil {
    private static final byte XOR_KEY = 0x5A;

    private CryptoUtil() {}

    public static String obfuscate(String input) {
        if (input == null) return null;
        byte[] bytes = input.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= XOR_KEY;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String deobfuscate(String encoded) {
        if (encoded == null) return null;
        byte[] bytes = Base64.getDecoder().decode(encoded);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= XOR_KEY;
        }
        return new String(bytes);
    }
}
