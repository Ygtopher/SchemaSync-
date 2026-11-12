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

public class GeminiLlmProvider implements LlmProvider {

    private static final ObjectMapper MAPPER = OpenAiCompatibleLlmProvider.sharedMapper();

    @Override
    public LlmProviderType getType() {
        return LlmProviderType.GEMINI;
    }

    @Override
    public String getDisplayName() {
        return "Google Gemini";
    }

    @Override
    public boolean requiresApiKey() {
        return true;
    }

    @Override
    public String getDefaultEndpoint() {
        return "https://generativelanguage.googleapis.com/v1beta/models";
    }

    @Override
    public List<String> getDefaultModels() {
        return List.of("gemini-2.5-flash", "gemini-2.5-pro");
    }

    @Override
    public LlmResponse chat(LlmRequest request, String apiKey, String endpointOverride, Consumer<String> deltaConsumer) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return LlmResponse.error("Google Gemini requires an API key.", request.model());
        }
        String model = (request.model() != null && !request.model().isBlank())
                ? request.model().trim()
                : getDefaultModels().get(0);
        String base = (endpointOverride != null && !endpointOverride.trim().isEmpty())
                ? endpointOverride.trim().replaceAll("/+$", "")
                : getDefaultEndpoint();
        String url = base.endsWith(":generateContent") ? base : (base + "/" + model + ":generateContent");

        try {
            ObjectNode root = MAPPER.createObjectNode();
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                ObjectNode sysInst = MAPPER.createObjectNode();
                ArrayNode sysParts = MAPPER.createArrayNode();
                sysParts.add(MAPPER.createObjectNode().put("text", request.systemPrompt()));
                sysInst.set("parts", sysParts);
                root.set("system_instruction", sysInst);
            }

            ArrayNode contents = MAPPER.createArrayNode();
            for (LlmMessage msg : request.messages()) {
                ObjectNode turn = MAPPER.createObjectNode();
                turn.put("role", msg.role() == LlmRole.ASSISTANT ? "model" : "user");
                ArrayNode parts = MAPPER.createArrayNode();
                parts.add(MAPPER.createObjectNode().put("text", msg.content()));
                turn.set("parts", parts);
                contents.add(turn);
            }
            root.set("contents", contents);

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(root)))
                    .build();

            HttpResponse<String> resp = OpenAiCompatibleLlmProvider.sharedHttpClient()
                    .send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return LlmResponse.error("HTTP " + resp.statusCode() + ": " + resp.body(), model);
            }

            JsonNode json = MAPPER.readTree(resp.body());
            StringBuilder textOut = new StringBuilder();
            JsonNode parts = json.path("candidates").path(0).path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    textOut.append(part.path("text").asText(""));
                }
            }
            String text = textOut.toString();
            if (deltaConsumer != null && !text.isEmpty()) {
                deltaConsumer.accept(text);
            }
            return LlmResponse.ok(text, "", model);
        } catch (Exception e) {
            return LlmResponse.error(e.getMessage() != null ? e.getMessage() : e.toString(), model);
        }
    }
}
