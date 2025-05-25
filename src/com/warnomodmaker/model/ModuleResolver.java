package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves module paths using type-based identifiers instead of fragile array indices.
 * This provides a robust way to access properties across different unit types that may
 * have different module arrangements.
 */
public class ModuleResolver {

    /**
     * Resolves a property path using module type identifiers instead of array indices.
     *
     * Examples:
     * - "TBaseDamageModuleDescriptor.MaxPhysicalDamages" instead of "ModulesDescriptors[14].MaxPhysicalDamages"
     * - "TDamageModuleDescriptor.BlindageProperties.ExplosiveReactiveArmor" instead of "ModulesDescriptors[15].BlindageProperties.ExplosiveReactiveArmor"
     *
     * @param unit The unit to search in
     * @param typePath The path using module type identifiers
     * @return The resolved property value, or null if not found
     */
    public static NDFValue resolvePropertyByType(ObjectValue unit, String typePath) {
        if (unit == null || typePath == null || typePath.isEmpty()) {
            return null;
        }

        String[] pathParts = typePath.split("\\.", 2);
        if (pathParts.length < 2) {
            return null; // Need at least ModuleType.Property
        }

        String moduleType = pathParts[0];
        String remainingPath = pathParts[1];

        // Find the module by type
        ObjectValue targetModule = findModuleByType(unit, moduleType);
        if (targetModule == null) {
            return null;
        }

        // Use PropertyUpdater to get the remaining path from the module
        return PropertyUpdater.getPropertyValue(targetModule, remainingPath);
    }

    /**
     * Updates a property using module type identifiers instead of array indices.
     *
     * @param unit The unit to update
     * @param typePath The path using module type identifiers
     * @param newValue The new value to set
     * @param tracker Optional modification tracker
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updatePropertyByType(ObjectValue unit, String typePath, NDFValue newValue, ModificationTracker tracker) {
        if (unit == null || typePath == null || typePath.isEmpty()) {
            return false;
        }

        String[] pathParts = typePath.split("\\.", 2);
        if (pathParts.length < 2) {
            return false; // Need at least ModuleType.Property
        }

        String moduleType = pathParts[0];
        String remainingPath = pathParts[1];

        // Find the module by type
        ObjectValue targetModule = findModuleByType(unit, moduleType);
        if (targetModule == null) {
            return false;
        }

        // Use PropertyUpdater to update the remaining path in the module
        return PropertyUpdater.updateProperty(targetModule, remainingPath, newValue, tracker);
    }

    /**
     * Updates a numeric property using module type identifiers.
     *
     * @param unit The unit to update
     * @param typePath The path using module type identifiers
     * @param modificationType The type of modification to apply
     * @param value The modification value
     * @param tracker Optional modification tracker
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateNumericPropertyByType(ObjectValue unit, String typePath,
                                                    PropertyUpdater.ModificationType modificationType,
                                                    double value, ModificationTracker tracker) {
        if (unit == null || typePath == null || typePath.isEmpty()) {
            return false;
        }

        String[] pathParts = typePath.split("\\.", 2);
        if (pathParts.length < 2) {
            return false; // Need at least ModuleType.Property
        }

        String moduleType = pathParts[0];
        String remainingPath = pathParts[1];

        // Find the module by type
        ObjectValue targetModule = findModuleByType(unit, moduleType);
        if (targetModule == null) {
            return false;
        }

        // Use PropertyUpdater to update the remaining path in the module
        return PropertyUpdater.updateNumericProperty(targetModule, remainingPath, modificationType, value, tracker);
    }

    /**
     * Checks if a property exists using module type identifiers.
     *
     * @param unit The unit to check
     * @param typePath The path using module type identifiers
     * @return true if the property exists, false otherwise
     */
    public static boolean hasPropertyByType(ObjectValue unit, String typePath) {
        return resolvePropertyByType(unit, typePath) != null;
    }

    /**
     * Finds a module in the ModulesDescriptors array by its type name.
     *
     * @param unit The unit to search in
     * @param moduleType The module type to find (e.g., "TBaseDamageModuleDescriptor")
     * @return The module object, or null if not found
     */
    public static ObjectValue findModuleByType(ObjectValue unit, String moduleType) {
        if (unit == null || moduleType == null) {
            return null;
        }

        // Get the ModulesDescriptors array
        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return null;
        }

        ArrayValue modulesArray = (ArrayValue) modulesValue;

        // Search through the array for a module with the matching type
        for (NDFValue element : modulesArray.getElements()) {
            if (element instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) element;
                if (moduleType.equals(module.getTypeName())) {
                    return module;
                }
            }
        }

        return null;
    }

    /**
     * Gets a map of all available module types in a unit.
     *
     * @param unit The unit to analyze
     * @return A map of module type -> module object
     */
    public static Map<String, ObjectValue> getAvailableModules(ObjectValue unit) {
        Map<String, ObjectValue> modules = new HashMap<>();

        if (unit == null) {
            return modules;
        }

        // Get the ModulesDescriptors array
        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return modules;
        }

        ArrayValue modulesArray = (ArrayValue) modulesValue;

        // Collect all modules by type
        for (NDFValue element : modulesArray.getElements()) {
            if (element instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) element;
                String moduleType = module.getTypeName();
                if (moduleType != null && !moduleType.isEmpty()) {
                    modules.put(moduleType, module);
                }
            }
        }

        return modules;
    }

    /**
     * Converts an array-index-based path to a type-based path.
     *
     * @param unit The unit to analyze
     * @param indexPath The path with array indices (e.g., "ModulesDescriptors[15].BlindageProperties.ExplosiveReactiveArmor")
     * @return The type-based path (e.g., "TDamageModuleDescriptor.BlindageProperties.ExplosiveReactiveArmor"), or null if conversion fails
     */
    public static String convertIndexPathToTypePath(ObjectValue unit, String indexPath) {
        if (unit == null || indexPath == null || !indexPath.startsWith("ModulesDescriptors[")) {
            return null;
        }

        // Extract the index and remaining path
        int bracketEnd = indexPath.indexOf(']');
        if (bracketEnd == -1) {
            return null;
        }

        String indexStr = indexPath.substring("ModulesDescriptors[".length(), bracketEnd);
        String remainingPath = indexPath.substring(bracketEnd + 1);

        // Remove leading dot if present
        if (remainingPath.startsWith(".")) {
            remainingPath = remainingPath.substring(1);
        }

        try {
            int index = Integer.parseInt(indexStr);

            // Get the module at that index
            NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
            if (!(modulesValue instanceof ArrayValue)) {
                return null;
            }

            ArrayValue modulesArray = (ArrayValue) modulesValue;
            if (index < 0 || index >= modulesArray.getElements().size()) {
                return null;
            }

            NDFValue moduleValue = modulesArray.getElements().get(index);
            if (!(moduleValue instanceof ObjectValue)) {
                return null;
            }

            ObjectValue module = (ObjectValue) moduleValue;
            String moduleType = module.getTypeName();

            if (moduleType == null || moduleType.isEmpty()) {
                return null;
            }

            // Construct the type-based path
            if (remainingPath.isEmpty()) {
                return moduleType;
            } else {
                return moduleType + "." + remainingPath;
            }

        } catch (NumberFormatException e) {
            return null;
        }
    }
}
