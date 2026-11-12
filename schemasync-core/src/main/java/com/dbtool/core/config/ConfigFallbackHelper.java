package com.dbtool.core.config;

public class ConfigFallbackHelper {
    public static AppSettings getSafeSettings(AppSettings s) {
        return s != null ? s : new AppSettings();
    }
}
