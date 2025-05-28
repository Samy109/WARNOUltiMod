package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.PropertyUpdater.ModificationType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModificationTracker {
    private final List<ModificationRecord> modifications;
    private final Map<String, ModificationRecord> latestModifications;
    private final List<ModificationListener> listeners;

    public interface ModificationListener {
        void onModificationAdded(ModificationRecord record);
        void onModificationsCleared();
    }

    public ModificationTracker() {
        this.modifications = new CopyOnWriteArrayList<>();
        this.latestModifications = new LinkedHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }


    public void recordModification(String unitName, String propertyPath,
                                 NDFValue oldValue, NDFValue newValue) {
        recordModification(unitName, propertyPath, oldValue, newValue, ModificationType.SET, null);
    }

    public void recordModification(String unitName, String propertyPath,
                                 NDFValue oldValue, NDFValue newValue,
                                 ModificationType modificationType, String modificationDetails) {
        ModificationRecord record = new ModificationRecord(
            unitName, propertyPath, oldValue, newValue, modificationType, modificationDetails);

        if (record.isValid()) {
            modifications.add(record);
            latestModifications.put(record.getKey(), record);

            for (ModificationListener listener : listeners) {
                listener.onModificationAdded(record);
            }
        }
    }

    public List<ModificationRecord> getAllModifications() {
        return new ArrayList<>(modifications);
    }


    public List<ModificationRecord> getLatestModifications() {
        return new ArrayList<>(latestModifications.values());
    }


    public List<ModificationRecord> getModificationsForUnit(String unitName) {
        List<ModificationRecord> unitMods = new ArrayList<>();
        for (ModificationRecord record : modifications) {
            if (unitName.equals(record.getUnitName())) {
                unitMods.add(record);
            }
        }
        return unitMods;
    }


    public List<ModificationRecord> getModificationsForProperty(String propertyPath) {
        List<ModificationRecord> propertyMods = new ArrayList<>();
        for (ModificationRecord record : modifications) {
            if (propertyPath.equals(record.getPropertyPath())) {
                propertyMods.add(record);
            }
        }
        return propertyMods;
    }


    public boolean hasModifications() {
        return !modifications.isEmpty();
    }


    public int getModificationCount() {
        return modifications.size();
    }


    public int getUniqueModificationCount() {
        return latestModifications.size();
    }


    public boolean hasModificationsForObject(NDFValue.ObjectValue object) {
        if (object == null) return false;
        String unitName = object.getInstanceName();
        if (unitName == null) return false;

        for (ModificationRecord record : modifications) {
            if (unitName.equals(record.getUnitName())) {
                return true;
            }
        }
        return false;
    }


    public boolean hasModificationForProperty(String unitName, String propertyPath) {
        if (unitName == null || propertyPath == null) return false;

        // Normalize the property path to handle different array index formats
        String normalizedPath = normalizeArrayIndexFormat(propertyPath);

        for (ModificationRecord record : modifications) {
            if (unitName.equals(record.getUnitName())) {
                String recordPath = normalizeArrayIndexFormat(record.getPropertyPath());
                if (normalizedPath.equals(recordPath)) {
                    return true;
                }
            }
        }
        return false;
    }


    public boolean hasModificationForPropertyOrChildren(String unitName, String propertyPath) {
        if (unitName == null || propertyPath == null) return false;

        // Normalize the property path to handle different array index formats
        String normalizedPath = normalizeArrayIndexFormat(propertyPath);

        for (ModificationRecord record : modifications) {
            if (unitName.equals(record.getUnitName())) {
                String recordPath = normalizeArrayIndexFormat(record.getPropertyPath());
                // Check if the record path starts with the given path (child property)
                if (recordPath.startsWith(normalizedPath)) {
                    return true;
                }
            }
        }
        return false;
    }


    private String normalizeArrayIndexFormat(String path) {
        if (path == null) return null;
        // Convert "Value.[1].MeshDescriptor" to "Value[1].MeshDescriptor" for consistent comparison
        return path.replace(".[", "[").replace("].", "]");
    }


    public void clearModifications() {
        modifications.clear();
        latestModifications.clear();

        // Notify listeners
        for (ModificationListener listener : listeners) {
            listener.onModificationsCleared();
        }
    }


    public boolean removeModification(ModificationRecord record) {
        boolean removed = modifications.remove(record);
        if (removed) {
            String key = record.getKey();
            if (latestModifications.get(key) == record) {
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


    private String normalizePropertyPath(String propertyPath) {
        // Convert specific array indices back to wildcards for counting
        // ModulesDescriptors[15].GameplayBehavior -> ModulesDescriptors[*].GameplayBehavior
        return propertyPath.replaceAll("\\[\\d+\\]", "[*]");
    }


    public void addListener(ModificationListener listener) {
        listeners.add(listener);
    }


    public void removeListener(ModificationListener listener) {
        listeners.remove(listener);
    }


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
