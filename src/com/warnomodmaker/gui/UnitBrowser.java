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
import com.warnomodmaker.model.TagExtractor;

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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
    private static final String SEARCH_BY_UNIT_TYPES = "Search by Unit Types";
    private static final String SEARCH_BY_WEAPONS_COMBAT = "Search by Weapons & Combat";
    private static final String SEARCH_BY_MOVEMENT_MOBILITY = "Search by Movement & Mobility";
    private static final String SEARCH_BY_SPECIAL_ABILITIES = "Search by Special Abilities";
    private static final String SEARCH_BY_UNIT_ROLE = "Search by Unit Role";
    private static final String SEARCH_BY_SPECIALTIES = "Search by Specialties";
    private static final String SEARCH_BY_OTHER_TAGS = "Search by Other Tags";


    public UnitBrowser() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Objects"));
        ndfObjects = new ArrayList<>();
        filteredObjects = new ArrayList<>();
        originalObjects = new ArrayList<>();
        selectionListeners = new ArrayList<>();
        propertyFilterListeners = new ArrayList<>();
        currentFileType = NDFFileType.UNKNOWN;
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Top row: Search type dropdown
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topRow.add(new JLabel("Search by:"));
        searchTypeComboBox = new JComboBox<>();
        updateSearchTypeOptions(); // Initialize with appropriate options
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

    /**
     * Update search type options based on current file type
     */
    private void updateSearchTypeOptions() {
        String currentSelection = (String) searchTypeComboBox.getSelectedItem();
        searchTypeComboBox.removeAllItems();

        // Always available search types
        searchTypeComboBox.addItem(SEARCH_BY_NAME);
        searchTypeComboBox.addItem(SEARCH_BY_PROPERTY);
        searchTypeComboBox.addItem(SEARCH_HAS_PROPERTY);

        // Tag-based searches - always available for all files
        searchTypeComboBox.addItem(SEARCH_BY_WEAPONS_COMBAT);
        searchTypeComboBox.addItem(SEARCH_BY_MOVEMENT_MOBILITY);
        searchTypeComboBox.addItem(SEARCH_BY_SPECIAL_ABILITIES);

        // Only add unit role and specialties searches for unitdescriptor files
        if (currentFileType == NDFFileType.UNITE_DESCRIPTOR) {
            searchTypeComboBox.addItem(SEARCH_BY_UNIT_ROLE);
            searchTypeComboBox.addItem(SEARCH_BY_SPECIALTIES);
            searchTypeComboBox.addItem(SEARCH_BY_UNIT_TYPES);
        }

        // Add "Other Tags" last as it's a catch-all category
        searchTypeComboBox.addItem(SEARCH_BY_OTHER_TAGS);

        // Try to restore previous selection if it's still available
        if (currentSelection != null) {
            for (int i = 0; i < searchTypeComboBox.getItemCount(); i++) {
                if (currentSelection.equals(searchTypeComboBox.getItemAt(i))) {
                    searchTypeComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }
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

        // Update search type options based on file type (after originalObjects is initialized)
        updateSearchTypeOptions();
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

        // Update search type options based on file type (after originalObjects is initialized)
        updateSearchTypeOptions();
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

    /**
     * Select an entity by its index in the original list
     */
    public void selectEntityByIndex(int index) {
        if (index >= 0 && index < listModel.getSize()) {
            objectList.setSelectedIndex(index);
            objectList.ensureIndexIsVisible(index);
            // Trigger selection event
            ObjectValue selectedObject = listModel.getElementAt(index);
            notifySelectionListeners(selectedObject);
        }
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
        } else if (SEARCH_BY_UNIT_TYPES.equals(searchType)) {
            filterByTagCategory(searchText, "Unit Types"); // Search by unit type tags
            return;
        } else if (SEARCH_BY_WEAPONS_COMBAT.equals(searchType)) {
            filterByTagCategory(searchText, "Weapons & Combat"); // Search by weapons & combat tags
            return;
        } else if (SEARCH_BY_MOVEMENT_MOBILITY.equals(searchType)) {
            filterByTagCategory(searchText, "Movement & Mobility"); // Search by movement & mobility tags
            return;
        } else if (SEARCH_BY_SPECIAL_ABILITIES.equals(searchType)) {
            filterByTagCategory(searchText, "Special Abilities"); // Search by special abilities tags
            return;
        } else if (SEARCH_BY_OTHER_TAGS.equals(searchType)) {
            filterByTagCategory(searchText, "Other"); // Search by other tags
            return;
        } else if (SEARCH_BY_UNIT_ROLE.equals(searchType)) {
            filterByUnitRole(searchText); // Search by unit role
            return;
        } else if (SEARCH_BY_SPECIALTIES.equals(searchType)) {
            filterBySpecialties(searchText); // Search by specialties
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
                            case SEARCH_BY_UNIT_TYPES:
                                matches = matchesTagCategory(unit, searchText, "Unit Types");
                                break;
                            case SEARCH_BY_WEAPONS_COMBAT:
                                matches = matchesTagCategory(unit, searchText, "Weapons & Combat");
                                break;
                            case SEARCH_BY_MOVEMENT_MOBILITY:
                                matches = matchesTagCategory(unit, searchText, "Movement & Mobility");
                                break;
                            case SEARCH_BY_SPECIAL_ABILITIES:
                                matches = matchesTagCategory(unit, searchText, "Special Abilities");
                                break;
                            case SEARCH_BY_OTHER_TAGS:
                                matches = matchesTagCategory(unit, searchText, "Other");
                                break;
                            case SEARCH_BY_UNIT_ROLE:
                                // Search by unit role
                                String unitRole = TagExtractor.extractUnitRole(unit);
                                matches = unitRole != null && unitRole.toLowerCase().contains(searchText.toLowerCase());
                                break;
                            case SEARCH_BY_SPECIALTIES:
                                // Search by specialties
                                java.util.Set<String> specialties = TagExtractor.extractSpecialtiesList(unit);
                                matches = specialties.stream().anyMatch(specialty ->
                                    specialty.toLowerCase().contains(searchText.toLowerCase()));
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
        // Use centralized property checking
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



    /**
     * Generic filter method that performs background filtering with a predicate.
     * Reduces code duplication across filterByTagCategory, filterByUnitRole, and filterBySpecialties.
     */
    private void filterWithPredicate(String searchText, Predicate<ObjectValue> matcher,
                                     String searchingMessage, String resultMessage, String errorMessage) {
        // Cancel any existing search
        if (currentSearchWorker != null && !currentSearchWorker.isDone()) {
            currentSearchWorker.cancel(true);
        }

        if (searchText.isEmpty()) {
            resetToAllUnits();
            return;
        }

        statusLabel.setText(searchingMessage);

        currentSearchWorker = new SwingWorker<List<ObjectValue>, Void>() {
            @Override
            protected List<ObjectValue> doInBackground() throws Exception {
                List<ObjectValue> results = new ArrayList<>();
                for (ObjectValue unit : ndfObjects) {
                    try {
                        if (matcher.test(unit)) {
                            results.add(unit);
                        }
                    } catch (Exception e) {
                        // Skip units that cause errors
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
                    statusLabel.setText(filteredObjects.size() + " " + objectTypeName + " " + resultMessage);
                    if (!filteredObjects.isEmpty()) {
                        objectList.setSelectedIndex(0);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText(errorMessage + e.getMessage());
                } finally {
                    searchField.setEnabled(true);
                    searchField.requestFocusInWindow();
                }
            }
        };

        currentSearchWorker.execute();
    }

    private void filterByTagCategory(String searchText, String category) {
        filterWithPredicate(searchText,
            unit -> matchesTagCategory(unit, searchText, category),
            "Searching by " + category.toLowerCase() + " tags...",
            "found with " + category.toLowerCase() + " tag containing '" + searchText + "'",
            "Error searching by " + category.toLowerCase() + " tags: ");
    }

    private boolean matchesTagCategory(ObjectValue unit, String searchText, String category) {
        java.util.Set<String> unitTags = TagExtractor.extractTagsFromUnit(unit);
        java.util.Set<String> categoryTags = getCategoryTags(unitTags, category);
        return categoryTags.stream().anyMatch(tag ->
            tag.toLowerCase().contains(searchText.toLowerCase()));
    }

    private java.util.Set<String> getCategoryTags(java.util.Set<String> allTags, String category) {
        switch (category) {
            case "Unit Types":
                return TagExtractor.categorizeUnitTypeTags(allTags);
            case "Weapons & Combat":
                return TagExtractor.categorizeWeaponTags(allTags);
            case "Movement & Mobility":
                return TagExtractor.categorizeMobilityTags(allTags);
            case "Special Abilities":
                return TagExtractor.categorizeSpecialTags(allTags);
            case "Other":
                java.util.Set<String> otherTags = new java.util.HashSet<>(allTags);
                otherTags.removeAll(TagExtractor.categorizeUnitTypeTags(allTags));
                otherTags.removeAll(TagExtractor.categorizeWeaponTags(allTags));
                otherTags.removeAll(TagExtractor.categorizeMobilityTags(allTags));
                otherTags.removeAll(TagExtractor.categorizeSpecialTags(allTags));
                return otherTags;
            default:
                return new java.util.HashSet<>();
        }
    }

    private void filterByUnitRole(String searchText) {
        filterWithPredicate(searchText,
            unit -> {
                String unitRole = TagExtractor.extractUnitRole(unit);
                return unitRole != null && unitRole.toLowerCase().contains(searchText.toLowerCase());
            },
            "Searching by unit role...",
            "found with unit role containing '" + searchText + "'",
            "Error searching by unit role: ");
    }

    private void filterBySpecialties(String searchText) {
        filterWithPredicate(searchText,
            unit -> {
                Set<String> specialties = TagExtractor.extractSpecialtiesList(unit);
                return specialties.stream().anyMatch(specialty ->
                    specialty.toLowerCase().contains(searchText.toLowerCase()));
            },
            "Searching by specialties...",
            "found with specialty containing '" + searchText + "'",
            "Error searching by specialties: ");
    }

    private String getObjectTypeNameForDisplay(NDFFileType fileType) {
        if (fileType != NDFFileType.UNKNOWN) {
            return fileType.getDisplayName().toLowerCase() + "s";
        }
        return "objects";
    }

    /**
     * Dispose of resources to prevent memory leaks when the browser is no longer needed.
     * This should be called when the parent tab is closed.
     */
    public void dispose() {
        // Stop the search timer
        if (searchTimer != null) {
            searchTimer.stop();
        }

        // Cancel any running search worker
        if (currentSearchWorker != null && !currentSearchWorker.isDone()) {
            currentSearchWorker.cancel(true);
        }

        // Clear listeners to prevent memory leaks
        selectionListeners.clear();
        propertyFilterListeners.clear();

        // Clear data references
        if (ndfObjects != null) {
            ndfObjects = null;
        }
        if (filteredObjects != null) {
            filteredObjects = null;
        }
        if (originalObjects != null) {
            originalObjects = null;
        }

        // Clear the list model
        if (listModel != null) {
            listModel.clear();
        }

        // Clear modification tracker reference
        modificationTracker = null;
        if (cellRenderer != null) {
            cellRenderer.setModificationTracker(null);
        }
    }
}
