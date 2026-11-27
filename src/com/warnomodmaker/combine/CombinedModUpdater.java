package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.CombineProgress;
import com.warnomodmaker.combine.Models.CombineRequest;
import com.warnomodmaker.combine.Models.CombineResult;
import com.warnomodmaker.combine.Models.CombinedModState;
import com.warnomodmaker.combine.Models.MergePreview;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.ModKind;
import com.warnomodmaker.combine.Models.SourceModFingerprint;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.IgnoreCase;
import com.warnomodmaker.combine.ndf.NdfFormatException;
import com.warnomodmaker.combine.ndf.VanillaDatabases;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Keeps combined mods current. A combined mod records a fingerprint of every source mod and of WARNO's vanilla mod
 * data when it is built; when a Workshop mod updates or WARNO patches, those stop matching and the combined mod is
 * rebuilt from the same mods in the same priority order. A source mod that doesn't match the installed WARNO is
 * reported with how far behind it is; the combined mod can then wait for it, or be rebuilt without it.
 */
public final class CombinedModUpdater {
    /** A source mod that can't go into a rebuild as it is, and why. */
    public static final class OutdatedSource {
        public final String name, reason;

        OutdatedSource(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
    }

    /** Where one combined mod stands against its source mods and the installed WARNO. */
    public static final class Update {
        public final CombinedModState state;
        /** The installed source mods that are current, in the combined mod's priority order. */
        public final List<ModDescriptor> sources;
        /** Source mods that are missing or don't match the installed WARNO. */
        public final List<OutdatedSource> outdated;
        /** What changed since the combined mod was built. */
        public final List<String> changes;

        Update(CombinedModState state, List<ModDescriptor> sources, List<OutdatedSource> outdated, List<String> changes) {
            this.state = state;
            this.sources = sources;
            this.outdated = outdated;
            this.changes = changes;
        }

        public String outputName() {
            return state.outputName;
        }

        public boolean upToDate() {
            return changes.isEmpty() && outdated.isEmpty();
        }

        /** Every source mod is current and something changed: a plain rebuild brings it up to date. */
        public boolean canUpdate() {
            return !changes.isEmpty() && outdated.isEmpty();
        }

        /** Some source mods are outdated, but at least two current ones remain to rebuild without them. */
        public boolean canUpdateWithoutOutdated() {
            return !outdated.isEmpty() && sources.size() >= 2;
        }

        public String status() {
            List<String> parts = new ArrayList<>(changes);
            for (OutdatedSource o : outdated) parts.add(o.name + ": " + o.reason);
            return parts.isEmpty() ? "Up to date" : String.join("; ", parts);
        }
    }

    private final CombineService service;
    private final MergePlanner planner;
    private final CombinedModStateStore store = new CombinedModStateStore();
    private final ModFingerprintService fingerprints = new ModFingerprintService();

    public CombinedModUpdater(CombineService service, MergePlanner planner) {
        this.service = service;
        this.planner = planner;
    }

    public List<Update> check(WarnoPaths paths, List<ModDescriptor> installed, Consumer<CombineProgress> progress, Cancel token) {
        List<CombinedModState> states = store.findAll(paths, token);
        if (states.isEmpty()) return Collections.emptyList();
        String game = FileOps.sha256Cached(paths.modDataBaseZip(), token);
        int modGen;
        try {
            modGen = VanillaDatabases.installedModGen(paths.warnoRoot);
        } catch (NdfFormatException e) {
            throw new CombineException(e.getMessage(), e);
        }
        List<ModDescriptor[]> resolved = new ArrayList<>();
        // Each source mod is checked and fingerprinted once, however many combined mods use it.
        Map<String, ModDescriptor> distinct = new LinkedHashMap<>();
        for (CombinedModState state : states) {
            ModDescriptor[] sources = new ModDescriptor[state.sources().size()];
            for (int i = 0; i < sources.length; i++) {
                sources[i] = CombinedModStateStore.resolve(state.sources().get(i), installed);
                if (sources[i] != null) distinct.putIfAbsent(IgnoreCase.upper(FileOps.full(sources[i].rootPath)), sources[i]);
            }
            resolved.add(sources);
        }
        TreeMap<String, String> reasons = IgnoreCase.map(), behind = IgnoreCase.map();
        for (Map.Entry<String, ModDescriptor> e : distinct.entrySet()) {
            reasons.put(e.getKey(), outdatedReason(paths, e.getValue(), modGen));
            behind.put(e.getKey(), behindNote(paths, e.getValue(), modGen, token));
        }
        TreeMap<String, SourceModFingerprint> current = IgnoreCase.map();
        for (SourceModFingerprint f : fingerprints.compute(new ArrayList<>(distinct.values()), progress, token, "Checking combined mods for updates"))
            current.put(IgnoreCase.upper(f.rootPath), f);

        var revisions = DeliveryManifest.revisions(paths.warnoRoot);
        List<Update> updates = new ArrayList<>();
        for (int s = 0; s < states.size(); s++) {
            CombinedModState state = states.get(s);
            ModDescriptor[] sources = resolved.get(s);
            List<OutdatedSource> outdated = new ArrayList<>();
            List<ModDescriptor> ready = new ArrayList<>();
            List<String> changes = new ArrayList<>();
            // A combined mod built for this WARNO already has its old source mods brought up to date; they only make it
            // out of date once they or WARNO change.
            Integer builtFor = builtModGen(paths, state.outputName);
            boolean builtForCurrent = builtFor != null && builtFor == modGen && game.equalsIgnoreCase(state.gameFingerprint);
            for (int i = 0; i < sources.length; i++) {
                SourceModFingerprint stored = state.sources().get(i);
                ModDescriptor mod = sources[i];
                if (mod == null) {
                    outdated.add(new OutdatedSource(stored.name, "no longer installed"));
                    continue;
                }
                String key = IgnoreCase.upper(FileOps.full(mod.rootPath));
                String reason = reasons.get(key);
                if (reason != null) outdated.add(new OutdatedSource(mod.name, reason));
                else ready.add(mod);
                boolean unchanged = CombinedModStateStore.fingerprintMatches(stored, current.get(key));
                if (!unchanged) changes.add(stored.name + " changed");
                String note = behind.get(key);
                if (note != null && !(unchanged && builtForCurrent)) changes.add(mod.name + " " + note);
            }
            if (builtFor != null && builtFor != modGen) changes.add("built for ModGen " + builtFor + ", WARNO is at " + modGen);
            else if (!game.equalsIgnoreCase(state.gameFingerprint)) changes.add("WARNO updated");
            Path runtime = paths.savedModsRoot.resolve(state.outputName);
            Path manifest = runtime.resolve("Config.ini");
            if (!Files.isRegularFile(manifest)) changes.add("Combined Config.ini is missing");
            else {
                String problem = DeliveryManifest.problem(IniDocument.load(manifest), revisions, runtime);
                if (problem != null) changes.add(problem);
            }
            updates.add(new Update(state, ready, outdated, changes));
        }
        return updates;
    }

    /** Rebuilds one combined mod in place from its current source mods, in its priority order. */
    public CombineResult update(WarnoPaths paths, Update update, boolean withoutOutdated, Consumer<String> log,
                                Consumer<CombineProgress> progress, Cancel token) {
        if (withoutOutdated ? !update.canUpdateWithoutOutdated() : !update.outdated.isEmpty())
            throw new CombineException("'" + update.outputName() + "' can't be updated: " + update.status() + ".");
        MergePreview plan = planner.createPreview(paths, update.sources, update.outputName(), true, token);
        return service.rebuild(new CombineRequest(paths, update.sources, update.outputName(), plan), log, progress, token);
    }

    /** Why a source mod can't go into a build for the installed WARNO at all, or null when it can. */
    private static String outdatedReason(WarnoPaths paths, ModDescriptor mod, int modGen) {
        if (mod.kind == ModKind.EDITABLE_SOURCE) return null; // updated with WARNO's UpdateMod during the build
        Integer v = mod.modGenVersion;
        if (v == null || v < 0) return "its Config.ini has no ModGen revision; refresh its Workshop download";
        if (v > modGen) return "made for a newer WARNO (ModGen " + v + ", WARNO is at " + modGen + "); let Steam update WARNO";
        if (v < modGen) return ModRebaser.cannotRebase(paths, mod);
        return null;
    }

    /** How a source mod that the build brings up to date is behind, or null when it is current. */
    private static String behindNote(WarnoPaths paths, ModDescriptor mod, int modGen, Cancel token) {
        if (mod.kind == ModKind.EDITABLE_SOURCE)
            return MergePlanner.isOnCurrentBase(paths, mod, token) ? null : "is based on an older WARNO and will be updated";
        Integer v = mod.modGenVersion;
        if (v != null && v >= 0 && v < modGen && ModRebaser.cannotRebase(paths, mod) == null)
            return "was made for ModGen " + v + " and will be brought up to " + modGen;
        return null;
    }

    /** The ModGen revision a combined mod was generated with, from its Config.ini. */
    private static Integer builtModGen(WarnoPaths paths, String outputName) {
        Path config = paths.savedModsRoot.resolve(outputName).resolve("Config.ini");
        if (!Files.isRegularFile(config)) return null;
        int value = IniDocument.load(config).getInt("Properties", "ModGenVersion", -1);
        return value >= 0 ? value : null;
    }
}
