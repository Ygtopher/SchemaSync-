package com.dbtool.panels;

import com.dbtool.DatabaseManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.awt.*;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class DependencyGraphPanel extends JPanel {

    private DatabaseManager dbManager;
    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JLabel statusLabel;
    private JComboBox<String> layoutDropdown;
    private JTextField searchField;

    // Cache of extracted data so we can rebuild the tree instantly when searching
    private List<String> allTables = new ArrayList<>();
    private List<String> allViews = new ArrayList<>();
    private List<String> allProcedures = new ArrayList<>();
    private List<String> allTriggers = new ArrayList<>();

    private Map<String, List<String>> tableImportedFks = new HashMap<>(); // Table -> List of "col -> ParentTable"
    private Map<String, List<String>> tableExportedFks = new HashMap<>(); // Table -> List of "ChildTable(col)"
    private Map<String, List<String>> viewDependencies = new HashMap<>(); // View -> List of Tables it uses
    private Map<String, List<String>> tableUsedByViews = new HashMap<>(); // Table -> List of Views that use it
    private Map<String, List<String>> procedureDependencies = new HashMap<>(); // Procedure -> List of Tables it uses
    private Map<String, List<String>> tableUsedByProcedures = new HashMap<>(); // Table -> List of Procedures
    private Map<String, List<String>> triggerDependencies = new HashMap<>(); // Trigger -> Table it is on
    private Map<String, List<String>> tableTriggers = new HashMap<>();
        private Map<String, String> objectDefinitions = new HashMap<>();

    public DependencyGraphPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton refreshBtn = new JButton("Load Dependencies");
        refreshBtn.addActionListener(e -> loadData());
        
        searchField = new JTextField(20);
        searchField.setMaximumSize(new Dimension(200, 30));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { buildTreeFromCache(searchField.getText()); }
            public void removeUpdate(DocumentEvent e) { buildTreeFromCache(searchField.getText()); }
            public void changedUpdate(DocumentEvent e) { buildTreeFromCache(searchField.getText()); }
        });

        statusLabel = new JLabel(" Ready. Click 'Load Dependencies' to begin.");

        toolbar.add(refreshBtn);
        toolbar.addSeparator();
        toolbar.add(new JLabel(" Search Objects: "));
        toolbar.add(searchField);
        toolbar.addSeparator();
        toolbar.add(statusLabel);
        add(toolbar, BorderLayout.NORTH);

        // Tree
        rootNode = new DefaultMutableTreeNode("Database");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setShowsRootHandles(true);
        tree.setRootVisible(true);
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent me) {
                if (me.getClickCount() == 2) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                    if (node != null) {
                        String nodeStr = node.toString();
                        if (objectDefinitions.containsKey(nodeStr)) {
                            showDefinitionDialog(nodeStr, objectDefinitions.get(nodeStr));
                        }
                    }
                }
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    private void loadData() {
        if (dbManager == null || dbManager.connection == null) {
            statusLabel.setText(" Not connected to a database.");
            return;
        }
        statusLabel.setText(" Scanning database metadata... This may take a moment for large databases.");
        refreshButtonState(false);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                clearCache();

                Connection conn = dbManager.connection;
                DatabaseMetaData meta = conn.getMetaData();
                String catalog = conn.getCatalog();
                String schema = null;
                try { schema = conn.getSchema(); } catch (Exception ignored) {}

                // 1. Extract Tables and Views
                try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
                    while (rs.next()) {
                        String name = rs.getString("TABLE_NAME");
                        String type = rs.getString("TABLE_TYPE");
                        if (name == null || name.toLowerCase().startsWith("pg_") || name.toLowerCase().startsWith("sql_")) continue;
                        
                        if ("VIEW".equalsIgnoreCase(type)) {
                            allViews.add(name);
                        } else {
                            allTables.add(name);
                        }
                    }
                }

                // Initialize maps
                for (String t : allTables) {
                    tableImportedFks.put(t, new ArrayList<>());
                    tableExportedFks.put(t, new ArrayList<>());
                    tableUsedByViews.put(t, new ArrayList<>());
                    tableUsedByProcedures.put(t, new ArrayList<>());
                    tableTriggers.put(t, new ArrayList<>());
                }
                for (String v : allViews) viewDependencies.put(v, new ArrayList<>());

                // 2. Foreign Keys
                for (String tName : allTables) {
                    try (ResultSet rsFk = meta.getImportedKeys(catalog, schema, tName)) {
                        while (rsFk.next()) {
                            String pkTable = rsFk.getString("PKTABLE_NAME");
                            String pkCol = rsFk.getString("PKCOLUMN_NAME");
                            String fkCol = rsFk.getString("FKCOLUMN_NAME");
                            if (pkTable != null) {
                                tableImportedFks.get(tName).add(fkCol + " -> " + pkTable + " (" + pkCol + ")");
                                if (tableExportedFks.containsKey(pkTable)) {
                                    tableExportedFks.get(pkTable).add(tName + " (" + fkCol + ")");
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 3. Triggers
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_STATEMENT FROM INFORMATION_SCHEMA.TRIGGERS")) {
                    while (rs.next()) {
                        String trigName = rs.getString("TRIGGER_NAME");
                        String targetTable = rs.getString("EVENT_OBJECT_TABLE");
                        if (trigName != null && targetTable != null) {
                            allTriggers.add(trigName);
                            triggerDependencies.put(trigName, new ArrayList<>());
                            triggerDependencies.get(trigName).add(targetTable);
                            try {
                                String action = rs.getString("ACTION_STATEMENT");
                                if (action != null) objectDefinitions.put("Trigger: " + trigName, action);
                            } catch (Exception ignored) {}
                            
                            // Check ignoring case
                            String matchedTable = findCaseInsensitiveMatch(targetTable, allTables);
                            if (matchedTable != null) {
                                tableTriggers.get(matchedTable).add(trigName);
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // 4. View Dependencies (Heuristic)
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT TABLE_NAME, VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS")) {
                    while (rs.next()) {
                        String viewName = rs.getString("TABLE_NAME");
                        String def = rs.getString("VIEW_DEFINITION");
                        if (viewName != null && def != null) {
                            String viewMatch = findCaseInsensitiveMatch(viewName, allViews);
                            if (viewMatch != null) {
                                objectDefinitions.put("View: " + viewMatch, def);
                                String defLower = def.toLowerCase();
                                for (String tName : allTables) {
                                    if (tName.equalsIgnoreCase(viewName)) continue;
                                    if (Pattern.compile("\\\\b" + Pattern.quote(tName) + "\\\\b").matcher(defLower).find()) {
                                        viewDependencies.get(viewMatch).add(tName);
                                        tableUsedByViews.get(tName).add(viewMatch);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // 5. Routines
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT ROUTINE_NAME, ROUTINE_DEFINITION, ROUTINE_SCHEMA FROM INFORMATION_SCHEMA.ROUTINES")) {
                    while (rs.next()) {
                        String routName = rs.getString("ROUTINE_NAME");
                        String schemaName = rs.getString("ROUTINE_SCHEMA");
                        if (schemaName != null && (schemaName.equalsIgnoreCase("pg_catalog") || schemaName.equalsIgnoreCase("information_schema"))) {
                            continue; // Skip system routines
                        }
                        String def = rs.getString("ROUTINE_DEFINITION");
                        if (def == null) def = "Definition not available or is an internal function.";
                        
                        if (routName != null) {
                            allProcedures.add(routName);
                            procedureDependencies.put(routName, new ArrayList<>());
                            objectDefinitions.put("Procedure: " + routName, def);
                            
                            String defLower = def.toLowerCase();
                            for (String tName : allTables) {
                                if (Pattern.compile("\\\\b" + Pattern.quote(tName) + "\\\\b").matcher(defLower).find()) {
                                    procedureDependencies.get(routName).add(tName);
                                    tableUsedByProcedures.get(tName).add(routName);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // 6. Enrich Trigger Definitions with their underlying functions
                for (String trigName : allTriggers) {
                    String trigKey = "Trigger: " + trigName;
                    String def = objectDefinitions.get(trigKey);
                    if (def != null) {
                        String defUpper = def.toUpperCase();
                        if (defUpper.contains("EXECUTE FUNCTION") || defUpper.contains("EXECUTE PROCEDURE")) {
                            String defLower = def.toLowerCase();
                            for (String routName : allProcedures) {
                                if (defLower.contains(routName.toLowerCase())) {
                                    String funcDef = objectDefinitions.get("Procedure: " + routName);
                                    if (funcDef != null && !funcDef.isEmpty()) {
                                        objectDefinitions.put(trigKey, def + "\n\n/* --- FUNCTION BODY: " + routName + " --- */\n" + funcDef);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                
                // 7. Postgres fallback for trigger functions missing in INFORMATION_SCHEMA
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT proname, prosrc, n.nspname FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace")) {
                    while (rs.next()) {
                        String proname = rs.getString("proname");
                        String prosrc = rs.getString("prosrc");
                        String nspname = rs.getString("nspname");
                        
                        if (proname != null && prosrc != null) {
                            // Update procedure definition if it's a user procedure
                            if (nspname != null && !nspname.equalsIgnoreCase("pg_catalog") && !nspname.equalsIgnoreCase("information_schema")) {
                                if (allProcedures.contains(proname)) {
                                    objectDefinitions.put("Procedure: " + proname, prosrc);
                                    String prosrcLower = prosrc.toLowerCase();
                                    for (String tName : allTables) {
                                        if (java.util.regex.Pattern.compile("\b" + java.util.regex.Pattern.quote(tName) + "\b").matcher(prosrcLower).find()) {
                                            if (!procedureDependencies.get(proname).contains(tName)) {
                                                procedureDependencies.get(proname).add(tName);
                                            }
                                            if (!tableUsedByProcedures.get(tName).contains(proname)) {
                                                tableUsedByProcedures.get(tName).add(proname);
                                            }
                                        }
                                    }
                                }
                            }
                            
                            for (String trigName : allTriggers) {
                                String trigKey = "Trigger: " + trigName;
                                String def = objectDefinitions.get(trigKey);
                                if (def != null && (def.toLowerCase().contains("execute function") || def.toLowerCase().contains("execute procedure"))) {
                                    if (def.toLowerCase().contains(proname.toLowerCase()) && !def.contains("/* --- FUNCTION BODY")) {
                                        objectDefinitions.put(trigKey, def + "\n\n/* --- FUNCTION BODY (from pg_proc): " + proname + " --- */\n" + prosrc);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
                
                
                // 8. Generate Table Schema definitions
                try (ResultSet rsCols = meta.getColumns(catalog, schema, "%", "%")) {
                    Map<String, StringBuilder> tableSchemas = new HashMap<>();
                    while (rsCols.next()) {
                        String tName = rsCols.getString("TABLE_NAME");
                        if (!allTables.contains(tName) && !allTables.contains(tName.toLowerCase())) continue;
                        String colName = rsCols.getString("COLUMN_NAME");
                        String typeName = rsCols.getString("TYPE_NAME");
                        int size = rsCols.getInt("COLUMN_SIZE");
                        String isNullable = rsCols.getString("IS_NULLABLE"); // "YES" or "NO"
                        
                        // Use exact case for key to match tree nodes
                        String exactTName = findCaseInsensitiveMatch(tName, allTables);
                        if (exactTName == null) exactTName = tName;
                        
                        tableSchemas.putIfAbsent(exactTName, new StringBuilder("CREATE TABLE " + exactTName + " (\n"));
                        
                        StringBuilder sb = tableSchemas.get(exactTName);
                        sb.append("    ").append(colName).append(" ").append(typeName);
                        if (size > 0 && typeName != null && !typeName.toLowerCase().contains("date") && !typeName.toLowerCase().contains("time") && !typeName.toLowerCase().contains("int") && !typeName.toLowerCase().contains("serial")) {
                            sb.append("(").append(size).append(")");
                        }
                        if ("NO".equalsIgnoreCase(isNullable)) {
                            sb.append(" NOT NULL");
                        }
                        sb.append(",\n");
                    }
                    
                    for (Map.Entry<String, StringBuilder> entry : tableSchemas.entrySet()) {
                        String tName = entry.getKey();
                        StringBuilder sb = entry.getValue();
                        // Remove trailing comma
                        if (sb.length() > 2) {
                            sb.setLength(sb.length() - 2);
                        }
                        sb.append("\n);");
                        objectDefinitions.put("Table: " + tName, sb.toString());
                    }
                } catch (Exception ignored) {}

                Collections.sort(allTables);
                Collections.sort(allViews);
                Collections.sort(allProcedures);
                Collections.sort(allTriggers);

                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    buildTreeFromCache("");
                    statusLabel.setText(" Dependencies loaded successfully.");
                } catch (Exception ex) {
                    statusLabel.setText(" Error: " + ex.getMessage());
                } finally {
                    refreshButtonState(true);
                }
            }
        };
        worker.execute();
    }

    private String findCaseInsensitiveMatch(String target, List<String> list) {
        for (String item : list) {
            if (item.equalsIgnoreCase(target)) return item;
        }
        return null;
    }

    private void clearCache() {
        allTables.clear();
        allViews.clear();
        allProcedures.clear();
        allTriggers.clear();
        tableImportedFks.clear();
        tableExportedFks.clear();
        viewDependencies.clear();
        tableUsedByViews.clear();
        procedureDependencies.clear();
        tableUsedByProcedures.clear();
        triggerDependencies.clear();
        tableTriggers.clear();
        objectDefinitions.clear();
    }

    private void buildTreeFromCache(String filterQuery) {
        rootNode.removeAllChildren();
        String q = filterQuery != null ? filterQuery.toLowerCase() : "";

        // Tables Category
        DefaultMutableTreeNode tablesRoot = new DefaultMutableTreeNode("Tables (" + allTables.size() + ")");
        for (String t : allTables) {
            if (!q.isEmpty() && !t.toLowerCase().contains(q)) continue;
            
            DefaultMutableTreeNode tableNode = new DefaultMutableTreeNode("Table: " + t);
            
            // Depends On
            List<String> imported = tableImportedFks.getOrDefault(t, Collections.emptyList());
            if (!imported.isEmpty()) {
                DefaultMutableTreeNode outFks = new DefaultMutableTreeNode("⬆️ Depends On (Outgoing FKs)");
                for (String fk : imported) outFks.add(new DefaultMutableTreeNode(fk));
                tableNode.add(outFks);
            }
            
            // Used By
            List<String> exported = tableExportedFks.getOrDefault(t, Collections.emptyList());
            if (!exported.isEmpty()) {
                DefaultMutableTreeNode inFks = new DefaultMutableTreeNode("⬇️ Used By (Incoming FKs)");
                for (String fk : exported) inFks.add(new DefaultMutableTreeNode(fk));
                tableNode.add(inFks);
            }

            // Views that use this
            List<String> views = tableUsedByViews.getOrDefault(t, Collections.emptyList());
            if (!views.isEmpty()) {
                DefaultMutableTreeNode vNode = new DefaultMutableTreeNode("👁️ Views that query this table");
                for (String v : views) vNode.add(new DefaultMutableTreeNode("View: " + v));
                tableNode.add(vNode);
            }

            // Procs/Triggers
            List<String> procs = tableUsedByProcedures.getOrDefault(t, Collections.emptyList());
            List<String> trigs = tableTriggers.getOrDefault(t, Collections.emptyList());
            if (!procs.isEmpty() || !trigs.isEmpty()) {
                DefaultMutableTreeNode ptNode = new DefaultMutableTreeNode("⚙️ Procedures & Triggers");
                for (String tr : trigs) ptNode.add(new DefaultMutableTreeNode("Trigger: " + tr));
                for (String p : procs) ptNode.add(new DefaultMutableTreeNode("Procedure: " + p));
                tableNode.add(ptNode);
            }

            tablesRoot.add(tableNode);
        }

        // Views Category
        DefaultMutableTreeNode viewsRoot = new DefaultMutableTreeNode("Views (" + allViews.size() + ")");
        for (String v : allViews) {
            if (!q.isEmpty() && !v.toLowerCase().contains(q)) continue;
            DefaultMutableTreeNode viewNode = new DefaultMutableTreeNode("View: " + v);
            List<String> deps = viewDependencies.getOrDefault(v, Collections.emptyList());
            if (!deps.isEmpty()) {
                DefaultMutableTreeNode dNode = new DefaultMutableTreeNode("Queries Tables");
                for (String d : deps) dNode.add(new DefaultMutableTreeNode("Table: " + d));
                viewNode.add(dNode);
            }
            viewsRoot.add(viewNode);
        }

        // Procedures Category
        DefaultMutableTreeNode procsRoot = new DefaultMutableTreeNode("Procedures (" + allProcedures.size() + ")");
        for (String p : allProcedures) {
            if (!q.isEmpty() && !p.toLowerCase().contains(q)) continue;
            DefaultMutableTreeNode pNode = new DefaultMutableTreeNode("Procedure: " + p);
            List<String> deps = procedureDependencies.getOrDefault(p, Collections.emptyList());
            if (!deps.isEmpty()) {
                DefaultMutableTreeNode dNode = new DefaultMutableTreeNode("Uses Tables");
                for (String d : deps) dNode.add(new DefaultMutableTreeNode("Table: " + d));
                pNode.add(dNode);
            }
            procsRoot.add(pNode);
        }

        // Triggers Category
        DefaultMutableTreeNode trigsRoot = new DefaultMutableTreeNode("Triggers (" + allTriggers.size() + ")");
        for (String tr : allTriggers) {
            if (!q.isEmpty() && !tr.toLowerCase().contains(q)) continue;
            DefaultMutableTreeNode tNode = new DefaultMutableTreeNode("Trigger: " + tr);
            List<String> deps = triggerDependencies.getOrDefault(tr, Collections.emptyList());
            if (!deps.isEmpty()) {
                DefaultMutableTreeNode dNode = new DefaultMutableTreeNode("Attached to Table");
                for (String d : deps) dNode.add(new DefaultMutableTreeNode("Table: " + d));
                tNode.add(dNode);
            }
            trigsRoot.add(tNode);
        }

        if (tablesRoot.getChildCount() > 0) rootNode.add(tablesRoot);
        if (viewsRoot.getChildCount() > 0) rootNode.add(viewsRoot);
        if (procsRoot.getChildCount() > 0) rootNode.add(procsRoot);
        if (trigsRoot.getChildCount() > 0) rootNode.add(trigsRoot);

        treeModel.reload();

        // If there's a search query, automatically expand all folders so user can see matches
        if (!q.isEmpty()) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        }
    }

    private void refreshButtonState(boolean enabled) {
        for (Component c : ((JToolBar) getComponent(0)).getComponents()) {
            if (c instanceof JButton) {
                c.setEnabled(enabled);
            }
        }
    }

    private void showDefinitionDialog(String title, String definition) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        
        org.fife.ui.rsyntaxtextarea.RSyntaxTextArea textArea = new org.fife.ui.rsyntaxtextarea.RSyntaxTextArea(20, 60);
        textArea.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL);
        textArea.setCodeFoldingEnabled(true);
        textArea.setEditable(false);
        textArea.setText(definition);
        textArea.setCaretPosition(0);
        
        dialog.add(new org.fife.ui.rtextarea.RTextScrollPane(textArea), BorderLayout.CENTER);
        
        JPanel btnPanel = new JPanel();
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        btnPanel.add(closeBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
