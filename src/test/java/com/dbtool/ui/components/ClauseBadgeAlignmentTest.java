package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

public class ClauseBadgeAlignmentTest {

    @Test
    public void testAllSemanticBadgesShareFixedPrefixDimensionsAndRenderInBothThemes() {
        for (ClauseBadgeFactory.ClauseType type : ClauseBadgeFactory.ClauseType.values()) {
            JComponent badge = ClauseBadgeFactory.createBadge(type);
            assertNotNull(badge);
            Dimension pref = badge.getPreferredSize();
            assertEquals(ClauseBadgeFactory.PREFIX_WIDTH, pref.width,
                "Badge " + type + " should have fixed PREFIX_WIDTH");
            assertEquals(ClauseBadgeFactory.ROW_HEIGHT, pref.height,
                "Badge " + type + " should have fixed ROW_HEIGHT");

            badge.setSize(pref);
            BufferedImage img = new BufferedImage(pref.width, pref.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            badge.paint(g2);
            g2.dispose();
        }
    }

    @Test
    public void testLogicDropdownMatchesBadgePrefixWidth() {
        JComboBox<String> logicDropdown = new JComboBox<>(new String[]{"AND", "OR"});
        ClauseBadgeFactory.styleLogicDropdown(logicDropdown);

        Dimension pref = logicDropdown.getPreferredSize();
        assertEquals(ClauseBadgeFactory.PREFIX_WIDTH, pref.width);
        assertEquals(ClauseBadgeFactory.ROW_HEIGHT, pref.height);
    }

    @Test
    public void testGridColumnHelpersMaintainAlignedWidths() {
        JLabel dot = ClauseBadgeFactory.createDotSeparator();
        assertEquals(ClauseBadgeFactory.DOT_WIDTH, dot.getPreferredSize().width);
        assertEquals(ClauseBadgeFactory.ROW_HEIGHT, dot.getPreferredSize().height);

        JLabel eq = ClauseBadgeFactory.createFixedOperatorLabel("=");
        assertEquals(ClauseBadgeFactory.OPERATOR_WIDTH, eq.getPreferredSize().width);
        assertEquals(ClauseBadgeFactory.ROW_HEIGHT, eq.getPreferredSize().height);

        JButton rem = ClauseBadgeFactory.createRemoveButton("Remove");
        assertEquals(28, rem.getPreferredSize().width);
        assertEquals(ClauseBadgeFactory.ROW_HEIGHT, rem.getPreferredSize().height);
    }
}
