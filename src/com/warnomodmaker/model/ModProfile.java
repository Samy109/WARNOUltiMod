package com.warnomodmaker.model;

import com.warnomodmaker.model.PropertyUpdater.ModificationType;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Represents a complete mod profile that can be saved to and loaded from JSON.
 * Contains all modifications made during a session along with metadata.
 */
public class ModProfile {
    private String profileName;
    private String description;
    private LocalDateTime createdDate;
    private LocalDateTime lastModified;
    private String gameVersion;
    private String sourceFileName;
    private String createdBy;
    private List<ModificationRecord> modifications;

    // JSON format version for compatibility
    private static final String FORMAT_VERSION = "1.0";

    /**
     * Creates a new empty mod profile
     */
    public ModProfile() {
        this.profileName = "Untitled Profile";
        this.description = "";
        this.createdDate = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        this.gameVersion = "Unknown";
        this.sourceFileName = "";
        this.createdBy = "WARNO Mod Maker";
        this.modifications = new ArrayList<>();
    }

    /**
     * Creates a mod profile from a modification tracker
     */
    public ModProfile(String profileName, ModificationTracker tracker, String sourceFileName) {
        this();
        this.profileName = profileName;
        this.sourceFileName = sourceFileName;
        this.modifications = new ArrayList<>(tracker.getLatestModifications());
        this.lastModified = LocalDateTime.now();
    }

    // Getters and Setters
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) {
        this.profileName = profileName;
        this.lastModified = LocalDateTime.now();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        this.lastModified = LocalDateTime.now();
    }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public LocalDateTime getLastModified() { return lastModified; }
    public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }

    public String getGameVersion() { return gameVersion; }
    public void setGameVersion(String gameVersion) {
        this.gameVersion = gameVersion;
        this.lastModified = LocalDateTime.now();
    }

    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
        this.lastModified = LocalDateTime.now();
    }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public List<ModificationRecord> getModifications() { return new ArrayList<>(modifications); }
    public void setModifications(List<ModificationRecord> modifications) {
        this.modifications = new ArrayList<>(modifications);
        this.lastModified = LocalDateTime.now();
    }

    /**
     * Adds a modification to the profile
     */
    public void addModification(ModificationRecord modification) {
        modifications.add(modification);
        this.lastModified = LocalDateTime.now();
    }

    /**
     * Removes a modification from the profile
     */
    public boolean removeModification(ModificationRecord modification) {
        boolean removed = modifications.remove(modification);
        if (removed) {
            this.lastModified = LocalDateTime.now();
        }
        return removed;
    }

    /**
     * Gets the number of modifications in this profile
     */
    public int getModificationCount() {
        return modifications.size();
    }

    /**
     * Checks if the profile has any modifications
     */
    public boolean hasModifications() {
        return !modifications.isEmpty();
    }

    /**
     * Gets formatted creation date
     */
    public String getFormattedCreatedDate() {
        return createdDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Gets formatted last modified date
     */
    public String getFormattedLastModified() {
        return lastModified.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Saves the profile to a JSON file
     */
    public void saveToFile(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("{");
            writer.println("  \"formatVersion\": \"" + FORMAT_VERSION + "\",");
            writer.println("  \"profileName\": \"" + escapeJson(profileName) + "\",");
            writer.println("  \"description\": \"" + escapeJson(description) + "\",");
            writer.println("  \"createdDate\": \"" + createdDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
            writer.println("  \"lastModified\": \"" + lastModified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
            writer.println("  \"gameVersion\": \"" + escapeJson(gameVersion) + "\",");
            writer.println("  \"sourceFileName\": \"" + escapeJson(sourceFileName) + "\",");
            writer.println("  \"createdBy\": \"" + escapeJson(createdBy) + "\",");
            writer.println("  \"modificationCount\": " + modifications.size() + ",");
            writer.println("  \"modifications\": [");

            for (int i = 0; i < modifications.size(); i++) {
                ModificationRecord mod = modifications.get(i);
                writer.println("    {");
                writer.println("      \"unitName\": \"" + escapeJson(mod.getUnitName()) + "\",");
                writer.println("      \"propertyPath\": \"" + escapeJson(mod.getPropertyPath()) + "\",");
                writer.println("      \"oldValue\": \"" + escapeJson(mod.getOldValue()) + "\",");
                writer.println("      \"newValue\": \"" + escapeJson(mod.getNewValue()) + "\",");
                writer.println("      \"oldValueType\": \"" + mod.getOldValueType() + "\",");
                writer.println("      \"newValueType\": \"" + mod.getNewValueType() + "\",");
                writer.println("      \"timestamp\": \"" + mod.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
                writer.println("      \"modificationType\": \"" + mod.getModificationType().name() + "\",");
                writer.println("      \"modificationDetails\": \"" + escapeJson(mod.getModificationDetails()) + "\"");
                writer.print("    }");
                if (i < modifications.size() - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }

            writer.println("  ]");
            writer.println("}");
        }
    }

    /**
     * Loads a profile from a JSON file
     */
    public static ModProfile loadFromFile(File file) throws IOException {
        ModProfile profile = new ModProfile();
        List<ModificationRecord> mods = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append("\n");
            }

            // Parse the JSON content
            String content = jsonContent.toString();

            // Extract basic profile information
            profile.profileName = extractJsonValue(content, "profileName");
            profile.description = extractJsonValue(content, "description");
            profile.gameVersion = extractJsonValue(content, "gameVersion");
            profile.sourceFileName = extractJsonValue(content, "sourceFileName");
            profile.createdBy = extractJsonValue(content, "createdBy");

            // Parse dates
            String createdDateStr = extractJsonValue(content, "createdDate");
            if (!createdDateStr.isEmpty()) {
                profile.createdDate = LocalDateTime.parse(createdDateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            String lastModifiedStr = extractJsonValue(content, "lastModified");
            if (!lastModifiedStr.isEmpty()) {
                profile.lastModified = LocalDateTime.parse(lastModifiedStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            // Parse modifications array
            mods = parseModifications(content);
            profile.modifications = mods;
        }

        return profile;
    }

    /**
     * Simple JSON string escaping
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Extracts a JSON value by key from the content
     */
    private static String extractJsonValue(String content, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*?)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * Parses the modifications array from JSON content
     */
    private static List<ModificationRecord> parseModifications(String content) {
        List<ModificationRecord> modifications = new ArrayList<>();

        // Find the modifications array
        int modificationsStart = content.indexOf("\"modifications\": [");
        if (modificationsStart == -1) {
            return modifications;
        }

        int arrayStart = content.indexOf("[", modificationsStart);
        int arrayEnd = findMatchingBracket(content, arrayStart);

        if (arrayStart == -1 || arrayEnd == -1) {
            return modifications;
        }

        String modificationsArray = content.substring(arrayStart + 1, arrayEnd);

        // Split by objects (simple approach)
        String[] objects = modificationsArray.split("\\},\\s*\\{");

        for (String obj : objects) {
            // Clean up the object string
            obj = obj.trim();
            if (obj.startsWith("{")) {
                obj = obj.substring(1);
            }
            if (obj.endsWith("}")) {
                obj = obj.substring(0, obj.length() - 1);
            }

            if (obj.trim().isEmpty()) {
                continue;
            }

            // Parse individual modification
            try {
                String unitName = extractValueFromObject(obj, "unitName");
                String propertyPath = extractValueFromObject(obj, "propertyPath");
                String oldValue = extractValueFromObject(obj, "oldValue");
                String newValue = extractValueFromObject(obj, "newValue");
                String oldValueType = extractValueFromObject(obj, "oldValueType");
                String newValueType = extractValueFromObject(obj, "newValueType");
                String timestampStr = extractValueFromObject(obj, "timestamp");
                String modificationTypeStr = extractValueFromObject(obj, "modificationType");
                String modificationDetails = extractValueFromObject(obj, "modificationDetails");

                LocalDateTime timestamp = LocalDateTime.now();
                if (!timestampStr.isEmpty()) {
                    timestamp = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }

                PropertyUpdater.ModificationType modificationType = PropertyUpdater.ModificationType.SET;
                if (!modificationTypeStr.isEmpty()) {
                    try {
                        modificationType = PropertyUpdater.ModificationType.valueOf(modificationTypeStr);
                    } catch (IllegalArgumentException e) {
                        // Use default
                    }
                }

                ModificationRecord record = new ModificationRecord(
                    unitName, propertyPath, oldValue, newValue, oldValueType, newValueType,
                    timestamp, modificationType, modificationDetails
                );

                modifications.add(record);
            } catch (Exception e) {
                // Skip malformed modification records
                System.err.println("Failed to parse modification record: " + e.getMessage());
            }
        }

        return modifications;
    }

    /**
     * Finds the matching closing bracket for an opening bracket
     */
    private static int findMatchingBracket(String content, int openIndex) {
        if (openIndex >= content.length() || content.charAt(openIndex) != '[') {
            return -1;
        }

        int count = 1;
        for (int i = openIndex + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '[') {
                count++;
            } else if (c == ']') {
                count--;
                if (count == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Extracts a value from a JSON object string
     */
    private static String extractValueFromObject(String objectStr, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*?)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(objectStr);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    @Override
    public String toString() {
        return String.format("ModProfile{name='%s', modifications=%d, created=%s}",
                           profileName, modifications.size(), getFormattedCreatedDate());
    }
}
