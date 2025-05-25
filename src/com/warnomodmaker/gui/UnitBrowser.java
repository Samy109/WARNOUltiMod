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

/**
 * Component for browsing and selecting units.
 */
public class UnitBrowser extends JPanel {
    private List<ObjectValue> unitDescriptors;
    private List<ObjectValue> filteredUnits;
    private List<Consumer<ObjectValue>> selectionListeners;
    private List<ObjectValue> originalUnits; // Keep a separate copy of the original units
    private NDFFileType currentFileType; // Current file type being displayed

    // GUI components
    private JTextField searchField;
    private JComboBox<String> searchTypeComboBox;
    private JList<ObjectValue> unitList;
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

    /**
     * Creates a new unit browser
     */
    public UnitBrowser() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Units"));

        // Initialize state
        unitDescriptors = new ArrayList<>();
        filteredUnits = new ArrayList<>();
        selectionListeners = new ArrayList<>();
        currentFileType = NDFFileType.UNKNOWN;

        // Create the search panel
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
                // Show a dialog to enter the custom property path
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

        // Create the unit list
        listModel = new DefaultListModel<>();
        unitList = new JList<>(listModel);
        unitList.setCellRenderer(new UnitListCellRenderer());
        unitList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        unitList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    ObjectValue selectedUnit = unitList.getSelectedValue();
                    notifySelectionListeners(selectedUnit);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(unitList);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Sets the unit descriptors to display
     *
     * @param unitDescriptors The unit descriptors
     */
    public void setUnitDescriptors(List<ObjectValue> unitDescriptors) {
        setUnitDescriptors(unitDescriptors, NDFFileType.UNKNOWN);
    }

    /**
     * Sets the unit descriptors to display with file type information
     *
     * @param unitDescriptors The unit descriptors
     * @param fileType The file type being displayed
     */
    public void setUnitDescriptors(List<ObjectValue> unitDescriptors, NDFFileType fileType) {
        // Store the original list and file type
        this.unitDescriptors = unitDescriptors != null ? unitDescriptors : new ArrayList<>();
        this.currentFileType = fileType;

        // Store a separate copy of the original units
        this.originalUnits = new ArrayList<>(this.unitDescriptors);

        // Initialize the filtered list with all units
        this.filteredUnits = new ArrayList<>(this.unitDescriptors);

        // Update the border title based on file type
        String borderTitle = fileType != NDFFileType.UNKNOWN ? fileType.getDisplayName() + "s" : "Units";
        setBorder(BorderFactory.createTitledBorder(borderTitle));

        // Update the status label
        String objectTypeName = fileType != NDFFileType.UNKNOWN ? fileType.getDisplayName().toLowerCase() + "s" : "units";
        statusLabel.setText(this.unitDescriptors.size() + " " + objectTypeName + " found");

        // Create a new list model instead of updating the existing one
        DefaultListModel<ObjectValue> newModel = new DefaultListModel<>();

        // Add all units to the new model at once
        for (ObjectValue unit : this.unitDescriptors) {
            newModel.addElement(unit);
        }

        // Replace the entire model at once - this is much more efficient
        unitList.setModel(newModel);

        // Update our reference to the model
        listModel = newModel;

        // Select the first unit if available
        if (!this.unitDescriptors.isEmpty()) {
            unitList.setSelectedIndex(0);
        }
    }

    /**
     * Gets the selected unit descriptor
     *
     * @return The selected unit descriptor, or null if none is selected
     */
    public ObjectValue getSelectedUnitDescriptor() {
        return unitList.getSelectedValue();
    }

    /**
     * Adds a listener for unit selection events
     *
     * @param listener The listener to add
     */
    public void addUnitSelectionListener(Consumer<ObjectValue> listener) {
        selectionListeners.add(listener);
    }

    /**
     * Removes a listener for unit selection events
     *
     * @param listener The listener to remove
     */
    public void removeUnitSelectionListener(Consumer<ObjectValue> listener) {
        selectionListeners.remove(listener);
    }

    /**
     * Refreshes the unit list
     */
    public void refresh() {
        filterUnits();
    }

    /**
     * Filters the units based on the search criteria
     */
    private void filterUnits() {
        // Get the search text and type
        final String searchText = searchField.getText().toLowerCase().trim();
        final String searchType = (String) searchTypeComboBox.getSelectedItem();

        // If search text is empty, use the resetToAllUnits method
        if (searchText.isEmpty()) {
            resetToAllUnits();
            return;
        }

        // Show a "Searching..." message
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
                    for (ObjectValue unit : unitDescriptors) {
                        String unitName = unit.getInstanceName().toLowerCase();
                        if (unitName.contains(searchText)) {
                            results.add(unit);
                        }
                    }
                    return results;
                }

                // For longer search terms or custom search, do the full search
                for (ObjectValue unit : unitDescriptors) {
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
                    // Get the search results
                    List<ObjectValue> results = get();

                    // Update the filtered units list
                    filteredUnits = results;

                    // Create a new list model instead of updating the existing one
                    DefaultListModel<ObjectValue> newModel = new DefaultListModel<>();

                    // Add all filtered units to the new model at once
                    for (ObjectValue unit : filteredUnits) {
                        newModel.addElement(unit);
                    }

                    // Replace the entire model at once - this is much more efficient
                    unitList.setModel(newModel);

                    // Update our reference to the model
                    listModel = newModel;

                    // Update the status label
                    String objectTypeName = currentFileType != NDFFileType.UNKNOWN ?
                        currentFileType.getDisplayName().toLowerCase() + "s" : "units";
                    statusLabel.setText(filteredUnits.size() + " " + objectTypeName + " found");

                    // Select the first unit if available
                    if (!filteredUnits.isEmpty()) {
                        unitList.setSelectedIndex(0);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    // Handle any errors
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

    /**
     * Checks if a unit has a property with the given path
     *
     * @param unit The unit to check
     * @param propertyPath The property path to look for
     * @return True if the property exists, false otherwise
     */
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

    /**
     * Checks if a unit property matches a value
     *
     * @param unit The unit to check
     * @param propertyPath The property path to look for
     * @param valueToMatch The value to match
     * @return True if the property matches the value, false otherwise
     */
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

        // Check if the value matches
        String valueStr = currentValue.toString().toLowerCase();

        // Handle comparison operators
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



    /**
     * Notifies selection listeners of a selection change
     *
     * @param selectedUnit The selected unit
     */
    private void notifySelectionListeners(ObjectValue selectedUnit) {
        for (Consumer<ObjectValue> listener : selectionListeners) {
            listener.accept(selectedUnit);
        }
    }

    /**
     * Cell renderer for the unit list
     */
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

    /**
     * Resets the unit list to show all units
     * This is a special method to handle the empty search case efficiently
     */
    private void resetToAllUnits() {
        // Create a new list model instead of updating the existing one
        DefaultListModel<ObjectValue> newModel = new DefaultListModel<>();

        // Add all units to the new model at once
        for (ObjectValue unit : originalUnits) {
            newModel.addElement(unit);
        }

        // Replace the entire model at once - this is much more efficient
        unitList.setModel(newModel);

        // Update our reference to the model
        listModel = newModel;

        // Update the filtered units list
        filteredUnits = new ArrayList<>(originalUnits);

        // Update the status label
        String objectTypeName = currentFileType != NDFFileType.UNKNOWN ?
            currentFileType.getDisplayName().toLowerCase() + "s" : "units";
        statusLabel.setText(filteredUnits.size() + " " + objectTypeName + " found");

        // Select the first unit if available
        if (!filteredUnits.isEmpty()) {
            unitList.setSelectedIndex(0);
        }

        // Make sure the search field has focus
        searchField.requestFocusInWindow();
    }
}
