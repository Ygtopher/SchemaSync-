package com.dbtool.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public class AnthropicLlmProvider implements LlmProvider {

    private static final ObjectMapper MAPPER = OpenAiCompatibleLlmProvider.sharedMapper();

    @Override
    public LlmProviderType getType() {
        return LlmProviderType.ANTHROPIC;
    }

    @Override
    public String getDisplayName() {
        return "Anthropic Claude";
    }

    @Override
    public boolean requiresApiKey() {
        return true;
    }

    @Override
    public String getDefaultEndpoint() {
        return "https://api.anthropic.com/v1/messages";
    }

    @Override
    public List<String> getDefaultModels() {
        return List.of("claude-sonnet-4-20250514", "claude-3-5-haiku-latest");
    }

    @Override
    public LlmResponse chat(LlmRequest request, String apiKey, String endpointOverride, Consumer<String> deltaConsumer) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return LlmResponse.error("Anthropic Claude requires an API key.", request.model());
        }
        String model = (request.model() != null && !request.model().isBlank())
                ? request.model().trim()
                : getDefaultModels().get(0);
        String endpoint = (endpointOverride != null && !endpointOverride.trim().isEmpty())
                ? endpointOverride.trim()
                : getDefaultEndpoint();

        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", model);
            root.put("max_tokens", request.maxTokens());
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                root.put("system", request.systemPrompt());
            }

            ArrayNode msgs = MAPPER.createArrayNode();
            for (LlmMessage msg : request.messages()) {
                if (msg.role() == LlmRole.SYSTEM) continue;
                ObjectNode m = MAPPER.createObjectNode();
                m.put("role", msg.role().getApiRole());
                m.put("content", msg.content());
                msgs.add(m);
            }
            root.set("messages", msgs);

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(root)))
                    .build();

            HttpResponse<String> resp = OpenAiCompatibleLlmProvider.sharedHttpClient()
                    .send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return LlmResponse.error("HTTP " + resp.statusCode() + ": " + resp.body(), model);
            }

            JsonNode json = MAPPER.readTree(resp.body());
            StringBuilder out = new StringBuilder();
            JsonNode blocks = json.path("content");
            if (blocks.isArray()) {
                for (JsonNode b : blocks) {
                    if ("text".equals(b.path("type").asText())) {
                        out.append(b.path("text").asText(""));
                    }
                }
            }
            String text = out.toString();
            if (deltaConsumer != null && !text.isEmpty()) {
                deltaConsumer.accept(text);
            }
            return LlmResponse.ok(text, "", model);
        } catch (Exception e) {
            return LlmResponse.error(e.getMessage() != null ? e.getMessage() : e.toString(), model);
        }
    }
}
