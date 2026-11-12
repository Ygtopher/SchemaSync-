package com.dbtool.core.ai;

import java.util.Collections;
import java.util.List;

public record LlmRequest(
        String model,
        String systemPrompt,
        List<LlmMessage> messages,
        double temperature,
        int maxTokens
) {
    public LlmRequest(String model, String systemPrompt, List<LlmMessage> messages) {
        this(model, systemPrompt, messages != null ? List.copyOf(messages) : Collections.emptyList(), 0.2, 2048);
    }
}
