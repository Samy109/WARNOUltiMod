package com.warnomodmaker.model;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class UserPreferences {
    private static final String PREFERENCES_FILE = "warno-mod-maker.properties";
    private static final String LAST_DIRECTORY_KEY = "last.directory"; // Legacy key for backward compatibility
    private static final String LAST_NDF_DIRECTORY_KEY = "last.ndf.directory";
    private static final String LAST_PROFILE_DIRECTORY_KEY = "last.profile.directory";
    private static final String WINDOW_WIDTH_KEY = "window.width";
    private static final String WINDOW_HEIGHT_KEY = "window.height";
    private static final String WINDOW_X_KEY = "window.x";
    private static final String WINDOW_Y_KEY = "window.y";

    private static UserPreferences instance;
    private Properties properties;
    private Path preferencesPath;

    private UserPreferences() {
        properties = new Properties();
        String userHome = System.getProperty("user.home");
        preferencesPath = Paths.get(userHome, ".warno-mod-maker", PREFERENCES_FILE);

        loadPreferences();
    }

    
    public static UserPreferences getInstance() {
        if (instance == null) {
            instance = new UserPreferences();
        }
        return instance;
    }

    
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

    
    public void savePreferences() {
        try {
            Files.createDirectories(preferencesPath.getParent());

            try (OutputStream output = Files.newOutputStream(preferencesPath)) {
                properties.store(output, "WARNO Mod Maker Preferences");
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not save preferences: " + e.getMessage());
        }
    }

    
    public String getLastDirectory() {
        return properties.getProperty(LAST_DIRECTORY_KEY, System.getProperty("user.home"));
    }

    
    public void setLastDirectory(String directory) {
        if (directory != null && !directory.trim().isEmpty()) {
            properties.setProperty(LAST_DIRECTORY_KEY, directory);
            savePreferences();
        }
    }

    
    public String getLastNDFDirectory() {
        // First check for specific NDF directory, then fall back to legacy directory
        String ndfDir = properties.getProperty(LAST_NDF_DIRECTORY_KEY);
        if (ndfDir != null && !ndfDir.trim().isEmpty()) {
            return ndfDir;
        }
        return getLastDirectory(); // Fall back to legacy directory
    }

    
    public void setLastNDFDirectory(String directory) {
        if (directory != null && !directory.trim().isEmpty()) {
            properties.setProperty(LAST_NDF_DIRECTORY_KEY, directory);
            savePreferences();
        }
    }

    
    public String getLastProfileDirectory() {
        // First check for specific profile directory, then fall back to legacy directory
        String profileDir = properties.getProperty(LAST_PROFILE_DIRECTORY_KEY);
        if (profileDir != null && !profileDir.trim().isEmpty()) {
            return profileDir;
        }
        return getLastDirectory(); // Fall back to legacy directory
    }

    
    public void setLastProfileDirectory(String directory) {
        if (directory != null && !directory.trim().isEmpty()) {
            properties.setProperty(LAST_PROFILE_DIRECTORY_KEY, directory);
            savePreferences();
        }
    }

    
    public int getWindowWidth() {
        return Integer.parseInt(properties.getProperty(WINDOW_WIDTH_KEY, "1200"));
    }

    
    public void setWindowWidth(int width) {
        properties.setProperty(WINDOW_WIDTH_KEY, String.valueOf(width));
    }

    
    public int getWindowHeight() {
        return Integer.parseInt(properties.getProperty(WINDOW_HEIGHT_KEY, "800"));
    }

    
    public void setWindowHeight(int height) {
        properties.setProperty(WINDOW_HEIGHT_KEY, String.valueOf(height));
    }

    
    public int getWindowX() {
        return Integer.parseInt(properties.getProperty(WINDOW_X_KEY, "100"));
    }

    
    public void setWindowX(int x) {
        properties.setProperty(WINDOW_X_KEY, String.valueOf(x));
    }

    
    public int getWindowY() {
        return Integer.parseInt(properties.getProperty(WINDOW_Y_KEY, "100"));
    }

    
    public void setWindowY(int y) {
        properties.setProperty(WINDOW_Y_KEY, String.valueOf(y));
    }

    
    public void saveWindowBounds(int x, int y, int width, int height) {
        setWindowX(x);
        setWindowY(y);
        setWindowWidth(width);
        setWindowHeight(height);
        savePreferences();
    }
}
