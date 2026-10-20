package com.warnomodmaker.gui;

import com.warnomodmaker.gui.theme.WarnoTheme;
import com.warnomodmaker.model.NDFValue.ObjectValue;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Preview dialog for mass modifications allowing users to inspect and selectively
 * check/uncheck units before changes are applied.
 */
public class MassModifyPreviewDialog extends JDialog {

    public static class ChangeItem {
        private boolean selected;
        private final ObjectValue unit;
        private final String unitName;
        private final String propertyPath;
        private final String currentValue;
        private final String newValue;

        public ChangeItem(ObjectValue unit, String unitName, String propertyPath, String currentValue, String newValue) {
            this.selected = true;
            this.unit = unit;
            this.unitName = unitName != null ? unitName : "Unnamed";
            this.propertyPath = propertyPath;
            this.currentValue = currentValue != null ? currentValue : "";
            this.newValue = newValue != null ? newValue : "";
        }

        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public ObjectValue getUnit() { return unit; }
        public String getUnitName() { return unitName; }
        public String getPropertyPath() { return propertyPath; }
        public String getCurrentValue() { return currentValue; }
        public String getNewValue() { return newValue; }
    }

    private final List<ChangeItem> items;
    private boolean confirmed = false;
    private PreviewTableModel tableModel;
    private JTable table;
    private JLabel summaryLabel;

    public MassModifyPreviewDialog(JDialog owner, String propertyPath, String operationDesc, List<ChangeItem> items) {
        super(owner, "Preview Mass Modification Changes", true);
        this.items = items != null ? items : new ArrayList<>();
        initComponents(propertyPath, operationDesc);
        setSize(800, 520);
        setLocationRelativeTo(owner);
    }

    private void initComponents(String propertyPath, String operationDesc) {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Top info header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        JLabel titleLabel = new JLabel("<html><b>Property:</b> <font color='#4da6ff'>" +
                escapeHtml(propertyPath) + "</font> | <b>Operation:</b> " + escapeHtml(operationDesc) + "</html>");
        summaryLabel = new JLabel("Pending changes: " + items.size() + " units affected");
        headerPanel.add(titleLabel);
        headerPanel.add(summaryLabel);
        content.add(headerPanel, BorderLayout.NORTH);

        // Center table
        tableModel = new PreviewTableModel(items);
        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);

        // Column sizing
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);

        // Diff renderer for New Value column
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFocus, row, col);
                if (!isSel) {
                    c.setForeground(new Color(90, 200, 90)); // Soft green highlight
                }
                return c;
            }
        });

        TableRowSorter<PreviewTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(table);
        content.add(scrollPane, BorderLayout.CENTER);

        // Filter / Selection Toolbar
        JPanel actionToolbar = new JPanel(new BorderLayout(5, 5));
        JPanel selectionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.addActionListener(e -> setAllSelected(true));
        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.addActionListener(e -> setAllSelected(false));
        selectionButtons.add(selectAllBtn);
        selectionButtons.add(deselectAllBtn);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        searchPanel.add(new JLabel("Filter Units:"));
        JTextField searchField = new JTextField(15);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                String text = searchField.getText().trim();
                if (text.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 1));
                }
                updateSummary();
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        searchPanel.add(searchField);

        actionToolbar.add(selectionButtons, BorderLayout.WEST);
        actionToolbar.add(searchPanel, BorderLayout.EAST);

        // Bottom dialog buttons
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 10));
        bottomContainer.add(actionToolbar, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton applyBtn = new JButton("Apply to Selected (" + getSelectedCount() + ")");
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD));
        applyBtn.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        tableModel.addTableModelListener(e -> {
            applyBtn.setText("Apply to Selected (" + getSelectedCount() + ")");
            updateSummary();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        buttonPanel.add(applyBtn);
        buttonPanel.add(cancelBtn);
        bottomContainer.add(buttonPanel, BorderLayout.SOUTH);

        content.add(bottomContainer, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void setAllSelected(boolean sel) {
        for (ChangeItem item : items) {
            item.setSelected(sel);
        }
        tableModel.fireTableDataChanged();
        updateSummary();
    }

    public int getSelectedCount() {
        int count = 0;
        for (ChangeItem item : items) {
            if (item.isSelected()) count++;
        }
        return count;
    }

    private void updateSummary() {
        if (summaryLabel != null) {
            summaryLabel.setText("Selected: " + getSelectedCount() + " of " + items.size() + " units");
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<ChangeItem> getSelectedItems() {
        List<ChangeItem> selected = new ArrayList<>();
        for (ChangeItem item : items) {
            if (item.isSelected()) {
                selected.add(item);
            }
        }
        return selected;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static class PreviewTableModel extends AbstractTableModel {
        private final String[] columns = {"Apply", "Unit Name", "Property", "Current Value", "New Value"};
        private final List<ChangeItem> data;

        public PreviewTableModel(List<ChangeItem> data) {
            this.data = data;
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : String.class;
        }
        @Override public boolean isCellEditable(int r, int c) {
            return c == 0;
        }
        @Override public Object getValueAt(int r, int c) {
            ChangeItem item = data.get(r);
            switch (c) {
                case 0: return item.isSelected();
                case 1: return item.getUnitName();
                case 2: return item.getPropertyPath();
                case 3: return item.getCurrentValue();
                case 4: return item.getNewValue();
                default: return "";
            }
        }
        @Override public void setValueAt(Object val, int r, int c) {
            if (c == 0 && val instanceof Boolean) {
                data.get(r).setSelected((Boolean) val);
                fireTableCellUpdated(r, c);
            }
        }
    }
}
