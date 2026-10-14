package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.ColumnDefinition;
import com.dbtool.util.ThemeManager;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.view.mxGraph;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.List;

/**
 * Interactive ER Diagram & Schema Architecture Visualizer.
 * Provides zoomable/pannable diagram cards with detailed PK/FK and type badges,
 * dynamic layout engine (Hierarchical, Organic, Circular), PNG export,
 * Mermaid markdown ERD generation, and interactive double-click/right-click navigation.
 */
public class ErDiagramPanel extends JPanel {

    public interface TableActionHandler {
        void onOpenTable(String tableName);
        void onGenerateCode(String tableName);
        void onMockData(String tableName);
        void onImportData(String tableName);
        void onIndexAdvisor(String tableName);
        default void onMaskData(String tableName) {}
    }

    private mxGraph graph;
    private mxGraphComponent graphComponent;
    private DatabaseManager dbManager;
    private JComboBox<String> searchDropdown;
    private JButton searchBtn;
    private JButton showAllBtn;
    private JComboBox<String> layoutCombo;
    private JLabel loadingLabel;
    private TableActionHandler tableActionHandler;

    // Cache of active visual state for export
    private final List<String> includedTables = new ArrayList<>();
    private final Map<String, List<ColumnDefinition>> tableDetailedColsMap = new HashMap<>();
    private final Map<String, Map<String, String[]>> tableFkMaps = new HashMap<>();
    private final Map<Object, String> vertexToTableMap = new HashMap<>();

    public ErDiagramPanel() {
        setLayout(new BorderLayout());

        // Top Toolbar
        JPanel toolbar = new JPanel(new com.dbtool.ui.components.WrapLayout(FlowLayout.LEFT, 4, 3));
        toolbar.add(new JLabel(" Table: "));

        searchDropdown = new JComboBox<>();
        searchDropdown.setEditable(true);
        searchDropdown.setPreferredSize(new Dimension(180, 26));

        // Autocomplete search suggestions
        JTextField editorField = (JTextField) searchDropdown.getEditor().getEditorComponent();
        editorField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                int code = e.getKeyCode();
                if (code == java.awt.event.KeyEvent.VK_ENTER || code == java.awt.event.KeyEvent.VK_UP ||
                    code == java.awt.event.KeyEvent.VK_DOWN || code == java.awt.event.KeyEvent.VK_LEFT ||
                    code == java.awt.event.KeyEvent.VK_RIGHT || code == java.awt.event.KeyEvent.VK_ESCAPE) {
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    if (dbManager == null) return;
                    String text = editorField.getText();
                    List<String> all = dbManager.getTableNames();

                    searchDropdown.removeAllItems();
                    if (text.isEmpty()) {
                        for (String t : all) searchDropdown.addItem(t);
                    } else {
                        for (String t : all) {
                            if (t.toLowerCase().contains(text.toLowerCase())) {
                                searchDropdown.addItem(t);
                            }
                        }
                    }
                    searchDropdown.hidePopup();
                    if (searchDropdown.getItemCount() > 0) {
                        searchDropdown.showPopup();
                    }
                    editorField.setText(text);
                });
            }
        });

        toolbar.add(searchDropdown);

        searchBtn = new JButton("🔍 Focus");
        showAllBtn = new JButton("🌐 Show All");

        loadingLabel = new JLabel(" Loading...");
        loadingLabel.setForeground(Color.GRAY);
        loadingLabel.setVisible(false);

        searchBtn.addActionListener(e -> {
            Object item = searchDropdown.getSelectedItem();
            if (item != null) refresh(dbManager, item.toString().trim());
        });

        showAllBtn.addActionListener(e -> {
            searchDropdown.setSelectedItem("");
            refresh(dbManager, "");
        });

        toolbar.add(searchBtn);
        toolbar.add(showAllBtn);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        // Layout Engine Switcher
        layoutCombo = new JComboBox<>(new String[]{
            "Hierarchical Layout", "Organic Layout", "Circular Layout", "Compact Tree"
        });
        layoutCombo.setToolTipText("Change visual layout algorithm");
        layoutCombo.addActionListener(e -> applyCurrentLayout());
        toolbar.add(new JLabel("Layout:"));
        toolbar.add(layoutCombo);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        // Zoom Controls
        JButton zoomInBtn = new JButton("➕ Zoom In");
        JButton zoomOutBtn = new JButton("➖ Zoom Out");
        JButton zoomFitBtn = new JButton("🔄 100%");
        zoomInBtn.addActionListener(e -> graphComponent.zoomIn());
        zoomOutBtn.addActionListener(e -> graphComponent.zoomOut());
        zoomFitBtn.addActionListener(e -> graphComponent.zoomTo(1.0, true));
        toolbar.add(zoomInBtn);
        toolbar.add(zoomOutBtn);
        toolbar.add(zoomFitBtn);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        // Export Controls
        JButton exportPngBtn = new JButton("📷 Export PNG");
        exportPngBtn.setToolTipText("Export high-resolution diagram image to PNG file");
        exportPngBtn.addActionListener(e -> exportToImage());
        toolbar.add(exportPngBtn);

        JButton exportMermaidBtn = new JButton("🧜 Mermaid ERD");
        exportMermaidBtn.setToolTipText("Export schema as Mermaid markdown diagram for documentation");
        exportMermaidBtn.addActionListener(e -> showMermaidDialog());
        toolbar.add(exportMermaidBtn);

        toolbar.add(loadingLabel);
        add(toolbar, BorderLayout.NORTH);

        // Graph Setup
        graph = new mxGraph();
        graph.setHtmlLabels(false);
        graph.setCellsEditable(false);
        graph.setAllowDanglingEdges(false);
        graph.setDropEnabled(false);
        graph.setSplitEnabled(false);
        graph.setCellsMovable(true);
        graph.setCellsSelectable(true);

        graphComponent = new mxGraphComponent(graph) {
            @Override
            public boolean isPanningEvent(java.awt.event.MouseEvent event) {
                return getCellAt(event.getX(), event.getY()) == null;
            }
        };
        graphComponent.setPanning(true);
        graphComponent.setConnectable(false);
        graphComponent.getGraphControl().addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                if (e.getWheelRotation() < 0) {
                    graphComponent.zoomIn();
                } else {
                    graphComponent.zoomOut();
                }
            }
        });

        // Interactive Table Click / Double-Click / Right-Click listener
        graphComponent.getGraphControl().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Object cell = graphComponent.getCellAt(e.getX(), e.getY());
                if (cell != null && graph.getModel().isVertex(cell)) {
                    String tableName = vertexToTableMap.get(cell);
                    if (tableName != null) {
                        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                            if (tableActionHandler != null) {
                                tableActionHandler.onOpenTable(tableName);
                            }
                        } else if (SwingUtilities.isRightMouseButton(e)) {
                            showTableContextMenu(e.getComponent(), e.getX(), e.getY(), tableName);
                        }
                    }
                }
            }
        });

        add(graphComponent, BorderLayout.CENTER);
    }

    public void setTableActionHandler(TableActionHandler handler) {
        this.tableActionHandler = handler;
    }

    private void showTableContextMenu(Component comp, int x, int y, String tableName) {
        JPopupMenu menu = new JPopupMenu();
        JLabel header = new JLabel("  🗄 " + tableName.toUpperCase());
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        menu.add(header);
        menu.addSeparator();

        JMenuItem openItem = new JMenuItem("🔍 Browse & Search Data");
        openItem.addActionListener(e -> {
            if (tableActionHandler != null) tableActionHandler.onOpenTable(tableName);
        });
        menu.add(openItem);

        JMenuItem codeGenItem = new JMenuItem("💻 Generate Entity / ORM Code...");
        codeGenItem.addActionListener(e -> {
            if (tableActionHandler != null) tableActionHandler.onGenerateCode(tableName);
        });
        menu.add(codeGenItem);

        JMenuItem mockItem = new JMenuItem("🧪 Generate Mock Test Data...");
        mockItem.addActionListener(e -> {
            if (tableActionHandler != null) tableActionHandler.onMockData(tableName);
        });
        menu.add(mockItem);

        JMenuItem importItem = new JMenuItem("📥 Import Data (CSV / Excel)...");
        importItem.addActionListener(e -> {
            if (tableActionHandler != null) tableActionHandler.onImportData(tableName);
        });
        menu.add(importItem);

        JMenuItem indexItem = new JMenuItem("⚡ Table Index & Performance Advisor...");
        indexItem.addActionListener(e -> {
            if (tableActionHandler != null) tableActionHandler.onIndexAdvisor(tableName);
        });
        menu.add(indexItem);

        JMenuItem maskItem = new JMenuItem("🔒 Mask & Anonymize Data...");
        maskItem.addActionListener(e -> {
            if (tableActionHandler != null) tableActionHandler.onMaskData(tableName);
        });
        menu.add(maskItem);

        menu.show(comp, x, y);
    }

    private boolean needsRefresh = false;

    public void markNeedsRefresh(DatabaseManager db) {
        this.dbManager = db;
        this.needsRefresh = true;
    }

    public void checkAndRefresh() {
        if (needsRefresh && dbManager != null) {
            needsRefresh = false;
            refresh(dbManager, "");
        }
    }

    public void refresh(DatabaseManager db, String focusTable) {
        this.dbManager = db;
        if (db == null || db.connection == null) {
            graph.getModel().beginUpdate();
            try {
                graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));
            } finally {
                graph.getModel().endUpdate();
            }
            searchDropdown.removeAllItems();
            vertexToTableMap.clear();
            includedTables.clear();
            needsRefresh = false;
            return;
        }
        needsRefresh = false;

        loadingLabel.setVisible(true);
        searchBtn.setEnabled(false);
        showAllBtn.setEnabled(false);
        searchDropdown.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            List<String> tempIncluded = new ArrayList<>();
            Map<String, List<ColumnDefinition>> tempColsMap = new HashMap<>();
            Map<String, Map<String, String[]>> tempFkMaps = new HashMap<>();
            List<String> allTables;

            @Override
            protected Void doInBackground() throws Exception {
                allTables = db.getTableNames();
                if (allTables == null || allTables.isEmpty()) {
                    return null;
                }

                if (focusTable != null && !focusTable.isEmpty()) {
                    String exactFocus = null;
                    for (String t : allTables) {
                        if (t.equalsIgnoreCase(focusTable)) {
                            exactFocus = t;
                            break;
                        }
                    }

                    if (exactFocus != null) {
                        tempIncluded.add(exactFocus);
                        tempFkMaps.put(exactFocus, db.getForeignKeys(exactFocus));

                        // 1. Tables exactFocus points to
                        Map<String, String[]> fks = tempFkMaps.get(exactFocus);
                        for (String[] pkInfo : fks.values()) {
                            String target = pkInfo[0];
                            if (!tempIncluded.contains(target) && allTables.contains(target)) {
                                tempIncluded.add(target);
                                tempFkMaps.put(target, db.getForeignKeys(target));
                            }
                        }

                        // 2. Tables that point to exactFocus
                        for (String t : allTables) {
                            if (t.equals(exactFocus)) continue;
                            Map<String, String[]> tFks = db.getForeignKeys(t);
                            for (String[] pkInfo : tFks.values()) {
                                if (pkInfo[0].equalsIgnoreCase(exactFocus)) {
                                    if (!tempIncluded.contains(t)) tempIncluded.add(t);
                                    tempFkMaps.put(t, tFks);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    int limit = Math.min(45, allTables.size());
                    for (int i = 0; i < limit; i++) {
                        tempIncluded.add(allTables.get(i));
                    }
                    for (String t : tempIncluded) {
                        tempFkMaps.put(t, db.getForeignKeys(t));
                    }
                }

                // Fetch detailed columns (PK, FK, data types)
                for (String t : tempIncluded) {
                    tempColsMap.put(t, db.getDetailedColumns(t));
                }

                return null;
            }

            @Override
            protected void done() {
                try {
                    get();

                    includedTables.clear();
                    includedTables.addAll(tempIncluded);
                    tableDetailedColsMap.clear();
                    tableDetailedColsMap.putAll(tempColsMap);
                    tableFkMaps.clear();
                    tableFkMaps.putAll(tempFkMaps);
                    vertexToTableMap.clear();

                    if (allTables != null && searchDropdown.getItemCount() <= 1) {
                        searchDropdown.removeAllItems();
                        searchDropdown.addItem("");
                        for (String t : allTables) {
                            searchDropdown.addItem(t);
                        }
                        if (focusTable != null && !focusTable.isEmpty()) {
                            searchDropdown.setSelectedItem(focusTable);
                        }
                    }

                    // Build Graph on EDT
                    graph.getModel().beginUpdate();
                    try {
                        graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));
                        Object parent = graph.getDefaultParent();
                        Map<String, Object> vertexMap = new HashMap<>();

                        boolean isDark = ThemeManager.isDarkMode();
                        String fillColor = isDark ? "#1e222a" : "#f8fafc";
                        String fontColor = isDark ? "#e2e8f0" : "#1e293b";
                        String strokeColor = isDark ? "#475569" : "#cbd5e1";
                        String edgeStroke = isDark ? "#818cf8" : "#4f46e5";
                        String edgeFont = isDark ? "#c7d2fe" : "#312e81";

                        // Create Vertices with detailed cards
                        for (String t : includedTables) {
                            List<ColumnDefinition> cols = tableDetailedColsMap.get(t);
                            int colCount = cols != null ? cols.size() : 0;
                            StringBuilder sb = new StringBuilder();
                            sb.append("  🗄  ").append(t.toUpperCase()).append("  (").append(colCount).append(" cols)\n");
                            sb.append(" ──────────────────────────────────────────\n");

                            int maxLineLen = t.length() + 14;
                            if (cols != null) {
                                for (ColumnDefinition c : cols) {
                                    String badge = c.isPrimaryKey ? "🔑 [PK] " : (c.foreignKeyRef != null ? "🔗 [FK] " : "   ");
                                    String line = badge + c.name + " : " + c.getFriendlyType();
                                    if (c.isAutoIncrement) line += " ⚡";
                                    if (!c.isNullable && !c.isPrimaryKey) line += " *";
                                    sb.append(" ").append(line).append("\n");
                                    maxLineLen = Math.max(maxLineLen, line.length());
                                }
                            }

                            int height = 55 + (colCount * 19);
                            int width = Math.max(260, maxLineLen * 9);

                            String style = "shape=rectangle;rounded=1;arcSize=6;fillColor=" + fillColor +
                                           ";fontColor=" + fontColor + ";strokeColor=" + strokeColor +
                                           ";strokeWidth=1.5;verticalAlign=top;align=left;spacingTop=8;spacingLeft=8;fontSize=11;fontFamily=Consolas,Monospaced;";

                            Object v = graph.insertVertex(parent, null, sb.toString(), 0, 0, width, height, style);
                            vertexMap.put(t, v);
                            vertexToTableMap.put(v, t);
                        }

                        // Create Edges
                        for (String t : includedTables) {
                            Object vSource = vertexMap.get(t);
                            if (vSource == null) continue;

                            Map<String, String[]> fks = tableFkMaps.get(t);
                            if (fks == null) continue;

                            for (Map.Entry<String, String[]> entry : fks.entrySet()) {
                                String fkCol = entry.getKey();
                                String pkTable = entry.getValue()[0];
                                String pkCol = entry.getValue()[1];

                                Object vTarget = vertexMap.get(pkTable);
                                if (vTarget != null) {
                                    String edgeLabel = fkCol + " ➔ " + pkCol;
                                    String edgeStyle = "edgeStyle=orthogonalEdgeStyle;rounded=1;strokeColor=" + edgeStroke +
                                                       ";strokeWidth=2;fontColor=" + edgeFont + ";fontSize=10;fontFamily=Segoe UI,Sans-Serif;";
                                    graph.insertEdge(parent, null, edgeLabel, vSource, vTarget, edgeStyle);
                                }
                            }
                        }

                        applyCurrentLayout();

                    } finally {
                        graph.getModel().endUpdate();
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(ErDiagramPanel.this, "Error building ER Diagram: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    loadingLabel.setVisible(false);
                    searchBtn.setEnabled(true);
                    showAllBtn.setEnabled(true);
                    searchDropdown.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void applyCurrentLayout() {
        if (graph == null || graph.getDefaultParent() == null) return;
        String selected = layoutCombo != null ? (String) layoutCombo.getSelectedItem() : "Hierarchical Layout";
        if (selected == null) selected = "Hierarchical Layout";

        graph.getModel().beginUpdate();
        try {
            Object parent = graph.getDefaultParent();
            if (selected.contains("Organic")) {
                mxFastOrganicLayout layout = new mxFastOrganicLayout(graph);
                layout.setForceConstant(160);
                layout.execute(parent);
            } else if (selected.contains("Circular")) {
                mxCircleLayout layout = new mxCircleLayout(graph);
                layout.execute(parent);
            } else if (selected.contains("Tree")) {
                mxCompactTreeLayout layout = new mxCompactTreeLayout(graph);
                layout.execute(parent);
            } else {
                mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
                layout.setIntraCellSpacing(60);
                layout.setInterRankCellSpacing(110);
                layout.execute(parent);
            }
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private void exportToImage() {
        if (includedTables.isEmpty()) {
            JOptionPane.showMessageDialog(this, "The ER Diagram is currently empty.", "Export PNG", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            boolean isDark = ThemeManager.isDarkMode();
            Color bgColor = isDark ? new Color(24, 27, 34) : Color.WHITE;
            BufferedImage image = mxCellRenderer.createBufferedImage(graph, null, 1.8, bgColor, true, null);

            if (image == null) {
                JOptionPane.showMessageDialog(this, "Could not render graph image.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("SchemaSync_ERD.png"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File target = chooser.getSelectedFile();
                if (!target.getName().toLowerCase().endsWith(".png")) {
                    target = new File(target.getParentFile(), target.getName() + ".png");
                }
                ImageIO.write(image, "PNG", target);
                JOptionPane.showMessageDialog(this, "ER Diagram successfully exported to:\n" + target.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to export image: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public String generateMermaidErd() {
        StringBuilder sb = new StringBuilder();
        sb.append("erDiagram\n");

        // Relationships
        for (String t : includedTables) {
            Map<String, String[]> fks = tableFkMaps.get(t);
            if (fks != null) {
                for (Map.Entry<String, String[]> entry : fks.entrySet()) {
                    String fkCol = entry.getKey();
                    String pkTable = entry.getValue()[0];
                    String pkCol = entry.getValue()[1];
                    if (includedTables.contains(pkTable)) {
                        String safeChild = t.replaceAll("[^a-zA-Z0-9_]", "_");
                        String safeParent = pkTable.replaceAll("[^a-zA-Z0-9_]", "_");
                        sb.append("    ").append(safeChild).append(" }o--|| ").append(safeParent)
                          .append(" : \"").append(fkCol).append(" -> ").append(pkCol).append("\"\n");
                    }
                }
            }
        }
        sb.append("\n");

        // Tables & Columns
        for (String t : includedTables) {
            String safeTable = t.replaceAll("[^a-zA-Z0-9_]", "_");
            sb.append("    ").append(safeTable).append(" {\n");
            List<ColumnDefinition> cols = tableDetailedColsMap.get(t);
            if (cols != null) {
                for (ColumnDefinition col : cols) {
                    String cleanType = col.getFriendlyType().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
                    String keyBadge = col.isPrimaryKey ? " PK" : (col.foreignKeyRef != null ? " FK" : "");
                    String safeCol = col.name.replaceAll("[^a-zA-Z0-9_]", "_");
                    sb.append("        ").append(cleanType).append(" ").append(safeCol).append(keyBadge).append("\n");
                }
            }
            sb.append("    }\n");
        }
        return sb.toString();
    }

    private void showMermaidDialog() {
        if (includedTables.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No tables currently visible in the ER diagram.", "Mermaid ERD", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String mermaidCode = generateMermaidErd();
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "🧜 Mermaid ER Diagram Markdown", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(750, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel title = new JLabel("Mermaid.js Entity-Relationship Markdown");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        JLabel hint = new JLabel("Ready to embed in GitHub README.md, Notion, Obsidian, or documentation sites.");
        hint.setForeground(Color.GRAY);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        dialog.add(header, BorderLayout.NORTH);

        RSyntaxTextArea area = new RSyntaxTextArea(mermaidCode);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        area.setEditable(true);
        dialog.add(new RTextScrollPane(area), BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton copyBtn = new JButton("📋 Copy to Clipboard");
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(area.getText()), null);
            JOptionPane.showMessageDialog(dialog, "Mermaid ERD markdown copied to clipboard!");
        });
        btnBar.add(copyBtn);

        JButton saveBtn = new JButton("💾 Save .md / .mmd");
        saveBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("schema-erd.md"));
            if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                    fw.write("```mermaid\n" + area.getText() + "```\n");
                    JOptionPane.showMessageDialog(dialog, "Saved successfully to " + chooser.getSelectedFile().getName());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        btnBar.add(saveBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        btnBar.add(closeBtn);

        dialog.add(btnBar, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
}
