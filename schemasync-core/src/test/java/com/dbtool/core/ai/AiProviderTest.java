package com.dbtool.core.ai;

import com.dbtool.util.CryptoUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AiProviderTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        CryptoUtil.setConfigDirOverrideForTesting(null);
    }

    @Test
    void testServiceLoaderDiscoversAllFiveProviders() {
        LlmProviderRegistry reg = LlmProviderRegistry.getInstance();
        assertEquals(5, reg.getAllProviders().size());
        assertNotNull(reg.getProvider(LlmProviderType.OPENROUTER));
        assertNotNull(reg.getProvider(LlmProviderType.OPENAI));
        assertNotNull(reg.getProvider(LlmProviderType.GEMINI));
        assertNotNull(reg.getProvider(LlmProviderType.ANTHROPIC));
        assertNotNull(reg.getProvider(LlmProviderType.OLLAMA));
        assertFalse(reg.getProvider(LlmProviderType.OLLAMA).requiresApiKey());
    }

    @Test
    void testLocalMockHttpServersForOllamaGeminiAndAnthropic() throws Exception {
        AtomicReference<String> lastGeminiKey = new AtomicReference<>();
        AtomicReference<String> lastAnthropicKey = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] resp = "{\"choices\":[{\"message\":{\"content\":\"SELECT count(*) FROM users;\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            lastGeminiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] resp = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"CREATE INDEX idx_u ON users(email);\"}]}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/v1/messages", exchange -> {
            lastAnthropicKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] resp = "{\"content\":[{\"type\":\"text\",\"text\":\"EXPLAIN ANALYZE SELECT 1;\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            LlmRequest req = new LlmRequest("qwen2.5-coder:7b", "sys", List.of(new LlmMessage(LlmRole.USER, "count users")));

            LlmProvider ollama = LlmProviderRegistry.getInstance().getProvider(LlmProviderType.OLLAMA);
            LlmResponse ollamaResp = ollama.chat(req, "", "http://127.0.0.1:" + port + "/v1/chat/completions");
            assertTrue(ollamaResp.success());
            assertEquals("SELECT count(*) FROM users;", ollamaResp.content());

            LlmProvider gemini = LlmProviderRegistry.getInstance().getProvider(LlmProviderType.GEMINI);
            LlmResponse geminiResp = gemini.chat(
                    new LlmRequest("gemini-2.5-flash", "sys", List.of(new LlmMessage(LlmRole.USER, "idx"))),
                    "gem-test-key", "http://127.0.0.1:" + port + "/v1beta/models");
            assertTrue(geminiResp.success());
            assertEquals("gem-test-key", lastGeminiKey.get());
            assertEquals("CREATE INDEX idx_u ON users(email);", geminiResp.content());

            LlmProvider anthropic = LlmProviderRegistry.getInstance().getProvider(LlmProviderType.ANTHROPIC);
            LlmResponse claudeResp = anthropic.chat(
                    new LlmRequest("claude-sonnet-4-20250514", "sys", List.of(new LlmMessage(LlmRole.USER, "explain"))),
                    "ant-test-key", "http://127.0.0.1:" + port + "/v1/messages");
            assertTrue(claudeResp.success());
            assertEquals("ant-test-key", lastAnthropicKey.get());
            assertEquals("EXPLAIN ANALYZE SELECT 1;", claudeResp.content());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testSchemaContextConsentToggleAndAiKeyVaultEncryption() throws Exception {
        CryptoUtil.setConfigDirOverrideForTesting(tempDir);
        AiKeyVault.saveApiKey(LlmProviderType.GEMINI, "AIza-secret-demo-key");
        String storedJson = Files.readString(tempDir.resolve("ai_keys.v2.json"), StandardCharsets.UTF_8);
        assertFalse(storedJson.contains("AIza-secret-demo-key"), "API key must never be persisted in plaintext");
        assertEquals("AIza-secret-demo-key", AiKeyVault.loadApiKey(LlmProviderType.GEMINI));

        String url = "jdbc:sqlite:" + tempDir.resolve("consent.db");
        try (Connection conn = DriverManager.getConnection(url); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, ssn TEXT)");
            st.executeUpdate("INSERT INTO customers VALUES (1, 'SENSITIVE-999-888')");

            String schemaOnly = SchemaContextBuilder.buildContext(conn, false);
            assertTrue(schemaOnly.toLowerCase().contains("customers"));
            assertTrue(schemaOnly.toLowerCase().contains("ssn"));
            assertFalse(schemaOnly.contains("SENSITIVE-999-888"), "Schema-Only mode must NEVER include table rows");

            String withSamples = SchemaContextBuilder.buildContext(conn, true);
            assertTrue(withSamples.contains("SENSITIVE-999-888"), "Opt-In sample mode should include sample row");
        }
    }
}
