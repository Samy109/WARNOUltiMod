package com.warnomodmaker.model;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

/**
 * Generates unique GUIDs for new NDF objects.
 * Ensures no collisions with existing GUIDs in the file.
 */
public class GUIDGenerator {
    
    private final Set<String> existingGuids;
    
    public GUIDGenerator() {
        this.existingGuids = new HashSet<>();
    }
    
    /**
     * Register an existing GUID to avoid collisions
     */
    public void registerExistingGuid(String guid) {
        if (guid != null && !guid.isEmpty()) {
            existingGuids.add(guid.toLowerCase());
        }
    }
    
    /**
     * Generate a new unique GUID
     */
    public String generateGUID() {
        String newGuid;
        do {
            UUID uuid = UUID.randomUUID();
            newGuid = "GUID:{" + uuid.toString() + "}";
        } while (existingGuids.contains(newGuid.toLowerCase()));
        
        // Register the new GUID to prevent future collisions
        existingGuids.add(newGuid.toLowerCase());
        
        return newGuid;
    }
    
    /**
     * Check if a GUID already exists
     */
    public boolean guidExists(String guid) {
        return guid != null && existingGuids.contains(guid.toLowerCase());
    }
    
    /**
     * Clear all registered GUIDs (useful when switching files)
     */
    public void clear() {
        existingGuids.clear();
    }
    
    /**
     * Get count of registered GUIDs
     */
    public int getRegisteredGuidCount() {
        return existingGuids.size();
    }
}
