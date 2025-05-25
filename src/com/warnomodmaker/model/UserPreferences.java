package com.warnomodmaker.model;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Manages user preferences for the WARNO Mod Maker application
 */
public class UserPreferences {
    private static final String PREFERENCES_FILE = "warno-mod-maker.properties";
    private static final String LAST_DIRECTORY_KEY = "last.directory";
    private static final String WINDOW_WIDTH_KEY = "window.width";
    private static final String WINDOW_HEIGHT_KEY = "window.height";
    private static final String WINDOW_X_KEY = "window.x";
    private static final String WINDOW_Y_KEY = "window.y";
    
    private static UserPreferences instance;
    private Properties properties;
    private Path preferencesPath;
    
    private UserPreferences() {
        properties = new Properties();
        
        // Get user home directory for storing preferences
        String userHome = System.getProperty("user.home");
        preferencesPath = Paths.get(userHome, ".warno-mod-maker", PREFERENCES_FILE);
        
        loadPreferences();
    }
    
    /**
     * Gets the singleton instance of UserPreferences
     */
    public static UserPreferences getInstance() {
        if (instance == null) {
            instance = new UserPreferences();
        }
        return instance;
    }
    
    /**
     * Loads preferences from the file system
     */
    private void loadPreferences() {
        try {
            if (Files.exists(preferencesPath)) {
                try (InputStream input = Files.newInputStream(preferencesPath)) {
                    properties.load(input);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load preferences: " + e.getMessage());
            // Continue with default preferences
        }
    }
    
    /**
     * Saves preferences to the file system
     */
    public void savePreferences() {
        try {
            // Create directory if it doesn't exist
            Files.createDirectories(preferencesPath.getParent());
            
            try (OutputStream output = Files.newOutputStream(preferencesPath)) {
                properties.store(output, "WARNO Mod Maker Preferences");
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not save preferences: " + e.getMessage());
        }
    }
    
    /**
     * Gets the last used directory for file operations
     */
    public String getLastDirectory() {
        return properties.getProperty(LAST_DIRECTORY_KEY, System.getProperty("user.home"));
    }
    
    /**
     * Sets the last used directory for file operations
     */
    public void setLastDirectory(String directory) {
        if (directory != null && !directory.trim().isEmpty()) {
            properties.setProperty(LAST_DIRECTORY_KEY, directory);
            savePreferences();
        }
    }
    
    /**
     * Gets the last window width
     */
    public int getWindowWidth() {
        return Integer.parseInt(properties.getProperty(WINDOW_WIDTH_KEY, "1200"));
    }
    
    /**
     * Sets the window width
     */
    public void setWindowWidth(int width) {
        properties.setProperty(WINDOW_WIDTH_KEY, String.valueOf(width));
    }
    
    /**
     * Gets the last window height
     */
    public int getWindowHeight() {
        return Integer.parseInt(properties.getProperty(WINDOW_HEIGHT_KEY, "800"));
    }
    
    /**
     * Sets the window height
     */
    public void setWindowHeight(int height) {
        properties.setProperty(WINDOW_HEIGHT_KEY, String.valueOf(height));
    }
    
    /**
     * Gets the last window X position
     */
    public int getWindowX() {
        return Integer.parseInt(properties.getProperty(WINDOW_X_KEY, "100"));
    }
    
    /**
     * Sets the window X position
     */
    public void setWindowX(int x) {
        properties.setProperty(WINDOW_X_KEY, String.valueOf(x));
    }
    
    /**
     * Gets the last window Y position
     */
    public int getWindowY() {
        return Integer.parseInt(properties.getProperty(WINDOW_Y_KEY, "100"));
    }
    
    /**
     * Sets the window Y position
     */
    public void setWindowY(int y) {
        properties.setProperty(WINDOW_Y_KEY, String.valueOf(y));
    }
    
    /**
     * Saves window position and size
     */
    public void saveWindowBounds(int x, int y, int width, int height) {
        setWindowX(x);
        setWindowY(y);
        setWindowWidth(width);
        setWindowHeight(height);
        savePreferences();
    }
}
