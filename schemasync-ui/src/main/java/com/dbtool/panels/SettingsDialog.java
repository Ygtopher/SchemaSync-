package com.dbtool.panels;

import javax.swing.JDialog;
import javax.swing.JFrame;

public class SettingsDialog extends JDialog {
    public SettingsDialog(JFrame parent) {
        super(parent, "Preferences", true);
        setSize(400, 250);
        setLocationRelativeTo(parent);
    }
}
