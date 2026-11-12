package com.dbtool.core.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * Service Provider Interface (SPI) for pluggable LLM backends loaded via {@link java.util.ServiceLoader}.
 */
public interface LlmProvider {
    LlmProviderType getType();
    String getDisplayName();
    boolean requiresApiKey();
    String getDefaultEndpoint();
    List<String> getDefaultModels();

    /**
     * Executes a chat completion request synchronously, optionally streaming incremental text
     * chunks to {@code deltaConsumer}.
     */
    LlmResponse chat(LlmRequest request, String apiKey, String endpointOverride, Consumer<String> deltaConsumer);

    default LlmResponse chat(LlmRequest request, String apiKey, String endpointOverride) {
        return chat(request, apiKey, endpointOverride, null);
    }
}
