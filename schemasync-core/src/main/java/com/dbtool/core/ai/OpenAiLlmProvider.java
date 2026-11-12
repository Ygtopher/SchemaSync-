package com.dbtool.core.ai;

import java.util.List;

public class OpenAiLlmProvider extends OpenAiCompatibleLlmProvider {

    @Override
    public LlmProviderType getType() {
        return LlmProviderType.OPENAI;
    }

    @Override
    public String getDisplayName() {
        return "OpenAI";
    }

    @Override
    public boolean requiresApiKey() {
        return true;
    }

    @Override
    public String getDefaultEndpoint() {
        return "https://api.openai.com/v1/chat/completions";
    }

    @Override
    public List<String> getDefaultModels() {
        return List.of("gpt-4o-mini", "gpt-4o", "o3-mini");
    }
}
