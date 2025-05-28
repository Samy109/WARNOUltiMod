package com.warnomodmaker.gui.renderers;

import com.warnomodmaker.gui.theme.WarnoTheme;
import com.warnomodmaker.model.NDFValue;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * Enhanced tree cell renderer with type-specific icons and styling
 */
public class EnhancedTreeCellRenderer extends DefaultTreeCellRenderer {

    // Simple text-based icons for different types
    private static final String ICON_OBJECT = "[O]";
    private static final String ICON_ARRAY = "[A]";
    private static final String ICON_STRING = "[S]";
    private static final String ICON_NUMBER = "[N]";
    private static final String ICON_BOOLEAN = "[B]";
    private static final String ICON_ENUM = "[E]";
    private static final String ICON_REFERENCE = "[R]";
    private static final String ICON_GUID = "[G]";
    private static final String ICON_MAP = "[M]";
    private static final String ICON_TUPLE = "[T]";

    private boolean showModificationIndicators = true;

    public EnhancedTreeCellRenderer() {
        setLeafIcon(null);
        setClosedIcon(null);
        setOpenIcon(null);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                boolean selected, boolean expanded,
                                                boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof PropertyNode) {
                PropertyNode propertyNode = (PropertyNode) userObject;
                formatPropertyNode(propertyNode, selected);
            } else {
                // Root or other nodes
                setText(userObject.toString());
                setIcon(null);
            }
        }

        // Apply theme colors
        if (selected) {
            setBackgroundSelectionColor(WarnoTheme.ACCENT_BLUE);
            setTextSelectionColor(Color.WHITE);
        } else {
            setBackgroundNonSelectionColor(WarnoTheme.TACTICAL_GREEN_DARK);
            setTextNonSelectionColor(Color.WHITE);
        }

        setBorderSelectionColor(WarnoTheme.ACCENT_BLUE_LIGHT);

        return this;
    }

    private void formatPropertyNode(PropertyNode propertyNode, boolean selected) {
        String propertyName = propertyNode.getName();
        NDFValue propertyValue = propertyNode.getValue();

        // Get type-specific icon and formatting
        String icon = getTypeIcon(propertyValue);
        String displayText = formatDisplayText(propertyName, propertyValue);

        // Create HTML formatted text with icon and value preview
        StringBuilder html = new StringBuilder("<html>");

        // Add type icon
        html.append(icon).append(" ");

        // Add property name (bold) with modification color if needed
        if (showModificationIndicators && propertyNode.isModified()) {
            html.append("<b><font color='").append(colorToHex(WarnoTheme.WARNING_ORANGE)).append("'>")
                .append(escapeHtml(propertyName)).append("</font></b>");
        } else {
            html.append("<b>").append(escapeHtml(propertyName)).append("</b>");
        }

        // Add value preview for simple types
        String valuePreview = getValuePreview(propertyValue);
        if (!valuePreview.isEmpty()) {
            String color = selected ? "#FFFFFF" : colorToHex(WarnoTheme.ACCENT_BLUE_LIGHT);
            html.append(" <font color='").append(color).append("'>")
                .append(escapeHtml(valuePreview)).append("</font>");
        }

        html.append("</html>");
        setText(html.toString());
        setIcon(null); // We're using text-based icons in the HTML
    }

    private String getTypeIcon(NDFValue value) {
        if (value == null) return "";

        switch (value.getType()) {
            case OBJECT:
                return ICON_OBJECT;
            case ARRAY:
                return ICON_ARRAY;
            case STRING:
                return ICON_STRING;
            case NUMBER:
                return ICON_NUMBER;
            case BOOLEAN:
                return ICON_BOOLEAN;
            case ENUM:
                return ICON_ENUM;
            case TEMPLATE_REF:
                return ICON_REFERENCE;
            case GUID:
                return ICON_GUID;
            case MAP:
                return ICON_MAP;
            case TUPLE:
                return ICON_TUPLE;
            default:
                return "[?]";
        }
    }

    private String formatDisplayText(String propertyName, NDFValue propertyValue) {
        // Handle array indices and map keys specially
        if (propertyName.startsWith("[") && propertyName.endsWith("]")) {
            return "Item " + propertyName;
        }
        if (propertyName.startsWith("(") && propertyName.endsWith(")")) {
            return "Key " + propertyName;
        }
        return propertyName;
    }

    private String getValuePreview(NDFValue value) {
        if (value == null) return "";

        switch (value.getType()) {
            case STRING:
                String strValue = value.toString();
                if (strValue.length() > 30) {
                    return "= \"" + strValue.substring(0, 27) + "...\"";
                }
                return "= " + strValue;

            case NUMBER:
            case BOOLEAN:
            case ENUM:
                return "= " + value.toString();

            case ARRAY:
                NDFValue.ArrayValue arrayValue = (NDFValue.ArrayValue) value;
                return "(" + arrayValue.getElements().size() + " items)";

            case OBJECT:
                NDFValue.ObjectValue objectValue = (NDFValue.ObjectValue) value;
                return "(" + objectValue.getProperties().size() + " properties)";

            case MAP:
                NDFValue.MapValue mapValue = (NDFValue.MapValue) value;
                return "(" + mapValue.getEntries().size() + " entries)";

            case TUPLE:
                NDFValue.TupleValue tupleValue = (NDFValue.TupleValue) value;
                return "(" + tupleValue.getElements().size() + " elements)";

            case TEMPLATE_REF:
                String refValue = value.toString();
                if (refValue.length() > 25) {
                    return "→ " + refValue.substring(0, 22) + "...";
                }
                return "→ " + refValue;

            default:
                return "";
        }
    }

    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;");
    }

    public void setShowModificationIndicators(boolean show) {
        this.showModificationIndicators = show;
    }

    // Inner class to represent property nodes with modification tracking
    public static class PropertyNode {
        private final String name;
        private final NDFValue value;
        private boolean modified;

        public PropertyNode(String name, NDFValue value) {
            this.name = name;
            this.value = value;
            this.modified = false;
        }

        public String getName() {
            return name;
        }

        public NDFValue getValue() {
            return value;
        }

        public boolean isModified() {
            return modified;
        }

        public void setModified(boolean modified) {
            this.modified = modified;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
