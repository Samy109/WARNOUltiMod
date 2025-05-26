package com.warnomodmaker.gui;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.ObjectValue;
import com.warnomodmaker.model.NDFValue.NumberValue;
import com.warnomodmaker.model.NDFValue.NDFFileType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class UnitBrowser extends JPanel {
    private List<ObjectValue> ndfObjects;
    private List<ObjectValue> filteredObjects;
    private List<Consumer<ObjectValue>> selectionListeners;
    private List<ObjectValue> originalObjects;
    private NDFFileType currentFileType;
    private JTextField searchField;
    private JComboBox<String> searchTypeComboBox;
    private JList<ObjectValue> objectList;
    private DefaultListModel<ObjectValue> listModel;
    private JLabel statusLabel;

    // Flag to prevent recursive search calls
    private boolean isSearching = false;

    // Search types
    private static final String SEARCH_BY_NAME = "Search by Name";
    private static final String SEARCH_BY_DAMAGE = "Search by Damage";
    private static final String SEARCH_BY_SPEED = "Search by Speed";
    private static final String SEARCH_BY_FUEL = "Search by Fuel";
    private static final String SEARCH_BY_VISION = "Search by Vision";
    private static final String SEARCH_BY_CUSTOM = "Search by Custom Path";

    
    public UnitBrowser() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Objects"));
        ndfObjects = new ArrayList<>();
        filteredObjects = new ArrayList<>();
        selectionListeners = new ArrayList<>();
        currentFileType = NDFFileType.UNKNOWN;
        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Search type dropdown
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        searchTypeComboBox = new JComboBox<>(new String[] {
            SEARCH_BY_NAME,
            SEARCH_BY_DAMAGE,
            SEARCH_BY_SPEED,
            SEARCH_BY_FUEL,
            SEARCH_BY_VISION,
            SEARCH_BY_CUSTOM
        });
        searchTypeComboBox.addActionListener(e -> {
            if (searchTypeComboBox.getSelectedItem().equals(SEARCH_BY_CUSTOM)) {
                String customPath = JOptionPane.showInputDialog(
                    this,
                    "Enter the property path to search (e.g., MaxPhysicalDamages):",
                    "Custom Property Path",
                    JOptionPane.QUESTION_MESSAGE
                );

                if (customPath != null && !customPath.isEmpty()) {
                    searchField.setText(customPath + "=");
                }
            }
            filterUnits();
        });
        searchPanel.add(searchTypeComboBox, gbc);

        // Search field
        gbc.gridx = 1;
        gbc.weightx = 0.7;
        searchField = new JTextField();

        // Use a timer to delay the search until the user stops typing
        Timer searchTimer = new Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // If the search field is empty, just reset to show all units
                if (searchField.getText().trim().isEmpty()) {
                    // This is a special case to handle empty search box efficiently
                    SwingUtilities.invokeLater(() -> {
                        resetToAllUnits();
                    });
                } else {
                    // Normal search
                    SwingUtilities.invokeLater(() -> {
                        if (!isSearching) {
                            isSearching = true;
                            try {
                                filterUnits();
                            } finally {
                                isSearching = false;
                            }
                        }
                    });
                }
            }
        });
        searchTimer.setRepeats(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchTimer.restart();
            }
        });
        searchPanel.add(searchField, gbc);

        // Status label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        statusLabel = new JLabel("0 units found");
        searchPanel.add(statusLabel, gbc);

        add(searchPanel, BorderLayout.NORTH);
        listModel = new DefaultListModel<>();
        objectList = new JList<>(listModel);
        objectList.setCellRenderer(new UnitListCellRenderer());
        objectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        objectList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    ObjectValue selectedObject = objectList.getSelectedValue();
                    notifySelectionListeners(selectedObject);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(objectList);
        add(scrollPane, BorderLayout.CENTER);
    }

    
    public void setUnitDescriptors(List<ObjectValue> unitDescriptors) {
        setUnitDescriptors(unitDescriptors, NDFFileType.UNKNOWN);
    }

    
    public void setUnitDescriptors(List<ObjectValue> ndfObjects, NDFFileType fileType) {
        // Store the original list and file type
        this.ndfObjects = ndfObjects != null ? ndfObjects : new ArrayList<>();
        this.currentFileType = fileType;

        // Store a separate copy of the original objects
        this.originalObjects = new ArrayList<>(this.ndfObjects);
        this.filteredObjects = new ArrayList<>(this.ndfObjects);
        String borderTitle = fileType != NDFFileType.UNKNOWN ? fileType.getDisplayName() + "s" : "Objects";
        setBorder(BorderFactory.createTitledBorder(borderTitle));
        String objectTypeName = fileType != NDFFileType.UNKNOWN ? fileType.getDisplayName().toLowerCase() + "s" : "objects";
        statusLabel.setText(this.ndfObjects.size() + " " + objectTypeName + " found");
        DefaultListModel<ObjectValue> newModel = new DefaultListModel<>();
        for (ObjectValue object : this.ndfObjects) {
            newModel.addElement(object);
        }

        // Replace the entire model at once - this is much more efficient
        objectList.setModel(newModel);
        listModel = newModel;

        // Select the first object if available
        if (!this.ndfObjects.isEmpty()) {
            objectList.setSelectedIndex(0);
        }
    }

    
    public ObjectValue getSelectedUnitDescriptor() {
        return objectList.getSelectedValue();
    }

    
    public void addUnitSelectionListener(Consumer<ObjectValue> listener) {
        selectionListeners.add(listener);
    }

    
    public void removeUnitSelectionListener(Consumer<ObjectValue> listener) {
        selectionListeners.remove(listener);
    }

    
    public void refresh() {
        filterUnits();
    }

    
    private void filterUnits() {
        final String searchText = searchField.getText().toLowerCase().trim();
        final String searchType = (String) searchTypeComboBox.getSelectedItem();

        // If search text is empty, use the resetToAllUnits method
        if (searchText.isEmpty()) {
            resetToAllUnits();
            return;
        }
        statusLabel.setText("Searching...");

        // Disable the search field while searching
        searchField.setEnabled(false);

        // Use SwingWorker to perform the search in a background thread
        SwingWorker<List<ObjectValue>, Void> worker = new SwingWorker<List<ObjectValue>, Void>() {
            @Override
            protected List<ObjectValue> doInBackground() throws Exception {
                List<ObjectValue> results = new ArrayList<>();

                // For very short search terms (1-2 characters), only search by name to avoid performance issues
                if (searchText.length() <= 2 && !searchType.equals(SEARCH_BY_CUSTOM)) {
                    for (ObjectValue unit : ndfObjects) {
                        String unitName = unit.getInstanceName().toLowerCase();
                        if (unitName.contains(searchText)) {
                            results.add(unit);
                        }
                    }
                    return results;
                }

                // For longer search terms or custom search, do the full search
                for (ObjectValue unit : ndfObjects) {
                    boolean matches = false;

                    try {
                        switch (searchType) {
                            case SEARCH_BY_NAME:
                                // Search by unit name
                                String unitName = unit.getInstanceName();
                                matches = unitName.toLowerCase().contains(searchText);
                                break;

                            case SEARCH_BY_DAMAGE:
                                // Search by damage value
                                matches = matchesPropertyValue(unit, "MaxPhysicalDamages", searchText);
                                break;

                            case SEARCH_BY_SPEED:
                                // Search by speed value
                                matches = matchesPropertyValue(unit, "MaxSpeedInKmph", searchText);
                                break;

                            case SEARCH_BY_FUEL:
                                // Search by fuel capacity
                                matches = matchesPropertyValue(unit, "FuelCapacity", searchText) ||
                                        matchesPropertyValue(unit, "FuelMoveDuration", searchText);
                                break;

                            case SEARCH_BY_VISION:
                                // Search by vision range
                                matches = matchesPropertyValue(unit, "VisionRangesGRU", searchText) ||
                                        matchesPropertyValue(unit, "OpticalStrengths", searchText);
                                break;

                            case SEARCH_BY_CUSTOM:
                                // Search by custom property path
                                if (searchText.contains("=")) {
                                    String[] parts = searchText.split("=", 2);
                                    String propertyPath = parts[0].trim();
                                    String valueToMatch = parts.length > 1 ? parts[1].trim() : "";
                                    matches = matchesPropertyValue(unit, propertyPath, valueToMatch);
                                } else {
                                    // If no value specified, just check if the property exists
                                    matches = hasProperty(unit, searchText);
                                }
                                break;
                        }
                    } catch (Exception e) {
                        // Skip units that cause errors during search
                        continue;
                    }

                    if (matches) {
                        results.add(unit);
                    }
                }

                return results;
            }

            @Override
            protected void done() {
                try {
                    List<ObjectValue> results = get();
                    filteredObjects = results;
                    DefaultListModel<ObjectValue> newModel = new DefaultListModel<>();
                    for (ObjectValue unit : filteredObjects) {
                        newModel.addElement(unit);
                    }

                    // Replace the entire model at once - this is much more efficient
                    objectList.setModel(newModel);
                    listModel = newModel;
                    String objectTypeName = currentFileType != NDFFileType.UNKNOWN ?
                        currentFileType.getDisplayName().toLowerCase() + "s" : "objects";
                    statusLabel.setText(filteredObjects.size() + " " + objectTypeName + " found");

                    // Select the first object if available
                    if (!filteredObjects.isEmpty()) {
                        objectList.setSelectedIndex(0);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText("Error searching: " + e.getMessage());
                } finally {
                    // Re-enable the search field and restore focus
                    searchField.setEnabled(true);
                    searchField.requestFocusInWindow();
                }
            }
        };

        // Start the background task
        worker.execute();
    }

    
    private boolean hasProperty(ObjectValue unit, String propertyPath) {
        // Split the property path into parts
        String[] pathParts = propertyPath.split("\\.");

        // Start with the unit
        NDFValue currentValue = unit;

        // Navigate through the property path
        for (String part : pathParts) {
            if (currentValue instanceof ObjectValue) {
                ObjectValue currentObject = (ObjectValue) currentValue;
                currentValue = currentObject.getProperty(part);

                if (currentValue == null) {
                    return false;
                }
            } else {
                return false;
            }
        }

        return true;
    }

    
    private boolean matchesPropertyValue(ObjectValue unit, String propertyPath, String valueToMatch) {
        // If no value to match, just check if the property exists
        if (valueToMatch.isEmpty()) {
            return hasProperty(unit, propertyPath);
        }

        // Split the property path into parts
        String[] pathParts = propertyPath.split("\\.");

        // Start with the unit
        NDFValue currentValue = unit;

        // Navigate through the property path
        for (String part : pathParts) {
            if (currentValue instanceof ObjectValue) {
                ObjectValue currentObject = (ObjectValue) currentValue;
                currentValue = currentObject.getProperty(part);

                if (currentValue == null) {
                    return false;
                }
            } else {
                return false;
            }
        }
        String valueStr = currentValue.toString().toLowerCase();
        if (valueToMatch.startsWith(">")) {
            try {
                double threshold = Double.parseDouble(valueToMatch.substring(1).trim());
                double value = Double.parseDouble(valueStr);
                return value > threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (valueToMatch.startsWith("<")) {
            try {
                double threshold = Double.parseDouble(valueToMatch.substring(1).trim());
                double value = Double.parseDouble(valueStr);
                return value < threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (valueToMatch.startsWith(">=")) {
            try {
                double threshold = Double.parseDouble(valueToMatch.substring(2).trim());
                double value = Double.parseDouble(valueStr);
                return value >= threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (valueToMatch.startsWith("<=")) {
            try {
                double threshold = Double.parseDouble(valueToMatch.substring(2).trim());
                double value = Double.parseDouble(valueStr);
                return value <= threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (valueToMatch.startsWith("=")) {
            try {
                double threshold = Double.parseDouble(valueToMatch.substring(1).trim());
                double value = Double.parseDouble(valueStr);
                return value == threshold;
            } catch (NumberFormatException e) {
                return valueStr.equals(valueToMatch.substring(1).trim().toLowerCase());
            }
        } else {
            // Simple contains check
            return valueStr.contains(valueToMatch);
        }
    }

    
    private void notifySelectionListeners(ObjectValue selectedUnit) {
        for (Consumer<ObjectValue> listener : selectionListeners) {
            listener.accept(selectedUnit);
        }
    }

    
    private static class UnitListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof ObjectValue) {
                ObjectValue unit = (ObjectValue) value;
                setText(unit.getInstanceName());
            }

            return component;
        }
    }

    
    private void resetToAllUnits() {
        DefaultListModel<ObjectValue> newModel = new DefaultListModel<>();
        for (ObjectValue unit : originalObjects) {
            newModel.addElement(unit);
        }

        // Replace the entire model at once - this is much more efficient
        objectList.setModel(newModel);
        listModel = newModel;
        filteredObjects = new ArrayList<>(originalObjects);
        String objectTypeName = currentFileType != NDFFileType.UNKNOWN ?
            currentFileType.getDisplayName().toLowerCase() + "s" : "objects";
        statusLabel.setText(filteredObjects.size() + " " + objectTypeName + " found");

        // Select the first object if available
        if (!filteredObjects.isEmpty()) {
            objectList.setSelectedIndex(0);
        }

        // Make sure the search field has focus
        searchField.requestFocusInWindow();
    }
}
