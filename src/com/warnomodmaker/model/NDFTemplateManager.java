package com.warnomodmaker.model;

import java.util.*;

/**
 * Dynamic template manager that learns from existing NDF files to create comprehensive templates.
 * Automatically generates templates based on actual file structure and common patterns.
 */
public class NDFTemplateManager {

    private final Map<String, NDFValue.ObjectValue> objectTemplates;
    private final Map<String, NDFValue.ObjectValue> moduleTemplates;
    private final Map<String, Map<String, Object>> propertyDefaults;
    private final Map<String, Integer> propertyFrequency;
    private final Set<String> topLevelObjectTypes;
    private final Set<String> nestedObjectTypes;

    public NDFTemplateManager() {
        this.objectTemplates = new HashMap<>();
        this.moduleTemplates = new HashMap<>();
        this.propertyDefaults = new HashMap<>();
        this.propertyFrequency = new HashMap<>();
        this.topLevelObjectTypes = new HashSet<>();
        this.nestedObjectTypes = new HashSet<>();
    }

    /**
     * Learn templates from existing objects in the file
     */
    public void learnFromObjects(List<NDFValue.ObjectValue> objects) {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        // First pass: identify top-level object types
        for (NDFValue.ObjectValue obj : objects) {
            if (obj != null && obj.getTypeName() != null) {
                topLevelObjectTypes.add(obj.getTypeName());
            }
        }

        // Second pass: analyze all objects to understand patterns
        for (NDFValue.ObjectValue obj : objects) {
            if (obj != null) {
                analyzeObjectForTemplate(obj, true); // true = is top level
            }
        }

        // Generate templates based on learned patterns
        generateDynamicTemplates();
    }

    /**
     * Analyze an object to learn template patterns
     */
    private void analyzeObjectForTemplate(NDFValue.ObjectValue obj, boolean isTopLevel) {
        String typeName = obj.getTypeName();
        if (typeName == null) return;

        // Track whether this is a nested object type
        if (!isTopLevel) {
            nestedObjectTypes.add(typeName);
        }

        // Track property patterns for this object type
        String typeKey = typeName;
        Map<String, Object> defaults = propertyDefaults.computeIfAbsent(typeKey, k -> new HashMap<>());

        for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
            String propertyName = entry.getKey();
            NDFValue propertyValue = entry.getValue();

            if (propertyName == null || propertyValue == null) continue;

            String propKey = typeName + "." + propertyName;
            propertyFrequency.merge(propKey, 1, Integer::sum);

            // Store common default values
            if (!defaults.containsKey(propertyName)) {
                defaults.put(propertyName, extractDefaultValue(propertyValue));
            }

            // Recursively analyze nested objects and modules
            if (propertyValue instanceof NDFValue.ObjectValue) {
                analyzeObjectForTemplate((NDFValue.ObjectValue) propertyValue, false); // false = nested
            } else if (propertyValue instanceof NDFValue.ArrayValue) {
                analyzeArrayForTemplates((NDFValue.ArrayValue) propertyValue);
            }
        }
    }

    /**
     * Analyze array elements for template patterns
     */
    private void analyzeArrayForTemplates(NDFValue.ArrayValue array) {
        for (NDFValue element : array.getElements()) {
            if (element instanceof NDFValue.ObjectValue) {
                analyzeObjectForTemplate((NDFValue.ObjectValue) element, false); // false = nested
            }
        }
    }

    /**
     * Extract a reasonable default value from an existing property
     */
    private Object extractDefaultValue(NDFValue value) {
        switch (value.getType()) {
            case STRING:
                return ((NDFValue.StringValue) value).getValue();
            case NUMBER:
                return ((NDFValue.NumberValue) value).getValue();
            case BOOLEAN:
                return ((NDFValue.BooleanValue) value).getValue();
            case ENUM:
                return ((NDFValue.EnumValue) value).getValue();
            case TEMPLATE_REF:
                return ((NDFValue.TemplateRefValue) value).getPath();
            case RESOURCE_REF:
                return ((NDFValue.ResourceRefValue) value).getPath();
            case GUID:
                return "PLACEHOLDER_GUID";
            default:
                return null;
        }
    }
    
    /**
     * Generate dynamic templates based on learned patterns
     */
    private void generateDynamicTemplates() {
        for (Map.Entry<String, Map<String, Object>> entry : propertyDefaults.entrySet()) {
            String typeName = entry.getKey();
            Map<String, Object> defaults = entry.getValue();

            // Create template for this object type
            NDFValue.ObjectValue template = createTemplateFromDefaults(typeName, defaults);

            if (isModuleType(typeName)) {
                moduleTemplates.put(typeName, template);
            } else {
                objectTemplates.put(typeName, template);
            }
        }
    }

    /**
     * Create a template object from learned default values
     */
    private NDFValue.ObjectValue createTemplateFromDefaults(String typeName, Map<String, Object> defaults) {
        NDFValue.ObjectValue template = NDFValue.createObject(typeName);

        // Add properties based on frequency (only include common properties)
        for (Map.Entry<String, Object> defaultEntry : defaults.entrySet()) {
            String propertyName = defaultEntry.getKey();
            Object defaultValue = defaultEntry.getValue();

            String propKey = typeName + "." + propertyName;
            int frequency = propertyFrequency.getOrDefault(propKey, 0);

            // Include ALL discovered properties - full depth learning with no frequency filtering
            if (frequency >= 1) {
                NDFValue ndfValue = convertToTemplateValue(defaultValue, propertyName);
                if (ndfValue != null) {
                    template.setProperty(propertyName, ndfValue);
                    template.setPropertyComma(propertyName, true);
                }
            }
        }

        return template;
    }

    /**
     * Check if a property is essential and should always be included
     */
    private boolean isEssentialProperty(String propertyName) {
        return propertyName.equals("DescriptorId") ||
               propertyName.equals("ModulesDescriptors") ||
               propertyName.equals("ClassNameForDebug") ||
               propertyName.equals("Name") ||
               propertyName.equals("TagSet") ||
               propertyName.equals("Coalition") ||
               propertyName.equals("Factory");
    }

    /**
     * Convert a default value to an appropriate NDFValue for templates
     */
    private NDFValue convertToTemplateValue(Object defaultValue, String propertyName) {
        if (defaultValue == null) {
            return null;
        }

        if (defaultValue instanceof String) {
            String strValue = (String) defaultValue;

            // Special handling for specific property types
            if (propertyName.equals("DescriptorId") || strValue.startsWith("GUID:")) {
                return NDFValue.createGuid("PLACEHOLDER_GUID");
            } else if (strValue.startsWith("~/")) {
                return NDFValue.createTemplateRef(strValue);
            } else if (strValue.startsWith("$/")) {
                return NDFValue.createResourceRef(strValue);
            } else if (strValue.contains("/") && (strValue.startsWith("E") || strValue.contains("Family"))) {
                return NDFValue.createEnum(strValue);
            } else {
                return NDFValue.createString(strValue);
            }
        } else if (defaultValue instanceof Number) {
            return NDFValue.createNumber(((Number) defaultValue).doubleValue());
        } else if (defaultValue instanceof Boolean) {
            return NDFValue.createBoolean((Boolean) defaultValue);
        }

        return NDFValue.createString(defaultValue.toString());
    }

    /**
     * Check if a type name represents a module
     * FIX: Don't classify top-level object types as modules, even if they contain "Module"
     */
    private boolean isModuleType(String typeName) {
        if (typeName == null) return false;

        // If this type appears as a top-level object, it's NOT a module
        if (topLevelObjectTypes.contains(typeName)) {
            return false;
        }

        // Otherwise, use the standard module detection logic
        return typeName.endsWith("ModuleDescriptor") ||
               typeName.endsWith("Module") ||
               typeName.contains("Module");
    }


    
    /**
     * Get template for object type (learned from file)
     */
    public NDFValue.ObjectValue getTemplate(String objectType) {
        return objectTemplates.get(objectType);
    }

    /**
     * Get template for module type (learned from file)
     */
    public NDFValue.ObjectValue getModuleTemplate(String moduleType) {
        return moduleTemplates.get(moduleType);
    }

    /**
     * Get all available object template types (discovered from file)
     * Only returns top-level object types, not nested ones
     */
    public Set<String> getAvailableObjectTypes() {
        Set<String> availableTypes = new HashSet<>();

        // Only include object types that actually appear as top-level objects
        for (String typeName : objectTemplates.keySet()) {
            if (topLevelObjectTypes.contains(typeName)) {
                availableTypes.add(typeName);
            }
        }

        return availableTypes;
    }

    /**
     * Get all available module template types (discovered from file)
     */
    public Set<String> getAvailableModuleTypes() {
        return new HashSet<>(moduleTemplates.keySet());
    }

    /**
     * Get count of top-level objects of a specific type
     */
    public int getTopLevelObjectCount(String typeName, List<NDFValue.ObjectValue> objects) {
        if (objects == null) return 0;

        int count = 0;
        for (NDFValue.ObjectValue obj : objects) {
            if (obj != null && typeName.equals(obj.getTypeName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if a type is a top-level object type
     */
    public boolean isTopLevelObjectType(String typeName) {
        return topLevelObjectTypes.contains(typeName);
    }

    /**
     * Check if a type is a nested object type
     */
    public boolean isNestedObjectType(String typeName) {
        return nestedObjectTypes.contains(typeName);
    }
    

    

    



}
