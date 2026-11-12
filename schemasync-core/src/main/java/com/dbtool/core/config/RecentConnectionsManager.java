package com.dbtool.core.config;

import java.util.ArrayList;
import java.util.List;

public class RecentConnectionsManager {
    private final List<String> recentUrls = new ArrayList<>();

    public void addUrl(String url) {
        recentUrls.remove(url);
        recentUrls.add(0, url);
        if (recentUrls.size() > 10) recentUrls.remove(recentUrls.size() - 1);
    }

    public List<String> getRecentUrls() { return recentUrls; }
}
