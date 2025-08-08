package com.warnomodmaker.parser;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.ModificationTracker;
import com.warnomodmaker.model.ModificationRecord;
import com.warnomodmaker.model.PropertyUpdater;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * LINE-BASED ARCHITECTURE: Simple and reliable writer that replaces only modified lines
 * This solves the git diff problem by preserving all unmodified content exactly
 */
public class LineBasedWriter {
    private final Writer writer;
    private final SourceLineTracker lineTracker;
    private final ModificationTracker modificationTracker;
    
    public LineBasedWriter(Writer writer, String originalContent, ModificationTracker modificationTracker) {
        this.writer = writer;
        this.lineTracker = new SourceLineTracker(originalContent);
        this.modificationTracker = modificationTracker;
    }
    
    /**
     * Write the file with line-based modifications applied
     */
    public void write(List<NDFValue.ObjectValue> objects) throws IOException {
        // Apply all modifications to the line tracker
        applyModifications();
        
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

        // Handle property additions differently
        if (mod.getModificationType() == PropertyUpdater.ModificationType.PROPERTY_ADDED) {
            boolean success = addPropertyLine(unitName, propertyPath, newValue);
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
            throw new IllegalStateException("Could not find property line for " + unitName + "." + propertyPath + " - NO FALLBACKS ALLOWED");
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

            System.out.println("DEBUG: Searching for " + propertyPath + " in unit " + unitName + " from line " + (unitLineStart + 1) + " to " + unitEnd);

            // For complex nested array paths with multiple array levels, use structural navigation
            // Example: TurretDescriptorList[1].MountedWeaponDescriptorList[0].Ammunition
            if (propertyPath.contains("[") && propertyPath.contains(".") &&
                propertyPath.split("\\[").length > 2) { // More than one array access
                return findPropertyLineWithStructuralNavigation(unitLineStart, unitEnd, propertyPath);
            }

            // For simple properties, use the original logic
            for (int i = unitLineStart; i < unitEnd; i++) {
                String line = lineTracker.getOriginalLine(i);
                if (containsProperty(line, propertyPath)) {
                    System.out.println("DEBUG: Found property at line " + (i + 1) + ": " + line.trim());
                    return i;
                }
            }
            System.out.println("DEBUG: Property not found in range");
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

            // Debug: Print when we think we found the end
            if (foundOpenParen && parenDepth == 0) {
                System.out.println("DEBUG: Found unit end at line " + (i + 1) + ": " + line.trim());
                return i + 1;
            }

            // If we hit another export, this is the end
            if (i > unitStart && line.trim().startsWith("export ")) {
                System.out.println("DEBUG: Found next export at line " + (i + 1) + ": " + line.trim());
                return i;
            }
        }

        return lineTracker.getLineCount();
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

        // Look for object constructors (with or without opening paren on same line)
        if (trimmed.contains("(")) {
            return true;
        }

        // Look for type names that start array elements (like "TTurretTwoAxisDescriptor")
        if (trimmed.matches("^T[A-Z][a-zA-Z]*$")) {
            return true;
        }

        // Look for property assignments that start elements
        if (trimmed.contains("=") && !trimmed.startsWith("export")) {
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

        // Debug for the specific case we're tracking
        if (propertyPath.contains("ProductionRessourcesNeeded") && line.contains("$/GFX/Resources/Resource_CommandPoints")) {
            System.out.println("DEBUG: Checking line for ProductionRessourcesNeeded: " + line.trim());
            System.out.println("DEBUG: PropertyPath: " + propertyPath);
            System.out.println("DEBUG: PropertyName: " + propertyName);
        }

        // UNIVERSAL PATTERN 1: Standalone property assignment
        // Examples: "MaxPhysicalDamages = 6", "  Coalition = ECoalition/PACT"
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
                System.out.println("DEBUG: MAP key access detected, looking for key: " + mapKey);
                boolean found = line.contains(mapKey);
                System.out.println("DEBUG: MAP key found in line: " + found);
                return found;
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
                    System.out.println("DEBUG: Checking MAP key '" + mapKey + "' in line: " + line.trim());
                    // Check if the line contains this MAP key
                    boolean found = line.contains(mapKey);
                    System.out.println("DEBUG: MAP key found: " + found);
                    return found;
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

        // Clean the values for comparison
        String cleanOldValue = cleanValue(oldValue);
        String cleanNewValue = cleanValue(newValue);

        // Try different replacement strategies for single-line values
        String newLine = originalLine;

        // Strategy 1: Direct value replacement
        if (originalLine.contains(cleanOldValue)) {
            newLine = originalLine.replace(cleanOldValue, cleanNewValue);
        }
        // Strategy 2: Quoted value replacement
        else if (originalLine.contains("'" + cleanOldValue + "'")) {
            newLine = originalLine.replace("'" + cleanOldValue + "'", "'" + cleanNewValue + "'");
        }
        else if (originalLine.contains("\"" + cleanOldValue + "\"")) {
            newLine = originalLine.replace("\"" + cleanOldValue + "\"", "\"" + cleanNewValue + "\"");
        }

        // Apply the modification if the line changed
        if (!newLine.equals(originalLine)) {
            lineTracker.modifyLine(lineNumber, newLine);
            return true;
        }

        return false;
    }

    /**
     * Add a new property line to a unit
     */
    private boolean addPropertyLine(String unitName, String propertyPath, String newValue) {
        // Find the unit definition
        int unitLineStart = findUnitDefinitionLine(unitName);
        if (unitLineStart < 0) {
            return false;
        }

        // Find the best insertion point within the unit
        int insertionPoint = findPropertyInsertionPoint(unitLineStart, propertyPath);
        if (insertionPoint < 0) {
            return false;
        }

        // Create the new property line with proper formatting
        String newPropertyLine = createPropertyLine(propertyPath, newValue, insertionPoint);

        // Insert the new line
        lineTracker.insertLine(insertionPoint, newPropertyLine);

        return true;
    }

    /**
     * Find the best insertion point for a new property within a unit
     */
    private int findPropertyInsertionPoint(int unitLineStart, String propertyPath) {
        int unitEnd = findUnitDefinitionEnd(unitLineStart);

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
     * Create a properly formatted property line
     */
    private String createPropertyLine(String propertyPath, String newValue, int insertionPoint) {
        // Get the indentation from the surrounding lines
        String indentation = getIndentationFromContext(insertionPoint);

        // Extract the property name from the path
        String propertyName = extractFinalPropertyName(propertyPath);

        // Clean the value
        String cleanValue = cleanValue(newValue);

        // Create the property line with proper formatting
        return indentation + propertyName + " = " + cleanValue + ",";
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
}
