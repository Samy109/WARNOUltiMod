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
     * Universal property detection for ALL NDF file types and patterns
     * Handles: function parameters, standalone properties, nested objects, arrays, maps, etc.
     */
    private boolean containsProperty(String line, String propertyPath) {
        String propertyName = extractFinalPropertyName(propertyPath);
        String trimmed = line.trim();

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
        if (line.contains("(") && line.contains(",") && line.contains(")")) {
            // For MAP patterns, the property name might be the key
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
