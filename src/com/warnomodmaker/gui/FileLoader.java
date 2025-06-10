package com.warnomodmaker.gui;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.ModificationTracker;
import java.util.List;
import java.util.Map;

/**
 * Interface for loading files - allows wizard to request file loading without direct dependency on MainWindow
 */
public interface FileLoader {
    void autoLoadRequiredFiles(String[] fileNames);
    Map<String, List<NDFValue.ObjectValue>> getOpenFiles();
    Map<String, ModificationTracker> getModificationTrackers();
}
