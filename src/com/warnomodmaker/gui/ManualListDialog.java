package com.warnomodmaker.gui;

import com.warnomodmaker.model.NDFValue.ObjectValue;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class ManualListDialog extends JDialog {
    private List<ObjectValue> allUnits;
    private List<ObjectValue> selectedUnits;
    private DefaultListModel<ObjectValue> availableListModel;
    private DefaultListModel<ObjectValue> selectedListModel;
    private JList<ObjectValue> availableList;
    private JList<ObjectValue> selectedList;
    private JTextField searchField;
    private boolean confirmed;

    public ManualListDialog(JFrame parent, List<ObjectValue> allUnits) {
        super(parent, "Create Manual Unit List", true);
        this.allUnits = new ArrayList<>(allUnits);
        this.selectedUnits = new ArrayList<>();
        this.confirmed = false;
        
        initializeGUI();
        populateAvailableList();
    }

    private void initializeGUI() {
        setSize(800, 600);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel with search
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JLabel("Search available units:"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterAvailableUnits(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterAvailableUnits(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterAvailableUnits(); }
        });
        topPanel.add(searchField, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel with two lists
        JPanel centerPanel = new JPanel(new GridLayout(1, 3));
        
        // Available units panel
        JPanel availablePanel = new JPanel(new BorderLayout());
        availablePanel.setBorder(BorderFactory.createTitledBorder("Available Units"));
        availableListModel = new DefaultListModel<>();
        availableList = new JList<>(availableListModel);
        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        availableList.setCellRenderer(new UnitListCellRenderer());
        JScrollPane availableScrollPane = new JScrollPane(availableList);
        availablePanel.add(availableScrollPane, BorderLayout.CENTER);
        centerPanel.add(availablePanel);

        // Button panel
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JButton addButton = new JButton("Add →");
        addButton.addActionListener(this::addSelectedUnits);
        gbc.gridy = 0;
        buttonPanel.add(addButton, gbc);

        JButton removeButton = new JButton("< Remove");
        removeButton.addActionListener(this::removeSelectedUnits);
        gbc.gridy = 1;
        buttonPanel.add(removeButton, gbc);

        JButton addAllButton = new JButton("Add All >");
        addAllButton.addActionListener(this::addAllUnits);
        gbc.gridy = 2;
        buttonPanel.add(addAllButton, gbc);

        JButton removeAllButton = new JButton("< Remove All");
        removeAllButton.addActionListener(this::removeAllUnits);
        gbc.gridy = 3;
        buttonPanel.add(removeAllButton, gbc);

        centerPanel.add(buttonPanel);

        // Selected units panel
        JPanel selectedPanel = new JPanel(new BorderLayout());
        selectedPanel.setBorder(BorderFactory.createTitledBorder("Selected Units"));
        selectedListModel = new DefaultListModel<>();
        selectedList = new JList<>(selectedListModel);
        selectedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        selectedList.setCellRenderer(new UnitListCellRenderer());
        JScrollPane selectedScrollPane = new JScrollPane(selectedList);
        selectedPanel.add(selectedScrollPane, BorderLayout.CENTER);
        centerPanel.add(selectedPanel);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with action buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        bottomPanel.add(okButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        bottomPanel.add(cancelButton);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void populateAvailableList() {
        availableListModel.clear();
        for (ObjectValue unit : allUnits) {
            availableListModel.addElement(unit);
        }
    }

    private void filterAvailableUnits() {
        String searchText = searchField.getText().trim().toLowerCase();
        availableListModel.clear();
        
        for (ObjectValue unit : allUnits) {
            if (searchText.isEmpty() || 
                unit.getInstanceName().toLowerCase().contains(searchText)) {
                // Only show units that aren't already selected
                if (!selectedUnits.contains(unit)) {
                    availableListModel.addElement(unit);
                }
            }
        }
    }

    private void addSelectedUnits(ActionEvent e) {
        List<ObjectValue> selected = availableList.getSelectedValuesList();
        for (ObjectValue unit : selected) {
            if (!selectedUnits.contains(unit)) {
                selectedUnits.add(unit);
                selectedListModel.addElement(unit);
            }
        }
        filterAvailableUnits(); // Refresh available list to remove added units
    }

    private void removeSelectedUnits(ActionEvent e) {
        List<ObjectValue> selected = selectedList.getSelectedValuesList();
        for (ObjectValue unit : selected) {
            selectedUnits.remove(unit);
            selectedListModel.removeElement(unit);
        }
        filterAvailableUnits(); // Refresh available list to show removed units
    }

    private void addAllUnits(ActionEvent e) {
        for (int i = 0; i < availableListModel.getSize(); i++) {
            ObjectValue unit = availableListModel.getElementAt(i);
            if (!selectedUnits.contains(unit)) {
                selectedUnits.add(unit);
                selectedListModel.addElement(unit);
            }
        }
        filterAvailableUnits(); // Refresh available list
    }

    private void removeAllUnits(ActionEvent e) {
        selectedUnits.clear();
        selectedListModel.clear();
        filterAvailableUnits(); // Refresh available list
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<ObjectValue> getSelectedUnits() {
        return new ArrayList<>(selectedUnits);
    }

    private static class UnitListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                                                    boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof ObjectValue) {
                ObjectValue unit = (ObjectValue) value;
                setText(unit.getInstanceName());
            }
            
            return component;
        }
    }
}
