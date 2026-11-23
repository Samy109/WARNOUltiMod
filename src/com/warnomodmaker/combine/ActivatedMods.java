package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.IgnoreCase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        List<String> current = read(paths);
        List<String> kept = new ArrayList<>();
        kept.add(outputName);
        for (String entry : current) {
            if (entry.equalsIgnoreCase(outputName)) continue;
            if (contained.contains(entry)) names.add(describe(entry, sources));
            else kept.add(entry);
        }
        write(paths, kept);
        log.accept("  Activated '" + outputName + "' for WARNO's next start"
            + (names.isEmpty() ? "." : " and deactivated the mods it already contains: " + String.join(", ", names) + "."));
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

    private static String describe(String entry, List<ModDescriptor> sources) {
        for (ModDescriptor mod : sources) if (entry.equalsIgnoreCase(mod.workshopId)) return mod.name;
        return entry;
    }

    private static Path file(WarnoPaths paths) {
        return paths.savedModsRoot.resolve("Config.ini");
    }
}
