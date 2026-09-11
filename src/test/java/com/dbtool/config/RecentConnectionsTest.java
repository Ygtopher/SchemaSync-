package com.dbtool.config;

import com.dbtool.core.config.RecentConnectionsManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RecentConnectionsTest {
    @Test
    void testRecent() {
        RecentConnectionsManager r = new RecentConnectionsManager();
        r.addUrl("url1");
        r.addUrl("url2");
        assertEquals("url2", r.getRecentUrls().get(0));
    }
}
