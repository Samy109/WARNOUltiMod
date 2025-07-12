package com.warnomodmaker.gui;

import com.warnomodmaker.model.FileTabState;
import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.PropertyScanner;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FileTabPanel extends JPanel {
    private final FileTabState tabState;
    private final List<PropertyChangeListener> modificationListeners;
    private final List<Consumer<FileTabState>> tabStateChangeListeners;

    // UI Components
    private JSplitPane splitPane;
    private UnitBrowser objectBrowser;
    private UnitEditor objectEditor;


    public FileTabPanel(FileTabState tabState) {
        this.tabState = tabState;
        this.modificationListeners = new ArrayList<>();
        this.tabStateChangeListeners = new ArrayList<>();

        initializeUI();
        setupEventHandlers();
        updateFromTabState();
    }


    public FileTabPanel(FileTabState tabState, PropertyScanner propertyScanner, DefaultListModel<NDFValue.ObjectValue> listModel) {
        this.tabState = tabState;
        this.modificationListeners = new ArrayList<>();
        this.tabStateChangeListeners = new ArrayList<>();

        initializeUI();
        setupEventHandlers();
        updateFromTabStateWithPreprocessedData(propertyScanner, listModel);
    }


    private void initializeUI() {
        setLayout(new BorderLayout());
        objectBrowser = new UnitBrowser();
        objectEditor = new UnitEditor();
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, objectBrowser, objectEditor);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.3); // Give more space to the editor

        add(splitPane, BorderLayout.CENTER);
    }


    private void setupEventHandlers() {
        // Listen for object selection changes
        objectBrowser.addUnitSelectionListener(this::onUnitSelected);

        // Listen for property filter changes
        objectBrowser.addPropertyFilterListener(this::onPropertyFilterChanged);

        // Listen for property changes in the editor
        objectEditor.addPropertyChangeListener(this::onPropertyChanged);
    }


    public void updateFromTabState() {
        if (tabState.hasData()) {
            objectBrowser.setUnitDescriptors(tabState.getUnitDescriptors(), tabState.getFileType());
            objectBrowser.setModificationTracker(tabState.getModificationTracker());
            objectEditor.setUnitDescriptor(null, tabState.getModificationTracker(), tabState.getFileType());

            // Restore selection if available
            restoreSelection();
        } else {
            // Clear components but preserve file type
            objectBrowser.setUnitDescriptors(null, tabState.getFileType());
            objectBrowser.setModificationTracker(null);
            objectEditor.setUnitDescriptor(null, null, tabState.getFileType());
        }
    }


    public void updateFromTabStateWithPreprocessedData(PropertyScanner propertyScanner, DefaultListModel<NDFValue.ObjectValue> listModel) {
        if (tabState.hasData()) {
            objectBrowser.setUnitDescriptorsWithPreprocessedData(tabState.getUnitDescriptors(), tabState.getFileType(), propertyScanner, listModel);
            objectBrowser.setModificationTracker(tabState.getModificationTracker());
            objectEditor.setUnitDescriptor(null, tabState.getModificationTracker(), tabState.getFileType());

            // Restore selection if available
            restoreSelection();
        } else {
            // Clear components but preserve file type
            objectBrowser.setUnitDescriptors(null, tabState.getFileType());
            objectBrowser.setModificationTracker(null);
            objectEditor.setUnitDescriptor(null, null, tabState.getFileType());
        }
    }


    public void saveToTabState() {
        // This would require extending object browser to expose current selection
        // For now, we'll implement this in a future iteration
    }


    private void restoreSelection() {
        String selectedObjectName = tabState.getSelectedUnitName();
        if (selectedObjectName != null && tabState.getUnitDescriptors() != null) {
            for (NDFValue.ObjectValue object : tabState.getUnitDescriptors()) {
                if (selectedObjectName.equals(object.getInstanceName())) {
                    // This would require extending object browser to support programmatic selection
                    // For now, we'll implement this in a future iteration
                    break;
                }
            }
        }
    }


    private void onUnitSelected(NDFValue.ObjectValue ndfObject) {
        objectEditor.setUnitDescriptor(ndfObject, tabState.getModificationTracker(), tabState.getFileType());
        if (ndfObject != null) {
            tabState.setSelectedUnitName(ndfObject.getInstanceName());
        } else {
            tabState.setSelectedUnitName(null);
        }

        // Notify listeners of tab state change
        notifyTabStateChangeListeners();
    }

    private void onPropertyFilterChanged(String filter) {
        // Update the editor to show only properties matching the filter
        objectEditor.setPropertyFilter(filter);
    }


    private void onPropertyChanged(PropertyChangeEvent e) {
        // Mark tab as modified
        tabState.setModified(true);

        // Update browser visual representation without changing selection
        // Only repaint to show modification indicators, don't refresh the entire list
        objectBrowser.repaintList();

        // Notify modification listeners
        notifyModificationListeners(e);

        // Notify tab state change listeners
        notifyTabStateChangeListeners();
    }


    public void refresh() {
        // Re-set the unit descriptors to ensure the browser has the latest data
        if (tabState.hasData()) {
            objectBrowser.setUnitDescriptors(tabState.getNDFObjects(), tabState.getFileType());
        }

        objectEditor.refresh();
    }


    public FileTabState getTabState() {
        return tabState;
    }


    public UnitBrowser getUnitBrowser() {
        return objectBrowser;
    }


    public UnitEditor getUnitEditor() {
        return objectEditor;
    }


    public void addModificationListener(PropertyChangeListener listener) {
        modificationListeners.add(listener);
    }


    public void removeModificationListener(PropertyChangeListener listener) {
        modificationListeners.remove(listener);
    }


    public void addTabStateChangeListener(Consumer<FileTabState> listener) {
        tabStateChangeListeners.add(listener);
    }


    public void removeTabStateChangeListener(Consumer<FileTabState> listener) {
        tabStateChangeListeners.remove(listener);
    }


    private void notifyModificationListeners(PropertyChangeEvent e) {
        for (PropertyChangeListener listener : modificationListeners) {
            listener.propertyChange(e);
        }
    }


    private void notifyTabStateChangeListeners() {
        for (Consumer<FileTabState> listener : tabStateChangeListeners) {
            listener.accept(tabState);
        }
    }


    public int getDividerLocation() {
        return splitPane.getDividerLocation();
    }


    public void setDividerLocation(int location) {
        splitPane.setDividerLocation(location);
    }


    public void performGlobalSearch(String searchText) {
        objectBrowser.performGlobalSearch(searchText);
    }

    /**
     * Select and highlight a specific entity by name
     */
    public void selectAndHighlightEntity(String entityName) {
        if (entityName == null || !tabState.hasData()) {
            return;
        }

        // Find the entity in the unit descriptors
        List<NDFValue.ObjectValue> units = tabState.getUnitDescriptors();

        if (units != null) {
            for (int i = 0; i < units.size(); i++) {
                NDFValue.ObjectValue unit = units.get(i);
                String unitName = unit.getInstanceName();

                if (entityName.equals(unitName)) {
                    // Select the entity in the browser
                    objectBrowser.selectEntityByIndex(i);
                    return;
                }
            }
        }
    }
}
