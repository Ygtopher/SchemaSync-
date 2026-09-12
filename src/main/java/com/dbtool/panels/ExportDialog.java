package com.dbtool.panels;

import javax.swing.JDialog;
import javax.swing.JFrame;

public class ExportDialog extends JDialog {
    public ExportDialog(JFrame parent) {
        super(parent, "Export Data", true);
        setSize(400, 250);
        setLocationRelativeTo(parent);
    }
}
