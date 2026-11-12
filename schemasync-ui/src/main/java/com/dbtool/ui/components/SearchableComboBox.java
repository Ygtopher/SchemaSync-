package com.dbtool.ui.components;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 * A sleek, searchable JComboBox that filters its options in real-time as the user types.
 * Supports keyboard navigation, popup auto-display, and thread-safe population.
 * Optimized for Linux/Ubuntu (X11/Wayland) and FlatLaf to prevent popup auto-closing and selection loss.
 */
public class SearchableComboBox<E> extends JComboBox<E> {
    private List<E> allItems = new ArrayList<>();
    public boolean isAdjusting = false;
    private boolean suppressActionEvents = false;

    public SearchableComboBox() {
        super();
        setEditable(true);
        setLightWeightPopupEnabled(true);
        setMaximumRowCount(12);
        initSearchBehavior();
    }

    public SearchableComboBox(Collection<? extends E> items) {
        this();
        if (items != null) {
            setAllItems(items);
        }
    }

    @Override
    protected void fireActionEvent() {
        if (suppressActionEvents) {
            return;
        }
        super.fireActionEvent();
    }

    private void initSearchBehavior() {
        Component editorComp = getEditor() != null ? getEditor().getEditorComponent() : null;
        if (!(editorComp instanceof JTextField)) return;
        JTextField editor = (JTextField) editorComp;

        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (isAdjusting) return;
                int code = e.getKeyCode();

                // Ignore navigation and modifier keys so arrow key navigation in popup works natively
                if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN ||
                    code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT ||
                    code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_PAGE_DOWN ||
                    code == KeyEvent.VK_HOME || code == KeyEvent.VK_END ||
                    code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL ||
                    code == KeyEvent.VK_ALT || code == KeyEvent.VK_META ||
                    code == KeyEvent.VK_CAPS_LOCK || code == KeyEvent.VK_TAB) {
                    return;
                }

                if (code == KeyEvent.VK_ESCAPE) {
                    if (isPopupVisible()) {
                        hidePopup();
                    }
                    return;
                }

                if (code == KeyEvent.VK_ENTER) {
                    String text = editor.getText() != null ? editor.getText().trim() : "";
                    E matched = findMatchingItem(text);
                    if (matched != null) {
                        restoreFullModelWithSelection(matched, matched.toString());
                        hidePopup();
                        fireActionEvent();
                    } else if (getModel().getSize() > 0) {
                        Object sel = getModel().getSelectedItem();
                        E pick = sel != null ? findMatchingItem(sel.toString()) : getModel().getElementAt(0);
                        if (pick != null) {
                            restoreFullModelWithSelection(pick, pick.toString());
                            hidePopup();
                            fireActionEvent();
                        }
                    }
                    return;
                }

                char ch = e.getKeyChar();
                boolean isEditKey = code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE ||
                    (ch != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(ch));
                if (!isEditKey) return;

                filterModelByEditorText(editor);
            }
        });

        // Clicking inside the editor text field opens the popup if not already visible
        editor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isEnabled()) return;
                if (!isPopupVisible() && !allItems.isEmpty()) {
                    ensureFullModelIfUnfiltered(editor);
                    showPopup();
                }
            }
        });

        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Ensure editor text stays synced when focus leaves
                if (isAdjusting) return;
                String text = editor.getText();
                if (text != null && !text.trim().isEmpty()) {
                    E match = findMatchingItem(text.trim());
                    if (match != null && !match.equals(getModel().getSelectedItem())) {
                        suppressActionEvents = true;
                        isAdjusting = true;
                        try {
                            getModel().setSelectedItem(match);
                            editor.setText(match.toString());
                        } finally {
                            isAdjusting = false;
                            suppressActionEvents = false;
                        }
                    }
                }
            }
        });

        this.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                if (isAdjusting) return;
                ensureFullModelIfUnfiltered(editor);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if (isAdjusting) return;
                Object sel = getModel().getSelectedItem();
                if (sel != null) {
                    String selStr = sel.toString();
                    if (getModel().getSize() != allItems.size()) {
                        E match = findMatchingItem(selStr);
                        restoreFullModelWithSelection(match != null ? match : sel, selStr);
                    } else if (!selStr.equals(editor.getText())) {
                        isAdjusting = true;
                        try {
                            editor.setText(selStr);
                        } finally {
                            isAdjusting = false;
                        }
                    }
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
        });
    }

    private E findMatchingItem(String text) {
        if (text == null) return null;
        for (E item : allItems) {
            if (item != null && item.toString().equals(text)) {
                return item;
            }
        }
        for (E item : allItems) {
            if (item != null && item.toString().equalsIgnoreCase(text)) {
                return item;
            }
        }
        return null;
    }

    private void ensureFullModelIfUnfiltered(JTextField editor) {
        String text = editor.getText();
        int caret = editor.getCaretPosition();
        E exactMatch = findMatchingItem(text != null ? text.trim() : "");
        if (exactMatch != null || text == null || text.trim().isEmpty()) {
            if (getModel().getSize() != allItems.size()) {
                restoreFullModelWithSelection(exactMatch, text);
                if (text != null) {
                    editor.setCaretPosition(Math.min(caret, editor.getText().length()));
                }
            }
        }
    }

    private void restoreFullModelWithSelection(Object selectedItem, String editorText) {
        suppressActionEvents = true;
        isAdjusting = true;
        try {
            DefaultComboBoxModel<E> model = new DefaultComboBoxModel<>(new Vector<>(allItems));
            model.setSelectedItem(selectedItem);
            setModel(model);
            if (getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
                JTextField editor = (JTextField) getEditor().getEditorComponent();
                String targetText = editorText != null ? editorText : (selectedItem != null ? selectedItem.toString() : "");
                editor.setText(targetText);
            }
        } finally {
            isAdjusting = false;
            suppressActionEvents = false;
        }
    }

    private void filterModelByEditorText(JTextField editor) {
        suppressActionEvents = true;
        isAdjusting = true;
        try {
            String text = editor.getText();
            int caret = editor.getCaretPosition();
            String query = text != null ? text.toLowerCase() : "";

            Vector<E> filtered = new Vector<>();
            for (E item : allItems) {
                if (item != null && item.toString().toLowerCase().contains(query)) {
                    filtered.add(item);
                }
            }

            DefaultComboBoxModel<E> model = new DefaultComboBoxModel<>(filtered);
            // Keep selectedObject null while typing so clicking the first item in popup always fires selection
            model.setSelectedItem(null);
            setModel(model);

            editor.setText(text);
            if (text != null) {
                editor.setCaretPosition(Math.min(caret, text.length()));
            }

            if (!filtered.isEmpty() && isShowing() && (editor.hasFocus() || hasFocus())) {
                if (isPopupVisible()) {
                    hidePopup();
                }
                showPopup();
            } else if (filtered.isEmpty() && isPopupVisible()) {
                hidePopup();
            }
        } finally {
            isAdjusting = false;
            suppressActionEvents = false;
        }
    }

    public void setAllItems(Collection<? extends E> items) {
        suppressActionEvents = true;
        isAdjusting = true;
        try {
            this.allItems = new ArrayList<>();
            if (items != null) {
                this.allItems.addAll(items);
            }
            DefaultComboBoxModel<E> model = new DefaultComboBoxModel<>(new Vector<>(allItems));
            model.setSelectedItem(null);
            setModel(model);
            if (getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
                ((JTextField) getEditor().getEditorComponent()).setText("");
            }
        } finally {
            isAdjusting = false;
            suppressActionEvents = false;
        }
    }

    public List<E> getAllItems() {
        return Collections.unmodifiableList(allItems);
    }

    @Override
    public void addItem(E item) {
        boolean wasSuppressing = suppressActionEvents;
        boolean wasAdjusting = isAdjusting;
        suppressActionEvents = true;
        isAdjusting = true;
        try {
            if (!allItems.contains(item)) {
                allItems.add(item);
            }
            super.addItem(item);
        } finally {
            isAdjusting = wasAdjusting;
            suppressActionEvents = wasSuppressing;
        }
    }

    @Override
    public void removeAllItems() {
        boolean wasSuppressing = suppressActionEvents;
        boolean wasAdjusting = isAdjusting;
        suppressActionEvents = true;
        isAdjusting = true;
        try {
            allItems.clear();
            super.removeAllItems();
            if (getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
                ((JTextField) getEditor().getEditorComponent()).setText("");
            }
        } finally {
            isAdjusting = wasAdjusting;
            suppressActionEvents = wasSuppressing;
        }
    }

    @Override
    public void setSelectedItem(Object anObject) {
        Object oldSelected = getModel() != null ? getModel().getSelectedItem() : null;
        boolean wasAdjusting = isAdjusting;
        try {
            // If model was filtered and doesn't contain anObject, restore full model first
            if (anObject != null && getModel().getSize() != allItems.size() && allItems.contains(anObject)) {
                restoreFullModelWithSelection(null, "");
            }
            super.setSelectedItem(anObject);
        } catch (Exception ignored) {
            try {
                getModel().setSelectedItem(anObject);
                if (getEditor() != null) {
                    getEditor().setItem(anObject);
                }
            } catch (Exception ignored2) {}
        } finally {
            isAdjusting = true;
            try {
                if (getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
                    ((JTextField) getEditor().getEditorComponent()).setText(anObject != null ? anObject.toString() : "");
                }
            } finally {
                isAdjusting = wasAdjusting;
            }
        }
        // If DefaultComboBoxModel skipped firing because oldSelected already equaled anObject, fire explicitly
        if (anObject != null && anObject.equals(oldSelected) && !suppressActionEvents) {
            fireActionEvent();
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
