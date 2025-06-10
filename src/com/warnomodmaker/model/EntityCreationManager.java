package com.warnomodmaker.model;

import java.util.*;

/**
 * Comprehensive entity creation system based on actual NDF cross-file dependency analysis.
 * 
 * DISCOVERED DEPENDENCY CHAIN:
 * UniteDescriptor.ndf → WeaponDescriptor.ndf → Ammunition.ndf
 * UniteDescriptor.ndf → WeaponDescriptor.ndf → AmmunitionMissiles.ndf (for missiles)
 * BuildingDescriptors.ndf (standalone for buildings)
 * MissileDescriptors.ndf (for missile-specific data)
 */
public class EntityCreationManager {
    
    private final Map<String, EntityBlueprint> entityBlueprints;
    private final AdditiveOperationManager additiveManager;
    private final GUIDGenerator guidGenerator;

    // Template learning system for perfect placeholder values
    private final Map<String, NDFValue.ObjectValue> templateExamples = new HashMap<>(); // entityType -> example unit
    private final Map<String, Map<String, Object>> learnedPropertyDefaults = new HashMap<>(); // entityType -> property defaults
    private final Map<String, Set<String>> optionalDependencies = new HashMap<>(); // entityType -> optional file types

    // On-demand template system - cached templates for file types
    private final Map<String, NDFTemplateManager> fileTypeTemplates = new HashMap<>(); // fileType -> template manager
    private final Map<String, String> fileTypeObjectTypes = new HashMap<>(); // fileType -> primary object type

    public EntityCreationManager() {
        this.entityBlueprints = new HashMap<>();
        this.additiveManager = new AdditiveOperationManager();
        this.guidGenerator = new GUIDGenerator();
        initializeFileTypeObjectTypes();
    }

    /**
     * Initialize mapping of file types to their primary object types
     */
    private void initializeFileTypeObjectTypes() {
        fileTypeObjectTypes.put("UniteDescriptor", "TEntityDescriptor");
        fileTypeObjectTypes.put("WeaponDescriptor", "TWeaponManagerModuleDescriptor");
        fileTypeObjectTypes.put("Ammunition", "TAmmunitionDescriptor");
        fileTypeObjectTypes.put("AmmunitionMissiles", "TAmmunitionDescriptor");
        fileTypeObjectTypes.put("CapaciteList", "TCapaciteDescriptor");
        fileTypeObjectTypes.put("EffetsSurUnite", "TEffectsPackDescriptor");
        fileTypeObjectTypes.put("SoundDescriptors", "TSoundDescriptor");
        fileTypeObjectTypes.put("WeaponSoundHappenings", "TSoundHappening");
        fileTypeObjectTypes.put("DamageResistance", "TDamageResistanceDescriptor");
        fileTypeObjectTypes.put("DamageResistanceFamilyList", "TDamageResistanceFamilyDescriptor");
        fileTypeObjectTypes.put("ExperienceLevels", "TExperienceLevelDescriptor");
        fileTypeObjectTypes.put("ProjectileType", "EProjectileType");
        fileTypeObjectTypes.put("NdfDepictionList", "ConstantDefinition");
        fileTypeObjectTypes.put("GeneratedInfantryDepiction", "TInfantryDepictionDescriptor");
        fileTypeObjectTypes.put("VehicleDepiction", "TVehicleDepictionDescriptor");
        fileTypeObjectTypes.put("InfantryAnimationDescriptor", "TInfantryAnimationDescriptor");
        fileTypeObjectTypes.put("VehicleAnimationDescriptor", "TVehicleAnimationDescriptor");
        fileTypeObjectTypes.put("AircraftAnimationDescriptor", "TAircraftAnimationDescriptor");
        fileTypeObjectTypes.put("SupplyDescriptor", "TSupplyDescriptor");
        fileTypeObjectTypes.put("ReconDescriptor", "TReconDescriptor");
        fileTypeObjectTypes.put("CommandDescriptor", "TCommandDescriptor");
        fileTypeObjectTypes.put("ExperienceLevels", "TExperienceLevelDescriptor");
    }
    
    /**
     * Analyze open files to discover what entity types can be created
     */
    public void analyzeOpenFiles(Map<String, List<NDFValue.ObjectValue>> openFiles) {
        entityBlueprints.clear();
        templateExamples.clear();
        learnedPropertyDefaults.clear();
        optionalDependencies.clear();

        // Learn templates from ALL open files for on-demand creation
        learnTemplatesFromOpenFiles(openFiles);

        // Only analyze entity creation if we have UniteDescriptor.ndf (primary file)
        if (!openFiles.containsKey("UniteDescriptor")) {
            return;
        }

        // Learn from existing objects
        additiveManager.learnFromExistingObjects(getAllObjects(openFiles));

        // Learn template examples and property defaults
        learnFromExistingEntities(openFiles);

        // Discover entity types and their requirements
        discoverEntityBlueprints(openFiles);
    }

    /**
     * Learn templates from all open files for on-demand object creation
     */
    private void learnTemplatesFromOpenFiles(Map<String, List<NDFValue.ObjectValue>> openFiles) {
        for (Map.Entry<String, List<NDFValue.ObjectValue>> entry : openFiles.entrySet()) {
            String fileType = entry.getKey();
            List<NDFValue.ObjectValue> objects = entry.getValue();

            if (objects != null && !objects.isEmpty()) {
                // Create template manager for this file type
                NDFTemplateManager templateManager = new NDFTemplateManager();
                templateManager.learnFromObjects(objects);
                fileTypeTemplates.put(fileType, templateManager);

                System.out.println("Learned templates for " + fileType + " from " + objects.size() + " objects");
            }
        }
    }
    
    private List<NDFValue.ObjectValue> getAllObjects(Map<String, List<NDFValue.ObjectValue>> openFiles) {
        List<NDFValue.ObjectValue> allObjects = new ArrayList<>();
        for (List<NDFValue.ObjectValue> objects : openFiles.values()) {
            if (objects != null) {
                allObjects.addAll(objects);
            }
        }
        return allObjects;
    }

    /**
     * Learn from existing entities to create perfect templates with realistic placeholder values
     */
    private void learnFromExistingEntities(Map<String, List<NDFValue.ObjectValue>> openFiles) {
        List<NDFValue.ObjectValue> units = openFiles.get("UniteDescriptor");
        Map<String, List<NDFValue.ObjectValue>> entitiesByType = new HashMap<>();

        // Group units by entity type
        for (NDFValue.ObjectValue unit : units) {
            String entityType = classifyUnit(unit);
            if (entityType != null) {
                entitiesByType.computeIfAbsent(entityType, k -> new ArrayList<>()).add(unit);
            }
        }

        // Learn from each entity type
        for (Map.Entry<String, List<NDFValue.ObjectValue>> entry : entitiesByType.entrySet()) {
            String entityType = entry.getKey();
            List<NDFValue.ObjectValue> entities = entry.getValue();

            if (!entities.isEmpty()) {
                // Use the first complete entity as template example
                NDFValue.ObjectValue templateExample = findMostCompleteEntity(entities);
                templateExamples.put(entityType, templateExample);

                // Learn property defaults from all entities of this type
                Map<String, Object> propertyDefaults = learnPropertyDefaults(entities);
                learnedPropertyDefaults.put(entityType, propertyDefaults);

                // Analyze which dependencies are optional vs required
                Set<String> optionalDeps = analyzeOptionalDependencies(entities, openFiles);
                optionalDependencies.put(entityType, optionalDeps);
            }
        }
    }

    /**
     * Find the most complete entity (most properties, most modules) to use as template
     */
    private NDFValue.ObjectValue findMostCompleteEntity(List<NDFValue.ObjectValue> entities) {
        NDFValue.ObjectValue mostComplete = entities.get(0);
        int maxComplexity = calculateEntityComplexity(mostComplete);

        for (NDFValue.ObjectValue entity : entities) {
            int complexity = calculateEntityComplexity(entity);
            if (complexity > maxComplexity) {
                maxComplexity = complexity;
                mostComplete = entity;
            }
        }

        return mostComplete;
    }

    /**
     * Calculate entity complexity based on number of properties and modules
     */
    private int calculateEntityComplexity(NDFValue.ObjectValue entity) {
        int complexity = entity.getProperties().size();

        // Add complexity for modules
        if (entity.hasProperty("ModulesDescriptors")) {
            NDFValue modulesValue = entity.getProperty("ModulesDescriptors");
            if (modulesValue instanceof NDFValue.ArrayValue) {
                NDFValue.ArrayValue modules = (NDFValue.ArrayValue) modulesValue;
                complexity += modules.getElements().size() * 2; // Modules are more complex
            }
        }

        // Add complexity for weapon manager
        if (hasWeaponManager(entity)) {
            complexity += 5; // Weapons add significant complexity
        }

        return complexity;
    }

    /**
     * Learn realistic property defaults from existing entities
     */
    private Map<String, Object> learnPropertyDefaults(List<NDFValue.ObjectValue> entities) {
        Map<String, Object> defaults = new HashMap<>();
        Map<String, Map<Object, Integer>> propertyValueCounts = new HashMap<>();

        // Count frequency of property values across all entities
        for (NDFValue.ObjectValue entity : entities) {
            for (Map.Entry<String, NDFValue> prop : entity.getProperties().entrySet()) {
                String propName = prop.getKey();
                Object propValue = extractComparableValue(prop.getValue());

                if (propValue != null) {
                    propertyValueCounts.computeIfAbsent(propName, k -> new HashMap<>())
                                     .merge(propValue, 1, Integer::sum);
                }
            }
        }

        // Choose most common value for each property as default
        for (Map.Entry<String, Map<Object, Integer>> entry : propertyValueCounts.entrySet()) {
            String propName = entry.getKey();
            Map<Object, Integer> valueCounts = entry.getValue();

            Object mostCommonValue = valueCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

            if (mostCommonValue != null) {
                defaults.put(propName, mostCommonValue);
            }
        }

        return defaults;
    }

    /**
     * Extract comparable value from NDFValue for frequency analysis
     */
    private Object extractComparableValue(NDFValue value) {
        if (value instanceof NDFValue.StringValue) {
            return ((NDFValue.StringValue) value).getValue();
        } else if (value instanceof NDFValue.NumberValue) {
            return ((NDFValue.NumberValue) value).getValue();
        } else if (value instanceof NDFValue.BooleanValue) {
            return ((NDFValue.BooleanValue) value).getValue();
        } else if (value instanceof NDFValue.EnumValue) {
            return ((NDFValue.EnumValue) value).getValue();
        }
        return null; // Complex types not suitable for defaults
    }

    /**
     * Analyze which dependencies are optional by checking how many entities actually use them
     */
    private Set<String> analyzeOptionalDependencies(List<NDFValue.ObjectValue> entities,
                                                   Map<String, List<NDFValue.ObjectValue>> openFiles) {
        Set<String> optionalDeps = new HashSet<>();
        Map<String, Integer> dependencyUsage = new HashMap<>();

        // Count how many entities actually use each dependency
        for (NDFValue.ObjectValue entity : entities) {
            EntityTypeAnalysis tempAnalysis = new EntityTypeAnalysis();
            analyzeFileDependencies(entity, tempAnalysis, openFiles);

            for (String fileType : tempAnalysis.fileRequirements.keySet()) {
                dependencyUsage.merge(fileType, 1, Integer::sum);
            }
        }

        // Dependencies used by less than 50% of entities are considered optional
        // Also, certain file types are inherently optional regardless of usage
        int totalEntities = entities.size();
        double optionalThreshold = 0.5; // Much more reasonable threshold

        for (Map.Entry<String, Integer> entry : dependencyUsage.entrySet()) {
            String fileType = entry.getKey();
            int usage = entry.getValue();
            double usageRatio = (double) usage / totalEntities;

            if (usageRatio < optionalThreshold && !isAlwaysRequired(fileType)) {
                optionalDeps.add(fileType);
            }

            // Mark certain file types as inherently optional
            if (isInherentlyOptional(fileType)) {
                optionalDeps.add(fileType);
            }
        }

        return optionalDeps;
    }

    /**
     * Check if a file type is always required regardless of usage statistics
     */
    private boolean isAlwaysRequired(String fileType) {
        // Only UniteDescriptor is always required for entity creation
        // NdfDepictionList is a system-generated file and should not have entities added to it
        return "UniteDescriptor".equals(fileType);
    }

    /**
     * Check if a file type is inherently optional by design
     */
    private boolean isInherentlyOptional(String fileType) {
        // These file types are optional by design - not all units need them
        return "CapaciteList".equals(fileType) ||
               "EffetsSurUnite".equals(fileType) ||
               "ArtilleryProjectileDescriptor".equals(fileType) ||
               "WeaponSoundHappenings".equals(fileType) ||
               "VehicleSoundDescriptor".equals(fileType) ||
               "InfantryAnimationDescriptor".equals(fileType) ||
               "VehicleAnimationDescriptor".equals(fileType) ||
               "AircraftAnimationDescriptor".equals(fileType) ||
               "SupplyDescriptor".equals(fileType) ||
               "ReconDescriptor".equals(fileType) ||
               "CommandDescriptor".equals(fileType) ||
               "ExperienceLevels".equals(fileType);
    }
    
    /**
     * Discover entity blueprints based on actual cross-file dependencies
     */
    private void discoverEntityBlueprints(Map<String, List<NDFValue.ObjectValue>> openFiles) {
        List<NDFValue.ObjectValue> units = openFiles.get("UniteDescriptor");
        Map<String, EntityTypeAnalysis> analysis = new HashMap<>();
        
        // Analyze each unit to understand entity patterns
        for (NDFValue.ObjectValue unit : units) {
            String entityType = classifyUnit(unit);
            if (entityType != null) {
                EntityTypeAnalysis typeAnalysis = analysis.computeIfAbsent(entityType, k -> new EntityTypeAnalysis());
                typeAnalysis.addExample(unit);
                analyzeFileDependencies(unit, typeAnalysis, openFiles);
            }
        }
        
        // Convert analysis to blueprints
        for (Map.Entry<String, EntityTypeAnalysis> entry : analysis.entrySet()) {
            EntityBlueprint blueprint = createBlueprint(entry.getKey(), entry.getValue(), openFiles);
            entityBlueprints.put(entry.getKey(), blueprint);
        }
    }
    
    /**
     * Classify unit based on UnitRole and other reliable indicators
     */
    private String classifyUnit(NDFValue.ObjectValue unit) {
        // Check for missile units first (they might not have standard UnitRole)
        if (isMissileUnit(unit) || hasMissileModules(unit)) {
            return "MISSILE";
        }

        // Get UnitRole from TUnitUIModuleDescriptor (most reliable)
        String unitRole = extractUnitRole(unit);
        if (unitRole != null) {
            String role = unitRole.toLowerCase();
            // Handle enum format like "EUnitRole/Tank"
            if (role.contains("/")) {
                role = role.substring(role.lastIndexOf("/") + 1);
            }

            switch (role) {
                // Tank/Armor roles
                case "armor": return "Tank";
                case "hq_tank": return "Tank";

                // Artillery roles
                case "howitzer": return "Artillery";
                case "mortar": return "Artillery";
                case "mlrs": return "Artillery";

                // Infantry roles
                case "infantry": return "Infantry";
                case "hq_inf": return "Infantry";
                case "engineer": return "Infantry";

                // Air Defense roles
                case "aa": return "Air Defense";

                // Transport/Vehicle roles
                case "transport": return "Transport";
                case "ifv": return "IFV";
                case "hq_veh": return "Command Vehicle";

                // Reconnaissance roles
                case "reco": return "Reconnaissance";

                // Support roles
                case "supply": return "Supply";
                case "log": return "Logistics";
                case "cmd": return "Command";

                // Aircraft roles (fixed-wing)
                case "appui": return "Aircraft";
                case "sead": return "SEAD Aircraft";
                case "uav": return "UAV";
                case "avion": return "Aircraft";
                case "plane": return "Aircraft";
                case "fighter": return "Fighter Aircraft";
                case "bomber": return "Bomber Aircraft";

                // Helicopter roles
                case "hq_helo": return "Command Helicopter";
                case "helo": return "Helicopter";
                case "helicopter": return "Helicopter";

                // Anti-Tank roles
                case "at": return "Anti-Tank";
            }
        }


        return null;
    }
    
    /**
     * Analyze what files this unit type requires - comprehensive analysis for ALL unit types
     */
    private void analyzeFileDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                       Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // ALWAYS requires UniteDescriptor.ndf - this is the primary file
        analysis.addFileRequirement("UniteDescriptor", "TEntityDescriptor");

        // Comprehensive dependency analysis
        analyzeWeaponDependencies(unit, analysis, openFiles);
        analyzeMissileDependencies(unit, analysis, openFiles);
        analyzeBuildingDependencies(unit, analysis, openFiles);
        analyzeDepictionDependencies(unit, analysis, openFiles);
        analyzeEffectDependencies(unit, analysis, openFiles);
        analyzeProjectileDependencies(unit, analysis, openFiles);
        analyzeSoundDependencies(unit, analysis, openFiles);
        analyzeAnimationDependencies(unit, analysis, openFiles);
        analyzeTemplateReferenceDependencies(unit, analysis, openFiles);
        analyzeSpecializedDependencies(unit, analysis, openFiles);
    }

    /**
     * Analyze weapon-related file dependencies
     */
    private void analyzeWeaponDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                         Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Check for WeaponManager property (most combat units have this)
        if (hasWeaponManager(unit)) {
            // Units with weapons need WeaponDescriptor.ndf
            analysis.addFileRequirement("WeaponDescriptor", "TWeaponManagerModuleDescriptor");

            // Weapons need ammunition - this is almost universal
            analysis.addFileRequirement("Ammunition", "TAmmunitionDescriptor");
        }
    }

    /**
     * Analyze missile-specific dependencies
     */
    private void analyzeMissileDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                          Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Check for missile-specific modules or properties
        if (isMissileUnit(unit) || hasMissileModules(unit)) {
            // Missile units need specialized ammunition
            analysis.addFileRequirement("AmmunitionMissiles", "TAmmunitionDescriptor");

            // Some missiles need MissileDescriptors.ndf
            analysis.addFileRequirement("MissileDescriptors", "TEntityDescriptor");
        }
    }

    /**
     * Analyze building-specific dependencies
     */
    private void analyzeBuildingDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                           Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Check for building modules
        if (hasBuildingModules(unit)) {
            analysis.addFileRequirement("BuildingDescriptors", "TEntityDescriptor");
        }
    }

    /**
     * Analyze depiction (visual) dependencies - CRITICAL for all units
     */
    private void analyzeDepictionDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                            Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // NOTE: NdfDepictionList is a system-generated file that should not have entities added to it
        // It contains a list of depiction file paths and is managed by the game engine

        // Infantry units specifically need GeneratedDepictionInfantry
        String entityType = classifyUnit(unit);
        if ("Infantry".equals(entityType)) {
            analysis.addFileRequirement("GeneratedDepictionInfantry", "TInfantryDepictionDescriptor");
        }

        // All units need depiction resources which are managed through NdfDepictionList
        if (!"Infantry".equals(entityType)) {
            analysis.addFileRequirement("NdfDepictionList", "DepictionResourceList");
        }

        // Check for specific depiction modules
        if (hasModuleOfType(unit, "TDepictionModuleDescriptor")) {
            analysis.addFileRequirement("DepictionDescriptor", "TDepictionDescriptor");
        }
    }

    /**
     * Analyze effect dependencies - weapons, explosions, etc.
     */
    private void analyzeEffectDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                         Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Units with special abilities need capacities defined in CapaciteList.ndf
        if (hasModuleOfType(unit, "TCapaciteModuleDescriptor")) {
            analysis.addFileRequirement("CapaciteList", "TCapaciteDescriptor");
        }

        // Units with effects need effects defined in EffetsSurUnite.ndf
        if (hasModuleOfType(unit, "TEffectApplierModuleDescriptor") ||
            hasModuleOfType(unit, "TEffectModuleDescriptor") ||
            hasModuleOfType(unit, "TSpecialEffectModuleDescriptor")) {
            analysis.addFileRequirement("EffetsSurUnite", "TEffectsPackDescriptor");
        }

        // Damage effects - Note: DamageDescriptor.ndf doesn't exist
        // Damage info is in DamageResistance.ndf and DamageResistanceFamilyList.ndf
        if (hasModuleOfType(unit, "TDamageModuleDescriptor")) {
            analysis.addFileRequirement("DamageResistance", "TDamageResistanceDescriptor");
            analysis.addFileRequirement("DamageResistanceFamilyList", "TDamageResistanceFamilyDescriptor");
        }
    }

    /**
     * Analyze projectile dependencies - ballistics, trajectories
     */
    private void analyzeProjectileDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                             Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Note: ProjectileDescriptor.ndf and BallisticDescriptor.ndf don't exist
        // Projectile and ballistic information is embedded in Ammunition.ndf files

        // Artillery specifically needs ballistic descriptors
        String entityType = classifyUnit(unit);
        if ("Artillery".equals(entityType)) {
            analysis.addFileRequirement("ArtilleryProjectileDescriptor", "TArtilleryProjectileDescriptor");
        }
    }

    /**
     * Analyze sound dependencies
     */
    private void analyzeSoundDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                        Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Correct file name is SoundDescriptors.ndf (plural), not SoundDescriptor.ndf
        // All units need sound descriptors
        analysis.addFileRequirement("SoundDescriptors", "TSoundDescriptor");

        // Units with weapons need weapon sound happenings (maps SFXWeapon_* to sound descriptors)
        if (hasWeaponManager(unit)) {
            analysis.addFileRequirement("WeaponSoundHappenings", "TSoundHappening");
        }

        // Vehicles need engine sounds (if they exist)
        String entityType = classifyUnit(unit);
        if ("Tank".equals(entityType) || "IFV".equals(entityType) || "Transport".equals(entityType) ||
            "Command Vehicle".equals(entityType) || "Reconnaissance".equals(entityType) ||
            "Air Defense".equals(entityType) || "Artillery".equals(entityType) ||
            "Anti-Tank".equals(entityType) || "Supply".equals(entityType) ||
            "Logistics".equals(entityType) || "Command".equals(entityType)) {
            analysis.addFileRequirement("VehicleSoundDescriptor", "TVehicleSoundDescriptor");
        }
    }

    /**
     * Analyze animation dependencies
     */
    private void analyzeAnimationDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                            Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Animation descriptors are typically embedded in the depiction files
        // No separate animation descriptor files needed
    }

    /**
     * Analyze template reference dependencies - cross-file template chains
     */
    private void analyzeTemplateReferenceDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                                     Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Analyze all template references in the unit to find cross-file dependencies
        analyzeTemplateReferencesRecursive(unit, analysis, new HashSet<>());
    }

    /**
     * Recursively analyze template references to find cross-file dependencies
     */
    private void analyzeTemplateReferencesRecursive(NDFValue value, EntityTypeAnalysis analysis, Set<String> visited) {
        if (value instanceof NDFValue.TemplateRefValue) {
            NDFValue.TemplateRefValue templateRef = (NDFValue.TemplateRefValue) value;
            String path = templateRef.getPath();

            // Skip if already visited to avoid infinite loops
            if (visited.contains(path)) return;
            visited.add(path);

            // Determine what file this template reference points to
            String requiredFile = determineFileFromTemplatePath(path);
            if (requiredFile != null) {
                String objectType = determineObjectTypeFromTemplatePath(path);
                analysis.addFileRequirement(requiredFile, objectType);
            }
        } else if (value instanceof NDFValue.ObjectValue) {
            NDFValue.ObjectValue obj = (NDFValue.ObjectValue) value;
            for (Map.Entry<String, NDFValue> prop : obj.getProperties().entrySet()) {
                analyzeTemplateReferencesRecursive(prop.getValue(), analysis, visited);
            }
        } else if (value instanceof NDFValue.ArrayValue) {
            NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
            for (NDFValue element : array.getElements()) {
                analyzeTemplateReferencesRecursive(element, analysis, visited);
            }
        } else if (value instanceof NDFValue.MapValue) {
            NDFValue.MapValue map = (NDFValue.MapValue) value;
            for (Map.Entry<NDFValue, NDFValue> entry : map.getEntries()) {
                analyzeTemplateReferencesRecursive(entry.getKey(), analysis, visited);
                analyzeTemplateReferencesRecursive(entry.getValue(), analysis, visited);
            }
        }
    }

    /**
     * Determine which file a template path points to
     */
    private String determineFileFromTemplatePath(String templatePath) {
        if (templatePath == null) return null;

        String lower = templatePath.toLowerCase();

        // Weapon-related templates
        if (lower.contains("weapondescriptor_") || lower.contains("weapon_")) {
            return "WeaponDescriptor";
        }

        // Ammunition templates
        if (lower.contains("ammunition_") || lower.contains("ammo_")) {
            if (lower.contains("missile")) {
                return "AmmunitionMissiles";
            }
            return "Ammunition";
        }

        // Depiction templates
        if (lower.contains("depiction_") || lower.contains("visual_")) {
            if (lower.contains("infantry")) {
                return "GeneratedInfantryDepiction";
            }
            // NdfDepictionList is system-generated, route to appropriate depiction file
            return "DepictionDescriptor";
        }

        // Capacity/ability templates
        if (lower.contains("capacite_") || lower.contains("ability_") || lower.contains("skill_")) {
            return "CapaciteList";
        }

        // Projectile templates - Note: ProjectileDescriptor.ndf doesn't exist, projectile info is in Ammunition.ndf
        if (lower.contains("projectile_") || lower.contains("ballistic_")) {
            return "Ammunition";
        }

        // Sound templates
        if (lower.contains("sound_") || lower.contains("audio_")) {
            // Correct file name is SoundDescriptors.ndf (plural), not SoundDescriptor.ndf
            return "SoundDescriptors";
        }

        // Missile templates
        if (lower.contains("missile_")) {
            return "MissileDescriptors";
        }

        // Building templates
        if (lower.contains("building_")) {
            return "BuildingDescriptors";
        }

        return null; // Unknown template path
    }

    /**
     * Determine object type from template path
     */
    private String determineObjectTypeFromTemplatePath(String templatePath) {
        if (templatePath == null) return "TEntityDescriptor";

        String lower = templatePath.toLowerCase();

        if (lower.contains("weapondescriptor_")) return "TWeaponManagerModuleDescriptor";
        if (lower.contains("ammunition_")) return "TAmmunitionDescriptor";
        if (lower.contains("depiction_")) return "ConstantDefinition";
        if (lower.contains("effect_")) return "TEffectsPackDescriptor";
        if (lower.contains("projectile_")) return "TAmmunitionDescriptor"; // Projectile info is in ammunition files
        if (lower.contains("sound_")) return "TSoundDescriptor";
        if (lower.contains("missile_")) return "TEntityDescriptor";
        if (lower.contains("building_")) return "TBuildingDescriptor";

        return "TEntityDescriptor"; // Default
    }

    /**
     * Analyze other specialized dependencies
     */
    private void analyzeSpecializedDependencies(NDFValue.ObjectValue unit, EntityTypeAnalysis analysis,
                                              Map<String, List<NDFValue.ObjectValue>> openFiles) {
        // Experience and progression systems
        if (hasModuleOfType(unit, "TExperienceModuleDescriptor")) {
            analysis.addFileRequirement("ExperienceLevels", "TExperienceDescriptor");
        }

        // Production and factory systems - Note: Production.ndf only contains simple constants, not descriptors
        // TProductionModuleDescriptor doesn't require additional files beyond the unit itself

        // Supply systems
        if (hasModuleOfType(unit, "TSupplyModuleDescriptor")) {
            analysis.addFileRequirement("SupplyDescriptor", "TSupplyDescriptor");
        }

        // Reconnaissance systems
        if (hasModuleOfType(unit, "TReconModuleDescriptor")) {
            analysis.addFileRequirement("ReconDescriptor", "TReconDescriptor");
        }

        // Command and control systems
        if (hasModuleOfType(unit, "TCommandModuleDescriptor")) {
            analysis.addFileRequirement("CommandDescriptor", "TCommandDescriptor");
        }
    }

    /**
     * Check if unit has weapon manager (most combat units do)
     * WeaponManager is a direct property of the unit, not inside ModulesDescriptors
     */
    private boolean hasWeaponManager(NDFValue.ObjectValue unit) {
        // Check for direct WeaponManager property
        if (unit.hasProperty("WeaponManager")) {
            NDFValue weaponManager = unit.getProperty("WeaponManager");
            if (weaponManager instanceof NDFValue.ObjectValue) {
                NDFValue.ObjectValue weaponObj = (NDFValue.ObjectValue) weaponManager;
                if ("TModuleSelector".equals(weaponObj.getTypeName()) &&
                    weaponObj.hasProperty("Default")) {
                    NDFValue defaultValue = weaponObj.getProperty("Default");
                    if (defaultValue instanceof NDFValue.TemplateRefValue) {
                        String path = ((NDFValue.TemplateRefValue) defaultValue).getPath();
                        return path.contains("WeaponDescriptor_");
                    }
                }
            }
        }

        // Also check inside ModulesDescriptors for any weapon-related modules
        if (unit.hasProperty("ModulesDescriptors")) {
            NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
            if (modulesValue instanceof NDFValue.ArrayValue) {
                NDFValue.ArrayValue modules = (NDFValue.ArrayValue) modulesValue;
                for (NDFValue module : modules.getElements()) {
                    if (module instanceof NDFValue.ObjectValue) {
                        NDFValue.ObjectValue moduleObj = (NDFValue.ObjectValue) module;
                        String moduleType = moduleObj.getTypeName();
                        if (moduleType != null && moduleType.contains("Weapon")) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
    
    private String extractUnitRole(NDFValue.ObjectValue unit) {
        return extractFromModule(unit, "TUnitUIModuleDescriptor", "UnitRole");
    }
    
    private String extractMovingType(NDFValue.ObjectValue unit) {
        return extractFromModule(unit, "TLandMovementModuleDescriptor", "UnitMovingType");
    }
    
    private String extractFromModule(NDFValue.ObjectValue unit, String moduleType, String propertyName) {
        if (unit.hasProperty("ModulesDescriptors")) {
            NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
            if (modulesValue instanceof NDFValue.ArrayValue) {
                NDFValue.ArrayValue modules = (NDFValue.ArrayValue) modulesValue;
                for (NDFValue module : modules.getElements()) {
                    if (module instanceof NDFValue.ObjectValue) {
                        NDFValue.ObjectValue moduleObj = (NDFValue.ObjectValue) module;
                        if (moduleType.equals(moduleObj.getTypeName()) && moduleObj.hasProperty(propertyName)) {
                            NDFValue propValue = moduleObj.getProperty(propertyName);
                            if (propValue instanceof NDFValue.StringValue) {
                                return ((NDFValue.StringValue) propValue).getValue();
                            } else if (propValue instanceof NDFValue.EnumValue) {
                                return ((NDFValue.EnumValue) propValue).getValue();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    

    
    private boolean isMissileUnit(NDFValue.ObjectValue unit) {
        String instanceName = unit.getInstanceName();
        return instanceName != null && instanceName.toLowerCase().contains("missile");
    }

    /**
     * Check if unit has missile-specific modules
     */
    private boolean hasMissileModules(NDFValue.ObjectValue unit) {
        return hasModuleOfType(unit, "TMissileModuleDescriptor") ||
               hasModuleOfType(unit, "TGuidedMissileModuleDescriptor") ||
               hasProperty(unit, "MissileCarriage");
    }

    /**
     * Check if unit has building-specific modules
     */
    private boolean hasBuildingModules(NDFValue.ObjectValue unit) {
        return hasModuleOfType(unit, "TBuildingModuleDescriptor") ||
               hasModuleOfType(unit, "TConstructionModuleDescriptor");
    }

    /**
     * Check if unit has special effect modules
     */
    private boolean hasSpecialEffectModules(NDFValue.ObjectValue unit) {
        return hasModuleOfType(unit, "TEffectModuleDescriptor") ||
               hasModuleOfType(unit, "TSpecialEffectModuleDescriptor");
    }

    /**
     * Check if unit has a specific module type
     */
    private boolean hasModuleOfType(NDFValue.ObjectValue unit, String moduleType) {
        if (unit.hasProperty("ModulesDescriptors")) {
            NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
            if (modulesValue instanceof NDFValue.ArrayValue) {
                NDFValue.ArrayValue modules = (NDFValue.ArrayValue) modulesValue;
                for (NDFValue module : modules.getElements()) {
                    if (module instanceof NDFValue.ObjectValue) {
                        NDFValue.ObjectValue moduleObj = (NDFValue.ObjectValue) module;
                        String type = moduleObj.getTypeName();
                        if (moduleType.equals(type)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if unit has a specific property (at top level)
     */
    private boolean hasProperty(NDFValue.ObjectValue unit, String propertyName) {
        return unit.hasProperty(propertyName);
    }
    

    
    /**
     * Create blueprint from analysis with template examples and optional dependency info
     */
    private EntityBlueprint createBlueprint(String entityType, EntityTypeAnalysis analysis,
                                          Map<String, List<NDFValue.ObjectValue>> openFiles) {
        String displayName = formatEntityTypeName(entityType);
        String description = "Complete " + displayName + " entity based on " + analysis.exampleCount + " examples";

        EntityBlueprint blueprint = new EntityBlueprint(entityType, displayName, description);

        // Set template example for perfect placeholder generation
        NDFValue.ObjectValue templateExample = templateExamples.get(entityType);
        if (templateExample != null) {
            blueprint.setTemplateExample(templateExample);
        }

        // Set learned property defaults
        Map<String, Object> propertyDefaults = learnedPropertyDefaults.get(entityType);
        if (propertyDefaults != null) {
            blueprint.setPropertyDefaults(propertyDefaults);
        }

        // Add ALL file requirements with optional/required classification
        Set<String> optionalDeps = optionalDependencies.getOrDefault(entityType, new HashSet<>());

        for (Map.Entry<String, String> req : analysis.fileRequirements.entrySet()) {
            String fileType = req.getKey();
            String objectType = req.getValue();
            boolean isOptional = optionalDeps.contains(fileType);
            blueprint.addFileRequirement(fileType, objectType, isOptional);
        }

        return blueprint;
    }
    
    private String formatEntityTypeName(String entityType) {
        return entityType.substring(0, 1).toUpperCase() + 
               entityType.substring(1).toLowerCase().replace("_", " ");
    }
    
    /**
     * Get available entity types
     */
    public Set<String> getAvailableEntityTypes() {
        return new HashSet<>(entityBlueprints.keySet());
    }
    
    /**
     * Get entity blueprint
     */
    public EntityBlueprint getEntityBlueprint(String entityType) {
        return entityBlueprints.get(entityType);
    }

    /**
     * Create a complete entity across multiple files with perfect templates and cross-references
     */
    public EntityCreationResult createCompleteEntity(String entityType, String entityName,
                                                   Map<String, Object> customProperties,
                                                   Map<String, List<NDFValue.ObjectValue>> openFiles,
                                                   Map<String, ModificationTracker> trackers) {
        // Validate input parameters
        if (entityType == null || entityType.trim().isEmpty()) {
            return new EntityCreationResult(false, entityName).addError("Entity type cannot be null or empty");
        }

        if (entityName == null || entityName.trim().isEmpty()) {
            return new EntityCreationResult(false, entityName).addError("Entity name cannot be null or empty");
        }

        EntityCreationResult result = new EntityCreationResult(true, entityName);

        EntityBlueprint blueprint = entityBlueprints.get(entityType);
        if (blueprint == null) {
            result.addError("Unknown entity type: " + entityType);
            return new EntityCreationResult(false, entityName);
        }

        // Generate base GUID for this entity - used for cross-file consistency
        String baseGuid = guidGenerator.generateGUID();
        Map<String, String> crossFileReferences = new HashMap<>(); // fileType -> templateName

        // First pass: Create all objects and collect template names for cross-references
        for (FileRequirement requirement : blueprint.getFileRequirements()) {
            if (requirement.isOptional() && !openFiles.containsKey(requirement.getFileType())) {
                // Skip optional files that aren't open
                continue;
            }

            String fileType = requirement.getFileType();
            List<NDFValue.ObjectValue> objects = openFiles.get(fileType);
            ModificationTracker tracker = trackers.get(fileType);

            if (objects == null) {
                if (requirement.isRequired()) {
                    result.addError("Required file not open: " + fileType);
                }
                continue;
            }

            // Generate unique template name for cross-references
            String templateName = generateUniqueTemplateName(entityName, requirement.getObjectType(), fileType, openFiles);
            crossFileReferences.put(fileType, templateName);
        }

        // Second pass: Create objects with perfect templates and cross-references
        for (FileRequirement requirement : blueprint.getFileRequirements()) {
            String fileType = requirement.getFileType();
            String templateName = crossFileReferences.get(fileType);

            // Create perfect template properties using learned defaults and cross-references
            Map<String, Object> templateProperties = createPerfectTemplate(
                blueprint, requirement, entityName, baseGuid, customProperties, crossFileReferences);

            boolean success = false;

            // Check if file is open
            if (openFiles.containsKey(fileType)) {
                // File is open - use existing system with entity-type-specific template
                List<NDFValue.ObjectValue> objects = openFiles.get(fileType);
                ModificationTracker tracker = trackers.get(fileType);

                // Get entity-type-specific template for better object creation
                NDFValue.ObjectValue entityTemplate = getEntityTypeSpecificTemplate(
                    blueprint.getEntityType(), fileType, requirement.getObjectType());

                success = additiveManager.addNewObjectWithTemplate(
                    objects,
                    requirement.getObjectType(),
                    templateName,
                    templateProperties,
                    tracker,
                    entityTemplate
                );
            } else if (requirement.isRequired()) {
                // Required file not open - this is an error
                result.addError("Required file not open: " + fileType);
                continue;
            } else {
                // Optional file not open - create from template if we have one
                success = createObjectFromTemplate(fileType, requirement.getObjectType(),
                                                 templateName, templateProperties, result);
            }

            if (success) {
                result.addCreatedObject(fileType, templateName);
            } else {
                result.addError("Failed to create " + requirement.getObjectType() + " in " + fileType);
            }
        }

        return result;
    }

    /**
     * Create object from template for files that aren't open
     */
    private boolean createObjectFromTemplate(String fileType, String objectType, String templateName,
                                           Map<String, Object> templateProperties, EntityCreationResult result) {
        // Check if we have learned templates for this file type
        NDFTemplateManager templateManager = fileTypeTemplates.get(fileType);
        if (templateManager == null) {
            // No template available - create basic object
            result.addPendingFileCreation(fileType, templateName, objectType, templateProperties);
            return true;
        }

        // Get template from learned patterns - use the specific object type requested
        NDFValue.ObjectValue template = templateManager.getTemplate(objectType);
        if (template == null) {
            // Try fallback to primary object type for this file
            String primaryObjectType = fileTypeObjectTypes.get(fileType);
            if (primaryObjectType != null && !primaryObjectType.equals(objectType)) {
                template = templateManager.getTemplate(primaryObjectType);
            }
        }

        if (template == null) {
            // No specific template - create basic object
            result.addPendingFileCreation(fileType, templateName, objectType, templateProperties);
            return true;
        }

        // Create object from template with custom properties
        NDFValue.ObjectValue newObject = createObjectFromTemplateWithProperties(template, templateName, templateProperties);
        result.addPendingFileCreation(fileType, templateName, newObject);

        return true;
    }

    /**
     * Get entity-type-specific template for better template selection
     */
    private NDFValue.ObjectValue getEntityTypeSpecificTemplate(String entityType, String fileType, String objectType) {
        // First try to get a template example for this specific entity type
        NDFValue.ObjectValue templateExample = templateExamples.get(entityType);
        if (templateExample != null && fileType.equals("UniteDescriptor")) {
            return templateExample;
        }

        // Fall back to learned templates from file type
        NDFTemplateManager templateManager = fileTypeTemplates.get(fileType);
        if (templateManager != null) {
            return templateManager.getTemplate(objectType);
        }

        return null;
    }

    /**
     * Create object from template with custom properties applied
     */
    private NDFValue.ObjectValue createObjectFromTemplateWithProperties(NDFValue.ObjectValue template,
                                                                       String templateName,
                                                                       Map<String, Object> customProperties) {
        // Clone the template
        NDFValue.ObjectValue newObject = NDFValue.createObject(template.getTypeName());

        // Copy all properties from template
        for (Map.Entry<String, NDFValue> entry : template.getProperties().entrySet()) {
            newObject.setProperty(entry.getKey(), entry.getValue());
        }

        // Apply custom properties
        for (Map.Entry<String, Object> entry : customProperties.entrySet()) {
            String propName = entry.getKey();
            Object propValue = entry.getValue();

            NDFValue ndfValue = convertObjectToNDFValue(propValue);
            if (ndfValue != null) {
                newObject.setProperty(propName, ndfValue);
            }
        }

        return newObject;
    }

    /**
     * Convert Object to NDFValue for property setting
     */
    private NDFValue convertObjectToNDFValue(Object value) {
        if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.startsWith("~/")) {
                return NDFValue.createTemplateRef(strValue);
            } else if (strValue.startsWith("$/")) {
                return NDFValue.createResourceRef(strValue);
            } else if (strValue.startsWith("GUID:")) {
                return NDFValue.createGuid(strValue);
            } else if (strValue.contains("/") && (strValue.startsWith("E") || strValue.contains("Family"))) {
                return NDFValue.createEnum(strValue);
            } else {
                return NDFValue.createString(strValue);
            }
        } else if (value instanceof Number) {
            return NDFValue.createNumber(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            return NDFValue.createBoolean((Boolean) value);
        } else if (value instanceof NDFValue) {
            return (NDFValue) value;
        }

        return NDFValue.createString(value.toString());
    }

    /**
     * Generate unique template name following WARNO naming conventions, avoiding collisions
     */
    private String generateUniqueTemplateName(String entityName, String objectType, String fileType,
                                            Map<String, List<NDFValue.ObjectValue>> openFiles) {
        String baseName = generateTemplateName(entityName, objectType, fileType);

        // Check if this file is open and if the name already exists
        List<NDFValue.ObjectValue> objects = openFiles.get(fileType);
        if (objects == null || objects.isEmpty()) {
            return baseName; // File not open or empty, no collision possible
        }

        // Check for collision
        if (!hasNameCollisionInFile(objects, baseName)) {
            return baseName; // No collision, use original name
        }

        // Generate unique name by appending counter
        int counter = 1;
        String uniqueName;
        do {
            uniqueName = baseName + "_" + counter;
            counter++;
        } while (hasNameCollisionInFile(objects, uniqueName));

        return uniqueName;
    }

    /**
     * Generate template name following WARNO naming conventions
     */
    private String generateTemplateName(String entityName, String objectType, String fileType) {
        // Follow WARNO naming patterns based on file type
        switch (fileType) {
            case "UniteDescriptor":
                return "Descriptor_Unit_" + entityName;
            case "WeaponDescriptor":
                return "WeaponDescriptor_" + entityName;
            case "Ammunition":
                return "Ammunition_" + entityName;
            case "AmmunitionMissiles":
                return "AmmunitionMissiles_" + entityName;
            case "GeneratedInfantryDepiction":
                return "InfantryDepiction_" + entityName;
            case "VehicleDepiction":
                return "VehicleDepiction_" + entityName;
            case "CapaciteList":
                return "UnitCapacity_" + entityName;
            case "WeaponSoundHappenings":
                return "WeaponSound_" + entityName;
            default:
                // Fallback to generic naming
                String cleanType = objectType.replace("T", "").replace("Descriptor", "");
                return cleanType + "_" + entityName;
        }
    }

    /**
     * Check if an object with the given name exists in the file
     */
    private boolean hasNameCollisionInFile(List<NDFValue.ObjectValue> objects, String instanceName) {
        for (NDFValue.ObjectValue obj : objects) {
            if (instanceName.equals(obj.getInstanceName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create perfect template properties using learned defaults and cross-references
     */
    private Map<String, Object> createPerfectTemplate(EntityBlueprint blueprint, FileRequirement requirement,
                                                     String entityName, String baseGuid,
                                                     Map<String, Object> customProperties,
                                                     Map<String, String> crossFileReferences) {
        Map<String, Object> templateProperties = new HashMap<>();

        // Start with learned property defaults for this entity type
        Map<String, Object> defaults = blueprint.getPropertyDefaults();
        if (defaults != null) {
            templateProperties.putAll(defaults);
        }

        // Add universal properties
        templateProperties.put("DescriptorId", baseGuid);
        templateProperties.put("ClassNameForDebug", entityName);

        // Add file-specific properties with perfect cross-references
        addFileSpecificPropertiesWithCrossReferences(templateProperties, requirement, entityName,
                                                    blueprint.getEntityType(), crossFileReferences);

        // Apply custom properties (user overrides)
        templateProperties.putAll(customProperties);

        // Use template example to fill in complex structures
        if (blueprint.getTemplateExample() != null) {
            enhanceWithTemplateExample(templateProperties, blueprint.getTemplateExample(), requirement);
        }

        return templateProperties;
    }

    /**
     * Add file-specific properties with perfect cross-references to other files
     */
    private void addFileSpecificPropertiesWithCrossReferences(Map<String, Object> properties,
                                                             FileRequirement requirement, String entityName,
                                                             String entityType, Map<String, String> crossFileReferences) {
        String fileType = requirement.getFileType();
        String objectType = requirement.getObjectType();

        switch (fileType) {
            case "UniteDescriptor":
                // Main unit properties with cross-references
                if (!properties.containsKey("Coalition")) {
                    properties.put("Coalition", "ECoalition/Allied");
                }
                if (!properties.containsKey("MotherCountry")) {
                    properties.put("MotherCountry", "USA");
                }

                // Cross-reference to weapon descriptor
                String weaponTemplate = crossFileReferences.get("WeaponDescriptor");
                if (weaponTemplate != null) {
                    properties.put("WeaponManager", "~/WeaponDescriptor_" + weaponTemplate);
                }

                // Cross-reference to depiction (use DepictionDescriptor instead of NdfDepictionList)
                String depictionTemplate = crossFileReferences.get("DepictionDescriptor");
                if (depictionTemplate != null) {
                    properties.put("DepictionTemplate", "~/DepictionDescriptor_" + depictionTemplate);
                }
                break;

            case "WeaponDescriptor":
                // Weapon properties with ammunition cross-reference
                if (!properties.containsKey("WeaponName")) {
                    properties.put("WeaponName", entityName + "_Weapon");
                }

                String ammoTemplate = crossFileReferences.get("Ammunition");
                if (ammoTemplate != null) {
                    properties.put("Ammunition", "~/Ammunition_" + ammoTemplate);
                }

                String capacityTemplate = crossFileReferences.get("CapaciteList");
                if (capacityTemplate != null) {
                    properties.put("SpecialAbilities", "~/CapaciteList_" + capacityTemplate);
                }
                break;

            case "Ammunition":
                // Ammunition properties
                if (!properties.containsKey("Name")) {
                    properties.put("Name", entityName + "_Ammo");
                }
                if (!properties.containsKey("Caliber")) {
                    properties.put("Caliber", getDefaultCaliber(entityType));
                }

                // Add essential projectile properties that are embedded in ammunition files
                if (!properties.containsKey("ProjectileType")) {
                    properties.put("ProjectileType", getDefaultProjectileType(entityType));
                }
                if (!properties.containsKey("MaximalSpeedGRU")) {
                    properties.put("MaximalSpeedGRU", "0.0");
                }
                if (!properties.containsKey("FluidFriction")) {
                    properties.put("FluidFriction", getDefaultFluidFriction(entityType));
                }
                break;

            case "CapaciteList":
                // Capacity properties
                if (!properties.containsKey("NameForDebug")) {
                    properties.put("NameForDebug", entityName);
                }
                if (!properties.containsKey("CapacityDescriptors")) {
                    properties.put("EffectsDescriptors", "[]");
                }
                break;
        }
    }

    /**
     * Get default caliber based on entity type
     */
    private String getDefaultCaliber(String entityType) {
        switch (entityType) {
            case "Tank": return "120mm";
            case "Infantry": return "7.62mm";
            case "Artillery": return "155mm";
            case "Air Defense": return "35mm";
            case "IFV": return "25mm";
            case "Transport": return "12.7mm";
            case "Command Vehicle": return "12.7mm";
            case "Reconnaissance": return "20mm";
            case "Anti-Tank": return "105mm";
            case "Aircraft":
            case "Fighter Aircraft":
            case "Bomber Aircraft": return "20mm";
            case "SEAD Aircraft": return "20mm";
            case "UAV": return "Hellfire";
            case "Helicopter":
            case "Command Helicopter": return "20mm";
            case "Supply":
            case "Logistics":
            case "Command": return "7.62mm";
            default: return "25mm";
        }
    }

    /**
     * Get default projectile type based on entity type
     */
    private String getDefaultProjectileType(String entityType) {
        switch (entityType) {
            case "Aircraft":
            case "Fighter Aircraft":
            case "Bomber Aircraft":
                return "EProjectileType/Bombe";
            case "SEAD Aircraft":
                return "EProjectileType/GuidedMissile";
            case "UAV":
                return "EProjectileType/GuidedMissile";
            case "Helicopter":
            case "Command Helicopter":
                return "EProjectileType/Roquette";
            case "Artillery":
                return "EProjectileType/Artillerie";
            case "Air Defense":
                return "EProjectileType/GuidedMissile";
            case "Anti-Tank":
                return "EProjectileType/GuidedMissile";
            case "Infantry":
                return "EProjectileType/Balle";
            default:
                return "EProjectileType/Obus";
        }
    }

    /**
     * Get default fluid friction based on entity type
     */
    private String getDefaultFluidFriction(String entityType) {
        switch (entityType) {
            case "Aircraft":
            case "Fighter Aircraft":
            case "Bomber Aircraft":
                return "0.4"; // Aircraft bombs have higher friction
            case "SEAD Aircraft":
            case "UAV":
                return "0.1"; // Guided missiles have low friction
            case "Helicopter":
            case "Command Helicopter":
                return "0.2"; // Helicopter rockets have some friction
            case "Air Defense":
            case "Anti-Tank":
                return "0.1"; // Guided missiles have low friction
            case "Artillery":
                return "0.3"; // Artillery shells have some friction
            default:
                return "0.0"; // Most projectiles have no friction
        }
    }

    /**
     * Enhance properties using template example for complex structures
     */
    private void enhanceWithTemplateExample(Map<String, Object> properties, NDFValue.ObjectValue templateExample,
                                           FileRequirement requirement) {
        // Copy complex structures from template example that aren't simple properties
        for (Map.Entry<String, NDFValue> entry : templateExample.getProperties().entrySet()) {
            String propName = entry.getKey();
            NDFValue propValue = entry.getValue();

            // Only copy complex structures, not simple values that we've already set
            if (!properties.containsKey(propName) && isComplexStructure(propValue)) {
                Object convertedValue = convertNDFValueToObject(propValue);
                if (convertedValue != null) {
                    properties.put(propName, convertedValue);
                }
            }
        }
    }

    /**
     * Check if an NDFValue represents a complex structure worth copying
     */
    private boolean isComplexStructure(NDFValue value) {
        return value instanceof NDFValue.ArrayValue ||
               value instanceof NDFValue.ObjectValue ||
               value instanceof NDFValue.MapValue;
    }

    /**
     * Convert NDFValue to Object for property setting
     */
    private Object convertNDFValueToObject(NDFValue value) {
        if (value instanceof NDFValue.StringValue) {
            return ((NDFValue.StringValue) value).getValue();
        } else if (value instanceof NDFValue.NumberValue) {
            return ((NDFValue.NumberValue) value).getValue();
        } else if (value instanceof NDFValue.BooleanValue) {
            return ((NDFValue.BooleanValue) value).getValue();
        } else if (value instanceof NDFValue.EnumValue) {
            return ((NDFValue.EnumValue) value).getValue();
        } else if (value instanceof NDFValue.TemplateRefValue) {
            return ((NDFValue.TemplateRefValue) value).getPath();
        }
        // For complex types, return the NDFValue itself
        return value;
    }



    /**
     * Result of entity creation operation with support for pending file creations
     */
    public static class EntityCreationResult {
        private final boolean success;
        private final String entityName;
        private final Map<String, String> createdObjects; // fileType -> objectName
        private final List<String> errors;
        private final List<PendingFileCreation> pendingCreations; // Objects to be created in files not yet open

        public EntityCreationResult(boolean success, String entityName) {
            this.success = success;
            this.entityName = entityName;
            this.createdObjects = new HashMap<>();
            this.errors = new ArrayList<>();
            this.pendingCreations = new ArrayList<>();
        }

        public boolean isSuccess() { return success; }
        public String getEntityName() { return entityName; }
        public Map<String, String> getCreatedObjects() { return createdObjects; }
        public List<String> getErrors() { return errors; }
        public List<PendingFileCreation> getPendingCreations() { return pendingCreations; }
        public boolean hasPendingCreations() { return !pendingCreations.isEmpty(); }

        public void addCreatedObject(String fileType, String objectName) {
            createdObjects.put(fileType, objectName);
        }

        public EntityCreationResult addError(String error) {
            errors.add(error);
            return this;
        }

        public void addPendingFileCreation(String fileType, String templateName, String objectType, Map<String, Object> properties) {
            pendingCreations.add(new PendingFileCreation(fileType, templateName, objectType, properties));
        }

        public void addPendingFileCreation(String fileType, String templateName, NDFValue.ObjectValue object) {
            pendingCreations.add(new PendingFileCreation(fileType, templateName, object));
        }
    }

    /**
     * Represents an object that needs to be created in a file that isn't currently open
     */
    public static class PendingFileCreation {
        private final String fileType;
        private final String templateName;
        private final String objectType;
        private final Map<String, Object> properties;
        private final NDFValue.ObjectValue object;

        public PendingFileCreation(String fileType, String templateName, String objectType, Map<String, Object> properties) {
            this.fileType = fileType;
            this.templateName = templateName;
            this.objectType = objectType;
            this.properties = properties;
            this.object = null;
        }

        public PendingFileCreation(String fileType, String templateName, NDFValue.ObjectValue object) {
            this.fileType = fileType;
            this.templateName = templateName;
            this.objectType = object.getTypeName();
            this.properties = null;
            this.object = object;
        }

        public String getFileType() { return fileType; }
        public String getTemplateName() { return templateName; }
        public String getObjectType() { return objectType; }
        public Map<String, Object> getProperties() { return properties; }
        public NDFValue.ObjectValue getObject() { return object; }
        public boolean hasObject() { return object != null; }

        public String getDisplayName() {
            return fileType + ".ndf → " + templateName;
        }
    }
    
    /**
     * Analysis data for an entity type
     */
    private static class EntityTypeAnalysis {
        final Map<String, String> fileRequirements = new HashMap<>(); // fileType -> objectType
        int exampleCount = 0;
        
        void addExample(NDFValue.ObjectValue unit) {
            exampleCount++;
        }
        
        void addFileRequirement(String fileType, String objectType) {
            fileRequirements.put(fileType, objectType);
        }
    }
    
    /**
     * Blueprint for creating a complete entity with template learning and cross-references
     */
    public static class EntityBlueprint {
        private final String entityType;
        private final String displayName;
        private final String description;
        private final List<FileRequirement> fileRequirements;

        // Template learning data
        private NDFValue.ObjectValue templateExample;
        private Map<String, Object> propertyDefaults;

        public EntityBlueprint(String entityType, String displayName, String description) {
            this.entityType = entityType;
            this.displayName = displayName;
            this.description = description;
            this.fileRequirements = new ArrayList<>();
            this.propertyDefaults = new HashMap<>();
        }

        public void addFileRequirement(String fileType, String objectType) {
            fileRequirements.add(new FileRequirement(fileType, objectType, false));
        }

        public void addFileRequirement(String fileType, String objectType, boolean isOptional) {
            fileRequirements.add(new FileRequirement(fileType, objectType, isOptional));
        }

        public void setTemplateExample(NDFValue.ObjectValue templateExample) {
            this.templateExample = templateExample;
        }

        public void setPropertyDefaults(Map<String, Object> propertyDefaults) {
            this.propertyDefaults = propertyDefaults;
        }

        public String getEntityType() { return entityType; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public List<FileRequirement> getFileRequirements() { return fileRequirements; }
        public NDFValue.ObjectValue getTemplateExample() { return templateExample; }
        public Map<String, Object> getPropertyDefaults() { return propertyDefaults; }

        public List<FileRequirement> getRequiredFiles() {
            return fileRequirements.stream()
                .filter(req -> !req.isOptional())
                .collect(java.util.stream.Collectors.toList());
        }

        public List<FileRequirement> getOptionalFiles() {
            return fileRequirements.stream()
                .filter(FileRequirement::isOptional)
                .collect(java.util.stream.Collectors.toList());
        }
    }
    
    /**
     * File requirement for entity creation with optional/required classification
     */
    public static class FileRequirement {
        private final String fileType;
        private final String objectType;
        private final boolean isOptional;

        public FileRequirement(String fileType, String objectType, boolean isOptional) {
            this.fileType = fileType;
            this.objectType = objectType;
            this.isOptional = isOptional;
        }

        public String getFileType() { return fileType; }
        public String getObjectType() { return objectType; }
        public boolean isOptional() { return isOptional; }
        public boolean isRequired() { return !isOptional; }

        public String getDisplayName() {
            return fileType + ".ndf" + (isOptional ? " (Optional)" : " (Required)");
        }
    }
}
