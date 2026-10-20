package com.warnomodmaker.parser;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.ModificationTracker;
import com.warnomodmaker.model.ModificationRecord;
import com.warnomodmaker.model.PropertyUpdater;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LINE-BASED ARCHITECTURE: Simple and reliable writer that replaces only modified lines
 * This solves the git diff problem by preserving all unmodified content exactly
 */
public class LineBasedWriter {
    private final Writer writer;
    private final SourceLineTracker lineTracker;
    private final ModificationTracker modificationTracker;
    private List<NDFValue.ObjectValue> objects;

    public LineBasedWriter(Writer writer, String originalContent, ModificationTracker modificationTracker) {
        this.writer = writer;
        this.lineTracker = new SourceLineTracker(originalContent);
        this.modificationTracker = modificationTracker;
    }

    /**
     * Write the file with line-based modifications applied
     */
    public void write(List<NDFValue.ObjectValue> objects) throws IOException {
        this.objects = objects;

        // Apply all modifications to the line tracker
        applyModifications();

        // Ensure all newly created or existing objects from memory model are present in the output
        ensureAllObjectsPresent(objects);

        // Write the complete output with modifications
        String output = lineTracker.generateOutput();
        writer.write(output);
    }

    /**
     * Apply all tracked modifications to the appropriate lines
     */
    private void applyModifications() {
        if (modificationTracker == null) {
            return;
        }

        List<ModificationRecord> modifications = modificationTracker.getAllModifications();

        for (ModificationRecord mod : modifications) {
            applyModification(mod);
        }
    }

    /**
     * Apply a single modification to the appropriate line
     */
    private void applyModification(ModificationRecord mod) {
        String unitName = mod.getUnitName();
        String propertyPath = mod.getPropertyPath();
        String oldValue = mod.getOldValue();
        String newValue = mod.getNewValue();
        PropertyUpdater.ModificationType modType = mod.getModificationType();

        // Handle full object additions
        if (modType == PropertyUpdater.ModificationType.OBJECT_ADDED) {
            applyObjectAdded(mod);
            return;
        }

        // Handle module additions to ModulesDescriptors
        if (modType == PropertyUpdater.ModificationType.MODULE_ADDED) {
            applyModuleAdded(mod);
            return;
        }

        // Handle array element additions
        if (modType == PropertyUpdater.ModificationType.ARRAY_ELEMENT_ADDED) {
            applyArrayElementAdded(mod);
            return;
        }

        // Handle property additions differently
        if (modType == PropertyUpdater.ModificationType.PROPERTY_ADDED) {
            boolean success = addPropertyLine(unitName, propertyPath, newValue, mod.getNewValueType());
            if (!success) {
                throw new IllegalStateException("Failed to add property line for " + unitName + "." + propertyPath + " - NO FALLBACKS ALLOWED");
            }
            return;
        }

        int lineNumber = findPropertyLine(unitName, propertyPath);

        if (lineNumber >= 0) {
            boolean success = replaceValueInLine(lineNumber, propertyPath, oldValue, newValue);
            if (!success) {
                throw new IllegalStateException("Failed to replace value in line " + (lineNumber + 1) + " for " + unitName + "." + propertyPath + " - NO FALLBACKS ALLOWED");
            }
        } else {
            // Property doesn't exist in this unit - skip silently
            // This is normal behavior as not all units have all properties
            return;
        }
    }

    /**
     * Find the line number containing a property for a specific unit
     */
    private int findPropertyLine(String unitName, String propertyPath) {
        // Strategy 1: Look for the unit definition first, then find the property within that context
        int unitLineStart = findUnitDefinitionLine(unitName);

        if (unitLineStart >= 0) {
            // Search within the entire unit definition (until next export or end of file)
            int unitEnd = findUnitDefinitionEnd(unitLineStart);



            // Use structural navigation for complex nested array paths
            // Example: TurretDescriptorList[1].MountedWeaponDescriptorList[0].Ammunition
            // Also use for ModulesDescriptors with nested properties like ModulesDescriptors[20].Default.SpeedBonusFactorOnRoad
            if (propertyPath.contains("[") && propertyPath.contains(".") &&
                (propertyPath.split("\\[").length > 2 || // More than one array access
                 (propertyPath.startsWith("ModulesDescriptors[") && propertyPath.contains("].") &&
                  propertyPath.substring(propertyPath.indexOf("].") + 2).contains(".")))) { // ModulesDescriptors with nested properties
                int result = findPropertyLineWithStructuralNavigation(unitLineStart, unitEnd, propertyPath);
                return result;
            }

            // Special handling for ModulesDescriptors - search by module type, not index
            if (propertyPath.startsWith("ModulesDescriptors[") && propertyPath.contains(".")) {
                String propertyName = propertyPath.substring(propertyPath.lastIndexOf('.') + 1);

                // Check if this is a nested property (contains multiple dots)
                String pathAfterModule = propertyPath.substring(propertyPath.indexOf("].") + 2);
                if (pathAfterModule.contains(".")) {
                    // This is a nested property like "Default.SpeedBonusFactorOnRoad"
                    // For nested properties, we need to find the right module type first
                    String moduleType = getModuleTypeForNestedProperty(propertyName);
                    if (moduleType != null) {
                        int moduleStart = findModuleByType(unitLineStart, unitEnd, moduleType);
                        if (moduleStart >= 0) {
                            int moduleEnd = findModuleEnd(moduleStart, unitEnd);
                            int result = findNestedProperty(moduleStart, moduleEnd, pathAfterModule);
                            if (result >= 0) {
                                return result;
                            }
                        }
                    }
                } else {
                    // This is a direct property like "UnitAttackValue" or "OpticalStrength"
                    // Search through all modules to find the property
                    int result = findPropertyInAnyModule(unitLineStart, unitEnd, propertyName);
                    if (result >= 0) {
                        return result;
                    }
                }

                // NO FALLBACKS ALLOWED - if not found, fail cleanly
                return -1;
            }

            // For simple properties, use the original logic
            for (int i = unitLineStart; i < unitEnd; i++) {
                String line = lineTracker.getOriginalLine(i);
                if (containsProperty(line, propertyPath)) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * Find the end of a unit definition
     */
    private int findUnitDefinitionEnd(int unitStart) {
        int parenDepth = 0;
        boolean foundOpenParen = false;

        for (int i = unitStart; i < lineTracker.getLineCount(); i++) {
            String line = lineTracker.getOriginalLine(i);

            // Count parentheses
            for (char c : line.toCharArray()) {
                if (c == '(') {
                    parenDepth++;
                    foundOpenParen = true;
                } else if (c == ')') {
                    parenDepth--;
                }
            }

            // If we've found the opening paren and we're back to depth 0, this is the end
            if (foundOpenParen && parenDepth == 0) {
                return i + 1;
            }

            // If we hit another export, this is the end
            if (i > unitStart && line.trim().startsWith("export ")) {
                return i;
            }
        }

        return lineTracker.getLineCount();
    }









    /**
     * Find a property in any module within the unit (universal approach)
     */
    private int findPropertyInAnyModule(int unitStart, int unitEnd, String propertyName) {
        // Simple approach: just search through the entire unit for the property
        // This is more reliable than trying to track module boundaries
        for (int i = unitStart; i < unitEnd; i++) {
            String line = lineTracker.getOriginalLine(i);
            String trimmed = line.trim();

            // Look for the property assignment
            if (trimmed.startsWith(propertyName + " = ") || trimmed.startsWith(propertyName + "=")) {
                return i;
            }
        }

        return -1;
    }









    /**
     * Find a nested property like "Default.MaxSpeedInKmph"
     */
    private int findNestedProperty(int unitStart, int unitEnd, String nestedPath) {
        String[] pathParts = nestedPath.split("\\.");
        if (pathParts.length != 2) {
            return -1; // Only support one level of nesting for now
        }

        String containerName = pathParts[0]; // e.g., "Default"
        String propertyName = pathParts[1];  // e.g., "MaxSpeedInKmph"

        // Find the container object first
        for (int i = unitStart; i < unitEnd; i++) {
            String line = lineTracker.getOriginalLine(i);
            String trimmed = line.trim();

            // Look for the container (e.g., "Default = TGenericMovementModuleDescriptor")
            // Check if line contains the container assignment
            if (trimmed.contains(containerName + " =") || trimmed.contains(containerName + "=")) {
                // Found the container, now look for the property inside it
                // Find the opening parenthesis
                int containerStart = i;
                int openParenLine = -1;

                // Check if opening paren is on the same line
                if (trimmed.contains("(")) {
                    openParenLine = i;
                } else {
                    // Look for opening paren on next lines
                    for (int j = i + 1; j < unitEnd && j < i + 5; j++) {
                        String nextLine = lineTracker.getOriginalLine(j);
                        if (nextLine.trim().equals("(")) {
                            openParenLine = j;
                            break;
                        }
                    }
                }

                if (openParenLine >= 0) {
                    // Find the matching closing parenthesis
                    int closeParenLine = findMatchingCloseParen(openParenLine, unitEnd);
                    if (closeParenLine > openParenLine) {
                        // Search for the property within this container
                        for (int k = openParenLine + 1; k < closeParenLine; k++) {
                            String propLine = lineTracker.getOriginalLine(k);
                            String propTrimmed = propLine.trim();
                            if (propTrimmed.startsWith(propertyName + " = ") || propTrimmed.startsWith(propertyName + "=")) {
                                return k;
                            }
                        }
                    }
                }
                break; // Found the container but not the property
            }
        }

        return -1;
    }



    /**
     * Find the end line of a module starting at moduleStart
     */
    private int findModuleEnd(int moduleStart, int searchEnd) {
        int bracketCount = 0;
        boolean foundStart = false;

        for (int i = moduleStart; i < searchEnd; i++) {
            String line = lineTracker.getOriginalLine(i);

            for (char c : line.toCharArray()) {
                if (c == '(' || c == '[') {
                    bracketCount++;
                    foundStart = true;
                } else if (c == ')' || c == ']') {
                    bracketCount--;
                    if (foundStart && bracketCount == 0) {
                        return i + 1; // Return line after the closing bracket
                    }
                }
            }
        }

        return searchEnd;
    }

    /**
     * Find the matching closing parenthesis for an opening parenthesis
     */
    private int findMatchingCloseParen(int openParenLine, int searchEnd) {
        int parenCount = 1;

        for (int i = openParenLine + 1; i < searchEnd; i++) {
            String line = lineTracker.getOriginalLine(i);
            for (char c : line.toCharArray()) {
                if (c == '(') {
                    parenCount++;
                } else if (c == ')') {
                    parenCount--;
                    if (parenCount == 0) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Find property line using structural navigation for complex nested array paths
     * Example: TurretDescriptorList[1].MountedWeaponDescriptorList[0].Ammunition
     */
    private int findPropertyLineWithStructuralNavigation(int unitStart, int unitEnd, String propertyPath) {
        String[] pathParts = propertyPath.split("\\.");

        // Start from the unit definition
        int currentSearchStart = unitStart;
        int currentSearchEnd = unitEnd;

        // Navigate through each part of the path
        for (int partIndex = 0; partIndex < pathParts.length; partIndex++) {
            String part = pathParts[partIndex];
            boolean isLastPart = (partIndex == pathParts.length - 1);

            if (part.contains("[") && part.contains("]")) {
                // Array access like TurretDescriptorList[1]
                String arrayName = part.substring(0, part.indexOf('['));
                int arrayIndex = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));

                // Find the array property line
                int arrayPropertyLine = findArrayPropertyInRange(currentSearchStart, currentSearchEnd, arrayName);
                if (arrayPropertyLine < 0) {
                    return -1;
                }

                // Navigate to the specific array element
                int elementStart = findArrayElementStart(arrayPropertyLine, arrayIndex);
                if (elementStart < 0) {
                    return -1;
                }

                if (isLastPart) {
                    // This shouldn't happen for our use case, but handle it
                    return elementStart;
                } else {
                    // Update search range to within this array element
                    int elementEnd = findArrayElementEnd(elementStart);
                    currentSearchStart = elementStart;
                    currentSearchEnd = elementEnd;
                }
            } else {
                // Simple property access
                if (isLastPart) {
                    // Find the final property within the current range
                    return findSimplePropertyInRange(currentSearchStart, currentSearchEnd, part);
                } else {
                    // Find the object property and update search range
                    int propertyLine = findSimplePropertyInRange(currentSearchStart, currentSearchEnd, part);
                    if (propertyLine < 0) {
                        return -1;
                    }

                    // Update search range to within this object
                    int objectEnd = findObjectEnd(propertyLine);
                    currentSearchStart = propertyLine;
                    currentSearchEnd = objectEnd;
                }
            }
        }

        return -1;
    }

    /**
     * Find an array property line within a specific range
     */
    private int findArrayPropertyInRange(int startLine, int endLine, String arrayName) {
        for (int i = startLine; i < endLine; i++) {
            String line = lineTracker.getOriginalLine(i);
            String trimmed = line.trim();

            // Look for array property assignment like "TurretDescriptorList = ["
            if (trimmed.startsWith(arrayName)) {
                String afterProperty = trimmed.substring(arrayName.length()).trim();
                if (afterProperty.startsWith("=")) {
                    // Check if this line or the next few lines contain the opening bracket
                    if (afterProperty.contains("[")) {
                        return i;
                    }

                    // Check next few lines for the opening bracket
                    for (int j = i + 1; j < Math.min(i + 3, endLine); j++) {
                        String nextLine = lineTracker.getOriginalLine(j);
                        if (nextLine.trim().equals("[")) {
                            return i;
                        }
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Find the start line of a specific array element (0-based index)
     */
    private int findArrayElementStart(int arrayPropertyLine, int elementIndex) {
        int currentIndex = 0;
        int bracketDepth = 0;
        boolean inArray = false;

        for (int i = arrayPropertyLine; i < lineTracker.getLineCount(); i++) {
            String line = lineTracker.getOriginalLine(i);

            for (char c : line.toCharArray()) {
                if (c == '[') {
                    bracketDepth++;
                    if (bracketDepth == 1) {
                        inArray = true;
                    }
                } else if (c == ']') {
                    bracketDepth--;
                    if (bracketDepth == 0) {
                        return -1; // End of array without finding element
                    }
                }
            }

            if (inArray && bracketDepth == 1) {
                // We're at the top level of the array
                String trimmed = line.trim();

                // Skip empty lines and the opening bracket line
                if (trimmed.isEmpty() || trimmed.equals("[") || trimmed.startsWith(extractArrayName(arrayPropertyLine))) {
                    continue;
                }

                // Check if this line starts a new element (not a continuation)
                if (isArrayElementStart(line)) {
                    if (currentIndex == elementIndex) {
                        return i; // Found the target element
                    }
                    currentIndex++;
                }
            }
        }

        return -1;
    }

    /**
     * Find the end line of an array element starting from elementStart
     */
    private int findArrayElementEnd(int elementStart) {
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean foundStructure = false;

        for (int i = elementStart; i < lineTracker.getLineCount(); i++) {
            String line = lineTracker.getOriginalLine(i);

            for (char c : line.toCharArray()) {
                if (c == '(') {
                    parenDepth++;
                    foundStructure = true;
                } else if (c == ')') {
                    parenDepth--;
                } else if (c == '[') {
                    bracketDepth++;
                } else if (c == ']') {
                    bracketDepth--;
                }
            }

            // If we've found structure and we're back to depth 0, this is the end
            if (foundStructure && parenDepth == 0 && bracketDepth <= 0) {
                return i + 1;
            }

            // Also check for comma at the same level (end of array element)
            if (foundStructure && parenDepth == 0 && bracketDepth == 0 && line.trim().endsWith(",")) {
                return i + 1;
            }
        }

        return lineTracker.getLineCount();
    }

    /**
     * Find a simple property within a specific range
     */
    private int findSimplePropertyInRange(int startLine, int endLine, String propertyName) {
        for (int i = startLine; i < endLine; i++) {
            String line = lineTracker.getOriginalLine(i);
            String trimmed = line.trim();

            // Look for property assignment
            if (trimmed.startsWith(propertyName)) {
                String afterProperty = trimmed.substring(propertyName.length()).trim();
                if (afterProperty.startsWith("=")) {
                    return i;
                }
            }

            // Also check for function parameter style
            if (line.contains(propertyName + "=") || line.contains(propertyName + " =")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find the end of an object starting from a property line
     */
    private int findObjectEnd(int propertyLine) {
        // For now, use a simple heuristic - this could be improved
        return findArrayElementEnd(propertyLine);
    }

    /**
     * Check if a line starts a new array element
     */
    private boolean isArrayElementStart(String line) {
        String trimmed = line.trim();

        // Skip empty lines, comments, and structural elements
        if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.equals("[") || trimmed.equals("]")) {
            return false;
        }

        // For ModulesDescriptors arrays, only count top-level module declarations
        // 1. Type constructor with opening paren: "TTypeUnitModuleDescriptor("
        if (trimmed.matches("^T[a-zA-Z][a-zA-Z]*\\s*\\(.*")) {
            return true;
        }

        // 2. Simple type reference with comma: "TankFlagsModuleDescriptor," or "InfantryFlagsModuleDescriptor,"
        if (trimmed.matches("^[A-Z][a-zA-Z]*ModuleDescriptor\\s*,\\s*$")) {
            return true;
        }

        // 3. Template reference: "~/SomeModuleDescriptor," or "$/Path/Module,"
        if (trimmed.matches("^[~$]/[^,]+,\\s*$")) {
            return true;
        }

        // 4. Template with constructor: "TemplateUnitCriticalModule(Module=...)"
        if (trimmed.matches("^[A-Z][a-zA-Z]*\\s*\\(.*")) {
            return true;
        }

        // 5. Simple type reference without comma (end of array): "TankFlagsModuleDescriptor" or "InfantryFlagsModuleDescriptor"
        if (trimmed.matches("^[A-Z][a-zA-Z]*ModuleDescriptor\\s*$")) {
            return true;
        }

        // 6. Simple descriptor reference: "TScannerConfigurationDescriptor"
        if (trimmed.matches("^T[A-Z][a-zA-Z]*Descriptor\\s*$")) {
            return true;
        }

        // 7. Template reference without comma (end of array): "~/SomeModuleDescriptor"
        if (trimmed.matches("^[~$]/[^,\\s]+\\s*$")) {
            return true;
        }

        // 8. Named module with "is" keyword: "ApparenceModel is VehicleApparenceModuleDescriptor"
        if (trimmed.matches("^[A-Z][a-zA-Z]*\\s+is\\s+.*")) {
            return true;
        }

        // 9. Multi-line "is" declaration: "GroupeCombat is"
        if (trimmed.matches("^[A-Z][a-zA-Z]*\\s+is\\s*$")) {
            return true;
        }

        // 10. Single-line module with parameters: "TDangerousnessModuleDescriptor(Dangerousness = 39),"
        if (trimmed.matches("^T[a-zA-Z][a-zA-Z]*\\s*\\([^)]*\\)\\s*,?\\s*$")) {
            return true;
        }

        return false;
    }

    /**
     * Extract array name from array property line
     */
    private String extractArrayName(int arrayPropertyLine) {
        String line = lineTracker.getOriginalLine(arrayPropertyLine);
        String trimmed = line.trim();

        int equalsIndex = trimmed.indexOf('=');
        if (equalsIndex > 0) {
            return trimmed.substring(0, equalsIndex).trim();
        }

        return "";
    }

    /**
     * Universal property detection for ALL NDF file types and patterns
     * Handles: function parameters, standalone properties, nested objects, arrays, maps, etc.
     */
    private boolean containsProperty(String line, String propertyPath) {
        String propertyName = extractFinalPropertyName(propertyPath);
        String trimmed = line.trim();



        // UNIVERSAL PATTERN 1: Standalone property assignment
        // Examples: "MaxPhysicalDamages = 6", "Coalition = ECoalition/PACT"
        if (trimmed.startsWith(propertyName)) {
            String afterProperty = trimmed.substring(propertyName.length()).trim();
            if (afterProperty.startsWith("=")) {
                return true;
            }
        }

        // UNIVERSAL PATTERN 2: Function parameter
        // Examples: "TFormationModuleDescriptor(TypeUnitFormation = 'Artillerie')"
        //           "TResistanceTypeRTTI(Family=ResistanceFamily_blindage Index=1)"
        if (line.contains(propertyName + "=") || line.contains(propertyName + " =")) {
            return true;
        }

        // UNIVERSAL PATTERN 3: Nested property with context validation
        // For paths like "BlindageProperties.ResistanceSides.Index", ensure parent context exists
        if (propertyPath.contains(".")) {
            String[] pathParts = propertyPath.split("\\.");

            // Special case: MAP key access like "ModulesDescriptors[34].ProductionRessourcesNeeded.($/GFX/Resources/Resource_CommandPoints)"
            if (pathParts.length == 3 && pathParts[0].matches(".*\\[\\d+\\]") &&
                pathParts[2].startsWith("(") && pathParts[2].endsWith(")")) {
                // This is a MAP key access pattern - check if line contains the MAP key
                String mapKey = pathParts[2].substring(1, pathParts[2].length() - 1);
                return line.contains(mapKey);
            }

            // Special case: Simple array property access like "ModulesDescriptors[34].ProductionRessourcesNeeded"
            if (pathParts.length == 2 && pathParts[0].matches(".*\\[\\d+\\]")) {
                // Check for the property name followed by = (with possible content in between)
                if (trimmed.startsWith(propertyName)) {
                    String afterProperty = trimmed.substring(propertyName.length()).trim();
                    return afterProperty.startsWith("=") || afterProperty.contains(" = ");
                }
                // Also check for inline property assignments
                return line.contains(propertyName + "=") || line.contains(propertyName + " =");
            }

            // Skip array index parts like "ModulesDescriptors[15]"
            int startIndex = 0;
            for (int i = 0; i < pathParts.length; i++) {
                if (pathParts[i].matches(".*\\[\\d+\\]")) {
                    startIndex = i + 1;
                    break;
                }
            }

            // Check if we have meaningful context parts after array indices
            if (pathParts.length > startIndex + 1) {
                String parentContext = pathParts[pathParts.length - 2];

                // For nested properties, require both parent context and property
                boolean hasContext = line.contains(parentContext);
                boolean hasProperty = line.contains(propertyName + "=") || line.contains(propertyName + " =");

                return hasContext && hasProperty;
            }
        }

        // UNIVERSAL PATTERN 4: MAP key-value pairs
        // Examples: "( 'FOB_BEL', 'FOB_Nato_03' )", "( EVisionRange/Standard, 3500.0 )"
        // Also handles: "($/GFX/Resources/Resource_CommandPoints, 155)"
        if (line.contains("(") && line.contains(",") && line.contains(")")) {
            // Check if this is a MAP key access pattern like "PropertyName.(MapKey)"
            if (propertyPath.contains(".(") && propertyPath.contains(")")) {
                // Extract the MAP key from the property path
                int keyStart = propertyPath.indexOf(".(") + 2;
                int keyEnd = propertyPath.lastIndexOf(")");
                if (keyStart < keyEnd) {
                    String mapKey = propertyPath.substring(keyStart, keyEnd);
                    return line.contains(mapKey);
                }
            }

            // For other MAP patterns, the property name might be the key
            return line.contains(propertyName) || line.contains("'" + propertyName + "'") || line.contains("\"" + propertyName + "\"");
        }

        // UNIVERSAL PATTERN 5: Array element assignment
        // Examples: "TagSet = [", "Values = ["
        if (trimmed.startsWith(propertyName) && (line.contains("[") || line.contains("MAP"))) {
            String afterProperty = trimmed.substring(propertyName.length()).trim();
            return afterProperty.startsWith("=");
        }

        // UNIVERSAL PATTERN 6: Template/Resource reference assignment
        // Examples: "WeaponManager is $/GFX/Weapon/WeaponDescriptor_2K11_KRUG_DDR"
        if (trimmed.startsWith(propertyName) && line.contains("is")) {
            return true;
        }

        return false;
    }

    /**
     * Replace a value in a specific line while preserving formatting
     */
    private boolean replaceValueInLine(int lineNumber, String propertyPath, String oldValue, String newValue) {
        String originalLine = lineTracker.getOriginalLine(lineNumber);

        if (originalLine.isEmpty()) {
            return false;
        }

        // Check if this is an array property
        String propertyName = extractFinalPropertyName(propertyPath);
        if (isArrayProperty(originalLine, propertyName, newValue)) {
            // Check if it's a single-line array (complete array on one line)
            if (originalLine.contains("[") && originalLine.contains("]")) {
                // Single-line array: just replace the whole line content after the '='
                return replaceSingleLineArray(lineNumber, propertyName, newValue);
            } else {
                // Multi-line array: use the working multi-line replacement logic
                return replaceMultiLineArrayValue(lineNumber, propertyName, oldValue, newValue);
            }
        }

        // Check if this is a MAP key-value pair replacement
        if (propertyPath.contains(".(") && propertyPath.contains(")") &&
            originalLine.contains("(") && originalLine.contains(",") && originalLine.contains(")")) {
            // Handle MAP entry replacement like "($/GFX/Resources/Resource_CommandPoints, 155),"
            return replaceMapEntryValue(lineNumber, propertyPath, oldValue, newValue, originalLine);
        }

        // Clean the values for comparison
        String cleanOldValue = cleanValue(oldValue);
        String cleanNewValue = cleanValue(newValue);

        String newLine = originalLine;

        // If line is a property assignment with '=', restrict value replacement to AFTER the '='
        // to prevent corrupting property identifiers (e.g. UnitAttackValue1 = 1)
        int equalsIndex = originalLine.indexOf('=');
        if (equalsIndex >= 0) {
            String beforeEquals = originalLine.substring(0, equalsIndex + 1); // Include the '='
            String afterEquals = originalLine.substring(equalsIndex + 1);

            String modifiedAfter = null;
            if (afterEquals.contains("'" + cleanOldValue + "'")) {
                modifiedAfter = afterEquals.replace("'" + cleanOldValue + "'", "'" + cleanNewValue + "'");
            } else if (afterEquals.contains("\"" + cleanOldValue + "\"")) {
                modifiedAfter = afterEquals.replace("\"" + cleanOldValue + "\"", "\"" + cleanNewValue + "\"");
            } else if (propertyPath.startsWith("ModulesDescriptors[")) {
                String currentValueInLine = extractValueFromLine(originalLine, propertyName);
                if (currentValueInLine != null) {
                    modifiedAfter = afterEquals.replace(currentValueInLine, cleanNewValue);
                }
            } else if (afterEquals.contains(cleanOldValue)) {
                modifiedAfter = afterEquals.replace(cleanOldValue, cleanNewValue);
            }

            if (modifiedAfter == null) {
                // Strategy 4: Pattern-based replacement for property assignments
                String trailingPunctuation = "";
                String valueOnly = afterEquals.trim();
                if (valueOnly.endsWith(",")) {
                    trailingPunctuation = ",";
                    valueOnly = valueOnly.substring(0, valueOnly.length() - 1).trim();
                }

                String trimmedAfter = afterEquals.trim();
                if (!trimmedAfter.isEmpty()) {
                    int spacingLength = afterEquals.indexOf(trimmedAfter);
                    String spacing = afterEquals.substring(0, spacingLength);
                    modifiedAfter = spacing + cleanNewValue + trailingPunctuation;
                } else {
                    modifiedAfter = " " + cleanNewValue + trailingPunctuation;
                }
            }

            newLine = beforeEquals + modifiedAfter;
        } else {
            // Non-equals line fallback
            if (originalLine.contains("'" + cleanOldValue + "'")) {
                newLine = originalLine.replace("'" + cleanOldValue + "'", "'" + cleanNewValue + "'");
            } else if (originalLine.contains("\"" + cleanOldValue + "\"")) {
                newLine = originalLine.replace("\"" + cleanOldValue + "\"", "\"" + cleanNewValue + "\"");
            } else if (originalLine.contains(cleanOldValue)) {
                newLine = originalLine.replace(cleanOldValue, cleanNewValue);
            }
        }

        // Apply the modification if the line changed
        if (!newLine.equals(originalLine)) {

            lineTracker.modifyLine(lineNumber, newLine);
            return true;
        }

        // Check if we're trying to replace the same value (which should be considered successful)
        if (cleanOldValue.equals(cleanNewValue)) {

            return true;
        }


        return false;
    }

    /**
     * Extract the current value from a property line
     * Example: "UnitAttackValue = 1" -> "1"
     */
    private String extractValueFromLine(String line, String propertyName) {
        String trimmed = line.trim();

        // Look for "PropertyName = Value" pattern
        int equalsIndex = trimmed.indexOf('=');
        if (equalsIndex < 0) {
            return null;
        }

        String beforeEquals = trimmed.substring(0, equalsIndex).trim();
        if (!beforeEquals.equals(propertyName)) {
            return null;
        }

        String afterEquals = trimmed.substring(equalsIndex + 1).trim();

        // Remove trailing comma if present
        if (afterEquals.endsWith(",")) {
            afterEquals = afterEquals.substring(0, afterEquals.length() - 1).trim();
        }

        return afterEquals;
    }

    /**
     * Replace the value in a MAP entry line
     * Example: "($/GFX/Resources/Resource_CommandPoints, 155)," -> "($/GFX/Resources/Resource_CommandPoints, 200),"
     */
    private boolean replaceMapEntryValue(int lineNumber, String propertyPath, String oldValue, String newValue, String originalLine) {
        // Extract the MAP key from the property path
        int keyStart = propertyPath.indexOf(".(") + 2;
        int keyEnd = propertyPath.lastIndexOf(")");
        if (keyStart >= keyEnd) {
            return false;
        }

        String mapKey = propertyPath.substring(keyStart, keyEnd);

        // Find the MAP entry pattern: (mapKey, value)
        // Look for the comma after the map key
        int keyIndex = originalLine.indexOf(mapKey);
        if (keyIndex < 0) {
            return false;
        }

        int commaIndex = originalLine.indexOf(",", keyIndex);
        if (commaIndex < 0) {
            return false;
        }

        // Find the closing parenthesis
        int closeParenIndex = originalLine.indexOf(")", commaIndex);
        if (closeParenIndex < 0) {
            return false;
        }

        // Extract the current value between comma and closing paren
        String valueSection = originalLine.substring(commaIndex + 1, closeParenIndex).trim();

        // Replace the old value with the new value
        String cleanOldValue = cleanValue(oldValue);
        String cleanNewValue = cleanValue(newValue);

        if (valueSection.equals(cleanOldValue)) {
            // Build the new line
            String beforeValue = originalLine.substring(0, commaIndex + 1) + " ";
            String afterValue = originalLine.substring(closeParenIndex);
            String newLine = beforeValue + cleanNewValue + afterValue;

            lineTracker.modifyLine(lineNumber, newLine);
            return true;
        }

        return false;
    }

    /**
     * Add a new property line to a unit (defaults to STRING type)
     */
    private boolean addPropertyLine(String unitName, String propertyPath, String newValue) {
        return addPropertyLine(unitName, propertyPath, newValue, "STRING");
    }

    /**
     * Add a new property line to a unit with explicit type formatting
     */
    private boolean addPropertyLine(String unitName, String propertyPath, String newValue, String valueType) {
        // Find the unit definition
        int unitLineStart = findUnitDefinitionLine(unitName);
        if (unitLineStart < 0) {
            // Newly created object in memory will have this property serialized directly with the object
            if (findObjectByName(unitName) != null) {
                return true;
            }
            return false;
        }

        // Find the best insertion point within the unit (supporting nested paths)
        int insertionPoint = findPropertyInsertionPoint(unitLineStart, propertyPath);
        if (insertionPoint < 0) {
            return false;
        }

        // Create the new property line with proper formatting and quote preservation
        String newPropertyLine = createPropertyLine(propertyPath, newValue, insertionPoint, valueType);

        // Insert the new line
        lineTracker.insertLine(insertionPoint, newPropertyLine);

        return true;
    }

    /**
     * Find the best insertion point for a new property within a unit
     */
    private int findPropertyInsertionPoint(int unitLineStart, String propertyPath) {
        int unitEnd = findUnitDefinitionEnd(unitLineStart);

        // Nested property support for ModulesDescriptors[...].prop or Default.prop
        if (propertyPath.contains(".")) {
            String[] parts = propertyPath.split("\\.");
            if (parts[0].startsWith("ModulesDescriptors")) {
                String subProp = parts[parts.length - 1];
                String moduleType = getModuleTypeForNestedProperty(subProp);
                if (moduleType != null) {
                    int moduleStart = findModuleByType(unitLineStart, unitEnd, moduleType);
                    if (moduleStart >= 0) {
                        int moduleEnd = findModuleEnd(moduleStart, unitEnd);
                        int lastInModule = moduleStart;
                        for (int i = moduleStart + 1; i < moduleEnd - 1; i++) {
                            String l = lineTracker.getOriginalLine(i).trim();
                            if (l.contains("=") && !l.startsWith("//")) {
                                lastInModule = i;
                            }
                        }
                        return lastInModule + 1;
                    }
                }
            }
        }

        // For simple properties, insert before the closing parenthesis/brace
        // Look for the last property line before the closing
        int lastPropertyLine = unitLineStart;

        for (int i = unitLineStart + 1; i < unitEnd - 1; i++) {
            String line = lineTracker.getOriginalLine(i);
            String trimmed = line.trim();

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }

            // Look for property assignments (contains =)
            if (trimmed.contains("=") && !trimmed.startsWith("export")) {
                lastPropertyLine = i;
            }
        }

        // Insert after the last property line
        return lastPropertyLine + 1;
    }

    /**
     * Create a properly formatted property line with quote preservation
     */
    private String createPropertyLine(String propertyPath, String newValue, int insertionPoint) {
        return createPropertyLine(propertyPath, newValue, insertionPoint, "STRING");
    }

    private String createPropertyLine(String propertyPath, String newValue, int insertionPoint, String valueType) {
        // Get the indentation from the surrounding lines
        String indentation = getIndentationFromContext(insertionPoint);

        // Extract the property name from the path
        String propertyName = extractFinalPropertyName(propertyPath);

        // Format value preserving quotes and types
        String formattedValue = formatValueForStorage(newValue, valueType);

        // Create the property line with proper formatting
        return indentation + propertyName + " = " + formattedValue + ",";
    }

    /**
     * Format a string value for NDF line output, preserving quotes and types
     */
    public static String formatValueForStorage(String val, String type) {
        if (val == null) {
            return "nil";
        }
        String cleaned = val.trim();
        if (cleaned.startsWith("SQ:")) {
            return "'" + cleaned.substring(3) + "'";
        }
        if (cleaned.startsWith("DQ:")) {
            return "\"" + cleaned.substring(3) + "\"";
        }
        if ("STRING".equalsIgnoreCase(type)) {
            if ((cleaned.startsWith("'") && cleaned.endsWith("'")) ||
                (cleaned.startsWith("\"") && cleaned.endsWith("\""))) {
                return cleaned;
            }
            return "'" + cleaned + "'";
        }
        if ("GUID".equalsIgnoreCase(type)) {
            if (!cleaned.startsWith("GUID:")) {
                return "GUID:{" + cleaned + "}";
            }
            return cleaned;
        }
        return cleaned;
    }

    /**
     * Get appropriate indentation from the context around the insertion point
     */
    private String getIndentationFromContext(int insertionPoint) {
        // Look at nearby lines to determine proper indentation
        for (int i = Math.max(0, insertionPoint - 3); i < Math.min(lineTracker.getLineCount(), insertionPoint + 3); i++) {
            String line = lineTracker.getOriginalLine(i);
            if (line.trim().contains("=") && !line.trim().startsWith("export")) {
                // Extract indentation from this property line
                int firstNonSpace = 0;
                while (firstNonSpace < line.length() && Character.isWhitespace(line.charAt(firstNonSpace))) {
                    firstNonSpace++;
                }
                return line.substring(0, firstNonSpace);
            }
        }

        // Default indentation if we can't determine from context
        return "        "; // 8 spaces
    }

    /**
     * Clean a value string for comparison and replacement
     */
    private String cleanValue(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim();

        // Handle ModificationRecord prefixes (SQ: for single quotes, DQ: for double quotes)
        if (cleaned.startsWith("SQ:")) {
            cleaned = cleaned.substring(3); // Remove "SQ:" prefix
        } else if (cleaned.startsWith("DQ:")) {
            cleaned = cleaned.substring(3); // Remove "DQ:" prefix
        }

        // Remove surrounding quotes if present
        if ((cleaned.startsWith("'") && cleaned.endsWith("'")) ||
            (cleaned.startsWith("\"") && cleaned.endsWith("\""))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        return cleaned;
    }

    /**
     * Extract the final property name from a property path
     */
    private String extractFinalPropertyName(String propertyPath) {
        String finalPart = propertyPath;
        if (propertyPath.contains(".")) {
            finalPart = propertyPath.substring(propertyPath.lastIndexOf(".") + 1);
        }

        // Handle MAP key access patterns like "($/GFX/Resources/Resource_CommandPoints)"
        if (finalPart.startsWith("(") && finalPart.endsWith(")")) {
            // For MAP key access, extract the key name
            String mapKey = finalPart.substring(1, finalPart.length() - 1);
            // Extract the final part of the key path
            if (mapKey.contains("/")) {
                return mapKey.substring(mapKey.lastIndexOf("/") + 1);
            }
            return mapKey;
        }

        // Remove array indices for property matching
        // BaseHitValueModifiers[1] -> BaseHitValueModifiers
        if (finalPart.contains("[")) {
            finalPart = finalPart.substring(0, finalPart.indexOf('['));
        }

        return finalPart;
    }

    /**
     * Check if this is an array property (single-line or multi-line)
     */
    private boolean isArrayProperty(String line, String propertyName, String newValue) {
        // Check if the line contains the property assignment (handle multiple spaces)
        boolean hasPropertyAssignment = line.contains(propertyName) && line.contains("=");
        boolean newValueIsArray = newValue.trim().startsWith("[") && newValue.trim().endsWith("]");

        return hasPropertyAssignment && newValueIsArray;
    }

    /**
     * Replace a single-line array by replacing everything after the '=' sign
     */
    private boolean replaceSingleLineArray(int lineNumber, String propertyName, String newValue) {
        String originalLine = lineTracker.getOriginalLine(lineNumber);

        // Find the '=' sign and replace everything after it
        int equalsIndex = originalLine.indexOf('=');
        if (equalsIndex < 0) {
            return false;
        }

        String beforeEquals = originalLine.substring(0, equalsIndex + 1);
        String newLine = beforeEquals + " " + newValue.trim();

        lineTracker.modifyLine(lineNumber, newLine);
        return true;
    }

    /**
     * Replace a multi-line array value while preserving formatting
     */
    private boolean replaceMultiLineArrayValue(int startLine, String propertyName, String oldValue, String newValue) {
        // Find the end of the array (look for the closing bracket)
        int endLine = findArrayEndLine(startLine, propertyName);
        if (endLine < 0) {
            return false; // Couldn't find array end
        }

        // Get the indentation from the property line
        String propertyLine = lineTracker.getOriginalLine(startLine);
        String indentation = getLineIndentation(propertyLine);

        // Parse the new array value and format it properly
        String formattedArrayValue = formatArrayValue(newValue, indentation);

        // Replace the entire array block
        return replaceArrayBlock(startLine, endLine, propertyName, formattedArrayValue);
    }

    /**
     * Find the line where an array ends (closing bracket)
     */
    private int findArrayEndLine(int startLine, String propertyName) {
        int bracketDepth = 0;
        boolean foundOpenBracket = false;

        for (int i = startLine; i < lineTracker.getLineCount(); i++) {
            String line = lineTracker.getOriginalLine(i);

            for (char c : line.toCharArray()) {
                if (c == '[') {
                    bracketDepth++;
                    foundOpenBracket = true;
                } else if (c == ']') {
                    bracketDepth--;
                    if (foundOpenBracket && bracketDepth == 0) {
                        return i; // Found the closing bracket
                    }
                }
            }
        }

        return -1; // Couldn't find closing bracket
    }

    /**
     * Get the indentation (leading whitespace) from a line
     */
    private String getLineIndentation(String line) {
        StringBuilder indentation = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indentation.append(c);
            } else {
                break;
            }
        }
        return indentation.toString();
    }

    /**
     * Format an array value with proper indentation and line breaks
     */
    private String formatArrayValue(String arrayValue, String baseIndentation) {
        String trimmed = arrayValue.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return arrayValue; // Not a proper array format
        }

        // Extract array content (without brackets)
        String content = trimmed.substring(1, trimmed.length() - 1).trim();

        if (content.isEmpty()) {
            return "[]"; // Empty array
        }

        // Split array elements while respecting nested structures
        String[] elements = splitArrayElements(content);

        StringBuilder formatted = new StringBuilder();
        formatted.append("=\n").append(baseIndentation).append("[\n");

        for (int i = 0; i < elements.length; i++) {
            String element = elements[i].trim();
            formatted.append(baseIndentation).append("    ").append(element);

            if (i < elements.length - 1) {
                formatted.append(",");
            }
            formatted.append("\n");
        }

        formatted.append(baseIndentation).append("]");
        return formatted.toString();
    }

    /**
     * Split array elements while respecting nested parentheses and quotes
     */
    private String[] splitArrayElements(String content) {
        List<String> elements = new ArrayList<>();
        StringBuilder currentElement = new StringBuilder();
        int parenthesesDepth = 0;
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (!inQuotes) {
                if (c == '\'' || c == '"') {
                    inQuotes = true;
                    quoteChar = c;
                    currentElement.append(c);
                } else if (c == '(') {
                    parenthesesDepth++;
                    currentElement.append(c);
                } else if (c == ')') {
                    parenthesesDepth--;
                    currentElement.append(c);
                } else if (c == ',' && parenthesesDepth == 0) {
                    // Found a top-level comma - end current element
                    elements.add(currentElement.toString().trim());
                    currentElement.setLength(0);
                } else {
                    currentElement.append(c);
                }
            } else {
                currentElement.append(c);
                if (c == quoteChar) {
                    inQuotes = false;
                }
            }
        }

        // Add the last element
        if (currentElement.length() > 0) {
            elements.add(currentElement.toString().trim());
        }

        return elements.toArray(new String[0]);
    }

    /**
     * Replace an entire array block with new content
     */
    private boolean replaceArrayBlock(int startLine, int endLine, String propertyName, String newArrayContent) {
        // Get the property line and extract the part before the '='
        String propertyLine = lineTracker.getOriginalLine(startLine);
        String beforeEquals = propertyLine.substring(0, propertyLine.indexOf('='));

        // Create the new property line with the formatted array
        String newPropertyLine = beforeEquals + newArrayContent;

        // Replace the first line
        lineTracker.modifyLine(startLine, newPropertyLine);

        // Clear all the intermediate lines (from startLine+1 to endLine inclusive)
        // by replacing them with empty lines
        for (int i = startLine + 1; i <= endLine; i++) {
            lineTracker.modifyLine(i, "");
        }

        return true;
    }

    /**
     * Find the unit definition line
     */
    private int findUnitDefinitionLine(String unitName) {
        for (int i = 0; i < lineTracker.getLineCount(); i++) {
            String line = lineTracker.getOriginalLine(i);
            if (line.trim().startsWith("export " + unitName) ||
                line.trim().startsWith(unitName + " is ")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get modification statistics for debugging
     */
    public String getModificationStats() {
        return lineTracker.getModificationSummary();
    }

    /**
     * Get module type for nested properties
     */
    private String getModuleTypeForNestedProperty(String propertyName) {
        switch (propertyName) {
            case "MaxSpeedInKmph":
            case "PathfindType":
                return "TModuleSelector"; // GenericMovement module
            case "SpeedBonusFactorOnRoad":
            case "MaxAccelerationGRU":
            case "MaxDecelerationGRU":
            case "TempsDemiTour":
            case "UnitMovingType":
                return "TModuleSelector"; // LandMovement module
            default:
                return null;
        }
    }

    /**
     * Find a module by its type name
     */
    private int findModuleByType(int unitStart, int unitEnd, String moduleType) {
        for (int i = unitStart; i < unitEnd; i++) {
            String line = lineTracker.getOriginalLine(i);
            String trimmed = line.trim();

            // Look for module declarations like "LandMovement is TModuleSelector"
            if (trimmed.contains(" is " + moduleType)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find an object in the memory model by its instance name
     */
    private NDFValue.ObjectValue findObjectByName(String name) {
        if (name == null || objects == null) {
            return null;
        }
        for (NDFValue.ObjectValue obj : objects) {
            if (obj != null && name.equals(obj.getInstanceName())) {
                return obj;
            }
        }
        return null;
    }

    private final Set<String> insertedObjectNames = new HashSet<>();

    /**
     * Apply an OBJECT_ADDED modification record
     */
    private void applyObjectAdded(ModificationRecord mod) {
        String unitName = mod.getUnitName();
        NDFValue.ObjectValue obj = findObjectByName(unitName);
        if (obj == null) {
            return;
        }
        // If already present in lineTracker or already inserted, do not re-add
        if (findUnitDefinitionLine(unitName) >= 0 || insertedObjectNames.contains(unitName)) {
            return;
        }
        String serialized = serializeTopLevelObject(obj);
        int insertPos = lineTracker.getLineCount();
        lineTracker.insertLine(insertPos, "\n" + serialized);
        insertedObjectNames.add(unitName);
    }

    /**
     * Ensure all objects present in memory model exist in source lines
     */
    private void ensureAllObjectsPresent(List<NDFValue.ObjectValue> memoryObjects) {
        if (memoryObjects == null) {
            return;
        }
        for (NDFValue.ObjectValue obj : memoryObjects) {
            if (obj == null || obj.getInstanceName() == null) {
                continue;
            }
            String name = obj.getInstanceName();
            if (findUnitDefinitionLine(name) < 0 && !insertedObjectNames.contains(name)) {
                String serialized = serializeTopLevelObject(obj);
                int insertPos = lineTracker.getLineCount();
                lineTracker.insertLine(insertPos, "\n" + serialized);
                insertedObjectNames.add(name);
            }
        }
    }

    /**
     * Apply a MODULE_ADDED modification record
     */
    private void applyModuleAdded(ModificationRecord mod) {
        String unitName = mod.getUnitName();
        NDFValue.ObjectValue unit = findObjectByName(unitName);

        int unitStart = findUnitDefinitionLine(unitName);
        if (unitStart < 0) {
            return;
        }
        int unitEnd = findUnitDefinitionEnd(unitStart);

        // Find the module in the unit's ModulesDescriptors in memory
        NDFValue.ObjectValue moduleObj = null;
        if (unit != null) {
            NDFValue modulesVal = unit.getProperty("ModulesDescriptors");
            if (modulesVal instanceof NDFValue.ArrayValue) {
                NDFValue.ArrayValue arr = (NDFValue.ArrayValue) modulesVal;
                int index = parseIndexFromPath(mod.getPropertyPath());
                if (index >= 0 && index < arr.getElements().size()) {
                    NDFValue elem = arr.getElements().get(index);
                    if (elem instanceof NDFValue.ObjectValue) {
                        moduleObj = (NDFValue.ObjectValue) elem;
                    }
                } else if (!arr.getElements().isEmpty()) {
                    NDFValue lastElem = arr.getElements().get(arr.getElements().size() - 1);
                    if (lastElem instanceof NDFValue.ObjectValue) {
                        moduleObj = (NDFValue.ObjectValue) lastElem;
                    }
                }
            }
        }

        if (moduleObj == null) {
            return;
        }

        // Find ModulesDescriptors declaration line
        int modulesStartLine = -1;
        for (int i = unitStart; i < unitEnd; i++) {
            String line = lineTracker.getOriginalLine(i);
            if (line.contains("ModulesDescriptors") && line.contains("=")) {
                modulesStartLine = i;
                break;
            }
        }

        if (modulesStartLine < 0) {
            return;
        }

        int modulesCloseBracketLine = findArrayEnd(modulesStartLine);
        if (modulesCloseBracketLine < 0) {
            return;
        }

        ensurePreviousLineHasComma(modulesStartLine, modulesCloseBracketLine);

        String serializedModule = serializeModuleForArray(moduleObj, 2);
        lineTracker.insertLine(modulesCloseBracketLine, serializedModule);
    }

    /**
     * Apply an ARRAY_ELEMENT_ADDED modification record
     */
    private void applyArrayElementAdded(ModificationRecord mod) {
        String unitName = mod.getUnitName();
        int unitStart = findUnitDefinitionLine(unitName);
        if (unitStart < 0) return;
        int unitEnd = findUnitDefinitionEnd(unitStart);

        String propertyPath = mod.getPropertyPath();
        String arrayName = extractArrayBaseName(propertyPath);
        int arrayStart = -1;
        for (int i = unitStart; i < unitEnd; i++) {
            String line = lineTracker.getOriginalLine(i);
            if (containsProperty(line, arrayName) && line.contains("[")) {
                arrayStart = i;
                break;
            }
        }
        if (arrayStart < 0) return;

        int closeBracketLine = findArrayEnd(arrayStart);
        if (closeBracketLine < 0) return;

        String indent = getIndentationFromContext(closeBracketLine);
        if (!indent.endsWith("    ")) {
            indent = indent + "    ";
        }

        // Check if single-line array
        if (arrayStart == closeBracketLine) {
            String line = lineTracker.getCurrentLine(arrayStart);
            int closingBracketPos = line.lastIndexOf(']');
            if (closingBracketPos >= 0) {
                String beforeBracket = line.substring(0, closingBracketPos).trim();
                String afterBracket = line.substring(closingBracketPos);
                String formattedVal = formatValueForStorage(mod.getNewValue(), mod.getNewValueType());
                String separator = beforeBracket.endsWith("[") ? "" : ", ";
                String newLine = beforeBracket + separator + formattedVal + afterBracket;
                lineTracker.modifyLine(arrayStart, newLine);
            }
            return;
        }

        // Multi-line array
        ensurePreviousLineHasComma(arrayStart, closeBracketLine);
        String formattedVal = formatValueForStorage(mod.getNewValue(), mod.getNewValueType());
        String insertedLine = indent + formattedVal + ",";
        lineTracker.insertLine(closeBracketLine, insertedLine);
    }

    /**
     * Find the closing bracket ']' matching the opening bracket '[' starting from arrayStartLine
     */
    private int findArrayEnd(int arrayStartLine) {
        int bracketDepth = 0;
        boolean foundOpenBracket = false;

        for (int i = arrayStartLine; i < lineTracker.getLineCount(); i++) {
            String line = lineTracker.getOriginalLine(i);
            for (char c : line.toCharArray()) {
                if (c == '[') {
                    bracketDepth++;
                    foundOpenBracket = true;
                } else if (c == ']') {
                    bracketDepth--;
                }
            }
            if (foundOpenBracket && bracketDepth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Ensure the last non-empty line before a closing bracket has a trailing comma
     */
    private void ensurePreviousLineHasComma(int startLine, int closeBracketLine) {
        for (int i = closeBracketLine - 1; i >= startLine; i--) {
            String line = lineTracker.getCurrentLine(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.endsWith("[") || trimmed.endsWith(",")) {
                break;
            }
            lineTracker.modifyLine(i, line + ",");
            break;
        }
    }

    /**
     * Parse array index from path like "ModulesDescriptors[5]"
     */
    private int parseIndexFromPath(String path) {
        if (path == null) return -1;
        int open = path.lastIndexOf('[');
        int close = path.lastIndexOf(']');
        if (open >= 0 && close > open) {
            try {
                return Integer.parseInt(path.substring(open + 1, close).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Extract base array property name from path
     */
    private String extractArrayBaseName(String propertyPath) {
        if (propertyPath == null) return "";
        String lastPart = propertyPath;
        if (lastPart.contains(".")) {
            lastPart = lastPart.substring(lastPart.lastIndexOf('.') + 1);
        }
        if (lastPart.contains("[")) {
            lastPart = lastPart.substring(0, lastPart.indexOf('['));
        }
        return lastPart;
    }

    /**
     * Serialize a top-level ObjectValue to NDF syntax
     */
    public static String serializeTopLevelObject(NDFValue.ObjectValue obj) {
        StringBuilder sb = new StringBuilder();
        if (obj.isExported()) {
            sb.append("export ");
        }
        sb.append(obj.getInstanceName()).append(" is ").append(obj.getTypeName()).append("\n");
        sb.append("(\n");
        for (Map.Entry<String, NDFValue> entry : obj.getProperties().entrySet()) {
            String propName = entry.getKey();
            NDFValue val = entry.getValue();
            sb.append("    ").append(propName).append(" = ");
            sb.append(serializeValue(val, 1));
            if (obj.hasCommaAfter(propName)) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(")\n");
        return sb.toString();
    }

    /**
     * Serialize a module object with appropriate indentation for insertion into an array
     */
    private String serializeModuleForArray(NDFValue.ObjectValue moduleObj, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        String subIndent = "    ".repeat(indentLevel + 1);
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append(moduleObj.getTypeName()).append("\n");
        sb.append(indent).append("(\n");
        for (Map.Entry<String, NDFValue> entry : moduleObj.getProperties().entrySet()) {
            String propName = entry.getKey();
            NDFValue val = entry.getValue();
            sb.append(subIndent).append(propName).append(" = ");
            sb.append(serializeValue(val, indentLevel + 1));
            if (moduleObj.hasCommaAfter(propName)) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent).append("),");
        return sb.toString();
    }

    /**
     * Serialize an NDFValue recursively with indentation
     */
    public static String serializeValue(NDFValue val, int indentLevel) {
        if (val == null) {
            return "nil";
        }
        String indent = "    ".repeat(indentLevel);
        String subIndent = "    ".repeat(indentLevel + 1);

        switch (val.getType()) {
            case STRING: {
                NDFValue.StringValue sv = (NDFValue.StringValue) val;
                if (sv.useDoubleQuotes()) {
                    return "\"" + sv.getValue() + "\"";
                } else {
                    return "'" + sv.getValue() + "'";
                }
            }
            case NUMBER: {
                NDFValue.NumberValue nv = (NDFValue.NumberValue) val;
                if (nv.getOriginalFormat() != null) {
                    return nv.getOriginalFormat();
                }
                if (nv.wasOriginallyInteger()) {
                    return String.valueOf(nv.getIntValue());
                } else {
                    double d = nv.getValue();
                    if (d == (long) d) {
                        return String.format(java.util.Locale.US, "%.1f", d);
                    }
                    return String.valueOf(d);
                }
            }
            case BOOLEAN: {
                return val.toString();
            }
            case GUID: {
                NDFValue.GUIDValue gv = (NDFValue.GUIDValue) val;
                String guid = gv.getGUID();
                if (!guid.startsWith("GUID:")) {
                    return "GUID:{" + guid + "}";
                }
                return guid;
            }
            case ENUM: {
                return val.toString();
            }
            case RESOURCE_REF: {
                NDFValue.ResourceRefValue rv = (NDFValue.ResourceRefValue) val;
                return rv.getPath();
            }
            case TEMPLATE_REF: {
                NDFValue.TemplateRefValue tr = (NDFValue.TemplateRefValue) val;
                if (tr.getInstanceName() != null && !tr.getInstanceName().trim().isEmpty()) {
                    return tr.getInstanceName() + " is " + tr.getPath();
                }
                return tr.getPath();
            }
            case RAW_EXPRESSION: {
                NDFValue.RawExpressionValue rev = (NDFValue.RawExpressionValue) val;
                return rev.getExpression();
            }
            case ARRAY: {
                NDFValue.ArrayValue av = (NDFValue.ArrayValue) val;
                List<NDFValue> elements = av.getElements();
                if (elements.isEmpty()) {
                    return "[]";
                }
                StringBuilder sb = new StringBuilder("[\n");
                for (int i = 0; i < elements.size(); i++) {
                    NDFValue elem = elements.get(i);
                    sb.append(subIndent).append(serializeValue(elem, indentLevel + 1));
                    if (i < elements.size() - 1 || av.hasCommaAfter(i)) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(indent).append("]");
                return sb.toString();
            }
            case TUPLE: {
                NDFValue.TupleValue tv = (NDFValue.TupleValue) val;
                List<NDFValue> elements = tv.getElements();
                if (elements.isEmpty()) {
                    return "()";
                }
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < elements.size(); i++) {
                    sb.append(serializeValue(elements.get(i), indentLevel));
                    if (i < elements.size() - 1) {
                        sb.append(", ");
                    }
                }
                sb.append(")");
                return sb.toString();
            }
            case MAP: {
                NDFValue.MapValue mv = (NDFValue.MapValue) val;
                List<Map.Entry<NDFValue, NDFValue>> entries = mv.getEntries();
                if (entries.isEmpty()) {
                    return "MAP []";
                }
                StringBuilder sb = new StringBuilder("MAP [\n");
                for (int i = 0; i < entries.size(); i++) {
                    Map.Entry<NDFValue, NDFValue> entry = entries.get(i);
                    sb.append(subIndent).append("(")
                      .append(serializeValue(entry.getKey(), indentLevel + 1)).append(", ")
                      .append(serializeValue(entry.getValue(), indentLevel + 1)).append(")");
                    if (i < entries.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(indent).append("]");
                return sb.toString();
            }
            case OBJECT: {
                NDFValue.ObjectValue ov = (NDFValue.ObjectValue) val;
                StringBuilder sb = new StringBuilder();
                sb.append(ov.getTypeName()).append("\n");
                sb.append(indent).append("(\n");
                for (Map.Entry<String, NDFValue> entry : ov.getProperties().entrySet()) {
                    String propName = entry.getKey();
                    NDFValue propVal = entry.getValue();
                    sb.append(subIndent).append(propName).append(" = ");
                    sb.append(serializeValue(propVal, indentLevel + 1));
                    if (ov.hasCommaAfter(propName)) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(indent).append(")");
                return sb.toString();
            }
            case NULL:
                return "nil";
            default:
                return val.toString();
        }
    }
}
