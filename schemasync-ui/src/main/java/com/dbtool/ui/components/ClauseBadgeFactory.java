package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Factory and layout metrics for Fixed-Width Semantic Clause Badges and Aligned Grid Columns
 * across Visual Join Builder, Browse & Search, Subquery Builder, and Dual DB Compare.
 */
public final class ClauseBadgeFactory {

    // Fixed column widths so all stacked clause rows align vertically into a clean grid
    public static final int PREFIX_WIDTH   = 96;
    public static final int TABLE_WIDTH    = 148;
    public static final int DOT_WIDTH      = 18;
    public static final int COLUMN_WIDTH   = 168;
    public static final int OPERATOR_WIDTH = 94;
    public static final int VALUE_WIDTH    = 165;
    public static final int ROW_HEIGHT     = 26;

    public enum ClauseType {
        JOIN("🔗 JOIN", new Color(37, 99, 235), new Color(96, 165, 250)),
        ON("↳ ON", new Color(2, 132, 199), new Color(56, 189, 248)),
        AND_ON("↳ AND", new Color(2, 132, 199), new Color(56, 189, 248)),
        TABLE("➕ TABLE", new Color(79, 70, 229), new Color(129, 140, 248)),
        WHERE("🔍 WHERE", new Color(5, 150, 105), new Color(52, 211, 153)),
        ORDER_BY("↕ ORDER", new Color(124, 58, 237), new Color(167, 139, 250)),
        GROUP_BY("📂 GROUP", new Color(217, 119, 6), new Color(251, 191, 36)),
        HAVING("🎯 HAVING", new Color(225, 29, 72), new Color(251, 113, 133)),
        AGGREGATE("∑ AGG", new Color(13, 148, 136), new Color(45, 212, 191)),
        WINDOW("🪟 OVER", new Color(147, 51, 234), new Color(192, 132, 252));

        public final String label;
        public final Color lightColor;
        public final Color darkColor;

        ClauseType(String label, Color lightColor, Color darkColor) {
            this.label = label;
            this.lightColor = lightColor;
            this.darkColor = darkColor;
        }
    }

    private ClauseBadgeFactory() {}

    /**
     * Creates a fixed-width (96x26) rounded semantic clause badge that adapts dynamically
     * to Dark Mode and Solar Daylight (Light Mode).
     */
    public static JComponent createBadge(ClauseType type) {
        return new SemanticBadgeLabel(type);
    }

    /**
     * Styles a logic dropdown (AND / OR) to match the exact 96x26 fixed width of semantic clause badges.
     */
    public static void styleLogicDropdown(JComboBox<String> logicDropdown) {
        Dimension d = new Dimension(PREFIX_WIDTH, ROW_HEIGHT);
        logicDropdown.setPreferredSize(d);
        logicDropdown.setMinimumSize(d);
        logicDropdown.setMaximumSize(d);
        logicDropdown.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        logicDropdown.setToolTipText("Logical operator (AND / OR)");
    }

    /**
     * Applies a fixed column width (with 26px height) to a dropdown or text field so columns align across rows.
     */
    public static void setFixedColumnWidth(JComponent comp, int width) {
        Dimension d = new Dimension(width, ROW_HEIGHT);
        comp.setPreferredSize(d);
        comp.setMinimumSize(d);
    }

    /**
     * Creates a fixed-width (18x26) centered '.' separator between table and column dropdowns.
     */
    public static JLabel createDotSeparator() {
        JLabel lbl = new JLabel(".", SwingConstants.CENTER);
        lbl.setFont(ThemeManager.getUiFont(Font.BOLD, 13));
        Dimension d = new Dimension(DOT_WIDTH, ROW_HEIGHT);
        lbl.setPreferredSize(d);
        lbl.setMinimumSize(d);
        lbl.setMaximumSize(d);
        return lbl;
    }

    /**
     * Creates a fixed-width operator badge (e.g. '=' in JOIN ON clauses) that aligns with operator dropdowns.
     */
    public static JLabel createFixedOperatorLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean dark = ThemeManager.isDarkMode();
                g2.setColor(dark ? new Color(255, 255, 255, 18) : new Color(15, 23, 42, 14));
                g2.fillRoundRect(1, 2, getWidth() - 2, getHeight() - 4, 8, 8);
                g2.setColor(dark ? new Color(255, 255, 255, 40) : new Color(15, 23, 42, 35));
                g2.drawRoundRect(1, 2, getWidth() - 3, getHeight() - 5, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lbl.setFont(ThemeManager.getUiFont(Font.BOLD, 12));
        Dimension d = new Dimension(OPERATOR_WIDTH, ROW_HEIGHT);
        lbl.setPreferredSize(d);
        lbl.setMinimumSize(d);
        lbl.setMaximumSize(d);
        return lbl;
    }

    /**
     * Creates a compact, consistently sized '✕' remove button for clause rows.
     */
    public static JButton createRemoveButton(String tooltip) {
        JButton btn = new JButton("✕");
        btn.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        Dimension d = new Dimension(28, ROW_HEIGHT);
        btn.setPreferredSize(d);
        btn.setMinimumSize(d);
        btn.setMaximumSize(d);
        btn.setMargin(new Insets(1, 2, 1, 2));
        btn.setFocusPainted(false);
        btn.setForeground(new Color(220, 60, 60));
        if (tooltip != null && !tooltip.isEmpty()) {
            btn.setToolTipText(tooltip);
        }
        return btn;
    }

    /**
     * Styles a clause row panel with consistent horizontal/vertical spacing and subtle bottom divider.
     */
    public static void styleClauseRow(JPanel row) {
        row.setLayout(new WrapLayout(FlowLayout.LEFT, 4, 2));
        row.setBorder(new EmptyBorder(1, 4, 1, 4));
    }

    private static final class SemanticBadgeLabel extends JLabel {
        private final ClauseType type;

        SemanticBadgeLabel(ClauseType type) {
            super(type.label, SwingConstants.CENTER);
            this.type = type;
            setFont(ThemeManager.getUiFont(Font.BOLD, 11));
            Dimension d = new Dimension(PREFIX_WIDTH, ROW_HEIGHT);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean dark = ThemeManager.isDarkMode();
            Color accent = dark ? type.darkColor : type.lightColor;

            Color bg = dark
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 38)
                : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 26);
            Color border = dark
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110)
                : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 95);

            int w = getWidth();
            int h = getHeight();
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 8, 8));

            // Left accent bar inside the pill
            g2.setColor(accent);
            g2.fill(new RoundRectangle2D.Float(1, 1, 4, h - 2, 4, 4));

            g2.setColor(border);
            g2.draw(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 8, 8));

            setForeground(accent);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
