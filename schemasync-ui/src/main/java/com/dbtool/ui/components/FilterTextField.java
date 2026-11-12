package com.dbtool.ui.components;

import javax.swing.JTextField;

public class FilterTextField extends JTextField {
    public FilterTextField(int cols) {
        super(cols);
        putClientProperty("JTextField.placeholderText", "Filter...");
    }
}
