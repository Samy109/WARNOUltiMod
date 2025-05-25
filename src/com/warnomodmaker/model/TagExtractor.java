package com.warnomodmaker.model;

import com.warnomodmaker.model.NDFValue.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for extracting and categorizing tags from unit descriptors
 */
public class TagExtractor {

    /**
     * Extracts all meaningful tags from a list of unit descriptors (no UNITE tags)
     *
     * @param unitDescriptors The unit descriptors to extract tags from
     * @return Set of all meaningful tags
     */
    public static Set<String> extractAllMeaningfulTags(List<ObjectValue> unitDescriptors) {
        Set<String> allTags = new HashSet<>();

        // Extract all unique tags, excluding meaningless ones
        for (ObjectValue unit : unitDescriptors) {
            Set<String> unitTags = extractTagsFromUnit(unit);
            // Filter out tags containing "UNITE" as they're not meaningful for filtering
            unitTags = unitTags.stream()
                .filter(tag -> !tag.contains("UNITE"))
                .collect(Collectors.toSet());
            allTags.addAll(unitTags);
        }

        return allTags;
    }

    /**
     * Extracts all tags from a list of unit descriptors
     *
     * @param unitDescriptors The unit descriptors to extract tags from
     * @return A map of tag categories to sets of tags
     */
    public static Map<String, Set<String>> extractAllTags(List<ObjectValue> unitDescriptors) {
        Map<String, Set<String>> categorizedTags = new HashMap<>();
        Set<String> allTags = extractAllMeaningfulTags(unitDescriptors);

        // Categorize tags
        categorizedTags.put("Unit Types", categorizeUnitTypeTags(allTags));
        categorizedTags.put("Weapons & Combat", categorizeWeaponTags(allTags));
        categorizedTags.put("Movement & Mobility", categorizeMobilityTags(allTags));
        categorizedTags.put("Special Abilities", categorizeSpecialTags(allTags));
        categorizedTags.put("Countries & Coalitions", categorizeCountryTags(allTags));
        categorizedTags.put("Other", categorizeOtherTags(allTags, categorizedTags));

        // Remove empty categories
        categorizedTags.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        return categorizedTags;
    }

    /**
     * Extracts tags from a single unit descriptor
     *
     * @param unit The unit descriptor
     * @return Set of tags for this unit
     */
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

                // Check if this is a TTagsModuleDescriptor
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
     * Gets units that have any of the specified tags
     *
     * @param unitDescriptors All unit descriptors
     * @param requiredTags Tags to filter by
     * @return List of units that have at least one of the required tags
     */
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

    /**
     * Gets units that have all of the specified tags
     *
     * @param unitDescriptors All unit descriptors
     * @param requiredTags Tags to filter by
     * @return List of units that have all of the required tags
     */
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

    /**
     * Categorizes unit type tags
     */
    private static Set<String> categorizeUnitTypeTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.contains("Tank") || tag.contains("Vehicule") || tag.contains("Helo") ||
                          tag.contains("Avion") || tag.contains("Infanterie") || tag.contains("Artillerie") ||
                          tag.contains("GroundUnits") || tag.contains("AllUnits"))
            .collect(Collectors.toSet());
    }

    /**
     * Categorizes weapon and combat related tags
     */
    private static Set<String> categorizeWeaponTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.contains("Canon") || tag.contains("AA") || tag.contains("AT") ||
                          tag.contains("Missile") || tag.contains("Weapon") || tag.contains("Reco") ||
                          tag.contains("SEAD") || tag.contains("Radar"))
            .collect(Collectors.toSet());
    }

    /**
     * Categorizes mobility and movement related tags
     */
    private static Set<String> categorizeMobilityTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.contains("Transport") || tag.contains("Logistique") ||
                          tag.contains("Mobile") || tag.contains("Fast"))
            .collect(Collectors.toSet());
    }

    /**
     * Categorizes special ability tags
     */
    private static Set<String> categorizeSpecialTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.contains("Stealth") || tag.contains("ECM") || tag.contains("Snipe") ||
                          tag.contains("Elite") || tag.contains("Special") || tag.contains("RoE"))
            .collect(Collectors.toSet());
    }

    /**
     * Categorizes country and coalition tags
     */
    private static Set<String> categorizeCountryTags(Set<String> allTags) {
        return allTags.stream()
            .filter(tag -> tag.matches(".*[A-Z]{2,3}$") || // Country codes like DDR, POL, etc.
                          tag.contains("NATO") || tag.contains("Warsaw"))
            .collect(Collectors.toSet());
    }

    /**
     * Categorizes remaining tags as "Other"
     */
    private static Set<String> categorizeOtherTags(Set<String> allTags, Map<String, Set<String>> categorizedTags) {
        Set<String> usedTags = new HashSet<>();
        categorizedTags.values().forEach(usedTags::addAll);

        return allTags.stream()
            .filter(tag -> !usedTags.contains(tag))
            .collect(Collectors.toSet());
    }
}
