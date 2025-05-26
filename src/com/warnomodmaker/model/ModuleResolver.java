package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue.*;
import java.util.HashMap;
import java.util.Map;

public class ModuleResolver {

    
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
        ObjectValue targetModule = findModuleByType(unit, moduleType);
        if (targetModule == null) {
            return null;
        }

        // Use PropertyUpdater to get the remaining path from the module
        return PropertyUpdater.getPropertyValue(targetModule, remainingPath);
    }

    
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
        ObjectValue targetModule = findModuleByType(unit, moduleType);
        if (targetModule == null) {
            return false;
        }

        // Use PropertyUpdater to update the remaining path in the module
        return PropertyUpdater.updateProperty(targetModule, remainingPath, newValue, tracker);
    }

    
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
        ObjectValue targetModule = findModuleByType(unit, moduleType);
        if (targetModule == null) {
            return false;
        }

        // Use PropertyUpdater to update the remaining path in the module
        return PropertyUpdater.updateNumericProperty(targetModule, remainingPath, modificationType, value, tracker);
    }

    
    public static boolean hasPropertyByType(ObjectValue unit, String typePath) {
        return resolvePropertyByType(unit, typePath) != null;
    }

    
    public static ObjectValue findModuleByType(ObjectValue unit, String moduleType) {
        if (unit == null || moduleType == null) {
            return null;
        }
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

    
    public static Map<String, ObjectValue> getAvailableModules(ObjectValue unit) {
        Map<String, ObjectValue> modules = new HashMap<>();

        if (unit == null) {
            return modules;
        }
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
        if (remainingPath.startsWith(".")) {
            remainingPath = remainingPath.substring(1);
        }

        try {
            int index = Integer.parseInt(indexStr);
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
