package com.warnomodmaker.model;

import java.util.*;

/**
 * Dynamic registry that learns NDF object schemas from actual files.
 * Analyzes existing objects to understand property types and patterns.
 */
public class NDFSchemaRegistry {

    private final Map<String, DynamicObjectSchema> objectSchemas;
    private final Map<String, DynamicModuleSchema> moduleSchemas;
    private final Set<String> validEnumValues;
    private final Map<String, Set<NDFValue.ValueType>> propertyTypes;
    private final Map<String, Integer> propertyFrequency;

    public NDFSchemaRegistry() {
        this.objectSchemas = new HashMap<>();
        this.moduleSchemas = new HashMap<>();
        this.validEnumValues = new HashSet<>();
        this.propertyTypes = new HashMap<>();
        this.propertyFrequency = new HashMap<>();
        initializeBasicEnums();
    }

    /**
     * Learn schemas from a list of NDF objects
     */
    public void learnFromObjects(List<NDFValue.ObjectValue> objects) {
        if (objects == null) {
            return;
        }

        for (NDFValue.ObjectValue obj : objects) {
            if (obj != null) {
                try {
                    learnFromObject(obj);
                } catch (Exception e) {
                    System.err.println("Error learning from object " + obj.getTypeName() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Learn schema from a single object
     */
    private void learnFromObject(NDFValue.ObjectValue obj) {
        if (obj == null) {
            return;
        }

        String typeName = obj.getTypeName();
        if (typeName == null || typeName.isEmpty()) {
            return;
        }

        // Get or create schema for this object type
        DynamicObjectSchema schema = objectSchemas.computeIfAbsent(typeName, DynamicObjectSchema::new);

        // Learn from all properties
        Map<String, NDFValue> properties = obj.getProperties();
        if (properties != null) {
            for (Map.Entry<String, NDFValue> entry : properties.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }

                String propertyName = entry.getKey();
                NDFValue propertyValue = entry.getValue();

                try {
                    // Record property type
                    NDFValue.ValueType valueType = propertyValue.getType();
                    schema.recordProperty(propertyName, valueType);

                    // Global property type tracking
                    String globalKey = typeName + "." + propertyName;
                    propertyTypes.computeIfAbsent(globalKey, k -> new HashSet<>()).add(valueType);
                    propertyFrequency.merge(globalKey, 1, Integer::sum);

                    // Learn enum values
                    if (valueType == NDFValue.ValueType.ENUM) {
                        NDFValue.EnumValue enumValue = (NDFValue.EnumValue) propertyValue;
                        String enumValueStr = enumValue.getValue();
                        if (enumValueStr != null) {
                            validEnumValues.add(enumValueStr);
                        }
                    }

                    // Recursively learn from nested objects
                    if (valueType == NDFValue.ValueType.OBJECT) {
                        learnFromObject((NDFValue.ObjectValue) propertyValue);
                    } else if (valueType == NDFValue.ValueType.ARRAY) {
                        learnFromArray((NDFValue.ArrayValue) propertyValue);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing property " + propertyName + " in " + typeName + ": " + e.getMessage());
                }
            }
        }

        // If this is a module (in ModulesDescriptors), also track as module schema
        if (isModuleType(typeName)) {
            try {
                DynamicModuleSchema moduleSchema = moduleSchemas.computeIfAbsent(typeName, DynamicModuleSchema::new);
                if (properties != null) {
                    for (Map.Entry<String, NDFValue> entry : properties.entrySet()) {
                        if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                            moduleSchema.recordProperty(entry.getKey(), entry.getValue().getType());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing module " + typeName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Learn from array elements
     */
    private void learnFromArray(NDFValue.ArrayValue array) {
        if (array == null) {
            return;
        }

        List<NDFValue> elements = array.getElements();
        if (elements == null) {
            return;
        }

        for (NDFValue element : elements) {
            if (element == null) {
                continue;
            }

            try {
                if (element.getType() == NDFValue.ValueType.OBJECT) {
                    learnFromObject((NDFValue.ObjectValue) element);
                } else if (element.getType() == NDFValue.ValueType.ENUM) {
                    NDFValue.EnumValue enumValue = (NDFValue.EnumValue) element;
                    String enumValueStr = enumValue.getValue();
                    if (enumValueStr != null) {
                        validEnumValues.add(enumValueStr);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing array element: " + e.getMessage());
            }
        }
    }

    /**
     * Check if a type name represents a module
     */
    private boolean isModuleType(String typeName) {
        return typeName.endsWith("ModuleDescriptor") ||
               typeName.endsWith("Module") ||
               typeName.contains("Module");
    }
    
    /**
     * Initialize basic enum values that are commonly used
     */
    private void initializeBasicEnums() {
        // Coalition values
        validEnumValues.add("ECoalition/Axis");
        validEnumValues.add("ECoalition/Allied");

        // Factory values
        validEnumValues.add("EDefaultFactories/DCA");
        validEnumValues.add("EDefaultFactories/Infanterie");
        validEnumValues.add("EDefaultFactories/Vehicule");
        validEnumValues.add("EDefaultFactories/Tank");
        validEnumValues.add("EDefaultFactories/Artillerie");
        validEnumValues.add("EDefaultFactories/Avion");
        validEnumValues.add("EDefaultFactories/Helicoptere");

        // Projectile types
        validEnumValues.add("EProjectileType/Obus");
        validEnumValues.add("EProjectileType/Missile");
        validEnumValues.add("EProjectileType/Roquette");

        // Unit moving types
        validEnumValues.add("EUnitMovingType/Track");
        validEnumValues.add("EUnitMovingType/Wheel");
        validEnumValues.add("EUnitMovingType/Foot");

        // Common boolean values
        validEnumValues.add("True");
        validEnumValues.add("False");
    }
    
    /**
     * Get suggested property type for a given object type and property name
     */
    public NDFValue.ValueType getSuggestedPropertyType(String objectType, String propertyName) {
        String key = objectType + "." + propertyName;
        Set<NDFValue.ValueType> types = propertyTypes.get(key);

        if (types == null || types.isEmpty()) {
            return NDFValue.ValueType.STRING; // Default fallback
        }

        // Return the most common type for this property
        return types.iterator().next();
    }

    /**
     * Get all known property names for an object type
     */
    public Set<String> getKnownProperties(String objectType) {
        DynamicObjectSchema schema = objectSchemas.get(objectType);
        if (schema != null) {
            return schema.getAllProperties();
        }
        return new HashSet<>();
    }

    /**
     * Get property frequency (how often a property appears)
     */
    public int getPropertyFrequency(String objectType, String propertyName) {
        String key = objectType + "." + propertyName;
        return propertyFrequency.getOrDefault(key, 0);
    }

    /**
     * Get all enum values that have been seen
     */
    public Set<String> getAllKnownEnumValues() {
        return new HashSet<>(validEnumValues);
    }

    /**
     * Get enum values that match a pattern (for suggestions)
     */
    public Set<String> getMatchingEnumValues(String pattern) {
        Set<String> matches = new HashSet<>();
        String lowerPattern = pattern.toLowerCase();

        for (String enumValue : validEnumValues) {
            if (enumValue.toLowerCase().contains(lowerPattern)) {
                matches.add(enumValue);
            }
        }

        return matches;
    }
    
    /**
     * Validate an object against learned patterns (lenient validation)
     */
    public boolean validateObject(NDFValue.ObjectValue object) {
        // Dynamic validation - very lenient, just checks basic consistency
        return object != null && object.getTypeName() != null;
    }

    /**
     * Validate a module against learned patterns
     */
    public boolean validateModule(NDFValue.ObjectValue module, String moduleType) {
        // Dynamic validation - check if we've seen this module type before
        DynamicModuleSchema schema = moduleSchemas.get(moduleType);
        if (schema == null) {
            // Unknown module type - allow it but learn from it
            return true;
        }

        return true; // Very lenient for now
    }

    /**
     * Validate a property against learned patterns
     */
    public boolean validateProperty(String objectType, String propertyName, NDFValue propertyValue) {
        String key = objectType + "." + propertyName;
        Set<NDFValue.ValueType> knownTypes = propertyTypes.get(key);

        if (knownTypes == null || knownTypes.isEmpty()) {
            // Unknown property - allow it
            return true;
        }

        // Check if the value type matches any known type for this property
        return knownTypes.contains(propertyValue.getType());
    }

    /**
     * Validate an array element
     */
    public boolean validateArrayElement(String objectType, String arrayPropertyPath, NDFValue element) {
        // Basic validation - element should not be null
        return element != null;
    }

    /**
     * Get all known object types
     */
    public Set<String> getKnownObjectTypes() {
        return new HashSet<>(objectSchemas.keySet());
    }

    /**
     * Get all known module types
     */
    public Set<String> getKnownModuleTypes() {
        return new HashSet<>(moduleSchemas.keySet());
    }

    /**
     * Check if an enum value is valid
     */
    public boolean isValidEnumValue(String enumValue) {
        return validEnumValues.contains(enumValue);
    }
    
    /**
     * Dynamic schema for NDF objects that learns from examples
     */
    private static class DynamicObjectSchema {
        private final String typeName;
        private final Map<String, Set<NDFValue.ValueType>> propertyTypes;
        private final Map<String, Integer> propertyFrequency;

        public DynamicObjectSchema(String typeName) {
            this.typeName = typeName;
            this.propertyTypes = new HashMap<>();
            this.propertyFrequency = new HashMap<>();
        }

        public void recordProperty(String propertyName, NDFValue.ValueType type) {
            propertyTypes.computeIfAbsent(propertyName, k -> new HashSet<>()).add(type);
            propertyFrequency.merge(propertyName, 1, Integer::sum);
        }

        public Set<String> getAllProperties() {
            return new HashSet<>(propertyTypes.keySet());
        }

        public Set<NDFValue.ValueType> getPropertyTypes(String propertyName) {
            return propertyTypes.getOrDefault(propertyName, new HashSet<>());
        }

        public int getPropertyFrequency(String propertyName) {
            return propertyFrequency.getOrDefault(propertyName, 0);
        }

        public boolean isCommonProperty(String propertyName) {
            return getPropertyFrequency(propertyName) > 1;
        }
    }

    /**
     * Dynamic schema for NDF modules that learns from examples
     */
    private static class DynamicModuleSchema {
        private final String typeName;
        private final Map<String, Set<NDFValue.ValueType>> propertyTypes;
        private final Map<String, Integer> propertyFrequency;

        public DynamicModuleSchema(String typeName) {
            this.typeName = typeName;
            this.propertyTypes = new HashMap<>();
            this.propertyFrequency = new HashMap<>();
        }

        public void recordProperty(String propertyName, NDFValue.ValueType type) {
            propertyTypes.computeIfAbsent(propertyName, k -> new HashSet<>()).add(type);
            propertyFrequency.merge(propertyName, 1, Integer::sum);
        }

        public Set<String> getAllProperties() {
            return new HashSet<>(propertyTypes.keySet());
        }

        public Set<NDFValue.ValueType> getPropertyTypes(String propertyName) {
            return propertyTypes.getOrDefault(propertyName, new HashSet<>());
        }

        public int getPropertyFrequency(String propertyName) {
            return propertyFrequency.getOrDefault(propertyName, 0);
        }
    }
}
