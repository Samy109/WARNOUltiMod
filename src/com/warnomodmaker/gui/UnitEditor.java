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

public class UnitEditor extends JPanel {
    private ObjectValue ndfObject;
    private ModificationTracker modificationTracker;
    private PropertyChangeSupport propertyChangeSupport;

    private JSplitPane splitPane;
    private JTree propertyTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JPanel editorPanel;
    private JTextField valueField;
    private JButton applyButton;

    private String selectedPath;
    private NDFValue selectedValue;
    private ObjectValue selectedParentObject;
    private String selectedPropertyName;

    private TreePath currentSelectedPath;
    private boolean suppressSelectionEvents = false;


    public UnitEditor() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Object Properties"));
        ndfObject = null;
        propertyChangeSupport = new PropertyChangeSupport(this);
        selectedPath = null;
        selectedValue = null;
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(300);
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


    public void setUnitDescriptor(ObjectValue ndfObject) {
        setUnitDescriptor(ndfObject, null);
    }


    public void setUnitDescriptor(ObjectValue ndfObject, ModificationTracker modificationTracker) {
        this.ndfObject = ndfObject;
        this.modificationTracker = modificationTracker;
        updatePropertyTree();
        clearEditor();
        updateBorderTitle();
    }


    private void updateBorderTitle() {
        String title = "Object Properties";
        if (ndfObject != null && ndfObject.getTypeName() != null) {
            String typeName = ndfObject.getTypeName();
            // Make the title more user-friendly
            if (typeName.startsWith("T") && typeName.length() > 1) {
                typeName = typeName.substring(1);
            }
            title = typeName + " Properties";
        }
        setBorder(BorderFactory.createTitledBorder(title));
    }


    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }


    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }


    public void refresh() {
        refreshWithStatePreservation();
    }


    private void refreshWithStatePreservation() {
        String savedPath = selectedPath;
        TreePath savedTreePath = currentSelectedPath;
        Set<String> expandedPaths = getExpandedPaths();
        updatePropertyTree();

        // Restore expanded paths
        restoreExpandedPaths(expandedPaths);

        // Restore selection and update editor with new value
        if (savedPath != null) {
            restoreSelectionAndUpdateEditor(savedPath);
        }
    }


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


    private void restoreExpandedPaths(Set<String> expandedPaths) {
        for (int i = 0; i < propertyTree.getRowCount(); i++) {
            TreePath path = propertyTree.getPathForRow(i);
            String pathString = getPropertyPath(path);

            if (expandedPaths.contains(pathString)) {
                propertyTree.expandPath(path);
            }
        }
    }


    private void restoreSelectionAndUpdateEditor(String propertyPath) {
        TreePath newPath = findTreePathByPropertyPath(propertyPath);

        if (newPath != null) {
            // Suppress selection events while we restore the selection
            suppressSelectionEvents = true;
            propertyTree.setSelectionPath(newPath);
            currentSelectedPath = newPath;
            selectedPath = propertyPath;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) newPath.getLastPathComponent();
            if (node != null && node.getUserObject() instanceof PropertyNode) {
                PropertyNode propertyNode = (PropertyNode) node.getUserObject();
                selectedValue = propertyNode.getValue();
                selectedPropertyName = propertyNode.getName();
                selectedParentObject = findParentObject(newPath);
                valueField.setText(selectedValue != null ? selectedValue.toString() : "");

                // Re-enable the apply button if the value is editable
                boolean editable = isEditableType(selectedValue);
                applyButton.setEnabled(editable);
            }

            // Re-enable selection events
            suppressSelectionEvents = false;
        }
    }


    private TreePath findTreePathByPropertyPath(String propertyPath) {
        if (propertyPath == null || propertyPath.isEmpty()) {
            return null;
        }

        // Search through all nodes to find the matching path
        return findNodeByPath(rootNode, propertyPath, new TreePath(rootNode));
    }


    private TreePath findNodeByPath(DefaultMutableTreeNode node, String targetPath, TreePath currentPath) {
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


    private void updatePropertyTree() {
        rootNode.removeAllChildren();

        if (ndfObject != null) {
            DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(
                new PropertyNode("Type", ndfObject.getTypeName())
            );
            rootNode.add(typeNode);
            for (Map.Entry<String, NDFValue> entry : ndfObject.getProperties().entrySet()) {
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


    private DefaultMutableTreeNode createPropertyNode(String propertyName, NDFValue propertyValue) {
        PropertyNode node = new PropertyNode(propertyName, propertyValue);
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
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

            case TUPLE:
                // CRITICAL FIX: Handle tuples like (EBaseHitValueModifier,Moving, 7)
                TupleValue tupleValue = (TupleValue) propertyValue;

                for (int i = 0; i < tupleValue.getElements().size(); i++) {
                    NDFValue element = tupleValue.getElements().get(i);
                    String childName = "[" + i + "]";

                    DefaultMutableTreeNode childNode = createPropertyNode(childName, element);
                    treeNode.add(childNode);
                }
                break;
        }

        return treeNode;
    }


    private void handlePropertySelection(TreePath path) {
        // Skip if we're suppressing selection events (during refresh)
        if (suppressSelectionEvents) {
            return;
        }
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
            currentSelectedPath = path;
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


    private String getPropertyPath(TreePath treePath) {
        StringBuilder path = new StringBuilder();

        Object[] nodes = treePath.getPath();

        // Skip the root node
        for (int i = 1; i < nodes.length; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes[i];

            if (node.getUserObject() instanceof PropertyNode) {
                PropertyNode propertyNode = (PropertyNode) node.getUserObject();
                String nodeName = propertyNode.getName();
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
            return ndfObject;
        }

        // Navigate through the object hierarchy to find the parent
        ObjectValue currentObject = ndfObject;

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
                    System.out.println("    Found array, need to get array element");
                    ArrayValue arrayValue = (ArrayValue) value;

                    // The next node should be the array index like "[19]"
                    if (i + 1 < nodes.length - 1) {
                        DefaultMutableTreeNode nextNode = (DefaultMutableTreeNode) nodes[i + 1];
                        if (nextNode.getUserObject() instanceof PropertyNode) {
                            PropertyNode nextPropertyNode = (PropertyNode) nextNode.getUserObject();
                            String indexStr = nextPropertyNode.getName();
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


    private void updateEditor(PropertyNode propertyNode) {
        NDFValue value = propertyNode.getValue();

        // Only allow editing of simple types
        boolean editable = isEditableType(value);

        valueField.setText(value != null ? value.toString() : "");
        valueField.setEditable(editable);
        applyButton.setEnabled(editable);
    }


    private void clearEditor() {
        selectedPath = null;
        selectedValue = null;
        selectedParentObject = null;
        selectedPropertyName = null;

        valueField.setText("");
        valueField.setEditable(false);
        applyButton.setEnabled(false);
    }


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
            case TUPLE:  // FIXED: Tuples like (EBaseHitValueModifier,Moving, 7) are now editable
                return true;

            case ARRAY:
                // Arrays themselves are not directly editable, but their elements might be
                return false;

            case OBJECT:
                // Objects themselves are not directly editable, but their properties might be
                return false;

            case MAP:
                // Maps themselves are not directly editable, but their entries might be
                return false;

            default:
                return false;
        }
    }


    private void applyValue(ActionEvent e) {
        // Debug output
        System.out.println("Apply button clicked:");
        System.out.println("  selectedPath: " + selectedPath);
        System.out.println("  selectedValue: " + selectedValue);
        System.out.println("  ndfObject: " + (ndfObject != null ? "not null" : "null"));

        if (selectedPath == null || selectedValue == null || ndfObject == null) {
            String message = "No property selected for editing\n";
            message += "selectedPath: " + (selectedPath == null ? "null" : selectedPath) + "\n";
            message += "selectedValue: " + (selectedValue == null ? "null" : selectedValue.toString()) + "\n";
            message += "ndfObject: " + (ndfObject == null ? "null" : "not null");

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
            NDFValue newValue = parseValue(newValueText, selectedValue);
            String actualPath = selectedPath;
            if (selectedPath.startsWith("Type.")) {
                actualPath = selectedPath.substring(5);
            }

            System.out.println("Using PropertyUpdater for individual change:");
            System.out.println("  actualPath: " + actualPath);
            System.out.println("  oldValue: " + selectedValue);
            System.out.println("  newValue: " + newValue);

            // Use PropertyUpdater.updateProperty with tracking - same as mass changes!
            boolean success = PropertyUpdater.updateProperty(ndfObject, actualPath, newValue, modificationTracker);

            if (!success) {
                throw new IllegalArgumentException("Failed to update property at path: " + actualPath);
            }

            // Notify listeners
            propertyChangeSupport.firePropertyChange("unitModified", null, ndfObject);

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


    private NDFValue parseValue(String text, NDFValue originalValue) {
        switch (originalValue.getType()) {
            case STRING:
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

            case TUPLE:
                // Handle tuple editing: (EBaseHitValueModifier,Moving, 7)
                if (!text.startsWith("(") || !text.endsWith(")")) {
                    throw new IllegalArgumentException("Tuple must be in the format '(value1, value2, value3)'");
                }

                String tupleContent = text.substring(1, text.length() - 1);
                String[] tupleElements = tupleContent.split(",");

                TupleValue newTuple = NDFValue.createTuple();
                for (String element : tupleElements) {
                    element = element.trim();

                    // Try to parse each element as the appropriate type
                    NDFValue elementValue;
                    if (element.startsWith("'") && element.endsWith("'")) {
                        elementValue = NDFValue.createString(element.substring(1, element.length() - 1));
                    } else if (element.equalsIgnoreCase("true") || element.equalsIgnoreCase("false")) {
                        elementValue = NDFValue.createBoolean(Boolean.parseBoolean(element));
                    } else if (element.matches("-?\\d+(\\.\\d+)?")) {
                        elementValue = NDFValue.createNumber(Double.parseDouble(element));
                    } else if (element.contains("/")) {
                        String[] enumParts = element.split("/");
                        if (enumParts.length == 2) {
                            elementValue = NDFValue.createEnum(enumParts[0], enumParts[1]);
                        } else {
                            elementValue = NDFValue.createString(element);
                        }
                    } else {
                        elementValue = NDFValue.createString(element);
                    }

                    newTuple.add(elementValue);
                }

                return newTuple;

            default:
                throw new IllegalArgumentException("Cannot edit this type of value");
        }
    }


    private void updateValueInUnitDescriptor(String path, NDFValue newValue) {
        // Skip the "Type" node if present
        String actualPath = path;
        if (path.startsWith("Type.")) {
            actualPath = path.substring(5);
        }
        // Note: Don't pass modificationTracker here as we already recorded it in applyValue()
        boolean success = PropertyUpdater.updateProperty(ndfObject, actualPath, newValue, null);

        if (!success) {
            throw new IllegalArgumentException("Failed to update property at path: " + path);
        }
    }


    private void selectPropertyByPath(String path) {
        // Not implemented yet
    }


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
