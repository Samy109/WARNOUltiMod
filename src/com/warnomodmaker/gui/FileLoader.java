package com.warnomodmaker.gui;

/**
 * Interface for loading files - allows wizard to request file loading without direct dependency on MainWindow
 */
public interface FileLoader {
    void autoLoadRequiredFiles(String[] fileNames);
}
