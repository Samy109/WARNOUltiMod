package com.warnomodmaker.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class NDFValue {

    
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
        EFFETS_SUR_UNITE("EffetsSurUnite.ndf", "TEffectsPackDescriptor", "Effects on Unit"),
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

        // COMPREHENSIVE DEPENDENCY SUPPORT - Critical missing file types
        GENERATED_INFANTRY_DEPICTION("GeneratedInfantryDepiction.ndf", "TInfantryDepictionDescriptor", "Infantry Depiction"),
        VEHICLE_DEPICTION("VehicleDepiction.ndf", "TVehicleDepictionDescriptor", "Vehicle Depiction"),
        AIRCRAFT_DEPICTION("AircraftDepiction.ndf", "TAircraftDepictionDescriptor", "Aircraft Depiction"),
        DEPICTION_DESCRIPTOR("DepictionDescriptor.ndf", "TDepictionDescriptor", "Depiction"),

        // Note: EffectDescriptor.ndf doesn't exist - effects are in EffetsSurUnite.ndf
        // Note: DamageDescriptor.ndf doesn't exist - damage info is in DamageResistance.ndf and DamageResistanceFamilyList.ndf
        // Note: ProjectileDescriptor.ndf doesn't exist - projectile info is in Ammunition.ndf files
        // Note: ExplosionDescriptor.ndf doesn't exist - explosion effects are in EffetsSurUnite.ndf
        // Note: BallisticDescriptor.ndf doesn't exist - ballistic info is in TemplatesProjectiles.ndf

        ARTILLERY_PROJECTILE_DESCRIPTOR("ArtilleryProjectileDescriptor.ndf", "TArtilleryProjectileDescriptor", "Artillery Projectile"),

        // Correct sound file name is SoundDescriptors.ndf (plural), not SoundDescriptor.ndf
        SOUND_DESCRIPTORS("SoundDescriptors.ndf", "TSoundDescriptor", "Sound Descriptors"),
        WEAPON_SOUND_HAPPENINGS("WeaponSoundHappenings.ndf", "TSoundHappening", "Weapon Sound Happenings"),
        VEHICLE_SOUND_DESCRIPTOR("VehicleSoundDescriptor.ndf", "TVehicleSoundDescriptor", "Vehicle Sound"),

        INFANTRY_ANIMATION_DESCRIPTOR("InfantryAnimationDescriptor.ndf", "TInfantryAnimationDescriptor", "Infantry Animation"),
        VEHICLE_ANIMATION_DESCRIPTOR("VehicleAnimationDescriptor.ndf", "TVehicleAnimationDescriptor", "Vehicle Animation"),
        AIRCRAFT_ANIMATION_DESCRIPTOR("AircraftAnimationDescriptor.ndf", "TAircraftAnimationDescriptor", "Aircraft Animation"),

        // Note: ProductionDescriptor.ndf doesn't exist - production info is in Production.ndf constants file
        SUPPLY_DESCRIPTOR("SupplyDescriptor.ndf", "TSupplyDescriptor", "Supply"),
        RECON_DESCRIPTOR("ReconDescriptor.ndf", "TReconDescriptor", "Reconnaissance"),
        COMMAND_DESCRIPTOR("CommandDescriptor.ndf", "TCommandDescriptor", "Command"),

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

        
        public static NDFFileType fromFilename(String filename) {
            if (filename == null) return UNKNOWN;

            String name = filename.toLowerCase();
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

            // Additional fallbacks for comprehensive dependency support
            if (name.endsWith("generatedinfantrydepiction.ndf")) return GENERATED_INFANTRY_DEPICTION;
            if (name.endsWith("vehicledepiction.ndf")) return VEHICLE_DEPICTION;
            if (name.endsWith("aircraftdepiction.ndf")) return AIRCRAFT_DEPICTION;
            if (name.endsWith("effetssurUnite.ndf")) return EFFETS_SUR_UNITE;
            if (name.endsWith("sounddescriptors.ndf")) return SOUND_DESCRIPTORS;
            if (name.endsWith("weaponsoundhappenings.ndf")) return WEAPON_SOUND_HAPPENINGS;

            return UNKNOWN;
        }
    }

    
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

    protected String originalPrefix = "";
    protected String originalSuffix = "";
    protected boolean hasOriginalFormatting = false;

    /**
     * Set the original formatting that surrounded this value in the source file
     * @param prefix The whitespace/indentation that appeared before this value
     * @param suffix The whitespace/newlines that appeared after this value
     */
    public void setOriginalFormatting(String prefix, String suffix) {
        this.originalPrefix = prefix != null ? prefix : "";
        this.originalSuffix = suffix != null ? suffix : "";
        this.hasOriginalFormatting = true;
    }

    /**
     * Get the original prefix (whitespace/indentation before this value)
     */
    public String getOriginalPrefix() {
        return originalPrefix;
    }

    /**
     * Get the original suffix (whitespace/newlines after this value)
     */
    public String getOriginalSuffix() {
        return originalSuffix;
    }

    /**
     * Check if this value has original formatting information
     */
    public boolean hasOriginalFormatting() {
        return hasOriginalFormatting;
    }

    /**
     * Clear formatting information (used when creating new values)
     */
    public void clearOriginalFormatting() {
        this.originalPrefix = "";
        this.originalSuffix = "";
        this.hasOriginalFormatting = false;
    }


    public abstract ValueType getType();

    
    public static NDFValue createString(String value) {
        return new StringValue(value);
    }

    
    public static NDFValue createString(String value, boolean useDoubleQuotes) {
        return new StringValue(value, useDoubleQuotes);
    }

    
    public static NDFValue createNumber(double value) {
        return new NumberValue(value);
    }

    
    public static NDFValue createNumber(double value, boolean wasOriginallyInteger) {
        return new NumberValue(value, wasOriginallyInteger);
    }

    
    public static NDFValue createNumber(double value, String originalFormat) {
        return new NumberValue(value, originalFormat);
    }

    
    public static NDFValue createBoolean(boolean value) {
        return new BooleanValue(value);
    }

    
    public static ArrayValue createArray() {
        return new ArrayValue();
    }

    
    public static TupleValue createTuple() {
        return new TupleValue();
    }

    
    public static MapValue createMap() {
        return new MapValue();
    }

    
    public static ObjectValue createObject(String typeName) {
        return new ObjectValue(typeName);
    }

    
    public static NDFValue createTemplateRef(String path) {
        return new TemplateRefValue(path);
    }

    public static NDFValue createResourceRef(String path) {
        return new ResourceRefValue(path);
    }

    public static NDFValue createGuid(String value) {
        return new GUIDValue(value);
    }

    public static NDFValue createEnum(String value) {
        return new EnumValue(value);
    }

    
    public static NDFValue createGUID(String guid) {
        return new GUIDValue(guid);
    }

    
    public static NDFValue createEnum(String enumType, String enumValue) {
        return new EnumValue(enumType, enumValue);
    }

    
    public static NDFValue createRawExpression(String expression) {
        return new RawExpressionValue(expression);
    }

    
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

        
        public double getRoundedValue() {
            if (wasOriginallyInteger) {
                return Math.round(value);
            }
            return value;
        }

        
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
                    return formatDecimalNumber(value);
                }
            }

            // Legacy behavior with smart rounding
            if (wasOriginallyInteger || (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e10)) {
                return Integer.toString((int) Math.round(value));
            }
            return formatDecimalNumber(value);
        }

        
        private String formatDecimalNumber(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return Double.toString(value);
            }

            // For reasonable ranges, avoid scientific notation
            if (Math.abs(value) >= 1e-15 && Math.abs(value) < 1e15) {
                java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value);
                return bd.stripTrailingZeros().toPlainString();
            }

            // For very large or very small numbers, use scientific notation
            return Double.toString(value);
        }
    }

    
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

    
    public static class ArrayValue extends NDFValue {
        private final List<NDFValue> elements;
        private final List<Boolean> hasCommaAfter; // Tracks which elements have commas after them

        private boolean originallyMultiLine = false;
        private String originalOpeningBracket = "[";
        private String originalClosingBracket = "]";
        private final List<String> originalElementPrefix = new ArrayList<>();
        private final List<String> originalElementSuffix = new ArrayList<>();

        public ArrayValue() {
            this.elements = new ArrayList<>();
            this.hasCommaAfter = new ArrayList<>();
        }

        public void add(NDFValue element) {
            elements.add(element);
            hasCommaAfter.add(false); // Default to no comma
            originalElementPrefix.add("");
            originalElementSuffix.add("");
        }

        public void add(NDFValue element, boolean hasComma) {
            elements.add(element);
            hasCommaAfter.add(hasComma);
            originalElementPrefix.add("");
            originalElementSuffix.add("");
        }

        public void addElement(NDFValue element) {
            add(element);
        }

        public void addElement(NDFValue element, boolean hasComma) {
            add(element, hasComma);
        }

        public void setElementComma(int index, boolean hasComma) {
            setCommaAfter(index, hasComma);
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

        // ORIGINAL FORMATTING PRESERVATION METHODS - ZERO INTELLIGENCE, 1-1 REPRODUCTION
        public boolean isOriginallyMultiLine() {
            return originallyMultiLine;
        }

        public void setOriginallyMultiLine(boolean multiLine) {
            this.originallyMultiLine = multiLine;
        }

        public String getOriginalOpeningBracket() {
            return originalOpeningBracket;
        }

        public void setOriginalOpeningBracket(String openingBracket) {
            this.originalOpeningBracket = openingBracket;
        }

        public String getOriginalClosingBracket() {
            return originalClosingBracket;
        }

        public void setOriginalClosingBracket(String closingBracket) {
            this.originalClosingBracket = closingBracket;
        }

        public String getOriginalElementPrefix(int index) {
            return index < originalElementPrefix.size() ? originalElementPrefix.get(index) : "";
        }

        public void setOriginalElementPrefix(int index, String prefix) {
            while (originalElementPrefix.size() <= index) {
                originalElementPrefix.add("");
            }
            originalElementPrefix.set(index, prefix);
        }

        public String getOriginalElementSuffix(int index) {
            return index < originalElementSuffix.size() ? originalElementSuffix.get(index) : "";
        }

        public void setOriginalElementSuffix(int index, String suffix) {
            while (originalElementSuffix.size() <= index) {
                originalElementSuffix.add("");
            }
            originalElementSuffix.set(index, suffix);
        }

        
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

        public void put(NDFValue key, NDFValue value) {
            add(key, value);
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

    
    public static class ObjectValue extends NDFValue {
        private final String typeName;
        private final Map<String, NDFValue> properties;
        private final Map<String, Boolean> hasCommaAfter;
        private String instanceName;
        private String moduleIdentifier;
        private boolean isExported;

        private String originalOpeningParen = "(";
        private String originalClosingParen = ")";
        private final Map<String, String> originalPropertyPrefix = new LinkedHashMap<>();
        private final Map<String, String> originalPropertyEquals = new LinkedHashMap<>();
        private final Map<String, String> originalPropertySuffix = new LinkedHashMap<>();

        private int originalTokenStartIndex = -1;
        private int originalTokenEndIndex = -1;

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

        public void setPropertyComma(String propertyName, boolean hasComma) {
            setCommaAfter(propertyName, hasComma);
        }

        public boolean hasProperty(String propertyName) {
            return properties.containsKey(propertyName);
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

        // UNIVERSAL FORMATTING PRESERVATION METHODS - ZERO INTELLIGENCE, 1-1 REPRODUCTION
        public String getOriginalOpeningParen() {
            return originalOpeningParen;
        }

        public void setOriginalOpeningParen(String openingParen) {
            this.originalOpeningParen = openingParen;
        }

        public String getOriginalClosingParen() {
            return originalClosingParen;
        }

        public void setOriginalClosingParen(String closingParen) {
            this.originalClosingParen = closingParen;
        }

        public String getOriginalPropertyPrefix(String propertyName) {
            return originalPropertyPrefix.getOrDefault(propertyName, "");
        }

        public void setOriginalPropertyPrefix(String propertyName, String prefix) {
            originalPropertyPrefix.put(propertyName, prefix);
        }

        public String getOriginalPropertyEquals(String propertyName) {
            return originalPropertyEquals.getOrDefault(propertyName, " = ");
        }

        public void setOriginalPropertyEquals(String propertyName, String equals) {
            originalPropertyEquals.put(propertyName, equals);
        }

        public String getOriginalPropertySuffix(String propertyName) {
            return originalPropertySuffix.getOrDefault(propertyName, "");
        }

        public void setOriginalPropertySuffix(String propertyName, String suffix) {
            originalPropertySuffix.put(propertyName, suffix);
        }

        public int getOriginalTokenStartIndex() {
            return originalTokenStartIndex;
        }

        public void setOriginalTokenStartIndex(int startIndex) {
            this.originalTokenStartIndex = startIndex;
        }

        public int getOriginalTokenEndIndex() {
            return originalTokenEndIndex;
        }

        public void setOriginalTokenEndIndex(int endIndex) {
            this.originalTokenEndIndex = endIndex;
        }

        public boolean hasOriginalTokenRange() {
            return originalTokenStartIndex >= 0 && originalTokenEndIndex >= 0;
        }

        @Override
        public ValueType getType() {
            return ValueType.OBJECT;
        }

        @Override
        public String toString() {
            String name = getInstanceName();
            if (name != null && !name.isEmpty()) {
                return name + " (" + typeName + ")";
            }
            return typeName;
        }

        public String toDetailedString() {
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



    
    public static class EnumValue extends NDFValue {
        private final String enumType;
        private final String enumValue;

        public EnumValue(String enumType, String enumValue) {
            this.enumType = enumType;
            this.enumValue = enumValue;
        }

        public EnumValue(String fullEnumValue) {
            if (fullEnumValue.contains("/")) {
                String[] parts = fullEnumValue.split("/", 2);
                this.enumType = parts[0];
                this.enumValue = parts[1];
            } else {
                this.enumType = "";
                this.enumValue = fullEnumValue;
            }
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

        public String getValue() {
            if (enumType.isEmpty()) {
                return enumValue;
            }
            return enumType + "/" + enumValue;
        }

        @Override
        public String toString() {
            return getValue();
        }
    }

    
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
