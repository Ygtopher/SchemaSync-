package com.dbtool.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cryptographic integrity & authenticity verification for SchemaSync release artifacts.
 * Verifies downloaded binaries against `SHA256SUMS` manifests and optional ECDSA (`SHA256withECDSA`) signatures.
 */
public final class ReleaseVerifier {

    public static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private ReleaseVerifier() {}

    public static String computeSha256Hex(File file) throws Exception {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Artifact file missing for SHA-256 verification: " + file);
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[32768];
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            int n;
            while ((n = bis.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Parses standard GNU `sha256sum` formatted text (`<64-hex>  <filename>` or `<64-hex> *<filename>`).
     */
    public static Map<String, String> parseSha256Sums(String content) {
        Map<String, String> map = new LinkedHashMap<>();
        if (content == null) return map;
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int spaceIdx = -1;
            for (int i = 0; i < line.length(); i++) {
                if (Character.isWhitespace(line.charAt(i))) {
                    spaceIdx = i;
                    break;
                }
            }
            if (spaceIdx <= 0) continue;
            String hash = line.substring(0, spaceIdx).trim().toLowerCase();
            String filePart = line.substring(spaceIdx).trim();
            if (filePart.startsWith("*")) {
                filePart = filePart.substring(1).trim();
            }
            // Normalize path separators to basename
            int lastSlash = Math.max(filePart.lastIndexOf('/'), filePart.lastIndexOf('\\'));
            if (lastSlash >= 0 && lastSlash + 1 < filePart.length()) {
                filePart = filePart.substring(lastSlash + 1);
            }
            if (hash.matches("[0-9a-f]{64}") && !filePart.isEmpty()) {
                map.put(filePart.toLowerCase(), hash);
            }
        }
        return map;
    }

    public static void verifyArtifactChecksum(File artifact, String expectedAssetName, Map<String, String> sha256Map) throws Exception {
        if (sha256Map == null || sha256Map.isEmpty()) {
            throw new SecurityException("Empty or missing SHA256SUMS manifest.");
        }
        String lookup = expectedAssetName != null ? expectedAssetName.trim().toLowerCase() : artifact.getName().toLowerCase();
        String expected = sha256Map.get(lookup);
        if (expected == null) {
            expected = sha256Map.get(artifact.getName().toLowerCase());
        }
        if (expected == null) {
            throw new SecurityException("SHA256SUMS manifest does not contain checksum for '" + expectedAssetName + "'.");
        }
        String actual = computeSha256Hex(artifact).toLowerCase();
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("SHA-256 verification failed for '" + expectedAssetName + "' (expected " + expected + ", got " + actual + ").");
        }
    }

    public static boolean verifyDetachedEcdsaSignature(byte[] data, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        if (data == null || signatureBytes == null || publicKey == null) return false;
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signatureBytes);
    }

    public static byte[] signDetachedEcdsa(byte[] data, PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    public static PublicKey decodeEcPublicKey(String base64X509Der) throws Exception {
        if (base64X509Der == null || base64X509Der.trim().isEmpty()) return null;
        byte[] keyBytes = Base64.getDecoder().decode(base64X509Der.trim());
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    public static byte[] decodeSignaturePayload(byte[] rawBytes) {
        if (rawBytes == null) return null;
        String text = new String(rawBytes, StandardCharsets.UTF_8).trim();
        // Support Base64-encoded signature files cleanly as well as raw DER bytes
        if (text.matches("^[A-Za-z0-9+/=\\r\\n]+$") && text.length() >= 32) {
            try {
                return Base64.getDecoder().decode(text.replaceAll("\\s+", ""));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return rawBytes;
    }
}
