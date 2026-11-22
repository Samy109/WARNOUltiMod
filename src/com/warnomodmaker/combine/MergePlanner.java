package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.DecisionKind;
import com.warnomodmaker.combine.Models.DeltaKind;
import com.warnomodmaker.combine.Models.MergeDecision;
import com.warnomodmaker.combine.Models.MergePreview;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.ModKind;
import com.warnomodmaker.combine.Models.SourceDelta;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.IgnoreCase;
import com.warnomodmaker.combine.ndf.NdfContainer;
import com.warnomodmaker.combine.ndf.NdfDatabase;
import com.warnomodmaker.combine.ndf.NdfFormatException;
import com.warnomodmaker.combine.ndf.NdfLayout;
import com.warnomodmaker.combine.ndf.NdfMerger;
import com.warnomodmaker.combine.ndf.NdfPoolOrder;
import com.warnomodmaker.combine.ndf.NdfWriter;
import com.warnomodmaker.combine.ndf.VanillaDatabases;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Plans a combined mod: for every file, which mod's copy is used or how the copies are merged. */
public final class MergePlanner {
    private static final Set<String> RESERVED_WINDOWS_NAMES = IgnoreCase.set();
    private static final String[] FILE_MANIFESTS = {"Gen\\DeclaredFiles.txt", "Gen\\UsedFiles.txt"};
    private static final String[] RUNTIME_FOLDERS = {"Gen", "GameData", "CommonData", "DatasMap", "DecorsSets", "Maps", "Scenarios"};
    private static final byte[] POOL_CLASS = "TPoolModel".getBytes(StandardCharsets.US_ASCII);

    static {
        RESERVED_WINDOWS_NAMES.addAll(Arrays.asList("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6",
            "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"));
    }

    /** A compiled payload taking part in the merge: a Workshop mod, or WARNO's generation of editable mods. */
    public static final class CompiledInput {
        public final String name;
        public final Path root;
        public final Map<String, String> configKeys;

        public CompiledInput(String name, Path root, Map<String, String> configKeys) {
            this.name = name;
            this.root = root;
            this.configKeys = configKeys;
        }
    }

    /** A file of a mod: its mod-relative path (backslashes) and where it is. */
    static final class ModFile {
        final String relativePath;
        final Path fullPath;

        ModFile(String relativePath, Path fullPath) {
            this.relativePath = relativePath;
            this.fullPath = fullPath;
        }
    }

    private final SourceDeltaAnalyzer deltaAnalyzer;

    public MergePlanner(SourceDeltaAnalyzer deltaAnalyzer) {
        this.deltaAnalyzer = deltaAnalyzer;
    }

    /** @param mods selected mods in priority order; the first is the primary mod. */
    public MergePreview createPreview(WarnoPaths paths, List<ModDescriptor> mods, String outputName, boolean allowExistingOutput, Cancel token) {
        validateOutputName(outputName);
        validateInputs(paths, mods, token);
        for (Path root : new Path[]{paths.modsRoot, paths.savedModsRoot}) {
            Path output = root.resolve(outputName);
            if (mods.stream().anyMatch(m -> FileOps.samePath(output, m.rootPath))) throw new CombineException("The output must not replace an input mod.");
            if (!allowExistingOutput && Files.exists(output)) throw new CombineException("An output named '" + outputName + "' already exists.");
        }
        int installed = installedModGen(paths);
        List<String> notes = new ArrayList<>();
        for (ModDescriptor mod : mods) {
            if (mod.kind == ModKind.EDITABLE_SOURCE && !isOnCurrentBase(paths, mod, token))
                notes.add(mod.name + " is based on an older WARNO; the build first updates it to the current one with WARNO's UpdateMod, keeping the mod's changes where both changed the same lines.");
        }
        if (mods.stream().allMatch(m -> m.kind == ModKind.EDITABLE_SOURCE))
            return new MergePreview(outputName, mods, planSourceMerge(mods, token), notes, !notes.isEmpty(), null);

        List<ModRebaser.RebasedMod> rebased = new ArrayList<>();
        List<ModDescriptor> current = bringUpToDate(paths, mods, installed, null, token, rebased);
        notes.addAll(rebaseNotes(rebased, installed));
        // Editable mods are previewed from their own last generation; the build regenerates them together.
        List<CompiledInput> inputs = new ArrayList<>();
        for (ModDescriptor m : current)
            inputs.add(new CompiledInput(m.name, m.kind == ModKind.WORKSHOP_COMPILED ? m.rootPath : paths.savedModsRoot.resolve(m.name), m.configKeys));
        VanillaDatabases vanilla = VanillaDatabases.open(paths.warnoRoot, installed);
        boolean provisional = mods.stream().anyMatch(m -> m.kind == ModKind.EDITABLE_SOURCE) || rebased.stream().anyMatch(r -> !r.ownNames.isEmpty());
        MergePreview plan = createCompiledPlan(outputName, mods, inputs, vanilla, null, token, SourceBaseline.open(paths.modDataBaseZip()),
            provisional, null, Collections.emptyList());
        return withRebaseReport(plan, rebased, notes);
    }

    /** The installed WARNO's ModGen revision (see {@link VanillaDatabases#installedModGen}). */
    static int installedModGen(WarnoPaths paths) {
        try {
            return VanillaDatabases.installedModGen(paths.warnoRoot);
        } catch (NdfFormatException e) {
            throw new CombineException(e.getMessage(), e);
        }
    }

    static boolean isOnCurrentBase(WarnoPaths paths, ModDescriptor mod, Cancel token) {
        return Files.isRegularFile(mod.baseZipPath())
            && FileOps.sha256Cached(mod.baseZipPath(), token).equals(FileOps.sha256Cached(paths.modDataBaseZip(), token));
    }

    /** The mods as they go into a build: Workshop mods made for an older WARNO are brought up to date (see {@link ModRebaser}). */
    static List<ModDescriptor> bringUpToDate(WarnoPaths paths, List<ModDescriptor> mods, int installed, Consumer<String> log,
                                             Cancel token, List<ModRebaser.RebasedMod> rebased) {
        ModRebaser rebaser = new ModRebaser();
        List<ModDescriptor> current = new ArrayList<>();
        for (ModDescriptor mod : mods) {
            if (!ModRebaser.isOutdated(mod, installed)) {
                current.add(mod);
                continue;
            }
            ModRebaser.RebasedMod result = rebaser.rebase(paths, mod, installed, log, token);
            rebased.add(result);
            current.add(result.mod);
        }
        return current;
    }

    static List<String> rebaseNotes(List<ModRebaser.RebasedMod> rebased, int installed) {
        List<String> notes = new ArrayList<>();
        for (ModRebaser.RebasedMod r : rebased) {
            String note = r.original.name + " was made for ModGen " + r.original.modGenVersion + " and is brought up to ModGen " + installed
                + ": its changes are applied to the current game's databases"
                + (!r.conflicts.isEmpty() ? String.format(", keeping its value in %,d place(s) WARNO's updates also changed.", r.conflicts.size()) : ".")
                + (!r.ownNames.isEmpty() ? " WARNO rebuilds its own " + String.join(", ", r.ownNames) + " dictionary and resource pack during the build." : "");
            notes.add(note);
            for (String n : r.notes) notes.add(r.original.name + ": " + n);
        }
        return notes;
    }

    /** The plan with the updated mods' notes and the values they kept over WARNO's updates. */
    static MergePreview withRebaseReport(MergePreview plan, List<ModRebaser.RebasedMod> rebased, List<String> notes) {
        if (notes.isEmpty() && rebased.stream().allMatch(r -> r.conflicts.isEmpty())) return plan;
        List<MergeDecision> conflicts = new ArrayList<>();
        for (ModRebaser.RebasedMod r : rebased) {
            for (NdfMerger.Conflict c : r.conflicts) {
                conflicts.add(new MergeDecision(c.path, DecisionKind.VALUE_CONFLICT, r.original.name,
                    r.original.name + "'s value kept over WARNO's update (" + String.join(", ", c.overridden) + "): " + c.winnerValue, null));
            }
        }
        return plan.withReport(notes, conflicts);
    }

    static void validateInputs(WarnoPaths paths, List<ModDescriptor> mods, Cancel token) {
        if (mods.size() < 2) throw new CombineException("Add at least two mods to combine.");
        for (int i = 0; i < mods.size(); i++) {
            for (int j = i + 1; j < mods.size(); j++) {
                if (FileOps.samePath(mods.get(i).rootPath, mods.get(j).rootPath)) throw new CombineException(mods.get(i).name + " is selected twice.");
                if (mods.get(i).name.equalsIgnoreCase(mods.get(j).name))
                    throw new CombineException("Two mods named " + mods.get(i).name + " are selected (" + mods.get(i).kindLabel() + " and "
                        + mods.get(j).kindLabel() + "). They are versions of the same mod; keep one.");
            }
        }
        Integer installed = null;
        for (ModDescriptor mod : mods) {
            token.check();
            if (!Files.isDirectory(mod.rootPath)) throw new CombineException("Source mod folder is missing: " + mod.rootPath);
            if (mod.kind != ModKind.WORKSHOP_COMPILED) continue; // an editable mod on an older base is updated during the build
            if (mod.modGenVersion == null || mod.modGenVersion < 0)
                throw new CombineException(mod.name + " has no readable ModGenVersion. Refresh its Workshop subscription before combining it.");
            if (installed == null) installed = installedModGen(paths);
            if (mod.modGenVersion > installed)
                throw new CombineException(mod.name + " was made for a newer WARNO (ModGen " + mod.modGenVersion + ") than the one installed (ModGen " + installed + "). Let Steam update WARNO.");
            if (mod.modGenVersion < installed) {
                String reason = ModRebaser.cannotRebase(paths, mod);
                if (reason != null) throw new CombineException(mod.name + " can't be combined: " + reason + ".");
            }
        }
    }

    /** File-level merge of editable sources: for each changed file the highest-priority mod wins. */
    List<MergeDecision> planSourceMerge(List<ModDescriptor> mods, Cancel token) {
        List<ModDescriptor> editable = new ArrayList<>();
        List<TreeMap<String, SourceDelta>> changes = new ArrayList<>();
        TreeSet<String> paths = IgnoreCase.set();
        for (ModDescriptor m : mods) {
            if (m.kind != ModKind.EDITABLE_SOURCE) continue;
            TreeMap<String, SourceDelta> map = IgnoreCase.map();
            for (SourceDelta d : deltaAnalyzer.analyze(m, token)) map.put(d.relativePath, d);
            editable.add(m);
            changes.add(map);
            paths.addAll(map.keySet());
        }
        List<MergeDecision> decisions = new ArrayList<>();
        for (String path : paths) {
            token.check();
            List<ModDescriptor> providers = new ArrayList<>();
            List<SourceDelta> provided = new ArrayList<>();
            for (int i = 0; i < editable.size(); i++) {
                SourceDelta d = changes.get(i).get(path);
                if (d != null) {
                    providers.add(editable.get(i));
                    provided.add(d);
                }
            }
            ModDescriptor winner = providers.get(0);
            SourceDelta change = provided.get(0);
            boolean identical = providers.size() > 1;
            for (int i = 1; identical && i < provided.size(); i++) {
                SourceDelta p = provided.get(i);
                identical = p.kind == change.kind && (change.kind == DeltaKind.DELETED || FileOps.sameContent(p.sourcePath, change.sourcePath, token));
            }
            List<String> others = providers.subList(1, providers.size()).stream().map(m -> m.name).collect(Collectors.toList());
            String detail = identical ? "All mods make the exact same source change (no conflict)."
                : !others.isEmpty() ? "Several mods modify this source file; " + winner.name + " has priority over " + String.join(", ", others) + "."
                : change.kind == DeltaKind.DELETED ? "Deleted relative to vanilla base.zip by " + winner.name + "."
                : (change.kind == DeltaKind.ADDED ? "Added" : "Modified") + " source file from " + winner.name + ".";
            DecisionKind kind = change.kind == DeltaKind.DELETED ? DecisionKind.DELETE : !others.isEmpty() ? DecisionKind.OVERRIDE : DecisionKind.SINGLE;
            decisions.add(new MergeDecision(path, kind, winner.name, detail, change.sourcePath, identical, !others.isEmpty()));
        }
        return decisions;
    }

    /**
     * @param inputs         compiled payloads in priority order (primary first).
     * @param mergeDirectory where merged databases are written; null plans without writing (preview).
     * @param generated      WARNO's own generation of the output, when no input already represents it. It takes part
     *                       with the lowest priority, so the output keeps the files every generated mod has under its own name.
     */
    static MergePreview createCompiledPlan(String outputName, List<ModDescriptor> mods, List<CompiledInput> inputs, VanillaDatabases vanilla,
                                           Path mergeDirectory, Cancel token, SourceBaseline sourceBaseline, boolean provisional,
                                           CompiledInput generated, List<String> generatedNames) {
        List<CompiledInput> contributors = new ArrayList<>(inputs);
        if (generated != null) contributors.add(generated);
        // WARNO's generation of the output owns the files under its name, and the dictionaries and resource packs it
        // rebuilt for mods brought up to date.
        List<String> owned = new ArrayList<>();
        owned.add(outputName);
        owned.addAll(generatedNames);
        List<List<ModFile>> listings = new ArrayList<>();
        for (CompiledInput input : contributors) {
            List<ModFile> listing = new ArrayList<>();
            for (ModFile f : enumerateRuntimeFiles(input.root))
                if (input != generated || owned.stream().anyMatch(name -> isOutputOwned(f.relativePath, name))) listing.add(f);
            listings.add(listing);
        }
        boolean transactionLayout = vanilla.transactionLayout()
            || listings.stream().anyMatch(l -> l.stream().anyMatch(x -> NdfLayout.isTransactionDatabase(x.relativePath)));
        List<String> layoutNotes = new ArrayList<>();
        List<TreeMap<String, Path>> files = new ArrayList<>();
        for (int i = 0; i < contributors.size(); i++) files.add(mapToLayout(contributors.get(i).name, listings.get(i), transactionLayout, layoutNotes));
        // For a mod brought up to date, WARNO's generator only fills what the mod doesn't ship itself: its rebuilt
        // dictionary and resource pack, and placeholder packs only where the mod has none.
        if (generated != null) {
            TreeMap<String, Path> mine = files.get(files.size() - 1);
            for (String path : new ArrayList<>(mine.keySet())) {
                if (isOutputOwned(path, outputName)) continue;
                for (int i = 0; i < files.size() - 1; i++) {
                    if (files.get(i).containsKey(path)) {
                        mine.remove(path);
                        break;
                    }
                }
            }
        }
        // A database's .dbg is part of it: it is planned with the database, never on its own.
        List<TreeMap<String, Path>> debugInfo = new ArrayList<>();
        for (TreeMap<String, Path> f : files) debugInfo.add(splitDebugInfo(f));
        for (int i = 0; i < inputs.size(); i++) {
            if (files.get(i).isEmpty() && !provisional) throw new CombineException(inputs.get(i).name + " has no runtime payload.");
        }

        TreeSet<String> pathSet = IgnoreCase.set();
        for (TreeMap<String, Path> f : files) pathSet.addAll(f.keySet());
        List<String> paths = new ArrayList<>(pathSet);
        Map<String, Integer> payloadSizes = new HashMap<>();
        for (int i = 0; i < contributors.size(); i++) payloadSizes.put(contributors.get(i).name, files.get(i).size());
        List<List<MergeDecision>> conflicts = new ArrayList<>(Collections.nCopies(paths.size(), null));

        List<MergeDecision> decisions = Parallel.map(paths.size(), Parallel.half(), index -> {
            token.check();
            String path = paths.get(index);
            List<CompiledInput> providerInputs = new ArrayList<>();
            List<Path> providerFiles = new ArrayList<>();
            List<Path> debug = new ArrayList<>();
            for (int i = 0; i < contributors.size(); i++) {
                Path file = files.get(i).get(path);
                if (file == null) continue;
                providerInputs.add(contributors.get(i));
                providerFiles.add(file);
                debug.add(debugInfo.get(i).get(path));
            }
            MergeDecision decision = decide(path, providerInputs, providerFiles, debug, payloadSizes, vanilla, sourceBaseline,
                mergeDirectory, conflicts, index, token);
            // A database taken whole from one mod ships its pools in WARNO's order too (see NdfPoolOrder).
            if (NdfLayout.isDatabase(path) && decision.kind != DecisionKind.MERGED && decision.sourcePath != null) {
                Path source = null;
                for (int i = 0; i < providerFiles.size(); i++) {
                    if (FileOps.samePath(providerFiles.get(i), decision.sourcePath)) {
                        source = debug.get(i);
                        break;
                    }
                }
                MergeDecision settled = settlePools(path, decision, source, mergeDirectory);
                if (settled != null) decision = settled;
            }
            return decision;
        });

        List<TreeMap<String, Path>> tags = new ArrayList<>();
        for (CompiledInput input : contributors) {
            TreeMap<String, Path> map = IgnoreCase.map();
            for (ModFile f : enumerateTagFiles(input.root))
                if (input != generated || owned.stream().anyMatch(name -> isOutputOwned(f.relativePath, name))) map.put(f.relativePath, f.fullPath);
            tags.add(map);
        }
        List<MergeDecision> ordered = new ArrayList<>();
        for (MergeDecision d : decisions) {
            ordered.add(d);
            ordered.addAll(companions(d, contributors, files, tags, debugInfo, mergeDirectory));
        }
        List<MergeDecision> valueConflicts = new ArrayList<>();
        for (List<MergeDecision> c : conflicts) if (c != null) valueConflicts.addAll(c);
        List<String> notes = new ArrayList<>();
        if (provisional)
            notes.add("Provisional preview: editable SDK input will be compiled by WARNO during the build; the final report will reflect the exact compiled files.");
        long merged = ordered.stream().filter(d -> d.kind == DecisionKind.MERGED && !d.relativePath.toLowerCase().endsWith(".txt")).count();
        if (merged > 0)
            notes.add(String.format("%,d database(s) were changed by several mods and are delta merged against vanilla; %,d value conflict(s) resolved by priority (%s).",
                merged, valueConflicts.size(), inputs.stream().map(i -> i.name).collect(Collectors.joining(" > "))));
        notes.addAll(layoutNotes);
        return new MergePreview(outputName, mods, ordered, notes, provisional, valueConflicts);
    }

    /**
     * Puts one mod's databases in the installed build's layout. A mod that ships .txn databases was rebuilt after
     * WARNO's November 2026 format change, and any .ndfbin it still carries is a stale leftover WARNO no longer reads,
     * so those are left out. A mod that ships only .ndfbin databases was published before it: it has none of the .tag
     * files WARNO now needs to load a database, so a combined mod using it can't load, and it is refused.
     */
    private static TreeMap<String, Path> mapToLayout(String inputName, List<ModFile> listing, boolean transactionLayout, List<String> notes) {
        TreeMap<String, Path> map = IgnoreCase.map();
        boolean rebuilt = listing.stream().anyMatch(x -> NdfLayout.isTransactionDatabase(x.relativePath));
        if (transactionLayout && !rebuilt && listing.stream().anyMatch(x -> NdfLayout.isLegacyDatabase(x.relativePath)))
            throw new CombineException(inputName + " was made before WARNO's November 2026 database format change. It only has old .ndfbin databases, without the .tag files WARNO now needs to load them, so a combined mod with it fails with \"No writable component found\". Remove "
                + inputName + ", or wait for its author to update it for the current WARNO version.");
        int stale = 0;
        for (ModFile f : listing) {
            if (!transactionLayout || !NdfLayout.isLegacyDatabase(f.relativePath)) map.put(f.relativePath, f.fullPath);
            else stale++;
        }
        if (stale > 0)
            notes.add(String.format("%s still carries %,d .ndfbin database(s) from before WARNO's format change; WARNO no longer reads them, so they were left out.", inputName, stale));
        return map;
    }

    private static boolean isManifest(String path) {
        for (String m : FILE_MANIFESTS) if (m.equalsIgnoreCase(path)) return true;
        return false;
    }

    // A generated mod's packs, dictionaries and their database entries sit under its name; the asset catalog and file
    // lists register them, so those are merged with the inputs' rather than replaced.
    private static boolean isOutputOwned(String path, String outputName) {
        if (isManifest(path) || path.equalsIgnoreCase("Gen\\ResourceFile\\Catalog.cat")) return true;
        for (String segment : path.split("\\\\")) {
            if (segment.equalsIgnoreCase(outputName) || segment.equalsIgnoreCase(outputName + ".ndfbin") || segment.equalsIgnoreCase(outputName + ".txn"))
                return true;
        }
        return false;
    }

    private static boolean isGeneratorByproduct(String path) {
        return path.regionMatches(true, 0, "Gen\\Intermediate\\", 0, 17)
            || path.equalsIgnoreCase("Gen\\GenerationReport.txt") || path.equalsIgnoreCase("Gen\\DecorSetAssets.ndf");
    }

    private static MergeDecision decide(String path, List<CompiledInput> inputs, List<Path> files, List<Path> debugInfo,
                                        Map<String, Integer> payloadSizes, VanillaDatabases vanilla, SourceBaseline sourceBaseline,
                                        Path mergeDirectory, List<List<MergeDecision>> conflicts, int conflictSlot, Cancel token) {
        CompiledInput primary = inputs.get(0);
        Path primaryFile = files.get(0);
        if (inputs.size() == 1)
            return new MergeDecision(path, DecisionKind.SINGLE, primary.name, "Only " + primary.name + " provides this file.", primaryFile);
        boolean allSame = true;
        for (int i = 1; allSame && i < files.size(); i++) allSame = FileOps.sameContent(files.get(i), primaryFile, token);
        if (allSame)
            return new MergeDecision(path, DecisionKind.SINGLE, primary.name, "Identical in every mod that provides it (no conflict).", primaryFile, true, true);

        String others = inputs.subList(1, inputs.size()).stream().map(i -> i.name).collect(Collectors.joining(", "));
        MergeDecision overrideDecision = new MergeDecision(path, DecisionKind.OVERRIDE, primary.name,
            "Atomic file: " + primary.name + " has priority over " + others + ".", primaryFile, false, true);
        if (isManifest(path)) return mergeManifest(path, inputs, files, mergeDirectory);
        if (isGeneratorByproduct(path)) {
            // Build caches and reports describe a mod's own asset generation; keep them with the largest payload.
            int largest = 0;
            for (int i = 1; i < inputs.size(); i++) if (payloadSizes.get(inputs.get(i).name) > payloadSizes.get(inputs.get(largest).name)) largest = i;
            return new MergeDecision(path, DecisionKind.OVERRIDE, inputs.get(largest).name,
                "Generator output taken from " + inputs.get(largest).name + ", the mod with the largest compiled payload.", files.get(largest), false, true);
        }
        if (!isNdfBinary(primaryFile)) {
            // A mod that ships vanilla's own source file has not changed it, so it must neither win nor conflict.
            List<Integer> changed = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) if (!sourceBaseline.isVanilla(path, files.get(i), token)) changed.add(i);
            if (changed.isEmpty() || changed.size() == files.size()) return overrideDecision;
            CompiledInput winner = inputs.get(changed.get(0));
            Path winnerFile = files.get(changed.get(0));
            if (changed.size() == 1)
                return new MergeDecision(path, DecisionKind.SINGLE, winner.name,
                    "Only " + winner.name + " changes this file from vanilla; the others ship it unchanged.", winnerFile, false, true);
            String losers = changed.subList(1, changed.size()).stream().map(i -> inputs.get(i).name).collect(Collectors.joining(", "));
            return new MergeDecision(path, DecisionKind.OVERRIDE, winner.name, "Atomic file: " + winner.name + " has priority over " + losers + ".", winnerFile, false, true);
        }

        // Several mods ship this database with different content: it is delta merged against vanilla's, value by value.
        if (!vanilla.available())
            throw new CombineException(others + " and " + primary.name + " both change " + path + ", and merging them needs WARNO's vanilla databases, which were not found ("
                + vanilla.searched() + "). Verify WARNO's files in Steam.");
        try {
            byte[] baseBytes = vanilla.tryRead(path);
            // A database vanilla doesn't have (an asset catalog, or one only mods add) is merged without a baseline:
            // every mod's content is an addition, and differing values are resolved by priority and reported.
            NdfDatabase baseDb = baseBytes == null ? null : NdfDatabase.load(baseBytes);
            List<NdfMerger.Variant> variants = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++)
                variants.add(new NdfMerger.Variant(inputs.get(i).name, NdfDatabase.load(FileOps.read(files.get(i)),
                    debugInfo.get(i) != null ? FileOps.read(debugInfo.get(i)) : null)));
            NdfMerger.Result result = NdfMerger.merge(baseDb, variants, token);
            if (result.changedBy.isEmpty())
                return new MergeDecision(path, DecisionKind.SINGLE, primary.name, "No mod changes this database from vanilla.", primaryFile, true, true);
            if (result.changedBy.size() == 1 && result.conflicts.isEmpty() && baseDb != null) {
                int only = 0;
                for (int i = 0; i < inputs.size(); i++) if (inputs.get(i).name.equals(result.changedBy.get(0))) { only = i; break; }
                return new MergeDecision(path, DecisionKind.SINGLE, inputs.get(only).name,
                    "Only " + inputs.get(only).name + " changes this database; the others ship it unchanged from vanilla.", files.get(only), false, true);
            }
            Path output = null;
            if (mergeDirectory != null) output = writeDatabase(result.database, mergeDirectory, path);
            List<MergeDecision> found = new ArrayList<>();
            for (NdfMerger.Conflict c : result.conflicts) {
                found.add(new MergeDecision(path + " › " + c.path, DecisionKind.VALUE_CONFLICT, c.winner, !c.overridden.isEmpty()
                    ? c.winner + " has priority over " + String.join(", ", c.overridden) + ": " + c.winnerValue
                    : c.winner + ": " + c.winnerValue, null));
            }
            conflicts.set(conflictSlot, found);
            String detail = String.format("Delta merged: %,d object(s) changed by %s", result.changedObjects, String.join(", ", result.changedBy))
                + (!result.conflicts.isEmpty() ? String.format("; %,d value conflict(s) resolved by priority.", result.conflicts.size()) : "; no conflicts.");
            return new MergeDecision(path, DecisionKind.MERGED, String.join(" + ", result.changedBy), detail, output, false, true);
        } catch (NdfFormatException e) {
            throw new CombineException(path + " is changed by " + primary.name + " and " + others + " and can't be merged: " + e.getMessage(), e);
        }
    }

    /** Writes a database the app compiled, with a .dbg written to match its objects. */
    private static Path writeDatabase(NdfDatabase database, Path mergeDirectory, String path) {
        Path output = FileOps.safeCombine(mergeDirectory, path);
        NdfWriter.Output written = database.saveWithDebugInfo();
        FileOps.write(output, written.database);
        if (NdfLayout.isTransactionDatabase(path)) FileOps.write(FileOps.safeCombine(mergeDirectory, NdfLayout.debugInfoPath(path)), written.debugInfo);
        return output;
    }

    /**
     * A database one mod ships whole, rewritten when one of its pools breaks WARNO's pool rule (see
     * {@link NdfPoolOrder}), with a .dbg written to match; null when every pool is already in order. Only a database
     * that has a TPoolModel at all is decompiled.
     */
    private static MergeDecision settlePools(String path, MergeDecision decision, Path debugInfo, Path mergeDirectory) {
        byte[] bytes = FileOps.read(decision.sourcePath);
        if (!NdfContainer.isNdfBinary(bytes)) return null;
        if (NdfContainer.indexOf(NdfContainer.decompress(bytes).data, POOL_CLASS) < 0) return null;
        NdfDatabase database = NdfDatabase.load(bytes, debugInfo == null ? null : FileOps.read(debugInfo));
        NdfPoolOrder.Result settled = NdfPoolOrder.settle(database);
        if (settled.reordered.isEmpty()) return null;
        Path output = mergeDirectory == null ? null : writeDatabase(settled.database, mergeDirectory, path);
        List<String> shown = settled.reordered.subList(0, Math.min(5, settled.reordered.size()));
        // Written by the app like a merged database, so it gets the .dbg written with it.
        return new MergeDecision(path, DecisionKind.MERGED, decision.winner,
            String.format("%s %,d pool(s) reordered so Required elements come first, as WARNO requires: %s%s.", decision.detail,
                settled.reordered.size(), String.join(", ", shown), settled.reordered.size() > 5 ? ", ..." : ""),
            output, false, decision.overlap);
    }

    /** Every database's .dbg, keyed by the database, taken out of the file map. */
    private static TreeMap<String, Path> splitDebugInfo(TreeMap<String, Path> files) {
        TreeMap<String, Path> debug = IgnoreCase.map();
        for (String database : new ArrayList<>(files.keySet())) {
            if (!NdfLayout.isTransactionDatabase(database)) continue;
            Path file = files.remove(NdfLayout.debugInfoPath(database));
            if (file != null) debug.put(database, file);
        }
        return debug;
    }

    /**
     * The companions WARNO generates with a file and needs next to it: the .tag of every generated file (WARNO won't
     * mount a database without it: "No writable component found") and the .dbg of every database. They come from the
     * mod whose copy of the file is used; a delta-merged database gets the .dbg written with it, matching its objects,
     * and the .tag of the highest-priority mod that changed it.
     */
    private static List<MergeDecision> companions(MergeDecision decision, List<CompiledInput> contributors, List<TreeMap<String, Path>> files,
                                                  List<TreeMap<String, Path>> tags, List<TreeMap<String, Path>> debugInfo, Path mergeDirectory) {
        List<MergeDecision> result = new ArrayList<>();
        boolean merged = decision.kind == DecisionKind.MERGED;
        if (!merged && decision.sourcePath == null) return result;
        List<String> winners = Arrays.asList(decision.winner.split(" \\+ "));
        int source = -1;
        for (int i = 0; i < contributors.size(); i++) {
            Path file = files.get(i).get(decision.relativePath);
            if (file == null) continue;
            if (merged ? winners.contains(contributors.get(i).name) : FileOps.samePath(file, decision.sourcePath)) {
                source = i;
                break;
            }
        }
        if (source < 0) throw new IllegalStateException("No contributor provides " + decision.relativePath + ".");
        String name = contributors.get(source).name;
        String tagPath = decision.relativePath + ".tag";
        boolean database = NdfLayout.isTransactionDatabase(decision.relativePath);
        // A database's tag may come from another mod's copy (it describes the build, and is re-stamped with the
        // combined sources), preferring the mods that changed it; any other generated file keeps only its own tag.
        int tagSource = -1;
        if (!database) {
            if (tags.get(source).containsKey(tagPath)) tagSource = source;
        } else {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < contributors.size(); i++) order.add(i);
            int chosen = source;
            order.sort((a, b) -> {
                int byUsed = Boolean.compare(b == chosen, a == chosen);
                return byUsed != 0 ? byUsed : Boolean.compare(winners.contains(contributors.get(b).name), winners.contains(contributors.get(a).name));
            });
            for (int i : order) if (tags.get(i).containsKey(tagPath)) { tagSource = i; break; }
        }
        if (tagSource >= 0) {
            String tagOwner = contributors.get(tagSource).name;
            result.add(new MergeDecision(tagPath, DecisionKind.SINGLE, tagOwner, tagSource == source
                ? "Generation tag of " + decision.relativePath + ", from " + name + "."
                : "Generation tag of " + decision.relativePath + ", from " + tagOwner + " (" + name + " has none).",
                tags.get(tagSource).get(tagPath), false, decision.overlap));
        } else if (database) {
            throw new CombineException("None of the selected mods has " + tagPath + ", the generation tag WARNO needs to load " + decision.relativePath
                + "; without it the combined mod fails with \"No writable component found\". Refresh " + name + "'s Workshop download, or remove it.");
        }
        if (!database) return result;
        String debugPath = NdfLayout.debugInfoPath(decision.relativePath);
        if (merged) {
            result.add(new MergeDecision(debugPath, DecisionKind.MERGED, decision.winner,
                "Debug info written with the merged " + decision.relativePath + ", one line per object.",
                mergeDirectory == null ? null : FileOps.safeCombine(mergeDirectory, debugPath), false, true));
        } else {
            Path debug = debugInfo.get(source).get(decision.relativePath);
            if (debug != null)
                result.add(new MergeDecision(debugPath, DecisionKind.SINGLE, name, "Debug info of " + decision.relativePath + ", from " + name + ".", debug, false, decision.overlap));
        }
        return result;
    }

    /** Generated file lists (DeclaredFiles.txt, UsedFiles.txt) become the union of every mod's lines. */
    private static MergeDecision mergeManifest(String path, List<CompiledInput> inputs, List<Path> files, Path mergeDirectory) {
        List<String> lines = new ArrayList<>();
        Set<String> seen = IgnoreCase.set();
        for (Path file : files) {
            // Latin-1 keeps every byte as it is, whatever encoding the generator wrote the paths in.
            byte[] bytes = FileOps.read(file);
            int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
            for (String line : new String(bytes, start, bytes.length - start, StandardCharsets.ISO_8859_1).split("\r\n|\n|\r"))
                if (!line.isEmpty() && seen.add(line)) lines.add(line);
        }
        Path output = null;
        if (mergeDirectory != null) {
            output = FileOps.safeCombine(mergeDirectory, path);
            FileOps.write(output, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        return new MergeDecision(path, DecisionKind.MERGED, inputs.stream().map(i -> i.name).collect(Collectors.joining(" + ")),
            String.format("Union of %,d entries listed by every mod.", lines.size()), output, false, true);
    }

    private static boolean isNdfBinary(Path path) {
        byte[] header = new byte[0x2C];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.readNBytes(header, 0, header.length);
            return NdfContainer.isNdfBinary(header, read);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void validateOutputName(String outputName) {
        if (outputName == null || outputName.trim().isEmpty()) throw new CombineException("Enter an output name.");
        if (!outputName.equals(outputName.trim()) || outputName.equals(".") || outputName.equals("..") || outputName.endsWith(".")
            || FileOps.hasInvalidFileNameChars(outputName) || outputName.contains("%") || outputName.length() > 120
            || RESERVED_WINDOWS_NAMES.contains(outputName.split("\\.", -1)[0]))
            throw new CombineException("The output name is not a valid Windows folder name.");
    }

    public static boolean isValidOutputName(String outputName) {
        try {
            validateOutputName(outputName);
            return true;
        } catch (CombineException e) {
            return false;
        }
    }

    static List<ModFile> enumerateRuntimeFiles(Path root) {
        List<ModFile> result = new ArrayList<>();
        for (String child : RUNTIME_FOLDERS) {
            for (Path file : FileOps.files(root.resolve(child))) {
                if (file.getFileName().toString().toLowerCase().endsWith(".tag")) continue;
                result.add(new ModFile(FileOps.relative(root, file), file));
            }
        }
        return result;
    }

    /** The .tag companions of a mod's generated files; they are planned with their file (see {@link #companions}). */
    static List<ModFile> enumerateTagFiles(Path root) {
        List<ModFile> result = new ArrayList<>();
        for (Path file : FileOps.files(root.resolve("Gen"))) {
            if (file.getFileName().toString().toLowerCase().endsWith(".tag")) result.add(new ModFile(FileOps.relative(root, file), file));
        }
        return result;
    }
}
