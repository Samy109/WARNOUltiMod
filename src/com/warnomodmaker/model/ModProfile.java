package com.warnomodmaker.model;

import com.warnomodmaker.model.PropertyUpdater.ModificationType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ModProfile {
    private String profileName;
    private String description;
    private LocalDateTime createdDate;
    private LocalDateTime lastModified;
    private String gameVersion;
    private String sourceFileName;
    private String createdBy;
    private List<ModificationRecord> modifications;

    private static final String FORMAT_VERSION = "1.0";

    
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

    
    public ModProfile(String profileName, ModificationTracker tracker, String sourceFileName) {
        this();
        this.profileName = profileName;
        this.sourceFileName = sourceFileName;
        this.modifications = new ArrayList<>(tracker.getLatestModifications());
        this.lastModified = LocalDateTime.now();
    }
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

    
    public void addModification(ModificationRecord modification) {
        modifications.add(modification);
        this.lastModified = LocalDateTime.now();
    }

    
    public boolean removeModification(ModificationRecord modification) {
        boolean removed = modifications.remove(modification);
        if (removed) {
            this.lastModified = LocalDateTime.now();
        }
        return removed;
    }

    
    public int getModificationCount() {
        return modifications.size();
    }

    
    public boolean hasModifications() {
        return !modifications.isEmpty();
    }

    
    public String getFormattedCreatedDate() {
        return createdDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    
    public String getFormattedLastModified() {
        return lastModified.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    
    public void saveToFile(File file) throws IOException {
        saveToFile(file, false);
    }

    /**
     * SPYBORG ENHANCEMENT: Save with _meta/_input structure separation
     */
    public void saveToFile(File file, boolean useMetaInputStructure) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            if (useMetaInputStructure) {
                saveWithMetaInputStructure(writer);
            } else {
                saveLegacyStructure(writer);
            }
        }
    }

    private void saveWithMetaInputStructure(PrintWriter writer) {
        writer.println("{");
        writer.println("  \"_meta\": {");
        writer.println("    \"description\": \"Profile metadata and tracking information\",");
        writer.println("    \"author\": \"" + escapeJson(createdBy) + "\",");
        writer.println("    \"created\": \"" + createdDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
        writer.println("    \"lastModified\": \"" + lastModified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
        writer.println("    \"version\": \"1.0\",");
        writer.println("    \"modificationCount\": " + modifications.size() + ",");
        writer.println("    \"tags\": [\"warno\", \"mod\", \"profile\"]");
        writer.println("  },");
        writer.println("  \"_input\": {");
        writer.println("    \"formatVersion\": \"" + FORMAT_VERSION + "\",");
        writer.println("    \"profileName\": \"" + escapeJson(profileName) + "\",");
        writer.println("    \"description\": \"" + escapeJson(description) + "\",");
        writer.println("    \"gameVersion\": \"" + escapeJson(gameVersion) + "\",");
        writer.println("    \"sourceFileName\": \"" + escapeJson(sourceFileName) + "\",");
        writer.println("    \"createdBy\": \"" + escapeJson(createdBy) + "\",");
        writer.println("    \"createdDate\": \"" + createdDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
        writer.println("    \"lastModified\": \"" + lastModified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
        writer.println("    \"modificationCount\": " + modifications.size() + ",");
        writer.println("    \"modifications\": [");

        for (int i = 0; i < modifications.size(); i++) {
            ModificationRecord mod = modifications.get(i);
            writer.println("      {");
            writer.println("        \"unitName\": \"" + escapeJson(mod.getUnitName()) + "\",");
            writer.println("        \"propertyPath\": \"" + escapeJson(mod.getPropertyPath()) + "\",");
            writer.println("        \"oldValue\": \"" + escapeJson(mod.getOldValue()) + "\",");
            writer.println("        \"newValue\": \"" + escapeJson(mod.getNewValue()) + "\",");
            writer.println("        \"oldValueType\": \"" + mod.getOldValueType() + "\",");
            writer.println("        \"newValueType\": \"" + mod.getNewValueType() + "\",");
            writer.println("        \"timestamp\": \"" + mod.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
            writer.println("        \"modificationType\": \"" + mod.getModificationType().name() + "\",");
            writer.println("        \"modificationDetails\": \"" + escapeJson(mod.getModificationDetails()) + "\"");
            writer.print("      }");
            if (i < modifications.size() - 1) {
                writer.println(",");
            } else {
                writer.println();
            }
        }

        writer.println("    ]");
        writer.println("  }");
        writer.println("}");
    }

    private void saveLegacyStructure(PrintWriter writer) {
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

    
    public static ModProfile loadFromFile(File file) throws IOException {
        ModProfile profile = new ModProfile();
        List<ModificationRecord> mods = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append("\n");
            }
            String content = jsonContent.toString();

            // SPYBORG ENHANCEMENT: Detect if this is a _meta/_input structure or legacy structure
            if (content.contains("\"_input\"") && content.contains("\"_meta\"")) {
                loadFromMetaInputStructure(profile, content);
            } else {
                loadFromLegacyStructure(profile, content);
            }
        }

        return profile;
    }

    private static void loadFromMetaInputStructure(ModProfile profile, String content) {
        // Extract from _input section
        String inputSection = extractJsonSection(content, "_input");
        if (!inputSection.isEmpty()) {
            profile.profileName = extractJsonValue(inputSection, "profileName");
            profile.description = extractJsonValue(inputSection, "description");
            profile.gameVersion = extractJsonValue(inputSection, "gameVersion");
            profile.sourceFileName = extractJsonValue(inputSection, "sourceFileName");
            profile.createdBy = extractJsonValue(inputSection, "createdBy");

            String createdDateStr = extractJsonValue(inputSection, "createdDate");
            if (!createdDateStr.isEmpty()) {
                profile.createdDate = LocalDateTime.parse(createdDateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            String lastModifiedStr = extractJsonValue(inputSection, "lastModified");
            if (!lastModifiedStr.isEmpty()) {
                profile.lastModified = LocalDateTime.parse(lastModifiedStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            profile.modifications = parseModifications(inputSection);
        }
    }

    private static void loadFromLegacyStructure(ModProfile profile, String content) {
        // Extract basic profile information from root level
        profile.profileName = extractJsonValue(content, "profileName");
        profile.description = extractJsonValue(content, "description");
        profile.gameVersion = extractJsonValue(content, "gameVersion");
        profile.sourceFileName = extractJsonValue(content, "sourceFileName");
        profile.createdBy = extractJsonValue(content, "createdBy");

        String createdDateStr = extractJsonValue(content, "createdDate");
        if (!createdDateStr.isEmpty()) {
            profile.createdDate = LocalDateTime.parse(createdDateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        String lastModifiedStr = extractJsonValue(content, "lastModified");
        if (!lastModifiedStr.isEmpty()) {
            profile.lastModified = LocalDateTime.parse(lastModifiedStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        profile.modifications = parseModifications(content);
    }

    private static String extractJsonSection(String content, String sectionName) {
        String pattern = "\"" + sectionName + "\"\\s*:\\s*\\{";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(content);

        if (m.find()) {
            int start = m.end() - 1; // Include the opening brace
            int braceCount = 1;
            int i = start + 1;

            while (i < content.length() && braceCount > 0) {
                char c = content.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                i++;
            }

            if (braceCount == 0) {
                return content.substring(start, i);
            }
        }

        return "";
    }

    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    
    private static String extractJsonValue(String content, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*?)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    
    private static List<ModificationRecord> parseModifications(String content) {
        List<ModificationRecord> modifications = new ArrayList<>();
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
