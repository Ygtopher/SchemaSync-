package com.dbtool.ui.components;

import com.dbtool.util.ClipboardUtil;
import javax.swing.JLabel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CopyableLabel extends JLabel {
    public CopyableLabel(String text) {
        super(text);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ClipboardUtil.copyToClipboard(getText());
            }
        });
    }
}
