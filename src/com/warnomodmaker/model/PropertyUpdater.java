package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue.*;
import com.warnomodmaker.parser.NDFWriter;

public class PropertyUpdater {

    // File type context for property navigation
    private static NDFValue.NDFFileType currentFileType = NDFValue.NDFFileType.UNKNOWN;

    public static void setFileType(NDFValue.NDFFileType fileType) {
        currentFileType = fileType;
    }

    public static NDFValue.NDFFileType getFileType() {
        return currentFileType;
    }

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


    public static boolean updateProperty(ObjectValue ndfObject, String propertyPath, NDFValue newValue) {
        return updateProperty(ndfObject, propertyPath, newValue, null);
    }


    public static boolean updateProperty(ObjectValue ndfObject, String propertyPath, NDFValue newValue, ModificationTracker tracker) {
        if (ndfObject == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }
        NDFValue oldValue = null;
        if (tracker != null) {
            oldValue = getPropertyValue(ndfObject, propertyPath);
        }

        // CRITICAL FIX: Handle complex nested paths with multiple array indices
        boolean success = navigateToPropertyAndUpdate(ndfObject, propertyPath, newValue, tracker, oldValue);

        return success;
    }

    private static boolean navigateToPropertyAndUpdate(ObjectValue ndfObject, String propertyPath, NDFValue newValue, ModificationTracker tracker, NDFValue oldValue) {
        // Parse the property path to handle complex nested structures
        String[] pathParts = propertyPath.split("\\.");

        // Navigate to the target location
        Object[] navigationResult = navigateToProperty(ndfObject, pathParts);
        if (navigationResult == null) {
            return false;
        }

        Object container = navigationResult[0];
        Object key = navigationResult[1];

        // Update the value based on container type
        boolean success = false;
        if (container instanceof ObjectValue && key instanceof String) {
            ObjectValue obj = (ObjectValue) container;
            String propertyName = (String) key;
            obj.setProperty(propertyName, newValue);
            success = true;
        } else if (container instanceof ArrayValue && key instanceof Integer) {
            ArrayValue array = (ArrayValue) container;
            int index = (Integer) key;
            if (index >= 0 && index < array.getElements().size()) {
                array.getElements().set(index, newValue);
                success = true;
            }
        } else if (container instanceof TupleValue && key instanceof Integer) {
            TupleValue tuple = (TupleValue) container;
            int index = (Integer) key;
            if (index >= 0 && index < tuple.getElements().size()) {
                tuple.getElements().set(index, newValue);
                success = true;
            }
        }

        // Record the modification if tracker is provided
        if (success && tracker != null && oldValue != null) {
            String objectName = ndfObject.getInstanceName() != null ? ndfObject.getInstanceName() : "Unknown Object";
            tracker.recordModification(objectName, propertyPath, oldValue, newValue);
        }

        return success;
    }

    private static Object[] navigateToProperty(ObjectValue startObject, String[] pathParts) {
        Object currentContainer = startObject;

        for (int i = 0; i < pathParts.length; i++) {
            String part = pathParts[i];
            boolean isLastPart = (i == pathParts.length - 1);

            if (part.contains("[") && part.contains("]")) {
                // Handle array/tuple access: PropertyName[index]
                int bracketStart = part.indexOf('[');
                String propertyName = part.substring(0, bracketStart);
                String indexStr = part.substring(bracketStart + 1, part.indexOf(']'));

                try {
                    int index = Integer.parseInt(indexStr);

                    if (currentContainer instanceof ObjectValue) {
                        ObjectValue obj = (ObjectValue) currentContainer;
                        NDFValue arrayValue = obj.getProperty(propertyName);

                        if (arrayValue instanceof ArrayValue) {
                            ArrayValue array = (ArrayValue) arrayValue;
                            if (isLastPart) {
                                // Return the array and index for final update
                                return new Object[]{array, index};
                            } else {
                                // Navigate into the array element
                                if (index >= 0 && index < array.getElements().size()) {
                                    currentContainer = array.getElements().get(index);
                                } else {
                                    return null; // Index out of bounds
                                }
                            }
                        } else if (arrayValue instanceof TupleValue) {
                            TupleValue tuple = (TupleValue) arrayValue;
                            if (isLastPart) {
                                // Return the tuple and index for final update
                                return new Object[]{tuple, index};
                            } else {
                                // Navigate into the tuple element
                                if (index >= 0 && index < tuple.getElements().size()) {
                                    currentContainer = tuple.getElements().get(index);
                                } else {
                                    return null; // Index out of bounds
                                }
                            }
                        } else {
                            return null; // Property is not an array or tuple
                        }
                    } else {
                        return null; // Current container is not an object
                    }
                } catch (NumberFormatException e) {
                    return null; // Invalid index format
                }
            } else {
                // Handle regular property access
                if (currentContainer instanceof ObjectValue) {
                    ObjectValue obj = (ObjectValue) currentContainer;

                    if (isLastPart) {
                        // Return the object and property name for final update
                        return new Object[]{obj, part};
                    } else {
                        // Navigate into the property
                        NDFValue propertyValue = obj.getProperty(part);
                        if (propertyValue instanceof ObjectValue) {
                            currentContainer = propertyValue;
                        } else {
                            return null; // Property doesn't exist or isn't an object
                        }
                    }
                } else {
                    return null; // Current container is not an object
                }
            }
        }

        return null; // Should not reach here
    }

    public static boolean updateNumericProperty(ObjectValue ndfObject, String propertyPath,
                                              ModificationType modificationType, double value) {
        return updateNumericProperty(ndfObject, propertyPath, modificationType, value, null);
    }


    public static boolean updateNumericProperty(ObjectValue ndfObject, String propertyPath,
                                              ModificationType modificationType, double value, ModificationTracker tracker) {
        if (ndfObject == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }

        // First, get the current value
        NDFValue currentValue = getPropertyValue(ndfObject, propertyPath);
        if (!(currentValue instanceof NumberValue)) {
            return false; // Property doesn't exist or isn't numeric
        }

        NumberValue numberValue = (NumberValue) currentValue;
        double currentNumericValue = numberValue.getValue();

        // Calculate the new value
        double newNumericValue = calculateNewValue(currentNumericValue, modificationType, value);
        String modificationDetails = null;
        if (tracker != null) {
            modificationDetails = String.format("%s %.2f", modificationType.getDisplayName(), value);
        }
        NDFValue newValue;
        if (numberValue.wasOriginallyInteger()) {
            // For integer properties, round the result and preserve integer format
            newValue = NDFValue.createNumber(newNumericValue, true);
        } else {
            // For decimal properties, preserve decimal format
            newValue = NDFValue.createNumber(newNumericValue, false);
        }
        boolean success = updateProperty(ndfObject, propertyPath, newValue, null);

        // Record the modification with special numeric details if tracker is provided
        if (success && tracker != null) {
            String objectName = ndfObject.getInstanceName() != null ? ndfObject.getInstanceName() : "Unknown Object";
            tracker.recordModification(objectName, propertyPath, currentValue, newValue, modificationType, modificationDetails);
        }

        return success;
    }


    public static boolean updateBooleanProperty(ObjectValue ndfObject, String propertyPath, boolean value, ModificationTracker tracker) {
        if (ndfObject == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }
        NDFValue currentValue = getPropertyValue(ndfObject, propertyPath);
        if (!(currentValue instanceof BooleanValue)) {
            return false; // Property doesn't exist or isn't boolean
        }
        NDFValue newValue = NDFValue.createBoolean(value);
        boolean success = updateProperty(ndfObject, propertyPath, newValue, null);

        // Record the modification if tracker is provided and update was successful
        if (success && tracker != null) {
            String objectName = ndfObject.getInstanceName() != null ? ndfObject.getInstanceName() : "Unknown Object";
            tracker.recordModification(objectName, propertyPath, currentValue, newValue);
        }

        return success;
    }


    public static boolean updateStringProperty(ObjectValue ndfObject, String propertyPath, String value, ModificationTracker tracker) {
        if (ndfObject == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }
        NDFValue currentValue = getPropertyValue(ndfObject, propertyPath);
        if (!(currentValue instanceof StringValue)) {
            return false; // Property doesn't exist or isn't string
        }

        // CRITICAL FIX: Preserve original quote type when creating new string value
        StringValue originalStringValue = (StringValue) currentValue;
        boolean useDoubleQuotes = originalStringValue.useDoubleQuotes();

        // CRITICAL FIX: Remove quotes from input if user included them
        String cleanValue = value;
        if ((cleanValue.startsWith("\"") && cleanValue.endsWith("\"")) ||
            (cleanValue.startsWith("'") && cleanValue.endsWith("'"))) {
            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
        }

        NDFValue newValue = NDFValue.createString(cleanValue, useDoubleQuotes);
        boolean success = updateProperty(ndfObject, propertyPath, newValue, null);

        // Record the modification if tracker is provided and update was successful
        if (success && tracker != null) {
            String objectName = ndfObject.getInstanceName() != null ? ndfObject.getInstanceName() : "Unknown Object";
            tracker.recordModification(objectName, propertyPath, currentValue, newValue);
        }

        return success;
    }


    public static boolean updateTemplateRefProperty(ObjectValue ndfObject, String propertyPath, String value, ModificationTracker tracker) {
        if (ndfObject == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }
        NDFValue currentValue = getPropertyValue(ndfObject, propertyPath);
        if (currentValue == null) {
            return false; // Property doesn't exist
        }

        // Ensure it's actually a template reference
        if (currentValue.getType() != NDFValue.ValueType.TEMPLATE_REF &&
            currentValue.getType() != NDFValue.ValueType.RESOURCE_REF) {
            return false; // Not a template reference
        }
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
        boolean success = updateProperty(ndfObject, propertyPath, newValue, null);

        // Record the modification if tracker is provided and update was successful
        if (success && tracker != null) {
            String objectName = ndfObject.getInstanceName() != null ? ndfObject.getInstanceName() : "Unknown Object";
            tracker.recordModification(objectName, propertyPath, currentValue, newValue);
        }

        return success;
    }


    public static boolean updateEnumProperty(ObjectValue ndfObject, String propertyPath, String value, ModificationTracker tracker) {
        if (ndfObject == null || propertyPath == null || propertyPath.isEmpty()) {
            return false;
        }
        NDFValue currentValue = getPropertyValue(ndfObject, propertyPath);
        if (currentValue == null) {
            return false; // Property doesn't exist
        }
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
        boolean success = updateProperty(ndfObject, propertyPath, newValue, null);

        // Record the modification if tracker is provided and update was successful
        if (success && tracker != null) {
            String objectName = ndfObject.getInstanceName() != null ? ndfObject.getInstanceName() : "Unknown Object";
            tracker.recordModification(objectName, propertyPath, currentValue, newValue);
        }

        return success;
    }


    public static NDFValue getPropertyValue(ObjectValue ndfObject, String propertyPath) {
        if (ndfObject == null || propertyPath == null || propertyPath.isEmpty()) {
            return null;
        }

        // Use file-type-aware navigation
        return getPropertyValueWithFileType(ndfObject, propertyPath, currentFileType);
    }

    public static NDFValue getPropertyValue(ObjectValue ndfObject, String propertyPath, NDFValue.NDFFileType fileType) {
        return getPropertyValueWithFileType(ndfObject, propertyPath, fileType);
    }

    private static NDFValue getPropertyValueWithFileType(ObjectValue ndfObject, String propertyPath, NDFValue.NDFFileType fileType) {
        if (ndfObject == null || propertyPath == null || propertyPath.isEmpty()) {
            return null;
        }

        // Route to appropriate navigation logic based on file type
        if (fileType == NDFValue.NDFFileType.UNITE_DESCRIPTOR) {
            return getUniteDescriptorPropertyValue(ndfObject, propertyPath);
        } else {
            return getStandardPropertyValue(ndfObject, propertyPath);
        }
    }

    private static NDFValue getStandardPropertyValue(ObjectValue ndfObject, String propertyPath) {
        String[] pathParts = propertyPath.split("\\.");

        // Use the same navigation logic as update
        Object[] navigationResult = navigateToProperty(ndfObject, pathParts);
        if (navigationResult == null) {
            return null;
        }

        Object container = navigationResult[0];
        Object key = navigationResult[1];

        // Get the value based on container type
        if (container instanceof ObjectValue && key instanceof String) {
            ObjectValue obj = (ObjectValue) container;
            String propertyName = (String) key;
            return obj.getProperty(propertyName);
        } else if (container instanceof ArrayValue && key instanceof Integer) {
            ArrayValue array = (ArrayValue) container;
            int index = (Integer) key;
            if (index >= 0 && index < array.getElements().size()) {
                return array.getElements().get(index);
            }
        } else if (container instanceof TupleValue && key instanceof Integer) {
            TupleValue tuple = (TupleValue) container;
            int index = (Integer) key;
            if (index >= 0 && index < tuple.getElements().size()) {
                return tuple.getElements().get(index);
            }
        }

        return null;
    }

    private static NDFValue getUniteDescriptorPropertyValue(ObjectValue ndfObject, String propertyPath) {
        // UniteDescriptor files have different internal structure due to pipe-separated parsing
        // Handle the most common patterns for custom property lookup

        // Simple property access (no dots)
        if (!propertyPath.contains(".")) {
            return ndfObject.getProperty(propertyPath);
        }

        // Handle array access patterns like ModulesDescriptors[0]
        if (propertyPath.matches("^[^.]+\\[\\d+\\]$")) {
            String arrayName = propertyPath.substring(0, propertyPath.indexOf('['));
            int startIndex = propertyPath.indexOf('[') + 1;
            int endIndex = propertyPath.indexOf(']');
            int index = Integer.parseInt(propertyPath.substring(startIndex, endIndex));

            NDFValue arrayValue = ndfObject.getProperty(arrayName);
            if (arrayValue instanceof ArrayValue) {
                ArrayValue array = (ArrayValue) arrayValue;
                if (index >= 0 && index < array.getElements().size()) {
                    return array.getElements().get(index);
                }
            }
            return null;
        }

        // Handle nested property access like ModulesDescriptors[0].TagSet
        String[] pathParts = propertyPath.split("\\.");
        if (pathParts.length == 2) {
            String firstPart = pathParts[0];
            String secondPart = pathParts[1];

            // Check if first part is array access
            if (firstPart.matches("^[^.]+\\[\\d+\\]$")) {
                String arrayName = firstPart.substring(0, firstPart.indexOf('['));
                int startIndex = firstPart.indexOf('[') + 1;
                int endIndex = firstPart.indexOf(']');
                int index = Integer.parseInt(firstPart.substring(startIndex, endIndex));

                NDFValue arrayValue = ndfObject.getProperty(arrayName);
                if (arrayValue instanceof ArrayValue) {
                    ArrayValue array = (ArrayValue) arrayValue;
                    if (index >= 0 && index < array.getElements().size()) {
                        NDFValue element = array.getElements().get(index);
                        if (element instanceof ObjectValue) {
                            ObjectValue elementObj = (ObjectValue) element;
                            return elementObj.getProperty(secondPart);
                        }
                    }
                }
                return null;
            } else {
                // Simple nested access like SomeObject.Property
                NDFValue firstValue = ndfObject.getProperty(firstPart);
                if (firstValue instanceof ObjectValue) {
                    ObjectValue firstObj = (ObjectValue) firstValue;
                    return firstObj.getProperty(secondPart);
                }
                return null;
            }
        }

        // For more complex paths, fall back to standard navigation
        // This handles cases the specialized logic doesn't cover
        return getStandardPropertyValue(ndfObject, propertyPath);
    }


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


    public static boolean hasProperty(ObjectValue ndfObject, String propertyPath) {
        return getPropertyValue(ndfObject, propertyPath) != null;
    }

    public static boolean hasProperty(ObjectValue ndfObject, String propertyPath, NDFValue.NDFFileType fileType) {
        return getPropertyValue(ndfObject, propertyPath, fileType) != null;
    }


    public static int countUnitsWithProperty(java.util.List<ObjectValue> ndfObjects, String propertyPath) {
        int count = 0;
        for (ObjectValue ndfObject : ndfObjects) {
            if (hasProperty(ndfObject, propertyPath)) {
                count++;
            }
        }
        return count;
    }
}
