package com.dbtool.ui;

import com.dbtool.ui.components.WrapLayout;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class WrapLayoutTest {

    @Test
    public void testWrapLayoutCalculatesMultiRowHeight() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 5));
        
        // Add 5 buttons, each 100x30
        for (int i = 0; i < 5; i++) {
            JButton btn = new JButton("Btn" + i);
            btn.setPreferredSize(new Dimension(100, 30));
            panel.add(btn);
        }

        // When width is 600 (plenty of room), it should be 1 row
        panel.setSize(600, 100);
        Dimension prefWide = panel.getPreferredSize();
        // 1 row of height 30 + vgap (5*2 + insets)
        assertTrue(prefWide.height <= 50, "Wide container should be 1 row high");

        // When width is constrained to 250, 5 buttons of 100px will wrap into multiple rows
        panel.setSize(250, 100);
        Dimension prefNarrow = panel.getPreferredSize();
        assertTrue(prefNarrow.height > prefWide.height, "Narrow container should have larger height due to wrapping");
    }
}
