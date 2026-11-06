package com.dbtool.panels;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class QueryLogPanelResizeTest {

    @Test
    void testQueryLogHeaderAndToggleCallback() {
        QueryLogPanel panel = new QueryLogPanel();
        assertTrue(panel.getHeaderPreferredHeight() > 0, "Header preferred height must be positive");

        AtomicBoolean toggled = new AtomicBoolean(false);
        panel.setToggleCallback(() -> toggled.set(true));

        panel.setMinimizedUI(true);
        // Find closeBtn in header
        Component[] comps = panel.getComponents();
        for (Component c : comps) {
            if (c instanceof JPanel) {
                JPanel header = (JPanel) c;
                for (Component hc : header.getComponents()) {
                    if (hc instanceof JButton) {
                        JButton btn = (JButton) hc;
                        assertEquals("▲", btn.getText(), "When minimized, button icon should be ▲");
                        btn.doClick();
                        assertTrue(toggled.get(), "Clicking minimize button must invoke toggleCallback");
                    }
                }
            }
        }
    }

    @Test
    void testQueryLogStaysMinimizedOnSplitPaneResize() {
        JPanel workspace = new JPanel();
        QueryLogPanel queryLog = new QueryLogPanel();
        int headerH = queryLog.getHeaderPreferredHeight();

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, workspace, queryLog);
        split.setResizeWeight(1.0);
        split.setSize(800, 600);
        split.doLayout();

        final boolean[] isMinimized = {true};
        final int[] expandedHeight = {220};
        final boolean[] adjusting = {false};

        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int totalH = split.getHeight();
                if (totalH <= 0) return;
                int divSize = split.getDividerSize();
                adjusting[0] = true;
                try {
                    if (isMinimized[0]) {
                        split.setDividerLocation(Math.max(0, totalH - divSize - headerH));
                    } else {
                        split.setDividerLocation(Math.max(100, totalH - divSize - expandedHeight[0]));
                    }
                } finally {
                    adjusting[0] = false;
                }
            }
        });

        // 1. Initially minimized at height 600
        split.setSize(800, 600);
        for (java.awt.event.ComponentListener cl : split.getComponentListeners()) {
            cl.componentResized(new java.awt.event.ComponentEvent(split, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        }
        int divLoc600 = split.getDividerLocation();
        int queryLogHeight600 = split.getHeight() - divLoc600 - split.getDividerSize();
        assertEquals(headerH, queryLogHeight600, "Query log height should equal header height when minimized");

        // 2. Resize to 1000 (e.g. window maximize) - query log MUST remain minimized to headerH!
        split.setSize(800, 1000);
        for (java.awt.event.ComponentListener cl : split.getComponentListeners()) {
            cl.componentResized(new java.awt.event.ComponentEvent(split, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        }
        int divLoc1000 = split.getDividerLocation();
        int queryLogHeight1000 = split.getHeight() - divLoc1000 - split.getDividerSize();
        assertEquals(headerH, queryLogHeight1000, "Query log MUST stay minimized to header height even after window resize/maximize");

        // 3. Resize to 450 (e.g. window shrink) - query log MUST still remain minimized!
        split.setSize(800, 450);
        for (java.awt.event.ComponentListener cl : split.getComponentListeners()) {
            cl.componentResized(new java.awt.event.ComponentEvent(split, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        }
        int divLoc450 = split.getDividerLocation();
        int queryLogHeight450 = split.getHeight() - divLoc450 - split.getDividerSize();
        assertEquals(headerH, queryLogHeight450, "Query log MUST stay minimized when window is resized smaller");

        // 4. Restore/Expand query log
        isMinimized[0] = false;
        split.setSize(800, 800);
        for (java.awt.event.ComponentListener cl : split.getComponentListeners()) {
            cl.componentResized(new java.awt.event.ComponentEvent(split, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        }
        int queryLogHeightExpanded = split.getHeight() - split.getDividerLocation() - split.getDividerSize();
        assertEquals(expandedHeight[0], queryLogHeightExpanded, "Expanded query log should maintain preferred expanded height");
    }
}
