package com.warnomodmaker.model;

import com.warnomodmaker.parser.NDFParser;

import java.io.File;
import java.util.List;

public class FileTabState {
    // File and parsing state
    private File file;
    private List<NDFValue.ObjectValue> ndfObjects;
    private NDFValue.NDFFileType fileType;
    private NDFParser parser; // Store for original tokens

    // Modification tracking
    private ModificationTracker modificationTracker;
    private boolean modified;

    // UI state preservation
    private String selectedObjectName; // For preserving object selection across tab switches
    private String selectedPropertyPath; // For preserving property selection

    // Tab display information
    private String tabTitle;
    private String tabTooltip;

    
    public FileTabState() {
        this.file = null;
        this.ndfObjects = null;
        this.fileType = NDFValue.NDFFileType.UNKNOWN;
        this.parser = null;
        this.modificationTracker = new ModificationTracker();
        this.modified = false;
        this.selectedObjectName = null;
        this.selectedPropertyPath = null;
        this.tabTitle = "Untitled";
        this.tabTooltip = "";
    }

    
    public FileTabState(File file, List<NDFValue.ObjectValue> ndfObjects,
                       NDFValue.NDFFileType fileType, NDFParser parser) {
        this();
        this.file = file;
        this.ndfObjects = ndfObjects;
        this.fileType = fileType;
        this.parser = parser;
        updateTabDisplay();
    }
    public File getFile() { return file; }
    public void setFile(File file) {
        this.file = file;
        updateTabDisplay();
    }

    public List<NDFValue.ObjectValue> getUnitDescriptors() { return ndfObjects; }
    public void setUnitDescriptors(List<NDFValue.ObjectValue> ndfObjects) {
        this.ndfObjects = ndfObjects;
    }

    public List<NDFValue.ObjectValue> getNDFObjects() { return ndfObjects; }
    public void setNDFObjects(List<NDFValue.ObjectValue> ndfObjects) {
        this.ndfObjects = ndfObjects;
    }

    public NDFValue.NDFFileType getFileType() { return fileType; }
    public void setFileType(NDFValue.NDFFileType fileType) { this.fileType = fileType; }

    public NDFParser getParser() { return parser; }
    public void setParser(NDFParser parser) { this.parser = parser; }

    public ModificationTracker getModificationTracker() { return modificationTracker; }

    public boolean isModified() { return modified; }
    public void setModified(boolean modified) {
        this.modified = modified;
        updateTabDisplay();
    }

    public String getSelectedUnitName() { return selectedObjectName; }
    public void setSelectedUnitName(String selectedObjectName) { this.selectedObjectName = selectedObjectName; }

    public String getSelectedObjectName() { return selectedObjectName; }
    public void setSelectedObjectName(String selectedObjectName) { this.selectedObjectName = selectedObjectName; }

    public String getSelectedPropertyPath() { return selectedPropertyPath; }
    public void setSelectedPropertyPath(String selectedPropertyPath) { this.selectedPropertyPath = selectedPropertyPath; }

    public String getTabTitle() { return tabTitle; }
    public String getTabTooltip() { return tabTooltip; }

    
    private void updateTabDisplay() {
        if (file != null) {
            tabTitle = file.getName();
            tabTooltip = file.getAbsolutePath();
        } else {
            tabTitle = "Untitled";
            tabTooltip = "";
        }

        if (modified) {
            tabTitle += " *";
        }
    }

    
    public boolean hasData() {
        return ndfObjects != null && !ndfObjects.isEmpty();
    }

    
    public String getDisplayName() {
        if (file != null) {
            String name = file.getName();
            if (modified) name += " *";
            return name;
        }
        return "Untitled";
    }

    
    public void clearModifications() {
        modificationTracker.clearModifications();
        setModified(false);
    }
}
