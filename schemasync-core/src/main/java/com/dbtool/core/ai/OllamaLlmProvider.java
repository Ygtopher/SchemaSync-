package com.dbtool.core.ai;

import java.util.List;

public class OllamaLlmProvider extends OpenAiCompatibleLlmProvider {

    @Override
    public LlmProviderType getType() {
        return LlmProviderType.OLLAMA;
    }

    @Override
    public String getDisplayName() {
        return "Ollama (Local)";
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public String getDefaultEndpoint() {
        return "http://localhost:11434/v1/chat/completions";
    }

    @Override
    public List<String> getDefaultModels() {
        return List.of("qwen2.5-coder:7b", "llama3.1:8b", "deepseek-r1:8b");
    }
}
