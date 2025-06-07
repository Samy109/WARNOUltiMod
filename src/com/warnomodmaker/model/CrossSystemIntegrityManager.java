package com.warnomodmaker.model;

import java.util.*;

/**
 * Coordinates cross-system integrity across all NDF files.
 * Integrates CrossFileReferenceManager, GlobalGUIDManager, and PropertyPathMigrationManager.
 * Provides unified validation and management of cross-file dependencies.
 */
public class CrossSystemIntegrityManager {
    
    private final CrossFileReferenceManager referenceManager;
    private final GlobalGUIDManager guidManager;
    private final PropertyPathMigrationManager migrationManager;
    
    // File registration tracking
    private final Map<String, NDFValue.NDFFileType> registeredFiles = new HashMap<>();
    private final Map<String, List<NDFValue.ObjectValue>> fileContents = new HashMap<>();
    
    public CrossSystemIntegrityManager() {
        this.referenceManager = new CrossFileReferenceManager();
        this.guidManager = new GlobalGUIDManager();
        this.migrationManager = new PropertyPathMigrationManager();
    }
    
    /**
     * Register a file with all subsystems
     */
    public void registerFile(String fileName, NDFValue.NDFFileType fileType, List<NDFValue.ObjectValue> objects) {
        System.out.println("Registering file: " + fileName + " (" + fileType + ")");
        
        // Store file information
        registeredFiles.put(fileName, fileType);
        fileContents.put(fileName, new ArrayList<>(objects));
        
        // Register with all subsystems
        referenceManager.registerFile(fileName, objects);
        guidManager.registerFile(fileName, objects);
        migrationManager.learnValidPaths(fileType, objects);
        
        System.out.println("File registration complete for: " + fileName);
    }
    
    /**
     * Unregister a file from all subsystems
     */
    public void unregisterFile(String fileName) {
        System.out.println("Unregistering file: " + fileName);
        
        // Unregister from all subsystems
        referenceManager.unregisterFile(fileName);
        guidManager.unregisterFile(fileName);
        
        // Remove from tracking
        registeredFiles.remove(fileName);
        fileContents.remove(fileName);
        
        System.out.println("File unregistration complete for: " + fileName);
    }
    
    /**
     * Perform comprehensive validation across all systems
     */
    public CrossSystemValidationResult validateAllSystems() {
        System.out.println("Performing comprehensive cross-system validation...");
        
        // Validate references
        CrossFileReferenceManager.CrossFileValidationResult refResult = referenceManager.validateAllReferences();
        
        // Validate GUIDs
        GlobalGUIDManager.GUIDValidationResult guidResult = guidManager.validateAllGuids();
        
        // Collect all issues
        List<String> allIssues = new ArrayList<>();
        
        // Reference issues (only for loaded files)
        if (refResult.hasIssues()) {
            allIssues.add("REFERENCE ISSUES (in loaded files):");
            for (Map.Entry<String, List<CrossFileReferenceManager.ReferenceLocation>> entry : refResult.getBrokenReferences().entrySet()) {
                String template = entry.getKey();
                List<CrossFileReferenceManager.ReferenceLocation> locations = entry.getValue();
                allIssues.add("  Broken reference '" + template + "' used in " + locations.size() + " locations:");
                for (CrossFileReferenceManager.ReferenceLocation loc : locations) {
                    allIssues.add("    " + loc.toString());
                }
            }

            for (String missingFile : refResult.getMissingFiles()) {
                allIssues.add("  Missing file: " + missingFile);
            }
        }

        // Unloaded file references (informational, not errors)
        if (refResult.hasUnloadedFileReferences()) {
            allIssues.add("");
            allIssues.add("UNLOADED FILE REFERENCES (not errors):");
            Map<String, Set<String>> unloadedRefs = refResult.getUnloadedFileReferences();
            for (Map.Entry<String, Set<String>> entry : unloadedRefs.entrySet()) {
                String fileName = entry.getKey();
                Set<String> templates = entry.getValue();
                allIssues.add("  File '" + fileName + ".ndf' needs to be loaded for " + templates.size() + " template references");

                // Show first few template examples
                int count = 0;
                for (String template : templates) {
                    if (count >= 3) {
                        allIssues.add("    ... and " + (templates.size() - 3) + " more");
                        break;
                    }
                    allIssues.add("    " + template);
                    count++;
                }
            }
        }
        
        // GUID issues
        if (guidResult.hasIssues()) {
            allIssues.add("GUID ISSUES:");
            for (Map.Entry<String, List<GlobalGUIDManager.GUIDLocation>> entry : guidResult.getConflicts().entrySet()) {
                String guid = entry.getKey();
                List<GlobalGUIDManager.GUIDLocation> locations = entry.getValue();
                allIssues.add("  GUID conflict '" + guid + "' used in " + locations.size() + " locations:");
                for (GlobalGUIDManager.GUIDLocation loc : locations) {
                    allIssues.add("    " + loc.toString());
                }
            }
            
            for (String orphanedGuid : guidResult.getOrphanedGuids()) {
                allIssues.add("  Orphaned GUID: " + orphanedGuid);
            }
        }
        
        return new CrossSystemValidationResult(refResult, guidResult, allIssues);
    }
    
    /**
     * Validate entity creation requirements across all files
     */
    public EntityCreationValidationResult validateEntityCreation(String entityType, String entityName) {
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Check GUID uniqueness for new entity
        String newGuid = guidManager.generateUniqueGuid();
        if (guidManager.getFileOwningGuid(newGuid) != null) {
            issues.add("Generated GUID already exists: " + newGuid);
        }
        
        // Check template name uniqueness
        String templateName = "Descriptor_Unit_" + entityName; // Example template name
        String existingFile = referenceManager.getFileDefiningTemplate(templateName);
        if (existingFile != null) {
            issues.add("Template name already exists: " + templateName + " in " + existingFile);
        }
        
        // Validate cross-file dependencies would be satisfied
        // This would need to be enhanced based on your entity creation requirements
        
        return new EntityCreationValidationResult(issues, warnings, newGuid);
    }
    
    /**
     * Update references when an object is renamed
     */
    public ReferenceUpdateResult updateReferencesAfterRename(String fileName, String oldName, String newName) {
        List<String> updatedFiles = new ArrayList<>();
        List<String> failedUpdates = new ArrayList<>();
        
        // Get all files that reference the old name
        Set<String> referencingFiles = referenceManager.getFilesReferencingTemplate(oldName);
        
        for (String refFile : referencingFiles) {
            try {
                // Get reference locations
                List<CrossFileReferenceManager.ReferenceLocation> locations = referenceManager.getReferenceLocations(oldName);
                
                // Update references in the file
                List<NDFValue.ObjectValue> objects = fileContents.get(refFile);
                if (objects != null) {
                    boolean updated = updateReferencesInObjects(objects, oldName, newName, locations);
                    if (updated) {
                        updatedFiles.add(refFile);
                        // Re-register the file to update tracking
                        NDFValue.NDFFileType fileType = registeredFiles.get(refFile);
                        registerFile(refFile, fileType, objects);
                    }
                }
            } catch (Exception e) {
                failedUpdates.add(refFile + ": " + e.getMessage());
            }
        }
        
        return new ReferenceUpdateResult(updatedFiles, failedUpdates);
    }
    
    /**
     * Update references within objects
     */
    private boolean updateReferencesInObjects(List<NDFValue.ObjectValue> objects, String oldName, String newName, 
                                            List<CrossFileReferenceManager.ReferenceLocation> locations) {
        boolean updated = false;
        
        for (CrossFileReferenceManager.ReferenceLocation location : locations) {
            for (NDFValue.ObjectValue obj : objects) {
                if (obj.getInstanceName().equals(location.getObjectName())) {
                    // Update the reference using PropertyUpdater
                    NDFValue newValue = NDFValue.createTemplateRef("~/" + newName);
                    boolean success = PropertyUpdater.updateProperty(obj, location.getPropertyPath(), newValue, null);
                    if (success) {
                        updated = true;
                    }
                }
            }
        }
        
        return updated;
    }
    
    /**
     * Migrate a mod profile using the migration manager
     */
    public PropertyPathMigrationManager.ModProfileMigrationResult migrateModProfile(ModProfile profile) {
        return migrationManager.migrateModProfile(profile);
    }
    
    /**
     * Add explicit path mapping for migration
     */
    public void addPathMapping(String oldPath, String newPath, String reason) {
        migrationManager.addPathMapping(oldPath, newPath, reason);
    }
    
    /**
     * Get comprehensive system statistics
     */
    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== Cross-System Integrity Statistics ===\n");
        stats.append("Registered Files: ").append(registeredFiles.size()).append("\n");
        stats.append(referenceManager.getStatistics()).append("\n");
        stats.append(guidManager.getStatistics()).append("\n");
        stats.append(migrationManager.getStatistics()).append("\n");

        // Add system health summary
        CrossSystemValidationResult result = validateAllSystems();
        if (result.hasIssues()) {
            stats.append("System Health: Issues detected (").append(result.getTotalIssueCount()).append(" total)\n");
        } else {
            stats.append("System Health: All systems operational\n");
        }

        return stats.toString();
    }
    
    /**
     * Get all registered files
     */
    public Map<String, NDFValue.NDFFileType> getRegisteredFiles() {
        return new HashMap<>(registeredFiles);
    }
    
    /**
     * Get reference manager for direct access
     */
    public CrossFileReferenceManager getReferenceManager() {
        return referenceManager;
    }
    
    /**
     * Get GUID manager for direct access
     */
    public GlobalGUIDManager getGuidManager() {
        return guidManager;
    }
    
    /**
     * Get migration manager for direct access
     */
    public PropertyPathMigrationManager getMigrationManager() {
        return migrationManager;
    }
    
    /**
     * Cross-system validation result
     */
    public static class CrossSystemValidationResult {
        private final CrossFileReferenceManager.CrossFileValidationResult referenceResult;
        private final GlobalGUIDManager.GUIDValidationResult guidResult;
        private final List<String> allIssues;
        
        public CrossSystemValidationResult(CrossFileReferenceManager.CrossFileValidationResult referenceResult,
                                         GlobalGUIDManager.GUIDValidationResult guidResult,
                                         List<String> allIssues) {
            this.referenceResult = referenceResult;
            this.guidResult = guidResult;
            this.allIssues = allIssues;
        }
        
        public CrossFileReferenceManager.CrossFileValidationResult getReferenceResult() { return referenceResult; }
        public GlobalGUIDManager.GUIDValidationResult getGuidResult() { return guidResult; }
        public List<String> getAllIssues() { return allIssues; }
        
        public boolean hasIssues() {
            return referenceResult.hasIssues() || guidResult.hasIssues();
        }
        
        public int getTotalIssueCount() {
            return referenceResult.getTotalBrokenReferences() + guidResult.getTotalConflicts() + guidResult.getTotalOrphanedGuids();
        }
    }
    
    /**
     * Entity creation validation result
     */
    public static class EntityCreationValidationResult {
        private final List<String> issues;
        private final List<String> warnings;
        private final String generatedGuid;
        
        public EntityCreationValidationResult(List<String> issues, List<String> warnings, String generatedGuid) {
            this.issues = issues;
            this.warnings = warnings;
            this.generatedGuid = generatedGuid;
        }
        
        public List<String> getIssues() { return issues; }
        public List<String> getWarnings() { return warnings; }
        public String getGeneratedGuid() { return generatedGuid; }
        
        public boolean isValid() { return issues.isEmpty(); }
    }
    
    /**
     * Reference update result
     */
    public static class ReferenceUpdateResult {
        private final List<String> updatedFiles;
        private final List<String> failedUpdates;
        
        public ReferenceUpdateResult(List<String> updatedFiles, List<String> failedUpdates) {
            this.updatedFiles = updatedFiles;
            this.failedUpdates = failedUpdates;
        }
        
        public List<String> getUpdatedFiles() { return updatedFiles; }
        public List<String> getFailedUpdates() { return failedUpdates; }
        
        public boolean hasFailures() { return !failedUpdates.isEmpty(); }
        public int getSuccessCount() { return updatedFiles.size(); }
        public int getFailureCount() { return failedUpdates.size(); }
    }
}
