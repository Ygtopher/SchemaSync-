package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.DatabaseManager.ColumnDefinition;
import com.dbtool.util.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

/**
 * Instant ORM & Entity Code Generator
 * Generates Spring Boot JPA, Java Records, TypeScript Prisma/Drizzle,
 * Python SQLAlchemy/Pydantic, and C# EF Core models directly from database tables.
 * 
 * Part of SchemaSync Tier 2 Developer Productivity Suite.
 */
public class EntityCodeGeneratorDialog extends JDialog {

    private final DatabaseManager dbManager;
    private final JComboBox<String> tableCombo;
    private final JComboBox<String> targetCombo;
    private final JTextField packageField;
    private final JCheckBox lombokCheck;
    private final JCheckBox validationCheck;
    private final JCheckBox camelCaseCheck;
    private final RSyntaxTextArea codeArea;

    public EntityCodeGeneratorDialog(Frame owner, DatabaseManager dbManager, String initialTable) {
        super(owner, "💻 Instant ORM & Entity Code Generator", false);
        this.dbManager = dbManager;

        setSize(980, 720);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(10, 6));
        headerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        headerPanel.setBackground(new Color(248, 250, 252));
        if (ThemeManager.isDarkMode()) {
            headerPanel.setBackground(new Color(30, 41, 59));
        }

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);
        JLabel titleLabel = new JLabel("💻 Instant ORM & Entity Code Generator");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel subtitleLabel = new JLabel("Export clean, production-ready models for Spring Boot JPA, TypeScript, Python, and C# EF Core.");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        titleBox.add(titleLabel);
        titleBox.add(subtitleLabel);
        headerPanel.add(titleBox, BorderLayout.WEST);

        // Controls Bar
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        controlsPanel.setOpaque(false);

        controlsPanel.add(new JLabel("Table:"));
        List<String> tables = dbManager.getTableNames();
        tableCombo = new JComboBox<>(tables.toArray(new String[0]));
        if (initialTable != null && tables.contains(initialTable)) {
            tableCombo.setSelectedItem(initialTable);
        }
        tableCombo.setPreferredSize(new Dimension(180, 26));
        tableCombo.addActionListener(e -> generateCode());
        controlsPanel.add(tableCombo);

        controlsPanel.add(new JLabel("Target:"));
        targetCombo = new JComboBox<>(new String[]{
                "Java: Spring Boot JPA (@Entity)",
                "Java: Immutable Record (Java 17/21)",
                "TypeScript: Prisma Schema (model)",
                "TypeScript: Pure Interface",
                "TypeScript: Drizzle ORM Schema",
                "Python: SQLAlchemy Declarative Model",
                "Python: Pydantic Schema (v2)",
                "C#: .NET Entity Framework Core"
        });
        targetCombo.setPreferredSize(new Dimension(240, 26));
        targetCombo.addActionListener(e -> generateCode());
        controlsPanel.add(targetCombo);

        controlsPanel.add(new JLabel("Package/Namespace:"));
        packageField = new JTextField("com.example.model", 14);
        packageField.addActionListener(e -> generateCode());
        controlsPanel.add(packageField);

        lombokCheck = new JCheckBox("Lombok", true);
        lombokCheck.addActionListener(e -> generateCode());
        controlsPanel.add(lombokCheck);

        validationCheck = new JCheckBox("Validations", true);
        validationCheck.addActionListener(e -> generateCode());
        controlsPanel.add(validationCheck);

        camelCaseCheck = new JCheckBox("camelCase Fields", true);
        camelCaseCheck.addActionListener(e -> generateCode());
        controlsPanel.add(camelCaseCheck);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(headerPanel, BorderLayout.NORTH);
        northPanel.add(controlsPanel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // Center Code Editor
        codeArea = new RSyntaxTextArea();
        codeArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        codeArea.setCodeFoldingEnabled(true);
        codeArea.setAntiAliasingEnabled(true);
        RTextScrollPane scroll = new RTextScrollPane(codeArea);
        add(scroll, BorderLayout.CENTER);

        // Bottom Actions
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton copyBtn = new JButton("📋 Copy Code");
        copyBtn.setFont(copyBtn.getFont().deriveFont(Font.BOLD));
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(codeArea.getText()), null);
            JOptionPane.showMessageDialog(this, "Code copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton exportBtn = new JButton("💾 Save to File...");
        exportBtn.addActionListener(e -> exportToFile());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        bottomBar.add(copyBtn);
        bottomBar.add(exportBtn);
        bottomBar.add(closeBtn);
        add(bottomBar, BorderLayout.SOUTH);

        generateCode();
    }

    private void generateCode() {
        String tbl = (String) tableCombo.getSelectedItem();
        if (tbl == null || tbl.trim().isEmpty()) {
            codeArea.setText("// No table selected.");
            return;
        }

        List<ColumnDefinition> cols = dbManager.getDetailedColumns(tbl);
        String target = (String) targetCombo.getSelectedItem();
        String pkg = packageField.getText().trim();
        boolean useLombok = lombokCheck.isSelected();
        boolean useValidation = validationCheck.isSelected();
        boolean useCamelCase = camelCaseCheck.isSelected();

        String generated = "";
        if (target.contains("Spring Boot JPA")) {
            codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            generated = generateSpringBootJpa(tbl, cols, pkg, useLombok, useValidation, useCamelCase);
        } else if (target.contains("Immutable Record")) {
            codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            generated = generateJavaRecord(tbl, cols, pkg, useCamelCase);
        } else if (target.contains("Prisma")) {
            codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
            generated = generatePrisma(tbl, cols, useCamelCase);
        } else if (target.contains("Pure Interface")) {
            codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
            generated = generateTsInterface(tbl, cols, useCamelCase);
        } else if (target.contains("Drizzle")) {
            codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
            generated = generateDrizzle(tbl, cols, useCamelCase);
        } else if (target.contains("SQLAlchemy")) {
            codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
            generated = generateSqlAlchemy(tbl, cols, useCamelCase);
        } else if (target.contains("Pydantic")) {
            codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
            generated = generatePydantic(tbl, cols, useCamelCase);
        } else if (target.contains("Entity Framework")) {
            codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSHARP);
            generated = generateEfCore(tbl, cols, pkg, useCamelCase);
        }

        codeArea.setText(generated);
        codeArea.setCaretPosition(0);
    }

    private String toPascalCase(String s) {
        if (s == null || s.isEmpty()) return "";
        String clean = s.contains(".") ? s.split("\\.", 2)[1] : s;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : clean.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String toCamelCase(String s) {
        String pascal = toPascalCase(s);
        if (pascal.isEmpty()) return "";
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String mapToJavaType(ColumnDefinition col) {
        String t = col.typeName != null ? col.typeName.toLowerCase() : "";
        if (t.contains("int8") || t.contains("bigint")) return "Long";
        if (t.contains("int") || t.contains("serial")) return "Integer";
        if (t.contains("numeric") || t.contains("decimal")) return "java.math.BigDecimal";
        if (t.contains("float") || t.contains("real")) return "Float";
        if (t.contains("double")) return "Double";
        if (t.contains("bool")) return "Boolean";
        if (t.contains("date")) return "java.time.LocalDate";
        if (t.contains("time") && !t.contains("timestamp")) return "java.time.LocalTime";
        if (t.contains("timestamp") || t.contains("datetime")) return "java.time.LocalDateTime";
        if (t.contains("uuid")) return "java.util.UUID";
        if (t.contains("bytea") || t.contains("blob")) return "byte[]";
        return "String";
    }

    private String generateSpringBootJpa(String tbl, List<ColumnDefinition> cols, String pkg, boolean lombok, boolean validation, boolean camelCase) {
        String className = toPascalCase(tbl);
        StringBuilder sb = new StringBuilder();
        if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");

        sb.append("import jakarta.persistence.*;\n");
        if (validation) sb.append("import jakarta.validation.constraints.*;\n");
        if (lombok) sb.append("import lombok.*;\n");
        sb.append("import java.io.Serializable;\n\n");

        sb.append("/**\n * Entity model for table '").append(tbl).append("'.\n * Generated by SchemaSync.\n */\n");
        if (lombok) {
            sb.append("@Data\n@Builder\n@NoArgsConstructor\n@AllArgsConstructor\n");
        }
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(tbl).append("\")\n");
        sb.append("public class ").append(className).append(" implements Serializable {\n\n");

        for (ColumnDefinition c : cols) {
            String fieldName = camelCase ? toCamelCase(c.name) : c.name;
            String javaType = mapToJavaType(c);

            if (c.isPrimaryKey) {
                sb.append("    @Id\n");
                if (c.isAutoIncrement || c.name.toLowerCase().contains("id")) {
                    sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
                }
            }
            if (validation && !c.isNullable && !c.isPrimaryKey) {
                sb.append("    @NotNull\n");
            }
            if (validation && javaType.equals("String") && c.size > 0 && c.size < 10000) {
                sb.append("    @Size(max = ").append(c.size).append(")\n");
            }
            sb.append("    @Column(name = \"").append(c.name).append("\"");
            if (!c.isNullable) sb.append(", nullable = false");
            if (c.size > 0 && javaType.equals("String")) sb.append(", length = ").append(c.size);
            sb.append(")\n");

            sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }

        if (!lombok) {
            // Generate standard getters & setters
            for (ColumnDefinition c : cols) {
                String fieldName = camelCase ? toCamelCase(c.name) : c.name;
                String capName = toPascalCase(fieldName);
                String javaType = mapToJavaType(c);

                sb.append("    public ").append(javaType).append(" get").append(capName).append("() {\n");
                sb.append("        return this.").append(fieldName).append(";\n");
                sb.append("    }\n\n");

                sb.append("    public void set").append(capName).append("(").append(javaType).append(" ").append(fieldName).append(") {\n");
                sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
                sb.append("    }\n\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String generateJavaRecord(String tbl, List<ColumnDefinition> cols, String pkg, boolean camelCase) {
        String recordName = toPascalCase(tbl) + "Record";
        StringBuilder sb = new StringBuilder();
        if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");

        sb.append("/**\n * Immutable record for '").append(tbl).append("'.\n * Generated by SchemaSync.\n */\n");
        sb.append("public record ").append(recordName).append("(\n");
        for (int i = 0; i < cols.size(); i++) {
            ColumnDefinition c = cols.get(i);
            String fieldName = camelCase ? toCamelCase(c.name) : c.name;
            String javaType = mapToJavaType(c);
            sb.append("    ").append(javaType).append(" ").append(fieldName);
            if (i < cols.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(") {}\n");
        return sb.toString();
    }

    private String generatePrisma(String tbl, List<ColumnDefinition> cols, boolean camelCase) {
        String modelName = toPascalCase(tbl);
        StringBuilder sb = new StringBuilder();
        sb.append("// Prisma schema model for '").append(tbl).append("'\n");
        sb.append("model ").append(modelName).append(" {\n");
        for (ColumnDefinition c : cols) {
            String fieldName = camelCase ? toCamelCase(c.name) : c.name;
            String t = c.typeName != null ? c.typeName.toLowerCase() : "";
            String pType = "String";
            if (t.contains("int")) pType = "Int";
            else if (t.contains("bigint")) pType = "BigInt";
            else if (t.contains("numeric") || t.contains("decimal") || t.contains("float") || t.contains("double")) pType = "Decimal";
            else if (t.contains("bool")) pType = "Boolean";
            else if (t.contains("date") || t.contains("time")) pType = "DateTime";

            if (c.isNullable && !c.isPrimaryKey) pType += "?";

            sb.append("  ").append(String.format("%-18s", fieldName)).append(String.format("%-12s", pType));
            if (c.isPrimaryKey) sb.append(" @id");
            if (c.isAutoIncrement) sb.append(" @default(autoincrement())");
            if (!fieldName.equals(c.name)) sb.append(" @map(\"").append(c.name).append("\")");
            sb.append("\n");
        }
        sb.append("\n  @@map(\"").append(tbl).append("\")\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateTsInterface(String tbl, List<ColumnDefinition> cols, boolean camelCase) {
        String ifaceName = toPascalCase(tbl);
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n * TypeScript interface for '").append(tbl).append("'.\n */\n");
        sb.append("export interface ").append(ifaceName).append(" {\n");
        for (ColumnDefinition c : cols) {
            String fieldName = camelCase ? toCamelCase(c.name) : c.name;
            String t = c.typeName != null ? c.typeName.toLowerCase() : "";
            String tsType = "string";
            if (t.contains("int") || t.contains("numeric") || t.contains("decimal") || t.contains("float") || t.contains("double")) tsType = "number";
            else if (t.contains("bool")) tsType = "boolean";
            else if (t.contains("date") || t.contains("time")) tsType = "Date";

            sb.append("  ").append(fieldName);
            if (c.isNullable && !c.isPrimaryKey) sb.append("?");
            sb.append(": ").append(tsType).append(";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String generateDrizzle(String tbl, List<ColumnDefinition> cols, boolean camelCase) {
        String varName = toCamelCase(tbl);
        StringBuilder sb = new StringBuilder();
        sb.append("import { pgTable, serial, integer, text, varchar, timestamp, boolean, decimal } from 'drizzle-orm/pg-core';\n\n");
        sb.append("export const ").append(varName).append(" = pgTable('").append(tbl).append("', {\n");
        for (ColumnDefinition c : cols) {
            String fieldName = camelCase ? toCamelCase(c.name) : c.name;
            String t = c.typeName != null ? c.typeName.toLowerCase() : "";
            sb.append("  ").append(fieldName).append(": ");
            if (c.isPrimaryKey && (c.isAutoIncrement || t.contains("serial"))) {
                sb.append("serial('").append(c.name).append("').primaryKey()");
            } else if (t.contains("int")) {
                sb.append("integer('").append(c.name).append("')");
                if (c.isPrimaryKey) sb.append(".primaryKey()");
                if (!c.isNullable) sb.append(".notNull()");
            } else if (t.contains("numeric") || t.contains("decimal")) {
                sb.append("decimal('").append(c.name).append("')");
                if (!c.isNullable) sb.append(".notNull()");
            } else if (t.contains("bool")) {
                sb.append("boolean('").append(c.name).append("')");
                if (!c.isNullable) sb.append(".notNull()");
            } else if (t.contains("date") || t.contains("time")) {
                sb.append("timestamp('").append(c.name).append("')");
                if (!c.isNullable) sb.append(".notNull()");
            } else {
                sb.append("text('").append(c.name).append("')");
                if (!c.isNullable) sb.append(".notNull()");
            }
            sb.append(",\n");
        }
        sb.append("});\n");
        return sb.toString();
    }

    private String generateSqlAlchemy(String tbl, List<ColumnDefinition> cols, boolean camelCase) {
        String className = toPascalCase(tbl);
        StringBuilder sb = new StringBuilder();
        sb.append("from sqlalchemy import Column, Integer, BigInteger, String, Numeric, Boolean, DateTime, Date\n");
        sb.append("from sqlalchemy.ext.declarative import declarative_base\n\n");
        sb.append("Base = declarative_base()\n\n");
        sb.append("class ").append(className).append("(Base):\n");
        sb.append("    __tablename__ = '").append(tbl).append("'\n\n");
        for (ColumnDefinition c : cols) {
            String fieldName = camelCase ? toCamelCase(c.name) : c.name;
            String t = c.typeName != null ? c.typeName.toLowerCase() : "";
            String colType = "String";
            if (t.contains("bigint")) colType = "BigInteger";
            else if (t.contains("int")) colType = "Integer";
            else if (t.contains("numeric") || t.contains("decimal")) colType = "Numeric";
            else if (t.contains("bool")) colType = "Boolean";
            else if (t.contains("date")) colType = "Date";
            else if (t.contains("time")) colType = "DateTime";

            sb.append("    ").append(fieldName).append(" = Column(");
            if (!fieldName.equals(c.name)) sb.append("'").append(c.name).append("', ");
            sb.append(colType);
            if (c.isPrimaryKey) sb.append(", primary_key=True");
            if (!c.isNullable && !c.isPrimaryKey) sb.append(", nullable=False");
            sb.append(")\n");
        }
        return sb.toString();
    }

    private String generatePydantic(String tbl, List<ColumnDefinition> cols, boolean camelCase) {
        String schemaName = toPascalCase(tbl) + "Schema";
        StringBuilder sb = new StringBuilder();
        sb.append("from pydantic import BaseModel, ConfigDict\n");
        sb.append("from typing import Optional\n");
        sb.append("from datetime import datetime, date\n");
        sb.append("from decimal import Decimal\n\n");
        sb.append("class ").append(schemaName).append("(BaseModel):\n");
        sb.append("    model_config = ConfigDict(from_attributes=True)\n\n");
        for (ColumnDefinition c : cols) {
            String fieldName = camelCase ? toCamelCase(c.name) : c.name;
            String t = c.typeName != null ? c.typeName.toLowerCase() : "";
            String pyType = "str";
            if (t.contains("int")) pyType = "int";
            else if (t.contains("numeric") || t.contains("decimal") || t.contains("float") || t.contains("double")) pyType = "Decimal";
            else if (t.contains("bool")) pyType = "bool";
            else if (t.contains("date") && !t.contains("time")) pyType = "date";
            else if (t.contains("time")) pyType = "datetime";

            sb.append("    ").append(fieldName).append(": ");
            if (c.isNullable && !c.isPrimaryKey) {
                sb.append("Optional[").append(pyType).append("] = None\n");
            } else {
                sb.append(pyType).append("\n");
            }
        }
        return sb.toString();
    }

    private String generateEfCore(String tbl, List<ColumnDefinition> cols, String pkg, boolean camelCase) {
        String className = toPascalCase(tbl);
        StringBuilder sb = new StringBuilder();
        sb.append("using System;\nusing System.ComponentModel.DataAnnotations;\nusing System.ComponentModel.DataAnnotations.Schema;\n\n");
        sb.append("namespace ").append(pkg.isEmpty() ? "SchemaSync.Models" : pkg).append("\n{\n");
        sb.append("    [Table(\"").append(tbl).append("\")]\n");
        sb.append("    public class ").append(className).append("\n    {\n");
        for (ColumnDefinition c : cols) {
            String propName = toPascalCase(c.name);
            String t = c.typeName != null ? c.typeName.toLowerCase() : "";
            String csType = "string";
            if (t.contains("bigint")) csType = "long";
            else if (t.contains("int")) csType = "int";
            else if (t.contains("numeric") || t.contains("decimal")) csType = "decimal";
            else if (t.contains("float") || t.contains("real")) csType = "float";
            else if (t.contains("double")) csType = "double";
            else if (t.contains("bool")) csType = "bool";
            else if (t.contains("date") || t.contains("time")) csType = "DateTime";
            else if (t.contains("uuid")) csType = "Guid";

            if (c.isNullable && !c.isPrimaryKey && !csType.equals("string")) {
                csType += "?";
            }

            if (c.isPrimaryKey) sb.append("        [Key]\n");
            sb.append("        [Column(\"").append(c.name).append("\")]\n");
            if (!c.isNullable && csType.equals("string")) sb.append("        [Required]\n");
            sb.append("        public ").append(csType).append(" ").append(propName).append(" { get; set; }\n\n");
        }
        sb.append("    }\n}\n");
        return sb.toString();
    }

    private void exportToFile() {
        String code = codeArea.getText();
        if (code == null || code.trim().isEmpty()) return;

        String target = (String) targetCombo.getSelectedItem();
        String ext = ".java";
        if (target.contains("TypeScript") || target.contains("Prisma")) ext = target.contains("Prisma") ? ".prisma" : ".ts";
        else if (target.contains("Python")) ext = ".py";
        else if (target.contains("C#")) ext = ".cs";

        String defaultName = toPascalCase((String) tableCombo.getSelectedItem()) + ext;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.write(code);
                JOptionPane.showMessageDialog(this, "File saved successfully:\n" + file.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
