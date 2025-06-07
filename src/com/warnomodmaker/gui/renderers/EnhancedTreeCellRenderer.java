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
                formatPropertyNode(propertyNode, selected, tree);
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

    private void formatPropertyNode(PropertyNode propertyNode, boolean selected, JTree tree) {
        String propertyName = propertyNode.getName();
        NDFValue propertyValue = propertyNode.getValue();

        // Get type-specific icon and formatting
        String icon = getTypeIcon(propertyValue);
        String displayText = formatDisplayText(propertyName, propertyValue);

        StringBuilder html = new StringBuilder("<html>");

        html.append(icon).append(" ");

        String displayName = propertyName;
        if (showModificationIndicators && propertyNode.isModified()) {
            html.append("<b><font color='").append(colorToHex(WarnoTheme.WARNING_ORANGE)).append("'>")
                .append(escapeHtml(displayName)).append("</font></b>");
        } else {
            html.append("<b>").append(escapeHtml(displayName)).append("</b>");
        }

        String valuePreview = getValuePreview(propertyValue);
        if (!valuePreview.isEmpty()) {
            String color = selected ? "#FFFFFF" : colorToHex(WarnoTheme.ACCENT_BLUE_LIGHT);
            html.append(" <font color='").append(color).append("'>")
                .append(escapeHtml(valuePreview)).append("</font>");
        }

        html.append("</html>");
        setText(html.toString());
        setIcon(null);
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
            case RESOURCE_REF:
                return ICON_REFERENCE;
            case GUID:
                return ICON_GUID;
            case RAW_EXPRESSION:
                return "[R]";
            case NULL:
                return "[∅]";
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
                if (strValue.length() > 40) {
                    return "= \"" + strValue.substring(0, 37) + "...\"";
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
                if (refValue.length() > 35) {
                    return "=> " + refValue.substring(0, 32) + "...";
                }
                return "=> " + refValue;

            case RESOURCE_REF:
                String resourceValue = value.toString();
                if (resourceValue.length() > 35) {
                    return "=> " + resourceValue.substring(0, 32) + "...";
                }
                return "=> " + resourceValue;

            case GUID:
                String guidValue = value.toString();
                if (guidValue.length() > 25) {
                    return "= " + guidValue.substring(0, 22) + "...";
                }
                return "= " + guidValue;

            case RAW_EXPRESSION:
                String rawValue = value.toString();
                if (rawValue.length() > 30) {
                    return "= " + rawValue.substring(0, 27) + "...";
                }
                return "= " + rawValue;

            case NULL:
                return "= nil";

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
        private final String originalPropertyName; // Store original property name for path construction
        private final NDFValue value;
        private boolean modified;

        public PropertyNode(String name, NDFValue value) {
            this.name = name;
            this.originalPropertyName = name; // Default to same as display name
            this.value = value;
            this.modified = false;
        }

        public PropertyNode(String displayName, String originalPropertyName, NDFValue value) {
            this.name = displayName;
            this.originalPropertyName = originalPropertyName;
            this.value = value;
            this.modified = false;
        }

        public String getName() {
            return name;
        }

        public String getOriginalPropertyName() {
            return originalPropertyName;
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
