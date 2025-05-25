package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.PropertyUpdater.ModificationType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks all modifications made to NDF files during a session.
 * This class maintains a complete history of changes for creating mod profiles.
 */
public class ModificationTracker {
    private final List<ModificationRecord> modifications;
    private final Map<String, ModificationRecord> latestModifications; // Key: unit:path, Value: latest modification
    private final List<ModificationListener> listeners;

    /**
     * Interface for listening to modification events
     */
    public interface ModificationListener {
        void onModificationAdded(ModificationRecord record);
        void onModificationsCleared();
    }

    public ModificationTracker() {
        this.modifications = new CopyOnWriteArrayList<>();
        this.latestModifications = new LinkedHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Records a modification
     */
    public void recordModification(String unitName, String propertyPath,
                                 NDFValue oldValue, NDFValue newValue) {
        recordModification(unitName, propertyPath, oldValue, newValue, ModificationType.SET, null);
    }

    /**
     * Records a modification with type and details
     */
    public void recordModification(String unitName, String propertyPath,
                                 NDFValue oldValue, NDFValue newValue,
                                 ModificationType modificationType, String modificationDetails) {
        ModificationRecord record = new ModificationRecord(
            unitName, propertyPath, oldValue, newValue, modificationType, modificationDetails);

        if (record.isValid()) {
            modifications.add(record);
            latestModifications.put(record.getKey(), record);

            // Notify listeners
            for (ModificationListener listener : listeners) {
                listener.onModificationAdded(record);
            }
        }
    }

    /**
     * Gets all modifications in chronological order
     */
    public List<ModificationRecord> getAllModifications() {
        return new ArrayList<>(modifications);
    }

    /**
     * Gets the latest modification for each unique unit/property combination
     */
    public List<ModificationRecord> getLatestModifications() {
        return new ArrayList<>(latestModifications.values());
    }

    /**
     * Gets modifications for a specific unit
     */
    public List<ModificationRecord> getModificationsForUnit(String unitName) {
        List<ModificationRecord> unitMods = new ArrayList<>();
        for (ModificationRecord record : modifications) {
            if (unitName.equals(record.getUnitName())) {
                unitMods.add(record);
            }
        }
        return unitMods;
    }

    /**
     * Gets modifications for a specific property path across all units
     */
    public List<ModificationRecord> getModificationsForProperty(String propertyPath) {
        List<ModificationRecord> propertyMods = new ArrayList<>();
        for (ModificationRecord record : modifications) {
            if (propertyPath.equals(record.getPropertyPath())) {
                propertyMods.add(record);
            }
        }
        return propertyMods;
    }

    /**
     * Checks if any modifications have been made
     */
    public boolean hasModifications() {
        return !modifications.isEmpty();
    }

    /**
     * Gets the total number of modifications
     */
    public int getModificationCount() {
        return modifications.size();
    }

    /**
     * Gets the number of unique properties modified
     */
    public int getUniqueModificationCount() {
        return latestModifications.size();
    }

    /**
     * Clears all modifications
     */
    public void clearModifications() {
        modifications.clear();
        latestModifications.clear();

        // Notify listeners
        for (ModificationListener listener : listeners) {
            listener.onModificationsCleared();
        }
    }

    /**
     * Removes a specific modification record
     */
    public boolean removeModification(ModificationRecord record) {
        boolean removed = modifications.remove(record);
        if (removed) {
            // Update latest modifications map
            String key = record.getKey();
            if (latestModifications.get(key) == record) {
                // Find the previous latest modification for this key
                ModificationRecord latest = null;
                for (int i = modifications.size() - 1; i >= 0; i--) {
                    ModificationRecord mod = modifications.get(i);
                    if (key.equals(mod.getKey())) {
                        latest = mod;
                        break;
                    }
                }

                if (latest != null) {
                    latestModifications.put(key, latest);
                } else {
                    latestModifications.remove(key);
                }
            }
        }
        return removed;
    }

    /**
     * Gets statistics about the modifications
     */
    public ModificationStats getStats() {
        Set<String> uniqueUnits = new HashSet<>();
        Set<String> uniqueProperties = new HashSet<>();
        Map<ModificationType, Integer> typeCount = new HashMap<>();

        for (ModificationRecord record : modifications) {
            uniqueUnits.add(record.getUnitName());

            // Normalize property paths to count property types, not specific indices
            String normalizedPath = normalizePropertyPath(record.getPropertyPath());
            uniqueProperties.add(normalizedPath);

            typeCount.merge(record.getModificationType(), 1, Integer::sum);
        }

        return new ModificationStats(
            modifications.size(),
            uniqueUnits.size(),
            uniqueProperties.size(),
            typeCount
        );
    }

    /**
     * Normalizes a property path by converting specific array indices back to wildcards
     * This ensures that ModulesDescriptors[0].GameplayBehavior and ModulesDescriptors[15].GameplayBehavior
     * are counted as the same property type
     */
    private String normalizePropertyPath(String propertyPath) {
        // Convert specific array indices back to wildcards for counting
        // ModulesDescriptors[15].GameplayBehavior -> ModulesDescriptors[*].GameplayBehavior
        return propertyPath.replaceAll("\\[\\d+\\]", "[*]");
    }

    /**
     * Adds a modification listener
     */
    public void addListener(ModificationListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a modification listener
     */
    public void removeListener(ModificationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Statistics about modifications
     */
    public static class ModificationStats {
        public final int totalModifications;
        public final int uniqueUnits;
        public final int uniqueProperties;
        public final Map<ModificationType, Integer> modificationsByType;

        public ModificationStats(int totalModifications, int uniqueUnits,
                               int uniqueProperties, Map<ModificationType, Integer> modificationsByType) {
            this.totalModifications = totalModifications;
            this.uniqueUnits = uniqueUnits;
            this.uniqueProperties = uniqueProperties;
            this.modificationsByType = new HashMap<>(modificationsByType);
        }

        @Override
        public String toString() {
            return String.format("ModificationStats{total=%d, units=%d, properties=%d, types=%s}",
                               totalModifications, uniqueUnits, uniqueProperties, modificationsByType);
        }
    }
}
