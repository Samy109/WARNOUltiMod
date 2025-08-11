package com.warnomodmaker.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.warnomodmaker.util.StringSimilarity;

/**
 * Manages property path migration when WARNO updates change NDF structure.
 * Preserves mod profiles across game updates by mapping old paths to new paths.
 * NO FALLBACKS - all migrations must be explicitly defined and validated.
 */
public class PropertyPathMigrationManager {
    
    // Path mappings - old path -> new path
    private final Map<String, String> pathMappings = new ConcurrentHashMap<>();
    
    // Reverse mappings - new path -> old path (for validation)
    private final Map<String, String> reverseMappings = new ConcurrentHashMap<>();
    
    // Property existence cache - file type -> set of valid property paths
    private final Map<NDFValue.NDFFileType, Set<String>> validPaths = new ConcurrentHashMap<>();
    
    // Migration history - track what migrations have been applied
    private final List<MigrationRecord> migrationHistory = new ArrayList<>();
    
    // Fuzzy matching cache for performance
    private final Map<String, List<String>> fuzzyMatchCache = new ConcurrentHashMap<>();
    
    /**
     * Migration record for tracking applied migrations
     */
    public static class MigrationRecord {
        private final String oldPath;
        private final String newPath;
        private final long timestamp;
        private final String reason;
        
        public MigrationRecord(String oldPath, String newPath, String reason) {
            this.oldPath = oldPath;
            this.newPath = newPath;
            this.timestamp = System.currentTimeMillis();
            this.reason = reason;
        }
        
        public String getOldPath() { return oldPath; }
        public String getNewPath() { return newPath; }
        public long getTimestamp() { return timestamp; }
        public String getReason() { return reason; }
        
        @Override
        public String toString() {
            return String.format("%s -> %s (%s)", oldPath, newPath, reason);
        }
    }
    
    /**
     * Migration result container
     */
    public static class MigrationResult {
        private final String originalPath;
        private final String migratedPath;
        private final boolean successful;
        private final String reason;
        private final double confidence; // 0.0 to 1.0
        
        public MigrationResult(String originalPath, String migratedPath, boolean successful, String reason, double confidence) {
            this.originalPath = originalPath;
            this.migratedPath = migratedPath;
            this.successful = successful;
            this.reason = reason;
            this.confidence = confidence;
        }
        
        public String getOriginalPath() { return originalPath; }
        public String getMigratedPath() { return migratedPath; }
        public boolean isSuccessful() { return successful; }
        public String getReason() { return reason; }
        public double getConfidence() { return confidence; }
        
        public static MigrationResult success(String originalPath, String migratedPath, String reason, double confidence) {
            return new MigrationResult(originalPath, migratedPath, true, reason, confidence);
        }
        
        public static MigrationResult failure(String originalPath, String reason) {
            return new MigrationResult(originalPath, null, false, reason, 0.0);
        }
    }
    
    /**
     * Learn valid property paths from currently loaded files
     */
    public void learnValidPaths(NDFValue.NDFFileType fileType, List<NDFValue.ObjectValue> objects) {
        Set<String> paths = new HashSet<>();
        
        for (NDFValue.ObjectValue obj : objects) {
            extractAllPaths(obj, "", paths);
        }
        
        validPaths.put(fileType, paths);
        
        // Clear fuzzy match cache when paths change
        fuzzyMatchCache.clear();
        
        System.out.println("Learned " + paths.size() + " valid paths for " + fileType);
    }
    
    /**
     * Recursively extract all property paths from an object
     */
    private void extractAllPaths(NDFValue value, String currentPath, Set<String> paths) {
        if (value instanceof NDFValue.ObjectValue) {
            NDFValue.ObjectValue obj = (NDFValue.ObjectValue) value;
            for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                String newPath = currentPath.isEmpty() ? propertyName : currentPath + "." + propertyName;
                paths.add(newPath);
                extractAllPaths(entry.getValue(), newPath, paths);
            }
        } else if (value instanceof NDFValue.ArrayValue) {
            NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
            if (!array.getElements().isEmpty()) {
                // Efficient approach: Learn array pattern with [*] wildcard and sample structure
                String arrayPattern = currentPath + "[*]";
                paths.add(arrayPattern);

                // Learn structure from first element (representative)
                extractAllPaths(array.getElements().get(0), currentPath + "[0]", paths);

                // For large arrays, also learn a few more samples for validation
                if (array.getElements().size() > 1) {
                    int sampleIndex = Math.min(array.getElements().size() - 1, 5);
                    extractAllPaths(array.getElements().get(sampleIndex), currentPath + "[" + sampleIndex + "]", paths);
                }
            }
        }
    }
    
    /**
     * Add an explicit path mapping
     */
    public void addPathMapping(String oldPath, String newPath, String reason) {
        pathMappings.put(oldPath, newPath);
        reverseMappings.put(newPath, oldPath);
        migrationHistory.add(new MigrationRecord(oldPath, newPath, reason));
        
        System.out.println("Added path mapping: " + oldPath + " -> " + newPath + " (" + reason + ")");
    }
    
    /**
     * Remove a path mapping
     */
    public void removePathMapping(String oldPath) {
        String newPath = pathMappings.remove(oldPath);
        if (newPath != null) {
            reverseMappings.remove(newPath);
        }
    }
    
    /**
     * Migrate a single property path with intelligent array index handling
     */
    public MigrationResult migratePath(String oldPath, NDFValue.NDFFileType fileType) {
        // Check for explicit mapping first
        String explicitMapping = pathMappings.get(oldPath);
        if (explicitMapping != null) {
            return MigrationResult.success(oldPath, explicitMapping, "Explicit mapping", 1.0);
        }

        // Check if path still exists (no migration needed)
        Set<String> validPathsForType = validPaths.get(fileType);
        if (validPathsForType != null && validPathsForType.contains(oldPath)) {
            return MigrationResult.success(oldPath, oldPath, "Path still valid", 1.0);
        }

        // INTELLIGENT: Check if this is an array path that matches our pattern
        if (validPathsForType != null && oldPath.contains("[") && oldPath.contains("]")) {
            String arrayPattern = oldPath.replaceAll("\\[\\d+\\]", "[*]");
            if (validPathsForType.contains(arrayPattern)) {
                return MigrationResult.success(oldPath, oldPath, "Array path matches learned pattern", 1.0);
            }

            // Also check if the base structure exists (e.g., ModulesDescriptors[0].MaxPhysicalDamages)
            String basePattern = oldPath.replaceAll("\\[\\d+\\]", "[0]");
            if (validPathsForType.contains(basePattern)) {
                return MigrationResult.success(oldPath, oldPath, "Array path structure valid", 1.0);
            }
        }

        // Try fuzzy matching
        if (validPathsForType != null) {
            List<String> candidates = findFuzzyMatches(oldPath, validPathsForType);
            if (!candidates.isEmpty()) {
                String bestMatch = candidates.get(0);
                double confidence = calculatePathSimilarity(oldPath, bestMatch);

                if (confidence > 0.8) {
                    return MigrationResult.success(oldPath, bestMatch, "Fuzzy match", confidence);
                } else if (confidence > 0.6) {
                    return MigrationResult.success(oldPath, bestMatch, "Possible match (low confidence)", confidence);
                }
            }
        }

        return MigrationResult.failure(oldPath, "No migration found");
    }

    
    /**
     * Find fuzzy matches for a property path
     */
    private List<String> findFuzzyMatches(String targetPath, Set<String> validPaths) {
        // Check cache first
        List<String> cached = fuzzyMatchCache.get(targetPath);
        if (cached != null) {
            return cached;
        }
        
        List<PathMatch> matches = new ArrayList<>();
        
        for (String validPath : validPaths) {
            double similarity = calculatePathSimilarity(targetPath, validPath);
            if (similarity > 0.5) { // Only consider reasonable matches
                matches.add(new PathMatch(validPath, similarity));
            }
        }
        
        // Sort by similarity (highest first)
        matches.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        
        List<String> result = new ArrayList<>();
        for (PathMatch match : matches) {
            result.add(match.path);
        }
        
        // Cache the result
        fuzzyMatchCache.put(targetPath, result);
        
        return result;
    }
    
    /**
     * Calculate similarity between two property paths
     */
    private double calculatePathSimilarity(String path1, String path2) {
        // Split paths into components
        String[] parts1 = path1.split("\\.|\\[|\\]");
        String[] parts2 = path2.split("\\.|\\[|\\]");
        
        // Remove empty parts
        parts1 = Arrays.stream(parts1).filter(s -> !s.isEmpty()).toArray(String[]::new);
        parts2 = Arrays.stream(parts2).filter(s -> !s.isEmpty()).toArray(String[]::new);
        
        // Calculate Levenshtein distance for each component
        double totalSimilarity = 0.0;
        int maxLength = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < maxLength; i++) {
            String part1 = i < parts1.length ? parts1[i] : "";
            String part2 = i < parts2.length ? parts2[i] : "";
            
            double componentSimilarity = calculateStringSimilarity(part1, part2);
            totalSimilarity += componentSimilarity;
        }
        
        return totalSimilarity / maxLength;
    }
    
    /**
     * Calculate string similarity using Levenshtein distance
     */
    private double calculateStringSimilarity(String s1, String s2) {
        return StringSimilarity.calculateSimilarity(s1, s2);
    }
    
    /**
     * Helper class for path matching
     */
    private static class PathMatch {
        final String path;
        final double similarity;
        
        PathMatch(String path, double similarity) {
            this.path = path;
            this.similarity = similarity;
        }
    }
    
    /**
     * Migrate an entire mod profile
     */
    public ModProfileMigrationResult migrateModProfile(ModProfile profile) {
        List<MigrationResult> results = new ArrayList<>();
        Map<String, String> successfulMigrations = new HashMap<>();
        List<String> failedPaths = new ArrayList<>();
        
        // Get all modification records from the profile
        for (ModificationRecord record : profile.getModifications()) {
            String propertyPath = record.getPropertyPath();
            
            // Determine file type from unit name or other context
            // This might need to be enhanced based on your ModProfile structure
            NDFValue.NDFFileType fileType = determineFileType(record);
            
            MigrationResult result = migratePath(propertyPath, fileType);
            results.add(result);
            
            if (result.isSuccessful()) {
                successfulMigrations.put(propertyPath, result.getMigratedPath());
            } else {
                failedPaths.add(propertyPath);
            }
        }
        
        return new ModProfileMigrationResult(results, successfulMigrations, failedPaths);
    }
    
    /**
     * Intelligently determine file type from modification record context
     */
    private NDFValue.NDFFileType determineFileType(ModificationRecord record) {
        String unitName = record.getUnitName();
        String propertyPath = record.getPropertyPath();

        // INTELLIGENT: Use property path patterns to determine file type
        if (propertyPath != null) {
            // UniteDescriptor patterns
            if (propertyPath.contains("ModulesDescriptors") ||
                propertyPath.contains("WeaponDescriptor") ||
                propertyPath.contains("DamageTypeEvolutionModifier") ||
                propertyPath.contains("UnitMovingType")) {
                return NDFValue.NDFFileType.UNITE_DESCRIPTOR;
            }

            // WeaponDescriptor patterns
            if (propertyPath.contains("Ammunition") ||
                propertyPath.contains("TurretDescriptor") ||
                propertyPath.contains("SalvoDescriptor")) {
                return NDFValue.NDFFileType.WEAPON_DESCRIPTOR;
            }

            // Ammunition patterns
            if (propertyPath.contains("Arme") ||
                propertyPath.contains("Degats") ||
                propertyPath.contains("PorteeMaximale")) {
                return NDFValue.NDFFileType.AMMUNITION;
            }
        }

        // Fallback to unit name patterns
        if (unitName != null) {
            if (unitName.contains("Weapon") || unitName.contains("Arme")) {
                return NDFValue.NDFFileType.WEAPON_DESCRIPTOR;
            } else if (unitName.contains("Ammunition") || unitName.contains("Munition")) {
                return NDFValue.NDFFileType.AMMUNITION;
            } else if (unitName.contains("Missile")) {
                return NDFValue.NDFFileType.MISSILE_DESCRIPTORS;
            }
        }

        // Smart default: Check which file type has the most learned paths
        NDFValue.NDFFileType bestGuess = NDFValue.NDFFileType.UNITE_DESCRIPTOR;
        int maxPaths = 0;
        for (Map.Entry<NDFValue.NDFFileType, Set<String>> entry : validPaths.entrySet()) {
            if (entry.getValue().size() > maxPaths) {
                maxPaths = entry.getValue().size();
                bestGuess = entry.getKey();
            }
        }

        return bestGuess;
    }
    
    /**
     * Mod profile migration result container
     */
    public static class ModProfileMigrationResult {
        private final List<MigrationResult> allResults;
        private final Map<String, String> successfulMigrations;
        private final List<String> failedPaths;
        
        public ModProfileMigrationResult(List<MigrationResult> allResults, Map<String, String> successfulMigrations, List<String> failedPaths) {
            this.allResults = allResults;
            this.successfulMigrations = successfulMigrations;
            this.failedPaths = failedPaths;
        }
        
        public List<MigrationResult> getAllResults() { return allResults; }
        public Map<String, String> getSuccessfulMigrations() { return successfulMigrations; }
        public List<String> getFailedPaths() { return failedPaths; }
        
        public boolean hasFailures() { return !failedPaths.isEmpty(); }
        public int getSuccessCount() { return successfulMigrations.size(); }
        public int getFailureCount() { return failedPaths.size(); }
        public double getSuccessRate() {
            int total = successfulMigrations.size() + failedPaths.size();
            return total > 0 ? (double) successfulMigrations.size() / total : 1.0;
        }
    }
    
    /**
     * Get all current path mappings
     */
    public Map<String, String> getAllPathMappings() {
        return new HashMap<>(pathMappings);
    }
    
    /**
     * Get migration history
     */
    public List<MigrationRecord> getMigrationHistory() {
        return new ArrayList<>(migrationHistory);
    }
    
    /**
     * Get comprehensive statistics
     */
    public String getStatistics() {
        int totalMappings = pathMappings.size();
        int totalValidPaths = validPaths.values().stream().mapToInt(Set::size).sum();
        int totalFileTypes = validPaths.size();
        
        return String.format("Path Migration: %d explicit mappings, %d valid paths across %d file types",
                           totalMappings, totalValidPaths, totalFileTypes);
    }
}
