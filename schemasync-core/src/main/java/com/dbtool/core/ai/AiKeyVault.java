package com.dbtool.core.ai;

import com.dbtool.util.CryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encrypts AI provider API keys at rest via {@link CryptoUtil} (OS Keyring via {@link com.dbtool.util.SecretStore})
 * under {@code ~/.schemasync/ai_keys.v2.json}.
 */
public final class AiKeyVault {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private AiKeyVault() {}

    public static void saveApiKey(LlmProviderType providerType, String apiKey) {
        if (providerType == null) return;
        Map<String, String> keys = loadEncryptedMap();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            keys.remove(providerType.name());
        } else {
            keys.put(providerType.name(), CryptoUtil.encryptPassword(apiKey.trim()));
        }
        writeEncryptedMap(keys);
    }

    public static String loadApiKey(LlmProviderType providerType) {
        if (providerType == null) return "";
        Map<String, String> keys = loadEncryptedMap();
        String encrypted = keys.get(providerType.name());
        if (encrypted == null || encrypted.isEmpty()) {
            return envFallback(providerType);
        }
        String decrypted = CryptoUtil.decryptPassword(encrypted);
        return (decrypted != null && !decrypted.isEmpty()) ? decrypted : envFallback(providerType);
    }

    private static String envFallback(LlmProviderType providerType) {
        String envName = switch (providerType) {
            case OPENROUTER -> "OPENROUTER_API_KEY";
            case OPENAI -> "OPENAI_API_KEY";
            case GEMINI -> "GEMINI_API_KEY";
            case ANTHROPIC -> "ANTHROPIC_API_KEY";
            case OLLAMA -> null;
        };
        if (envName == null) return "";
        String val = System.getenv(envName);
        return val != null ? val : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadEncryptedMap() {
        Path file = CryptoUtil.getConfigDir().resolve("ai_keys.v2.json");
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(MAPPER.readValue(file.toFile(), Map.class));
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private static void writeEncryptedMap(Map<String, String> map) {
        try {
            Path dir = CryptoUtil.getConfigDir();
            Files.createDirectories(dir);
            MAPPER.writeValue(dir.resolve("ai_keys.v2.json").toFile(), map);
        } catch (IOException ignored) {}
    }
}
