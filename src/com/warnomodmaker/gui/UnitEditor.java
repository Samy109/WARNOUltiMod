package com.warnomodmaker.gui;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.*;
import com.warnomodmaker.model.PropertyUpdater;
import com.warnomodmaker.model.ModificationTracker;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Component for editing unit properties.
 */
public class UnitEditor extends JPanel {
    private ObjectValue unitDescriptor;
    private ModificationTracker modificationTracker;
    private PropertyChangeSupport propertyChangeSupport;

    // GUI components
    private JSplitPane splitPane;
    private JTree propertyTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JPanel editorPanel;
    private JTextField valueField;
    private JButton applyButton;

    // Currently selected property
    private String selectedPath;
    private NDFValue selectedValue;
    private ObjectValue selectedParentObject;
    private String selectedPropertyName;

    // Tree state management
    private TreePath currentSelectedPath;
    private boolean suppressSelectionEvents = false;

    /**
     * Creates a new unit editor
     */
    public UnitEditor() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Unit Properties"));

        // Initialize state
        unitDescriptor = null;
        propertyChangeSupport = new PropertyChangeSupport(this);
        selectedPath = null;
        selectedValue = null;

        // Create the split pane
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(300);

        // Create the property tree
        rootNode = new DefaultMutableTreeNode("Unit");
        treeModel = new DefaultTreeModel(rootNode);
        propertyTree = new JTree(treeModel);
        propertyTree.setRootVisible(false);
        propertyTree.setShowsRootHandles(true);
        propertyTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                TreePath path = e.getPath();
                if (path != null) {
                    handlePropertySelection(path);
                }
            }
        });

        JScrollPane treeScrollPane = new JScrollPane(propertyTree);
        splitPane.setLeftComponent(treeScrollPane);

        // Create the editor panel
        editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel valuePanel = new JPanel(new BorderLayout());
        valuePanel.setBorder(BorderFactory.createTitledBorder("Value"));

        valueField = new JTextField();
        valuePanel.add(valueField, BorderLayout.CENTER);

        applyButton = new JButton("Apply");
        applyButton.addActionListener(this::applyValue);
        applyButton.setEnabled(false);
        valuePanel.add(applyButton, BorderLayout.EAST);

        editorPanel.add(valuePanel, BorderLayout.NORTH);

        splitPane.setRightComponent(editorPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    /**
     * Sets the unit descriptor to edit
     *
     * @param unitDescriptor The unit descriptor
     */
    public void setUnitDescriptor(ObjectValue unitDescriptor) {
        setUnitDescriptor(unitDescriptor, null);
    }

    /**
     * Sets the unit descriptor to edit with modification tracking
     *
     * @param unitDescriptor The unit descriptor
     * @param modificationTracker The modification tracker (can be null)
     */
    public void setUnitDescriptor(ObjectValue unitDescriptor, ModificationTracker modificationTracker) {
        this.unitDescriptor = unitDescriptor;
        this.modificationTracker = modificationTracker;
        updatePropertyTree();
        clearEditor();
    }

    /**
     * Adds a property change listener
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Refreshes the editor
     */
    public void refresh() {
        refreshWithStatePreservation();
    }

    /**
     * Refreshes the tree while preserving expansion state and selection
     */
    private void refreshWithStatePreservation() {
        // Save current state
        String savedPath = selectedPath;
        TreePath savedTreePath = currentSelectedPath;
        Set<String> expandedPaths = getExpandedPaths();

        // Update the tree
        updatePropertyTree();

        // Restore expanded paths
        restoreExpandedPaths(expandedPaths);

        // Restore selection and update editor with new value
        if (savedPath != null) {
            restoreSelectionAndUpdateEditor(savedPath);
        }
    }

    /**
     * Gets all currently expanded paths in the tree
     */
    private Set<String> getExpandedPaths() {
        Set<String> expandedPaths = new HashSet<>();

        for (int i = 0; i < propertyTree.getRowCount(); i++) {
            TreePath path = propertyTree.getPathForRow(i);
            if (propertyTree.isExpanded(path)) {
                String pathString = getPropertyPath(path);
                if (!pathString.isEmpty()) {
                    expandedPaths.add(pathString);
                }
            }
        }

        return expandedPaths;
    }

    /**
     * Restores expanded paths in the tree
     */
    private void restoreExpandedPaths(Set<String> expandedPaths) {
        for (int i = 0; i < propertyTree.getRowCount(); i++) {
            TreePath path = propertyTree.getPathForRow(i);
            String pathString = getPropertyPath(path);

            if (expandedPaths.contains(pathString)) {
                propertyTree.expandPath(path);
            }
        }
    }

    /**
     * Restores the selection and updates the editor with the current value
     */
    private void restoreSelectionAndUpdateEditor(String propertyPath) {
        TreePath newPath = findTreePathByPropertyPath(propertyPath);

        if (newPath != null) {
            // Suppress selection events while we restore the selection
            suppressSelectionEvents = true;

            // Set the selection
            propertyTree.setSelectionPath(newPath);

            // Update our state variables manually
            currentSelectedPath = newPath;
            selectedPath = propertyPath;

            // Update the editor with the current (updated) value
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) newPath.getLastPathComponent();
            if (node != null && node.getUserObject() instanceof PropertyNode) {
                PropertyNode propertyNode = (PropertyNode) node.getUserObject();

                // Update selectedValue to the new value and other state
                selectedValue = propertyNode.getValue();
                selectedPropertyName = propertyNode.getName();
                selectedParentObject = findParentObject(newPath);

                // Update the editor field with the new value (this is key!)
                valueField.setText(selectedValue != null ? selectedValue.toString() : "");

                // Re-enable the apply button if the value is editable
                boolean editable = isEditableType(selectedValue);
                applyButton.setEnabled(editable);
            }

            // Re-enable selection events
            suppressSelectionEvents = false;
        }
    }

    /**
     * Finds a tree path by property path string
     */
    private TreePath findTreePathByPropertyPath(String propertyPath) {
        if (propertyPath == null || propertyPath.isEmpty()) {
            return null;
        }

        // Search through all nodes to find the matching path
        return findNodeByPath(rootNode, propertyPath, new TreePath(rootNode));
    }

    /**
     * Recursively searches for a node with the given property path
     */
    private TreePath findNodeByPath(DefaultMutableTreeNode node, String targetPath, TreePath currentPath) {
        // Check if this node matches the target path
        String currentPathString = getPropertyPath(currentPath);
        if (targetPath.equals(currentPathString)) {
            return currentPath;
        }

        // Search children
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            TreePath childPath = currentPath.pathByAddingChild(child);

            TreePath result = findNodeByPath(child, targetPath, childPath);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Updates the property tree
     */
    private void updatePropertyTree() {
        rootNode.removeAllChildren();

        if (unitDescriptor != null) {
            // Add unit type
            DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(
                new PropertyNode("Type", unitDescriptor.getTypeName())
            );
            rootNode.add(typeNode);

            // Add properties
            for (Map.Entry<String, NDFValue> entry : unitDescriptor.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue propertyValue = entry.getValue();

                DefaultMutableTreeNode propertyNode = createPropertyNode(propertyName, propertyValue);
                rootNode.add(propertyNode);
            }
        }

        treeModel.reload();

        // Expand the root node
        propertyTree.expandPath(new TreePath(rootNode.getPath()));
    }

    /**
     * Creates a tree node for a property
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return The tree node
     */
    private DefaultMutableTreeNode createPropertyNode(String propertyName, NDFValue propertyValue) {
        PropertyNode node = new PropertyNode(propertyName, propertyValue);
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);

        // Add child nodes for complex types
        switch (propertyValue.getType()) {
            case OBJECT:
                ObjectValue objectValue = (ObjectValue) propertyValue;

                for (Map.Entry<String, NDFValue> entry : objectValue.getProperties().entrySet()) {
                    String childName = entry.getKey();
                    NDFValue childValue = entry.getValue();

                    DefaultMutableTreeNode childNode = createPropertyNode(childName, childValue);
                    treeNode.add(childNode);
                }
                break;

            case ARRAY:
                ArrayValue arrayValue = (ArrayValue) propertyValue;

                for (int i = 0; i < arrayValue.getElements().size(); i++) {
                    NDFValue element = arrayValue.getElements().get(i);
                    String childName = "[" + i + "]";

                    DefaultMutableTreeNode childNode = createPropertyNode(childName, element);
                    treeNode.add(childNode);
                }
                break;

            case MAP:
                MapValue mapValue = (MapValue) propertyValue;

                for (int i = 0; i < mapValue.getEntries().size(); i++) {
                    Map.Entry<NDFValue, NDFValue> entry = mapValue.getEntries().get(i);
                    String childName = "(" + entry.getKey() + ")";

                    DefaultMutableTreeNode childNode = createPropertyNode(childName, entry.getValue());
                    treeNode.add(childNode);
                }
                break;
        }

        return treeNode;
    }

    /**
     * Handles property selection in the tree
     *
     * @param path The selected path
     */
    private void handlePropertySelection(TreePath path) {
        // Skip if we're suppressing selection events (during refresh)
        if (suppressSelectionEvents) {
            return;
        }

        // Handle null path (happens during tree clearing/rebuilding)
        if (path == null) {
            clearEditor();
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

        if (node != null && node.getUserObject() instanceof PropertyNode) {
            PropertyNode propertyNode = (PropertyNode) node.getUserObject();

            selectedPath = getPropertyPath(path);
            selectedValue = propertyNode.getValue();
            selectedPropertyName = propertyNode.getName();
            currentSelectedPath = path; // Save the current tree path

            // Find the parent object that contains this property
            selectedParentObject = findParentObject(path);

            // Debug output
            System.out.println("Property selected:");
            System.out.println("  Path: " + selectedPath);
            System.out.println("  Name: " + selectedPropertyName);
            System.out.println("  Value: " + selectedValue);
            System.out.println("  Parent: " + (selectedParentObject != null ? "found" : "null"));

            updateEditor(propertyNode);
        } else {
            clearEditor();
        }
    }

    /**
     * Gets the property path for a tree path
     *
     * @param treePath The tree path
     * @return The property path
     */
    private String getPropertyPath(TreePath treePath) {
        StringBuilder path = new StringBuilder();

        Object[] nodes = treePath.getPath();

        // Skip the root node
        for (int i = 1; i < nodes.length; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes[i];

            if (node.getUserObject() instanceof PropertyNode) {
                PropertyNode propertyNode = (PropertyNode) node.getUserObject();
                String nodeName = propertyNode.getName();

                // Handle array indices - they should be attached to the previous property name
                if (nodeName.startsWith("[") && nodeName.endsWith("]")) {
                    // This is an array index, append it directly to the path without a dot
                    path.append(nodeName);
                } else {
                    // Regular property name
                    if (path.length() > 0) {
                        path.append(".");
                    }
                    path.append(nodeName);
                }
            }
        }

        return path.toString();
    }

    /**
     * Finds the parent object that contains the selected property
     *
     * @param treePath The tree path to the selected property
     * @return The parent ObjectValue that contains this property
     */
    private ObjectValue findParentObject(TreePath treePath) {
        Object[] nodes = treePath.getPath();

        // Debug output
        System.out.println("Finding parent object for path with " + nodes.length + " nodes");
        for (int i = 0; i < nodes.length; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes[i];
            if (node.getUserObject() instanceof PropertyNode) {
                PropertyNode pn = (PropertyNode) node.getUserObject();
                System.out.println("  Node " + i + ": " + pn.getName());
            } else {
                System.out.println("  Node " + i + ": " + node.getUserObject());
            }
        }

        // If we're at the root level (direct property of unitDescriptor)
        if (nodes.length == 2) {
            System.out.println("  Returning unitDescriptor as parent (root level)");
            return unitDescriptor;
        }

        // Navigate through the object hierarchy to find the parent
        ObjectValue currentObject = unitDescriptor;

        // Skip root node and go to the parent of the selected node
        for (int i = 1; i < nodes.length - 1; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes[i];

            if (node.getUserObject() instanceof PropertyNode) {
                PropertyNode propertyNode = (PropertyNode) node.getUserObject();
                String propertyName = propertyNode.getName();

                System.out.println("  Processing node " + i + ": " + propertyName);

                // Skip "Type" node
                if ("Type".equals(propertyName)) {
                    System.out.println("    Skipping Type node");
                    continue;
                }

                NDFValue value = currentObject.getProperty(propertyName);
                System.out.println("    Property value type: " + (value != null ? value.getClass().getSimpleName() : "null"));

                if (value instanceof ObjectValue) {
                    currentObject = (ObjectValue) value;
                    System.out.println("    Updated current object");
                } else if (value instanceof ArrayValue) {
                    // Handle array case - we need to get the specific array element
                    System.out.println("    Found array, need to get array element");
                    ArrayValue arrayValue = (ArrayValue) value;

                    // The next node should be the array index like "[19]"
                    if (i + 1 < nodes.length - 1) {
                        DefaultMutableTreeNode nextNode = (DefaultMutableTreeNode) nodes[i + 1];
                        if (nextNode.getUserObject() instanceof PropertyNode) {
                            PropertyNode nextPropertyNode = (PropertyNode) nextNode.getUserObject();
                            String indexStr = nextPropertyNode.getName();

                            // Parse array index from "[19]" format
                            if (indexStr.startsWith("[") && indexStr.endsWith("]")) {
                                try {
                                    int index = Integer.parseInt(indexStr.substring(1, indexStr.length() - 1));
                                    System.out.println("    Getting array element at index: " + index);

                                    NDFValue arrayElement = arrayValue.getElements().get(index);
                                    if (arrayElement instanceof ObjectValue) {
                                        currentObject = (ObjectValue) arrayElement;
                                        System.out.println("    Got array element as ObjectValue");
                                        i++; // Skip the index node since we processed it
                                    } else {
                                        System.out.println("    Array element is not an ObjectValue");
                                        return null;
                                    }
                                } catch (NumberFormatException | IndexOutOfBoundsException ex) {
                                    System.out.println("    Failed to parse array index: " + ex.getMessage());
                                    return null;
                                }
                            } else {
                                System.out.println("    Next node is not an array index: " + indexStr);
                                return null;
                            }
                        }
                    } else {
                        System.out.println("    No next node for array index");
                        return null;
                    }
                } else {
                    System.out.println("    Value is not an ObjectValue, returning null");
                    return null;
                }
            }
        }

        System.out.println("  Returning current object as parent");
        return currentObject;
    }

    /**
     * Updates the editor with the selected property
     *
     * @param propertyNode The selected property node
     */
    private void updateEditor(PropertyNode propertyNode) {
        NDFValue value = propertyNode.getValue();

        // Only allow editing of simple types
        boolean editable = isEditableType(value);

        valueField.setText(value != null ? value.toString() : "");
        valueField.setEditable(editable);
        applyButton.setEnabled(editable);
    }

    /**
     * Clears the editor
     */
    private void clearEditor() {
        selectedPath = null;
        selectedValue = null;
        selectedParentObject = null;
        selectedPropertyName = null;

        valueField.setText("");
        valueField.setEditable(false);
        applyButton.setEnabled(false);
    }


    /**
     * Checks if a value type is editable
     *
     * @param value The value to check
     * @return True if the value is editable, false otherwise
     */
    private boolean isEditableType(NDFValue value) {
        if (value == null) {
            return false;
        }

        switch (value.getType()) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
            case TEMPLATE_REF:
            case RESOURCE_REF:
            case GUID:
            case ENUM:
                return true;

            default:
                return false;
        }
    }



    /**
     * Applies the edited value
     */
    private void applyValue(ActionEvent e) {
        // Debug output
        System.out.println("Apply button clicked:");
        System.out.println("  selectedPath: " + selectedPath);
        System.out.println("  selectedValue: " + selectedValue);
        System.out.println("  unitDescriptor: " + (unitDescriptor != null ? "not null" : "null"));

        if (selectedPath == null || selectedValue == null || unitDescriptor == null) {
            String message = "No property selected for editing\n";
            message += "selectedPath: " + (selectedPath == null ? "null" : selectedPath) + "\n";
            message += "selectedValue: " + (selectedValue == null ? "null" : selectedValue.toString()) + "\n";
            message += "unitDescriptor: " + (unitDescriptor == null ? "null" : "not null");

            JOptionPane.showMessageDialog(
                this,
                message,
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String newValueText = valueField.getText();

        try {
            // Parse the new value based on the type of the selected value
            NDFValue newValue = parseValue(newValueText, selectedValue);

            // Use PropertyUpdater for consistency with mass changes
            String actualPath = selectedPath;
            if (selectedPath.startsWith("Type.")) {
                actualPath = selectedPath.substring(5); // Remove "Type." prefix
            }

            System.out.println("Using PropertyUpdater for individual change:");
            System.out.println("  actualPath: " + actualPath);
            System.out.println("  oldValue: " + selectedValue);
            System.out.println("  newValue: " + newValue);

            // Use PropertyUpdater.updateProperty with tracking - same as mass changes!
            boolean success = PropertyUpdater.updateProperty(unitDescriptor, actualPath, newValue, modificationTracker);

            if (!success) {
                throw new IllegalArgumentException("Failed to update property at path: " + actualPath);
            }

            // Notify listeners
            propertyChangeSupport.firePropertyChange("unitModified", null, unitDescriptor);

            // Refresh the tree
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error applying value: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Parses a value from text based on the type of the original value
     *
     * @param text The text to parse
     * @param originalValue The original value
     * @return The parsed value
     */
    private NDFValue parseValue(String text, NDFValue originalValue) {
        switch (originalValue.getType()) {
            case STRING:
                // Remove quotes if present
                if (text.startsWith("'") && text.endsWith("'")) {
                    text = text.substring(1, text.length() - 1);
                }
                return NDFValue.createString(text);

            case NUMBER:
                try {
                    return NDFValue.createNumber(Double.parseDouble(text));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number format");
                }

            case BOOLEAN:
                if (text.equalsIgnoreCase("True") || text.equalsIgnoreCase("False")) {
                    return NDFValue.createBoolean(text.equalsIgnoreCase("True"));
                } else {
                    throw new IllegalArgumentException("Boolean value must be 'True' or 'False'");
                }

            case TEMPLATE_REF:
                if (!text.startsWith("~/")) {
                    throw new IllegalArgumentException("Template reference must start with '~/'");
                }
                return NDFValue.createTemplateRef(text);

            case RESOURCE_REF:
                if (!text.startsWith("$/")) {
                    throw new IllegalArgumentException("Resource reference must start with '$/'");
                }
                return NDFValue.createResourceRef(text);

            case GUID:
                if (!text.startsWith("GUID:{") || !text.endsWith("}")) {
                    throw new IllegalArgumentException("GUID must be in the format 'GUID:{...}'");
                }
                return NDFValue.createGUID(text);

            case ENUM:
                String[] parts = text.split("/");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Enum must be in the format 'Type/Value'");
                }
                return NDFValue.createEnum(parts[0], parts[1]);

            default:
                throw new IllegalArgumentException("Cannot edit this type of value");
        }
    }

    /**
     * Updates a value in the unit descriptor using the shared PropertyUpdater
     *
     * @param path The path to the value
     * @param newValue The new value
     */
    private void updateValueInUnitDescriptor(String path, NDFValue newValue) {
        // Skip the "Type" node if present
        String actualPath = path;
        if (path.startsWith("Type.")) {
            actualPath = path.substring(5); // Remove "Type." prefix
        }

        // Use the shared PropertyUpdater for consistency with mass updates
        // Note: Don't pass modificationTracker here as we already recorded it in applyValue()
        boolean success = PropertyUpdater.updateProperty(unitDescriptor, actualPath, newValue, null);

        if (!success) {
            throw new IllegalArgumentException("Failed to update property at path: " + path);
        }
    }

    /**
     * Selects a property in the tree by its path
     *
     * @param path The property path
     */
    private void selectPropertyByPath(String path) {
        // Not implemented yet
    }

    /**
     * Node for the property tree
     */
    private static class PropertyNode {
        private final String name;
        private final NDFValue value;

        public PropertyNode(String name, NDFValue value) {
            this.name = name;
            this.value = value;
        }

        public PropertyNode(String name, String value) {
            this.name = name;
            this.value = NDFValue.createString(value);
        }

        public String getName() {
            return name;
        }

        public NDFValue getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name + ": " + (value != null ? value.toString() : "null");
        }
    }
}
