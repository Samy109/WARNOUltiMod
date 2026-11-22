package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.ModKind;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.Hex;
import com.warnomodmaker.combine.ndf.IgnoreCase;
import com.warnomodmaker.combine.ndf.NdfDatabase;
import com.warnomodmaker.combine.ndf.NdfLayout;
import com.warnomodmaker.combine.ndf.NdfMerger;
import com.warnomodmaker.combine.ndf.NdfPoolOrder;
import com.warnomodmaker.combine.ndf.NdfRewrite;
import com.warnomodmaker.combine.ndf.NdfValue;
import com.warnomodmaker.combine.ndf.NdfWriter;
import com.warnomodmaker.combine.ndf.VanillaDatabases;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Brings a Workshop mod made for an older WARNO up to the installed one. Its compiled databases are what it changed
 * against that revision's vanilla databases, which WARNO keeps in Data\PC; each one is rebased by a three-way merge
 * from the old vanilla database to the current one, keeping the mod's value wherever both changed the same value. The
 * mod's own dictionaries and resource packs are rebuilt by WARNO's generator from their sources (the mod's own, or the
 * SDK's templates when it shipped none). Its added source files and assets carry over; its copies of vanilla source
 * files are left out, since they are the old revision's. The result is cached per mod and revision.
 */
public final class ModRebaser {
    private static final String CACHE_FOLDER = ".warno-moderator-updated";
    private static final String FORMAT_VERSION = "6";
    private static final String[] OWN_FOLDERS = {"Localisation", "ResourcePacks"};
    private static final String[][] DICTIONARIES = {
        {"dico_units", "UNITS.csv"}, {"dico_companies", "COMPANIES.csv"}, {"dico_platoons", "PLATOONS.csv"},
        {"dico_interface_ingame", "INTERFACE_INGAME.csv"}, {"dico_interface_outgame", "INTERFACE_OUTGAME.csv"}
    };

    /** A Workshop mod brought up to the installed WARNO, and what that changed. */
    public static final class RebasedMod {
        public final ModDescriptor original;
        /** The updated copy, in the current format and revision; combine it like any current mod. */
        public final ModDescriptor mod;
        /** Names of the mod's own dictionaries and resource packs, which WARNO's generator rebuilds. */
        public final List<String> ownNames;
        /** Values both the mod and WARNO's update changed; the mod's value is kept. */
        public final List<NdfMerger.Conflict> conflicts;
        public final List<String> notes;

        RebasedMod(ModDescriptor original, ModDescriptor mod, List<String> ownNames, List<NdfMerger.Conflict> conflicts, List<String> notes) {
            this.original = original;
            this.mod = mod;
            this.ownNames = ownNames;
            this.conflicts = conflicts;
            this.notes = notes;
        }
    }

    private static final class Moved {
        final String database;
        final NdfDatabase old, mod;

        Moved(String database, NdfDatabase old, NdfDatabase mod) {
            this.database = database;
            this.old = old;
            this.mod = mod;
        }
    }

    public static boolean isOutdated(ModDescriptor mod, int installedModGen) {
        return mod.kind == ModKind.WORKSHOP_COMPILED && mod.modGenVersion != null && mod.modGenVersion >= 0 && mod.modGenVersion < installedModGen;
    }

    /** Why the mod can't be brought up to date, or null when it can. */
    public static String cannotRebase(WarnoPaths paths, ModDescriptor mod) {
        return VanillaDatabases.open(paths.warnoRoot, mod.modGenVersion).available() ? null
            : "it was made for ModGen " + mod.modGenVersion + ", and WARNO no longer keeps that version's data, so it can't be brought up to date";
    }

    public RebasedMod rebase(WarnoPaths paths, ModDescriptor mod, int installedModGen, Consumer<String> log, Cancel token) {
        Consumer<String> say = log != null ? log : m -> { };
        String reason = cannotRebase(paths, mod);
        if (reason != null) throw new CombineException(mod.name + ": " + reason + ".");
        VanillaDatabases oldVanilla = VanillaDatabases.open(paths.warnoRoot, mod.modGenVersion);
        VanillaDatabases newVanilla = VanillaDatabases.open(paths.warnoRoot, installedModGen);
        if (!newVanilla.available()) throw new CombineException("WARNO's current databases weren't found (" + newVanilla.searched() + ").");
        List<MergePlanner.ModFile> listing = MergePlanner.enumerateRuntimeFiles(mod.rootPath);
        String stamp = stamp(mod, listing, installedModGen);
        Path root = paths.savedModsRoot.resolve(CACHE_FOLDER).resolve(safe(mod.workshopId != null ? mod.workshopId : mod.name)
            + "-" + mod.modGenVersion + "-to-" + installedModGen);
        Path marker = root.resolve(".complete");
        Path reportPath = root.resolve(".report.txt");
        if (Files.isRegularFile(marker) && readText(marker).equals(stamp)) {
            say.accept("  " + mod.name + ": using its copy already brought up to ModGen " + installedModGen + ".");
            return load(mod, root, installedModGen, reportPath);
        }

        say.accept("  Bringing " + mod.name + " from ModGen " + mod.modGenVersion + " up to ModGen " + installedModGen + "...");
        FileOps.deleteTree(root);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SourceBaseline baseline = SourceBaseline.open(paths.modDataBaseZip());
        boolean rebuilt = listing.stream().anyMatch(x -> NdfLayout.isTransactionDatabase(x.relativePath));
        TreeMap<String, Path> files = IgnoreCase.map();
        for (MergePlanner.ModFile f : listing) files.put(f.relativePath, f.fullPath);
        List<NdfMerger.Conflict> conflicts = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        TreeMap<String, String> configKeys = new TreeMap<>();
        TreeSet<String> ownNames = IgnoreCase.set();
        TreeMap<String, Path> tags = IgnoreCase.map();
        for (MergePlanner.ModFile f : MergePlanner.enumerateTagFiles(mod.rootPath)) tags.put(f.relativePath, f.fullPath);
        List<Moved> moved = new ArrayList<>();

        for (MergePlanner.ModFile f : listing) {
            token.check();
            String relative = f.relativePath;
            if (NdfLayout.isDatabase(relative)) {
                // A rebuilt mod's leftover .ndfbin files are stale; an old mod's .ndfbin files are its databases.
                if (rebuilt && NdfLayout.isLegacyDatabase(relative)) continue;
                String database = NdfLayout.isLegacyDatabase(relative) ? NdfLayout.toTransactionPath(relative) : relative;
                String own = ownName(database);
                if (own != null) {
                    ownNames.add(own);
                    continue;
                }
                rebaseDatabase(mod, database, f.fullPath, files.get(NdfLayout.debugInfoPath(database)), tags.get(database + ".tag"),
                    oldVanilla, newVanilla, installedModGen, root, conflicts, notes, configKeys, moved);
                continue;
            }
            if (relative.toLowerCase().endsWith(".dbg") && relative.regionMatches(true, 0, "Gen\\NDF\\", 0, 8)) continue; // written with its database
            String top = relative.split("\\\\")[0];
            if (top.equalsIgnoreCase("GameData") || top.equalsIgnoreCase("CommonData")) {
                // The old revision's copies of vanilla sources would override the current game's; only additions carry over.
                if (baseline.contains(relative) && !isOwnSource(relative)) continue;
                if (isOwnSource(relative)) ownNames.add(relative.split("\\\\")[2]);
            }
            FileOps.copy(f.fullPath, root.resolve(relative));
        }
        for (String name : ownNames) writeOwnSources(root, name);
        // Changes to databases WARNO has moved or split are carried over object by object, and every reference is
        // pointed at where its object lives now.
        ExportIndex index = new ExportIndex(newVanilla);
        Map<String, String> pathMap = new HashMap<>();
        relocate(mod, moved, newVanilla, installedModGen, root, index, pathMap, conflicts, notes, configKeys);
        fixReferences(mod, root, index, new ExportIndex(oldVanilla), oldVanilla, newVanilla, pathMap, notes, configKeys);

        writeConfig(mod, root, installedModGen, configKeys);
        StringBuilder report = new StringBuilder();
        for (String name : ownNames) report.append("own\t").append(name).append('\n');
        for (String note : notes) report.append("note\t").append(note).append('\n');
        for (NdfMerger.Conflict c : conflicts) report.append("conflict\t").append(c.path).append('\t').append(c.winnerValue).append('\n');
        writeText(reportPath, report.toString());
        writeText(marker, stamp);
        say.accept(String.format("  %s is now at ModGen %d: %,d value(s) both it and WARNO's updates changed kept the mod's value.",
            mod.name, installedModGen, conflicts.size()));
        return load(mod, root, installedModGen, reportPath);
    }

    private static void rebaseDatabase(ModDescriptor mod, String database, Path file, Path debugFile, Path modTag,
                                       VanillaDatabases oldVanilla, VanillaDatabases newVanilla, int installedModGen, Path root,
                                       List<NdfMerger.Conflict> conflicts, List<String> notes, Map<String, String> configKeys, List<Moved> moved) {
        NdfDatabase modDb = NdfDatabase.load(FileOps.read(file), debugFile == null ? null : FileOps.read(debugFile));
        byte[] oldBytes = oldVanilla.tryRead(database);
        byte[] newBytes = newVanilla.tryRead(database);
        if (newBytes == null && oldBytes != null) {
            NdfDatabase oldDb = NdfDatabase.load(oldBytes);
            if (sameContent(oldDb, modDb)) {
                notes.add("WARNO no longer has " + database + "; " + mod.name + " ships it unchanged, so there is nothing to carry over.");
                return;
            }
            // WARNO moved or split this database since: its changes are carried over object by object (see relocate).
            moved.add(new Moved(database, oldDb, modDb));
            return;
        }
        Path output = root.resolve(database);
        if (newBytes == null) {
            // A database only the mod has: it stays as the mod made it, with the tag WARNO needs to load it.
            if (modTag == null)
                throw new CombineException(mod.name + " has its own database " + database + " without the generation tag WARNO needs, so it can't be brought up to date.");
            write(NdfPoolOrder.settle(modDb).database, output);
            FileOps.copy(modTag, Path.of(output + ".tag"));
            return;
        }
        NdfDatabase newDb = NdfDatabase.load(newBytes);
        String updateName = "WARNO " + installedModGen;
        // The mod's changes against its own vanilla, and WARNO's changes since, applied to the old vanilla database.
        // Where both changed a value the mod's is kept: it is the mod author's intent. Without the old database (the
        // mod added a database vanilla only has now), the mod's copy is laid over the current one.
        NdfMerger.Result result = oldBytes != null
            ? NdfMerger.merge(NdfDatabase.load(oldBytes), Arrays.asList(new NdfMerger.Variant(mod.name, modDb), new NdfMerger.Variant(updateName, newDb)), Cancel.NONE)
            : NdfMerger.merge(newDb, Collections.singletonList(new NdfMerger.Variant(mod.name, modDb)), Cancel.NONE);
        for (NdfMerger.Conflict c : result.conflicts) conflicts.add(c.withPath(database + " › " + c.path));
        write(result.database, output);
        byte[] tag = newVanilla.tryRead(database + ".tag");
        if (tag == null) throw new CombineException("WARNO's current " + database + " has no generation tag to give " + mod.name + "'s updated copy.");
        FileOps.write(Path.of(output + ".tag"), tag);
        configKeys.put(NdfLayout.manifestKey(database), md5Hex(newBytes));
    }

    private static boolean sameContent(NdfDatabase a, NdfDatabase b) {
        if (a.topLevel.size() != b.topLevel.size()) return false;
        for (int i = 0; i < a.topLevel.size(); i++) {
            NdfDatabase.TopLevel x = a.topLevel.get(i), y = b.topLevel.get(i);
            if (!java.util.Objects.equals(x.exportPath, y.exportPath) || !NdfValue.same(x.value, y.value)) return false;
        }
        return true;
    }

    private static void write(NdfDatabase database, Path output) {
        NdfWriter.Output written = database.saveWithDebugInfo();
        FileOps.write(output, written.database);
        FileOps.write(Path.of(NdfLayout.debugInfoPath(output.toString())), written.debugInfo);
    }

    /** The name of a mod's own dictionary or resource pack database (Localisation\X\DicoDef.txn), if it is one. */
    private static String ownName(String database) {
        String[] parts = database.split("\\\\");
        if (parts.length != 5) return null;
        for (String folder : OWN_FOLDERS) if (folder.equalsIgnoreCase(parts[2])) return parts[3];
        return null;
    }

    private static boolean isOwnSource(String relative) {
        String[] parts = relative.split("\\\\");
        if (parts.length < 4 || !parts[0].equalsIgnoreCase("GameData")) return false;
        boolean ownFolder = false;
        for (String folder : OWN_FOLDERS) ownFolder |= folder.equalsIgnoreCase(parts[1]);
        return ownFolder && !parts[2].equalsIgnoreCase("Core") && !parts[2].equalsIgnoreCase("SteelMan") && !parts[2].equalsIgnoreCase("Database");
    }

    /**
     * The sources WARNO's generator builds a dictionary or resource pack from. A mod that shipped them keeps its own;
     * otherwise its compiled ones are the SDK's empty templates, which are written as CreateAssetDefinitionFiles does.
     */
    private static void writeOwnSources(Path root, String name) {
        Path dicos = root.resolve("GameData").resolve("Localisation").resolve(name).resolve("LocalisationDicos.ndf");
        if (!Files.isRegularFile(dicos)) {
            StringBuilder declaration = new StringBuilder();
            for (String[] dictionary : DICTIONARIES) {
                declaration.append("unnamed TLocalisationDicoResource\n(\n    DicoToken = ~/LocalisationConstantes/").append(dictionary[0])
                    .append("\n    FileName = 'GameData:/Localisation/").append(name).append('/').append(dictionary[1]).append("'\n)\n\n");
                Path csv = root.resolve("GameData").resolve("Localisation").resolve(name).resolve(dictionary[1]);
                if (!Files.isRegularFile(csv)) writeText(csv, "\"TOKEN\";\"REFTEXT\"\n");
            }
            writeText(dicos, declaration.toString());
        }
        Path packs = root.resolve("GameData").resolve("ResourcePacks").resolve(name).resolve("ResourcePacks.ndf");
        if (!Files.isRegularFile(packs)) {
            writeText(packs, "\nunnamed TResourcePackLoader\n(\n    Packs =\n    [\n        TResourceDescriptorMeshPack\n        (\n"
                + "            PackName = \"Base\"\n            PackRelativePath = \"MeshPack/" + name + "/Mesh_All.spk\"\n        ),\n"
                + "        TResourceDescriptorProxyPack\n        (\n            PackName = \"Base\"\n            PackRelativePath = \"TextureProxy/"
                + name + "/Mesh_All.ndfbin\"\n        ),\n    ]\n)\n");
        }
    }

    private static void writeConfig(ModDescriptor mod, Path root, int installedModGen, Map<String, String> configKeys) {
        StringBuilder text = new StringBuilder();
        text.append("[Properties]\r\n");
        text.append("Name = ").append(mod.name).append("\r\n");
        text.append("TagList = ").append(String.join(",", mod.tags)).append("\r\n");
        text.append("ID = ").append(mod.workshopId == null ? "" : mod.workshopId).append("\r\n");
        text.append("DeckFormatVersion = ").append(mod.deckFormatVersion).append("\r\n");
        text.append("ModGenVersion = ").append(installedModGen).append("\r\n\r\n");
        text.append("[Config]\r\n");
        for (Map.Entry<String, String> e : configKeys.entrySet()) text.append(e.getKey()).append('=').append(e.getValue()).append("\r\n");
        writeText(root.resolve("Config.ini"), text.toString());
    }

    private static RebasedMod load(ModDescriptor mod, Path root, int installedModGen, Path reportPath) {
        IniDocument config = IniDocument.load(root.resolve("Config.ini"));
        ModDescriptor updated = mod.withUpdate(root, installedModGen, config.getSection("Config"));
        List<String> own = new ArrayList<>(), notes = new ArrayList<>();
        List<NdfMerger.Conflict> conflicts = new ArrayList<>();
        for (String line : readText(reportPath).split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts[0].equals("own")) own.add(parts[1]);
            else if (parts[0].equals("note")) notes.add(parts[1]);
            else if (parts[0].equals("conflict"))
                conflicts.add(new NdfMerger.Conflict(parts[1], mod.name, Collections.singletonList("WARNO " + installedModGen), parts.length > 2 ? parts[2] : ""));
        }
        return new RebasedMod(mod, updated, own, conflicts, notes);
    }

    private static String stamp(ModDescriptor mod, List<MergePlanner.ModFile> listing, int installedModGen) {
        StringBuilder text = new StringBuilder(FORMAT_VERSION + "|" + mod.modGenVersion + "|" + installedModGen + "\n");
        List<MergePlanner.ModFile> sorted = new ArrayList<>(listing);
        sorted.sort((a, b) -> IgnoreCase.ORDER.compare(a.relativePath, b.relativePath));
        for (MergePlanner.ModFile f : sorted) {
            File file = f.fullPath.toFile();
            text.append(f.relativePath).append('|').append(file.length()).append('|').append(file.lastModified()).append('\n');
        }
        return Hex.upper(FileOps.sha256().digest(text.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static String safe(String name) {
        StringBuilder out = new StringBuilder();
        for (char c : name.toCharArray()) out.append(FileOps.hasInvalidFileNameChars(String.valueOf(c)) ? '_' : c);
        return out.toString();
    }

    private static String readText(Path path) {
        return new String(FileOps.read(path), StandardCharsets.UTF_8);
    }

    private static void writeText(Path path, String text) {
        FileOps.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String md5Hex(byte[] data) {
        try {
            return Hex.upper(MessageDigest.getInstance("MD5").digest(data)).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Where every export of the installed WARNO lives: by path, and by its last name. */
    private static final class ExportIndex {
        static final class Place {
            final String path, database, className;

            Place(String path, String database, String className) {
                this.path = path;
                this.database = database;
                this.className = className;
            }
        }

        final Map<String, Place> byPath = new HashMap<>();
        final Map<String, List<Place>> byName = new HashMap<>();

        ExportIndex(VanillaDatabases vanilla) {
            for (String database : vanilla.databasePaths()) {
                for (NdfDatabase.TopLevel top : NdfDatabase.load(vanilla.tryRead(database)).topLevel)
                    if (top.exportPath != null) add(top.exportPath, database, top.value.className);
            }
        }

        void add(String path, String database, String className) {
            Place place = new Place(path, database, className);
            byPath.put(path, place);
            byName.computeIfAbsent(shortName(path), k -> new ArrayList<>()).add(place);
        }

        List<Place> named(String path, String className) {
            List<Place> result = new ArrayList<>();
            for (Place p : byName.getOrDefault(shortName(path), Collections.emptyList())) if (p.className.equals(className)) result.add(p);
            return result;
        }

        /** The export's place now: the same path, or else the one export with its name and class. */
        Place locate(String oldPath, String className) {
            Place same = byPath.get(oldPath);
            if (same != null && same.className.equals(className)) return same;
            List<Place> candidates = named(oldPath, className);
            return candidates.size() == 1 ? candidates.get(0) : null;
        }
    }

    private static String shortName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static NdfValue reference(String path, String fromDatabase, ExportIndex index) {
        ExportIndex.Place place = index.byPath.get(path);
        return place != null && place.database.equalsIgnoreCase(fromDatabase) ? new NdfValue.ExportRef(path) : new NdfValue.Import(path);
    }

    private static NdfDatabase loadWorking(Path output, VanillaDatabases newVanilla, String database) {
        return Files.isRegularFile(output)
            ? NdfDatabase.load(FileOps.read(output), FileOps.read(Path.of(NdfLayout.debugInfoPath(output.toString()))))
            : NdfDatabase.load(newVanilla.tryRead(database));
    }

    private static NdfDatabase withTop(NdfDatabase database, NdfDatabase.TopLevel added) {
        List<NdfDatabase.TopLevel> top = new ArrayList<>(database.topLevel);
        top.add(added);
        return new NdfDatabase(top, database.propertyOwners);
    }

    /**
     * Carries a mod's changes to databases WARNO has since moved, split up or renamed (GFX\Constantes became parts of
     * Scenario\GFX\Constantes, GFX\Pawn and others, with its exports renamed to $/GameplayTuning/...). Each object the
     * mod changed is found in the current game by its export path, or by its unique name and class, or (for a private
     * constant) by class and position in the database most of its exports moved to; there it is three-way merged, the
     * mod's values winning. Exports the mod added join that database; ones it removed are removed.
     */
    private static void relocate(ModDescriptor mod, List<Moved> moved, VanillaDatabases newVanilla, int installedModGen, Path root,
                                 ExportIndex index, Map<String, String> pathMap, List<NdfMerger.Conflict> conflicts, List<String> notes,
                                 Map<String, String> configKeys) {
        if (moved.isEmpty()) return;
        String update = "WARNO " + installedModGen;
        TreeMap<String, NdfDatabase> working = IgnoreCase.map();
        Function<String, NdfDatabase> workingDb = database -> working.computeIfAbsent(database, d -> loadWorking(root.resolve(d), newVanilla, d));
        // A reference into a moved object points at its new path: within one database by export, across by import.
        Function<String, NdfRewrite> references = database -> new NdfRewrite(null, leaf -> {
            String path = leaf instanceof NdfValue.ExportRef ? ((NdfValue.ExportRef) leaf).path : leaf instanceof NdfValue.Import ? ((NdfValue.Import) leaf).path : null;
            String target = path == null ? null : pathMap.get(path);
            return target == null ? null : reference(target, database, index);
        });

        for (Moved move : moved) {
            List<String> unresolved = new ArrayList<>();
            Map<String, Integer> targets = new LinkedHashMap<>();
            for (NdfDatabase.TopLevel top : move.old.topLevel) {
                if (top.exportPath == null) continue;
                ExportIndex.Place place = index.locate(top.exportPath, top.value.className);
                if (place == null) continue;
                pathMap.put(top.exportPath, place.path);
                targets.merge(place.database, 1, Integer::sum);
            }
            List<String> ranked = new ArrayList<>(targets.keySet());
            ranked.sort((a, b) -> Integer.compare(targets.get(b), targets.get(a)));
            String primary = ranked.isEmpty() ? null : ranked.get(0);

            // Exports: changed, added and removed.
            Map<String, NdfValue.Obj> oldExports = new LinkedHashMap<>(), modExports = new LinkedHashMap<>();
            for (NdfDatabase.TopLevel t : move.old.topLevel) if (t.exportPath != null) oldExports.put(t.exportPath, t.value);
            for (NdfDatabase.TopLevel t : move.mod.topLevel) if (t.exportPath != null) modExports.put(t.exportPath, t.value);
            Set<String> all = new LinkedHashSet<>(oldExports.keySet());
            all.addAll(modExports.keySet());
            for (String path : all) {
                NdfValue.Obj before = oldExports.get(path), after = modExports.get(path);
                if (before != null && after != null && NdfValue.same(before, after)) continue;
                if (before == null) {
                    if (primary == null) {
                        unresolved.add(shortName(path));
                        continue;
                    }
                    Map<String, Integer> prefixes = new LinkedHashMap<>();
                    for (NdfDatabase.TopLevel t : workingDb.apply(primary).topLevel)
                        if (t.exportPath != null) prefixes.merge(t.exportPath.substring(0, t.exportPath.lastIndexOf('/')), 1, Integer::sum);
                    String prefix = Collections.max(prefixes.entrySet(), Map.Entry.comparingByValue()).getKey();
                    String newPath = prefix + "/" + shortName(path);
                    pathMap.put(path, newPath);
                    index.add(newPath, primary, after.className);
                    NdfValue.Obj added = references.apply(primary).object(after);
                    working.put(primary, withTop(workingDb.apply(primary), new NdfDatabase.TopLevel(newPath, added)));
                    continue;
                }
                String target = pathMap.get(path);
                ExportIndex.Place place = target == null ? null : index.byPath.get(target);
                if (place == null) {
                    unresolved.add(shortName(path));
                    continue;
                }
                String db = place.database;
                NdfDatabase current = workingDb.apply(db);
                NdfDatabase.TopLevel entry = current.topLevel.stream().filter(t -> target.equals(t.exportPath)).findFirst().orElseThrow();
                NdfRewrite rewrite = references.apply(db);
                NdfMerger.One merged = NdfMerger.mergeOne(shortName(target), rewrite.object(before),
                    Arrays.asList(new NdfMerger.Edit(mod.name, after == null ? null : rewrite.object(after)), new NdfMerger.Edit(update, entry.value)));
                for (NdfMerger.Conflict c : merged.conflicts) conflicts.add(c.withPath(db + " › " + c.path));
                boolean referenced = merged.value == null && NdfRewrite.referencePaths(current).contains(target);
                List<NdfDatabase.TopLevel> top = new ArrayList<>();
                for (NdfDatabase.TopLevel t : current.topLevel) {
                    if (t != entry) top.add(t);
                    else if (merged.value instanceof NdfValue.Obj) top.add(t.withValue((NdfValue.Obj) merged.value));
                    else if (referenced) top.add(t);
                }
                working.put(db, new NdfDatabase(top, current.propertyOwners));
                if (referenced) notes.add(mod.name + " removes " + shortName(target) + ", which the current game still uses, so it is kept.");
            }

            // Private constants: by class and position, in the databases the exports moved to.
            Map<String, List<NdfValue.Obj>> oldConstants = new LinkedHashMap<>(), modConstants = new HashMap<>();
            for (NdfDatabase.TopLevel t : move.old.topLevel) if (t.exportPath == null) oldConstants.computeIfAbsent(t.value.className, k -> new ArrayList<>()).add(t.value);
            for (NdfDatabase.TopLevel t : move.mod.topLevel) if (t.exportPath == null) modConstants.computeIfAbsent(t.value.className, k -> new ArrayList<>()).add(t.value);
            for (Map.Entry<String, List<NdfValue.Obj>> group : oldConstants.entrySet()) {
                List<NdfValue.Obj> olds = group.getValue();
                List<NdfValue.Obj> mods = modConstants.getOrDefault(group.getKey(), Collections.emptyList());
                for (int i = 0; i < Math.min(olds.size(), mods.size()); i++) {
                    if (NdfValue.same(olds.get(i), mods.get(i))) continue;
                    String placeDb = null;
                    List<NdfDatabase.TopLevel> placeConstants = null;
                    for (String db : ranked) {
                        List<NdfDatabase.TopLevel> found = new ArrayList<>();
                        for (NdfDatabase.TopLevel t : workingDb.apply(db).topLevel)
                            if (t.exportPath == null && t.value.className.equals(group.getKey())) found.add(t);
                        if (!found.isEmpty()) {
                            placeDb = db;
                            placeConstants = found;
                            break;
                        }
                    }
                    NdfValue.Obj current = placeConstants == null ? null
                        : placeConstants.size() == olds.size() ? placeConstants.get(i).value
                        : placeConstants.size() == 1 ? placeConstants.get(0).value : null;
                    if (current == null) {
                        unresolved.add("a " + group.getKey() + " constant");
                        continue;
                    }
                    NdfRewrite rewrite = references.apply(placeDb);
                    NdfMerger.One merged = NdfMerger.mergeOne(group.getKey(), rewrite.object(olds.get(i)),
                        Arrays.asList(new NdfMerger.Edit(mod.name, rewrite.object(mods.get(i))), new NdfMerger.Edit(update, current)));
                    String hostDb = placeDb;
                    for (NdfMerger.Conflict c : merged.conflicts) conflicts.add(c.withPath(hostDb + " › " + c.path));
                    if (merged.value instanceof NdfValue.Obj) {
                        NdfValue.Obj replacement = (NdfValue.Obj) merged.value, original = current;
                        working.put(placeDb, new NdfRewrite(o -> o == original ? replacement : null, null).apply(workingDb.apply(placeDb)));
                    }
                }
            }
            if (!unresolved.isEmpty()) {
                List<String> shown = new ArrayList<>(new LinkedHashSet<>(unresolved));
                throw new CombineException("WARNO has reorganised " + move.database + " since " + mod.name + " was made, and these changes of " + mod.name
                    + " have no counterpart in the current game: " + String.join(", ", shown.subList(0, Math.min(10, shown.size()))) + ".");
            }
            notes.add("WARNO moved or split " + move.database + "; " + mod.name + "'s changes to it were carried over to " + String.join(", ", ranked) + ".");
        }

        for (Map.Entry<String, NdfDatabase> e : working.entrySet()) {
            Path output = root.resolve(e.getKey());
            write(NdfPoolOrder.settle(e.getValue()).database, output);
            byte[] tag = newVanilla.tryRead(e.getKey() + ".tag");
            if (tag == null) throw new CombineException("WARNO's current " + e.getKey() + " has no generation tag.");
            FileOps.write(Path.of(output + ".tag"), tag);
            configKeys.put(NdfLayout.manifestKey(e.getKey()), md5Hex(newVanilla.tryRead(e.getKey())));
        }
    }

    /**
     * Points every reference in the updated databases at an object the current game has. A reference to an object of
     * the old revision's databases that WARNO has moved or renamed is re-pointed to its new path (by the moves found,
     * by its unique name and class now, or by the copy in the referring database). One WARNO deleted, but the mod still
     * uses (a unit its divisions still field, say), is restored from the old revision's database, with whatever it uses
     * in turn, so the mod keeps working as it was made. References to objects outside the game databases (engine
     * resources) are left as they are.
     */
    private static void fixReferences(ModDescriptor mod, Path root, ExportIndex index, ExportIndex oldIndex, VanillaDatabases oldVanilla,
                                      VanillaDatabases newVanilla, Map<String, String> pathMap, List<String> notes, Map<String, String> configKeys) {
        Path ndf = root.resolve("Gen").resolve("NDF");
        if (!Files.isDirectory(ndf)) return;
        TreeMap<String, NdfDatabase> working = IgnoreCase.map();
        for (Path f : FileOps.files(ndf)) {
            if (!f.getFileName().toString().toLowerCase().endsWith(".txn")) continue;
            working.put(FileOps.relative(root, f), NdfDatabase.load(FileOps.read(f), FileOps.read(Path.of(NdfLayout.debugInfoPath(f.toString())))));
        }
        Set<String> changed = IgnoreCase.set();
        Map<String, NdfDatabase> oldDatabases = new HashMap<>();
        List<String> restored = new ArrayList<>();

        // Until no reference is stale: re-point what moved, restore what WARNO deleted but the mod still uses.
        for (int pass = 0; pass < 64; pass++) {
            Set<String> own = new java.util.HashSet<>();
            for (NdfDatabase d : working.values()) for (NdfDatabase.TopLevel t : d.topLevel) if (t.exportPath != null) own.add(t.exportPath);
            List<String[]> stale = new ArrayList<>();
            boolean repoint = false;
            for (Map.Entry<String, NdfDatabase> w : working.entrySet()) {
                for (String p : NdfRewrite.referencePaths(w.getValue())) {
                    if (oldIndex.byPath.containsKey(p) && !index.byPath.containsKey(p) && !own.contains(p) && !pathMap.containsKey(p))
                        stale.add(new String[]{w.getKey(), p});
                    if (pathMap.containsKey(p) && !own.contains(p)) repoint = true;
                }
            }
            if (stale.isEmpty() && !repoint) break;
            for (String[] s : stale) {
                String database = s[0], path = s[1];
                if (pathMap.containsKey(path)) continue;
                String className = oldIndex.byPath.get(path).className;
                List<ExportIndex.Place> candidates = index.named(path, className);
                List<ExportIndex.Place> local = new ArrayList<>();
                for (ExportIndex.Place c : candidates) if (c.database.equalsIgnoreCase(database)) local.add(c);
                if (candidates.size() == 1) {
                    pathMap.put(path, candidates.get(0).path);
                    continue;
                }
                if (local.size() == 1) {
                    pathMap.put(path, local.get(0).path);
                    continue;
                }
                // Deleted (or no longer one object): bring back the old one, where its old database's objects live now.
                String oldDatabase = oldIndex.byPath.get(path).database;
                NdfDatabase oldDb = oldDatabases.computeIfAbsent(oldDatabase, d -> NdfDatabase.load(oldVanilla.tryRead(d)));
                // Old revisions name databases Gen\NDF\X.ndfbin, the current one Gen\NDF\X.txn.
                String currentName = NdfLayout.isLegacyDatabase(oldDatabase) ? NdfLayout.toTransactionPath(oldDatabase) : oldDatabase;
                String host;
                if (newVanilla.tryRead(currentName) != null) {
                    host = currentName;
                } else {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    for (NdfDatabase.TopLevel t : oldDb.topLevel) {
                        if (t.exportPath == null || !pathMap.containsKey(t.exportPath)) continue;
                        ExportIndex.Place place = index.byPath.get(pathMap.get(t.exportPath));
                        if (place != null) counts.merge(place.database.toUpperCase(), 1, Integer::sum);
                    }
                    String best = counts.isEmpty() ? null : Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
                    host = database;
                    if (best != null) {
                        for (NdfDatabase.TopLevel t : oldDb.topLevel) {
                            if (t.exportPath == null || !pathMap.containsKey(t.exportPath)) continue;
                            ExportIndex.Place place = index.byPath.get(pathMap.get(t.exportPath));
                            if (place != null && place.database.equalsIgnoreCase(best)) { host = place.database; break; }
                        }
                    }
                }
                NdfValue.Obj value = oldDb.topLevel.stream().filter(t -> path.equals(t.exportPath)).findFirst().orElseThrow().value;
                String hostName = host;
                NdfDatabase hostDb = working.computeIfAbsent(hostName, d -> NdfDatabase.load(newVanilla.tryRead(d)));
                working.put(hostName, withTop(hostDb, new NdfDatabase.TopLevel(path, value)));
                index.add(path, hostName, className);
                changed.add(hostName);
                restored.add(shortName(path));
            }
            for (String database : new ArrayList<>(working.keySet())) {
                NdfDatabase before = working.get(database);
                NdfDatabase after = new NdfRewrite(null, leaf -> {
                    if (leaf instanceof NdfValue.ExportRef) {
                        String target = pathMap.get(((NdfValue.ExportRef) leaf).path);
                        return target == null ? null : reference(target, database, index);
                    }
                    if (leaf instanceof NdfValue.Import) {
                        String importPath = ((NdfValue.Import) leaf).path;
                        String target = pathMap.get(importPath);
                        if (target != null) return reference(target, database, index);
                        ExportIndex.Place place = index.byPath.get(importPath);
                        if (place != null && place.database.equalsIgnoreCase(database)) return new NdfValue.ExportRef(importPath);
                    }
                    return null;
                }).apply(before);
                if (NdfRewrite.changed(before, after)) {
                    working.put(database, after);
                    changed.add(database);
                }
            }
        }

        for (String database : changed) {
            Path output = root.resolve(database);
            write(NdfPoolOrder.settle(working.get(database)).database, output);
            Path tagPath = Path.of(output + ".tag");
            if (!Files.isRegularFile(tagPath)) {
                byte[] tag = newVanilla.tryRead(database + ".tag");
                if (tag == null) throw new CombineException("WARNO's current " + database + " has no generation tag.");
                FileOps.write(tagPath, tag);
            }
            byte[] vanillaBytes = newVanilla.tryRead(database);
            if (vanillaBytes != null) configKeys.put(NdfLayout.manifestKey(database), md5Hex(vanillaBytes));
        }
        if (!restored.isEmpty()) {
            List<String> shown = new ArrayList<>(new LinkedHashSet<>(restored));
            notes.add(String.format("%s uses %,d object(s) WARNO has since removed (%s%s); they were restored from the version it was made for.",
                mod.name, restored.size(), String.join(", ", shown.subList(0, Math.min(8, shown.size()))), restored.size() > 8 ? ", ..." : ""));
        }
    }

}
