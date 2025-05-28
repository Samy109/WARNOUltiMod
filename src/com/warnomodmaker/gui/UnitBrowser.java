package com.warnomodmaker.gui;

import com.warnomodmaker.gui.renderers.EnhancedListCellRenderer;
import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.ObjectValue;
import com.warnomodmaker.model.PropertyScanner;
import com.warnomodmaker.model.ModificationTracker;
import java.util.Map;
import com.warnomodmaker.model.NDFValue.NumberValue;
import com.warnomodmaker.model.NDFValue.NDFFileType;
import com.warnomodmaker.model.PropertyUpdater;

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
    private List<Consumer<String>> propertyFilterListeners;
    private List<ObjectValue> originalObjects;
    private NDFFileType currentFileType;
    private JTextField searchField;
    private JComboBox<String> searchTypeComboBox;
    private PropertyScanner propertyScanner;
    private JList<ObjectValue> objectList;
    private DefaultListModel<ObjectValue> listModel;
    private JLabel statusLabel;
    private Timer searchTimer;
    private SwingWorker<?, ?> currentSearchWorker;
    private ModificationTracker modificationTracker;
    private EnhancedListCellRenderer cellRenderer;

    // Search types
    private static final String SEARCH_BY_NAME = "Search by Name";
    private static final String SEARCH_BY_PROPERTY = "Search by Property";
    private static final String SEARCH_HAS_PROPERTY = "Filter Tree by Property";


    public UnitBrowser() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Objects"));
        ndfObjects = new ArrayList<>();
        filteredObjects = new ArrayList<>();
        selectionListeners = new ArrayList<>();
        propertyFilterListeners = new ArrayList<>();
        currentFileType = NDFFileType.UNKNOWN;
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Top row: Search type dropdown
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topRow.add(new JLabel("Search by:"));
        searchTypeComboBox = new JComboBox<>(new String[] {
            SEARCH_BY_NAME,
            SEARCH_BY_PROPERTY,
            SEARCH_HAS_PROPERTY
        });
        searchTypeComboBox.addActionListener(e -> {
            // Cancel any pending search timer and search immediately when search type changes
            searchTimer.stop();
            filterUnits();
        });
        topRow.add(searchTypeComboBox);
        searchPanel.add(topRow, BorderLayout.NORTH);

        // Middle row: Search field (always visible)
        JPanel middleRow = new JPanel(new BorderLayout());
        middleRow.add(new JLabel("Search:"), BorderLayout.WEST);
        searchField = new JTextField();

        // Use a timer to delay the search until the user stops typing
        searchTimer = new Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // If the search field is empty, just reset to show all units
                if (searchField.getText().trim().isEmpty()) {
                    resetToAllUnits();
                } else {
                    // Normal search
                    filterUnits();
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
        middleRow.add(searchField, BorderLayout.CENTER);
        searchPanel.add(middleRow, BorderLayout.CENTER);

        // Status label
        statusLabel = new JLabel("0 units found");
        statusLabel.setPreferredSize(new Dimension(400, 25)); // Set fixed size to prevent overlap
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);
        searchPanel.add(statusPanel, BorderLayout.SOUTH);

        add(searchPanel, BorderLayout.NORTH);
        listModel = new DefaultListModel<>();
        objectList = new JList<>(listModel);
        cellRenderer = new EnhancedListCellRenderer();
        objectList.setCellRenderer(cellRenderer);
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

        // Set the file type context in PropertyUpdater for proper modification tracking
        PropertyUpdater.setFileType(fileType);

        // Initialize PropertyScanner for property search
        if (!this.ndfObjects.isEmpty()) {
            propertyScanner = new PropertyScanner(this.ndfObjects, fileType);
            propertyScanner.scanProperties();
        }

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

        // Select the first object if available and ensure tree is fully populated
        if (!this.ndfObjects.isEmpty()) {
            objectList.setSelectedIndex(0);
            // Manually trigger selection event to populate the tree
            ObjectValue firstObject = this.ndfObjects.get(0);
            notifySelectionListeners(firstObject);
        }

        // Make sure tree starts with no filter (fully populated)
        notifyPropertyFilterListeners(null);

        // Update status label with proper object type name
        String displayTypeName = getObjectTypeNameForDisplay(currentFileType);
        statusLabel.setText(this.ndfObjects.size() + " " + displayTypeName + " loaded");
    }


    public void setUnitDescriptorsWithPreprocessedData(List<ObjectValue> ndfObjects, NDFFileType fileType,
                                                      PropertyScanner propertyScanner, DefaultListModel<ObjectValue> listModel) {
        // Store the original list and file type
        this.ndfObjects = ndfObjects != null ? ndfObjects : new ArrayList<>();
        this.currentFileType = fileType;

        // Use the pre-processed PropertyScanner
        this.propertyScanner = propertyScanner;

        // Store a separate copy of the original objects
        this.originalObjects = new ArrayList<>(this.ndfObjects);
        this.filteredObjects = new ArrayList<>(this.ndfObjects);
        String borderTitle = fileType != NDFFileType.UNKNOWN ? fileType.getDisplayName() + "s" : "Objects";
        setBorder(BorderFactory.createTitledBorder(borderTitle));

        // Use the pre-processed list model - this is the key optimization!
        objectList.setModel(listModel);
        this.listModel = listModel;

        // Select the first object if available and ensure tree is fully populated
        if (!this.ndfObjects.isEmpty()) {
            objectList.setSelectedIndex(0);
            // Manually trigger selection event to populate the tree
            ObjectValue firstObject = this.ndfObjects.get(0);
            notifySelectionListeners(firstObject);
        }

        // Make sure tree starts with no filter (fully populated)
        notifyPropertyFilterListeners(null);

        // Update status label with proper object type name
        String displayTypeName = getObjectTypeNameForDisplay(currentFileType);
        statusLabel.setText(this.ndfObjects.size() + " " + displayTypeName + " loaded");
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

    public void addPropertyFilterListener(Consumer<String> listener) {
        propertyFilterListeners.add(listener);
    }

    public void removePropertyFilterListener(Consumer<String> listener) {
        propertyFilterListeners.remove(listener);
    }


    public void refresh() {
        filterUnits();
    }


    public void setModificationTracker(ModificationTracker tracker) {
        this.modificationTracker = tracker;
        cellRenderer.setModificationTracker(tracker);
        objectList.repaint();
    }

    /**
     * Repaints the object list to update visual indicators without changing selection.
     * This is used when modifications are made to update the display of which objects
     * have been modified without triggering selection changes.
     */
    public void repaintList() {
        objectList.repaint();
    }


    public void performGlobalSearch(String searchText) {
        searchField.setText(searchText);
        cellRenderer.setHighlightText(searchText);
        filterUnits();
    }


    private void filterUnits() {
        // Cancel any existing search
        if (currentSearchWorker != null && !currentSearchWorker.isDone()) {
            currentSearchWorker.cancel(true);
        }

        final String searchText = searchField.getText().trim();
        final String searchType = (String) searchTypeComboBox.getSelectedItem();

        // Special handling for property search
        if (SEARCH_BY_PROPERTY.equals(searchType)) {
            filterByPropertyContaining(searchText); // Search all objects for properties containing text
            return;
        } else if (SEARCH_HAS_PROPERTY.equals(searchType)) {
            filterByHasPropertyCurrent(searchText); // Check current object only
            return;
        } else {
            // Clear property filter when not in property search mode
            notifyPropertyFilterListeners(null);
        }

        // If search text is empty, use the resetToAllUnits method
        if (searchText.isEmpty()) {
            resetToAllUnits();
            return;
        }
        statusLabel.setText("Searching...");

        // Disable the search field while searching
        searchField.setEnabled(false);

        // Use SwingWorker to perform the search in a background thread
        currentSearchWorker = new SwingWorker<List<ObjectValue>, Void>() {
            @Override
            protected List<ObjectValue> doInBackground() throws Exception {
                List<ObjectValue> results = new ArrayList<>();

                // For very short search terms (1-2 characters), only search by name to avoid performance issues
                if (searchText.length() <= 2) {
                    for (ObjectValue unit : ndfObjects) {
                        String unitName = unit.getInstanceName().toLowerCase();
                        if (unitName.contains(searchText.toLowerCase())) {
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
                                matches = unitName.toLowerCase().contains(searchText.toLowerCase());
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
                    String objectTypeName = getObjectTypeNameForDisplay(currentFileType);
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
        currentSearchWorker.execute();
    }


    private boolean hasProperty(ObjectValue unit, String propertyPath) {

        return com.warnomodmaker.model.PropertyUpdater.hasProperty(unit, propertyPath);
    }


    private boolean matchesPropertyValue(ObjectValue unit, String propertyPath, String valueToMatch) {

        if (valueToMatch.isEmpty()) {
            return hasProperty(unit, propertyPath);
        }


        NDFValue currentValue = com.warnomodmaker.model.PropertyUpdater.getPropertyValue(unit, propertyPath);
        if (currentValue == null) {
            return false;
        }


        String valueStr;
        if (currentValue instanceof NDFValue.StringValue) {

            String rawValue = ((NDFValue.StringValue) currentValue).getValue();

            while ((rawValue.startsWith("'") && rawValue.endsWith("'")) ||
                   (rawValue.startsWith("\"") && rawValue.endsWith("\""))) {
                rawValue = rawValue.substring(1, rawValue.length() - 1);
            }
            valueStr = rawValue.toLowerCase();
        } else if (currentValue instanceof NDFValue.BooleanValue) {
            valueStr = Boolean.toString(((NDFValue.BooleanValue) currentValue).getValue()).toLowerCase();
        } else {
            valueStr = currentValue.toString().toLowerCase();
        }
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
            return valueStr.contains(valueToMatch);
        }
    }


    private void notifySelectionListeners(ObjectValue selectedUnit) {
        for (Consumer<ObjectValue> listener : selectionListeners) {
            listener.accept(selectedUnit);
        }
    }

    private void notifyPropertyFilterListeners(String filter) {
        for (Consumer<String> listener : propertyFilterListeners) {
            listener.accept(filter);
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
        String objectTypeName = getObjectTypeNameForDisplay(currentFileType);
        statusLabel.setText(filteredObjects.size() + " " + objectTypeName + " found");

        // Select the first object if available
        if (!filteredObjects.isEmpty()) {
            objectList.setSelectedIndex(0);
        }

        // Make sure the search field has focus
        searchField.requestFocusInWindow();
    }



    private void filterByPropertyContaining(String searchText) {
        // Cancel any existing search
        if (currentSearchWorker != null && !currentSearchWorker.isDone()) {
            currentSearchWorker.cancel(true);
        }

        if (searchText.isEmpty()) {
            resetToAllUnits();
            notifyPropertyFilterListeners(null);
            return;
        }

        // Don't filter the tree for "Search by Property" - only filter object list
        statusLabel.setText("Searching properties...");

        // Use SwingWorker to perform the search in a background thread
        currentSearchWorker = new SwingWorker<List<ObjectValue>, Void>() {
            @Override
            protected List<ObjectValue> doInBackground() throws Exception {
                List<ObjectValue> results = new ArrayList<>();

                for (ObjectValue unit : ndfObjects) {
                    try {
                        // Check if unit has any property containing the search text
                        if (hasPropertyContaining(unit, searchText)) {
                            results.add(unit);
                        }
                    } catch (Exception e) {
                        // Skip units that cause errors
                        continue;
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

                    objectList.setModel(newModel);
                    listModel = newModel;
                    String objectTypeName = getObjectTypeNameForDisplay(currentFileType);
                    // Truncate long property names to prevent UI overlap
                    String displayText = searchText.length() > 15 ? searchText.substring(0, 15) + "..." : searchText;
                    statusLabel.setText(filteredObjects.size() + " " + objectTypeName + " contain '" + displayText + "'");

                    // Select the first object if available
                    if (!filteredObjects.isEmpty()) {
                        objectList.setSelectedIndex(0);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText("Error searching: " + e.getMessage());
                }
            }
        };

        currentSearchWorker.execute();
    }

    private void filterByHasPropertyCurrent(String searchText) {
        if (searchText.isEmpty()) {
            // Clear the tree filter
            notifyPropertyFilterListeners(null);
            statusLabel.setText("Enter property name to filter tree");
            return;
        }

        // Get the currently selected unit
        ObjectValue currentUnit = objectList.getSelectedValue();
        if (currentUnit == null) {
            statusLabel.setText("No unit selected - select a unit first");
            return;
        }

        // Always filter the tree to show only this property
        notifyPropertyFilterListeners(searchText);

        // Check if the current unit has the property and update status
        boolean hasProperty = hasExactProperty(currentUnit, searchText);
        String displayText = searchText.length() > 15 ? searchText.substring(0, 15) + "..." : searchText;

        if (hasProperty) {
            statusLabel.setText("Tree filtered to '" + displayText + "' - property found");
        } else {
            statusLabel.setText("Tree filtered to '" + displayText + "' - property not found");
        }
    }

    private boolean hasExactProperty(ObjectValue unit, String propertyPath) {
        // First try exact match
        if (com.warnomodmaker.model.PropertyUpdater.hasProperty(unit, propertyPath)) {
            return true;
        }

        // If no exact match, check if any property contains this text (for partial typing)
        return hasPropertyContaining(unit, propertyPath);
    }

    private boolean hasPropertyContaining(ObjectValue unit, String searchText) {
        // Do a deep search through all properties in the unit
        return searchInObject(unit, "", searchText);
    }

    private boolean searchInObject(ObjectValue obj, String basePath, String searchText) {
        if (obj == null) {
            return false;
        }

        for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
            String propertyName = entry.getKey();
            NDFValue value = entry.getValue();
            String fullPath = basePath.isEmpty() ? propertyName : basePath + "." + propertyName;

            // Check if the property name contains the search text
            if (propertyName.toLowerCase().contains(searchText.toLowerCase())) {
                return true;
            }

            // Recursively search nested objects
            if (value instanceof NDFValue.ObjectValue) {
                if (searchInObject((NDFValue.ObjectValue) value, fullPath, searchText)) {
                    return true;
                }
            }
            // Search in arrays
            else if (value instanceof NDFValue.ArrayValue) {
                NDFValue.ArrayValue arrayValue = (NDFValue.ArrayValue) value;
                for (int i = 0; i < arrayValue.getElements().size(); i++) {
                    NDFValue element = arrayValue.getElements().get(i);
                    if (element instanceof NDFValue.ObjectValue) {
                        if (searchInObject((NDFValue.ObjectValue) element, fullPath + "[" + i + "]", searchText)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }


    private String getObjectTypeNameForDisplay(NDFFileType fileType) {
        if (fileType != NDFFileType.UNKNOWN) {
            return fileType.getDisplayName().toLowerCase() + "s";
        }
        return "objects";
    }
}
