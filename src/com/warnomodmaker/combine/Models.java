package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.NdfLayout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The records the combine engine passes around. */
public final class Models {
    private Models() {
    }

    public enum ModKind { EDITABLE_SOURCE, WORKSHOP_COMPILED }

    public enum DeltaKind { ADDED, MODIFIED, DELETED }

    /** How a file in the combined output was produced. */
    public enum DecisionKind {
        /** Only one selected mod provides the file. */
        SINGLE,
        /** Several mods provide an atomic file; the highest-priority one is used. */
        OVERRIDE,
        /** Compiled database merged value by value from each mod's changes against vanilla. */
        MERGED,
        /** A value several mods changed differently inside a merged database (report only). */
        VALUE_CONFLICT,
        DELETE
    }

    public static final class WarnoPaths {
        public final Path warnoRoot, modsRoot, workshopRoot, savedModsRoot;

        public WarnoPaths(Path warnoRoot, Path modsRoot, Path workshopRoot, Path savedModsRoot) {
            this.warnoRoot = warnoRoot;
            this.modsRoot = modsRoot;
            this.workshopRoot = workshopRoot;
            this.savedModsRoot = savedModsRoot;
        }

        public Path modDataBaseZip() {
            return modsRoot.resolve("ModData").resolve("base.zip");
        }
    }

    public static final class ModDescriptor {
        public final String name;
        public final Path rootPath;
        public final ModKind kind;
        public final String workshopId;
        /** Null when the mod has no readable revision. */
        public final Integer modGenVersion;
        public final int deckFormatVersion;
        public final List<String> tags;
        public final Map<String, String> configKeys;

        public ModDescriptor(String name, Path rootPath, ModKind kind, String workshopId, Integer modGenVersion,
                             int deckFormatVersion, List<String> tags, Map<String, String> configKeys) {
            this.name = name;
            this.rootPath = rootPath;
            this.kind = kind;
            this.workshopId = workshopId;
            this.modGenVersion = modGenVersion;
            this.deckFormatVersion = deckFormatVersion;
            this.tags = tags;
            this.configKeys = configKeys;
        }

        public ModDescriptor withUpdate(Path newRoot, int newModGen, Map<String, String> newConfigKeys) {
            return new ModDescriptor(name, newRoot, kind, workshopId, newModGen, deckFormatVersion, tags, newConfigKeys);
        }

        public String kindLabel() {
            return kind == ModKind.WORKSHOP_COMPILED ? "Workshop " + workshopId : isCombined() ? "Combined mod" : "Editable source";
        }

        private Boolean combined;

        /** True for a combined mod this tool (or WARNO UltiAI MODerator) built, which can itself be combined again. */
        public boolean isCombined() {
            if (combined == null)
                combined = kind == ModKind.EDITABLE_SOURCE && java.nio.file.Files.isRegularFile(rootPath.resolve(".warno-moderator.json"));
            return combined;
        }

        public String modGenLabel() {
            return modGenVersion != null && modGenVersion >= 0 ? "ModGen " + modGenVersion : "ModGen n/a";
        }

        public Path baseZipPath() {
            return rootPath.resolve("base.zip");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class SourceDelta {
        public final String relativePath;
        public final DeltaKind kind;
        public final Path sourcePath;

        public SourceDelta(String relativePath, DeltaKind kind, Path sourcePath) {
            this.relativePath = relativePath;
            this.kind = kind;
            this.sourcePath = sourcePath;
        }
    }

    public static final class MergeDecision {
        public final String relativePath;
        public final DecisionKind kind;
        public final String winner;
        public final String detail;
        public final Path sourcePath;
        public final boolean identical;
        public final boolean overlap;

        public MergeDecision(String relativePath, DecisionKind kind, String winner, String detail, Path sourcePath,
                             boolean identical, boolean overlap) {
            this.relativePath = relativePath;
            this.kind = kind;
            this.winner = winner;
            this.detail = detail;
            this.sourcePath = sourcePath;
            this.identical = identical;
            this.overlap = overlap;
        }

        public MergeDecision(String relativePath, DecisionKind kind, String winner, String detail, Path sourcePath) {
            this(relativePath, kind, winner, detail, sourcePath, false, false);
        }

        public boolean isCollision() {
            return kind == DecisionKind.VALUE_CONFLICT || overlap && kind == DecisionKind.OVERRIDE;
        }

        /** The badge colour key: Identical, Single, Override, Merged, ValueConflict or Delete. */
        public String actionKey() {
            if (identical) return "Identical";
            switch (kind) {
                case SINGLE: return "Single";
                case OVERRIDE: return "Override";
                case MERGED: return "Merged";
                case VALUE_CONFLICT: return "ValueConflict";
                default: return "Delete";
            }
        }

        public String actionLabel() {
            if (identical) return "Identical";
            switch (kind) {
                case SINGLE: return "Single Mod";
                case OVERRIDE: return "Priority Override";
                case MERGED: return "Delta Merged";
                case VALUE_CONFLICT: return "Value Conflict";
                default: return "Deleted";
            }
        }

        public String category() {
            if (kind == DecisionKind.VALUE_CONFLICT) return "Value";
            if (relativePath.regionMatches(true, 0, "Gen\\NDF\\UI\\", 0, 11)) return "UI Database";
            if (NdfLayout.isDatabase(relativePath)) return "NDF Database";
            if (relativePath.equalsIgnoreCase("Gen\\ResourceFile\\Catalog.cat")) return "Asset Catalog";
            if (relativePath.regionMatches(true, 0, "Gen\\", 0, 4)) return "Compiled Asset";
            return "Source File";
        }
    }

    /**
     * @param mods           selected mods in priority order; the first is the primary mod and wins every conflict.
     * @param valueConflicts value-level conflicts inside merged databases, for reporting only.
     */
    public static final class MergePreview {
        public final String outputName;
        public final List<ModDescriptor> mods;
        public final List<MergeDecision> decisions;
        public final List<String> warnings;
        public final boolean provisional;
        public final List<MergeDecision> conflicts;
        public final int mergedCount, overrideCount, singleCount, identicalCount;

        public MergePreview(String outputName, List<ModDescriptor> mods, List<MergeDecision> decisions,
                            List<String> warnings, boolean provisional, List<MergeDecision> valueConflicts) {
            this.outputName = outputName;
            this.mods = mods;
            this.decisions = decisions;
            this.warnings = warnings;
            this.provisional = provisional;
            this.conflicts = valueConflicts == null ? Collections.emptyList() : valueConflicts;
            int merged = 0, override = 0, single = 0, same = 0;
            for (MergeDecision d : decisions) {
                if (d.identical) same++;
                if (d.kind == DecisionKind.MERGED && !d.identical) merged++;
                if (d.kind == DecisionKind.OVERRIDE && !d.identical) override++;
                if (d.kind == DecisionKind.SINGLE) single++;
            }
            mergedCount = merged;
            overrideCount = override;
            singleCount = single;
            identicalCount = same;
        }

        public ModDescriptor primary() {
            return mods.get(0);
        }

        public int valueConflictCount() {
            return conflicts.size();
        }

        public MergePreview withReport(List<String> notes, List<MergeDecision> extraConflicts) {
            List<String> allWarnings = new ArrayList<>(notes);
            allWarnings.addAll(warnings);
            List<MergeDecision> allConflicts = new ArrayList<>(extraConflicts);
            allConflicts.addAll(conflicts);
            return new MergePreview(outputName, mods, decisions, allWarnings, provisional, allConflicts);
        }

        public String priorityLabel() {
            return mods.stream().map(m -> m.name).collect(Collectors.joining(" > "));
        }

        public String summary() {
            return String.format("%,d files · %,d delta merged · %,d value conflicts · %,d priority overrides · %,d single-mod · %,d identical",
                decisions.size(), mergedCount, valueConflictCount(), overrideCount, singleCount, identicalCount);
        }
    }

    public static final class CombineRequest {
        public final WarnoPaths paths;
        public final List<ModDescriptor> mods;
        public final String outputName;
        public final MergePreview preview;

        public CombineRequest(WarnoPaths paths, List<ModDescriptor> mods, String outputName, MergePreview preview) {
            this.paths = paths;
            this.mods = mods;
            this.outputName = outputName;
            this.preview = preview;
        }

        public CombineRequest withMods(List<ModDescriptor> newMods) {
            return new CombineRequest(paths, newMods, outputName, preview);
        }
    }

    public static final class CombineResult {
        public final Path outputSourcePath, outputRuntimePath;
        public final MergePreview plan;
        public final CombinedModState state;

        public CombineResult(Path outputSourcePath, Path outputRuntimePath, MergePreview plan, CombinedModState state) {
            this.outputSourcePath = outputSourcePath;
            this.outputRuntimePath = outputRuntimePath;
            this.plan = plan;
            this.state = state;
        }
    }

    public static final class CombineProgress {
        public final int percent;
        public final String stage;

        public CombineProgress(int percent, String stage) {
            this.percent = percent;
            this.stage = stage;
        }
    }

    public static final class SourceModFingerprint {
        public final String name;
        public final String rootPath;
        public final String fingerprint;

        public SourceModFingerprint(String name, String rootPath, String fingerprint) {
            this.name = name;
            this.rootPath = rootPath;
            this.fingerprint = fingerprint;
        }
    }

    /** The tracking record a combined mod keeps (.warno-moderator.json), shared with WARNO UltiAI MODerator. */
    public static final class CombinedModState {
        public static final int CURRENT_SCHEMA_VERSION = 2;
        public final int schemaVersion;
        public final String outputName;
        /** Source mods in priority order (primary first); null in schema 1 records. */
        public final List<SourceModFingerprint> mods;
        public final String runtimeFingerprint, gameFingerprint;
        public final SourceModFingerprint otherMod, priorityMod;

        public CombinedModState(int schemaVersion, String outputName, List<SourceModFingerprint> mods,
                                String runtimeFingerprint, String gameFingerprint,
                                SourceModFingerprint otherMod, SourceModFingerprint priorityMod) {
            this.schemaVersion = schemaVersion;
            this.outputName = outputName;
            this.mods = mods;
            this.runtimeFingerprint = runtimeFingerprint;
            this.gameFingerprint = gameFingerprint;
            this.otherMod = otherMod;
            this.priorityMod = priorityMod;
        }

        /** Source mods in priority order; schema 1 records stored UltiAI as the priority mod. */
        public List<SourceModFingerprint> sources() {
            if (mods != null) return mods;
            List<SourceModFingerprint> list = new ArrayList<>();
            if (priorityMod != null) list.add(priorityMod);
            if (otherMod != null) list.add(otherMod);
            return list;
        }

        public String sourcesLabel() {
            return sources().stream().map(s -> s.name).collect(Collectors.joining(" > "));
        }

        @Override
        public String toString() {
            return outputName;
        }
    }
}
