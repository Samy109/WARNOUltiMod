package com.warnomodmaker.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages GUID uniqueness across all NDF files.
 * Ensures no GUID conflicts exist and provides conflict resolution.
 * NO FALLBACKS - all GUIDs must be explicitly tracked and validated.
 */
public class GlobalGUIDManager {
    
    // GUID ownership tracking - GUID -> file that owns it
    private final Map<String, String> guidToFile = new ConcurrentHashMap<>();
    
    // GUID locations - GUID -> list of (file, object, property path) where it's used
    private final Map<String, List<GUIDLocation>> guidLocations = new ConcurrentHashMap<>();
    
    // File GUID sets - file -> set of GUIDs used in that file
    private final Map<String, Set<String>> fileGuids = new ConcurrentHashMap<>();
    
    // Reserved GUIDs that should never be generated
    private final Set<String> reservedGuids = new HashSet<>();
    
    /**
     * GUID location tracking
     */
    public static class GUIDLocation {
        private final String fileName;
        private final String objectName;
        private final String propertyPath;
        private final String fullPath;
        private final boolean isDefinition; // true if this is where the GUID is defined, false if referenced
        
        public GUIDLocation(String fileName, String objectName, String propertyPath, String fullPath, boolean isDefinition) {
            this.fileName = fileName;
            this.objectName = objectName;
            this.propertyPath = propertyPath;
            this.fullPath = fullPath;
            this.isDefinition = isDefinition;
        }
        
        public String getFileName() { return fileName; }
        public String getObjectName() { return objectName; }
        public String getPropertyPath() { return propertyPath; }
        public String getFullPath() { return fullPath; }
        public boolean isDefinition() { return isDefinition; }
        
        @Override
        public String toString() {
            String type = isDefinition ? "DEF" : "REF";
            return String.format("[%s] %s:%s.%s", type, fileName, objectName, propertyPath);
        }
    }
    
    /**
     * Register a file and scan it for GUID usage
     */
    public void registerFile(String fileName, List<NDFValue.ObjectValue> objects) {
        // Clear existing data for this file
        unregisterFile(fileName);
        
        Set<String> fileGuidSet = new HashSet<>();
        
        // Scan all objects for GUID usage
        for (NDFValue.ObjectValue obj : objects) {
            scanObjectForGuids(fileName, obj.getInstanceName(), obj, "", fileGuidSet);
        }
        
        // Store file GUID set
        fileGuids.put(fileName, fileGuidSet);
        
        System.out.println("Registered file " + fileName + " with " + fileGuidSet.size() + " GUIDs");
    }
    
    /**
     * Unregister a file and clean up all its GUID tracking
     */
    public void unregisterFile(String fileName) {
        Set<String> fileGuidSet = fileGuids.get(fileName);
        if (fileGuidSet != null) {
            // Remove GUID ownership for this file
            for (String guid : fileGuidSet) {
                if (fileName.equals(guidToFile.get(guid))) {
                    guidToFile.remove(guid);
                }
            }
            
            // Remove GUID locations from this file
            for (List<GUIDLocation> locations : guidLocations.values()) {
                locations.removeIf(loc -> loc.getFileName().equals(fileName));
            }
        }
        
        fileGuids.remove(fileName);
    }
    
    /**
     * Recursively scan an object for GUID usage
     */
    private void scanObjectForGuids(String fileName, String objectName, NDFValue value, String currentPath, Set<String> fileGuidSet) {
        if (value instanceof NDFValue.ObjectValue) {
            NDFValue.ObjectValue obj = (NDFValue.ObjectValue) value;
            for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                String newPath = currentPath.isEmpty() ? propertyName : currentPath + "." + propertyName;
                scanObjectForGuids(fileName, objectName, entry.getValue(), newPath, fileGuidSet);
            }
        } else if (value instanceof NDFValue.ArrayValue) {
            NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
            for (int i = 0; i < array.getElements().size(); i++) {
                String newPath = currentPath + "[" + i + "]";
                scanObjectForGuids(fileName, objectName, array.getElements().get(i), newPath, fileGuidSet);
            }
        } else if (value instanceof NDFValue.GUIDValue) {
            NDFValue.GUIDValue guidValue = (NDFValue.GUIDValue) value;
            String guid = guidValue.getGUID();
            
            // Track this GUID
            fileGuidSet.add(guid);
            
            // Determine if this is a definition or reference
            boolean isDefinition = currentPath.equals("DescriptorId") || currentPath.endsWith(".DescriptorId");
            
            // Record ownership (definitions take precedence)
            if (isDefinition || !guidToFile.containsKey(guid)) {
                guidToFile.put(guid, fileName);
            }
            
            // Record the location
            String fullPath = objectName + "." + currentPath;
            GUIDLocation location = new GUIDLocation(fileName, objectName, currentPath, fullPath, isDefinition);
            guidLocations.computeIfAbsent(guid, k -> new ArrayList<>()).add(location);
        }
    }
    
    /**
     * Check if a GUID is unique across all files
     */
    public boolean isGuidUnique(String guid) {
        List<GUIDLocation> locations = guidLocations.get(guid);
        if (locations == null || locations.isEmpty()) {
            return true; // Not used anywhere
        }
        
        // Count definitions (should be exactly 1)
        long definitionCount = locations.stream().filter(GUIDLocation::isDefinition).count();
        return definitionCount <= 1;
    }
    
    /**
     * Find all GUID conflicts (GUIDs used in multiple files or multiple definitions)
     */
    public Map<String, List<GUIDLocation>> findGuidConflicts() {
        Map<String, List<GUIDLocation>> conflicts = new HashMap<>();
        
        for (Map.Entry<String, List<GUIDLocation>> entry : guidLocations.entrySet()) {
            String guid = entry.getKey();
            List<GUIDLocation> locations = entry.getValue();
            
            // Check for multiple definitions
            long definitionCount = locations.stream().filter(GUIDLocation::isDefinition).count();
            if (definitionCount > 1) {
                conflicts.put(guid, new ArrayList<>(locations));
                continue;
            }
            
            // Check for usage across multiple files
            Set<String> filesUsing = new HashSet<>();
            for (GUIDLocation location : locations) {
                filesUsing.add(location.getFileName());
            }
            
            if (filesUsing.size() > 1) {
                conflicts.put(guid, new ArrayList<>(locations));
            }
        }
        
        return conflicts;
    }
    
    /**
     * Generate a new unique GUID
     */
    public String generateUniqueGuid() {
        String guid;
        int attempts = 0;
        do {
            guid = generateRandomGuid();
            attempts++;
            if (attempts > 1000) {
                throw new RuntimeException("Failed to generate unique GUID after 1000 attempts");
            }
        } while (guidToFile.containsKey(guid) || reservedGuids.contains(guid));
        
        return guid;
    }
    
    /**
     * Generate a random GUID in WARNO format
     */
    private String generateRandomGuid() {
        Random random = new Random();
        StringBuilder guid = new StringBuilder();
        
        // WARNO GUIDs are typically 8-4-4-4-12 format
        for (int i = 0; i < 32; i++) {
            if (i == 8 || i == 12 || i == 16 || i == 20) {
                guid.append('-');
            }
            guid.append(Integer.toHexString(random.nextInt(16)).toUpperCase());
        }
        
        return guid.toString();
    }
    
    /**
     * Reserve a GUID to prevent it from being generated
     */
    public void reserveGuid(String guid) {
        reservedGuids.add(guid);
    }
    
    /**
     * Get the file that owns a specific GUID
     */
    public String getFileOwningGuid(String guid) {
        return guidToFile.get(guid);
    }
    
    /**
     * Get all GUIDs used in a specific file
     */
    public Set<String> getGuidsInFile(String fileName) {
        Set<String> guids = fileGuids.get(fileName);
        return guids != null ? new HashSet<>(guids) : new HashSet<>();
    }
    
    /**
     * Get all locations where a GUID is used
     */
    public List<GUIDLocation> getGuidLocations(String guid) {
        List<GUIDLocation> locations = guidLocations.get(guid);
        return locations != null ? new ArrayList<>(locations) : new ArrayList<>();
    }
    
    /**
     * Validate all GUID usage across files
     */
    public GUIDValidationResult validateAllGuids() {
        Map<String, List<GUIDLocation>> conflicts = findGuidConflicts();
        List<String> orphanedGuids = new ArrayList<>();
        
        // Find orphaned GUIDs (referenced but not defined)
        for (Map.Entry<String, List<GUIDLocation>> entry : guidLocations.entrySet()) {
            String guid = entry.getKey();
            List<GUIDLocation> locations = entry.getValue();
            
            boolean hasDefinition = locations.stream().anyMatch(GUIDLocation::isDefinition);
            boolean hasReferences = locations.stream().anyMatch(loc -> !loc.isDefinition());
            
            if (hasReferences && !hasDefinition) {
                orphanedGuids.add(guid);
            }
        }
        
        return new GUIDValidationResult(conflicts, orphanedGuids);
    }
    
    /**
     * GUID validation result container
     */
    public static class GUIDValidationResult {
        private final Map<String, List<GUIDLocation>> conflicts;
        private final List<String> orphanedGuids;
        
        public GUIDValidationResult(Map<String, List<GUIDLocation>> conflicts, List<String> orphanedGuids) {
            this.conflicts = conflicts;
            this.orphanedGuids = orphanedGuids;
        }
        
        public Map<String, List<GUIDLocation>> getConflicts() { return conflicts; }
        public List<String> getOrphanedGuids() { return orphanedGuids; }
        
        public boolean hasIssues() {
            return !conflicts.isEmpty() || !orphanedGuids.isEmpty();
        }
        
        public int getTotalConflicts() {
            return conflicts.size();
        }
        
        public int getTotalOrphanedGuids() {
            return orphanedGuids.size();
        }
    }
    
    /**
     * Get comprehensive statistics
     */
    public String getStatistics() {
        int totalGuids = guidToFile.size();
        int totalFiles = fileGuids.size();
        int conflicts = findGuidConflicts().size();
        int totalUsages = guidLocations.values().stream().mapToInt(List::size).sum();
        
        return String.format("Global GUIDs: %d unique GUIDs across %d files, %d conflicts, %d total usages",
                           totalGuids, totalFiles, conflicts, totalUsages);
    }
}
