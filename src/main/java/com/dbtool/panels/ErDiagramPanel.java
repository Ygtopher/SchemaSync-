package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ErDiagramPanel extends JPanel {
    private mxGraph graph;
    private mxGraphComponent graphComponent;
    private DatabaseManager dbManager;
    private JComboBox<String> searchDropdown;
    private JButton searchBtn;
    private JButton showAllBtn;
    private JLabel loadingLabel;
    
    public ErDiagramPanel() {
        setLayout(new BorderLayout());
        
        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(new JLabel(" Focus on Table: "));
        
        searchDropdown = new JComboBox<>();
        searchDropdown.setEditable(true);
        searchDropdown.setPreferredSize(new Dimension(200, 26));
        
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
                    java.util.List<String> all = dbManager.getTableNames();
                    
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
        
        searchBtn = new JButton("Search");
        showAllBtn = new JButton("Show All");
        
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
        
        JButton zoomInBtn = new JButton("Zoom In");
        JButton zoomOutBtn = new JButton("Zoom Out");
        zoomInBtn.addActionListener(e -> graphComponent.zoomIn());
        zoomOutBtn.addActionListener(e -> graphComponent.zoomOut());
        toolbar.add(zoomInBtn);
        toolbar.add(zoomOutBtn);

        toolbar.add(loadingLabel);
        add(toolbar, BorderLayout.NORTH);
        
        // Graph setup
        graph = new mxGraph();
        graph.setHtmlLabels(false);
        graph.setCellsEditable(false);
        graph.setAllowDanglingEdges(false);
        graph.setDropEnabled(false);
        graph.setSplitEnabled(false);
        graph.setCellsMovable(true); // Allow user to drag and reposition tables
        graph.setCellsSelectable(true);
        
        // Disable node selection highlighting for cleaner look
        graphComponent = new mxGraphComponent(graph) {
            @Override
            public boolean isPanningEvent(java.awt.event.MouseEvent event) {
                // Pan if clicking on empty canvas. If clicking a cell, let JGraphX move it.
                return getCellAt(event.getX(), event.getY()) == null;
            }
        };
        graphComponent.setPanning(true);
        graphComponent.setConnectable(false);
        graphComponent.getGraphControl().addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                if (e.isControlDown()) {
                    if (e.getWheelRotation() < 0) {
                        graphComponent.zoomIn();
                    } else {
                        graphComponent.zoomOut();
                    }
                }
            }
        });
        
        add(graphComponent, BorderLayout.CENTER);
    }
    
    public void refresh(DatabaseManager db, String focusTable) {
        this.dbManager = db;
        if (db == null) {
            graph.getModel().beginUpdate();
            try {
                graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));
            } finally {
                graph.getModel().endUpdate();
            }
            searchDropdown.removeAllItems();
            return;
        }
        
        loadingLabel.setVisible(true);
        searchBtn.setEnabled(false);
        showAllBtn.setEnabled(false);
        searchDropdown.setEnabled(false);
        
        // Run database queries on background thread to prevent UI lag
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            List<String> includedTables = new ArrayList<>();
            Map<String, List<String>> tableColsMap = new HashMap<>();
            Map<String, Map<String, String[]>> tableFkMaps = new HashMap<>();
            List<String> allTables;
            
            @Override
            protected Void doInBackground() throws Exception {
                allTables = db.getTableNames();
                
                // Pre-fetch FKs for all tables
                for (String t : allTables) {
                    tableFkMaps.put(t, db.getForeignKeys(t));
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
                        includedTables.add(exactFocus);
                        
                        // 1. Tables exactFocus points to
                        Map<String, String[]> fks = tableFkMaps.get(exactFocus);
                        for (String[] pkInfo : fks.values()) {
                            String target = pkInfo[0];
                            if (!includedTables.contains(target) && allTables.contains(target)) {
                                includedTables.add(target);
                            }
                        }
                        
                        // 2. Tables that point to exactFocus
                        for (String t : allTables) {
                            if (t.equals(exactFocus)) continue;
                            Map<String, String[]> tFks = tableFkMaps.get(t);
                            for (String[] pkInfo : tFks.values()) {
                                if (pkInfo[0].equalsIgnoreCase(exactFocus)) {
                                    if (!includedTables.contains(t)) includedTables.add(t);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    includedTables.addAll(allTables);
                }
                
                // Pre-fetch columns for included tables
                for (String t : includedTables) {
                    tableColsMap.put(t, db.getColumnNames(t));
                }
                
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get(); // ensure no exceptions
                    
                    // Update Dropdown if empty
                    if (searchDropdown.getItemCount() <= 1) { // Maybe just empty
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
                        
                        // Create Vertices
                        for (String t : includedTables) {
                            List<String> cols = tableColsMap.get(t);
                            StringBuilder sb = new StringBuilder();
                            sb.append("  ").append(t.toUpperCase()).append("\n");
                            sb.append(" ----------------------------------------\n");
                            for (String c : cols) {
                                sb.append("   ").append(c).append("\n");
                            }
                            
                            int height = 50 + (cols.size() * 20); // Massive multiplier for extreme font scaling
                            int width = Math.max(220, t.length() * 12);
                            
                            // Using a dark slate theme for tables so it looks crisp and text is visible
                            String style = "shape=rectangle;fillColor=#2b2d30;fontColor=#a9b7c6;strokeColor=#4e5254;verticalAlign=top;align=left;spacingTop=8;fontSize=12;fontFamily=Monospaced;";
                            Object v = graph.insertVertex(parent, null, sb.toString(), 0, 0, width, height, style);
                            vertexMap.put(t, v);
                        }
                        
                        // Create Edges
                        for (String t : includedTables) {
                            Object vSource = vertexMap.get(t);
                            if (vSource == null) continue;
                            
                            Map<String, String[]> fks = tableFkMaps.get(t);
                            for (Map.Entry<String, String[]> entry : fks.entrySet()) {
                                String fkCol = entry.getKey();
                                String pkTable = entry.getValue()[0];
                                String pkCol = entry.getValue()[1];
                                
                                Object vTarget = vertexMap.get(pkTable);
                                if (vTarget != null) {
                                    String edgeLabel = fkCol + " -> " + pkCol;
                                    graph.insertEdge(parent, null, edgeLabel, vSource, vTarget,
                                            "edgeStyle=orthogonalEdgeStyle;rounded=1;strokeColor=#404040;fontColor=#606060;fontSize=10;");
                                }
                            }
                        }
                        
                        // Apply Layout
                        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
                        layout.setIntraCellSpacing(50);
                        layout.setInterRankCellSpacing(100);
                        layout.execute(parent);
                        
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
}
