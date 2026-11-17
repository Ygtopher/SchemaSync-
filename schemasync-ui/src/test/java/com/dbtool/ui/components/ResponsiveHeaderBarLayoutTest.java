package com.dbtool.ui.components;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class ResponsiveHeaderBarLayoutTest {

    @Test
    void testWideHeaderBarCentersSearchPillWithoutOverlap() {
        JPanel headerBar = new JPanel();
        ResponsiveHeaderBarLayout layout = new ResponsiveHeaderBarLayout(10, 110);
        headerBar.setLayout(layout);
        headerBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JPanel west = new JPanel();
        west.setPreferredSize(new Dimension(350, 30));

        JPanel center = new JPanel();
        center.setPreferredSize(new Dimension(220, 26));

        JPanel east = new JPanel();
        east.setPreferredSize(new Dimension(450, 30));

        headerBar.add(west, BorderLayout.WEST);
        headerBar.add(center, BorderLayout.CENTER);
        headerBar.add(east, BorderLayout.EAST);

        headerBar.setSize(1400, 42);
        headerBar.doLayout();

        assertTrue(west.isVisible());
        assertTrue(center.isVisible());
        assertTrue(east.isVisible());

        assertEquals(12, west.getX(), "West should anchor to left inset");
        assertEquals(350, west.getWidth());

        assertEquals(1400 - 12 - 450, east.getX(), "East should anchor to right inset");
        assertEquals(450, east.getWidth());

        // Center should be in visual center: (1400 - 220) / 2 = 590
        assertEquals(590, center.getX(), "Center should be placed at exact horizontal midpoint");
        assertEquals(220, center.getWidth());

        // Strictly verify zero overlap
        assertTrue(center.getX() >= west.getX() + west.getWidth() + 10, "Center must not overlap West");
        assertTrue(center.getX() + center.getWidth() <= east.getX() - 10, "Center must not overlap East");
    }

    @Test
    void testCompactHeaderBarClampsCenterWithoutOverlap() {
        JPanel headerBar = new JPanel();
        ResponsiveHeaderBarLayout layout = new ResponsiveHeaderBarLayout(10, 110);
        headerBar.setLayout(layout);
        headerBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JPanel west = new JPanel();
        west.setPreferredSize(new Dimension(360, 30));

        JPanel center = new JPanel();
        center.setPreferredSize(new Dimension(220, 26));

        JPanel east = new JPanel();
        east.setPreferredSize(new Dimension(450, 30));

        headerBar.add(west, BorderLayout.WEST);
        headerBar.add(center, BorderLayout.CENTER);
        headerBar.add(east, BorderLayout.EAST);

        // 1080px width: available between West (ending at 372) and East (starting at 618)
        headerBar.setSize(1080, 42);
        headerBar.doLayout();

        assertTrue(center.isVisible());

        // Strictly verify zero overlap
        assertTrue(center.getX() >= west.getX() + west.getWidth() + 10, "Center must not overlap West");
        assertTrue(center.getX() + center.getWidth() <= east.getX() - 10, "Center must not overlap East");
    }

    @Test
    void testNarrowHeaderBarHidesCenterCompletelyToPreventOverlap() {
        // Reproduces user's exact scenario: window resized to compact/split-screen size (e.g. 850px)
        JPanel headerBar = new JPanel();
        ResponsiveHeaderBarLayout layout = new ResponsiveHeaderBarLayout(10, 110);
        headerBar.setLayout(layout);
        headerBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JPanel west = new JPanel();
        west.setPreferredSize(new Dimension(380, 30));

        JPanel center = new JPanel();
        center.setPreferredSize(new Dimension(220, 26));

        JPanel east = new JPanel();
        east.setPreferredSize(new Dimension(460, 30));

        headerBar.add(west, BorderLayout.WEST);
        headerBar.add(center, BorderLayout.CENTER);
        headerBar.add(east, BorderLayout.EAST);

        // At 850px, West (380) + East (460) = 840px. Remaining space is < 110px.
        headerBar.setSize(850, 42);
        headerBar.doLayout();

        // In standard BorderLayout, Center would have negative width and bleed 100px+ over West.
        // With ResponsiveHeaderBarLayout, Center must be cleanly hidden to guarantee ZERO overlap.
        assertFalse(center.isVisible(), "Center search pill must hide when space is insufficient");
        assertEquals(0, center.getWidth(), "Center width must be 0 when hidden");

        // West and East must never overlap
        assertTrue(west.getX() + west.getWidth() <= east.getX(), "West must never overlap East");
    }

    @Test
    void testExtremeNarrowWindowBoundsWest() {
        JPanel headerBar = new JPanel();
        ResponsiveHeaderBarLayout layout = new ResponsiveHeaderBarLayout(10, 110);
        headerBar.setLayout(layout);
        headerBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JPanel west = new JPanel();
        west.setPreferredSize(new Dimension(400, 30));

        JPanel center = new JPanel();
        center.setPreferredSize(new Dimension(220, 26));

        JPanel east = new JPanel();
        east.setPreferredSize(new Dimension(350, 30));

        headerBar.add(west, BorderLayout.WEST);
        headerBar.add(center, BorderLayout.CENTER);
        headerBar.add(east, BorderLayout.EAST);

        headerBar.setSize(600, 42);
        headerBar.doLayout();

        // West must be clamped so it does not collide with East
        assertTrue(west.getX() + west.getWidth() <= east.getX() - 10, "West must be bounded before East");
        assertFalse(center.isVisible());
    }

    @Test
    void testResponsiveAdapterCallbackFired() {
        ResponsiveHeaderBarLayout layout = new ResponsiveHeaderBarLayout(10, 110);
        final int[] receivedWidth = new int[1];
        layout.setResponsiveAdapter(w -> receivedWidth[0] = w);

        JPanel headerBar = new JPanel(layout);
        headerBar.setSize(1024, 40);
        headerBar.doLayout();

        assertEquals(1024, receivedWidth[0]);
    }
}
