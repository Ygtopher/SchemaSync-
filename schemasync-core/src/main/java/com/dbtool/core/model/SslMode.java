package com.dbtool.core.model;

public enum SslMode {
    DISABLE("disable"),
    ALLOW("allow"),
    PREFER("prefer"),
    REQUIRE("require"),
    VERIFY_CA("verify-ca"),
    VERIFY_FULL("verify-full");

    private final String value;

    SslMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
