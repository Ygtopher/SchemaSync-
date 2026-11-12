package com.dbtool.ui.components;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;

public class PagingToolbar extends JPanel {
    private final JButton prevBtn = new JButton("<");
    private final JButton nextBtn = new JButton(">");
    private final JLabel pageLabel = new JLabel("Page 1");

    public PagingToolbar() {
        super(new FlowLayout(FlowLayout.RIGHT));
        add(prevBtn);
        add(pageLabel);
        add(nextBtn);
    }
}
