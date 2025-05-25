package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue.*;
import java.util.*;

/**
 * Dynamically scans the in-memory object model to discover all available properties.
 * This replaces hard-coded property lists with dynamic discovery from the actual parsed data.
 */
public class PropertyScanner {

    /**
     * Represents a discovered property with its path and metadata
     */
    public static class PropertyInfo {
        public final String name;
        public final String path;
        public final String description;
        public final NDFValue.ValueType type;
        public final String category;
        public final int occurrenceCount;

        public PropertyInfo(String name, String path, String description,
                          NDFValue.ValueType type, String category, int occurrenceCount) {
            this.name = name;
            this.path = path;
            this.description = description;
            this.type = type;
            this.category = category;
            this.occurrenceCount = occurrenceCount;
        }

        @Override
        public String toString() {
            return name + " (" + occurrenceCount + " units)";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PropertyInfo that = (PropertyInfo) obj;
            return Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }
    }

    private final List<ObjectValue> unitDescriptors;
    private Map<String, PropertyInfo> discoveredProperties;
    private Map<String, List<PropertyInfo>> categorizedProperties;
    private NDFValue.NDFFileType fileType; // Current file type being scanned

    public PropertyScanner(List<ObjectValue> unitDescriptors) {
        this(unitDescriptors, NDFValue.NDFFileType.UNKNOWN);
    }

    public PropertyScanner(List<ObjectValue> unitDescriptors, NDFValue.NDFFileType fileType) {
        this.unitDescriptors = unitDescriptors;
        this.fileType = fileType;
        this.discoveredProperties = new HashMap<>();
        this.categorizedProperties = new HashMap<>();
    }

    /**
     * Scans all unit descriptors and discovers available properties
     */
    public void scanProperties() {
        discoveredProperties.clear();
        categorizedProperties.clear();

        // Track property occurrences
        Map<String, Integer> propertyOccurrences = new HashMap<>();
        Map<String, NDFValue.ValueType> propertyTypes = new HashMap<>();

        // Scan each unit descriptor
        for (ObjectValue unit : unitDescriptors) {
            scanObject(unit, "", propertyOccurrences, propertyTypes);
        }

        // Normalize paths to group similar properties (e.g., array elements)
        Map<String, Integer> normalizedOccurrences = new HashMap<>();
        Map<String, NDFValue.ValueType> normalizedTypes = new HashMap<>();
        Map<String, String> normalizedToOriginal = new HashMap<>();

        for (Map.Entry<String, Integer> entry : propertyOccurrences.entrySet()) {
            String originalPath = entry.getKey();
            int count = entry.getValue();
            NDFValue.ValueType type = propertyTypes.get(originalPath);

            // Normalize the path by removing specific array indices
            String normalizedPath = normalizePropertyPath(originalPath);

            // Accumulate counts for normalized paths
            normalizedOccurrences.put(normalizedPath,
                normalizedOccurrences.getOrDefault(normalizedPath, 0) + count);
            normalizedTypes.put(normalizedPath, type);

            // Keep track of one original path for each normalized path
            if (!normalizedToOriginal.containsKey(normalizedPath)) {
                normalizedToOriginal.put(normalizedPath, originalPath);
            }
        }

        // Create PropertyInfo objects for normalized properties with ACCURATE unit counts
        for (Map.Entry<String, Integer> entry : normalizedOccurrences.entrySet()) {
            String normalizedPath = entry.getKey();
            NDFValue.ValueType type = normalizedTypes.get(normalizedPath);

            // For wildcard paths, use the normalized path as the original path for mass updates
            String originalPath = normalizedPath.contains("[*]") ? normalizedPath : normalizedToOriginal.get(normalizedPath);

            // Calculate ACCURATE unit count using direct property checking
            int actualUnitCount = countUnitsWithProperty(originalPath);

            // Include all properties that are editable, regardless of unit count
            if (actualUnitCount > 0 && isEditableType(type)) {
                String name = getPropertyDisplayName(normalizedPath);
                String category = categorizeProperty(normalizedPath, name);
                String description = generateDescription(normalizedPath, name, actualUnitCount);

                PropertyInfo info = new PropertyInfo(name, originalPath, description, type, category, actualUnitCount);
                discoveredProperties.put(normalizedPath, info);

                // Add to category
                categorizedProperties.computeIfAbsent(category, k -> new ArrayList<>()).add(info);
            }
        }

        // Sort properties within each category
        for (List<PropertyInfo> properties : categorizedProperties.values()) {
            properties.sort((a, b) -> {
                // Sort by occurrence count (descending), then by name
                int countCompare = Integer.compare(b.occurrenceCount, a.occurrenceCount);
                return countCompare != 0 ? countCompare : a.name.compareTo(b.name);
            });
        }
    }

    /**
     * Recursively scans an object and its nested properties
     */
    private void scanObject(ObjectValue object, String basePath,
                           Map<String, Integer> occurrences, Map<String, NDFValue.ValueType> types) {
        if (object == null) return;

        for (Map.Entry<String, NDFValue> entry : object.getProperties().entrySet()) {
            String propertyName = entry.getKey();
            NDFValue value = entry.getValue();
            String fullPath = basePath.isEmpty() ? propertyName : basePath + "." + propertyName;

            if (value != null) {
                // Record this property
                occurrences.put(fullPath, occurrences.getOrDefault(fullPath, 0) + 1);
                types.put(fullPath, value.getType());

                // Recursively scan nested objects
                if (value instanceof ObjectValue) {
                    scanObject((ObjectValue) value, fullPath, occurrences, types);
                } else if (value instanceof ArrayValue) {
                    // Special handling for ModulesDescriptors array - use type-based paths
                    if ("ModulesDescriptors".equals(propertyName)) {
                        scanModulesDescriptors((ArrayValue) value, occurrences, types);
                    } else {
                        // Scan other arrays with indices
                        ArrayValue arrayValue = (ArrayValue) value;
                        for (int i = 0; i < arrayValue.getElements().size(); i++) {
                            NDFValue element = arrayValue.getElements().get(i);
                            if (element instanceof ObjectValue) {
                                scanObject((ObjectValue) element, fullPath + "[" + i + "]", occurrences, types);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Scans ModulesDescriptors array using wildcard paths for mass update compatibility
     */
    private void scanModulesDescriptors(ArrayValue modulesArray,
                                      Map<String, Integer> occurrences, Map<String, NDFValue.ValueType> types) {
        for (NDFValue element : modulesArray.getElements()) {
            if (element instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) element;
                // Use wildcard path format for mass update compatibility
                scanObject(module, "ModulesDescriptors[*]", occurrences, types);
            }
        }
    }

    /**
     * Checks if a value type can be edited in mass updates
     */
    private boolean isEditableType(NDFValue.ValueType type) {
        return type == NDFValue.ValueType.NUMBER ||
               type == NDFValue.ValueType.STRING ||
               type == NDFValue.ValueType.BOOLEAN ||
               type == NDFValue.ValueType.ENUM ||
               type == NDFValue.ValueType.TEMPLATE_REF ||
               type == NDFValue.ValueType.RESOURCE_REF ||
               type == NDFValue.ValueType.ARRAY; // Include arrays (like TagSet) as they can be modified
    }

    /**
     * Normalizes a property path by replacing specific array indices with generic placeholders
     */
    private String normalizePropertyPath(String path) {
        // Replace specific array indices like [0], [1], [19] with [*]
        // This groups similar properties together regardless of their array position
        return path.replaceAll("\\[\\d+\\]", "[*]");
    }

    /**
     * Generates a user-friendly display name for a property path
     */
    private String getPropertyDisplayName(String path) {
        String[] parts = path.split("\\.");
        String lastPart = parts[parts.length - 1];

        // Remove array indices for cleaner display
        lastPart = lastPart.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");

        // Context-aware naming for resistance properties
        if (isResistanceProperty(path)) {
            if (lastPart.equals("Family")) {
                return "Resistance Family";
            } else if (lastPart.equals("Index")) {
                return "Armor Thickness";
            }
        }

        // Context-aware naming for damage properties
        if (isDamageProperty(path)) {
            if (lastPart.equals("Family")) {
                return "Damage Family";
            } else if (lastPart.equals("Index")) {
                return "Damage Index";
            }
        }

        // Convert camelCase to readable format
        return lastPart.replaceAll("([a-z])([A-Z])", "$1 $2")
                      .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
    }

    /**
     * Checks if a property path is related to resistance/armor properties
     */
    private boolean isResistanceProperty(String path) {
        String lowerPath = path.toLowerCase();
        return lowerPath.contains("resistance") ||
               lowerPath.contains("blindageproperties") ||
               lowerPath.contains("armor") ||
               (lowerPath.contains("resistancefront") || lowerPath.contains("resistancesides") ||
                lowerPath.contains("resistancerear") || lowerPath.contains("resistancetop"));
    }

    /**
     * Checks if a property path is related to damage properties
     */
    private boolean isDamageProperty(String path) {
        String lowerPath = path.toLowerCase();
        return lowerPath.contains("damage") &&
               (lowerPath.contains("family") || lowerPath.contains("index")) &&
               !lowerPath.contains("resistance"); // Exclude resistance damage properties
    }

    /**
     * Categorizes a property based on its path and name with file-type-aware categorization
     */
    private String categorizeProperty(String path, String name) {
        String lowerPath = path.toLowerCase();
        String lowerName = name.toLowerCase();

        // File-type-specific categorization
        if (fileType == NDFValue.NDFFileType.WEAPON_DESCRIPTOR) {
            return categorizeWeaponProperty(lowerPath, lowerName);
        } else if (fileType == NDFValue.NDFFileType.AMMUNITION || fileType == NDFValue.NDFFileType.AMMUNITION_MISSILES) {
            return categorizeAmmunitionProperty(lowerPath, lowerName);
        } else if (fileType == NDFValue.NDFFileType.MISSILE_DESCRIPTORS) {
            return categorizeMissileProperty(lowerPath, lowerName);
        } else if (fileType == NDFValue.NDFFileType.MISSILE_CARRIAGE) {
            return categorizeMissileCarriageProperty(lowerPath, lowerName);
        }

        // Default to unit descriptor categorization for unknown types
        return categorizeUnitProperty(lowerPath, lowerName);
    }

    /**
     * Categorizes weapon descriptor properties
     */
    private String categorizeWeaponProperty(String lowerPath, String lowerName) {
        // Weapon-specific categories
        if (lowerPath.contains("salves") || lowerName.contains("salvo")) {
            return "Salvo Configuration";
        }

        if (lowerPath.contains("turret") || lowerName.contains("turret") ||
            lowerPath.contains("rotation") || lowerName.contains("rotation") ||
            lowerPath.contains("angle") || lowerName.contains("angle")) {
            return "Turret & Rotation";
        }

        if (lowerPath.contains("mountedweapon") || lowerName.contains("weapon") ||
            lowerPath.contains("ammunition") || lowerName.contains("ammunition")) {
            return "Mounted Weapons";
        }

        if (lowerPath.contains("dispersion") || lowerName.contains("dispersion") ||
            lowerPath.contains("color") || lowerName.contains("color") ||
            lowerPath.contains("thickness") || lowerName.contains("thickness")) {
            return "Visual Effects";
        }

        return "Weapon System";
    }

    /**
     * Categorizes ammunition properties
     */
    private String categorizeAmmunitionProperty(String lowerPath, String lowerName) {
        // Ammunition-specific categories
        if (lowerPath.contains("damage") || lowerName.contains("damage") ||
            lowerPath.contains("suppression") || lowerName.contains("suppression") ||
            lowerPath.contains("stun") || lowerName.contains("stun") ||
            lowerPath.contains("radius") || lowerName.contains("radius")) {
            return "Damage & Effects";
        }

        if (lowerPath.contains("range") || lowerName.contains("range") ||
            lowerPath.contains("portee") || lowerName.contains("portee") ||
            lowerPath.contains("speed") || lowerName.contains("speed") ||
            lowerPath.contains("acceleration") || lowerName.contains("acceleration")) {
            return "Range & Ballistics";
        }

        if (lowerPath.contains("dispersion") || lowerName.contains("dispersion") ||
            lowerPath.contains("angle") || lowerName.contains("angle") ||
            lowerPath.contains("accuracy") || lowerName.contains("accuracy")) {
            return "Accuracy & Dispersion";
        }

        if (lowerPath.contains("cost") || lowerName.contains("cost") ||
            lowerPath.contains("supply") || lowerName.contains("supply") ||
            lowerPath.contains("time") || lowerName.contains("time")) {
            return "Cost & Timing";
        }

        if (lowerPath.contains("missile") || lowerName.contains("missile") ||
            lowerPath.contains("projectile") || lowerName.contains("projectile")) {
            return "Missile Properties";
        }

        return "Ammunition System";
    }

    /**
     * Categorizes missile descriptor properties
     */
    private String categorizeMissileProperty(String lowerPath, String lowerName) {
        // Similar to unit properties but missile-focused
        return categorizeUnitProperty(lowerPath, lowerName);
    }

    /**
     * Categorizes missile carriage properties
     */
    private String categorizeMissileCarriageProperty(String lowerPath, String lowerName) {
        if (lowerPath.contains("weapon") || lowerName.contains("weapon")) {
            return "Weapon Configuration";
        }
        return "Missile Carriage";
    }

    /**
     * Original unit descriptor categorization
     */
    private String categorizeUnitProperty(String lowerPath, String lowerName) {

        // 0. TAGS & CLASSIFICATION - Unit tags and AI classification
        if (lowerPath.contains("tagset") || lowerPath.contains("searchedtagsinengagementtarget") ||
            lowerPath.contains("transportabletagset") || lowerName.contains("tag") ||
            lowerName.contains("classification") || lowerName.contains("category")) {
            return "Tags & Classification";
        }

        // 1. COMBAT OFFENSE - Weapons and damage dealing
        if (lowerPath.contains("weapon") || lowerPath.contains("ammunition") ||
            lowerPath.contains("ammo") || lowerPath.contains("dangerousness") ||
            lowerPath.contains("damage") || lowerPath.contains("suppression") ||
            lowerPath.contains("stun") || lowerName.contains("weapon") ||
            lowerName.contains("ammunition") || lowerName.contains("dangerousness") ||
            lowerName.contains("damage") || lowerName.contains("combat")) {
            return "Combat Offense";
        }

        // 2. COMBAT DEFENSE - Health, armor, and protection
        if (lowerPath.contains("maxphysicaldamages") || lowerPath.contains("maxsuppressiondamages") ||
            lowerPath.contains("maxstundamages") || lowerPath.contains("suppressdamagesregenratio") ||
            lowerPath.contains("stundamagesregen") || lowerPath.contains("hitrollecm") ||
            lowerPath.contains("blindageproperties") || lowerPath.contains("resistance") ||
            lowerPath.contains("explosivereactivearmor") || lowerPath.contains("armor") ||
            lowerPath.contains("penetration") || lowerPath.contains("protection") ||
            lowerName.contains("physicaldamages") || lowerName.contains("suppressiondamages") ||
            lowerName.contains("stundamages") || lowerName.contains("damagesregen") ||
            lowerName.contains("hitroll") || lowerName.contains("ecm") ||
            lowerName.contains("resistance") || lowerName.contains("armor") ||
            lowerName.contains("blindage") || lowerName.contains("protection") ||
            lowerName.contains("reactive") || lowerName.contains("penetration")) {
            return "Combat Defense";
        }

        // 3. MOVEMENT - All movement and mobility (ground, air, advanced)
        if (lowerPath.contains("maxspeedinkmph") || lowerPath.contains("speedbonusfactoronroad") ||
            lowerPath.contains("maxaccelerationgru") || lowerPath.contains("maxdecelerationgru") ||
            lowerPath.contains("tempsdemi") || lowerPath.contains("starttime") ||
            lowerPath.contains("stoptime") || lowerPath.contains("rotationtime") ||
            lowerPath.contains("unitmovingtype") || lowerPath.contains("pathfindtype") ||
            lowerPath.contains("movement") || lowerPath.contains("pathfind") ||
            lowerPath.contains("mobility") || lowerPath.contains("upwardspeedinkmph") ||
            lowerPath.contains("torquemanoeuvrability") || lowerPath.contains("cyclicmanoeuvrability") ||
            lowerPath.contains("maxinclination") || lowerPath.contains("gfactorlimit") ||
            lowerPath.contains("rotorarea") || lowerPath.contains("mass") ||
            lowerPath.contains("altitude") || lowerPath.contains("agilityradiusgru") ||
            lowerPath.contains("pitchangle") || lowerPath.contains("rollangle") ||
            lowerPath.contains("rollspeed") || lowerPath.contains("evacangle") ||
            lowerPath.contains("evacuationtime") || lowerPath.contains("travelduration") ||
            lowerPath.contains("flight") || lowerName.contains("speed") ||
            lowerName.contains("acceleration") || lowerName.contains("deceleration") ||
            lowerName.contains("rotation") || lowerName.contains("turn") ||
            lowerName.contains("road") || lowerName.contains("movement") ||
            lowerName.contains("pathfind") || lowerName.contains("mobility") ||
            lowerName.contains("moving") || lowerName.contains("upward") ||
            lowerName.contains("torque") || lowerName.contains("cyclic") ||
            lowerName.contains("inclination") || lowerName.contains("rotor") ||
            lowerName.contains("altitude") || lowerName.contains("agility") ||
            lowerName.contains("pitch") || lowerName.contains("roll") ||
            lowerName.contains("evac") || lowerName.contains("flight")) {
            return "Movement";
        }

        // 4. VISION & DETECTION - Reconnaissance capabilities
        if (lowerPath.contains("visionrangesgru") || lowerPath.contains("opticalstrengths") ||
            lowerPath.contains("identifybaseprobability") || lowerPath.contains("timebetweeneachidentifyroll") ||
            lowerPath.contains("unitconcealmentbonus") || lowerPath.contains("scanner") ||
            lowerName.contains("vision") || lowerName.contains("optical") ||
            lowerName.contains("identify") || lowerName.contains("concealment") ||
            lowerName.contains("stealth") || lowerName.contains("detection") ||
            lowerName.contains("scanner") || lowerName.contains("reconnaissance")) {
            return "Vision & Detection";
        }

        // 5. AI BEHAVIOR - Combat AI and tactics
        if (lowerPath.contains("distancetofleegru") || lowerPath.contains("maxdistanceforoffensivereactiongru") ||
            lowerPath.contains("maxdistanceforengagementgru") || lowerPath.contains("canassist") ||
            lowerPath.contains("assistrequestbroadcastradiusgru") || lowerPath.contains("automaticbehavior") ||
            lowerPath.contains("gameplaybehavior") || lowerName.contains("flee") ||
            lowerName.contains("engagement") || lowerName.contains("assist") ||
            lowerName.contains("behavior") || lowerName.contains("automatic") ||
            lowerName.contains("reaction") || lowerName.contains("tactical")) {
            return "AI Behavior";
        }

        // 6. TRANSPORT & LOGISTICS - Transport capabilities and resource management
        if (lowerPath.contains("fuelcapacity") || lowerPath.contains("fuelmoveduration") ||
            lowerPath.contains("supplycapacity") || lowerPath.contains("supplypriority") ||
            lowerPath.contains("upkeeppercentage") || lowerPath.contains("nbseatsavailable") ||
            lowerPath.contains("loadradiusgru") || lowerPath.contains("transportabletagset") ||
            lowerPath.contains("wreckunload") || lowerPath.contains("transporter") ||
            lowerPath.contains("transportable") || lowerName.contains("fuel") ||
            lowerName.contains("supply") || lowerName.contains("upkeep") ||
            lowerName.contains("capacity") || lowerName.contains("duration") ||
            lowerName.contains("consumption") || lowerName.contains("logistics") ||
            lowerName.contains("seats") || lowerName.contains("transport") ||
            lowerName.contains("load") || lowerName.contains("passenger") ||
            lowerName.contains("cargo")) {
            return "Transport & Logistics";
        }

        // 7. PRODUCTION & COST - Economic properties
        if (lowerPath.contains("productiontime") || lowerPath.contains("productionressourcesneeded") ||
            lowerPath.contains("factory") || lowerPath.contains("commandpoints") ||
            lowerPath.contains("tickets") || lowerPath.contains("cost") ||
            lowerPath.contains("price") || lowerName.contains("production") ||
            lowerName.contains("cost") || lowerName.contains("price") ||
            lowerName.contains("factory") || lowerName.contains("resource") ||
            lowerName.contains("economy") || lowerName.contains("build")) {
            return "Production & Cost";
        }

        // 8. UNIT STATS - Strategic values, morale, experience, and progression
        if (lowerPath.contains("unitattackvalue") || lowerPath.contains("unitdefensevalue") ||
            lowerPath.contains("unitbonusxpperlevelvalue") || lowerPath.contains("multiselectionsorting") ||
            lowerPath.contains("strategic") || lowerPath.contains("morale") ||
            lowerPath.contains("experience") || lowerPath.contains("veteran") ||
            lowerPath.contains("level") || lowerPath.contains("moral") ||
            lowerPath.contains("rout") || lowerName.contains("attack") ||
            lowerName.contains("defense") || lowerName.contains("strategic") ||
            lowerName.contains("bonus") || lowerName.contains("sorting") ||
            lowerName.contains("value") || lowerName.contains("level") ||
            lowerName.contains("morale") || lowerName.contains("experience") ||
            lowerName.contains("veteran") || lowerName.contains("moral") ||
            lowerName.contains("rout")) {
            return "Unit Stats";
        }

        // 9. VISUAL & UI - User interface, graphics, and visual effects
        if (lowerPath.contains("texture") || lowerPath.contains("icon") ||
            lowerPath.contains("button") || lowerPath.contains("display") ||
            lowerPath.contains("minimap") || lowerPath.contains("label") ||
            lowerPath.contains("ui") || lowerPath.contains("menu") ||
            lowerPath.contains("depiction") || lowerPath.contains("model") ||
            lowerPath.contains("mesh") || lowerPath.contains("effect") ||
            lowerPath.contains("apparence") || lowerPath.contains("gfx") ||
            lowerName.contains("texture") || lowerName.contains("icon") ||
            lowerName.contains("display") || lowerName.contains("button") ||
            lowerName.contains("ui") || lowerName.contains("menu") ||
            lowerName.contains("depiction") || lowerName.contains("model") ||
            lowerName.contains("mesh") || lowerName.contains("effect") ||
            lowerName.contains("graphics") || lowerName.contains("visual")) {
            return "Visual & UI";
        }

        // 10. SYSTEM PROPERTIES - Unit identification, modules, and technical structure
        if (lowerPath.contains("unit") || lowerPath.contains("type") ||
            lowerPath.contains("formation") || lowerPath.contains("coalition") ||
            lowerPath.contains("country") || lowerPath.contains("acknowledge") ||
            lowerPath.contains("module") || lowerPath.contains("descriptor") ||
            lowerPath.contains("selector") || lowerPath.contains("template") ||
            lowerName.contains("unit") || lowerName.contains("type") ||
            lowerName.contains("formation") || lowerName.contains("coalition") ||
            lowerName.contains("country") || lowerName.contains("acknowledge") ||
            lowerName.contains("module") || lowerName.contains("descriptor") ||
            lowerName.contains("selector") || lowerName.contains("template")) {
            return "System Properties";
        }

        return "Other";
    }

    /**
     * Generates a description for a property
     */
    private String generateDescription(String path, String name, int count) {
        return String.format("%s (found in %d units)", name, count);
    }

    /**
     * Gets all discovered properties
     */
    public Collection<PropertyInfo> getAllProperties() {
        return discoveredProperties.values();
    }

    /**
     * Gets the discovered properties map
     */
    public Map<String, PropertyInfo> getDiscoveredProperties() {
        return discoveredProperties;
    }

    /**
     * Gets properties by category
     */
    public Map<String, List<PropertyInfo>> getCategorizedProperties() {
        return categorizedProperties;
    }

    /**
     * Gets a specific property by path
     */
    public PropertyInfo getProperty(String path) {
        return discoveredProperties.get(path);
    }

    /**
     * Searches for properties matching a query
     */
    public List<PropertyInfo> searchProperties(String query) {
        String lowerQuery = query.toLowerCase();
        List<PropertyInfo> results = new ArrayList<>();

        for (PropertyInfo property : discoveredProperties.values()) {
            if (property.name.toLowerCase().contains(lowerQuery) ||
                property.path.toLowerCase().contains(lowerQuery) ||
                property.description.toLowerCase().contains(lowerQuery)) {
                results.add(property);
            }
        }

        // Sort by relevance (exact matches first, then by occurrence count)
        results.sort((a, b) -> {
            boolean aExact = a.name.toLowerCase().equals(lowerQuery);
            boolean bExact = b.name.toLowerCase().equals(lowerQuery);

            if (aExact && !bExact) return -1;
            if (!aExact && bExact) return 1;

            return Integer.compare(b.occurrenceCount, a.occurrenceCount);
        });

        return results;
    }

    /**
     * Gets detailed scanning statistics for debugging
     */
    public String getScanningStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Property Scanning Statistics:\n");
        stats.append("Total units scanned: ").append(unitDescriptors.size()).append("\n");
        stats.append("Total discovered properties: ").append(discoveredProperties.size()).append("\n");

        // Count by category
        for (Map.Entry<String, List<PropertyInfo>> entry : categorizedProperties.entrySet()) {
            stats.append("Category '").append(entry.getKey()).append("': ")
                 .append(entry.getValue().size()).append(" properties\n");
        }

        // Count by type
        Map<NDFValue.ValueType, Integer> typeCount = new HashMap<>();
        for (PropertyInfo prop : discoveredProperties.values()) {
            typeCount.put(prop.type, typeCount.getOrDefault(prop.type, 0) + 1);
        }

        stats.append("\nBy type:\n");
        for (Map.Entry<NDFValue.ValueType, Integer> entry : typeCount.entrySet()) {
            stats.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        return stats.toString();
    }

    /**
     * Counts how many units actually have a specific property using direct checking
     * This uses the same logic as MassModifyDialog to ensure accuracy
     */
    private int countUnitsWithProperty(String propertyPath) {
        int count = 0;
        for (ObjectValue unit : unitDescriptors) {
            if (hasPropertyDirect(unit, propertyPath)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Direct property checking with comprehensive filtering
     * Accounts for ALL WARNO data patterns for accurate counts
     */
    private boolean hasPropertyDirect(ObjectValue unit, String propertyPath) {
        // Wildcard paths: check if ANY array element has the property
        if (propertyPath.contains("[*]")) {
            return hasPropertyWithWildcards(unit, propertyPath);
        }

        // Regular paths: check if property exists
        if (!PropertyUpdater.hasProperty(unit, propertyPath)) {
            return false;
        }

        // Get the actual value to analyze it
        NDFValue value = PropertyUpdater.getPropertyValue(unit, propertyPath);
        if (value == null) {
            return false;
        }

        // Apply comprehensive filtering based on WARNO data patterns
        if (!isModifiableProperty(value, propertyPath)) {
            return false;
        }

        // Apply module type filtering for unit-type-specific properties
        return hasRequiredModuleType(unit, propertyPath);
    }

    /**
     * Determines if a property is actually modifiable based on WARNO data patterns
     */
    private boolean isModifiableProperty(NDFValue value, String propertyPath) {
        // 1. BOOLEAN PROPERTIES: Only count if True (False means unit doesn't have capability)
        if (value.getType() == NDFValue.ValueType.BOOLEAN) {
            BooleanValue boolValue = (BooleanValue) value;
            return boolValue.getValue(); // Only count if True
        }

        // 2. TEMPLATE REFERENCES: Allow for "Set to value" operations
        if (value.getType() == NDFValue.ValueType.TEMPLATE_REF ||
            value.getType() == NDFValue.ValueType.RESOURCE_REF) {
            return true; // Template references can be replaced with new values
        }

        // 3. STRING PROPERTIES: Exclude template references and system paths
        if (value.getType() == NDFValue.ValueType.STRING) {
            StringValue stringValue = (StringValue) value;
            String str = stringValue.getValue();

            // Exclude template references (~/..., $/...)
            if (str.startsWith("~/") || str.startsWith("$/")) {
                return false;
            }

            // Exclude system identifiers and GUIDs
            if (str.startsWith("GUID:") || str.contains("Texture_") ||
                str.contains("CommonTexture_") || str.contains("Descriptor_")) {
                return false;
            }

            // Include actual modifiable strings (unit names, etc.)
            return true;
        }

        // 4. NUMERIC PROPERTIES: Include all numbers (they're modifiable)
        if (value.getType() == NDFValue.ValueType.NUMBER) {
            return true;
        }

        // 5. ENUM PROPERTIES: Include enums (they're modifiable)
        if (value.getType() == NDFValue.ValueType.ENUM) {
            return true;
        }

        // 6. COMPLEX OBJECTS: Exclude structural containers, but allow modifiable arrays
        if (value.getType() == NDFValue.ValueType.OBJECT ||
            value.getType() == NDFValue.ValueType.MAP) {
            return false; // These are containers, not values
        }

        // 6a. ARRAY PROPERTIES: Allow specific modifiable arrays
        if (value.getType() == NDFValue.ValueType.ARRAY) {
            return isModifiableArray(value, propertyPath);
        }

        // 7. DEFAULT: Include other types
        return true;
    }

    /**
     * Determines if an array property is modifiable
     */
    private boolean isModifiableArray(NDFValue value, String propertyPath) {
        if (!(value instanceof ArrayValue)) {
            return false;
        }

        ArrayValue arrayValue = (ArrayValue) value;
        String lowerPath = propertyPath.toLowerCase();

        // TagSet arrays are modifiable (for adding/removing tags)
        if (lowerPath.contains("tagset")) {
            return true;
        }

        // SearchedTagsInEngagementTarget arrays are modifiable
        if (lowerPath.contains("searchedtagsinengagementtarget")) {
            return true;
        }

        // TransportableTagSet arrays are modifiable
        if (lowerPath.contains("transportabletagset")) {
            return true;
        }

        // SpecialtiesList arrays are modifiable
        if (lowerPath.contains("specialtieslist")) {
            return true;
        }

        // Arrays of simple values (strings, numbers) that are modifiable
        if (!arrayValue.getElements().isEmpty()) {
            NDFValue firstElement = arrayValue.getElements().get(0);

            // Arrays of strings are often modifiable (like tag lists)
            if (firstElement instanceof StringValue) {
                String str = ((StringValue) firstElement).getValue();
                // Exclude arrays of template references or system identifiers
                if (str.startsWith("~/") || str.startsWith("$/") ||
                    str.startsWith("GUID:") || str.contains("Texture_")) {
                    return false;
                }
                return true; // Arrays of simple strings are modifiable
            }

            // Arrays of numbers are often modifiable (like coordinate lists, value arrays)
            if (firstElement instanceof NumberValue) {
                return true;
            }
        }

        // Default: exclude complex arrays (arrays of objects, etc.)
        return false;
    }

    /**
     * Checks if a unit has the required module type for a specific property
     * This prevents counting tank-specific properties for infantry units, etc.
     * For non-unit files, always returns true since they don't have module restrictions
     */
    private boolean hasRequiredModuleType(ObjectValue unit, String propertyPath) {
        // For non-unit descriptor files, skip module type checking
        if (fileType != NDFValue.NDFFileType.UNITE_DESCRIPTOR &&
            fileType != NDFValue.NDFFileType.MISSILE_DESCRIPTORS) {
            return true; // No module restrictions for weapons, ammunition, etc.
        }

        // Get the modules array
        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return true; // If no modules array, allow all properties
        }

        ArrayValue modules = (ArrayValue) modulesValue;

        // Check for unit type flags that indicate what kind of unit this is
        boolean hasTankFlags = false;
        boolean hasInfantryFlags = false;
        boolean hasHelicopterFlags = false;
        boolean hasPlaneFlags = false;
        boolean hasCanonFlags = false;

        for (NDFValue moduleValue : modules.getElements()) {
            if (moduleValue instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) moduleValue;
                String typeName = module.getTypeName();

                if ("TankFlagsModuleDescriptor".equals(typeName)) {
                    hasTankFlags = true;
                } else if ("InfantryFlagsModuleDescriptor".equals(typeName)) {
                    hasInfantryFlags = true;
                } else if ("HelicoFlagsModuleDescriptor".equals(typeName)) {
                    hasHelicopterFlags = true;
                } else if ("AirplaneFlagsModuleDescriptor".equals(typeName)) {
                    hasPlaneFlags = true;
                } else if ("CanonFlagsModuleDescriptor".equals(typeName)) {
                    hasCanonFlags = true;
                }
            }
        }

        // Apply unit-type-specific filtering based on property paths
        return isPropertyValidForUnitType(propertyPath, hasTankFlags, hasInfantryFlags, hasHelicopterFlags, hasPlaneFlags, hasCanonFlags);
    }

    /**
     * Determines if a property is valid for a specific unit type with comprehensive filtering
     */
    private boolean isPropertyValidForUnitType(String propertyPath, boolean hasTankFlags,
                                             boolean hasInfantryFlags, boolean hasHelicopterFlags, boolean hasPlaneFlags, boolean hasCanonFlags) {
        String lowerPath = propertyPath.toLowerCase();

        // ARMOR & PROTECTION - All unit types have armor (different families: blindage, infanterie, helico, avion)
        if (lowerPath.contains("blindageproperties") || lowerPath.contains("explosivereactivearmor") ||
            lowerPath.contains("resistance") || lowerPath.contains("armor") ||
            lowerPath.contains("penetration") || lowerPath.contains("protection")) {
            return true; // All unit types have armor properties with different resistance families
        }

        // AIRCRAFT FLIGHT - Only helicopters and planes
        if (lowerPath.contains("upwardspeedinkmph") || lowerPath.contains("torquemanoeuvrability") ||
            lowerPath.contains("cyclicmanoeuvrability") || lowerPath.contains("maxinclination") ||
            lowerPath.contains("gfactorlimit") || lowerPath.contains("rotorarea") ||
            lowerPath.contains("mass") || lowerPath.contains("altitude") ||
            lowerPath.contains("agilityradiusgru") || lowerPath.contains("pitchangle") ||
            lowerPath.contains("rollangle") || lowerPath.contains("rollspeed") ||
            lowerPath.contains("evacangle") || lowerPath.contains("evacuationtime") ||
            lowerPath.contains("travelduration") || lowerPath.contains("flight") ||
            lowerPath.contains("aircraft") || lowerPath.contains("helicopter") ||
            lowerPath.contains("helico")) {
            return hasHelicopterFlags || hasPlaneFlags; // Only aircraft
        }

        // INFANTRY-SPECIFIC - Only infantry units
        if (lowerPath.contains("infantry") || lowerPath.contains("soldier") ||
            lowerPath.contains("infanterie") || lowerPath.contains("crew")) {
            return hasInfantryFlags; // Only infantry units
        }

        // FUEL & LOGISTICS - Mainly for vehicles and aircraft (infantry usually walk)
        if (lowerPath.contains("fuel")) {
            return hasTankFlags || hasHelicopterFlags || hasPlaneFlags || hasCanonFlags; // Vehicles, aircraft, and artillery need fuel
        }

        // TRANSPORT & CAPACITY - Only transport vehicles and helicopters
        if (lowerPath.contains("nbseatsavailable") || lowerPath.contains("loadradiusgru") ||
            lowerPath.contains("transportabletagset") || lowerPath.contains("transporter")) {
            return hasTankFlags || hasHelicopterFlags; // Vehicles and helicopters can transport
        }

        // ADVANCED MOVEMENT - Different for different unit types
        if (lowerPath.contains("unitmovingtype") || lowerPath.contains("pathfindtype")) {
            // All units have movement, but different types
            return true; // All unit types have movement
        }

        // BASIC MOVEMENT - All units can move
        if (lowerPath.contains("maxspeedinkmph") || lowerPath.contains("speedbonusfactoronroad") ||
            lowerPath.contains("maxaccelerationgru") || lowerPath.contains("maxdecelerationgru")) {
            return true; // All unit types have basic movement
        }

        // VISION & DETECTION - All units have vision
        if (lowerPath.contains("visionrangesgru") || lowerPath.contains("opticalstrengths") ||
            lowerPath.contains("identifybaseprobability") || lowerPath.contains("unitconcealmentbonus")) {
            return true; // All unit types have vision/detection
        }

        // DAMAGE & HEALTH - All units have health
        if (lowerPath.contains("maxphysicaldamages") || lowerPath.contains("maxsuppressiondamages") ||
            lowerPath.contains("maxstundamages") || lowerPath.contains("suppressdamagesregenratio")) {
            return true; // All unit types have health/damage
        }

        // WEAPONS & COMBAT - Most units have weapons (except pure logistics)
        if (lowerPath.contains("weapon") || lowerPath.contains("dangerousness") ||
            lowerPath.contains("ammunition") || lowerPath.contains("ammo")) {
            return true; // Most units have weapons
        }

        // AI BEHAVIOR - All units have AI behavior
        if (lowerPath.contains("distancetofleegru") || lowerPath.contains("maxdistanceforoffensivereactiongru") ||
            lowerPath.contains("canassist") || lowerPath.contains("automaticbehavior")) {
            return true; // All unit types have AI behavior
        }

        // PRODUCTION & COST - All units have production costs
        if (lowerPath.contains("productiontime") || lowerPath.contains("productionressourcesneeded") ||
            lowerPath.contains("factory") || lowerPath.contains("cost")) {
            return true; // All unit types have production properties
        }

        // STRATEGIC VALUES - All units have strategic values
        if (lowerPath.contains("unitattackvalue") || lowerPath.contains("unitdefensevalue") ||
            lowerPath.contains("strategic")) {
            return true; // All unit types have strategic values
        }

        // Default: allow for all unit types (common properties)
        return true;
    }

    /**
     * Checks if a unit has a property with wildcard array indices - same logic as MassModifyDialog
     */
    private boolean hasPropertyWithWildcards(ObjectValue unit, String propertyPath) {
        // Split on [*] to get the parts
        String[] mainParts = propertyPath.split("\\[\\*\\]");
        if (mainParts.length < 2) {
            return false; // Invalid format
        }

        String arrayPropertyName = mainParts[0]; // "ModulesDescriptors"
        String remainingPath = mainParts[1]; // ".BlindageProperties.ExplosiveReactiveArmor"

        // Remove leading dot if present
        if (remainingPath.startsWith(".")) {
            remainingPath = remainingPath.substring(1);
        }

        // Get the array property
        NDFValue arrayValue = unit.getProperty(arrayPropertyName);
        if (!(arrayValue instanceof ArrayValue)) {
            return false; // Not an array
        }

        ArrayValue array = (ArrayValue) arrayValue;

        // Check if ANY array element has the target property - no assumptions
        for (int i = 0; i < array.getElements().size(); i++) {
            NDFValue element = array.getElements().get(i);
            if (element instanceof ObjectValue) {
                ObjectValue elementObj = (ObjectValue) element;

                // Check if this element has the property
                if (PropertyUpdater.hasProperty(elementObj, remainingPath)) {
                    // Get the value and apply comprehensive filtering
                    NDFValue value = PropertyUpdater.getPropertyValue(elementObj, remainingPath);
                    if (value != null && isModifiableProperty(value, remainingPath) &&
                        hasRequiredModuleType(unit, propertyPath)) {
                        return true; // Found at least one modifiable property for this unit type
                    }
                }
            }
        }

        return false; // Not found in any array element
    }
}
