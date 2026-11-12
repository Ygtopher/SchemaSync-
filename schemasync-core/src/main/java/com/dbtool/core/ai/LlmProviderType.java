package com.dbtool.core.ai;

public enum LlmProviderType {
    OPENROUTER("OpenRouter"),
    OPENAI("OpenAI"),
    GEMINI("Google Gemini"),
    ANTHROPIC("Anthropic Claude"),
    OLLAMA("Ollama (Local)");

    private final String displayName;

    LlmProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
