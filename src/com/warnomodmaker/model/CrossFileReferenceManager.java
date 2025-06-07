package com.warnomodmaker.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages template references across multiple NDF files.
 * Tracks which files reference which templates and validates cross-file integrity.
 * NO FALLBACKS - all references must be explicitly tracked and validated.
 */
public class CrossFileReferenceManager {
    
    // Core reference tracking - template name -> set of files that reference it
    private final Map<String, Set<String>> templateReferences = new ConcurrentHashMap<>();
    
    // Template definitions - template name -> file that defines it
    private final Map<String, String> templateDefinitions = new ConcurrentHashMap<>();
    
    // File contents - file name -> set of templates defined in that file
    private final Map<String, Set<String>> fileDefinitions = new ConcurrentHashMap<>();
    
    // Reference locations - template name -> list of (file, property path) where it's referenced
    private final Map<String, List<ReferenceLocation>> referenceLocations = new ConcurrentHashMap<>();
    
    /**
     * Reference location tracking
     */
    public static class ReferenceLocation {
        private final String fileName;
        private final String objectName;
        private final String propertyPath;
        private final String fullPath; // Complete path for precise updates
        
        public ReferenceLocation(String fileName, String objectName, String propertyPath, String fullPath) {
            this.fileName = fileName;
            this.objectName = objectName;
            this.propertyPath = propertyPath;
            this.fullPath = fullPath;
        }
        
        public String getFileName() { return fileName; }
        public String getObjectName() { return objectName; }
        public String getPropertyPath() { return propertyPath; }
        public String getFullPath() { return fullPath; }
        
        @Override
        public String toString() {
            return fileName + ":" + objectName + "." + propertyPath;
        }
    }
    
    /**
     * Register a file and scan it for template definitions and references
     */
    public void registerFile(String fileName, List<NDFValue.ObjectValue> objects) {
        // Clear existing data for this file
        unregisterFile(fileName);
        
        Set<String> definedTemplates = new HashSet<>();
        
        // Scan for template definitions (exported objects)
        for (NDFValue.ObjectValue obj : objects) {
            if (obj.isExported() && obj.getInstanceName() != null) {
                String templateName = obj.getInstanceName();
                definedTemplates.add(templateName);
                templateDefinitions.put(templateName, fileName);
            }
        }
        
        // Store file definitions
        fileDefinitions.put(fileName, definedTemplates);
        
        // Scan for template references
        for (NDFValue.ObjectValue obj : objects) {
            scanObjectForReferences(fileName, obj.getInstanceName(), obj, "");
        }
        
        System.out.println("Registered file " + fileName + " with " + definedTemplates.size() + " definitions");
    }
    
    /**
     * Unregister a file and clean up all its references
     */
    public void unregisterFile(String fileName) {
        // Remove template definitions
        Set<String> definedTemplates = fileDefinitions.get(fileName);
        if (definedTemplates != null) {
            for (String template : definedTemplates) {
                templateDefinitions.remove(template);
            }
        }
        fileDefinitions.remove(fileName);
        
        // Remove references from this file
        for (Set<String> referencingFiles : templateReferences.values()) {
            referencingFiles.remove(fileName);
        }
        
        // Remove reference locations from this file
        for (List<ReferenceLocation> locations : referenceLocations.values()) {
            locations.removeIf(loc -> loc.getFileName().equals(fileName));
        }
    }
    
    /**
     * Recursively scan an object for template references
     */
    private void scanObjectForReferences(String fileName, String objectName, NDFValue value, String currentPath) {
        if (value instanceof NDFValue.ObjectValue) {
            NDFValue.ObjectValue obj = (NDFValue.ObjectValue) value;
            for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                String newPath = currentPath.isEmpty() ? propertyName : currentPath + "." + propertyName;
                scanObjectForReferences(fileName, objectName, entry.getValue(), newPath);
            }
        } else if (value instanceof NDFValue.ArrayValue) {
            NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
            for (int i = 0; i < array.getElements().size(); i++) {
                String newPath = currentPath + "[" + i + "]";
                scanObjectForReferences(fileName, objectName, array.getElements().get(i), newPath);
            }
        } else if (value instanceof NDFValue.TemplateRefValue) {
            NDFValue.TemplateRefValue templateRef = (NDFValue.TemplateRefValue) value;
            String referencedTemplate = templateRef.getPath();
            
            // Clean template name (remove ~/ prefix)
            if (referencedTemplate.startsWith("~/")) {
                referencedTemplate = referencedTemplate.substring(2);
            }
            
            // Record the reference
            templateReferences.computeIfAbsent(referencedTemplate, k -> new HashSet<>()).add(fileName);
            
            // Record the location
            String fullPath = objectName + "." + currentPath;
            ReferenceLocation location = new ReferenceLocation(fileName, objectName, currentPath, fullPath);
            referenceLocations.computeIfAbsent(referencedTemplate, k -> new ArrayList<>()).add(location);
        } else if (value instanceof NDFValue.ResourceRefValue) {
            NDFValue.ResourceRefValue resourceRef = (NDFValue.ResourceRefValue) value;
            String referencedResource = resourceRef.getPath();
            
            // Clean resource name (remove $/ prefix)
            if (referencedResource.startsWith("$/")) {
                referencedResource = referencedResource.substring(2);
            }
            
            // Record the reference (treat resources like templates for cross-file tracking)
            templateReferences.computeIfAbsent(referencedResource, k -> new HashSet<>()).add(fileName);
            
            // Record the location
            String fullPath = objectName + "." + currentPath;
            ReferenceLocation location = new ReferenceLocation(fileName, objectName, currentPath, fullPath);
            referenceLocations.computeIfAbsent(referencedResource, k -> new ArrayList<>()).add(location);
        }
    }
    
    /**
     * Find all broken references (references to templates that don't exist in loaded files)
     */
    public List<String> findBrokenReferences() {
        List<String> brokenRefs = new ArrayList<>();

        for (String template : templateReferences.keySet()) {
            if (!templateDefinitions.containsKey(template)) {
                // Only consider it broken if the target file is actually loaded
                String targetFile = determineTargetFileForTemplate(template);
                if (targetFile != null && fileDefinitions.containsKey(targetFile)) {
                    // Target file is loaded but template doesn't exist - this is a real error
                    brokenRefs.add(template);
                }
            }
        }

        return brokenRefs;
    }

    /**
     * Get detailed information about broken references (only for loaded files)
     */
    public Map<String, List<ReferenceLocation>> getBrokenReferenceDetails() {
        Map<String, List<ReferenceLocation>> brokenDetails = new HashMap<>();

        for (String template : templateReferences.keySet()) {
            if (!templateDefinitions.containsKey(template)) {
                // Only consider it broken if the target file is actually loaded
                String targetFile = determineTargetFileForTemplate(template);
                if (targetFile != null && fileDefinitions.containsKey(targetFile)) {
                    // Target file is loaded but template doesn't exist - this is a real error
                    List<ReferenceLocation> locations = referenceLocations.get(template);
                    if (locations != null) {
                        brokenDetails.put(template, new ArrayList<>(locations));
                    }
                }
            }
        }

        return brokenDetails;
    }

    /**
     * Get references that require unloaded files
     */
    public Map<String, Set<String>> getReferencesRequiringUnloadedFiles() {
        Map<String, Set<String>> unloadedFileRefs = new HashMap<>();

        for (String template : templateReferences.keySet()) {
            if (!templateDefinitions.containsKey(template)) {
                String targetFile = determineTargetFileForTemplate(template);
                if (targetFile != null && !fileDefinitions.containsKey(targetFile)) {
                    // Target file is not loaded - this requires loading the file
                    unloadedFileRefs.computeIfAbsent(targetFile, k -> new HashSet<>()).add(template);
                }
            }
        }

        return unloadedFileRefs;
    }

    /**
     * Determine which file a template should be defined in based on naming patterns
     */
    private String determineTargetFileForTemplate(String templateName) {
        if (templateName == null) return null;

        String lower = templateName.toLowerCase();

        if (lower.startsWith("~/")) {
            lower = lower.substring(2);
        }

        if (lower.contains("weapondescriptor_") || lower.contains("weapon_") ||
            lower.startsWith("gfx/weapon/")) {
            return "WeaponDescriptor";
        }

        if (lower.contains("ammunition_") || lower.contains("ammo_")) {
            if (lower.contains("missile")) {
                return "AmmunitionMissiles";
            }
            return "Ammunition";
        }

        if (lower.contains("depiction_") || lower.contains("gfx_") || lower.startsWith("gfx/")) {
            if (lower.contains("infantry")) {
                return "GeneratedInfantryDepiction";
            }
            return "NdfDepictionList";
        }

        if (lower.contains("effect_") || lower.contains("explosion_")) {
            return "EffectDescriptor";
        }

        if (lower.contains("projectile_") || lower.contains("ballistic_")) {
            return "ProjectileDescriptor";
        }

        if (lower.contains("sound_") || lower.contains("audio_")) {
            return "SoundDescriptor";
        }

        if (lower.contains("missile_")) {
            return "MissileDescriptors";
        }

        if (lower.contains("building_")) {
            return "BuildingDescriptors";
        }

        if (lower.contains("cadavre_") || lower.contains("unitcadavre_")) {
            return "UniteCadavreDescriptor";
        }

        if (lower.contains("showroom_")) {
            return "ShowRoomUnits";
        }

        if (lower.contains("orderavailability_")) {
            return "OrderAvailability_Tactic";
        }

        if (lower.contains("experience_")) {
            return "ExperienceLevels";
        }

        return null;
    }
    
    /**
     * Get all files that reference a specific template
     */
    public Set<String> getFilesReferencingTemplate(String templateName) {
        Set<String> files = templateReferences.get(templateName);
        return files != null ? new HashSet<>(files) : new HashSet<>();
    }
    
    /**
     * Get the file that defines a specific template
     */
    public String getFileDefiningTemplate(String templateName) {
        return templateDefinitions.get(templateName);
    }
    
    /**
     * Get all templates defined in a specific file
     */
    public Set<String> getTemplatesDefinedInFile(String fileName) {
        Set<String> templates = fileDefinitions.get(fileName);
        return templates != null ? new HashSet<>(templates) : new HashSet<>();
    }
    
    /**
     * Get all reference locations for a specific template
     */
    public List<ReferenceLocation> getReferenceLocations(String templateName) {
        List<ReferenceLocation> locations = referenceLocations.get(templateName);
        return locations != null ? new ArrayList<>(locations) : new ArrayList<>();
    }
    
    /**
     * Validate all cross-file references
     */
    public CrossFileValidationResult validateAllReferences() {
        Map<String, List<ReferenceLocation>> brokenRefs = getBrokenReferenceDetails();
        Map<String, Set<String>> unloadedFileRefs = getReferencesRequiringUnloadedFiles();
        List<String> missingFiles = new ArrayList<>();

        // Check for missing files that are referenced
        for (String template : templateReferences.keySet()) {
            String definingFile = templateDefinitions.get(template);
            if (definingFile != null && !fileDefinitions.containsKey(definingFile)) {
                missingFiles.add(definingFile);
            }
        }

        return new CrossFileValidationResult(brokenRefs, unloadedFileRefs, missingFiles);
    }
    
    /**
     * Validation result container
     */
    public static class CrossFileValidationResult {
        private final Map<String, List<ReferenceLocation>> brokenReferences;
        private final Map<String, Set<String>> unloadedFileReferences;
        private final List<String> missingFiles;

        public CrossFileValidationResult(Map<String, List<ReferenceLocation>> brokenReferences,
                                       Map<String, Set<String>> unloadedFileReferences,
                                       List<String> missingFiles) {
            this.brokenReferences = brokenReferences;
            this.unloadedFileReferences = unloadedFileReferences;
            this.missingFiles = missingFiles;
        }

        public Map<String, List<ReferenceLocation>> getBrokenReferences() { return brokenReferences; }
        public Map<String, Set<String>> getUnloadedFileReferences() { return unloadedFileReferences; }
        public List<String> getMissingFiles() { return missingFiles; }

        public boolean hasIssues() {
            return !brokenReferences.isEmpty() || !missingFiles.isEmpty();
        }

        public boolean hasUnloadedFileReferences() {
            return !unloadedFileReferences.isEmpty();
        }

        public int getTotalBrokenReferences() {
            return brokenReferences.values().stream().mapToInt(List::size).sum();
        }

        public int getTotalUnloadedFileReferences() {
            return unloadedFileReferences.values().stream().mapToInt(Set::size).sum();
        }
    }
    
    /**
     * Get comprehensive statistics
     */
    public String getStatistics() {
        int totalTemplates = templateDefinitions.size();
        int totalReferences = templateReferences.values().stream().mapToInt(Set::size).sum();
        int brokenReferences = findBrokenReferences().size();
        int unloadedFileReferences = getReferencesRequiringUnloadedFiles().values().stream().mapToInt(Set::size).sum();
        int totalFiles = fileDefinitions.size();

        return String.format("Cross-File References: %d templates, %d references across %d files, %d broken, %d requiring unloaded files",
                           totalTemplates, totalReferences, totalFiles, brokenReferences, unloadedFileReferences);
    }
}
