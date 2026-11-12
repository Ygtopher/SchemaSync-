package com.dbtool.ui.components;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;

public class StatusBar extends JPanel {
    private final JLabel statusLabel = new JLabel("Ready");

    public StatusBar() {
        super(new FlowLayout(FlowLayout.LEFT));
        add(statusLabel);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }
}
