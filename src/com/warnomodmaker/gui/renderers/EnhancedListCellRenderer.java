package com.warnomodmaker.gui.renderers;

import com.warnomodmaker.gui.theme.WarnoTheme;
import com.warnomodmaker.model.NDFValue.ObjectValue;
import com.warnomodmaker.model.ModificationTracker;

import javax.swing.*;
import java.awt.*;

/**
 * Enhanced list cell renderer for the UnitBrowser with multi-line display
 * and modification indicators
 */
public class EnhancedListCellRenderer extends DefaultListCellRenderer {

    private ModificationTracker modificationTracker;
    private String highlightText;

    public EnhancedListCellRenderer() {
        this(null);
    }

    public EnhancedListCellRenderer(ModificationTracker modificationTracker) {
        this.modificationTracker = modificationTracker;
        this.highlightText = null;
        setOpaque(true);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
                                                int index, boolean isSelected,
                                                boolean cellHasFocus) {

        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof ObjectValue) {
            ObjectValue unit = (ObjectValue) value;
            formatObjectCell(unit, isSelected);
        }

        // Apply theme colors
        if (isSelected) {
            setBackground(WarnoTheme.ACCENT_BLUE);
            setForeground(Color.WHITE);
        } else {
            setBackground(WarnoTheme.TACTICAL_GREEN_DARK);
            setForeground(Color.WHITE);
        }

        // Set preferred height for multi-line display
        setPreferredSize(new Dimension(list.getWidth(), 50));

        return this;
    }

    private void formatObjectCell(ObjectValue unit, boolean isSelected) {
        String name = unit.getInstanceName();
        String typeName = unit.getTypeName();
        int propertyCount = unit.getProperties().size();
        boolean isModified = isObjectModified(unit);

        // Create HTML formatted text with multiple lines
        StringBuilder html = new StringBuilder("<html>");

        // First line: Name (bold) with modification indicator if needed
        html.append("<div style='margin-bottom: 2px;'>");
        if (isModified) {
            html.append("<font color='").append(colorToHex(WarnoTheme.WARNING_ORANGE)).append("'>*</font> ");
        }

        // Highlight matching text if search is active
        if (highlightText != null && !highlightText.isEmpty() && name.toLowerCase().contains(highlightText.toLowerCase())) {
            html.append(highlightMatchingText(name, highlightText, isSelected));
        } else {
            html.append("<b>").append(escapeHtml(name)).append("</b>");
        }
        html.append("</div>");

        // Second line: Type and property count
        String typeColor = isSelected ? "#FFFFFF" : colorToHex(WarnoTheme.ACCENT_BLUE_LIGHT);
        html.append("<div style='font-size: 90%;'>");
        html.append("<font color='").append(typeColor).append("'>")
            .append(escapeHtml(formatTypeName(typeName)))
            .append(" (").append(propertyCount).append(" properties)")
            .append("</font>");
        html.append("</div>");

        html.append("</html>");
        setText(html.toString());
    }

    private boolean isObjectModified(ObjectValue unit) {
        if (modificationTracker == null) return false;

        // Check if this object has any modifications
        return modificationTracker.hasModificationsForObject(unit);
    }

    private String formatTypeName(String typeName) {
        // Make type name more readable
        if (typeName.startsWith("T") && typeName.length() > 1) {
            return typeName.substring(1);
        }
        return typeName;
    }

    private String highlightMatchingText(String text, String highlight, boolean isSelected) {
        if (highlight == null || highlight.isEmpty()) {
            return "<b>" + escapeHtml(text) + "</b>";
        }

        String highlightColor = isSelected ? "#FFFF00" : "#FFA500"; // Yellow or orange
        String textLower = text.toLowerCase();
        String highlightLower = highlight.toLowerCase();

        StringBuilder result = new StringBuilder("<b>");
        int lastIndex = 0;
        int index;

        while ((index = textLower.indexOf(highlightLower, lastIndex)) != -1) {
            // Add text before match
            result.append(escapeHtml(text.substring(lastIndex, index)));

            // Add highlighted match
            result.append("<span style='background-color:").append(highlightColor).append(";")
                  .append(isSelected ? "color:#000000;" : "").append("'>")
                  .append(escapeHtml(text.substring(index, index + highlight.length())))
                  .append("</span>");

            lastIndex = index + highlight.length();
        }

        // Add remaining text
        if (lastIndex < text.length()) {
            result.append(escapeHtml(text.substring(lastIndex)));
        }

        result.append("</b>");
        return result.toString();
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

    public void setModificationTracker(ModificationTracker tracker) {
        this.modificationTracker = tracker;
    }

    public void setHighlightText(String text) {
        this.highlightText = text;
    }
}
