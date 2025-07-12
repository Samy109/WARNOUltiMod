package com.warnomodmaker.gui;

import com.warnomodmaker.gui.renderers.EnhancedTreeCellRenderer;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UnitEditor extends JPanel {
    private ObjectValue ndfObject;
    private ModificationTracker modificationTracker;
    private NDFValue.NDFFileType fileType = NDFValue.NDFFileType.UNKNOWN;
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
    private String propertyFilter;
    private EnhancedTreeCellRenderer treeCellRenderer;


    public UnitEditor() {
        propertyChangeSupport = new PropertyChangeSupport(this);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Object Properties"));
        ndfObject = null;
        selectedPath = null;
        selectedValue = null;
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(300);
        rootNode = new DefaultMutableTreeNode("Unit");
        treeModel = new DefaultTreeModel(rootNode);
        propertyTree = new JTree(treeModel);
        propertyTree.setRootVisible(false);
        propertyTree.setShowsRootHandles(true);

        treeCellRenderer = new EnhancedTreeCellRenderer();
        propertyTree.setCellRenderer(treeCellRenderer);
        propertyTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                TreePath path = e.getPath();
                if (path != null) {
                    handlePropertySelection(path);
                }
            }
        });

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (propertyTree != null) {
                    propertyTree.repaint();
                }
            }
        });

        JScrollPane treeScrollPane = new JScrollPane(propertyTree);
        splitPane.setLeftComponent(treeScrollPane);

        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (propertyTree != null) {
                SwingUtilities.invokeLater(() -> propertyTree.repaint());
            }
        });

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
        setUnitDescriptor(ndfObject, modificationTracker, NDFValue.NDFFileType.UNKNOWN);
    }

    public void setUnitDescriptor(ObjectValue ndfObject, ModificationTracker modificationTracker, NDFValue.NDFFileType fileType) {
        this.ndfObject = ndfObject;
        this.modificationTracker = modificationTracker;
        this.fileType = fileType;

        // Clear any stale state when setting a new object
        clearEditorState();

        updatePropertyTree();
        clearEditor();
        updateBorderTitle();
    }

    /**
     * Clears all internal state related to property selection and navigation.
     * This prevents stale references when switching between objects or reloading files.
     */
    private void clearEditorState() {
        selectedPath = null;
        selectedValue = null;
        selectedParentObject = null;
        selectedPropertyName = null;
        currentSelectedPath = null;
        suppressSelectionEvents = false;
    }

    public void setPropertyFilter(String filter) {
        this.propertyFilter = filter;
        updatePropertyTree();
        clearEditor();
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
        if (propertyChangeSupport != null) {
            propertyChangeSupport.addPropertyChangeListener(listener);
        }
    }


    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (propertyChangeSupport != null) {
            propertyChangeSupport.removePropertyChangeListener(listener);
        }
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
        // Safety check: don't try to restore if we don't have a valid object
        if (ndfObject == null || propertyPath == null) {
            return;
        }

        TreePath newPath = findTreePathByPropertyPath(propertyPath);

        if (newPath != null) {
            // Suppress selection events while we restore the selection
            suppressSelectionEvents = true;
            propertyTree.setSelectionPath(newPath);
            currentSelectedPath = newPath;
            selectedPath = propertyPath;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) newPath.getLastPathComponent();
            if (node != null && node.getUserObject() instanceof EnhancedTreeCellRenderer.PropertyNode) {
                EnhancedTreeCellRenderer.PropertyNode propertyNode = (EnhancedTreeCellRenderer.PropertyNode) node.getUserObject();
                selectedValue = propertyNode.getValue();
                selectedPropertyName = propertyNode.getName();

                // Use try-catch to handle any issues with finding parent object
                try {
                    selectedParentObject = findParentObject(newPath);
                } catch (Exception e) {
                    // If we can't find the parent object, clear the selection
                    selectedParentObject = null;
                    clearEditor();
                    suppressSelectionEvents = false;
                    return;
                }

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
                new EnhancedTreeCellRenderer.PropertyNode("Type", NDFValue.createString(ndfObject.getTypeName()))
            );
            rootNode.add(typeNode);
            for (Map.Entry<String, NDFValue> entry : ndfObject.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                NDFValue propertyValue = entry.getValue();

                DefaultMutableTreeNode propertyNode = createPropertyNode(propertyName, propertyValue, "");
                if (propertyNode != null) {
                    rootNode.add(propertyNode);
                }
            }
        }

        treeModel.reload();

        // Expand the root node
        propertyTree.expandPath(new TreePath(rootNode.getPath()));
    }


    private DefaultMutableTreeNode createPropertyNode(String propertyName, NDFValue propertyValue, String currentPath) {
        String fullPath = currentPath.isEmpty() ? propertyName : currentPath + "." + propertyName;

        // If we have a filter, check if this property or any of its children match
        if (propertyFilter != null && !propertyFilter.isEmpty()) {
            if (!propertyMatchesFilter(propertyName, propertyValue, fullPath)) {
                return null; // Skip this property if it doesn't match the filter
            }
        }

        // Create enhanced display name for better usability
        String displayName = createEnhancedDisplayName(propertyName, propertyValue, currentPath);
        EnhancedTreeCellRenderer.PropertyNode node = new EnhancedTreeCellRenderer.PropertyNode(displayName, propertyName, propertyValue);

        // Check if this property has been modified
        if (modificationTracker != null && ndfObject != null) {
            String unitName = ndfObject.getInstanceName();
            if (unitName != null) {
                // Check both the full path and without the "Type." prefix for compatibility
                boolean isModified = modificationTracker.hasModificationForProperty(unitName, fullPath);
                if (!isModified && fullPath.startsWith("Type.")) {
                    // Also check without the "Type." prefix since that's how modifications are stored
                    String pathWithoutType = fullPath.substring(5);
                    isModified = modificationTracker.hasModificationForProperty(unitName, pathWithoutType);
                }

                // Also check if any child property has been modified (hierarchical highlighting)
                if (!isModified) {
                    isModified = modificationTracker.hasModificationForPropertyOrChildren(unitName, fullPath);
                    if (!isModified && fullPath.startsWith("Type.")) {
                        String pathWithoutType = fullPath.substring(5);
                        isModified = modificationTracker.hasModificationForPropertyOrChildren(unitName, pathWithoutType);
                    }
                }

                if (isModified) {
                    node.setModified(true);
                }
            }
        }

        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);

        switch (propertyValue.getType()) {
            case OBJECT:
                ObjectValue objectValue = (ObjectValue) propertyValue;

                for (Map.Entry<String, NDFValue> entry : objectValue.getProperties().entrySet()) {
                    String childName = entry.getKey();
                    NDFValue childValue = entry.getValue();

                    DefaultMutableTreeNode childNode = createPropertyNode(childName, childValue, fullPath);
                    if (childNode != null) {
                        treeNode.add(childNode);
                    }
                }
                break;

            case ARRAY:
                ArrayValue arrayValue = (ArrayValue) propertyValue;

                // Determine if this array should be treated as a single property or expanded
                if (shouldExpandArray(arrayValue, propertyName)) {
                    // Expand complex arrays (arrays of objects, etc.)
                    for (int i = 0; i < arrayValue.getElements().size(); i++) {
                        NDFValue element = arrayValue.getElements().get(i);
                        String childName = "[" + i + "]";

                        DefaultMutableTreeNode childNode = createPropertyNode(childName, element, fullPath);
                        if (childNode != null) {
                            treeNode.add(childNode);
                        }
                    }
                } else {
                    // Treat simple arrays as single properties (like TraitsToken, BaseHitValueModifiers)
                    // No child nodes - the array will be editable as a whole
                }
                break;

            case MAP:
                MapValue mapValue = (MapValue) propertyValue;

                for (int i = 0; i < mapValue.getEntries().size(); i++) {
                    Map.Entry<NDFValue, NDFValue> entry = mapValue.getEntries().get(i);
                    String childName = "(" + entry.getKey() + ")";

                    DefaultMutableTreeNode childNode = createPropertyNode(childName, entry.getValue(), fullPath);
                    if (childNode != null) {
                        treeNode.add(childNode);
                    }
                }
                break;

            case TUPLE:
                TupleValue tupleValue = (TupleValue) propertyValue;

                for (int i = 0; i < tupleValue.getElements().size(); i++) {
                    NDFValue element = tupleValue.getElements().get(i);
                    String childName = "[" + i + "]";

                    DefaultMutableTreeNode childNode = createPropertyNode(childName, element, fullPath);
                    if (childNode != null) {
                        treeNode.add(childNode);
                    }
                }
                break;
        }

        return treeNode;
    }

    /**
     * Determines whether an array should be expanded into individual elements
     * or treated as a single editable property.
     */
    private boolean shouldExpandArray(ArrayValue arrayValue, String propertyName) {
        // Empty arrays should not be expanded
        if (arrayValue.getElements().isEmpty()) {
            return false;
        }

        // Special cases for known simple array properties that should remain as single properties
        if ("TraitsToken".equals(propertyName) ||
            "BaseHitValueModifiers".equals(propertyName) ||
            propertyName.endsWith("Token") ||
            propertyName.endsWith("Tokens")) {
            return false; // Keep as single property
        }

        // Check the type of elements in the array
        NDFValue firstElement = arrayValue.getElements().get(0);

        // Arrays of objects should always be expanded (like ModulesDescriptors)
        if (firstElement instanceof ObjectValue) {
            return true;
        }

        // Arrays of simple values (strings, numbers, enums, tuples) should remain as single properties
        // unless they are specifically known to need expansion
        if (firstElement instanceof StringValue ||
            firstElement instanceof NumberValue ||
            firstElement instanceof BooleanValue ||
            firstElement instanceof EnumValue ||
            firstElement instanceof TupleValue) {

            // Special case: ModulesDescriptors should always be expanded even if it contains tuples/other values
            if ("ModulesDescriptors".equals(propertyName)) {
                return true;
            }

            return false; // Keep as single property
        }

        // For other complex types (maps, nested arrays), expand by default
        return true;
    }

    private String createEnhancedDisplayName(String propertyName, NDFValue propertyValue, String currentPath) {
        if (propertyName.startsWith("[") && propertyName.endsWith("]")) {
            return createArrayElementDisplayName(propertyName, propertyValue, currentPath);
        }

        if (propertyName.startsWith("(") && propertyName.endsWith(")")) {
            return createMapKeyDisplayName(propertyName, propertyValue);
        }

        return propertyName;
    }

    private String createArrayElementDisplayName(String indexName, NDFValue elementValue, String currentPath) {
        String index = indexName.substring(1, indexName.length() - 1);

        if (currentPath.endsWith("ModulesDescriptors")) {
            return createModuleDisplayName(index, elementValue);
        }

        if (elementValue.getType() == NDFValue.ValueType.OBJECT) {
            ObjectValue objValue = (ObjectValue) elementValue;
            String typeName = objValue.getTypeName();
            if (typeName != null && !typeName.isEmpty()) {
                return typeName;
            }
        } else if (elementValue.getType() == NDFValue.ValueType.TEMPLATE_REF) {
            String templateRef = elementValue.toString();
            String displayRef = extractTemplateDisplayName(templateRef);
            return displayRef;
        } else if (elementValue.getType() == NDFValue.ValueType.STRING) {
            String strValue = elementValue.toString();
            if (strValue.length() > 20) {
                strValue = strValue.substring(0, 17) + "...";
            }
            return strValue;
        }

        return "Item " + (Integer.parseInt(index) + 1); // Convert to 1-based indexing for user display
    }

    private String createModuleDisplayName(String index, NDFValue moduleValue) {
        if (moduleValue.getType() == NDFValue.ValueType.OBJECT) {
            ObjectValue objValue = (ObjectValue) moduleValue;
            String typeName = objValue.getTypeName();

            String instanceName = objValue.getInstanceName();
            if (instanceName != null && !instanceName.isEmpty()) {
                return instanceName + " (" + typeName + ")";
            } else {
                return typeName;
            }
        } else if (moduleValue.getType() == NDFValue.ValueType.TEMPLATE_REF) {
            String templateRef = moduleValue.toString();
            String displayRef = extractTemplateDisplayName(templateRef);
            return displayRef;
        }

        return "Module";
    }

    private String createMapKeyDisplayName(String keyName, NDFValue keyValue) {
        // No truncation - show the full key name
        return keyName;
    }

    private String extractTemplateDisplayName(String templateRef) {
        if (templateRef.startsWith("~/") || templateRef.startsWith("$/")) {
            String withoutPrefix = templateRef.substring(2);
            String[] parts = withoutPrefix.split("/");
            return parts[parts.length - 1];
        }
        return templateRef;
    }

    private boolean propertyMatchesFilter(String propertyName, NDFValue propertyValue, String fullPath) {
        String filter = propertyFilter.toLowerCase();

        // Check if the property name contains the filter text
        if (propertyName.toLowerCase().contains(filter)) {
            return true;
        }

        // Check if the full path contains the filter text
        if (fullPath.toLowerCase().contains(filter)) {
            return true;
        }

        // Recursively check children to see if any match
        return hasChildMatchingFilter(propertyValue, fullPath, filter);
    }

    private boolean hasChildMatchingFilter(NDFValue value, String currentPath, String filter) {
        switch (value.getType()) {
            case OBJECT:
                ObjectValue objectValue = (ObjectValue) value;
                for (Map.Entry<String, NDFValue> entry : objectValue.getProperties().entrySet()) {
                    String childName = entry.getKey();
                    String childPath = currentPath + "." + childName;

                    if (childName.toLowerCase().contains(filter) ||
                        childPath.toLowerCase().contains(filter) ||
                        hasChildMatchingFilter(entry.getValue(), childPath, filter)) {
                        return true;
                    }
                }
                break;

            case ARRAY:
                ArrayValue arrayValue = (ArrayValue) value;
                for (int i = 0; i < arrayValue.getElements().size(); i++) {
                    String childPath = currentPath + "[" + i + "]";
                    if (hasChildMatchingFilter(arrayValue.getElements().get(i), childPath, filter)) {
                        return true;
                    }
                }
                break;

            case MAP:
                MapValue mapValue = (MapValue) value;
                for (int i = 0; i < mapValue.getEntries().size(); i++) {
                    Map.Entry<NDFValue, NDFValue> entry = mapValue.getEntries().get(i);
                    String childPath = currentPath + "(" + entry.getKey() + ")";
                    if (hasChildMatchingFilter(entry.getValue(), childPath, filter)) {
                        return true;
                    }
                }
                break;

            case TUPLE:
                TupleValue tupleValue = (TupleValue) value;
                for (int i = 0; i < tupleValue.getElements().size(); i++) {
                    String childPath = currentPath + "[" + i + "]";
                    if (hasChildMatchingFilter(tupleValue.getElements().get(i), childPath, filter)) {
                        return true;
                    }
                }
                break;
        }

        return false;
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

        if (node != null && node.getUserObject() instanceof EnhancedTreeCellRenderer.PropertyNode) {
            EnhancedTreeCellRenderer.PropertyNode propertyNode = (EnhancedTreeCellRenderer.PropertyNode) node.getUserObject();

            selectedPath = getPropertyPath(path);
            selectedValue = propertyNode.getValue();
            selectedPropertyName = propertyNode.getName();
            currentSelectedPath = path;
            selectedParentObject = findParentObject(path);



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

            if (node.getUserObject() instanceof EnhancedTreeCellRenderer.PropertyNode) {
                EnhancedTreeCellRenderer.PropertyNode propertyNode = (EnhancedTreeCellRenderer.PropertyNode) node.getUserObject();

                // Use the original property name for path construction
                String actualPropertyName = propertyNode.getOriginalPropertyName();

                if (actualPropertyName.startsWith("[") && actualPropertyName.endsWith("]")) {
                    // This is an array index, append it directly to the path without a dot
                    path.append(actualPropertyName);
                } else {
                    // Regular property name
                    if (path.length() > 0) {
                        path.append(".");
                    }
                    path.append(actualPropertyName);
                }
            }
        }

        return path.toString();
    }




    private ObjectValue findParentObject(TreePath treePath) {
        Object[] nodes = treePath.getPath();

        // Safety check for null or invalid tree path
        if (nodes == null || nodes.length == 0 || ndfObject == null) {
            return null;
        }

        // If we're at the root level (direct property of unitDescriptor)
        if (nodes.length == 2) {
            return ndfObject;
        }

        // Navigate through the object hierarchy to find the parent
        ObjectValue currentObject = ndfObject;

        // Skip root node and go to the parent of the selected node
        for (int i = 1; i < nodes.length - 1; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes[i];

            if (node.getUserObject() instanceof EnhancedTreeCellRenderer.PropertyNode) {
                EnhancedTreeCellRenderer.PropertyNode propertyNode = (EnhancedTreeCellRenderer.PropertyNode) node.getUserObject();

                // Use the original property name for navigation
                String propertyName = propertyNode.getOriginalPropertyName();

                // Skip "Type" node
                if ("Type".equals(propertyName)) {
                    continue;
                }

                // Safety check: ensure currentObject is not null before accessing properties
                if (currentObject == null) {
                    return null;
                }

                NDFValue value = currentObject.getProperty(propertyName);
                if (value instanceof ObjectValue) {
                    currentObject = (ObjectValue) value;
                } else if (value instanceof ArrayValue) {
                    ArrayValue arrayValue = (ArrayValue) value;

                    // The next node should be the array index like "[19]"
                    if (i + 1 < nodes.length - 1) {
                        DefaultMutableTreeNode nextNode = (DefaultMutableTreeNode) nodes[i + 1];
                        if (nextNode.getUserObject() instanceof EnhancedTreeCellRenderer.PropertyNode) {
                            EnhancedTreeCellRenderer.PropertyNode nextPropertyNode = (EnhancedTreeCellRenderer.PropertyNode) nextNode.getUserObject();

                            // Use the original property name to get the array index
                            String indexStr = nextPropertyNode.getOriginalPropertyName();

                            if (indexStr.startsWith("[") && indexStr.endsWith("]")) {
                                try {
                                    int index = Integer.parseInt(indexStr.substring(1, indexStr.length() - 1));
                                    NDFValue arrayElement = arrayValue.getElements().get(index);
                                    if (arrayElement instanceof ObjectValue) {
                                        currentObject = (ObjectValue) arrayElement;
                                        i++; // Skip the index node since we processed it
                                    } else {
                                        return null;
                                    }
                                } catch (NumberFormatException | IndexOutOfBoundsException ex) {
                                    return null;
                                }
                            } else {
                                return null;
                            }
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }

        return currentObject;
    }


    private void updateEditor(EnhancedTreeCellRenderer.PropertyNode propertyNode) {
        NDFValue value = propertyNode.getValue();

        // Only allow editing of simple types
        boolean editable = isEditableType(value);

        // Format the display value appropriately
        String displayValue = "";
        if (value != null) {
            if (value.getType() == NDFValue.ValueType.TEMPLATE_REF) {
                // For template references, ensure they show with ~/ prefix
                String refValue = value.toString();
                if (!refValue.startsWith("~/") && !refValue.startsWith("$/")) {
                    displayValue = "~/" + refValue;
                } else {
                    displayValue = refValue;
                }
            } else if (value.getType() == NDFValue.ValueType.RESOURCE_REF) {
                // For resource references, ensure they show with $/ prefix
                String refValue = value.toString();
                if (!refValue.startsWith("$/")) {
                    displayValue = "$/" + refValue;
                } else {
                    displayValue = refValue;
                }
            } else {
                displayValue = value.toString();
            }
        }

        valueField.setText(displayValue);
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
                // Simple arrays (like TraitsToken, BaseHitValueModifiers) should be editable
                // Complex arrays (like ModulesDescriptors) should not be directly editable
                ArrayValue arrayValue = (ArrayValue) value;
                return isSimpleArray(arrayValue);

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

    /**
     * Determines if an array contains simple values that can be edited as a complete array.
     */
    private boolean isSimpleArray(ArrayValue arrayValue) {
        if (arrayValue.getElements().isEmpty()) {
            return true; // Empty arrays are editable
        }

        // Check the type of elements in the array
        NDFValue firstElement = arrayValue.getElements().get(0);

        // Arrays of simple values are editable
        return firstElement instanceof StringValue ||
               firstElement instanceof NumberValue ||
               firstElement instanceof BooleanValue ||
               firstElement instanceof EnumValue ||
               firstElement instanceof TupleValue;
    }


    private void applyValue(ActionEvent e) {

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



            // Use PropertyUpdater.updateProperty with tracking and file type - same as mass changes!
            boolean success = PropertyUpdater.updateProperty(ndfObject, actualPath, newValue, modificationTracker, fileType);

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
                // CRITICAL FIX: Preserve original quote type when creating new string value
                StringValue originalStringValue = (StringValue) originalValue;
                boolean useDoubleQuotes = originalStringValue.useDoubleQuotes();

                // Remove quotes from input if user included them
                String cleanText = removeQuotesIfPresent(text);
                return NDFValue.createString(cleanText, useDoubleQuotes);

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
                // Auto-add ~/ prefix if missing
                if (text.startsWith("~/") || text.startsWith("$/")) {
                    return NDFValue.createTemplateRef(text);
                } else {
                    // Automatically add ~/ prefix for template references
                    return NDFValue.createTemplateRef("~/" + text);
                }

            case RESOURCE_REF:
                // Auto-add $/ prefix if missing
                if (text.startsWith("$/")) {
                    return NDFValue.createResourceRef(text);
                } else {
                    // Automatically add $/ prefix for resource references
                    return NDFValue.createResourceRef("$/" + text);
                }

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
                int elementIndex = 0;
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
                            // CRITICAL FIX: Preserve original quote type for string elements
                            boolean tupleElementUseDoubleQuotes = getOriginalQuoteType(elementIndex);
                            String cleanElement = removeQuotesIfPresent(element);
                            elementValue = NDFValue.createString(cleanElement, tupleElementUseDoubleQuotes);
                        }
                    } else {
                        // CRITICAL FIX: Preserve original quote type for string elements
                        boolean tupleElementUseDoubleQuotes = getOriginalQuoteType(elementIndex);
                        String cleanElement = removeQuotesIfPresent(element);
                        elementValue = NDFValue.createString(cleanElement, tupleElementUseDoubleQuotes);
                    }

                    newTuple.add(elementValue);
                    elementIndex++;
                }

                return newTuple;

            case ARRAY:
                // Handle array editing: [ 'MOTION', 'HE', 'KINETIC' ] or [ (tuple1), (tuple2) ]
                if (!text.startsWith("[") || !text.endsWith("]")) {
                    throw new IllegalArgumentException("Array must be in the format '[ element1, element2, element3 ]'");
                }

                String arrayContent = text.substring(1, text.length() - 1).trim();
                ArrayValue newArray = NDFValue.createArray();

                if (!arrayContent.isEmpty()) {
                    String[] arrayElements = splitArrayElements(arrayContent);

                    for (String element : arrayElements) {
                        element = element.trim();

                        // Parse each element based on its format
                        NDFValue elementValue;
                        if (element.startsWith("'") && element.endsWith("'")) {
                            // String element with single quotes
                            elementValue = NDFValue.createString(element.substring(1, element.length() - 1), false);
                        } else if (element.startsWith("\"") && element.endsWith("\"")) {
                            // String element with double quotes
                            elementValue = NDFValue.createString(element.substring(1, element.length() - 1), true);
                        } else if (element.startsWith("(") && element.endsWith(")")) {
                            // Tuple element - parse recursively
                            elementValue = parseValue(element, NDFValue.createTuple());
                        } else if (element.equalsIgnoreCase("true") || element.equalsIgnoreCase("false")) {
                            // Boolean element
                            elementValue = NDFValue.createBoolean(Boolean.parseBoolean(element));
                        } else if (element.matches("-?\\d+(\\.\\d+)?")) {
                            // Number element
                            elementValue = NDFValue.createNumber(Double.parseDouble(element));
                        } else if (element.contains("/")) {
                            // Enum element
                            String[] enumParts = element.split("/");
                            if (enumParts.length == 2) {
                                elementValue = NDFValue.createEnum(enumParts[0], enumParts[1]);
                            } else {
                                elementValue = NDFValue.createString(element);
                            }
                        } else {
                            // Default to string
                            elementValue = NDFValue.createString(element);
                        }

                        newArray.add(elementValue, true); // Add with comma
                    }
                }

                return newArray;

            default:
                throw new IllegalArgumentException("Cannot edit this type of value");
        }
    }

    /**
     * Split array elements while respecting nested parentheses and quotes.
     */
    private String[] splitArrayElements(String arrayContent) {
        List<String> elements = new ArrayList<>();
        StringBuilder currentElement = new StringBuilder();
        int parenthesesDepth = 0;
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);

            if (!inQuotes) {
                if (c == '\'' || c == '"') {
                    inQuotes = true;
                    quoteChar = c;
                    currentElement.append(c);
                } else if (c == '(') {
                    parenthesesDepth++;
                    currentElement.append(c);
                } else if (c == ')') {
                    parenthesesDepth--;
                    currentElement.append(c);
                } else if (c == ',' && parenthesesDepth == 0) {
                    // Found a top-level comma - end current element
                    elements.add(currentElement.toString().trim());
                    currentElement.setLength(0);
                } else {
                    currentElement.append(c);
                }
            } else {
                currentElement.append(c);
                if (c == quoteChar) {
                    inQuotes = false;
                }
            }
        }

        // Add the last element
        if (currentElement.length() > 0) {
            elements.add(currentElement.toString().trim());
        }

        return elements.toArray(new String[0]);
    }


    private void updateValueInUnitDescriptor(String path, NDFValue newValue) {
        // Skip the "Type" node if present
        String actualPath = path;
        if (path.startsWith("Type.")) {
            actualPath = path.substring(5);
        }
        // Note: Don't pass modificationTracker here as we already recorded it in applyValue()
        boolean success = PropertyUpdater.updateProperty(ndfObject, actualPath, newValue, null, fileType);

        if (!success) {
            throw new IllegalArgumentException("Failed to update property at path: " + path);
        }
    }


    private void selectPropertyByPath(String path) {
        // Not implemented yet
    }


    private boolean getOriginalQuoteType(int elementIndex) {
        if (selectedValue instanceof NDFValue.TupleValue) {
            NDFValue.TupleValue originalTuple = (NDFValue.TupleValue) selectedValue;
            if (elementIndex < originalTuple.getElements().size()) {
                NDFValue originalElement = originalTuple.getElements().get(elementIndex);
                if (originalElement instanceof NDFValue.StringValue) {
                    NDFValue.StringValue originalString = (NDFValue.StringValue) originalElement;
                    return originalString.useDoubleQuotes();
                }
            }
        }
        return false;
    }

    private String removeQuotesIfPresent(String input) {
        if ((input.startsWith("\"") && input.endsWith("\"")) ||
            (input.startsWith("'") && input.endsWith("'"))) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }


    // Using PropertyNode from EnhancedTreeCellRenderer
}
