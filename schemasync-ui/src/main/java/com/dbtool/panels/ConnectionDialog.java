package com.dbtool.panels;

import javax.swing.JDialog;
import javax.swing.JFrame;

public class ConnectionDialog extends JDialog {
    public ConnectionDialog(JFrame parent) {
        super(parent, "Connect to Database", true);
        setSize(400, 300);
        setLocationRelativeTo(parent);
    }
}
