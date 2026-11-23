package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.CombinedModState;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.SourceModFingerprint;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.IgnoreCase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WARNO's own list of mods to start with: Saved Games\EugenSystems\WARNO\mod\Config.ini,
 * {@code ActivatedMods = <entry>|<entry>|;comment}, where a local mod is its folder name and a Workshop mod its ID.
 *
 * <p>A combined mod already contains its source mods. If one of them stays activated beside it, WARNO compares their
 * database fingerprints and refuses to start ("Mods 'X' are incompatible") unless that mod happened to win every
 * database they share. So a combined mod is always activated in place of the mods it was built from.</p>
 */
public final class ActivatedMods {
    private static final Pattern LINE = Pattern.compile("^(\\s*ActivatedMods\\s*=)([^;\\r\\n]*)(;[^\\r\\n]*)?", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final String COMMENT = ";Set every mod you want activated for the next game startup separated by a '|'.";

    private ActivatedMods() {
    }

    /** Activates the combined mod and deactivates every mod it was built from, keeping any other activated mods. */
    public static void activateCombined(WarnoPaths paths, String outputName, List<ModDescriptor> sources, Consumer<String> log) {
        Set<String> contained = IgnoreCase.set();
        List<String> names = new ArrayList<>();
        for (ModDescriptor mod : sources) {
            contained.add(mod.name);
            if (mod.workshopId != null && !mod.workshopId.isEmpty()) contained.add(mod.workshopId);
            if (mod.rootPath.getFileName() != null) contained.add(mod.rootPath.getFileName().toString());
        }
        Path ownConfig = paths.savedModsRoot.resolve(outputName).resolve("Config.ini");
        if (!Files.isRegularFile(ownConfig)) throw new CombineException("The combined mod's manifest is missing: " + ownConfig);
        Map<String, String> fingerprints = IniDocument.load(ownConfig).getSection("Config");
        List<String> current = read(paths);
        List<String> kept = new ArrayList<>();
        kept.add(outputName);
        for (String entry : current) {
            if (entry.equalsIgnoreCase(outputName)) continue;
            Path config = installedConfig(paths, entry);
            if (contained.contains(entry)) names.add(describe(entry, sources));
            else if (config == null) names.add("'" + entry + "' (no longer installed)");
            else if (sharesSource(paths, entry, sources)) names.add("'" + entry + "' (an earlier combination of the same mods)");
            else if (conflicts(fingerprints, config)) names.add("'" + describeInstalled(entry, config) + "' (WARNO's compatibility check rejects it beside this combination)");
            else kept.add(entry);
        }
        write(paths, kept);
        log.accept("  Activated '" + outputName + "' for WARNO's next start"
            + (names.isEmpty() ? "." : " and deactivated: " + String.join(", ", names) + "."));
    }

    /**
     * The Config.ini WARNO reads for an activated entry (a Workshop ID or a local mod folder), or null when the mod
     * is not installed any more.
     */
    private static Path installedConfig(WarnoPaths paths, String entry) {
        List<Path> candidates = new ArrayList<>();
        if (entry.chars().allMatch(Character::isDigit)) candidates.add(paths.workshopRoot.resolve(entry).resolve("Config.ini"));
        if (MergePlanner.isValidOutputName(entry)) {
            candidates.add(paths.savedModsRoot.resolve(entry).resolve("Config.ini"));
            candidates.add(paths.modsRoot.resolve(entry).resolve("Config.ini"));
        }
        for (Path candidate : candidates) if (Files.isRegularFile(candidate)) return candidate;
        return null;
    }

    /**
     * WARNO's compatibility check: two activated mods are incompatible when both ship a database with different
     * [Config] fingerprints.
     */
    private static boolean conflicts(Map<String, String> ours, Path otherConfig) {
        Map<String, String> theirs = IniDocument.load(otherConfig).getSection("Config");
        for (Map.Entry<String, String> entry : ours.entrySet()) {
            String other = theirs.get(entry.getKey());
            if (other != null && !other.trim().equalsIgnoreCase(entry.getValue().trim())) return true;
        }
        return false;
    }

    private static String describeInstalled(String entry, Path config) {
        String name = IniDocument.load(config).get("Properties", "Name");
        return name != null && !name.trim().isEmpty() && !name.trim().equalsIgnoreCase(entry) ? name.trim() + " (" + entry + ")" : entry;
    }

    /** Removes a deleted combined mod from WARNO's activated mods. */
    public static void deactivate(WarnoPaths paths, String outputName, Consumer<String> log) {
        List<String> current = read(paths);
        List<String> kept = new ArrayList<>();
        for (String entry : current) if (!entry.equalsIgnoreCase(outputName)) kept.add(entry);
        if (kept.size() == current.size()) return;
        write(paths, kept);
        log.accept("Removed '" + outputName + "' from WARNO's activated mods.");
    }

    /** The activated entries, in order. */
    public static List<String> read(WarnoPaths paths) {
        List<String> entries = new ArrayList<>();
        Path file = file(paths);
        if (!Files.isRegularFile(file)) return entries;
        Matcher match = LINE.matcher(new String(FileOps.read(file), StandardCharsets.UTF_8));
        if (!match.find()) return entries;
        for (String entry : match.group(2).split("\\|")) if (!entry.trim().isEmpty()) entries.add(entry.trim());
        return entries;
    }

    private static void write(WarnoPaths paths, List<String> entries) {
        Path file = file(paths);
        String value = " " + String.join("|", entries) + "|";
        String text;
        if (Files.isRegularFile(file)) {
            text = new String(FileOps.read(file), StandardCharsets.UTF_8);
            Matcher match = LINE.matcher(text);
            if (match.find()) {
                String comment = match.group(3) != null ? match.group(3) : COMMENT;
                text = text.substring(0, match.start()) + match.group(1) + value + comment + text.substring(match.end());
            } else {
                String newline = text.contains("\r\n") ? "\r\n" : "\n";
                text = text.contains("[Config]")
                    ? text.replaceFirst("\\[Config\\][^\\r\\n]*", "$0" + newline + "ActivatedMods =" + Matcher.quoteReplacement(value + COMMENT))
                    : text + (text.isEmpty() || text.endsWith("\n") ? "" : newline) + "[Config]" + newline + "ActivatedMods =" + value + COMMENT + newline;
            }
        } else {
            text = "[Config]\nActivatedMods =" + value + COMMENT;
        }
        FileOps.write(file, text.getBytes(StandardCharsets.UTF_8));
        if (!read(paths).equals(entries)) throw new CombineException("WARNO's activated mod list couldn't be updated at " + file + ".");
    }

    /** True when the entry is another combined mod built from at least one of the same source mods. */
    private static boolean sharesSource(WarnoPaths paths, String entry, List<ModDescriptor> sources) {
        if (!MergePlanner.isValidOutputName(entry)) return false;
        Path folder = paths.modsRoot.resolve(entry);
        if (!Files.isDirectory(folder)) return false;
        CombinedModState state = new CombinedModStateStore().tryLoad(folder);
        if (state == null) return false;
        for (SourceModFingerprint used : state.sources()) {
            for (ModDescriptor mod : sources) {
                if (used.name != null && used.name.equalsIgnoreCase(mod.name)) return true;
                if (used.rootPath != null && CombinedModStateStore.sameSource(used.rootPath, mod.rootPath.toString())) return true;
            }
        }
        return false;
    }

    private static String describe(String entry, List<ModDescriptor> sources) {
        for (ModDescriptor mod : sources) if (entry.equalsIgnoreCase(mod.workshopId)) return mod.name;
        return entry;
    }

    private static Path file(WarnoPaths paths) {
        return paths.savedModsRoot.resolve("Config.ini");
    }
}
