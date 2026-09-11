package com.dbtool.core.config;

import java.io.File;

public class SettingsManager {
    private static final SettingsManager INSTANCE = new SettingsManager();
    private AppSettings settings = new AppSettings();

    private SettingsManager() {}

    public static SettingsManager getInstance() { return INSTANCE; }
    public AppSettings getSettings() { return settings; }
    public void setSettings(AppSettings settings) { this.settings = settings; }
}
