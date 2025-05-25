package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue.*;

/**
 * Shared utility for updating properties in the in-memory object model.
 * Used by both single property updates (UnitEditor) and mass updates (MassModifyDialog).
 * Now supports modification tracking for creating mod profiles.
 */
public class PropertyUpdater {

    /**
     * Types of modifications that can be applied to numeric values
     */
    public enum ModificationType {
        SET("Set to value"),
        MULTIPLY("Multiply by"),
        ADD("Add"),
        SUBTRACT("Subtract"),
        INCREASE_PERCENT("Percentage increase"),
        DECREASE_PERCENT("Percentage decrease");

        private final String displayName;

        ModificationType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Updates a property value in a unit using direct object navigation
     *
     * @param unit The unit to update
     * @param propertyPath The dot-separated path to the property
     * @param newValue The new value to set
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateProperty(ObjectValue unit, String propertyPath, NDFValue newValue) {
        return updateProperty(unit, propertyPath, newValue, null);
    }

    /**
     * Updates a property value in a unit using direct object navigation with tracking
     *
     * @param unit The unit to update
     * @param propertyPath The dot-separated path to the property
     * @param newValue The new value to set
     * @param tracker Optional modification tracker to record the change
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateProperty(ObjectValue unit, String propertyPath, NDFValue newValue, ModificationTracker tracker) {
        if (unit == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }

        // Get the old value for tracking
        NDFValue oldValue = null;
        if (tracker != null) {
            oldValue = getPropertyValue(unit, propertyPath);
        }

        String[] pathParts = propertyPath.split("\\.");

        // Navigate to the parent object
        ObjectValue currentObject = unit;
        for (int i = 0; i < pathParts.length - 1; i++) {
            String part = pathParts[i];

            // Handle array indices (e.g., "property[0]")
            if (part.contains("[") && part.contains("]")) {
                // Extract property name and index
                int bracketStart = part.indexOf('[');
                String propertyName = part.substring(0, bracketStart);
                String indexStr = part.substring(bracketStart + 1, part.indexOf(']'));

                try {
                    int index = Integer.parseInt(indexStr);
                    NDFValue arrayValue = currentObject.getProperty(propertyName);

                    if (arrayValue instanceof ArrayValue) {
                        ArrayValue array = (ArrayValue) arrayValue;
                        if (index >= 0 && index < array.getElements().size()) {
                            NDFValue element = array.getElements().get(index);
                            if (element instanceof ObjectValue) {
                                currentObject = (ObjectValue) element;
                                continue;
                            }
                        }
                    }
                    return false; // Invalid array access
                } catch (NumberFormatException e) {
                    return false; // Invalid index format
                }
            } else {
                // Regular property access
                NDFValue propertyValue = currentObject.getProperty(part);
                if (propertyValue instanceof ObjectValue) {
                    currentObject = (ObjectValue) propertyValue;
                } else {
                    return false; // Property doesn't exist or isn't an object
                }
            }
        }

        // Update the final property
        String finalPropertyName = pathParts[pathParts.length - 1];

        // Handle array access in final property
        if (finalPropertyName.contains("[") && finalPropertyName.contains("]")) {
            // Extract property name and index
            int bracketStart = finalPropertyName.indexOf('[');
            String propertyName = finalPropertyName.substring(0, bracketStart);
            String indexStr = finalPropertyName.substring(bracketStart + 1, finalPropertyName.indexOf(']'));

            try {
                int index = Integer.parseInt(indexStr);
                NDFValue arrayValue = currentObject.getProperty(propertyName);

                if (arrayValue instanceof ArrayValue) {
                    ArrayValue array = (ArrayValue) arrayValue;
                    if (index >= 0 && index < array.getElements().size()) {
                        // Update the specific array element
                        array.getElements().set(index, newValue);
                    } else {
                        return false; // Index out of bounds
                    }
                } else if (arrayValue instanceof TupleValue) {
                    TupleValue tuple = (TupleValue) arrayValue;
                    if (index >= 0 && index < tuple.getElements().size()) {
                        // Update the specific tuple element
                        tuple.getElements().set(index, newValue);
                    } else {
                        return false; // Index out of bounds
                    }
                } else {
                    return false; // Property is not an array or tuple
                }
            } catch (NumberFormatException e) {
                return false; // Invalid index format
            }
        } else {
            // Regular property update
            // Check if the property exists
            if (!currentObject.getProperties().containsKey(finalPropertyName)) {
                return false;
            }

            // Update the property
            currentObject.setProperty(finalPropertyName, newValue);
        }

        // Record the modification if tracker is provided
        if (tracker != null && oldValue != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            tracker.recordModification(unitName, propertyPath, oldValue, newValue);
        }

        return true;
    }

    /**
     * Updates a numeric property with a modification
     *
     * @param unit The unit to update
     * @param propertyPath The dot-separated path to the property
     * @param modificationType The type of modification to apply
     * @param value The modification value
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateNumericProperty(ObjectValue unit, String propertyPath,
                                              ModificationType modificationType, double value) {
        return updateNumericProperty(unit, propertyPath, modificationType, value, null);
    }

    /**
     * Updates a numeric property with a modification and tracking
     *
     * @param unit The unit to update
     * @param propertyPath The dot-separated path to the property
     * @param modificationType The type of modification to apply
     * @param value The modification value
     * @param tracker Optional modification tracker to record the change
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateNumericProperty(ObjectValue unit, String propertyPath,
                                              ModificationType modificationType, double value, ModificationTracker tracker) {
        if (unit == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }

        // First, get the current value
        NDFValue currentValue = getPropertyValue(unit, propertyPath);
        if (!(currentValue instanceof NumberValue)) {
            return false; // Property doesn't exist or isn't numeric
        }

        NumberValue numberValue = (NumberValue) currentValue;
        double currentNumericValue = numberValue.getValue();

        // Calculate the new value
        double newNumericValue = calculateNewValue(currentNumericValue, modificationType, value);

        // Create modification details for tracking
        String modificationDetails = null;
        if (tracker != null) {
            modificationDetails = String.format("%s %.2f", modificationType.getDisplayName(), value);
        }

        // Update the property with format preservation and smart rounding
        NDFValue newValue;
        if (numberValue.wasOriginallyInteger()) {
            // For integer properties, round the result and preserve integer format
            newValue = NDFValue.createNumber(newNumericValue, true);
        } else {
            // For decimal properties, preserve decimal format
            newValue = NDFValue.createNumber(newNumericValue, false);
        }
        boolean success = updateProperty(unit, propertyPath, newValue, null);

        // Record the modification with special numeric details if tracker is provided
        if (success && tracker != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            tracker.recordModification(unitName, propertyPath, currentValue, newValue, modificationType, modificationDetails);
        }

        return success;
    }

    /**
     * Updates a boolean property with a new value
     *
     * @param unit The unit to update
     * @param propertyPath The dot-separated path to the property
     * @param value The new boolean value
     * @param tracker Optional modification tracker to record the change
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateBooleanProperty(ObjectValue unit, String propertyPath, boolean value, ModificationTracker tracker) {
        if (unit == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }

        // Get the current value for tracking
        NDFValue currentValue = getPropertyValue(unit, propertyPath);
        if (!(currentValue instanceof BooleanValue)) {
            return false; // Property doesn't exist or isn't boolean
        }

        // Create the new boolean value
        NDFValue newValue = NDFValue.createBoolean(value);

        // Update the property and record modification if successful
        boolean success = updateProperty(unit, propertyPath, newValue, null);

        // Record the modification if tracker is provided and update was successful
        if (success && tracker != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            tracker.recordModification(unitName, propertyPath, currentValue, newValue);
        }

        return success;
    }

    /**
     * Updates a string property with a new value
     *
     * @param unit The unit to update
     * @param propertyPath The dot-separated path to the property
     * @param value The new string value
     * @param tracker Optional modification tracker to record the change
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateStringProperty(ObjectValue unit, String propertyPath, String value, ModificationTracker tracker) {
        if (unit == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }

        // Get the current value for tracking
        NDFValue currentValue = getPropertyValue(unit, propertyPath);
        if (!(currentValue instanceof StringValue)) {
            return false; // Property doesn't exist or isn't string
        }

        // Create the new string value
        NDFValue newValue = NDFValue.createString(value);

        // Update the property and record modification if successful
        boolean success = updateProperty(unit, propertyPath, newValue, null);

        // Record the modification if tracker is provided and update was successful
        if (success && tracker != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            tracker.recordModification(unitName, propertyPath, currentValue, newValue);
        }

        return success;
    }

    /**
     * Updates a template reference property with a new value
     *
     * @param unit The unit to update
     * @param propertyPath The dot-separated path to the property
     * @param value The new template reference value (e.g., "ExperienceLevelsPackDescriptor_XP_pack_AA_v3" or "~/SomeTemplate")
     * @param tracker Optional modification tracker to record the change
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateTemplateRefProperty(ObjectValue unit, String propertyPath, String value, ModificationTracker tracker) {
        if (unit == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }

        // Get the current value for tracking
        NDFValue currentValue = getPropertyValue(unit, propertyPath);
        if (currentValue == null) {
            return false; // Property doesn't exist
        }

        // Ensure it's actually a template reference
        if (currentValue.getType() != NDFValue.ValueType.TEMPLATE_REF &&
            currentValue.getType() != NDFValue.ValueType.RESOURCE_REF) {
            return false; // Not a template reference
        }

        // Create the new template reference value
        NDFValue newValue;
        if (value.startsWith("~/") || value.startsWith("$/")) {
            // It's a template reference path
            if (value.startsWith("~/")) {
                newValue = NDFValue.createTemplateRef(value);
            } else {
                newValue = NDFValue.createResourceRef(value);
            }
        } else {
            // It's a direct template name - convert to template reference
            newValue = NDFValue.createTemplateRef("~/" + value);
        }

        // Update the property and record modification if successful
        boolean success = updateProperty(unit, propertyPath, newValue, null);

        // Record the modification if tracker is provided and update was successful
        if (success && tracker != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            tracker.recordModification(unitName, propertyPath, currentValue, newValue);
        }

        return success;
    }

    /**
     * Updates an enum property with a new value
     *
     * @param unit The unit to update
     * @param propertyPath The dot-separated path to the property
     * @param value The new enum value (e.g., "EGameplayBehavior/Nothing")
     * @param tracker Optional modification tracker to record the change
     * @return true if the property was successfully updated, false otherwise
     */
    public static boolean updateEnumProperty(ObjectValue unit, String propertyPath, String value, ModificationTracker tracker) {
        if (unit == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }

        // Get the current value for tracking
        NDFValue currentValue = getPropertyValue(unit, propertyPath);
        if (currentValue == null) {
            return false; // Property doesn't exist
        }

        // Handle different enum types
        NDFValue newValue;
        if (currentValue.getType() == NDFValue.ValueType.ENUM) {
            // Simple enum - extract type and set new value
            EnumValue enumValue = (EnumValue) currentValue;
            String enumType = enumValue.getEnumType();
            newValue = NDFValue.createEnum(enumType, value);
        } else if (currentValue.getType() == NDFValue.ValueType.RAW_EXPRESSION) {
            // Complex enum expression (like bitwise OR combinations)
            newValue = NDFValue.createRawExpression(value);
        } else {
            return false; // Not an enum type
        }

        // Update the property and record modification if successful
        boolean success = updateProperty(unit, propertyPath, newValue, null);

        // Record the modification if tracker is provided and update was successful
        if (success && tracker != null) {
            String unitName = unit.getInstanceName() != null ? unit.getInstanceName() : "Unknown Unit";
            tracker.recordModification(unitName, propertyPath, currentValue, newValue);
        }

        return success;
    }

    /**
     * Gets the value of a property from a unit
     *
     * @param unit The unit to read from
     * @param propertyPath The dot-separated path to the property
     * @return The property value, or null if not found
     */
    public static NDFValue getPropertyValue(ObjectValue unit, String propertyPath) {
        if (unit == null || propertyPath == null || propertyPath.isEmpty()) {
            return null;
        }

        String[] pathParts = propertyPath.split("\\.");

        // Navigate to the parent object
        ObjectValue currentObject = unit;
        for (int i = 0; i < pathParts.length - 1; i++) {
            String part = pathParts[i];

            // Handle array indices
            if (part.contains("[") && part.contains("]")) {
                int bracketStart = part.indexOf('[');
                String propertyName = part.substring(0, bracketStart);
                String indexStr = part.substring(bracketStart + 1, part.indexOf(']'));

                try {
                    int index = Integer.parseInt(indexStr);
                    NDFValue arrayValue = currentObject.getProperty(propertyName);

                    if (arrayValue instanceof ArrayValue) {
                        ArrayValue array = (ArrayValue) arrayValue;
                        if (index >= 0 && index < array.getElements().size()) {
                            NDFValue element = array.getElements().get(index);
                            if (element instanceof ObjectValue) {
                                currentObject = (ObjectValue) element;
                                continue;
                            }
                        }
                    }
                    return null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                NDFValue propertyValue = currentObject.getProperty(part);
                if (propertyValue instanceof ObjectValue) {
                    currentObject = (ObjectValue) propertyValue;
                } else {
                    return null;
                }
            }
        }

        // Get the final property
        String finalPropertyName = pathParts[pathParts.length - 1];

        // Handle array access in final property
        if (finalPropertyName.contains("[") && finalPropertyName.contains("]")) {
            // Extract property name and index
            int bracketStart = finalPropertyName.indexOf('[');
            String propertyName = finalPropertyName.substring(0, bracketStart);
            String indexStr = finalPropertyName.substring(bracketStart + 1, finalPropertyName.indexOf(']'));

            try {
                int index = Integer.parseInt(indexStr);
                NDFValue arrayValue = currentObject.getProperty(propertyName);

                if (arrayValue instanceof ArrayValue) {
                    ArrayValue array = (ArrayValue) arrayValue;
                    if (index >= 0 && index < array.getElements().size()) {
                        return array.getElements().get(index);
                    }
                } else if (arrayValue instanceof TupleValue) {
                    TupleValue tuple = (TupleValue) arrayValue;
                    if (index >= 0 && index < tuple.getElements().size()) {
                        return tuple.getElements().get(index);
                    }
                }
                return null; // Index out of bounds or not an array/tuple
            } catch (NumberFormatException e) {
                return null; // Invalid index format
            }
        } else {
            // Regular property access
            return currentObject.getProperty(finalPropertyName);
        }
    }

    /**
     * Calculates a new numeric value based on the modification type
     */
    private static double calculateNewValue(double currentValue, ModificationType modificationType, double value) {
        switch (modificationType) {
            case SET:
                return value;
            case MULTIPLY:
                return currentValue * value;
            case ADD:
                return currentValue + value;
            case SUBTRACT:
                return currentValue - value;
            case INCREASE_PERCENT:
                return currentValue * (1 + value / 100);
            case DECREASE_PERCENT:
                return currentValue * (1 - value / 100);
            default:
                return currentValue;
        }
    }

    /**
     * Checks if a property exists in a unit
     *
     * @param unit The unit to check
     * @param propertyPath The dot-separated path to the property
     * @return true if the property exists, false otherwise
     */
    public static boolean hasProperty(ObjectValue unit, String propertyPath) {
        return getPropertyValue(unit, propertyPath) != null;
    }

    /**
     * Counts how many units have a specific property
     *
     * @param units The list of units to check
     * @param propertyPath The property path to look for
     * @return The number of units that have this property
     */
    public static int countUnitsWithProperty(java.util.List<ObjectValue> units, String propertyPath) {
        int count = 0;
        for (ObjectValue unit : units) {
            if (hasProperty(unit, propertyPath)) {
                count++;
            }
        }
        return count;
    }
}
