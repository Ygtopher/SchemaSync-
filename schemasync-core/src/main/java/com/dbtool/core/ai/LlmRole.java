package com.dbtool.core.ai;

public enum LlmRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String apiRole;

    LlmRole(String apiRole) {
        this.apiRole = apiRole;
    }

    public String getApiRole() {
        return apiRole;
    }
}
