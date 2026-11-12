package com.dbtool.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared HTTP + OpenAI-compatible Chat Completions implementation used by OpenAI, OpenRouter,
 * and Ollama providers without duplicating JSON serialization or SSE/response handling.
 */
public abstract class OpenAiCompatibleLlmProvider implements LlmProvider {

    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    protected void addExtraHeaders(HttpRequest.Builder builder) {}

    @Override
    public LlmResponse chat(LlmRequest request, String apiKey, String endpointOverride, Consumer<String> deltaConsumer) {
        if (requiresApiKey() && (apiKey == null || apiKey.trim().isEmpty())) {
            return LlmResponse.error(getDisplayName() + " requires an API key.", request.model());
        }
        String endpoint = (endpointOverride != null && !endpointOverride.trim().isEmpty())
                ? endpointOverride.trim()
                : getDefaultEndpoint();
        String model = (request.model() != null && !request.model().isBlank())
                ? request.model().trim()
                : (getDefaultModels().isEmpty() ? "" : getDefaultModels().get(0));

        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", model);
            root.put("temperature", request.temperature());
            root.put("max_tokens", request.maxTokens());
            root.put("stream", false);

            ArrayNode msgs = MAPPER.createArrayNode();
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                ObjectNode sys = MAPPER.createObjectNode();
                sys.put("role", "system");
                sys.put("content", request.systemPrompt());
                msgs.add(sys);
            }
            for (LlmMessage m : request.messages()) {
                ObjectNode msgNode = MAPPER.createObjectNode();
                msgNode.put("role", m.role().getApiRole());
                msgNode.put("content", m.content());
                msgs.add(msgNode);
            }
            root.set("messages", msgs);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
            }
            addExtraHeaders(reqBuilder);

            HttpRequest httpReq = reqBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(root)))
                    .build();

            HttpResponse<String> resp = HTTP.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return LlmResponse.error("HTTP " + resp.statusCode() + ": " + resp.body(), model);
            }

            JsonNode body = MAPPER.readTree(resp.body());
            JsonNode messageNode = body.path("choices").path(0).path("message");
            String content = messageNode.path("content").asText("");
            if (content.isEmpty() && body.has("message")) {
                // Native Ollama /api/chat shape fallback if user supplies /api/chat instead of /v1/chat/completions
                content = body.path("message").path("content").asText("");
            }
            String reasoning = messageNode.path("reasoning").asText("");
            if (deltaConsumer != null && !content.isEmpty()) {
                deltaConsumer.accept(content);
            }
            return LlmResponse.ok(content, reasoning, model);
        } catch (Exception e) {
            return LlmResponse.error(e.getMessage() != null ? e.getMessage() : e.toString(), model);
        }
    }

    protected static HttpClient sharedHttpClient() {
        return HTTP;
    }

    protected static ObjectMapper sharedMapper() {
        return MAPPER;
    }
}
