package com.dbtool.ui.components;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A sleek, searchable JComboBox that filters its options in real-time as the user types.
 * Supports keyboard navigation, popup auto-display, and thread-safe population.
 */
public class SearchableComboBox<E> extends JComboBox<E> {
    private List<E> allItems = new ArrayList<>();
    public boolean isAdjusting = false;

    public SearchableComboBox() {
        super();
        setEditable(true);
        initSearchBehavior();
    }

    public SearchableComboBox(Collection<? extends E> items) {
        super();
        setEditable(true);
        initSearchBehavior();
        if (items != null) {
            setAllItems(items);
        }
    }

    private void initSearchBehavior() {
        if (!(getEditor().getEditorComponent() instanceof JTextField)) return;
        JTextField editor = (JTextField) getEditor().getEditorComponent();

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }

            private void update() {
                if (isAdjusting) return;
                SwingUtilities.invokeLater(() -> {
                    if (isAdjusting) return;
                    isAdjusting = true;
                    String text = editor.getText();
                    int caret = editor.getCaretPosition();

                    DefaultComboBoxModel<E> model = (DefaultComboBoxModel<E>) getModel();
                    model.removeAllElements();
                    String query = text != null ? text.toLowerCase() : "";
                    for (E item : allItems) {
                        if (item != null && item.toString().toLowerCase().contains(query)) {
                            model.addElement(item);
                        }
                    }

                    try {
                        model.setSelectedItem(text);
                    } catch (Exception ignored) {}
                    editor.setText(text);
                    if (text != null) {
                        editor.setCaretPosition(Math.min(caret, text.length()));
                    }

                    if (model.getSize() > 0 && (editor.hasFocus() || SearchableComboBox.this.hasFocus())) {
                        if (!isPopupVisible()) {
                            showPopup();
                        }
                    } else {
                        if (isPopupVisible()) {
                            hidePopup();
                        }
                    }
                    isAdjusting = false;
                });
            }
        });

        this.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                if (isAdjusting) return;
                isAdjusting = true;
                String text = editor.getText();
                int caret = editor.getCaretPosition();

                boolean matchesExact = false;
                for (E item : allItems) {
                    if (item != null && item.toString().equalsIgnoreCase(text)) {
                        matchesExact = true;
                        break;
                    }
                }

                if (matchesExact || text == null || text.trim().isEmpty()) {
                    if (getModel().getSize() != allItems.size()) {
                        DefaultComboBoxModel<E> model = (DefaultComboBoxModel<E>) getModel();
                        model.removeAllElements();
                        for (E item : allItems) {
                            model.addElement(item);
                        }
                        editor.setText(text);
                        if (text != null) {
                            editor.setCaretPosition(Math.min(caret, text.length()));
                        }
                    }
                }
                isAdjusting = false;
            }

            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(PopupMenuEvent e) {}
        });
    }

    public void setAllItems(Collection<? extends E> items) {
        isAdjusting = true;
        this.allItems = new ArrayList<>();
        if (items != null) {
            this.allItems.addAll(items);
        }
        DefaultComboBoxModel<E> model = new DefaultComboBoxModel<>();
        for (E item : allItems) {
            model.addElement(item);
        }
        setModel(model);
        try {
            setSelectedIndex(-1);
        } catch (Exception ignored) {}
        if (getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
            ((JTextField) getEditor().getEditorComponent()).setText("");
        }
        isAdjusting = false;
    }

    public List<E> getAllItems() {
        return Collections.unmodifiableList(allItems);
    }

    @Override
    public void addItem(E item) {
        if (!allItems.contains(item)) {
            allItems.add(item);
        }
        super.addItem(item);
    }

    @Override
    public void removeAllItems() {
        allItems.clear();
        super.removeAllItems();
        if (getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
            ((JTextField) getEditor().getEditorComponent()).setText("");
        }
    }

    @Override
    public void setSelectedItem(Object anObject) {
        boolean wasAdjusting = isAdjusting;
        isAdjusting = true;
        try {
            super.setSelectedItem(anObject);
        } catch (Exception ignored) {
            try {
                getModel().setSelectedItem(anObject);
                if (getEditor() != null) {
                    getEditor().setItem(anObject);
                }
            } catch (Exception ignored2) {}
        } finally {
            if (anObject != null && getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
                ((JTextField) getEditor().getEditorComponent()).setText(anObject.toString());
            }
            isAdjusting = wasAdjusting;
        }
    }

    @Override
    public Object getSelectedItem() {
        Object item = getModel() != null ? getModel().getSelectedItem() : null;
        if (item != null && !item.toString().trim().isEmpty()) {
            return item;
        }
        Object sel = super.getSelectedItem();
        if (sel != null && !sel.toString().trim().isEmpty()) {
            return sel;
        }
        if (getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
            String txt = ((JTextField) getEditor().getEditorComponent()).getText();
            if (txt != null && !txt.trim().isEmpty()) return txt.trim();
        }
        return sel;
    }

    public String getSelectedText() {
        Object sel = getSelectedItem();
        return sel != null ? sel.toString().trim() : "";
    }
}
