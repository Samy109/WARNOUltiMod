package com.warnomodmaker.parser;

import java.util.*;
import java.util.regex.Pattern;

/**
 * LINE-BASED ARCHITECTURE: Tracks original source lines for precise replacement
 * This is the core of the new simple and reliable formatting preservation system
 */
public class SourceLineTracker {
    private final List<String> originalLines;
    private final Map<Integer, String> modifiedLines;
    private final Set<Integer> modifiedLineNumbers;
    private final List<String> insertedLines;
    private final Map<Integer, List<String>> insertionsAtLine;
    
    public SourceLineTracker(String sourceContent) {
        this.originalLines = new ArrayList<>();
        this.modifiedLines = new HashMap<>();
        this.modifiedLineNumbers = new HashSet<>();
        this.insertedLines = new ArrayList<>();
        this.insertionsAtLine = new HashMap<>();

        // Split source content into lines, preserving line endings
        String[] lines = sourceContent.split("\n", -1); // -1 to preserve empty lines
        for (String line : lines) {
            originalLines.add(line);
        }
    }
    
    /**
     * Get the original content of a specific line
     */
    public String getOriginalLine(int lineNumber) {
        if (lineNumber >= 0 && lineNumber < originalLines.size()) {
            return originalLines.get(lineNumber);
        }
        return "";
    }
    
    /**
     * Get the current content of a line (original or modified)
     */
    public String getCurrentLine(int lineNumber) {
        if (modifiedLines.containsKey(lineNumber)) {
            return modifiedLines.get(lineNumber);
        }
        return getOriginalLine(lineNumber);
    }
    
    /**
     * Mark a line as modified with new content
     */
    public void modifyLine(int lineNumber, String newContent) {
        if (lineNumber >= 0 && lineNumber < originalLines.size()) {
            modifiedLines.put(lineNumber, newContent);
            modifiedLineNumbers.add(lineNumber);
        }
    }

    /**
     * Insert a new line at the specified position
     */
    public void insertLine(int lineNumber, String content) {
        if (lineNumber >= 0 && lineNumber <= originalLines.size()) {
            insertionsAtLine.computeIfAbsent(lineNumber, k -> new ArrayList<>()).add(content);
        }
    }
    
    /**
     * Check if a line has been modified
     */
    public boolean isLineModified(int lineNumber) {
        return modifiedLineNumbers.contains(lineNumber);
    }
    
    /**
     * Get all modified line numbers
     */
    public Set<Integer> getModifiedLineNumbers() {
        return new HashSet<>(modifiedLineNumbers);
    }
    
    /**
     * Get the total number of lines
     */
    public int getLineCount() {
        return originalLines.size();
    }
    
    /**
     * Generate the complete output with modifications applied
     */
    public String generateOutput() {
        StringBuilder output = new StringBuilder();

        for (int i = 0; i < originalLines.size(); i++) {
            // Add any insertions before this line
            if (insertionsAtLine.containsKey(i)) {
                for (String insertedLine : insertionsAtLine.get(i)) {
                    if (output.length() > 0) {
                        output.append("\n");
                    }
                    output.append(insertedLine);
                }
            }

            String currentLine = getCurrentLine(i);
            // Only skip lines that were explicitly cleared during modifications
            // (modified to empty string), not original empty lines
            boolean shouldSkip = isLineModified(i) && currentLine.isEmpty();

            if (!shouldSkip) {
                if (output.length() > 0) {
                    output.append("\n");
                }
                output.append(currentLine);
            }
        }

        // Add any insertions after the last line
        if (insertionsAtLine.containsKey(originalLines.size())) {
            for (String insertedLine : insertionsAtLine.get(originalLines.size())) {
                output.append("\n");
                output.append(insertedLine);
            }
        }

        return output.toString();
    }
    
    /**
     * Get a summary of modifications for debugging
     */
    public String getModificationSummary() {
        if (modifiedLineNumbers.isEmpty()) {
            return "No modifications";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Modified lines: ");
        
        List<Integer> sortedLines = new ArrayList<>(modifiedLineNumbers);
        Collections.sort(sortedLines);
        
        for (int i = 0; i < sortedLines.size(); i++) {
            if (i > 0) summary.append(", ");
            summary.append(sortedLines.get(i) + 1); // Convert to 1-based for display
        }
        
        return summary.toString();
    }
    
    /**
     * Clear all modifications (reset to original state)
     */
    public void clearModifications() {
        modifiedLines.clear();
        modifiedLineNumbers.clear();
        insertedLines.clear();
        insertionsAtLine.clear();
    }
    
    /**
     * Replace a property value in a specific line while preserving formatting
     * This is the core method for line-based value replacement
     */
    public boolean replacePropertyInLine(int lineNumber, String propertyName, String oldValue, String newValue) {
        String originalLine = getOriginalLine(lineNumber);
        if (originalLine.isEmpty()) {
            return false;
        }
        
        // Find the property assignment pattern
        String pattern = propertyName + "\\s*=\\s*" + Pattern.quote(oldValue);
        String replacement = propertyName + " = " + newValue;
        
        String newLine = originalLine.replaceFirst(pattern, replacement);
        
        if (!newLine.equals(originalLine)) {
            modifyLine(lineNumber, newLine);
            return true;
        }
        
        return false;
    }
    
    /**
     * Find the line number containing a specific property assignment
     */
    public int findPropertyLine(String propertyName) {
        for (int i = 0; i < originalLines.size(); i++) {
            String line = originalLines.get(i);
            if (line.contains(propertyName + " =") || line.contains(propertyName + "=")) {
                return i;
            }
        }
        return -1;
    }
}
