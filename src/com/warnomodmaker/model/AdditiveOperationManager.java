package com.warnomodmaker.model;

import java.util.*;

/**
 * Manages additive operations for NDF files - adding new objects, modules, properties, and array elements
 * while maintaining proper formatting and structure integrity.
 */
public class AdditiveOperationManager {
    
    private final NDFSchemaRegistry schemaRegistry;
    private final NDFTemplateManager templateManager;
    private final GUIDGenerator guidGenerator;


    
    public AdditiveOperationManager() {
        this.schemaRegistry = new NDFSchemaRegistry();
        this.templateManager = new NDFTemplateManager();
        this.guidGenerator = new GUIDGenerator();
    }

    /**
     * Initialize the manager with existing objects to learn schemas and templates
     */
    public void learnFromExistingObjects(List<NDFValue.ObjectValue> objects) {
        if (objects == null || objects.isEmpty()) {
            return; // Nothing to learn from
        }

        try {
            // Learn schemas and templates from existing objects
            schemaRegistry.learnFromObjects(objects);
            templateManager.learnFromObjects(objects);

            // Register existing GUIDs
            for (NDFValue.ObjectValue obj : objects) {
                if (obj != null && obj.hasProperty("DescriptorId")) {
                    NDFValue guidValue = obj.getProperty("DescriptorId");
                    if (guidValue instanceof NDFValue.GUIDValue) {
                        guidGenerator.registerExistingGuid(((NDFValue.GUIDValue) guidValue).getGUID());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error learning from existing objects: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Add a new top-level object to the NDF file
     */
    public boolean addNewObject(List<NDFValue.ObjectValue> ndfObjects, String objectType,
                               String instanceName, Map<String, Object> properties,
                               ModificationTracker tracker) {
        return addNewObjectWithTemplate(ndfObjects, objectType, instanceName, properties, tracker, null);
    }

    /**
     * Add a new top-level object to the NDF file with a specific template
     */
    public boolean addNewObjectWithTemplate(List<NDFValue.ObjectValue> ndfObjects, String objectType,
                                           String instanceName, Map<String, Object> properties,
                                           ModificationTracker tracker, NDFValue.ObjectValue specificTemplate) {
        try {
            // Check for name collisions first
            if (hasNameCollision(ndfObjects, instanceName)) {
                throw new IllegalArgumentException("Object with name '" + instanceName + "' already exists in the file");
            }

            // Get template for object type - use specific template if provided
            NDFValue.ObjectValue template = specificTemplate;
            if (template == null) {
                template = templateManager.getTemplate(objectType);
            }
            if (template == null) {
                throw new IllegalArgumentException("No template found for object type: " + objectType);
            }

            // Clone template and customize
            NDFValue.ObjectValue newObject = cloneObject(template);
            newObject.setInstanceName(instanceName);

            // Set export status to match the majority of existing objects in the file
            boolean shouldExport = determineExportStatus(ndfObjects);
            newObject.setExported(shouldExport);

            // Generate new GUID if needed
            if (newObject.hasProperty("DescriptorId")) {
                String newGuid = guidGenerator.generateGUID();
                newObject.setProperty("DescriptorId", NDFValue.createGuid(newGuid));
            }

            // Apply custom properties
            applyProperties(newObject, properties);

            // Validate against schema
            if (!schemaRegistry.validateObject(newObject)) {
                return false;
            }

            // Add to objects list at the beginning (index 0) so new entities appear at the top
            ndfObjects.add(0, newObject);

            // Record the addition in modification tracker
            if (tracker != null) {
                tracker.recordModification(
                    instanceName,
                    "OBJECT_CREATION",
                    null, // No old value for new objects
                    NDFValue.createString(objectType), // New value is the object type
                    PropertyUpdater.ModificationType.OBJECT_ADDED,
                    "Created new " + objectType + " object"
                );
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Determine whether new objects should be exported based on existing objects in the file
     */
    private boolean determineExportStatus(List<NDFValue.ObjectValue> existingObjects) {
        if (existingObjects.isEmpty()) {
            return true; // Default to exported if no existing objects
        }

        // Count exported vs non-exported objects
        int exportedCount = 0;
        int nonExportedCount = 0;

        for (NDFValue.ObjectValue obj : existingObjects) {
            if (obj.isExported()) {
                exportedCount++;
            } else {
                nonExportedCount++;
            }
        }

        // Use the majority status, defaulting to exported if tied
        return exportedCount >= nonExportedCount;
    }

    /**
     * Check if an object with the given instance name already exists
     */
    private boolean hasNameCollision(List<NDFValue.ObjectValue> existingObjects, String instanceName) {
        if (instanceName == null || instanceName.trim().isEmpty()) {
            return false; // Empty names are handled by validation elsewhere
        }

        for (NDFValue.ObjectValue obj : existingObjects) {
            if (instanceName.equals(obj.getInstanceName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a new module to an existing object's ModulesDescriptors array
     */
    public boolean addModuleToObject(NDFValue.ObjectValue targetObject, String moduleType,
                                   Map<String, Object> moduleProperties, ModificationTracker tracker) {
        try {
            // Get the ModulesDescriptors array
            NDFValue modulesValue = targetObject.getProperty("ModulesDescriptors");
            if (!(modulesValue instanceof NDFValue.ArrayValue)) {
                return false;
            }

            NDFValue.ArrayValue modulesArray = (NDFValue.ArrayValue) modulesValue;

            // Create new module from template
            NDFValue.ObjectValue newModule = templateManager.getModuleTemplate(moduleType);
            if (newModule == null) {
                return false;
            }

            // Clone and customize module
            NDFValue.ObjectValue moduleInstance = cloneObject(newModule);
            applyProperties(moduleInstance, moduleProperties);

            // Validate module
            if (!schemaRegistry.validateModule(moduleInstance, moduleType)) {
                return false;
            }

            // Record array index before adding
            int arrayIndex = modulesArray.getElements().size();

            // Add to array with proper formatting
            addToArrayWithFormatting(modulesArray, moduleInstance);

            // Record the addition in modification tracker
            if (tracker != null) {
                String objectName = targetObject.getInstanceName() != null ?
                    targetObject.getInstanceName() : "Unknown Object";
                String propertyPath = "ModulesDescriptors[" + arrayIndex + "]";

                tracker.recordModification(
                    objectName,
                    propertyPath,
                    null, // No old value for new modules
                    moduleInstance,
                    PropertyUpdater.ModificationType.MODULE_ADDED,
                    "Added " + moduleType + " module"
                );
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Add a new property to an existing object
     */
    public boolean addPropertyToObject(NDFValue.ObjectValue targetObject, String propertyName,
                                     NDFValue propertyValue, ModificationTracker tracker) {
        try {
            // Validate input parameters
            if (targetObject == null || propertyName == null || propertyName.trim().isEmpty() || propertyValue == null) {
                return false;
            }

            // Validate property against schema
            if (!schemaRegistry.validateProperty(targetObject.getTypeName(), propertyName, propertyValue)) {
                return false;
            }

            // Add property with proper formatting
            targetObject.setProperty(propertyName, propertyValue);
            targetObject.setPropertyComma(propertyName, true); // Most properties have commas

            // Record the addition in modification tracker
            if (tracker != null) {
                String objectName = targetObject.getInstanceName() != null ?
                    targetObject.getInstanceName() : "Unknown Object";

                tracker.recordModification(
                    objectName,
                    propertyName,
                    null, // No old value for new properties
                    propertyValue,
                    PropertyUpdater.ModificationType.PROPERTY_ADDED,
                    "Added property " + propertyName
                );
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Add a new element to an existing array property
     */
    public boolean addElementToArray(NDFValue.ObjectValue targetObject, String arrayPropertyPath,
                                   NDFValue newElement, ModificationTracker tracker, NDFValue.NDFFileType fileType) {
        try {
            NDFValue arrayValue = PropertyUpdater.getPropertyValue(targetObject, arrayPropertyPath, fileType);
            if (!(arrayValue instanceof NDFValue.ArrayValue)) {
                return false;
            }

            NDFValue.ArrayValue array = (NDFValue.ArrayValue) arrayValue;

            // Validate element type against array schema
            if (!schemaRegistry.validateArrayElement(targetObject.getTypeName(), arrayPropertyPath, newElement)) {
                return false;
            }

            // Record array index before adding
            int arrayIndex = array.getElements().size();

            // Add with proper formatting
            addToArrayWithFormatting(array, newElement);

            // Record the addition in modification tracker
            if (tracker != null) {
                String objectName = targetObject.getInstanceName() != null ?
                    targetObject.getInstanceName() : "Unknown Object";
                String fullPropertyPath = arrayPropertyPath + "[" + arrayIndex + "]";

                tracker.recordModification(
                    objectName,
                    fullPropertyPath,
                    null, // No old value for new array elements
                    newElement,
                    PropertyUpdater.ModificationType.ARRAY_ELEMENT_ADDED,
                    "Added element to array " + arrayPropertyPath
                );
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Add a new element to an existing array property (backward compatibility)
     */
    public boolean addElementToArray(NDFValue.ObjectValue targetObject, String arrayPropertyPath,
                                   NDFValue newElement, ModificationTracker tracker) {
        return addElementToArray(targetObject, arrayPropertyPath, newElement, tracker, NDFValue.NDFFileType.UNKNOWN);
    }

    /**
     * Add a new element to an existing array property (backward compatibility)
     */
    public boolean addElementToArray(NDFValue.ObjectValue targetObject, String arrayPropertyPath,
                                   NDFValue newElement) {
        return addElementToArray(targetObject, arrayPropertyPath, newElement, null, NDFValue.NDFFileType.UNKNOWN);
    }
    
    /**
     * Clone an object with all its properties and formatting
     */
    private NDFValue.ObjectValue cloneObject(NDFValue.ObjectValue original) {
        NDFValue.ObjectValue clone = NDFValue.createObject(original.getTypeName());
        
        // Copy all properties
        for (Map.Entry<String, NDFValue> entry : original.getProperties().entrySet()) {
            clone.setProperty(entry.getKey(), cloneValue(entry.getValue()));
            clone.setPropertyComma(entry.getKey(), original.hasCommaAfter(entry.getKey()));
        }
        
        // Copy formatting
        clone.setOriginalFormatting(original.getOriginalPrefix(), original.getOriginalSuffix());
        
        return clone;
    }
    
    /**
     * Deep clone any NDFValue
     */
    private NDFValue cloneValue(NDFValue original) {
        switch (original.getType()) {
            case STRING:
                NDFValue.StringValue strVal = (NDFValue.StringValue) original;
                return new NDFValue.StringValue(strVal.getValue(), strVal.useDoubleQuotes());
                
            case NUMBER:
                NDFValue.NumberValue numVal = (NDFValue.NumberValue) original;
                return NDFValue.createNumber(numVal.getValue(), numVal.wasOriginallyInteger());
                
            case BOOLEAN:
                NDFValue.BooleanValue boolVal = (NDFValue.BooleanValue) original;
                return NDFValue.createBoolean(boolVal.getValue());
                
            case ARRAY:
                NDFValue.ArrayValue arrayVal = (NDFValue.ArrayValue) original;
                NDFValue.ArrayValue newArray = NDFValue.createArray();
                for (NDFValue element : arrayVal.getElements()) {
                    newArray.addElement(cloneValue(element));
                }
                return newArray;
                
            case OBJECT:
                return cloneObject((NDFValue.ObjectValue) original);
                
            case TEMPLATE_REF:
                NDFValue.TemplateRefValue templateRef = (NDFValue.TemplateRefValue) original;
                return NDFValue.createTemplateRef(templateRef.getPath());
                
            case RESOURCE_REF:
                NDFValue.ResourceRefValue resourceRef = (NDFValue.ResourceRefValue) original;
                return NDFValue.createResourceRef(resourceRef.getPath());
                
            case GUID:
                NDFValue.GUIDValue guidVal = (NDFValue.GUIDValue) original;
                return NDFValue.createGuid(guidVal.getGUID());
                
            case ENUM:
                NDFValue.EnumValue enumVal = (NDFValue.EnumValue) original;
                return NDFValue.createEnum(enumVal.getValue());
                
            default:
                // For other types, return a copy
                return original;
        }
    }
    
    /**
     * Apply properties from a map to an object
     */
    private void applyProperties(NDFValue.ObjectValue object, Map<String, Object> properties) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();

            NDFValue ndfValue = convertToNDFValue(value);
            if (ndfValue != null) {
                object.setProperty(propertyName, ndfValue);
                object.setPropertyComma(propertyName, true); // Most properties have commas
            }
        }
    }
    
    /**
     * Convert Java objects to NDFValue
     */
    private NDFValue convertToNDFValue(Object value) {
        if (value instanceof String) {
            return NDFValue.createString((String) value);
        } else if (value instanceof Number) {
            return NDFValue.createNumber(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            return NDFValue.createBoolean((Boolean) value);
        } else if (value instanceof String[]) {
            // Handle string arrays
            String[] stringArray = (String[]) value;
            NDFValue.ArrayValue array = NDFValue.createArray();
            for (int i = 0; i < stringArray.length; i++) {
                array.addElement(NDFValue.createString(stringArray[i]), i < stringArray.length - 1);
            }
            return array;
        } else if (value instanceof NDFValue) {
            return (NDFValue) value;
        }
        return null;
    }
    
    /**
     * Add element to array with proper formatting
     */
    private void addToArrayWithFormatting(NDFValue.ArrayValue array, NDFValue element) {
        // Determine if array is multi-line
        boolean isMultiLine = array.isOriginallyMultiLine() || array.getElements().size() > 3;
        
        if (isMultiLine) {
            // Add with proper indentation
            element.setOriginalFormatting("\n        ", "");
        } else {
            // Add with space separation
            element.setOriginalFormatting(" ", "");
        }
        
        array.addElement(element);
        
        // Set comma for previous element if needed
        if (array.getElements().size() > 1) {
            int prevIndex = array.getElements().size() - 2;
            array.setElementComma(prevIndex, true);
        }
    }



    /**
     * Get the template manager for accessing discovered templates
     */
    public NDFTemplateManager getTemplateManager() {
        return templateManager;
    }
}
