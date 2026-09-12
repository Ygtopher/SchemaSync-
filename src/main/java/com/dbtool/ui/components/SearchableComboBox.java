package com.dbtool.ui.components;

import javax.swing.JComboBox;

public class SearchableComboBox<E> extends JComboBox<E> {
    public SearchableComboBox() {
        setEditable(true);
    }
}
