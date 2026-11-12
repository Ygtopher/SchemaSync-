package com.dbtool.ui.components;

import java.awt.*;
import javax.swing.*;

/**
 * A FlowLayout subclass that fully supports wrapping of components.
 * Unlike standard FlowLayout, WrapLayout recalculates the preferred height
 * based on the container's current width so that wrapped rows of components
 * are given sufficient vertical height and are never clipped or overflowed.
 */
public class WrapLayout extends FlowLayout {

    private int lastReportedWidth = -1;

    public WrapLayout() {
        super();
    }

    public WrapLayout(int align) {
        super(align);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();

            if (targetWidth == 0) {
                // If container width is not established yet, search up the parent chain
                Container parent = target.getParent();
                while (parent != null) {
                    if (parent instanceof JViewport) {
                        targetWidth = parent.getWidth();
                        break;
                    }
                    if (parent.getWidth() > 0) {
                        targetWidth = parent.getWidth();
                        break;
                    }
                    parent = parent.getParent();
                }
            }

            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int maxRowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);

                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                    if ((rowWidth + d.width) > maxWidth && rowWidth > 0) {
                        maxRowWidth = Math.max(maxRowWidth, rowWidth);
                        dim.height += rowHeight + vgap;
                        rowWidth = 0;
                        rowHeight = 0;
                    }

                    rowWidth += d.width + hgap;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }

            maxRowWidth = Math.max(maxRowWidth, rowWidth);
            dim.width = Math.max(dim.width, maxRowWidth);
            dim.height += rowHeight + vgap;

            dim.width += insets.left + insets.right + (hgap * 2);
            dim.height += insets.top + insets.bottom + (vgap * 2);

            return dim;
        }
    }

    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        
        int currentWidth = target.getWidth();
        if (currentWidth > 0 && currentWidth != lastReportedWidth) {
            lastReportedWidth = currentWidth;
            // When width changes, revalidate so parent layout manager adjusts height
            Dimension pref = preferredLayoutSize(target);
            if (pref.height != target.getHeight()) {
                SwingUtilities.invokeLater(target::revalidate);
            }
        }
    }
}
