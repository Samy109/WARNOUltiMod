package com.warnomodmaker.gui;

import com.warnomodmaker.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Dialog for performing additive operations on NDF files.
 * Allows adding new objects, modules, properties, and array elements.
 */
public class AdditiveOperationsDialog extends JDialog {
    
    private final List<NDFValue.ObjectValue> ndfObjects;
    private final NDFValue.NDFFileType fileType;
    private final AdditiveOperationManager operationManager;
    
    private JTabbedPane tabbedPane;
    private AddObjectPanel addObjectPanel;
    private AddModulePanel addModulePanel;
    private AddPropertyPanel addPropertyPanel;
    private AddArrayElementPanel addArrayElementPanel;
    
    private boolean operationPerformed = false;
    
    private ModificationTracker modificationTracker;

    public AdditiveOperationsDialog(Frame parent, List<NDFValue.ObjectValue> ndfObjects,
                                  NDFValue.NDFFileType fileType, ModificationTracker tracker) {
        super(parent, "Additive Operations", true);
        this.ndfObjects = ndfObjects != null ? ndfObjects : new ArrayList<>();
        this.fileType = fileType != null ? fileType : NDFValue.NDFFileType.UNKNOWN;
        this.modificationTracker = tracker;
        this.operationManager = new AdditiveOperationManager();

        try {
            // Learn from existing objects to build dynamic schemas
            operationManager.learnFromExistingObjects(this.ndfObjects);
        } catch (Exception e) {
            System.err.println("Error initializing additive operations: " + e.getMessage());
            e.printStackTrace();
        }

        initializeUI();
        setupEventHandlers();

        // Update templates after UI is fully initialized
        SwingUtilities.invokeLater(() -> {
            if (addObjectPanel != null) {
                addObjectPanel.updatePropertiesTemplate();
            }
            if (addModulePanel != null) {
                addModulePanel.updateModulePropertiesTemplate();
            }
        });

        pack();
        setLocationRelativeTo(parent);
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        
        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        
        // Create panels for each operation type
        addObjectPanel = new AddObjectPanel();
        addModulePanel = new AddModulePanel();
        addPropertyPanel = new AddPropertyPanel();
        addArrayElementPanel = new AddArrayElementPanel();
        
        // Add tabs
        tabbedPane.addTab("Add Object", addObjectPanel);
        tabbedPane.addTab("Add Module", addModulePanel);
        tabbedPane.addTab("Add Property", addPropertyPanel);
        tabbedPane.addTab("Add Array Element", addArrayElementPanel);
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Create button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton executeButton = new JButton("Execute");
        JButton cancelButton = new JButton("Cancel");
        
        executeButton.addActionListener(this::executeOperation);
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(executeButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        setPreferredSize(new Dimension(600, 500));
    }
    
    private void setupEventHandlers() {
        // Update available objects when tab changes
        tabbedPane.addChangeListener(e -> updateAvailableObjects());
        
        // Initial update
        updateAvailableObjects();
    }
    
    private void updateAvailableObjects() {
        int selectedTab = tabbedPane.getSelectedIndex();
        
        switch (selectedTab) {
            case 0: // Add Object tab
                addObjectPanel.updateAvailableObjects(ndfObjects);
                break;
            case 1: // Add Module tab
                addModulePanel.updateAvailableObjects(ndfObjects);
                break;
            case 2: // Add Property tab
                addPropertyPanel.updateAvailableObjects(ndfObjects);
                break;
            case 3: // Add Array Element tab
                addArrayElementPanel.updateAvailableObjects(ndfObjects);
                break;
        }
    }
    
    private void executeOperation(ActionEvent e) {
        try {
            int selectedTab = tabbedPane.getSelectedIndex();
            boolean success = false;
            
            switch (selectedTab) {
                case 0: // Add Object
                    success = addObjectPanel.executeOperation(operationManager, ndfObjects);
                    break;
                case 1: // Add Module
                    success = addModulePanel.executeOperation(operationManager);
                    break;
                case 2: // Add Property
                    success = addPropertyPanel.executeOperation(operationManager);
                    break;
                case 3: // Add Array Element
                    success = addArrayElementPanel.executeOperation(operationManager);
                    break;
            }
            
            if (success) {
                operationPerformed = true;
                JOptionPane.showMessageDialog(this, 
                    "Operation completed successfully!", 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Operation failed. Please check your inputs.", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
            
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error executing operation: " + ex.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public boolean wasOperationPerformed() {
        return operationPerformed;
    }
    
    /**
     * Panel for adding new objects
     */
    private class AddObjectPanel extends JPanel {
        private JComboBox<NDFValue.ObjectValue> targetEntityCombo;
        private JComboBox<String> targetLocationCombo;
        private JComboBox<String> objectTypeCombo;
        private JTextArea propertiesArea;
        
        public AddObjectPanel() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createTitledBorder("Add Object to Module Structure"));

            JPanel formPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;

            // Target entity selection
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("Target Entity:"), gbc);

            targetEntityCombo = new JComboBox<>();
            targetEntityCombo.addActionListener(e -> updateTargetLocations());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(targetEntityCombo, gbc);

            // Target location (module/array within entity)
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Add Object To:"), gbc);

            targetLocationCombo = new JComboBox<>();
            targetLocationCombo.addActionListener(e -> updateObjectTypes());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(targetLocationCombo, gbc);

            // Object type selection
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Object Type:"), gbc);

            objectTypeCombo = new JComboBox<>();
            objectTypeCombo.addActionListener(e -> updatePropertiesTemplate());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(objectTypeCombo, gbc);

            // Properties template
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Object Properties (modify template):"), gbc);

            propertiesArea = new JTextArea(8, 30);
            propertiesArea.setText("# Select a target location and object type to see template\n" +
                                 "# Modify the values as needed\n" +
                                 "# Properties will be discovered from similar objects");

            JScrollPane scrollPane = new JScrollPane(propertiesArea);
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
            formPanel.add(scrollPane, gbc);

            add(formPanel, BorderLayout.CENTER);

            // Add validation panel
            JPanel validationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel validationLabel = new JLabel("✓ Template loaded successfully");
            validationLabel.setForeground(new Color(0, 120, 0));
            validationPanel.add(validationLabel);
            add(validationPanel, BorderLayout.SOUTH);
        }
        
        public void updateAvailableObjects(List<NDFValue.ObjectValue> objects) {
            targetEntityCombo.removeAllItems();
            for (NDFValue.ObjectValue obj : objects) {
                targetEntityCombo.addItem(obj);
            }

            // Trigger the cascade of updates
            if (targetEntityCombo.getItemCount() > 0) {
                targetEntityCombo.setSelectedIndex(0);
                updateTargetLocations();
            }
        }

        private void updateTargetLocations() {
            targetLocationCombo.removeAllItems();

            NDFValue.ObjectValue selectedEntity = (NDFValue.ObjectValue) targetEntityCombo.getSelectedItem();
            if (selectedEntity == null) {
                return;
            }

            // Find all possible object containers
            Set<String> allLocations = new HashSet<>();

            // Find arrays that can contain objects
            findObjectArrays(selectedEntity, "", allLocations, 0);

            // Find object properties that can contain nested objects
            findObjectProperties(selectedEntity, "", allLocations, 0);

            if (allLocations.isEmpty()) {
                targetLocationCombo.addItem("No suitable locations found");
            } else {
                // Sort and add all discovered locations
                allLocations.stream().sorted().forEach(targetLocationCombo::addItem);
            }

            // Trigger object type update
            if (targetLocationCombo.getItemCount() > 0) {
                targetLocationCombo.setSelectedIndex(0);
                updateObjectTypes();
            }
        }

        private void findObjectArrays(NDFValue.ObjectValue obj, String pathPrefix, Set<String> locations, int depth) {
            if (depth > 5) return; // Prevent infinite recursion

            for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue value = entry.getValue();
                String currentPath = pathPrefix.isEmpty() ? propertyName : pathPrefix + "." + propertyName;

                if (value instanceof NDFValue.ArrayValue) {
                    NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
                    // Check if this array can contain objects (either already has objects or is empty)
                    if (array.getElements().isEmpty() ||
                        array.getElements().get(0) instanceof NDFValue.ObjectValue) {
                        locations.add(currentPath + " (Array)");
                    }
                } else if (value instanceof NDFValue.ObjectValue) {
                    // Recursively check nested objects for arrays
                    NDFValue.ObjectValue nestedObj = (NDFValue.ObjectValue) value;
                    findObjectArrays(nestedObj, currentPath, locations, depth + 1);
                }
            }
        }

        private void findObjectProperties(NDFValue.ObjectValue obj, String pathPrefix, Set<String> locations, int depth) {
            if (depth > 5) return; // Prevent infinite recursion

            for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue value = entry.getValue();
                String currentPath = pathPrefix.isEmpty() ? propertyName : pathPrefix + "." + propertyName;

                if (value instanceof NDFValue.ObjectValue) {
                    NDFValue.ObjectValue nestedObj = (NDFValue.ObjectValue) value;

                    // Add this object as a potential container for new object properties
                    // Skip very basic properties that shouldn't contain objects
                    if (!isBasicProperty(propertyName)) {
                        String displayName = getObjectDisplayName(nestedObj, currentPath);
                        locations.add(displayName + " (Object)");
                    }

                    // Recursively check deeper nested objects
                    findObjectProperties(nestedObj, currentPath, locations, depth + 1);
                    findObjectArrays(nestedObj, currentPath, locations, depth + 1);

                } else if (value instanceof NDFValue.ArrayValue) {
                    NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
                    List<NDFValue> elements = array.getElements();

                    // Check if array contains objects and explore them
                    for (int i = 0; i < elements.size(); i++) {
                        if (elements.get(i) instanceof NDFValue.ObjectValue) {
                            NDFValue.ObjectValue arrayObj = (NDFValue.ObjectValue) elements.get(i);
                            String displayName = getArrayElementDisplayName(arrayObj, currentPath, i);

                            // Add this array element as a potential container
                            locations.add(displayName + " (Object)");

                            // Recursively explore this array element
                            findObjectProperties(arrayObj, currentPath + "[" + i + "]", locations, depth + 1);
                            findObjectArrays(arrayObj, currentPath + "[" + i + "]", locations, depth + 1);
                        }
                    }
                }
            }
        }

        private boolean isBasicProperty(String propertyName) {
            // Skip properties that are unlikely to contain objects that users would want to add to
            return propertyName.equals("DescriptorId") ||
                   propertyName.equals("ClassNameForDebug") ||
                   propertyName.equals("World") ||
                   propertyName.equals("NameForDebug");
        }

        private String getObjectDisplayName(NDFValue.ObjectValue obj, String path) {
            String typeName = obj.getTypeName();
            if (typeName != null) {
                // Clean up type name for display
                String cleanTypeName = typeName.replaceAll("^T", "").replaceAll("Descriptor$", "");
                return path + " (" + cleanTypeName + ")";
            }
            return path;
        }

        private String getArrayElementDisplayName(NDFValue.ObjectValue obj, String arrayPath, int index) {
            String typeName = obj.getTypeName();
            if (typeName != null) {
                // Clean up type name for display
                String cleanTypeName = typeName.replaceAll("^T", "").replaceAll("Descriptor$", "");

                // For ModulesDescriptors, show more meaningful names
                if (arrayPath.contains("ModulesDescriptors")) {
                    return arrayPath + " → " + cleanTypeName + " Module";
                } else {
                    return arrayPath + " → " + cleanTypeName + " [" + index + "]";
                }
            }

            // Fallback to showing just the index if no type name
            return arrayPath + "[" + index + "]";
        }

        private String extractPathFromDisplayName(String displayName) {
            // Extract the actual path from display names
            if (displayName.contains(" → ")) {
                // For array elements like "ModulesDescriptors → Damage Module"
                String[] parts = displayName.split(" → ");
                String basePath = parts[0];

                // If it contains module info, we need to find the actual index
                if (parts.length > 1 && parts[1].contains("Module")) {
                    // Find the index of this module type in the current entity
                    String moduleTypeName = parts[1].replace(" Module", "");
                    int index = findModuleIndex(moduleTypeName, basePath);
                    if (index >= 0) {
                        return basePath + "[" + index + "]";
                    }
                    return basePath + "[0]"; // Fallback to first element
                } else if (parts[1].contains("[") && parts[1].contains("]")) {
                    // Extract index from display like "SomeType [0]"
                    String indexPart = parts[1].substring(parts[1].lastIndexOf("["));
                    return basePath + indexPart;
                }
                return basePath;
            } else if (displayName.contains(" (Array)")) {
                return displayName.split(" \\(Array\\)")[0];
            } else if (displayName.contains(" (Object)")) {
                return displayName.split(" \\(Object\\)")[0];
            } else if (displayName.contains(" (")) {
                // Handle cases like "SomePath (SomeType)"
                return displayName.split(" \\(")[0];
            }

            return displayName;
        }

        private int findModuleIndex(String moduleTypeName, String arrayPath) {
            // Find the index of a module with the given type name in the specified array
            NDFValue.ObjectValue selectedEntity = (NDFValue.ObjectValue) targetEntityCombo.getSelectedItem();
            if (selectedEntity == null) return -1;

            NDFValue targetValue = getValueAtPath(selectedEntity, arrayPath);
            if (!(targetValue instanceof NDFValue.ArrayValue)) return -1;

            NDFValue.ArrayValue array = (NDFValue.ArrayValue) targetValue;
            List<NDFValue> elements = array.getElements();

            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i) instanceof NDFValue.ObjectValue) {
                    NDFValue.ObjectValue obj = (NDFValue.ObjectValue) elements.get(i);
                    String typeName = obj.getTypeName();
                    if (typeName != null) {
                        String cleanTypeName = typeName.replaceAll("^T", "").replaceAll("Descriptor$", "");
                        if (cleanTypeName.equals(moduleTypeName)) {
                            return i;
                        }
                    }
                }
            }
            return -1;
        }

        private void updateObjectTypes() {
            objectTypeCombo.removeAllItems();

            String selectedLocation = (String) targetLocationCombo.getSelectedItem();
            if (selectedLocation == null || selectedLocation.contains("No suitable locations")) {
                return;
            }

            // Get object types that are appropriate for this location
            Set<String> availableTypes = getObjectTypesForLocation(selectedLocation);

            if (availableTypes.isEmpty()) {
                objectTypeCombo.addItem("No object types found");
            } else {
                availableTypes.stream().sorted().forEach(objectTypeCombo::addItem);
            }

            // Trigger template update
            if (objectTypeCombo.getItemCount() > 0) {
                objectTypeCombo.setSelectedIndex(0);
                updatePropertiesTemplate();
            }
        }

        private Set<String> getObjectTypesForLocation(String location) {
            Set<String> discoveredTypes = new HashSet<>();

            // Extract the property path and location type from the location
            String propertyPath = extractPathFromDisplayName(location);
            String locationType;
            if (location.contains(" (Array)")) {
                locationType = "Array";
            } else if (location.contains(" (Object)")) {
                locationType = "Object";
            } else {
                locationType = "Unknown";
            }

            // First, get ALL available types from templates (both object and module types)
            Set<String> allTemplateTypes = new HashSet<>();
            Set<String> objectTypes = operationManager.getTemplateManager().getAvailableObjectTypes();
            Set<String> moduleTypes = operationManager.getTemplateManager().getAvailableModuleTypes();

            System.out.println("DEBUG: Template manager has " + objectTypes.size() + " object types and " + moduleTypes.size() + " module types");

            allTemplateTypes.addAll(objectTypes);
            allTemplateTypes.addAll(moduleTypes);

            // Find objects in this location across all entities to see what types actually exist
            for (NDFValue.ObjectValue entity : ndfObjects) {
                if (locationType.equals("Array")) {
                    // For arrays, find what object types are contained
                    NDFValue targetValue = getValueAtPath(entity, propertyPath);
                    if (targetValue instanceof NDFValue.ArrayValue) {
                        NDFValue.ArrayValue array = (NDFValue.ArrayValue) targetValue;
                        for (NDFValue element : array.getElements()) {
                            if (element instanceof NDFValue.ObjectValue) {
                                NDFValue.ObjectValue obj = (NDFValue.ObjectValue) element;
                                String typeName = obj.getTypeName();
                                if (typeName != null) {
                                    discoveredTypes.add(typeName);
                                }
                            }
                        }
                    }
                } else if (locationType.equals("Object")) {
                    // For objects, find what types of objects can be nested within similar objects
                    NDFValue targetValue = getValueAtPath(entity, propertyPath);
                    if (targetValue instanceof NDFValue.ObjectValue) {
                        // Look at what object types appear as properties in similar objects
                        for (NDFValue.ObjectValue otherEntity : ndfObjects) {
                            NDFValue otherTargetValue = getValueAtPath(otherEntity, propertyPath);
                            if (otherTargetValue instanceof NDFValue.ObjectValue) {
                                NDFValue.ObjectValue otherTargetObj = (NDFValue.ObjectValue) otherTargetValue;
                                // Check what object types appear as direct properties
                                for (NDFValue propertyValue : otherTargetObj.getProperties().values()) {
                                    if (propertyValue instanceof NDFValue.ObjectValue) {
                                        NDFValue.ObjectValue propObj = (NDFValue.ObjectValue) propertyValue;
                                        String typeName = propObj.getTypeName();
                                        if (typeName != null) {
                                            discoveredTypes.add(typeName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // If we found specific types in this location, prioritize those
            if (!discoveredTypes.isEmpty()) {
                // Add the discovered types plus all template types for flexibility
                discoveredTypes.addAll(allTemplateTypes);
            } else {
                // No specific types found, but still offer all available template types
                discoveredTypes.addAll(allTemplateTypes);

                // For ModulesDescriptors arrays, prioritize module types
                if (propertyPath.contains("ModulesDescriptors")) {
                    Set<String> prioritizedModuleTypes = operationManager.getTemplateManager().getAvailableModuleTypes();
                    // Put module types first by creating a new ordered set
                    Set<String> orderedTypes = new LinkedHashSet<>(prioritizedModuleTypes);
                    orderedTypes.addAll(discoveredTypes);
                    discoveredTypes = orderedTypes;
                }
            }

            return discoveredTypes;
        }

        private NDFValue getValueAtPath(NDFValue.ObjectValue obj, String path) {
            String[] parts = path.split("\\.");
            NDFValue current = obj;

            for (String part : parts) {
                if (!(current instanceof NDFValue.ObjectValue)) {
                    return null;
                }

                NDFValue.ObjectValue currentObj = (NDFValue.ObjectValue) current;

                if (part.contains("[") && part.contains("]")) {
                    // Handle array access like "ModulesDescriptors[0]"
                    String propertyName = part.substring(0, part.indexOf("["));
                    int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                    NDFValue arrayValue = currentObj.getProperty(propertyName);
                    if (!(arrayValue instanceof NDFValue.ArrayValue)) {
                        return null;
                    }

                    NDFValue.ArrayValue array = (NDFValue.ArrayValue) arrayValue;
                    if (index < 0 || index >= array.getElements().size()) {
                        return null;
                    }

                    current = array.getElements().get(index);
                } else {
                    // Simple property access
                    current = currentObj.getProperty(part);
                }

                if (current == null) {
                    return null;
                }
            }

            return current;
        }





        public void updatePropertiesTemplate() {
            // Check if components are initialized
            if (propertiesArea == null || objectTypeCombo == null) {
                return;
            }

            String selectedType = (String) objectTypeCombo.getSelectedItem();
            if (selectedType == null || selectedType.contains("No object types")) {
                propertiesArea.setText("# No templates available\n# Please ensure a file is loaded");
                return;
            }

            // Get the template for this object type (try both object and module templates)
            NDFValue.ObjectValue template = operationManager.getTemplateManager().getTemplate(selectedType);
            if (template == null) {
                template = operationManager.getTemplateManager().getModuleTemplate(selectedType);
            }

            if (template == null) {
                // No template learned - provide basic template structure
                propertiesArea.setText("# Basic template for " + selectedType + "\n" +
                                     "# No existing template found - you can add properties manually\n" +
                                     "# Use format: PropertyName=Value\n" +
                                     "# Example properties:\n" +
                                     "# Name='MyObject'\n" +
                                     "# Value=100\n" +
                                     "# Enabled=True\n\n");
                return;
            }

            // Generate template text from learned properties
            StringBuilder templateText = new StringBuilder();
            templateText.append("# Template for ").append(selectedType).append("\n");
            templateText.append("# Learned from actual file content\n");
            templateText.append("# Modify values as needed (PropertyName=Value format)\n\n");

            // Add all learned properties without categorization
            for (Map.Entry<String, NDFValue> entry : template.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue propertyValue = entry.getValue();
                templateText.append(propertyName).append("=")
                    .append(formatValueForTemplate(propertyValue)).append("\n");
            }

            propertiesArea.setText(templateText.toString());
        }



        private String formatValueForTemplate(NDFValue value) {
            switch (value.getType()) {
                case STRING:
                    return "'" + ((NDFValue.StringValue) value).getValue() + "'";
                case NUMBER:
                    return String.valueOf(((NDFValue.NumberValue) value).getValue());
                case BOOLEAN:
                    return String.valueOf(((NDFValue.BooleanValue) value).getValue());
                case ENUM:
                    return ((NDFValue.EnumValue) value).getValue();
                case TEMPLATE_REF:
                    return ((NDFValue.TemplateRefValue) value).getPath();
                case RESOURCE_REF:
                    return ((NDFValue.ResourceRefValue) value).getPath();
                case GUID:
                    return "PLACEHOLDER_GUID";
                case ARRAY:
                    NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
                    if (array.getElements().isEmpty()) {
                        return "[]";
                    }
                    StringBuilder arrayStr = new StringBuilder("[");
                    for (int i = 0; i < array.getElements().size(); i++) {
                        if (i > 0) arrayStr.append(", ");
                        arrayStr.append(formatValueForTemplate(array.getElements().get(i)));
                    }
                    arrayStr.append("]");
                    return arrayStr.toString();
                case OBJECT:
                    return "# Complex object - see template for " + ((NDFValue.ObjectValue) value).getTypeName();
                default:
                    return value.toString();
            }
        }
        
        public boolean executeOperation(AdditiveOperationManager manager, List<NDFValue.ObjectValue> objects) {
            NDFValue.ObjectValue targetEntity = (NDFValue.ObjectValue) targetEntityCombo.getSelectedItem();
            String targetLocation = (String) targetLocationCombo.getSelectedItem();
            String objectType = (String) objectTypeCombo.getSelectedItem();

            if (targetEntity == null || targetLocation == null || objectType == null) {
                return false;
            }

            // Check if these are error messages rather than real values
            if (targetLocation.contains("No suitable locations") || objectType.contains("No object types")) {
                return false;
            }

            // Parse properties
            Map<String, Object> properties = parseProperties(propertiesArea.getText());

            return addObjectToLocation(targetEntity, targetLocation, objectType, properties);
        }

        private boolean addObjectToLocation(NDFValue.ObjectValue targetEntity, String targetLocation,
                                          String objectType, Map<String, Object> properties) {
            try {
                // Extract the property path and location type from the location
                String propertyPath = extractPathFromDisplayName(targetLocation);
                String locationType;
                if (targetLocation.contains(" (Array)")) {
                    locationType = "Array";
                } else if (targetLocation.contains(" (Object)")) {
                    locationType = "Object";
                } else {
                    locationType = "Unknown";
                }

                // Create new object from template if available
                NDFValue.ObjectValue newObject;
                NDFValue.ObjectValue template = operationManager.getTemplateManager().getTemplate(objectType);
                if (template == null) {
                    // Try module template if object template not found
                    template = operationManager.getTemplateManager().getModuleTemplate(objectType);
                }

                if (template != null) {
                    // Clone template and apply properties
                    newObject = cloneObject(template);
                    applyPropertiesToObject(newObject, properties);
                } else {
                    // Create basic object if no template available
                    newObject = new NDFValue.ObjectValue(objectType);
                    // Add properties to the new object
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        NDFValue propertyValue = convertToNDFValue(entry.getValue());
                        newObject.setProperty(entry.getKey(), propertyValue);
                        newObject.setPropertyComma(entry.getKey(), true);
                    }
                }

                if (locationType.equals("Array")) {
                    // Navigate to the target array
                    NDFValue targetValue = getValueAtPath(targetEntity, propertyPath);
                    if (!(targetValue instanceof NDFValue.ArrayValue)) {
                        return false;
                    }

                    NDFValue.ArrayValue targetArray = (NDFValue.ArrayValue) targetValue;
                    // Add to array
                    targetArray.addElement(newObject);

                } else if (locationType.equals("Object")) {
                    // For object locations, we need to add as a new property
                    NDFValue targetValue = getValueAtPath(targetEntity, propertyPath);
                    if (targetValue instanceof NDFValue.ObjectValue) {
                        NDFValue.ObjectValue targetObj = (NDFValue.ObjectValue) targetValue;
                        // Generate a property name based on object type
                        String propertyName = generatePropertyName(objectType, targetObj);
                        targetObj.setProperty(propertyName, newObject);
                        targetObj.setPropertyComma(propertyName, true);
                    } else {
                        return false;
                    }
                }

                // Record modification
                if (modificationTracker != null) {
                    String entityName = targetEntity.getInstanceName() != null ?
                        targetEntity.getInstanceName() : "Unknown Entity";

                    modificationTracker.recordModification(
                        entityName,
                        propertyPath + "[new]",
                        null,
                        newObject,
                        PropertyUpdater.ModificationType.OBJECT_ADDED,
                        "Added " + objectType + " object to " + propertyPath
                    );
                }

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        private String generatePropertyName(String objectType, NDFValue.ObjectValue targetObj) {
            // Generate a unique property name based on object type
            String baseName = objectType.replaceAll("^T", "").replaceAll("Descriptor$", "");
            String propertyName = baseName;
            int counter = 1;

            // Ensure uniqueness
            while (targetObj.hasProperty(propertyName)) {
                propertyName = baseName + counter;
                counter++;
            }

            return propertyName;
        }

        private NDFValue.ObjectValue cloneObject(NDFValue.ObjectValue original) {
            NDFValue.ObjectValue clone = new NDFValue.ObjectValue(original.getTypeName());
            for (Map.Entry<String, NDFValue> entry : original.getProperties().entrySet()) {
                clone.setProperty(entry.getKey(), cloneValue(entry.getValue()));
                clone.setPropertyComma(entry.getKey(), original.hasCommaAfter(entry.getKey()));
            }
            return clone;
        }

        private NDFValue cloneValue(NDFValue value) {
            // Simple cloning - for templates this should be sufficient
            switch (value.getType()) {
                case STRING:
                    NDFValue.StringValue strVal = (NDFValue.StringValue) value;
                    return new NDFValue.StringValue(strVal.getValue(), strVal.useDoubleQuotes());
                case NUMBER:
                    NDFValue.NumberValue numVal = (NDFValue.NumberValue) value;
                    return new NDFValue.NumberValue(numVal.getValue());
                case BOOLEAN:
                    NDFValue.BooleanValue boolVal = (NDFValue.BooleanValue) value;
                    return new NDFValue.BooleanValue(boolVal.getValue());
                case ENUM:
                    NDFValue.EnumValue enumVal = (NDFValue.EnumValue) value;
                    return new NDFValue.EnumValue(enumVal.getValue());
                case TEMPLATE_REF:
                    NDFValue.TemplateRefValue templateVal = (NDFValue.TemplateRefValue) value;
                    return new NDFValue.TemplateRefValue(templateVal.getPath());
                case RESOURCE_REF:
                    NDFValue.ResourceRefValue resourceVal = (NDFValue.ResourceRefValue) value;
                    return new NDFValue.ResourceRefValue(resourceVal.getPath());
                case GUID:
                    // Generate new GUID for cloned objects
                    return new NDFValue.GUIDValue("PLACEHOLDER_GUID");
                case ARRAY:
                    NDFValue.ArrayValue arrayVal = (NDFValue.ArrayValue) value;
                    NDFValue.ArrayValue newArray = new NDFValue.ArrayValue();
                    for (NDFValue element : arrayVal.getElements()) {
                        newArray.addElement(cloneValue(element));
                    }
                    return newArray;
                case OBJECT:
                    return cloneObject((NDFValue.ObjectValue) value);
                default:
                    return value; // For other types, return as-is
            }
        }

        private void applyPropertiesToObject(NDFValue.ObjectValue object, Map<String, Object> properties) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                NDFValue propertyValue = convertToNDFValue(entry.getValue());
                object.setProperty(entry.getKey(), propertyValue);
                object.setPropertyComma(entry.getKey(), true);
            }
        }

        private NDFValue convertToNDFValue(Object value) {
            if (value instanceof String) {
                return new NDFValue.StringValue((String) value);
            } else if (value instanceof Integer) {
                return new NDFValue.NumberValue(((Integer) value).doubleValue());
            } else if (value instanceof Double) {
                return new NDFValue.NumberValue((Double) value);
            } else if (value instanceof Boolean) {
                return new NDFValue.BooleanValue((Boolean) value);
            } else {
                return new NDFValue.StringValue(value.toString());
            }
        }
        
        private Map<String, Object> parseProperties(String text) {
            Map<String, Object> properties = new HashMap<>();
            String[] lines = text.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    
                    // Try to parse value as appropriate type
                    Object parsedValue = parseValue(value);
                    properties.put(key, parsedValue);
                }
            }
            
            return properties;
        }
        
        private Object parseValue(String value) {
            // Try to determine the type and parse accordingly
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(value);
            }
            
            try {
                if (value.contains(".")) {
                    return Double.parseDouble(value);
                } else {
                    return Integer.parseInt(value);
                }
            } catch (NumberFormatException e) {
                // Not a number, treat as string
                return value;
            }
        }
    }
    
    /**
     * Panel for adding modules to existing objects
     */
    private class AddModulePanel extends JPanel {
        private JComboBox<NDFValue.ObjectValue> targetObjectCombo;
        private JComboBox<String> moduleTypeCombo;
        private JTextArea modulePropertiesArea;
        
        public AddModulePanel() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createTitledBorder("Add Module to Object"));
            
            JPanel formPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            
            // Target object selection
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("Target Object:"), gbc);
            
            targetObjectCombo = new JComboBox<>();
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(targetObjectCombo, gbc);
            
            // Module type selection
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Module Type:"), gbc);
            
            moduleTypeCombo = new JComboBox<>();
            populateModuleTypes();
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(moduleTypeCombo, gbc);
            
            // Module properties
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Module Properties (modify template):"), gbc);

            modulePropertiesArea = new JTextArea(8, 30);
            modulePropertiesArea.setText("# Template will be loaded when you select a module type\n" +
                                       "# Modify the values as needed\n" +
                                       "# All discovered properties will be shown with defaults");

            // Add listener to update template when module type changes
            moduleTypeCombo.addActionListener(e -> updateModulePropertiesTemplate());

            JScrollPane scrollPane = new JScrollPane(modulePropertiesArea);
            gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
            formPanel.add(scrollPane, gbc);
            
            add(formPanel, BorderLayout.CENTER);
        }
        
        private void populateModuleTypes() {
            moduleTypeCombo.removeAllItems();

            // Get available module types from the dynamic template manager
            Set<String> availableModuleTypes = operationManager.getTemplateManager().getAvailableModuleTypes();

            if (availableModuleTypes.isEmpty()) {
                // If no module templates learned yet, show a message
                moduleTypeCombo.addItem("No module types discovered in file");
            } else {
                // Filter and sort module types for better relevance
                Set<String> filteredModules = filterModuleTypesForFile(availableModuleTypes);

                if (filteredModules.isEmpty()) {
                    moduleTypeCombo.addItem("No relevant module types for this file");
                } else {
                    // Add modules sorted by relevance and frequency
                    filteredModules.stream()
                        .sorted((a, b) -> {
                            // Sort by relevance first, then alphabetically
                            int relevanceA = getModuleRelevanceScore(a);
                            int relevanceB = getModuleRelevanceScore(b);
                            if (relevanceA != relevanceB) {
                                return Integer.compare(relevanceB, relevanceA); // Higher relevance first
                            }
                            return a.compareTo(b);
                        })
                        .forEach(moduleTypeCombo::addItem);
                }
            }

            // Template will be updated when components are fully initialized
        }

        private Set<String> filterModuleTypesForFile(Set<String> allModules) {
            Set<String> filtered = new HashSet<>();

            // Always include modules that actually exist in the current file
            for (String moduleType : allModules) {
                if (moduleExistsInFile(moduleType)) {
                    filtered.add(moduleType);
                }
            }

            // Add all modules for comprehensive coverage (no file type bias)
            filtered.addAll(allModules);

            return filtered;
        }

        private boolean moduleExistsInFile(String moduleType) {
            for (NDFValue.ObjectValue obj : ndfObjects) {
                if (obj.hasProperty("ModulesDescriptors")) {
                    NDFValue modulesValue = obj.getProperty("ModulesDescriptors");
                    if (modulesValue instanceof NDFValue.ArrayValue) {
                        NDFValue.ArrayValue modulesArray = (NDFValue.ArrayValue) modulesValue;
                        for (NDFValue moduleValue : modulesArray.getElements()) {
                            if (moduleValue instanceof NDFValue.ObjectValue) {
                                NDFValue.ObjectValue module = (NDFValue.ObjectValue) moduleValue;
                                if (moduleType.equals(module.getTypeName())) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }





        private int getModuleRelevanceScore(String moduleType) {
            // Score modules by how commonly they appear and how essential they are
            int score = 0;

            // Essential modules get higher scores
            if (moduleType.contains("Type") || moduleType.contains("Tags")) score += 10;
            if (moduleType.contains("Damage") || moduleType.contains("Production")) score += 8;
            if (moduleType.contains("Experience") || moduleType.contains("Visibility")) score += 6;

            // Add frequency score
            for (NDFValue.ObjectValue obj : ndfObjects) {
                if (obj.hasProperty("ModulesDescriptors")) {
                    NDFValue modulesValue = obj.getProperty("ModulesDescriptors");
                    if (modulesValue instanceof NDFValue.ArrayValue) {
                        NDFValue.ArrayValue modulesArray = (NDFValue.ArrayValue) modulesValue;
                        for (NDFValue moduleValue : modulesArray.getElements()) {
                            if (moduleValue instanceof NDFValue.ObjectValue) {
                                NDFValue.ObjectValue module = (NDFValue.ObjectValue) moduleValue;
                                if (moduleType.equals(module.getTypeName())) {
                                    score += 1;
                                }
                            }
                        }
                    }
                }
            }

            return score;
        }

        public void updateModulePropertiesTemplate() {
            // Check if components are initialized
            if (modulePropertiesArea == null || moduleTypeCombo == null) {
                return;
            }

            String selectedType = (String) moduleTypeCombo.getSelectedItem();
            if (selectedType == null || selectedType.contains("No module types") || selectedType.contains("No relevant module types")) {
                modulePropertiesArea.setText("# No module templates available\n# Please ensure a file is loaded");
                return;
            }

            // Get the template for this module type
            NDFValue.ObjectValue template = operationManager.getTemplateManager().getModuleTemplate(selectedType);
            if (template == null) {
                modulePropertiesArea.setText("# No template found for " + selectedType + "\n" +
                                           "# You can add properties manually in key=value format");
                return;
            }

            // Generate template text showing all properties with their default values
            StringBuilder templateText = new StringBuilder();
            templateText.append("# Template for ").append(selectedType).append("\n");
            templateText.append("# Modify the values as needed, keep the format: PropertyName=Value\n");
            templateText.append("# Remove lines for properties you don't want to include\n\n");

            // Add all properties from the template
            for (Map.Entry<String, NDFValue> entry : template.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue propertyValue = entry.getValue();

                templateText.append(propertyName).append("=").append(formatValueForTemplate(propertyValue)).append("\n");
            }

            modulePropertiesArea.setText(templateText.toString());
        }

        private String formatValueForTemplate(NDFValue value) {
            switch (value.getType()) {
                case STRING:
                    return "'" + ((NDFValue.StringValue) value).getValue() + "'";
                case NUMBER:
                    return String.valueOf(((NDFValue.NumberValue) value).getValue());
                case BOOLEAN:
                    return String.valueOf(((NDFValue.BooleanValue) value).getValue());
                case ENUM:
                    return ((NDFValue.EnumValue) value).getValue();
                case TEMPLATE_REF:
                    return ((NDFValue.TemplateRefValue) value).getPath();
                case RESOURCE_REF:
                    return ((NDFValue.ResourceRefValue) value).getPath();
                case GUID:
                    return "PLACEHOLDER_GUID";
                case ARRAY:
                    NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
                    if (array.getElements().isEmpty()) {
                        return "[]";
                    }
                    StringBuilder arrayStr = new StringBuilder("[");
                    for (int i = 0; i < array.getElements().size(); i++) {
                        if (i > 0) arrayStr.append(", ");
                        arrayStr.append(formatValueForTemplate(array.getElements().get(i)));
                    }
                    arrayStr.append("]");
                    return arrayStr.toString();
                case OBJECT:
                    return "# Complex object - see template for " + ((NDFValue.ObjectValue) value).getTypeName();
                default:
                    return value.toString();
            }
        }
        
        public void updateAvailableObjects(List<NDFValue.ObjectValue> objects) {
            targetObjectCombo.removeAllItems();

            // Check if ANY object in this file type has ModulesDescriptors
            boolean fileSupportsModules = objects.stream()
                .anyMatch(obj -> obj.hasProperty("ModulesDescriptors"));

            if (!fileSupportsModules) {
                // If no objects have ModulesDescriptors, disable the components
                setModulePanelEnabled(false);
                return;
            }

            // Enable components and add objects that have ModulesDescriptors
            setModulePanelEnabled(true);
            for (NDFValue.ObjectValue obj : objects) {
                if (obj.hasProperty("ModulesDescriptors")) {
                    targetObjectCombo.addItem(obj);
                }
            }
        }

        private void setModulePanelEnabled(boolean enabled) {
            targetObjectCombo.setEnabled(enabled);
            moduleTypeCombo.setEnabled(enabled);
            modulePropertiesArea.setEnabled(enabled);

            if (!enabled) {
                modulePropertiesArea.setText("# Add Module functionality is not supported for this file type\n" +
                                           "# This file type does not contain objects with ModulesDescriptors\n" +
                                           "# Use 'Add Property' or 'Add Object' instead");
            }
        }
        
        public boolean executeOperation(AdditiveOperationManager manager) {
            // Check if components are disabled (file doesn't support modules)
            if (!targetObjectCombo.isEnabled()) {
                return false;
            }

            NDFValue.ObjectValue targetObject = (NDFValue.ObjectValue) targetObjectCombo.getSelectedItem();
            String moduleType = (String) moduleTypeCombo.getSelectedItem();

            if (targetObject == null || moduleType == null) {
                return false;
            }

            // Check if this is an error message rather than a real module type
            if (moduleType.contains("No module types") || moduleType.contains("No relevant module types")) {
                return false;
            }

            Map<String, Object> properties = parseProperties(modulePropertiesArea.getText());

            return manager.addModuleToObject(targetObject, moduleType, properties, modificationTracker);
        }
        
        private Map<String, Object> parseProperties(String text) {
            Map<String, Object> properties = new HashMap<>();
            String[] lines = text.split("\n");

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();

                    // Try to parse value as appropriate type
                    Object parsedValue = parseValue(value);
                    properties.put(key, parsedValue);
                }
            }

            return properties;
        }

        private Object parseValue(String value) {
            // Handle array values like ['item1', 'item2']
            if (value.startsWith("[") && value.endsWith("]")) {
                return parseArrayString(value);
            }

            // Try to determine the type and parse accordingly
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(value);
            }

            try {
                if (value.contains(".")) {
                    return Double.parseDouble(value);
                } else {
                    return Integer.parseInt(value);
                }
            } catch (NumberFormatException e) {
                // Not a number, treat as string
                return value;
            }
        }

        private Object parseArrayString(String value) {
            // Simple array parsing for module properties
            String content = value.substring(1, value.length() - 1).trim();
            if (content.isEmpty()) {
                return new String[0];
            }

            String[] elements = content.split(",");
            String[] result = new String[elements.length];
            for (int i = 0; i < elements.length; i++) {
                String element = elements[i].trim();
                // Remove quotes if present
                if ((element.startsWith("'") && element.endsWith("'")) ||
                    (element.startsWith("\"") && element.endsWith("\""))) {
                    element = element.substring(1, element.length() - 1);
                }
                result[i] = element;
            }
            return result;
        }
    }
    
    /**
     * Panel for adding properties to existing objects or modules
     */
    private class AddPropertyPanel extends JPanel {
        private JComboBox<NDFValue.ObjectValue> targetObjectCombo;
        private JComboBox<String> targetLocationCombo;
        private JTextField propertyNameField;
        private JComboBox<String> propertyTypeCombo;
        private JTextArea propertyValueArea;
        private JLabel locationLabel;

        public AddPropertyPanel() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createTitledBorder("Add Property to Object or Module"));

            JPanel formPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;

            // Target object selection
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("Target Object:"), gbc);

            targetObjectCombo = new JComboBox<>();
            targetObjectCombo.addActionListener(e -> updateTargetLocations());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(targetObjectCombo, gbc);

            // Target location (object itself or specific module)
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
            locationLabel = new JLabel("Add Property To:");
            formPanel.add(locationLabel, gbc);

            targetLocationCombo = new JComboBox<>();
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(targetLocationCombo, gbc);

            // Property name
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Property Name:"), gbc);

            propertyNameField = new JTextField(20);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(propertyNameField, gbc);

            // Property type
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Property Type:"), gbc);

            propertyTypeCombo = new JComboBox<>();
            populatePropertyTypes();
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(propertyTypeCombo, gbc);

            // Property value
            gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Property Value:"), gbc);

            propertyValueArea = new JTextArea(6, 30);
            propertyValueArea.setText("# Enter property value based on type:\n" +
                                    "# String: 'MyValue'\n" +
                                    "# Number: 42 or 3.14\n" +
                                    "# Boolean: True or False\n" +
                                    "# Array: ['item1', 'item2']\n" +
                                    "# Enum: ECoalition/Allied");
            JScrollPane scrollPane = new JScrollPane(propertyValueArea);
            gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
            formPanel.add(scrollPane, gbc);

            add(formPanel, BorderLayout.CENTER);
        }

        private void updateTargetLocations() {
            targetLocationCombo.removeAllItems();

            NDFValue.ObjectValue selectedObject = (NDFValue.ObjectValue) targetObjectCombo.getSelectedItem();
            if (selectedObject == null) {
                return;
            }

            // Always add option to add property to the object itself
            targetLocationCombo.addItem("Object Root (" + selectedObject.getTypeName() + ")");

            // Add options for each module in ModulesDescriptors (if it exists)
            if (selectedObject.hasProperty("ModulesDescriptors")) {
                NDFValue modulesValue = selectedObject.getProperty("ModulesDescriptors");
                if (modulesValue instanceof NDFValue.ArrayValue) {
                    NDFValue.ArrayValue modulesArray = (NDFValue.ArrayValue) modulesValue;
                    List<NDFValue> modules = modulesArray.getElements();

                    for (int i = 0; i < modules.size(); i++) {
                        NDFValue moduleValue = modules.get(i);
                        if (moduleValue instanceof NDFValue.ObjectValue) {
                            NDFValue.ObjectValue module = (NDFValue.ObjectValue) moduleValue;
                            String displayName = String.format("Module[%d]: %s", i, module.getTypeName());
                            targetLocationCombo.addItem(displayName);
                        }
                    }
                }
            }

            // Add options for nested objects/arrays that could accept properties
            addNestedObjectOptions(selectedObject, "");
        }

        private void addNestedObjectOptions(NDFValue.ObjectValue obj, String pathPrefix) {
            for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue value = entry.getValue();

                if (value instanceof NDFValue.ArrayValue) {
                    NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
                    List<NDFValue> elements = array.getElements();

                    for (int i = 0; i < elements.size(); i++) {
                        if (elements.get(i) instanceof NDFValue.ObjectValue) {
                            NDFValue.ObjectValue nestedObj = (NDFValue.ObjectValue) elements.get(i);
                            String displayName = String.format("%s[%d]: %s",
                                pathPrefix + propertyName, i, nestedObj.getTypeName());
                            targetLocationCombo.addItem(displayName);
                        }
                    }
                } else if (value instanceof NDFValue.ObjectValue) {
                    NDFValue.ObjectValue nestedObj = (NDFValue.ObjectValue) value;
                    String displayName = String.format("%s: %s",
                        pathPrefix + propertyName, nestedObj.getTypeName());
                    targetLocationCombo.addItem(displayName);
                }
            }
        }

        private void populatePropertyTypes() {
            propertyTypeCombo.removeAllItems();
            propertyTypeCombo.addItem("String");
            propertyTypeCombo.addItem("Number");
            propertyTypeCombo.addItem("Boolean");
            propertyTypeCombo.addItem("Array");
            propertyTypeCombo.addItem("Enum");
            propertyTypeCombo.addItem("Template Reference");
            propertyTypeCombo.addItem("Resource Reference");
            propertyTypeCombo.addItem("GUID");
        }

        public void updateAvailableObjects(List<NDFValue.ObjectValue> objects) {
            targetObjectCombo.removeAllItems();
            for (NDFValue.ObjectValue obj : objects) {
                targetObjectCombo.addItem(obj);
            }
            updateTargetLocations();
        }

        public boolean executeOperation(AdditiveOperationManager manager) {
            NDFValue.ObjectValue targetObject = (NDFValue.ObjectValue) targetObjectCombo.getSelectedItem();
            String targetLocation = (String) targetLocationCombo.getSelectedItem();
            String propertyName = propertyNameField.getText().trim();
            String propertyType = (String) propertyTypeCombo.getSelectedItem();
            String propertyValueText = propertyValueArea.getText().trim();

            if (targetObject == null || targetLocation == null || propertyName.isEmpty() ||
                propertyType == null || propertyValueText.isEmpty()) {
                return false;
            }

            // Skip comment lines
            String[] lines = propertyValueText.split("\n");
            String actualValue = "";
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    actualValue = line;
                    break;
                }
            }

            if (actualValue.isEmpty()) {
                return false;
            }

            NDFValue propertyValue = parsePropertyValue(actualValue, propertyType);
            if (propertyValue == null) {
                return false;
            }

            // Determine target: object root, specific module, or nested object
            if (targetLocation.startsWith("Object Root")) {
                // Add property to the object itself
                return manager.addPropertyToObject(targetObject, propertyName, propertyValue, modificationTracker);
            } else if (targetLocation.startsWith("Module[")) {
                // Extract module index and add property to that module
                try {
                    int startIndex = targetLocation.indexOf("[") + 1;
                    int endIndex = targetLocation.indexOf("]");
                    int moduleIndex = Integer.parseInt(targetLocation.substring(startIndex, endIndex));

                    return addPropertyToModule(targetObject, moduleIndex, propertyName, propertyValue);
                } catch (Exception e) {
                    return false;
                }
            } else if (targetLocation.contains("[") && targetLocation.contains("]")) {
                // Handle nested array object like "SomeProperty[0]: SomeType"
                return addPropertyToNestedArrayObject(targetObject, targetLocation, propertyName, propertyValue);
            } else if (targetLocation.contains(":")) {
                // Handle nested object like "SomeProperty: SomeType"
                return addPropertyToNestedObject(targetObject, targetLocation, propertyName, propertyValue);
            }

            return false;
        }

        private boolean addPropertyToModule(NDFValue.ObjectValue targetObject, int moduleIndex,
                                          String propertyName, NDFValue propertyValue) {
            if (!targetObject.hasProperty("ModulesDescriptors")) {
                return false;
            }

            NDFValue modulesValue = targetObject.getProperty("ModulesDescriptors");
            if (!(modulesValue instanceof NDFValue.ArrayValue)) {
                return false;
            }

            NDFValue.ArrayValue modulesArray = (NDFValue.ArrayValue) modulesValue;
            List<NDFValue> modules = modulesArray.getElements();

            if (moduleIndex < 0 || moduleIndex >= modules.size()) {
                return false;
            }

            NDFValue moduleValue = modules.get(moduleIndex);
            if (!(moduleValue instanceof NDFValue.ObjectValue)) {
                return false;
            }

            NDFValue.ObjectValue module = (NDFValue.ObjectValue) moduleValue;
            module.setProperty(propertyName, propertyValue);
            module.setPropertyComma(propertyName, true); // Most properties have commas

            // Record the addition in modification tracker
            if (modificationTracker != null) {
                String objectName = targetObject.getInstanceName() != null ?
                    targetObject.getInstanceName() : "Unknown Object";
                String fullPropertyPath = "ModulesDescriptors[" + moduleIndex + "]." + propertyName;

                modificationTracker.recordModification(
                    objectName,
                    fullPropertyPath,
                    null, // No old value for new properties
                    propertyValue,
                    PropertyUpdater.ModificationType.PROPERTY_ADDED,
                    "Added property " + propertyName + " to module"
                );
            }

            return true;
        }

        private boolean addPropertyToNestedArrayObject(NDFValue.ObjectValue targetObject, String targetLocation,
                                                     String propertyName, NDFValue propertyValue) {
            try {
                // Parse "PropertyName[index]: TypeName" format
                int bracketStart = targetLocation.indexOf("[");
                int bracketEnd = targetLocation.indexOf("]");
                String arrayPropertyName = targetLocation.substring(0, bracketStart);
                int arrayIndex = Integer.parseInt(targetLocation.substring(bracketStart + 1, bracketEnd));

                NDFValue arrayValue = targetObject.getProperty(arrayPropertyName);
                if (!(arrayValue instanceof NDFValue.ArrayValue)) {
                    return false;
                }

                NDFValue.ArrayValue array = (NDFValue.ArrayValue) arrayValue;
                List<NDFValue> elements = array.getElements();

                if (arrayIndex < 0 || arrayIndex >= elements.size()) {
                    return false;
                }

                NDFValue element = elements.get(arrayIndex);
                if (!(element instanceof NDFValue.ObjectValue)) {
                    return false;
                }

                NDFValue.ObjectValue nestedObject = (NDFValue.ObjectValue) element;
                nestedObject.setProperty(propertyName, propertyValue);
                nestedObject.setPropertyComma(propertyName, true);

                // Record modification
                if (modificationTracker != null) {
                    String objectName = targetObject.getInstanceName() != null ?
                        targetObject.getInstanceName() : "Unknown Object";
                    String fullPropertyPath = arrayPropertyName + "[" + arrayIndex + "]." + propertyName;

                    modificationTracker.recordModification(
                        objectName,
                        fullPropertyPath,
                        null,
                        propertyValue,
                        PropertyUpdater.ModificationType.PROPERTY_ADDED,
                        "Added property " + propertyName + " to nested object"
                    );
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private boolean addPropertyToNestedObject(NDFValue.ObjectValue targetObject, String targetLocation,
                                                String propertyName, NDFValue propertyValue) {
            try {
                // Parse "PropertyName: TypeName" format
                String nestedPropertyName = targetLocation.substring(0, targetLocation.indexOf(":")).trim();

                NDFValue nestedValue = targetObject.getProperty(nestedPropertyName);
                if (!(nestedValue instanceof NDFValue.ObjectValue)) {
                    return false;
                }

                NDFValue.ObjectValue nestedObject = (NDFValue.ObjectValue) nestedValue;
                nestedObject.setProperty(propertyName, propertyValue);
                nestedObject.setPropertyComma(propertyName, true);

                // Record modification
                if (modificationTracker != null) {
                    String objectName = targetObject.getInstanceName() != null ?
                        targetObject.getInstanceName() : "Unknown Object";
                    String fullPropertyPath = nestedPropertyName + "." + propertyName;

                    modificationTracker.recordModification(
                        objectName,
                        fullPropertyPath,
                        null,
                        propertyValue,
                        PropertyUpdater.ModificationType.PROPERTY_ADDED,
                        "Added property " + propertyName + " to nested object"
                    );
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private NDFValue parsePropertyValue(String value, String type) {
            try {
                switch (type) {
                    case "String":
                        // Remove quotes if present
                        if ((value.startsWith("'") && value.endsWith("'")) ||
                            (value.startsWith("\"") && value.endsWith("\""))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return NDFValue.createString(value);

                    case "Number":
                        if (value.contains(".")) {
                            return NDFValue.createNumber(Double.parseDouble(value));
                        } else {
                            return NDFValue.createNumber(Integer.parseInt(value), true);
                        }

                    case "Boolean":
                        return NDFValue.createBoolean(Boolean.parseBoolean(value));

                    case "Array":
                        return parseArrayValue(value);

                    case "Enum":
                        return NDFValue.createEnum(value);

                    case "Template Reference":
                        return NDFValue.createTemplateRef(value);

                    case "Resource Reference":
                        return NDFValue.createResourceRef(value);

                    case "GUID":
                        return NDFValue.createGuid(value);

                    default:
                        return NDFValue.createString(value);
                }
            } catch (Exception e) {
                return null;
            }
        }

        private NDFValue parseArrayValue(String value) {
            // Simple array parsing - expects format like ['item1', 'item2'] or [1, 2, 3]
            if (!value.startsWith("[") || !value.endsWith("]")) {
                return null;
            }

            String content = value.substring(1, value.length() - 1).trim();
            if (content.isEmpty()) {
                return NDFValue.createArray();
            }

            NDFValue.ArrayValue array = NDFValue.createArray();
            String[] elements = content.split(",");

            for (int i = 0; i < elements.length; i++) {
                String element = elements[i].trim();

                // Try to determine element type and parse accordingly
                NDFValue elementValue;
                if ((element.startsWith("'") && element.endsWith("'")) ||
                    (element.startsWith("\"") && element.endsWith("\""))) {
                    // String element
                    elementValue = NDFValue.createString(element.substring(1, element.length() - 1));
                } else if (element.equalsIgnoreCase("true") || element.equalsIgnoreCase("false")) {
                    // Boolean element
                    elementValue = NDFValue.createBoolean(Boolean.parseBoolean(element));
                } else {
                    // Try number, fallback to string
                    try {
                        if (element.contains(".")) {
                            elementValue = NDFValue.createNumber(Double.parseDouble(element));
                        } else {
                            elementValue = NDFValue.createNumber(Integer.parseInt(element), true);
                        }
                    } catch (NumberFormatException e) {
                        elementValue = NDFValue.createString(element);
                    }
                }

                array.addElement(elementValue, i < elements.length - 1); // Add comma except for last element
            }

            return array;
        }
    }
    
    /**
     * Panel for adding elements to existing arrays
     */
    private class AddArrayElementPanel extends JPanel {
        private JComboBox<NDFValue.ObjectValue> targetObjectCombo;
        private JComboBox<String> targetLocationCombo;
        private JComboBox<String> arrayPropertyCombo;
        private JComboBox<String> elementTypeCombo;
        private JTextArea elementValueArea;

        public AddArrayElementPanel() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createTitledBorder("Add Element to Array"));

            JPanel formPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;

            // Target object selection
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("Target Object:"), gbc);

            targetObjectCombo = new JComboBox<>();
            targetObjectCombo.addActionListener(e -> updateTargetLocations());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(targetObjectCombo, gbc);

            // Target location (object root or module)
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Target Location:"), gbc);

            targetLocationCombo = new JComboBox<>();
            targetLocationCombo.addActionListener(e -> updateArrayProperties());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(targetLocationCombo, gbc);

            // Array property selection
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Array Property:"), gbc);

            arrayPropertyCombo = new JComboBox<>();
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(arrayPropertyCombo, gbc);

            // Element type
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Element Type:"), gbc);

            elementTypeCombo = new JComboBox<>();
            populateElementTypes();
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(elementTypeCombo, gbc);

            // Element value
            gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Element Value:"), gbc);

            elementValueArea = new JTextArea(6, 30);
            elementValueArea.setText("# Enter element value based on type:\n" +
                                   "# String: 'MyValue'\n" +
                                   "# Number: 42 or 3.14\n" +
                                   "# Boolean: True or False\n" +
                                   "# Object: TMyObject( Property1 = 'Value1' )\n" +
                                   "# Enum: ECoalition/Allied");
            JScrollPane scrollPane = new JScrollPane(elementValueArea);
            gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
            formPanel.add(scrollPane, gbc);

            add(formPanel, BorderLayout.CENTER);
        }

        private void updateTargetLocations() {
            targetLocationCombo.removeAllItems();

            NDFValue.ObjectValue selectedObject = (NDFValue.ObjectValue) targetObjectCombo.getSelectedItem();
            if (selectedObject == null) {
                return;
            }

            // Check if root object has arrays before adding it
            Set<String> rootArrays = new HashSet<>();
            findArraysInObject(selectedObject, rootArrays);
            if (!rootArrays.isEmpty()) {
                targetLocationCombo.addItem("Root Object");
            }

            // Add modules as locations only if they contain arrays
            if (selectedObject.hasProperty("ModulesDescriptors")) {
                NDFValue modulesValue = selectedObject.getProperty("ModulesDescriptors");
                if (modulesValue instanceof NDFValue.ArrayValue) {
                    NDFValue.ArrayValue modulesArray = (NDFValue.ArrayValue) modulesValue;
                    List<NDFValue> modules = modulesArray.getElements();

                    for (int i = 0; i < modules.size(); i++) {
                        NDFValue moduleValue = modules.get(i);
                        if (moduleValue instanceof NDFValue.ObjectValue) {
                            NDFValue.ObjectValue module = (NDFValue.ObjectValue) moduleValue;

                            // Check if this module has any arrays
                            Set<String> moduleArrays = new HashSet<>();
                            findArraysInObject(module, moduleArrays);

                            if (!moduleArrays.isEmpty()) {
                                String typeName = module.getTypeName();
                                if (typeName != null) {
                                    String cleanTypeName = typeName.replaceAll("^T", "").replaceAll("Descriptor$", "");
                                    String displayName = String.format("Module[%d]: %s", i, cleanTypeName);
                                    targetLocationCombo.addItem(displayName);
                                }
                            }
                        }
                    }
                }
            }

            // Show message if no locations with arrays found
            if (targetLocationCombo.getItemCount() == 0) {
                targetLocationCombo.addItem("No locations with arrays found");
            }

            // Auto-select first option and trigger array properties update
            if (targetLocationCombo.getItemCount() > 0) {
                targetLocationCombo.setSelectedIndex(0);
                updateArrayProperties();
            }
        }

        private void findAllArrayPaths(NDFValue.ObjectValue obj, String pathPrefix, Set<String> arrayPaths, int depth) {
            if (depth > 5) return; // Prevent infinite recursion

            for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue value = entry.getValue();
                String currentPath = pathPrefix.isEmpty() ? propertyName : pathPrefix + "." + propertyName;

                if (value instanceof NDFValue.ArrayValue) {
                    // This is an array - add it to our list
                    arrayPaths.add(currentPath);
                } else if (value instanceof NDFValue.ObjectValue) {
                    // Recursively search nested objects for arrays
                    NDFValue.ObjectValue nestedObj = (NDFValue.ObjectValue) value;
                    findAllArrayPaths(nestedObj, currentPath, arrayPaths, depth + 1);
                }
            }
        }

        private void populateElementTypes() {
            elementTypeCombo.removeAllItems();
            elementTypeCombo.addItem("String");
            elementTypeCombo.addItem("Number");
            elementTypeCombo.addItem("Boolean");
            elementTypeCombo.addItem("Object");
            elementTypeCombo.addItem("Enum");
            elementTypeCombo.addItem("Template Reference");
            elementTypeCombo.addItem("Resource Reference");
            elementTypeCombo.addItem("GUID");
        }

        private void updateArrayProperties() {
            arrayPropertyCombo.removeAllItems();

            String selectedLocation = (String) targetLocationCombo.getSelectedItem();
            NDFValue.ObjectValue selectedObject = (NDFValue.ObjectValue) targetObjectCombo.getSelectedItem();

            if (selectedLocation == null || selectedObject == null) {
                arrayPropertyCombo.addItem("No arrays available");
                return;
            }

            // Handle the case where no locations with arrays were found
            if (selectedLocation.equals("No locations with arrays found")) {
                arrayPropertyCombo.addItem("No arrays available");
                return;
            }

            Set<String> arrayProperties = new HashSet<>();

            if (selectedLocation.equals("Root Object")) {
                // Find arrays in the root object
                findArraysInObject(selectedObject, arrayProperties);
            } else if (selectedLocation.startsWith("Module[")) {
                // Extract module index and find arrays in that module
                try {
                    int startIndex = selectedLocation.indexOf("[") + 1;
                    int endIndex = selectedLocation.indexOf("]");
                    int moduleIndex = Integer.parseInt(selectedLocation.substring(startIndex, endIndex));

                    NDFValue modulesValue = selectedObject.getProperty("ModulesDescriptors");
                    if (modulesValue instanceof NDFValue.ArrayValue) {
                        NDFValue.ArrayValue modulesArray = (NDFValue.ArrayValue) modulesValue;
                        if (moduleIndex < modulesArray.getElements().size()) {
                            NDFValue moduleValue = modulesArray.getElements().get(moduleIndex);
                            if (moduleValue instanceof NDFValue.ObjectValue) {
                                NDFValue.ObjectValue module = (NDFValue.ObjectValue) moduleValue;
                                findArraysInObject(module, arrayProperties);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // Invalid module index format
                    arrayPropertyCombo.addItem("Invalid location format");
                    return;
                }
            }

            // Since we pre-filtered locations to only include those with arrays,
            // we should always find arrays here. But add a safety check just in case.
            if (arrayProperties.isEmpty()) {
                arrayPropertyCombo.addItem("No arrays found (unexpected)");
            } else {
                // Sort and add array properties
                arrayProperties.stream().sorted().forEach(arrayPropertyCombo::addItem);
            }
        }

        private void findArraysInObject(NDFValue.ObjectValue obj, Set<String> arrayProperties) {
            findArraysInObjectRecursive(obj, "", arrayProperties, 0);
        }

        private void findArraysInObjectRecursive(NDFValue.ObjectValue obj, String pathPrefix, Set<String> arrayProperties, int depth) {
            if (depth > 5) return; // Prevent infinite recursion

            for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue value = entry.getValue();
                String currentPath = pathPrefix.isEmpty() ? propertyName : pathPrefix + "." + propertyName;

                if (value instanceof NDFValue.ArrayValue) {
                    // Found an array - add it to our collection
                    arrayProperties.add(currentPath);
                } else if (value instanceof NDFValue.ObjectValue) {
                    // Recursively search nested objects for arrays
                    NDFValue.ObjectValue nestedObj = (NDFValue.ObjectValue) value;
                    findArraysInObjectRecursive(nestedObj, currentPath, arrayProperties, depth + 1);
                }
            }
        }

        public void updateAvailableObjects(List<NDFValue.ObjectValue> objects) {
            targetObjectCombo.removeAllItems();
            for (NDFValue.ObjectValue obj : objects) {
                targetObjectCombo.addItem(obj);
            }

            // Trigger the cascade of updates
            if (targetObjectCombo.getItemCount() > 0) {
                targetObjectCombo.setSelectedIndex(0);
                updateTargetLocations();
            }
        }

        public boolean executeOperation(AdditiveOperationManager manager) {
            NDFValue.ObjectValue targetObject = (NDFValue.ObjectValue) targetObjectCombo.getSelectedItem();
            String selectedLocation = (String) targetLocationCombo.getSelectedItem();
            String arrayProperty = (String) arrayPropertyCombo.getSelectedItem();
            String elementType = (String) elementTypeCombo.getSelectedItem();
            String elementValueText = elementValueArea.getText().trim();

            if (targetObject == null || selectedLocation == null || arrayProperty == null ||
                elementType == null || elementValueText.isEmpty()) {
                return false;
            }

            // Check if no array properties were found
            if (arrayProperty.contains("No arrays found") || arrayProperty.contains("No arrays available") ||
                arrayProperty.contains("Invalid location format") || selectedLocation.contains("No locations with arrays found")) {
                return false;
            }

            // Skip comment lines
            String[] lines = elementValueText.split("\n");
            String actualValue = "";
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    actualValue = line;
                    break;
                }
            }

            if (actualValue.isEmpty()) {
                return false;
            }

            NDFValue elementValue = parseElementValue(actualValue, elementType);
            if (elementValue == null) {
                return false;
            }

            // Determine the full path to the array based on location and property
            String fullArrayPath;
            if (selectedLocation.equals("Root Object")) {
                // For root object, the array property already contains the full path
                fullArrayPath = arrayProperty;
            } else if (selectedLocation.startsWith("Module[")) {
                // Extract module index and combine with array property path
                try {
                    int startIndex = selectedLocation.indexOf("[") + 1;
                    int endIndex = selectedLocation.indexOf("]");
                    int moduleIndex = Integer.parseInt(selectedLocation.substring(startIndex, endIndex));
                    fullArrayPath = "ModulesDescriptors[" + moduleIndex + "]." + arrayProperty;
                } catch (NumberFormatException e) {
                    return false;
                }
            } else {
                fullArrayPath = arrayProperty;
            }

            return manager.addElementToArray(targetObject, fullArrayPath, elementValue, modificationTracker, fileType);
        }

        private NDFValue parseElementValue(String value, String type) {
            try {
                switch (type) {
                    case "String":
                        // Remove quotes if present
                        if ((value.startsWith("'") && value.endsWith("'")) ||
                            (value.startsWith("\"") && value.endsWith("\""))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return NDFValue.createString(value);

                    case "Number":
                        if (value.contains(".")) {
                            return NDFValue.createNumber(Double.parseDouble(value));
                        } else {
                            return NDFValue.createNumber(Integer.parseInt(value), true);
                        }

                    case "Boolean":
                        return NDFValue.createBoolean(Boolean.parseBoolean(value));

                    case "Object":
                        return parseObjectValue(value);

                    case "Enum":
                        return NDFValue.createEnum(value);

                    case "Template Reference":
                        return NDFValue.createTemplateRef(value);

                    case "Resource Reference":
                        return NDFValue.createResourceRef(value);

                    case "GUID":
                        return NDFValue.createGuid(value);

                    default:
                        return NDFValue.createString(value);
                }
            } catch (Exception e) {
                return null;
            }
        }

        private NDFValue parseObjectValue(String value) {
            // Simple object parsing - expects format like TMyObject( Property1 = 'Value1', Property2 = 42 )
            if (!value.contains("(") || !value.endsWith(")")) {
                return null;
            }

            int parenIndex = value.indexOf("(");
            String typeName = value.substring(0, parenIndex).trim();
            String content = value.substring(parenIndex + 1, value.length() - 1).trim();

            NDFValue.ObjectValue object = NDFValue.createObject(typeName);

            if (!content.isEmpty()) {
                // Parse properties - simple comma-separated key=value pairs
                String[] properties = content.split(",");
                for (String property : properties) {
                    String[] parts = property.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String val = parts[1].trim();

                        // Try to parse the value
                        NDFValue propertyValue;
                        if ((val.startsWith("'") && val.endsWith("'")) ||
                            (val.startsWith("\"") && val.endsWith("\""))) {
                            propertyValue = NDFValue.createString(val.substring(1, val.length() - 1));
                        } else if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) {
                            propertyValue = NDFValue.createBoolean(Boolean.parseBoolean(val));
                        } else {
                            try {
                                if (val.contains(".")) {
                                    propertyValue = NDFValue.createNumber(Double.parseDouble(val));
                                } else {
                                    propertyValue = NDFValue.createNumber(Integer.parseInt(val), true);
                                }
                            } catch (NumberFormatException e) {
                                propertyValue = NDFValue.createString(val);
                            }
                        }

                        object.setProperty(key, propertyValue);
                    }
                }
            }

            return object;
        }
    }
}
