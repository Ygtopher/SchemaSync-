package com.dbtool.core.ai;

import java.net.http.HttpRequest;
import java.util.List;

public class OpenRouterLlmProvider extends OpenAiCompatibleLlmProvider {

    @Override
    public LlmProviderType getType() {
        return LlmProviderType.OPENROUTER;
    }

    @Override
    public String getDisplayName() {
        return "OpenRouter";
    }

    @Override
    public boolean requiresApiKey() {
        return true;
    }

    @Override
    public String getDefaultEndpoint() {
        return "https://openrouter.ai/api/v1/chat/completions";
    }

    @Override
    public List<String> getDefaultModels() {
        return List.of(
                "google/gemma-4-31b-it:free",
                "qwen/qwen3.8-27b:free",
                "openai/gpt-4o-mini"
        );
    }

    @Override
    protected void addExtraHeaders(HttpRequest.Builder builder) {
        builder.header("HTTP-Referer", "https://github.com/Ygtopher/SchemaSync-");
        builder.header("X-Title", "SchemaSync AI Assistant");
    }
}
