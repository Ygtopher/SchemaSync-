package com.dbtool.ui.components;

import java.awt.*;

/**
 * Enterprise responsive layout manager for the SchemaSync application header bar.
 * Prevents overlapping between West (Brand/Session Tabs), Center (Quick Jump search pill),
 * and East (Action buttons) across all window widths, DPI scalings, and split-screen snaps.
 *
 * Guaranteed Invariants:
 * - West and East NEVER collide or overlap.
 * - Center is placed in the visual horizontal center whenever space permits, and smoothly
 *   compresses down to its minimum width.
 * - If available width between West and East is insufficient for Center, Center is cleanly
 *   hidden rather than overlapping West or East.
 * - Fires an optional {@link ResponsiveAdapter} callback before layout calculation so components
 *   can adapt their labels and compactness dynamically.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class ResponsiveHeaderBarLayout implements LayoutManager2 {

    @FunctionalInterface
    public interface ResponsiveAdapter {
        void onLayout(int availableWidth);
    }

    private Component west;
    private Component center;
    private Component east;
    private final int hgap;
    private final int minCenterWidth;
    private ResponsiveAdapter responsiveAdapter;

    public ResponsiveHeaderBarLayout(int hgap) {
        this(hgap, 110);
    }

    public ResponsiveHeaderBarLayout(int hgap, int minCenterWidth) {
        this.hgap = Math.max(0, hgap);
        this.minCenterWidth = Math.max(40, minCenterWidth);
    }

    public void setResponsiveAdapter(ResponsiveAdapter adapter) {
        this.responsiveAdapter = adapter;
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        synchronized (comp.getTreeLock()) {
            if (constraints == null || BorderLayout.CENTER.equals(constraints) || "Center".equalsIgnoreCase(String.valueOf(constraints))) {
                center = comp;
            } else if (BorderLayout.WEST.equals(constraints) || "West".equalsIgnoreCase(String.valueOf(constraints))) {
                west = comp;
            } else if (BorderLayout.EAST.equals(constraints) || "East".equalsIgnoreCase(String.valueOf(constraints))) {
                east = comp;
            }
        }
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        addLayoutComponent(comp, name);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        synchronized (comp.getTreeLock()) {
            if (comp == west) west = null;
            if (comp == center) center = null;
            if (comp == east) east = null;
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets ins = parent.getInsets();
            int w = ins.left + ins.right;
            int h = 0;
            if (west != null && west.isVisible()) {
                Dimension d = west.getPreferredSize();
                w += d.width + hgap;
                h = Math.max(h, d.height);
            }
            if (center != null && center.isVisible()) {
                Dimension d = center.getPreferredSize();
                w += d.width + hgap;
                h = Math.max(h, d.height);
            }
            if (east != null && east.isVisible()) {
                Dimension d = east.getPreferredSize();
                w += d.width;
                h = Math.max(h, d.height);
            }
            return new Dimension(w, h + ins.top + ins.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets ins = parent.getInsets();
            int w = ins.left + ins.right;
            int h = 0;
            if (west != null && west.isVisible()) {
                Dimension d = west.getMinimumSize();
                w += d.width;
                h = Math.max(h, d.height);
            }
            if (east != null && east.isVisible()) {
                Dimension d = east.getMinimumSize();
                w += (w > ins.left + ins.right ? hgap : 0) + d.width;
                h = Math.max(h, d.height);
            }
            return new Dimension(w, h + ins.top + ins.bottom);
        }
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {}

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets ins = parent.getInsets();
            int totalW = parent.getWidth();
            int totalH = parent.getHeight();
            int availW = totalW - ins.left - ins.right;
            int availH = totalH - ins.top - ins.bottom;

            if (availW <= 0 || availH <= 0) return;

            // Trigger responsive adapter callback so components can adapt button text/visibilities
            if (responsiveAdapter != null) {
                responsiveAdapter.onLayout(totalW);
            }

            // 1. Position East section (anchored right)
            int eastW = 0;
            int eastX = totalW - ins.right;
            if (east != null && east.isVisible()) {
                Dimension d = east.getPreferredSize();
                eastW = Math.min(d.width, availW);
                eastX = totalW - ins.right - eastW;
                int eastY = ins.top + Math.max(0, (availH - d.height) / 2);
                east.setBounds(eastX, eastY, eastW, Math.min(d.height, availH));
            }

            // 2. Position West section (anchored left, bounded by East to prevent any collision)
            int westW = 0;
            int westX = ins.left;
            if (west != null && west.isVisible()) {
                Dimension d = west.getPreferredSize();
                int maxAllowedWest = Math.max(0, eastX - westX - hgap);
                westW = Math.min(d.width, maxAllowedWest);
                int westY = ins.top + Math.max(0, (availH - d.height) / 2);
                west.setBounds(westX, westY, westW, Math.min(d.height, availH));
            }
            int westEndX = westX + westW;

            // 3. Position Center section (search pill / quick jump)
            if (center != null) {
                int minX = westW > 0 ? (westEndX + hgap) : ins.left;
                int maxX = eastW > 0 ? (eastX - hgap) : (totalW - ins.right);
                int availCenter = maxX - minX;

                if (availCenter < minCenterWidth) {
                    // Not enough space: hide center completely so it NEVER overlaps West or East
                    center.setBounds(0, 0, 0, 0);
                    center.setVisible(false);
                } else {
                    center.setVisible(true);
                    Dimension pref = center.getPreferredSize();
                    int cW = Math.min(pref.width, availCenter);

                    // Ideal visual position is the exact horizontal center of the header bar
                    int idealX = (totalW - cW) / 2;

                    // Clamp so it NEVER overlaps West or East
                    int actualX = Math.max(minX, Math.min(idealX, maxX - cW));
                    int actualH = Math.min(pref.height > 0 ? pref.height : availH, availH);
                    int actualY = ins.top + Math.max(0, (availH - actualH) / 2);

                    center.setBounds(actualX, actualY, cW, actualH);
                }
            }
        }
    }

    public Component getWest() { return west; }
    public Component getCenter() { return center; }
    public Component getEast() { return east; }
    public int getHgap() { return hgap; }
    public int getMinCenterWidth() { return minCenterWidth; }
}
