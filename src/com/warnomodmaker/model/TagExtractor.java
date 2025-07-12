package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue.*;
import java.util.*;
import java.util.stream.Collectors;

public class TagExtractor {

    
    public static Set<String> extractAllMeaningfulTags(List<ObjectValue> unitDescriptors) {
        Set<String> allTags = new HashSet<>();

        for (ObjectValue unit : unitDescriptors) {
            Set<String> unitTags = extractTagsFromUnit(unit);
            unitTags = unitTags.stream()
                .filter(tag -> !tag.contains("UNITE"))
                .collect(Collectors.toSet());
            allTags.addAll(unitTags);
        }

        return allTags;
    }

    
    public static Map<String, Set<String>> extractAllTags(List<ObjectValue> unitDescriptors) {
        Map<String, Set<String>> categorizedTags = new HashMap<>();
        Set<String> allTags = extractAllMeaningfulTags(unitDescriptors);

        categorizedTags.put("Unit Types", categorizeUnitTypeTags(allTags));
        categorizedTags.put("Weapons & Combat", categorizeWeaponTags(allTags));
        categorizedTags.put("Movement & Mobility", categorizeMobilityTags(allTags));
        categorizedTags.put("Special Abilities", categorizeSpecialTags(allTags));

        // Extract UnitRole and SpecialtiesList for unitdescriptor.ndf files
        categorizedTags.put("Unit Roles", extractAllUnitRoles(unitDescriptors));
        categorizedTags.put("Specialties List", extractAllSpecialties(unitDescriptors));

        categorizedTags.put("Other", categorizeOtherTags(allTags, categorizedTags));
        categorizedTags.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        return categorizedTags;
    }

    /**
     * Extract all unique UnitRole values from units
     */
    public static Set<String> extractAllUnitRoles(List<ObjectValue> unitDescriptors) {
        Set<String> unitRoles = new HashSet<>();

        for (ObjectValue unit : unitDescriptors) {
            String unitRole = extractUnitRole(unit);
            if (unitRole != null && !unitRole.trim().isEmpty()) {
                unitRoles.add(unitRole.trim());
            }
        }

        return unitRoles;
    }

    /**
     * Extract all unique SpecialtiesList values from units
     */
    public static Set<String> extractAllSpecialties(List<ObjectValue> unitDescriptors) {
        Set<String> allSpecialties = new HashSet<>();

        for (ObjectValue unit : unitDescriptors) {
            Set<String> unitSpecialties = extractSpecialtiesList(unit);
            allSpecialties.addAll(unitSpecialties);
        }

        return allSpecialties;
    }

    /**
     * Get units that have the specified unit role
     */
    public static List<ObjectValue> getUnitsWithUnitRole(List<ObjectValue> unitDescriptors, String unitRole) {
        return unitDescriptors.stream()
            .filter(unit -> {
                String role = extractUnitRole(unit);
                return role != null && role.equalsIgnoreCase(unitRole);
            })
            .collect(Collectors.toList());
    }

    /**
     * Get units that have any of the specified specialties
     */
    public static List<ObjectValue> getUnitsWithSpecialties(List<ObjectValue> unitDescriptors, Set<String> requiredSpecialties) {
        if (requiredSpecialties == null || requiredSpecialties.isEmpty()) {
            return new ArrayList<>(unitDescriptors);
        }

        return unitDescriptors.stream()
            .filter(unit -> {
                Set<String> unitSpecialties = extractSpecialtiesList(unit);
                return unitSpecialties.stream().anyMatch(requiredSpecialties::contains);
            })
            .collect(Collectors.toList());
    }

    /**
     * Get units that have all of the specified specialties
     */
    public static List<ObjectValue> getUnitsWithAllSpecialties(List<ObjectValue> unitDescriptors, Set<String> requiredSpecialties) {
        if (requiredSpecialties == null || requiredSpecialties.isEmpty()) {
            return new ArrayList<>(unitDescriptors);
        }

        return unitDescriptors.stream()
            .filter(unit -> {
                Set<String> unitSpecialties = extractSpecialtiesList(unit);
                return unitSpecialties.containsAll(requiredSpecialties);
            })
            .collect(Collectors.toList());
    }

    
    public static Set<String> extractTagsFromUnit(ObjectValue unit) {
        Set<String> tags = new HashSet<>();

        // Look for TTagsModuleDescriptor in ModulesDescriptors array
        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return tags;
        }

        ArrayValue modulesArray = (ArrayValue) modulesValue;
        for (NDFValue moduleValue : modulesArray.getElements()) {
            if (moduleValue instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) moduleValue;
                if ("TTagsModuleDescriptor".equals(module.getTypeName())) {
                    NDFValue tagSetValue = module.getProperty("TagSet");
                    if (tagSetValue instanceof ArrayValue) {
                        ArrayValue tagSetArray = (ArrayValue) tagSetValue;
                        for (NDFValue tagValue : tagSetArray.getElements()) {
                            if (tagValue instanceof StringValue) {
                                String tag = ((StringValue) tagValue).getValue();
                                if (tag != null && !tag.trim().isEmpty()) {
                                    tags.add(tag.trim());
                                }
                            }
                        }
                    }
                    break; // Found the tags module, no need to continue
                }
            }
        }

        return tags;
    }

    /**
     * Extract UnitRole from TUnitUIModuleDescriptor for unitdescriptor.ndf files
     */
    public static String extractUnitRole(ObjectValue unit) {
        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return null;
        }

        ArrayValue modulesArray = (ArrayValue) modulesValue;
        for (NDFValue moduleValue : modulesArray.getElements()) {
            if (moduleValue instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) moduleValue;
                if ("TUnitUIModuleDescriptor".equals(module.getTypeName())) {
                    NDFValue unitRoleValue = module.getProperty("UnitRole");
                    if (unitRoleValue instanceof StringValue) {
                        String role = ((StringValue) unitRoleValue).getValue();
                        if (role != null && !role.trim().isEmpty()) {
                            // Handle enum format like "EUnitRole/Tank"
                            if (role.contains("/")) {
                                role = role.substring(role.lastIndexOf("/") + 1);
                            }
                            return role.trim();
                        }
                    }
                    break; // Found the UI module, no need to continue
                }
            }
        }

        return null;
    }

    /**
     * Extract SpecialtiesList from TUnitUIModuleDescriptor for unitdescriptor.ndf files
     */
    public static Set<String> extractSpecialtiesList(ObjectValue unit) {
        Set<String> specialties = new HashSet<>();

        NDFValue modulesValue = unit.getProperty("ModulesDescriptors");
        if (!(modulesValue instanceof ArrayValue)) {
            return specialties;
        }

        ArrayValue modulesArray = (ArrayValue) modulesValue;
        for (NDFValue moduleValue : modulesArray.getElements()) {
            if (moduleValue instanceof ObjectValue) {
                ObjectValue module = (ObjectValue) moduleValue;
                if ("TUnitUIModuleDescriptor".equals(module.getTypeName())) {
                    NDFValue specialtiesValue = module.getProperty("SpecialtiesList");
                    if (specialtiesValue instanceof ArrayValue) {
                        ArrayValue specialtiesArray = (ArrayValue) specialtiesValue;
                        for (NDFValue specialtyValue : specialtiesArray.getElements()) {
                            if (specialtyValue instanceof StringValue) {
                                String specialty = ((StringValue) specialtyValue).getValue();
                                if (specialty != null && !specialty.trim().isEmpty()) {
                                    specialties.add(specialty.trim());
                                }
                            }
                        }
                    }
                    break; // Found the UI module, no need to continue
                }
            }
        }

        return specialties;
    }

    
    public static List<ObjectValue> getUnitsWithTags(List<ObjectValue> unitDescriptors, Set<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) {
            return new ArrayList<>(unitDescriptors);
        }

        return unitDescriptors.stream()
            .filter(unit -> {
                Set<String> unitTags = extractTagsFromUnit(unit);
                return unitTags.stream().anyMatch(requiredTags::contains);
            })
            .collect(Collectors.toList());
    }

    
    public static List<ObjectValue> getUnitsWithAllTags(List<ObjectValue> unitDescriptors, Set<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) {
            return new ArrayList<>(unitDescriptors);
        }

        return unitDescriptors.stream()
            .filter(unit -> {
                Set<String> unitTags = extractTagsFromUnit(unit);
                return unitTags.containsAll(requiredTags);
            })
            .collect(Collectors.toList());
    }

    
    public static Set<String> categorizeUnitTypeTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.contains("Tank") || tag.contains("Vehicule") || tag.contains("Helo") ||
                          tag.contains("Avion") || tag.contains("Infanterie") || tag.contains("Artillerie") ||
                          tag.contains("GroundUnits") || tag.contains("AllUnits"))
            .collect(Collectors.toSet());
    }

    
    public static Set<String> categorizeWeaponTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.contains("Canon") || tag.contains("AA") || tag.contains("AT") ||
                          tag.contains("Missile") || tag.contains("Weapon") || tag.contains("Reco") ||
                          tag.contains("SEAD") || tag.contains("Radar"))
            .collect(Collectors.toSet());
    }

    
    public static Set<String> categorizeMobilityTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.contains("Transport") || tag.contains("Logistique") ||
                          tag.contains("Mobile") || tag.contains("Fast"))
            .collect(Collectors.toSet());
    }

    
    public static Set<String> categorizeSpecialTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.contains("Stealth") || tag.contains("ECM") || tag.contains("Snipe") ||
                          tag.contains("Elite") || tag.contains("Special") || tag.contains("RoE"))
            .collect(Collectors.toSet());
    }

    


    
    private static Set<String> categorizeOtherTags(Set<String> allTags, Map<String, Set<String>> categorizedTags) {
        Set<String> usedTags = new HashSet<>();
        categorizedTags.values().forEach(usedTags::addAll);

        return allTags.stream()
            .filter(tag -> !usedTags.contains(tag))
            .collect(Collectors.toSet());
    }
}
