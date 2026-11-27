package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.CombinedModState;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.SourceModFingerprint;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.Hex;
import com.warnomodmaker.combine.ndf.IgnoreCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads and writes combined mods' tracking records (.warno-moderator.json, as WARNO UltiAI MODerator writes them). */
public final class CombinedModStateStore {
    public static final String FILE_NAME = ".warno-moderator.json";

    public List<CombinedModState> findAll(WarnoPaths paths, Cancel token) {
        List<CombinedModState> results = new ArrayList<>();
        for (Path directory : FileOps.directories(paths.modsRoot)) {
            token.check();
            CombinedModState state = tryLoad(directory);
            if (state != null) results.add(state);
        }
        results.sort((a, b) -> IgnoreCase.ORDER.compare(a.outputName, b.outputName));
        return results;
    }

    /** Outputs built from exactly the selected mods, in any priority order. */
    public List<CombinedModState> findAllForSources(WarnoPaths paths, List<ModDescriptor> mods, Cancel token) {
        List<CombinedModState> result = new ArrayList<>();
        for (CombinedModState state : findAll(paths, token)) {
            if (state.sources().size() != mods.size()) continue;
            boolean all = mods.stream().allMatch(mod -> state.sources().stream().anyMatch(s -> resolve(s, mods) == mod));
            if (all) result.add(state);
        }
        return result;
    }

    public static String suggestNewOutputName(WarnoPaths paths, String baseName) {
        if (baseName.length() > 105) baseName = baseName.substring(0, 105).replaceAll("[ .]+$", "");
        if (!MergePlanner.isValidOutputName(baseName)) baseName = "Combined mod";
        String candidate = baseName;
        for (int number = 2; outputExists(paths, candidate); number++) candidate = baseName + " (" + number + ")";
        return candidate;
    }

    public static boolean outputExists(WarnoPaths paths, String outputName) {
        return Files.exists(paths.modsRoot.resolve(outputName)) || Files.exists(paths.savedModsRoot.resolve(outputName));
    }

    public CombinedModState tryLoad(Path outputDirectory) {
        Path statePath = outputDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(statePath)) return null;
        try {
            Object parsed = Json.parse(new String(Files.readAllBytes(statePath), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map)) return null;
            Map<?, ?> json = (Map<?, ?>) parsed;
            Integer schema = Json.integer(json, "schemaVersion");
            String outputName = Json.str(json, "outputName");
            List<SourceModFingerprint> mods = null;
            if (Json.get(json, "mods") instanceof List) {
                mods = new ArrayList<>();
                for (Object item : (List<?>) Json.get(json, "mods")) mods.add(fingerprint(item));
            }
            CombinedModState state = new CombinedModState(schema == null ? 0 : schema, outputName, mods,
                Json.str(json, "runtimeFingerprint"), Json.str(json, "gameFingerprint"),
                fingerprint(Json.get(json, "otherMod")), fingerprint(Json.get(json, "priorityMod")));
            boolean valid = (state.schemaVersion == 1 || state.schemaVersion == CombinedModState.CURRENT_SCHEMA_VERSION)
                && state.sources().size() >= 2
                && state.sources().stream().allMatch(CombinedModStateStore::validFingerprint)
                && outputName != null && MergePlanner.isValidOutputName(outputName)
                && outputName.equalsIgnoreCase(outputDirectory.getFileName().toString());
            return valid ? state : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static SourceModFingerprint fingerprint(Object item) {
        if (!(item instanceof Map)) return null;
        Map<?, ?> map = (Map<?, ?>) item;
        return new SourceModFingerprint(Json.str(map, "name"), Json.str(map, "rootPath"), Json.str(map, "fingerprint"));
    }

    public void save(Path outputDirectory, CombinedModState state) {
        if (!Files.isDirectory(outputDirectory))
            throw new CombineException("The combined mod output is missing; its update state could not be saved.");
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schemaVersion", state.schemaVersion);
        json.put("outputName", state.outputName);
        if (state.mods != null) {
            List<Object> mods = new ArrayList<>();
            for (SourceModFingerprint mod : state.mods) mods.add(fingerprintJson(mod));
            json.put("mods", mods);
        }
        json.put("runtimeFingerprint", state.runtimeFingerprint);
        json.put("gameFingerprint", state.gameFingerprint);
        if (state.otherMod != null) json.put("otherMod", fingerprintJson(state.otherMod));
        if (state.priorityMod != null) json.put("priorityMod", fingerprintJson(state.priorityMod));
        Path statePath = outputDirectory.resolve(FILE_NAME);
        Path temporary = outputDirectory.resolve(FILE_NAME + ".tmp-" + UUID.randomUUID().toString().replace("-", ""));
        try {
            Files.write(temporary, Json.write(json).getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, statePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Nothing to clean up.
            }
        }
    }

    private static Map<String, Object> fingerprintJson(SourceModFingerprint mod) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", mod.name);
        map.put("rootPath", mod.rootPath);
        map.put("fingerprint", mod.fingerprint);
        return map;
    }

    public static boolean fingerprintMatches(SourceModFingerprint stored, SourceModFingerprint current) {
        return sameSource(stored.rootPath, current.rootPath) && stored.fingerprint.equalsIgnoreCase(current.fingerprint);
    }

    private static boolean validFingerprint(SourceModFingerprint fingerprint) {
        if (fingerprint == null || fingerprint.name == null || fingerprint.name.trim().isEmpty()
            || fingerprint.rootPath == null || fingerprint.rootPath.trim().isEmpty() || fingerprint.fingerprint == null) return false;
        if (fingerprint.fingerprint.length() != 64 || !Hex.isHex(fingerprint.fingerprint)) return false;
        try {
            return Paths.get(fingerprint.rootPath).isAbsolute();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The installed mod a combined mod's recorded source now is: the one at the same place (see {@link #sameSource}),
     * else the only Workshop item with the same name, for a Workshop mod its author re-published under a new ID.
     * Null when it isn't installed.
     */
    public static ModDescriptor resolve(SourceModFingerprint source, List<ModDescriptor> installed) {
        if (source == null || source.rootPath == null) return null;
        for (ModDescriptor m : installed) if (sameSource(source.rootPath, m.rootPath.toString())) return m;
        if (source.name == null || !source.rootPath.toLowerCase().replace('/', '\\').contains("\\workshop\\content\\1611600\\")) return null;
        ModDescriptor workshop = byName(source.name, installed, Models.ModKind.WORKSHOP_COMPILED);
        // Not subscribed under the new ID, but the same mod is installed as an editable mod (its author's copy).
        return workshop != null ? workshop : byName(source.name, installed, Models.ModKind.EDITABLE_SOURCE);
    }

    private static ModDescriptor byName(String name, List<ModDescriptor> installed, Models.ModKind kind) {
        ModDescriptor found = null;
        for (ModDescriptor m : installed) {
            if (m.kind != kind || m.isCombined() || !m.name.trim().equalsIgnoreCase(name.trim())) continue;
            if (found != null) return null; // two mods share the name; don't guess
            found = m;
        }
        return found;
    }

    /**
     * True when a source mod recorded at {@code storedRoot} is the mod now at {@code currentRoot}: the same folder,
     * or the same Workshop item (its ID) or editable mod (its folder in WARNO\Mods) after Steam moved WARNO and its
     * Workshop content to another library.
     */
    public static boolean sameSource(String storedRoot, String currentRoot) {
        try {
            Path stored = Paths.get(storedRoot), current = Paths.get(currentRoot);
            if (FileOps.samePath(stored, current)) return true;
            String storedFull = FileOps.full(stored), currentFull = FileOps.full(current);
            String marker = "\\workshop\\content\\1611600\\";
            boolean storedWorkshop = storedFull.toLowerCase().contains(marker), currentWorkshop = currentFull.toLowerCase().contains(marker);
            String storedFolder = Paths.get(storedFull).getFileName().toString(), currentFolder = Paths.get(currentFull).getFileName().toString();
            if (storedWorkshop != currentWorkshop || !storedFolder.equalsIgnoreCase(currentFolder)) return false;
            if (storedWorkshop) return true;
            Path parent = Paths.get(storedFull).getParent();
            return parent != null && parent.getFileName() != null && parent.getFileName().toString().equalsIgnoreCase("Mods");
        } catch (RuntimeException e) {
            return false;
        }
    }
}
