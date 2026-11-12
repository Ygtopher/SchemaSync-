package com.dbtool.panels;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * VS Code / TablePlus-inspired Ctrl+K Command Palette & Quick Table Switcher.
 * Allows instant fuzzy/substring search across all database tables, workbench tabs,
 * studio dialogs, and appearance settings.
 */
public class CommandPaletteDialog extends JDialog {

    public static class PaletteItem {
        private final String category;
        private final String title;
        private final String subtitle;
        private final String shortcutHint;
        private final Runnable action;

        public PaletteItem(String category, String title, String subtitle, String shortcutHint, Runnable action) {
            this.category = category;
            this.title = title;
            this.subtitle = subtitle;
            this.shortcutHint = shortcutHint != null ? shortcutHint : "";
            this.action = action;
        }

        public String getCategory() {
            return category;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getShortcutHint() {
            return shortcutHint;
        }

        public Runnable getAction() {
            return action;
        }

        public boolean matches(String query) {
            if (query == null || query.trim().isEmpty()) return true;
            String[] tokens = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
            String haystack = (category + " " + title + " " + subtitle).toLowerCase(Locale.ROOT);
            for (String token : tokens) {
                if (!haystack.contains(token)) {
                    return false;
                }
            }
            return true;
        }
    }

    private final List<PaletteItem> allItems = new ArrayList<>();
    private final DefaultListModel<PaletteItem> listModel = new DefaultListModel<>();
    private final JList<PaletteItem> resultList = new JList<>(listModel);
    private final JTextField searchField = new JTextField();
    private final JLabel countLabel = new JLabel();

    public CommandPaletteDialog(Frame owner, List<PaletteItem> items) {
        super(owner, "Command Palette", true);
        if (items != null) {
            this.allItems.addAll(items);
        }
        setUndecorated(false);
        setTitle("⚡ Command Palette & Quick Jump (Ctrl+K)");
        setResizable(true);
        setLayout(new BorderLayout(0, 0));

        boolean dark = ThemeManager.isDarkMode();
        Color bgColor = dark ? new Color(22, 27, 38) : new Color(255, 255, 255);
        Color headerBg = dark ? new Color(28, 35, 49) : new Color(248, 250, 252);
        Color borderColor = dark ? new Color(55, 65, 85) : new Color(203, 213, 225);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(bgColor);
        root.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        // Top Search Input Bar
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBackground(headerBg);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel iconLabel = new JLabel("🔍");
        iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD, 16f));
        topBar.add(iconLabel, BorderLayout.WEST);

        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.putClientProperty("JTextField.placeholderText", "Type a table name, studio tool, tab, or theme (↑↓ to navigate, Enter to run)...");
        searchField.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        topBar.add(searchField, BorderLayout.CENTER);

        countLabel.setForeground(ThemeManager.getMutedTextColor());
        countLabel.setFont(countLabel.getFont().deriveFont(11.5f));
        topBar.add(countLabel, BorderLayout.EAST);

        root.add(topBar, BorderLayout.NORTH);

        // Results List
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setBackground(bgColor);
        resultList.setFixedCellHeight(48);
        resultList.setCellRenderer(new PaletteCellRenderer());

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        // Footer hint bar
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(headerBg);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor),
                new EmptyBorder(6, 14, 6, 14)
        ));
        JLabel hintLbl = new JLabel("↑↓ Navigate   •   ↵ Enter to Open   •   Esc to Dismiss");
        hintLbl.setForeground(ThemeManager.getMutedTextColor());
        hintLbl.setFont(hintLbl.getFont().deriveFont(11.5f));
        footer.add(hintLbl, BorderLayout.WEST);
        root.add(footer, BorderLayout.SOUTH);

        add(root, BorderLayout.CENTER);

        // Wire filtering & keyboard navigation
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterItems(searchField.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { filterItems(searchField.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { filterItems(searchField.getText()); }
        });

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int size = listModel.getSize();
                if (size == 0) return;
                int idx = resultList.getSelectedIndex();
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    e.consume();
                    int next = (idx + 1) % size;
                    resultList.setSelectedIndex(next);
                    resultList.ensureIndexIsVisible(next);
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    e.consume();
                    int prev = (idx - 1 + size) % size;
                    resultList.setSelectedIndex(prev);
                    resultList.ensureIndexIsVisible(prev);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    executeSelected();
                }
            }
        });

        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = resultList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        resultList.setSelectedIndex(index);
                        if (e.getClickCount() == 2) {
                            executeSelected();
                        }
                    }
                }
            }
        });

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        filterItems("");

        setSize(640, 460);
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                searchField.requestFocusInWindow();
            }
        });
    }

    public static List<PaletteItem> filterPaletteItems(List<PaletteItem> source, String query) {
        List<PaletteItem> matched = new ArrayList<>();
        if (source == null) return matched;
        for (PaletteItem item : source) {
            if (item.matches(query)) {
                matched.add(item);
            }
        }
        return matched;
    }

    private void filterItems(String query) {
        listModel.clear();
        List<PaletteItem> matches = filterPaletteItems(allItems, query);
        for (PaletteItem item : matches) {
            listModel.addElement(item);
        }
        if (!listModel.isEmpty()) {
            resultList.setSelectedIndex(0);
        }
        countLabel.setText(matches.size() + " actions");
    }

    private void executeSelected() {
        PaletteItem selected = resultList.getSelectedValue();
        if (selected != null) {
            dispose();
            if (selected.getAction() != null) {
                SwingUtilities.invokeLater(selected.getAction());
            }
        }
    }

    private static class PaletteCellRenderer extends JPanel implements ListCellRenderer<PaletteItem> {
        private final JLabel categoryBadge = new JLabel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel subtitleLabel = new JLabel();
        private final JLabel shortcutLabel = new JLabel();

        public PaletteCellRenderer() {
            setLayout(new BorderLayout(10, 2));
            setBorder(new EmptyBorder(6, 14, 6, 14));

            JPanel leftText = new JPanel(new GridLayout(2, 1, 0, 1));
            leftText.setOpaque(false);

            JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            topRow.setOpaque(false);
            categoryBadge.setFont(categoryBadge.getFont().deriveFont(Font.BOLD, 10.5f));
            categoryBadge.setBorder(new EmptyBorder(1, 6, 1, 6));
            categoryBadge.setOpaque(true);

            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            topRow.add(categoryBadge);
            topRow.add(titleLabel);

            subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(11.5f));

            leftText.add(topRow);
            leftText.add(subtitleLabel);

            shortcutLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));

            add(leftText, BorderLayout.CENTER);
            add(shortcutLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PaletteItem> list, PaletteItem value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            boolean dark = ThemeManager.isDarkMode();
            if (isSelected) {
                setBackground(dark ? new Color(36, 62, 102) : new Color(219, 234, 254));
                titleLabel.setForeground(dark ? Color.WHITE : new Color(15, 23, 42));
                subtitleLabel.setForeground(dark ? new Color(203, 213, 225) : new Color(71, 85, 105));
            } else {
                setBackground(index % 2 == 0 ? list.getBackground() : ThemeManager.getZebraAlternateColor());
                titleLabel.setForeground(list.getForeground());
                subtitleLabel.setForeground(ThemeManager.getMutedTextColor());
            }

            categoryBadge.setText(value.getCategory());
            categoryBadge.setBackground(dark ? new Color(45, 55, 72) : new Color(226, 232, 240));
            categoryBadge.setForeground(ThemeManager.getPrimaryActionColor());

            titleLabel.setText(value.getTitle());
            subtitleLabel.setText("  " + value.getSubtitle());
            shortcutLabel.setText(value.getShortcutHint());
            shortcutLabel.setForeground(ThemeManager.getPrimaryActionColor());

            return this;
        }
    }
}
