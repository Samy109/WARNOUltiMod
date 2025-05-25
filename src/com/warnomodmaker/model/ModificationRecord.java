package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.PropertyUpdater.ModificationType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a single modification made to an NDF file.
 * This class tracks what was changed, when, and how.
 */
public class ModificationRecord {
    private final String unitName;
    private final String propertyPath;
    private final String oldValue;
    private final String newValue;
    private final String oldValueType;
    private final String newValueType;
    private final LocalDateTime timestamp;
    private final ModificationType modificationType;
    private final String modificationDetails; // For mass modifications, stores the operation details

    /**
     * Creates a new modification record for a direct value change
     */
    public ModificationRecord(String unitName, String propertyPath,
                            NDFValue oldValue, NDFValue newValue) {
        this(unitName, propertyPath, oldValue, newValue, ModificationType.SET, null);
    }

    /**
     * Creates a new modification record with modification type details
     */
    public ModificationRecord(String unitName, String propertyPath,
                            NDFValue oldValue, NDFValue newValue,
                            ModificationType modificationType, String modificationDetails) {
        this.unitName = unitName;
        this.propertyPath = propertyPath;
        this.oldValue = oldValue != null ? oldValue.toString() : "null";
        this.newValue = newValue != null ? newValue.toString() : "null";
        this.oldValueType = oldValue != null ? oldValue.getType().name() : "NULL";
        this.newValueType = newValue != null ? newValue.getType().name() : "NULL";
        this.timestamp = LocalDateTime.now();
        this.modificationType = modificationType;
        this.modificationDetails = modificationDetails;
    }

    /**
     * Constructor for JSON deserialization
     */
    public ModificationRecord(String unitName, String propertyPath,
                            String oldValue, String newValue,
                            String oldValueType, String newValueType,
                            LocalDateTime timestamp, ModificationType modificationType,
                            String modificationDetails) {
        this.unitName = unitName;
        this.propertyPath = propertyPath;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.oldValueType = oldValueType;
        this.newValueType = newValueType;
        this.timestamp = timestamp;
        this.modificationType = modificationType;
        this.modificationDetails = modificationDetails;
    }

    // Getters
    public String getUnitName() { return unitName; }
    public String getPropertyPath() { return propertyPath; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getOldValueType() { return oldValueType; }
    public String getNewValueType() { return newValueType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public ModificationType getModificationType() { return modificationType; }
    public String getModificationDetails() { return modificationDetails; }

    /**
     * Gets a formatted timestamp string
     */
    public String getFormattedTimestamp() {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Gets a human-readable description of this modification
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(unitName).append(" -> ").append(propertyPath);

        if (modificationType != ModificationType.SET) {
            sb.append(" (").append(modificationType.getDisplayName());
            if (modificationDetails != null) {
                sb.append(": ").append(modificationDetails);
            }
            sb.append(")");
        }

        sb.append(": ").append(getDisplayValue(oldValue)).append(" -> ").append(getDisplayValue(newValue));
        return sb.toString();
    }

    /**
     * Gets a display-friendly version of a value with ellipses for very long strings
     */
    private String getDisplayValue(String value) {
        if (value == null) {
            return "null";
        }

        // For very long values, use ellipses for display
        if (value.length() > 200) {
            return value.substring(0, 197) + "...";
        }

        return value;
    }

    /**
     * Validates that this modification record has valid data
     */
    public boolean isValid() {
        return unitName != null && !unitName.trim().isEmpty() &&
               propertyPath != null && !propertyPath.trim().isEmpty() &&
               oldValue != null && newValue != null &&
               oldValueType != null && newValueType != null &&
               timestamp != null && modificationType != null;
    }

    /**
     * Creates a unique key for this modification (unit + path)
     */
    public String getKey() {
        return unitName + ":" + propertyPath;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ModificationRecord that = (ModificationRecord) obj;
        return Objects.equals(unitName, that.unitName) &&
               Objects.equals(propertyPath, that.propertyPath) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unitName, propertyPath, timestamp);
    }

    @Override
    public String toString() {
        return String.format("ModificationRecord{unit='%s', path='%s', %s -> %s, time=%s}",
                           unitName, propertyPath, oldValue, newValue, getFormattedTimestamp());
    }
}
