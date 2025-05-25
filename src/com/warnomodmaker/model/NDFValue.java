package com.warnomodmaker.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a value in an NDF file.
 * This is the base class for all value types in the NDF format.
 */
public abstract class NDFValue {

    /**
     * Enumeration of supported NDF file types
     */
    public enum NDFFileType {
        UNITE_DESCRIPTOR("UniteDescriptor.ndf", "TEntityDescriptor", "Unit"),
        MISSILE_DESCRIPTORS("MissileDescriptors.ndf", "TEntityDescriptor", "Missile"),
        MISSILE_CARRIAGE("MissileCarriage.ndf", "TMissileCarriageConnoisseur", "Missile Carriage"),
        WEAPON_DESCRIPTOR("WeaponDescriptor.ndf", "TWeaponManagerModuleDescriptor", "Weapon"),
        AMMUNITION("Ammunition.ndf", "TAmmunitionDescriptor", "Ammunition"),
        AMMUNITION_MISSILES("AmmunitionMissiles.ndf", "TAmmunitionDescriptor", "Missile Ammunition"),
        BUILDING_DESCRIPTORS("BuildingDescriptors.ndf", "TBuildingDescriptor", "Building"),
        BUILDING_CADAVRE_DESCRIPTORS("BuildingCadavreDescriptors.ndf", "TBuildingCadavreDescriptor", "Building Cadavre"),
        CAPACITE_LIST("CapaciteList.ndf", "TCapaciteDescriptor", "Capacite"),
        CONDITIONS_DESCRIPTOR("ConditionsDescriptor.ndf", "TConditionDescriptor", "Condition"),
        DAMAGE_LEVELS("DamageLevels.ndf", "TDamageLevelsPackDescriptor", "Damage Level"),
        DAMAGE_RESISTANCE("DamageResistance.ndf", "TDamageResistanceDescriptor", "Damage Resistance"),
        DAMAGE_RESISTANCE_FAMILY_LIST("DamageResistanceFamilyList.ndf", "TDamageResistanceFamilyDescriptor", "Damage Resistance Family"),
        DAMAGE_RESISTANCE_FAMILY_LIST_IMPL("DamageResistanceFamilyListImpl.ndf", "TDamageResistanceFamilyDescriptor", "Damage Resistance Family Impl"),
        DAMAGE_STAIR_TYPE_EVOLUTION("DamageStairTypeEvolutionOverRangeDescriptor.ndf", "TDamageStairTypeEvolutionOverRangeDescriptor", "Damage Stair Type Evolution"),
        EFFETS_SUR_UNITE("EffetsSurUnite.ndf", "TEffetSurUniteDescriptor", "Effect on Unit"),
        EXPERIENCE_LEVELS("ExperienceLevels.ndf", "TExperienceLevelDescriptor", "Experience Level"),
        FIRE_DESCRIPTOR("FireDescriptor.ndf", "TFireDescriptor", "Fire"),
        GENERATED_DEPICTION_FX_MISSILES("GeneratedDepictionFXMissiles.ndf", "TDepictionDescriptor", "Depiction FX Missile"),
        GENERATED_DEPICTION_FX_WEAPONS("GeneratedDepictionFXWeapons.ndf", "TDepictionDescriptor", "Depiction FX Weapon"),
        GENERATED_DEPICTION_WEAPON_BLOCK("GeneratedDepictionWeaponBlock.ndf", "TDepictionDescriptor", "Depiction Weapon Block"),
        MIMETIC_IMPACT_MAPPING("MimeticImpactMapping.ndf", "TMimeticImpactMappingDescriptor", "Mimetic Impact Mapping"),
        MISSILE_CARRIAGE_DEPICTION("MissileCarriageDepiction.ndf", "TMissileCarriageDepictionDescriptor", "Missile Carriage Depiction"),
        NDF_DEPICTION_LIST("NdfDepictionList.ndf", "TDepictionDescriptor", "Depiction"),
        ORDER_AVAILABILITY_TACTIC("OrderAvailability_Tactic.ndf", "TOrderAvailabilityDescriptor", "Order Availability"),
        PLAYER_MISSION_TAGS("PlayerMissionTags.ndf", "TPlayerMissionTagDescriptor", "Player Mission Tag"),
        SHOW_ROOM_UNITS("ShowRoomUnits.ndf", "TShowRoomUnitDescriptor", "Show Room Unit"),
        SKINS("Skins.ndf", "TSkinDescriptor", "Skin"),
        SMOKE_DESCRIPTOR("SmokeDescriptor.ndf", "TSmokeDescriptor", "Smoke"),
        UNITE_CADAVRE_DESCRIPTOR("UniteCadavreDescriptor.ndf", "TUniteCadavreDescriptor", "Unit Cadavre"),
        UNKNOWN("", "", "Unknown");

        private final String filename;
        private final String rootObjectType;
        private final String displayName;

        NDFFileType(String filename, String rootObjectType, String displayName) {
            this.filename = filename;
            this.rootObjectType = rootObjectType;
            this.displayName = displayName;
        }

        public String getFilename() { return filename; }
        public String getRootObjectType() { return rootObjectType; }
        public String getDisplayName() { return displayName; }

        /**
         * Determines the file type based on the filename
         */
        public static NDFFileType fromFilename(String filename) {
            if (filename == null) return UNKNOWN;

            String name = filename.toLowerCase();

            // Check exact filename matches first
            for (NDFFileType type : values()) {
                if (type != UNKNOWN && name.equalsIgnoreCase(type.filename)) {
                    return type;
                }
            }

            // Fallback to endsWith checks for backwards compatibility
            if (name.endsWith("unitedescriptor.ndf")) return UNITE_DESCRIPTOR;
            if (name.endsWith("missiledescriptors.ndf")) return MISSILE_DESCRIPTORS;
            if (name.endsWith("missilecarriage.ndf")) return MISSILE_CARRIAGE;
            if (name.endsWith("weapondescriptor.ndf")) return WEAPON_DESCRIPTOR;
            if (name.endsWith("ammunitionmissiles.ndf")) return AMMUNITION_MISSILES;
            if (name.endsWith("ammunition.ndf")) return AMMUNITION;

            return UNKNOWN;
        }
    }

    /**
     * Types of values in the NDF file format
     */
    public enum ValueType {
        STRING,
        NUMBER,
        BOOLEAN,
        ARRAY,
        TUPLE,
        MAP,
        OBJECT,
        TEMPLATE_REF,
        RESOURCE_REF,
        GUID,
        ENUM,
        RAW_EXPRESSION,
        NULL
    }

    /**
     * Gets the type of this value
     *
     * @return The value type
     */
    public abstract ValueType getType();

    /**
     * Creates a string value
     *
     * @param value The string value
     * @return A new string value
     */
    public static NDFValue createString(String value) {
        return new StringValue(value);
    }

    /**
     * Creates a string value with specific quote type
     *
     * @param value The string value
     * @param useDoubleQuotes Whether to use double quotes
     * @return A new string value
     */
    public static NDFValue createString(String value, boolean useDoubleQuotes) {
        return new StringValue(value, useDoubleQuotes);
    }

    /**
     * Creates a number value
     *
     * @param value The number value
     * @return A new number value
     */
    public static NDFValue createNumber(double value) {
        return new NumberValue(value);
    }

    /**
     * Creates a number value with format preservation
     *
     * @param value The number value
     * @param wasOriginallyInteger Whether the original value was an integer
     * @return A new number value
     */
    public static NDFValue createNumber(double value, boolean wasOriginallyInteger) {
        return new NumberValue(value, wasOriginallyInteger);
    }

    /**
     * Creates a number value with original format preservation
     *
     * @param value The number value
     * @param originalFormat The original format string from the NDF file
     * @return A new number value
     */
    public static NDFValue createNumber(double value, String originalFormat) {
        return new NumberValue(value, originalFormat);
    }

    /**
     * Creates a boolean value
     *
     * @param value The boolean value
     * @return A new boolean value
     */
    public static NDFValue createBoolean(boolean value) {
        return new BooleanValue(value);
    }

    /**
     * Creates an array value
     *
     * @return A new empty array value
     */
    public static ArrayValue createArray() {
        return new ArrayValue();
    }

    /**
     * Creates a tuple value
     *
     * @return A new empty tuple value
     */
    public static TupleValue createTuple() {
        return new TupleValue();
    }

    /**
     * Creates a map value
     *
     * @return A new empty map value
     */
    public static MapValue createMap() {
        return new MapValue();
    }

    /**
     * Creates an object value
     *
     * @param typeName The type name of the object
     * @return A new empty object value
     */
    public static ObjectValue createObject(String typeName) {
        return new ObjectValue(typeName);
    }

    /**
     * Creates a template reference value
     *
     * @param path The reference path
     * @return A new template reference value
     */
    public static NDFValue createTemplateRef(String path) {
        return new TemplateRefValue(path);
    }

    /**
     * Creates a resource reference value
     *
     * @param path The reference path
     * @return A new resource reference value
     */
    public static NDFValue createResourceRef(String path) {
        return new ResourceRefValue(path);
    }

    /**
     * Creates a GUID value
     *
     * @param guid The GUID string
     * @return A new GUID value
     */
    public static NDFValue createGUID(String guid) {
        return new GUIDValue(guid);
    }

    /**
     * Creates an enum value
     *
     * @param enumType The enum type
     * @param enumValue The enum value
     * @return A new enum value
     */
    public static NDFValue createEnum(String enumType, String enumValue) {
        return new EnumValue(enumType, enumValue);
    }

    /**
     * Creates a raw expression value (for complex expressions that should be preserved as-is)
     *
     * @param expression The raw expression string
     * @return A new raw expression value
     */
    public static NDFValue createRawExpression(String expression) {
        return new RawExpressionValue(expression);
    }

    /**
     * String value implementation
     */
    public static class StringValue extends NDFValue {
        private final String value;
        private final boolean useDoubleQuotes; // Track original quote type

        public StringValue(String value) {
            this.value = value;
            this.useDoubleQuotes = false; // Default to single quotes
        }

        public StringValue(String value, boolean useDoubleQuotes) {
            this.value = value;
            this.useDoubleQuotes = useDoubleQuotes;
        }

        public String getValue() {
            return value;
        }

        public boolean useDoubleQuotes() {
            return useDoubleQuotes;
        }

        @Override
        public ValueType getType() {
            return ValueType.STRING;
        }

        @Override
        public String toString() {
            if (useDoubleQuotes) {
                return "\"" + value + "\"";
            } else {
                return "'" + value + "'";
            }
        }
    }

    /**
     * Number value implementation with format preservation and smart rounding
     */
    public static class NumberValue extends NDFValue {
        private final double value;
        private final boolean wasOriginallyInteger; // Track if the original value was an integer
        private final String originalFormat; // Store original format for preservation

        public NumberValue(double value) {
            this.value = value;
            this.wasOriginallyInteger = (value == Math.floor(value) && !Double.isInfinite(value));
            this.originalFormat = null;
        }

        public NumberValue(double value, boolean wasOriginallyInteger) {
            this.value = value;
            this.wasOriginallyInteger = wasOriginallyInteger;
            this.originalFormat = null;
        }

        public NumberValue(double value, String originalFormat) {
            this.value = value;
            this.originalFormat = originalFormat;
            // Determine if original was integer by checking if format contains decimal point
            this.wasOriginallyInteger = originalFormat != null && !originalFormat.contains(".");
        }

        public double getValue() {
            return value;
        }

        public boolean wasOriginallyInteger() {
            return wasOriginallyInteger;
        }

        public String getOriginalFormat() {
            return originalFormat;
        }

        /**
         * Gets the value rounded appropriately based on the original format
         */
        public double getRoundedValue() {
            if (wasOriginallyInteger) {
                return Math.round(value);
            }
            return value;
        }

        /**
         * Gets the value as an integer if it was originally an integer
         */
        public int getIntValue() {
            return (int) Math.round(value);
        }

        @Override
        public ValueType getType() {
            return ValueType.NUMBER;
        }

        @Override
        public String toString() {
            // If we have original format information, try to preserve it
            if (originalFormat != null) {
                if (wasOriginallyInteger) {
                    return Integer.toString((int) Math.round(value));
                } else {
                    // For decimals, preserve reasonable precision
                    if (Math.abs(value - Math.round(value)) < 0.0001) {
                        // Very close to integer, but was originally decimal
                        return String.format("%.1f", value);
                    }
                    return Double.toString(value);
                }
            }

            // Legacy behavior with smart rounding
            if (wasOriginallyInteger || (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e10)) {
                return Integer.toString((int) Math.round(value));
            }
            return Double.toString(value);
        }
    }

    /**
     * Boolean value implementation
     */
    public static class BooleanValue extends NDFValue {
        private final boolean value;

        public BooleanValue(boolean value) {
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }

        @Override
        public ValueType getType() {
            return ValueType.BOOLEAN;
        }

        @Override
        public String toString() {
            return value ? "True" : "False";
        }
    }

    /**
     * Array value implementation
     */
    public static class ArrayValue extends NDFValue {
        private final List<NDFValue> elements;
        private final List<Boolean> hasCommaAfter; // Tracks which elements have commas after them

        public ArrayValue() {
            this.elements = new ArrayList<>();
            this.hasCommaAfter = new ArrayList<>();
        }

        public void add(NDFValue element) {
            elements.add(element);
            hasCommaAfter.add(false); // Default to no comma
        }

        public void add(NDFValue element, boolean hasComma) {
            elements.add(element);
            hasCommaAfter.add(hasComma);
        }

        public List<NDFValue> getElements() {
            return elements;
        }

        public boolean hasCommaAfter(int index) {
            return index < hasCommaAfter.size() && hasCommaAfter.get(index);
        }

        public void setCommaAfter(int index, boolean hasComma) {
            if (index < hasCommaAfter.size()) {
                hasCommaAfter.set(index, hasComma);
            }
        }

        /**
         * Removes an element at the specified index and maintains comma tracking
         */
        public NDFValue remove(int index) {
            if (index >= 0 && index < elements.size()) {
                NDFValue removed = elements.remove(index);
                // Also remove the corresponding comma tracking
                if (index < hasCommaAfter.size()) {
                    hasCommaAfter.remove(index);
                }
                return removed;
            }
            return null;
        }

        /**
         * Clears all elements and comma tracking
         */
        public void clear() {
            elements.clear();
            hasCommaAfter.clear();
        }

        @Override
        public ValueType getType() {
            return ValueType.ARRAY;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(elements.get(i));
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * Tuple value implementation (for pairs like (key, value))
     */
    public static class TupleValue extends NDFValue {
        private final List<NDFValue> elements;
        private final List<Boolean> hasCommaAfter; // Tracks which elements have commas after them

        public TupleValue() {
            this.elements = new ArrayList<>();
            this.hasCommaAfter = new ArrayList<>();
        }

        public void add(NDFValue element) {
            elements.add(element);
            hasCommaAfter.add(false); // Default to no comma
        }

        public void add(NDFValue element, boolean hasComma) {
            elements.add(element);
            hasCommaAfter.add(hasComma);
        }

        public List<NDFValue> getElements() {
            return elements;
        }

        public boolean hasCommaAfter(int index) {
            return index < hasCommaAfter.size() && hasCommaAfter.get(index);
        }

        public void setCommaAfter(int index, boolean hasComma) {
            if (index < hasCommaAfter.size()) {
                hasCommaAfter.set(index, hasComma);
            }
        }

        @Override
        public ValueType getType() {
            return ValueType.TUPLE;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(elements.get(i));
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * Map value implementation
     */
    public static class MapValue extends NDFValue {
        private final List<Map.Entry<NDFValue, NDFValue>> entries;
        private final List<Boolean> hasCommaAfter; // Tracks which entries have commas after them

        public MapValue() {
            this.entries = new ArrayList<>();
            this.hasCommaAfter = new ArrayList<>();
        }

        public void add(NDFValue key, NDFValue value) {
            entries.add(Map.entry(key, value));
            hasCommaAfter.add(false); // Default to no comma
        }

        public void add(NDFValue key, NDFValue value, boolean hasComma) {
            entries.add(Map.entry(key, value));
            hasCommaAfter.add(hasComma);
        }

        public List<Map.Entry<NDFValue, NDFValue>> getEntries() {
            return entries;
        }

        public boolean hasCommaAfter(int index) {
            return index < hasCommaAfter.size() && hasCommaAfter.get(index);
        }

        public void setCommaAfter(int index, boolean hasComma) {
            if (index < hasCommaAfter.size()) {
                hasCommaAfter.set(index, hasComma);
            }
        }

        @Override
        public ValueType getType() {
            return ValueType.MAP;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("MAP [");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Map.Entry<NDFValue, NDFValue> entry = entries.get(i);
                sb.append("(").append(entry.getKey()).append(", ").append(entry.getValue()).append(")");
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * Object value implementation
     */
    public static class ObjectValue extends NDFValue {
        private final String typeName;
        private final Map<String, NDFValue> properties;
        private final Map<String, Boolean> hasCommaAfter; // Tracks which properties have commas after them
        private String instanceName;
        private String moduleIdentifier; // For identifying modules by type/name instead of index
        private boolean isExported; // Track if this was originally exported

        public ObjectValue(String typeName) {
            this.typeName = typeName;
            this.properties = new LinkedHashMap<>();
            this.hasCommaAfter = new LinkedHashMap<>();
            this.instanceName = null;
            this.moduleIdentifier = null;
            this.isExported = false; // Default to not exported
        }

        public void setProperty(String name, NDFValue value) {
            properties.put(name, value);
            hasCommaAfter.put(name, false); // Default to no comma
        }

        public void setProperty(String name, NDFValue value, boolean hasComma) {
            properties.put(name, value);
            hasCommaAfter.put(name, hasComma);
        }

        public NDFValue getProperty(String name) {
            return properties.get(name);
        }

        public Map<String, NDFValue> getProperties() {
            return properties;
        }

        public boolean hasCommaAfter(String propertyName) {
            return hasCommaAfter.getOrDefault(propertyName, false);
        }

        public void setCommaAfter(String propertyName, boolean hasComma) {
            hasCommaAfter.put(propertyName, hasComma);
        }

        public String getTypeName() {
            return typeName;
        }

        public String getInstanceName() {
            return instanceName;
        }

        public void setInstanceName(String instanceName) {
            this.instanceName = instanceName;
        }

        public void setModuleIdentifier(String moduleIdentifier) {
            this.moduleIdentifier = moduleIdentifier;
        }

        public String getModuleIdentifier() {
            return moduleIdentifier;
        }

        public boolean isExported() {
            return isExported;
        }

        public void setExported(boolean exported) {
            this.isExported = exported;
        }

        @Override
        public ValueType getType() {
            return ValueType.OBJECT;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (instanceName != null) {
                sb.append(instanceName).append(" is ");
            }
            sb.append(typeName).append("(");
            boolean first = true;
            for (Map.Entry<String, NDFValue> entry : properties.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(entry.getKey()).append(" = ").append(entry.getValue());
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * Template reference value implementation
     */
    public static class TemplateRefValue extends NDFValue {
        private final String path;
        private String instanceName;

        public TemplateRefValue(String path) {
            this.path = path;
            this.instanceName = null;
        }

        public String getPath() {
            return path;
        }

        public String getInstanceName() {
            return instanceName;
        }

        public void setInstanceName(String instanceName) {
            this.instanceName = instanceName;
        }

        @Override
        public ValueType getType() {
            return ValueType.TEMPLATE_REF;
        }

        @Override
        public String toString() {
            if (instanceName != null) {
                return instanceName + " is " + path;
            }
            return path;
        }
    }

    /**
     * Resource reference value implementation
     */
    public static class ResourceRefValue extends NDFValue {
        private final String path;

        public ResourceRefValue(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        @Override
        public ValueType getType() {
            return ValueType.RESOURCE_REF;
        }

        @Override
        public String toString() {
            return path;
        }
    }

    /**
     * GUID value implementation
     */
    public static class GUIDValue extends NDFValue {
        private final String guid;

        public GUIDValue(String guid) {
            this.guid = guid;
        }

        public String getGUID() {
            return guid;
        }

        @Override
        public ValueType getType() {
            return ValueType.GUID;
        }

        @Override
        public String toString() {
            return guid;
        }
    }

    /**
     * Enum value implementation
     */
    public static class EnumValue extends NDFValue {
        private final String enumType;
        private final String enumValue;

        public EnumValue(String enumType, String enumValue) {
            this.enumType = enumType;
            this.enumValue = enumValue;
        }

        public String getEnumType() {
            return enumType;
        }

        public String getEnumValue() {
            return enumValue;
        }

        @Override
        public ValueType getType() {
            return ValueType.ENUM;
        }

        @Override
        public String toString() {
            return enumType + "/" + enumValue;
        }
    }

    /**
     * Raw expression value implementation (for complex expressions that should be preserved as-is)
     */
    public static class RawExpressionValue extends NDFValue {
        private final String expression;

        public RawExpressionValue(String expression) {
            this.expression = expression;
        }

        public String getExpression() {
            return expression;
        }

        @Override
        public ValueType getType() {
            return ValueType.RAW_EXPRESSION;
        }

        @Override
        public String toString() {
            return expression;
        }
    }
}
