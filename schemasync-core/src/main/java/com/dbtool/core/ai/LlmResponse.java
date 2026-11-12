package com.dbtool.core.ai;

public record LlmResponse(
        boolean success,
        String content,
        String reasoning,
        String errorMessage,
        String modelUsed
) {
    public static LlmResponse ok(String content, String reasoning, String modelUsed) {
        return new LlmResponse(true, content, reasoning, null, modelUsed);
    }

    public static LlmResponse error(String errorMessage, String modelUsed) {
        return new LlmResponse(false, "", "", errorMessage, modelUsed);
    }
}
