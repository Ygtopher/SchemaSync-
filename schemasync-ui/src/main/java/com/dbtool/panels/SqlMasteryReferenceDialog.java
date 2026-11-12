package com.dbtool.panels;

import com.dbtool.util.ClipboardUtil;
import com.dbtool.util.SqlSnippetsLibrary;
import com.dbtool.util.SqlSnippetsLibrary.Snippet;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interactive SQL Mastery & Reference Library Dialog.
 * Allows searching, browsing, previewing, copying, and inserting
 * advanced production SQL templates directly into the SQL Editor.
 */
public class SqlMasteryReferenceDialog extends JDialog {

    private final Consumer<String> onInsertAction;
    private final JTextField searchField = new JTextField(25);
    private final DefaultListModel<Snippet> listModel = new DefaultListModel<>();
    private final JList<Snippet> snippetList = new JList<>(listModel);

    private final JLabel titleLabel = new JLabel("Select a SQL Template");
    private final JLabel dialectBadge = new JLabel("");
    private final JLabel categoryBadge = new JLabel("");
    private final JTextArea descArea = new JTextArea(2, 40);
    private final RSyntaxTextArea codeViewer = new RSyntaxTextArea();
    private final JButton insertBtn = new JButton("⚡ Insert into Editor");
    private final JButton copyBtn = new JButton("📋 Copy SQL");

    public SqlMasteryReferenceDialog(Window owner, Consumer<String> onInsertAction) {
        super(owner, "SQL Mastery & Production Query Reference", ModalityType.MODELESS);
        this.onInsertAction = onInsertAction;

        setSize(1000, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        initUI();
        refreshList("");
    }

    private void initUI() {
        // TOP: Header & Search Panel
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBorder(new EmptyBorder(10, 12, 6, 12));

        JLabel headerTitle = new JLabel("🔥 SQL Mastery & Production Reference Library");
        headerTitle.setFont(headerTitle.getFont().deriveFont(Font.BOLD, 15f));
        JLabel headerSub = new JLabel("Explore real-world production SQL: frame-aware windows, recursive CTEs, concurrency locks, engine-specific superpowers, and index architectures.");
        headerSub.setForeground(Color.GRAY);

        JPanel headerTextPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        headerTextPanel.add(headerTitle);
        headerTextPanel.add(headerSub);

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchBar.add(new JLabel("🔍 Search:"));
        searchField.setToolTipText("Filter by title, category, keyword, dialect, or SQL syntax...");
        searchBar.add(searchField);

        topPanel.add(headerTextPanel, BorderLayout.WEST);
        topPanel.add(searchBar, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // LEFT: Snippet List
        snippetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        snippetList.setCellRenderer(new SnippetListCellRenderer());
        snippetList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Snippet s = snippetList.getSelectedValue();
                displaySnippet(s);
            }
        });

        JScrollPane listScroll = new JScrollPane(snippetList);
        listScroll.setPreferredSize(new Dimension(340, 0));
        listScroll.setBorder(BorderFactory.createTitledBorder("Templates & Patterns"));

        // RIGHT: Snippet Details & Code Viewer
        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBorder(new EmptyBorder(0, 4, 0, 12));

        JPanel detailHeader = new JPanel(new BorderLayout(4, 4));
        detailHeader.setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel titleBadgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleBadgePanel.add(titleLabel);

        dialectBadge.setOpaque(true);
        boolean dark = ThemeManager.isDarkMode();
        dialectBadge.setBackground(dark ? new Color(35, 55, 85) : new Color(225, 235, 250));
        dialectBadge.setForeground(dark ? new Color(140, 195, 255) : new Color(25, 65, 130));
        dialectBadge.setBorder(new EmptyBorder(2, 6, 2, 6));
        titleBadgePanel.add(dialectBadge);

        categoryBadge.setForeground(Color.GRAY);
        titleBadgePanel.add(categoryBadge);
        detailHeader.add(titleBadgePanel, BorderLayout.NORTH);

        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setOpaque(false);
        descArea.setBorder(new EmptyBorder(4, 4, 4, 4));
        descArea.setForeground(UIManager.getColor("Label.foreground"));
        detailHeader.add(descArea, BorderLayout.CENTER);

        rightPanel.add(detailHeader, BorderLayout.NORTH);

        codeViewer.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        codeViewer.setEditable(false);
        codeViewer.setAntiAliasingEnabled(true);
        codeViewer.setCodeFoldingEnabled(true);
        RTextScrollPane codeScroll = new RTextScrollPane(codeViewer);
        codeScroll.setBorder(BorderFactory.createTitledBorder("SQL Code"));
        rightPanel.add(codeScroll, BorderLayout.CENTER);

        // SPLIT PANE
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, rightPanel);
        splitPane.setDividerLocation(340);
        add(splitPane, BorderLayout.CENTER);

        // BOTTOM: Action Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomPanel.setBorder(new EmptyBorder(4, 12, 10, 12));

        copyBtn.setEnabled(false);
        copyBtn.addActionListener(e -> {
            String sql = codeViewer.getText();
            if (sql != null && !sql.isEmpty()) {
                ClipboardUtil.copyToClipboard(sql);
                JOptionPane.showMessageDialog(this, "SQL copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        insertBtn.setEnabled(false);
        insertBtn.setFont(insertBtn.getFont().deriveFont(Font.BOLD));
        insertBtn.addActionListener(e -> {
            String sql = codeViewer.getText();
            if (sql != null && !sql.isEmpty() && onInsertAction != null) {
                onInsertAction.accept(sql);
                dispose();
            }
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        bottomPanel.add(copyBtn);
        bottomPanel.add(insertBtn);
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        // Live Search Listener
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshList(searchField.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { refreshList(searchField.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { refreshList(searchField.getText()); }
        });
    }

    private void refreshList(String query) {
        listModel.clear();
        List<Snippet> results = SqlSnippetsLibrary.search(query);
        for (Snippet s : results) {
            listModel.addElement(s);
        }
        if (!listModel.isEmpty()) {
            snippetList.setSelectedIndex(0);
        } else {
            clearDisplay();
        }
    }

    private void displaySnippet(Snippet s) {
        if (s == null) {
            clearDisplay();
            return;
        }
        titleLabel.setText(s.getTitle());
        dialectBadge.setText(s.getDialect() != null ? s.getDialect() : "ANSI");
        categoryBadge.setText("• " + s.getCategory());
        descArea.setText(s.getDescription());
        codeViewer.setText(s.getSql());
        codeViewer.setCaretPosition(0);
        insertBtn.setEnabled(true);
        copyBtn.setEnabled(true);
    }

    private void clearDisplay() {
        titleLabel.setText("No templates found");
        dialectBadge.setText("");
        categoryBadge.setText("");
        descArea.setText("Try adjusting your search query.");
        codeViewer.setText("");
        insertBtn.setEnabled(false);
        copyBtn.setEnabled(false);
    }

    private static class SnippetListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Snippet) {
                Snippet s = (Snippet) value;
                String catShort = s.getCategory().replaceAll("^[🌟🔥]\\s*", "");
                setText("<html><b>" + s.getTitle() + "</b><br><small style='color:gray;'>" + catShort + "</small></html>");
                setBorder(new EmptyBorder(4, 6, 4, 6));
            }
            return this;
        }
    }
}
