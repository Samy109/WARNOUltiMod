package com.warnomodmaker.parser;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.ModificationTracker;
import com.warnomodmaker.model.ModificationRecord;
import com.warnomodmaker.model.PropertyUpdater;

import java.io.IOException;
import java.io.Writer;
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

        // Clean the values for comparison
        String cleanOldValue = cleanValue(oldValue);
        String cleanNewValue = cleanValue(newValue);

        // Try different replacement strategies
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
        if (propertyPath.contains(".")) {
            return propertyPath.substring(propertyPath.lastIndexOf(".") + 1);
        }
        return propertyPath;
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
