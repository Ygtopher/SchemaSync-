package com.dbtool.ui.components;

import com.dbtool.util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Set;
import java.util.function.Function;

/**
 * TablePlus / DataGrip-inspired semantic cell and header renderer for SchemaSync data grids.
 * Renders NULL badges, colored boolean pills, right-aligned monospace numbers,
 * zebra row striping, mouse-hover row highlights, and PK/FK column header icons.
 */
public class SmartTableCellRenderer extends DefaultTableCellRenderer {

    private static final String HOVER_ROW_CLIENT_PROP = "SchemaSync.hoverRow";

    private boolean isNullValue = false;
    private Boolean booleanValue = null;
    private final Function<String, Boolean> isPrimaryKeyColumn;
    private final Function<String, Boolean> isForeignKeyColumn;

    public SmartTableCellRenderer() {
        this(null, null);
    }

    public SmartTableCellRenderer(Function<String, Boolean> isPrimaryKeyColumn,
                                  Function<String, Boolean> isForeignKeyColumn) {
        this.isPrimaryKeyColumn = isPrimaryKeyColumn;
        this.isForeignKeyColumn = isForeignKeyColumn;
        setOpaque(true);
    }

    /**
     * Installs SmartTableCellRenderer, SmartHeaderRenderer, comfortable 26px row height,
     * and live mouse-hover row tracking onto any JTable.
     */
    public static void install(JTable table,
                               Function<String, Boolean> isPrimaryKeyColumn,
                               Function<String, Boolean> isForeignKeyColumn) {
        if (table == null) return;

        SmartTableCellRenderer cellRenderer = new SmartTableCellRenderer(isPrimaryKeyColumn, isForeignKeyColumn);
        table.setDefaultRenderer(Object.class, cellRenderer);
        table.setDefaultRenderer(Number.class, cellRenderer);
        table.setDefaultRenderer(Boolean.class, cellRenderer);

        int scaledRowHeight = Math.max(24, Math.round(26 * ThemeManager.getUiScaleFactor()));
        table.setRowHeight(scaledRowHeight);
        table.setIntercellSpacing(new Dimension(1, 1));

        JTableHeader header = table.getTableHeader();
        if (header != null) {
            TableCellRenderer baseHeaderRenderer = header.getDefaultRenderer();
            header.setDefaultRenderer(new SmartHeaderRenderer(baseHeaderRenderer, isPrimaryKeyColumn, isForeignKeyColumn));
        }

        // Attach hover tracking once
        if (!Boolean.TRUE.equals(table.getClientProperty("SchemaSync.smartInstalled"))) {
            table.putClientProperty("SchemaSync.smartInstalled", Boolean.TRUE);
            table.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = table.rowAtPoint(e.getPoint());
                    Integer prev = (Integer) table.getClientProperty(HOVER_ROW_CLIENT_PROP);
                    if (prev == null || prev != row) {
                        table.putClientProperty(HOVER_ROW_CLIENT_PROP, row);
                        table.repaint();
                    }
                }
            });
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    table.putClientProperty(HOVER_ROW_CLIENT_PROP, -1);
                    table.repaint();
                }
            });
        }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        boolean dark = ThemeManager.isDarkMode();
        setBorder(new EmptyBorder(2, 8, 2, 8));

        isNullValue = (value == null || "NULL".equals(value) || "<null>".equalsIgnoreCase(String.valueOf(value)));
        booleanValue = parseBoolean(value);

        // Background color: Selected > Hovered > Zebra Alternating
        if (isSelected) {
            setBackground(table.getSelectionBackground());
            setForeground(table.getSelectionForeground());
        } else {
            Integer hoverRow = (Integer) table.getClientProperty(HOVER_ROW_CLIENT_PROP);
            if (hoverRow != null && hoverRow == row) {
                setBackground(ThemeManager.getRowHoverColor());
            } else if (row % 2 == 1) {
                setBackground(ThemeManager.getZebraAlternateColor());
            } else {
                setBackground(table.getBackground());
            }
            setForeground(table.getForeground());
        }

        Font baseFont = table.getFont();
        if (baseFont == null) {
            baseFont = UIManager.getFont("Table.font");
        }

        if (isNullValue) {
            setText("NULL");
            setHorizontalAlignment(SwingConstants.LEFT);
            if (baseFont != null) {
                setFont(baseFont.deriveFont(Font.ITALIC, Math.max(11f, baseFont.getSize2D() - 1f)));
            }
            if (!isSelected) {
                setForeground(dark ? new Color(120, 130, 150) : new Color(148, 163, 184));
            }
        } else if (booleanValue != null) {
            setText(booleanValue ? "● true" : "○ false");
            setHorizontalAlignment(SwingConstants.CENTER);
            if (baseFont != null) {
                setFont(baseFont.deriveFont(Font.BOLD));
            }
            if (!isSelected) {
                setForeground(booleanValue
                        ? (dark ? new Color(74, 222, 128) : new Color(22, 163, 74))
                        : (dark ? new Color(148, 163, 184) : new Color(100, 116, 139)));
            }
        } else if (isNumericValue(value)) {
            setText(String.valueOf(value));
            setHorizontalAlignment(SwingConstants.RIGHT);
            if (baseFont != null) {
                setFont(new Font(Font.MONOSPACED, Font.PLAIN, baseFont.getSize()));
            }
            if (!isSelected) {
                setForeground(dark ? new Color(125, 211, 252) : new Color(3, 105, 161));
            }
        } else {
            String str = String.valueOf(value);
            setText(str);
            setHorizontalAlignment(SwingConstants.LEFT);
            if (baseFont != null) {
                setFont(baseFont);
            }
            // Highlight date/timestamp strings subtly
            if (!isSelected && isTimestampLike(str)) {
                setForeground(dark ? new Color(253, 186, 116) : new Color(180, 83, 9));
            }
        }

        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isNullValue && getText() != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            boolean dark = ThemeManager.isDarkMode();
            Color pillBg = dark ? new Color(45, 52, 68, 180) : new Color(226, 232, 240, 200);
            Color pillBorder = dark ? new Color(75, 85, 105, 180) : new Color(203, 213, 225);

            FontMetrics fm = g2.getFontMetrics(getFont());
            int textW = fm.stringWidth("NULL");
            int pillW = textW + 12;
            int pillH = Math.min(getHeight() - 6, fm.getHeight() + 2);
            int x = 8;
            int y = (getHeight() - pillH) / 2;

            g2.setColor(pillBg);
            g2.fillRoundRect(x, y, pillW, pillH, 6, 6);
            g2.setColor(pillBorder);
            g2.drawRoundRect(x, y, pillW, pillH, 6, 6);

            g2.setColor(getForeground());
            g2.setFont(getFont());
            int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString("NULL", x + 6, textY);
            g2.dispose();
            return;
        }
        super.paintComponent(g);
    }

    public static Boolean parseBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        }
        return null;
    }

    public static boolean isNumericValue(Object value) {
        if (value instanceof Number) return true;
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty() || s.length() > 20) return false;
            // Don't format leading-zero phone numbers or zip codes as numeric
            if (s.length() > 1 && s.startsWith("0") && !s.startsWith("0.")) return false;
            try {
                Double.parseDouble(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isTimestampLike(String s) {
        return s.length() >= 10 && s.length() <= 32
                && Character.isDigit(s.charAt(0))
                && s.charAt(4) == '-'
                && s.charAt(7) == '-';
    }

    /**
     * Decorates column headers with 🔑 (Primary Key) and 🔗 (Foreign Key) icons.
     */
    public static class SmartHeaderRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;
        private final Function<String, Boolean> isPk;
        private final Function<String, Boolean> isFk;

        public SmartHeaderRenderer(TableCellRenderer delegate,
                                   Function<String, Boolean> isPk,
                                   Function<String, Boolean> isFk) {
            this.delegate = delegate;
            this.isPk = isPk;
            this.isFk = isFk;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            String rawCol = value != null ? value.toString() : "";
            String cleanCol = rawCol.contains(".") ? rawCol.substring(rawCol.indexOf('.') + 1) : rawCol;

            String decorated = rawCol;
            try {
                boolean pk = isPk != null && Boolean.TRUE.equals(isPk.apply(cleanCol));
                boolean fk = isFk != null && Boolean.TRUE.equals(isFk.apply(cleanCol));
                if (pk && !rawCol.startsWith("🔑")) {
                    decorated = "🔑 " + rawCol;
                } else if (fk && !rawCol.startsWith("🔗")) {
                    decorated = "🔗 " + rawCol;
                }
            } catch (Exception ignored) {
            }

            Component c = delegate.getTableCellRendererComponent(table, decorated, isSelected, hasFocus, row, column);
            if (c instanceof JLabel) {
                JLabel lbl = (JLabel) c;
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
                lbl.setBorder(BorderFactory.createCompoundBorder(
                        lbl.getBorder(),
                        new EmptyBorder(2, 6, 2, 6)
                ));
            }
            return c;
        }
    }
}
