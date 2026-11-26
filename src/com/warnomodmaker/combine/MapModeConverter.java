package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.EdatArchive;
import com.warnomodmaker.combine.ndf.IgnoreCase;
import com.warnomodmaker.combine.ndf.LocalisationToken;
import com.warnomodmaker.combine.ndf.NdfDatabase;
import com.warnomodmaker.combine.ndf.NdfRewrite;
import com.warnomodmaker.combine.ndf.NdfValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts WARNO skirmish scenarios between Conquest and Destruction.
 *
 * <p>A skirmish scenario is two datapacks in Data\PC\&lt;revision&gt;\Scenarios: &lt;Name&gt;_Definition.dat (the
 * scenario declaration, including the {@code TMultiMapInfo} whose {@code CombatRule} is the game mode) and
 * &lt;Name&gt;_Details.dat (command, deployment and playable zones, aerial corridors, camera paths and the
 * computer player's strategic zones and path cache). None of the zone data depends on the mode, so a conversion keeps
 * the source scenario's layout and declares it with the other {@code CombatRule}.
 *
 * <p>The new scenario is built the way Eugen's map editor manual describes: a source folder in
 * Saved Games\EugenSystems\WARNO\Scenario, WARNO's generation with local data, the AssetCooker and the DataPacker,
 * whose datapacks go into a mod's Scenarios folder. The mod itself comes from WARNO's CreateNewMod and GenerateMod.
 */
public final class MapModeConverter {
    public enum Mode {
        DESTRUCTION(1, "Destruction", "DEST"), CONQUEST(2, "Conquest", "CONQ");

        public final int combatRule;
        public final String label;
        final String tag;

        Mode(int combatRule, String label, String tag) {
            this.combatRule = combatRule;
            this.label = label;
            this.tag = tag;
        }

        public Mode other() {
            return this == DESTRUCTION ? CONQUEST : DESTRUCTION;
        }

        static Mode of(int combatRule) {
            for (Mode m : values()) if (m.combatRule == combatRule) return m;
            return null;
        }
    }

    /** A vanilla skirmish scenario that can be converted. */
    public static final class Scenario {
        public final String name;
        public final String map;
        public final Mode mode;
        public final int slots;
        final Path definition;
        final List<Path> details;

        Scenario(String name, String map, Mode mode, int slots, Path definition, List<Path> details) {
            this.name = name;
            this.map = map;
            this.mode = mode;
            this.slots = slots;
            this.definition = definition;
            this.details = details;
        }

        /** The converted scenario's name: the mode tag swapped (CONQ ↔ DEST) and a suffix that keeps it apart from vanilla. */
        public String convertedName() {
            Matcher m = MODE_TAG.matcher(name);
            String swapped = m.find() ? name.substring(0, m.start(1)) + mode.other().tag + name.substring(m.end(1)) : name + "_" + mode.other().tag;
            return swapped + "_UM";
        }

        /** The name shown in the skirmish lobby, such as "HESSE 2VS2 DESTRUCTION". */
        public String convertedDisplayName() {
            String base = name.replaceFirst("^_\\d+x\\d+_", "");
            Matcher m = MODE_TAG.matcher(base);
            if (m.find()) base = base.substring(0, m.start(1)) + base.substring(m.end(1));
            base = base.replace('_', ' ').replaceAll("\\s+", " ").trim();
            return (base + " " + mode.other().label).toUpperCase();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** A command zone of a scenario: its editor name, GUID and points (victory points in Conquest, income in Destruction). */
    public static final class Zone {
        public final String name;
        public final String guid;
        public final int points;

        Zone(String name, String guid, int points) {
            this.name = name;
            this.guid = guid;
            this.points = points;
        }
    }

    /** Everything a build can change about a scenario, starting from the source's own values (see {@link #defaults}). */
    public static final class Settings {
        public Mode mode;
        public String scenarioName;
        public String displayName;
        public int slots;
        public final List<String> availableEnvironments = new ArrayList<>();
        public final List<String> environments = new ArrayList<>();
        public String mapSize;
        public String density;
        public boolean rivers;
        public final List<Zone> zones = new ArrayList<>();
        /** Points per zone GUID; a zone missing here keeps its points. */
        public final Map<String, Integer> zonePoints = new LinkedHashMap<>();

        public int points(Zone zone) {
            Integer p = zonePoints.get(zone.guid);
            return p != null ? p : zone.points;
        }

        boolean pointsChanged() {
            for (Zone zone : zones) if (points(zone) != zone.points) return true;
            return false;
        }
    }

    public static final class Job {
        public final Scenario scenario;
        public final Settings settings;

        public Job(Scenario scenario, Settings settings) {
            this.scenario = scenario;
            this.settings = settings;
        }
    }

    public static final class Request {
        public final WarnoPaths paths;
        public final List<Job> jobs;
        public final String modName;

        public Request(WarnoPaths paths, List<Job> jobs, String modName) {
            this.paths = paths;
            this.jobs = jobs;
            this.modName = modName;
        }
    }

    public static final List<String> MAP_SIZES = Arrays.asList("SIZESMA", "SIZEMED", "SIZELAR");
    public static final List<String> DENSITIES = Arrays.asList("DENSLOW", "DENSMED", "DENSHIGH");
    private static final Pattern SCENARIO_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private static final Pattern MODE_TAG = Pattern.compile("_(CONQ|DEST)(?=_|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REVISION = Pattern.compile("\\d{1,9}");
    private static final String MARKER = ".ultimod-map-mode";

    private final ProcessRunner processRunner;

    public MapModeConverter(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /** Every vanilla skirmish scenario with a Conquest or Destruction declaration, sorted by name. */
    public List<Scenario> scan(Path warnoRoot, Cancel token) {
        Path pc = warnoRoot.resolve("Data").resolve("PC");
        if (!Files.isDirectory(pc)) throw new CombineException("WARNO's data folder " + pc + " doesn't exist. Choose the WARNO installation folder.");
        TreeMap<String, List<Path>> definitions = IgnoreCase.map();
        TreeMap<String, List<Path>> details = IgnoreCase.map();
        try (Stream<Path> walk = Files.walk(pc, 4)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getParent().getFileName().toString().equalsIgnoreCase("Scenarios"))
                .forEach(p -> {
                    String file = p.getFileName().toString();
                    if (file.endsWith("_Definition.dat")) definitions.computeIfAbsent(file.substring(0, file.length() - 15), k -> new ArrayList<>()).add(p);
                    else if (file.endsWith("_Details.dat")) details.computeIfAbsent(file.substring(0, file.length() - 12), k -> new ArrayList<>()).add(p);
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Scenario> result = new ArrayList<>();
        for (Map.Entry<String, List<Path>> entry : definitions.entrySet()) {
            token.check();
            List<Path> layers = details.get(entry.getKey());
            if (layers == null) continue;
            List<Path> definitionLayers = layered(entry.getValue());
            Path definition = definitionLayers.get(definitionLayers.size() - 1);
            Declaration declaration;
            try {
                declaration = Declaration.read(definition);
            } catch (RuntimeException e) {
                continue; // Not a skirmish scenario this tool understands (campaign, challenge, strategic battleground).
            }
            if (declaration == null || declaration.mode == null || !"Skirmish".equalsIgnoreCase(declaration.category)) continue;
            result.add(new Scenario(declaration.name, declaration.map, declaration.mode, declaration.slots, definition, layered(layers)));
        }
        result.sort(Comparator.comparing(s -> s.name, IgnoreCase.ORDER));
        return result;
    }

    /**
     * Revision layers oldest first. A copy's revision is the highest revision folder on its path
     * (Data\PC\211352\212400\Scenarios is 212400); a nested copy is a later patch of the folder it is in.
     */
    static List<Path> layered(List<Path> copies) {
        List<Path> sorted = new ArrayList<>(copies);
        sorted.sort(Comparator.comparingLong(MapModeConverter::revision).thenComparingInt(Path::getNameCount));
        return sorted;
    }

    private static long revision(Path file) {
        long best = -1;
        for (Path part : file) if (REVISION.matcher(part.toString()).matches()) best = Math.max(best, Long.parseLong(part.toString()));
        return best;
    }

    /** The parts of a scenario declaration a conversion carries over. */
    static final class Declaration {
        String name, map, category, description, mapSize, density;
        Mode mode;
        int slots;
        boolean rivers;
        List<String> environments = new ArrayList<>();

        static Declaration read(Path definition) {
            EdatArchive archive = new EdatArchive(definition);
            NdfValue.Obj config = null, info = null;
            for (EdatArchive.Entry entry : archive.entries().values()) {
                String path = entry.path.toLowerCase();
                if (path.endsWith("/mapconfiguration.txn")) config = first(archive, entry, "TMultiMapInfo");
                else if (path.endsWith("/scenarioinfo.txn")) info = first(archive, entry, "TScenarioDefinition");
            }
            if (config == null || info == null) return null;
            Declaration d = new Declaration();
            d.name = text(config.get("ScenarioName"));
            d.map = text(info.get("RootDatapackName"));
            d.category = text(info.get("Category"));
            d.mode = Mode.of(Integer.parseInt(config.get("CombatRule").describe()));
            d.slots = Integer.parseInt(config.get("DefaultNbSlotsOpened").describe());
            d.description = LocalisationToken.decode(config.get("Description"));
            d.mapSize = LocalisationToken.decode(config.get("MapSize"));
            d.density = LocalisationToken.decode(config.get("Density"));
            NdfValue rivers = config.get("HasRivers");
            d.rivers = rivers != null && rivers.describe().equalsIgnoreCase("True");
            for (NdfValue v : ((NdfValue.ListV) config.get("EnvironmentSettings")).items) d.environments.add(v.describe());
            return d;
        }

        private static NdfValue.Obj first(EdatArchive archive, EdatArchive.Entry entry, String className) {
            for (NdfDatabase.TopLevel top : NdfDatabase.load(archive.read(entry)).topLevel)
                if (top.value.className.equals(className)) return top.value;
            return null;
        }

        private static String text(NdfValue value) {
            if (!(value instanceof NdfValue.Text)) throw new CombineException("Unexpected scenario declaration value " + (value == null ? "null" : value.describe()) + ".");
            return ((NdfValue.Text) value).text;
        }
    }

    /** The zone files of a scenario: every layer's Details datapack, later revisions replacing earlier ones' files. */
    static TreeMap<String, byte[]> details(Scenario scenario) {
        TreeMap<String, byte[]> files = new TreeMap<>();
        for (Path layer : scenario.details) {
            EdatArchive archive = new EdatArchive(layer);
            for (EdatArchive.Entry entry : archive.entries().values()) files.put(entry.path.replace('\\', '/'), archive.read(entry));
        }
        return files;
    }

    /** The source scenario's values, with the mode switched and a new name: the starting point for editing. */
    public Settings defaults(Scenario scenario) {
        Declaration d = Declaration.read(scenario.definition);
        Settings settings = new Settings();
        settings.mode = scenario.mode.other();
        settings.scenarioName = scenario.convertedName();
        settings.displayName = scenario.convertedDisplayName();
        settings.slots = d.slots;
        settings.availableEnvironments.addAll(d.environments);
        settings.environments.addAll(d.environments);
        settings.mapSize = d.mapSize;
        settings.density = d.density;
        settings.rivers = d.rivers;
        settings.zones.addAll(zones(details(scenario).get("out/CommandZone.ndfbin")));
        return settings;
    }

    static List<Zone> zones(byte[] commandZones) {
        List<Zone> zones = new ArrayList<>();
        if (commandZones == null) return zones;
        new NdfRewrite(o -> {
            if (o.className.equals("TGameDesignAddOn_CommandPoints") && o.get("GUID") != null && o.get("Points") != null) {
                NdfValue name = o.get("Name");
                zones.add(new Zone(name instanceof NdfValue.Text ? ((NdfValue.Text) name).text : "Zone " + (zones.size() + 1),
                    o.get("GUID").describe(), Integer.parseInt(o.get("Points").describe())));
            }
            return null;
        }, null).apply(NdfDatabase.load(commandZones));
        return zones;
    }

    /** A zone file with its command zones' points replaced, matched by GUID; the same bytes when nothing changes. */
    static byte[] withPoints(byte[] file, Settings settings) {
        if (file == null || !settings.pointsChanged()) return file;
        NdfDatabase before = NdfDatabase.load(file);
        NdfDatabase after = new NdfRewrite(o -> {
            if (!o.className.equals("TGameDesignAddOn_CommandPoints") || o.get("GUID") == null) return null;
            Integer points = settings.zonePoints.get(o.get("GUID").describe());
            NdfValue old = o.get("Points");
            if (points == null || !(old instanceof NdfValue.Raw) || Integer.parseInt(old.describe()) == points) return null;
            NdfValue.Raw raw = (NdfValue.Raw) old;
            byte[] data = ByteBuffer.allocate(raw.data.length).order(ByteOrder.LITTLE_ENDIAN).putInt(points).array();
            List<Map.Entry<String, NdfValue>> fields = new ArrayList<>();
            for (Map.Entry<String, NdfValue> f : o.fields)
                fields.add(f.getKey().equals("Points") ? NdfValue.field("Points", new NdfValue.Raw(raw.type, data)) : f);
            return new NdfValue.Obj(o.className, fields, o.origin);
        }, null).apply(before);
        return NdfRewrite.changed(before, after) ? after.save() : file;
    }

    /** Refuses settings WARNO's tools or lobby would reject. */
    public static void validate(Scenario scenario, Settings s, List<Scenario> vanilla) {
        String what = scenario.name + ": ";
        if (s.mode == null) throw new CombineException(what + "choose a game mode.");
        if (s.scenarioName == null || !SCENARIO_NAME.matcher(s.scenarioName).matches())
            throw new CombineException(what + "the scenario name may only hold letters, digits and underscores (up to 64), and can't start with a digit.");
        for (Scenario v : vanilla)
            if (v.name.equalsIgnoreCase(s.scenarioName)) throw new CombineException(what + s.scenarioName + " is one of WARNO's own scenarios; choose another name.");
        if (s.displayName == null || s.displayName.trim().isEmpty() || s.displayName.length() > 60 || s.displayName.matches(".*[\"\r\n;].*"))
            throw new CombineException(what + "the lobby name must be 1 to 60 characters, without quotes, semicolons or line breaks.");
        if (s.slots < 2 || s.slots > 20) throw new CombineException(what + "players must be between 2 and 20.");
        if (s.environments.isEmpty()) throw new CombineException(what + "keep at least one environment (time of day).");
        for (String e : s.environments)
            if (!s.availableEnvironments.contains(e)) throw new CombineException(what + e + " isn't one of this map's environments.");
        if (!MAP_SIZES.contains(s.mapSize)) throw new CombineException(what + "unknown map size label " + s.mapSize + ".");
        if (!DENSITIES.contains(s.density)) throw new CombineException(what + "unknown density label " + s.density + ".");
        for (Zone zone : s.zones) {
            int p = s.points(zone);
            if (p < 0 || p > 99) throw new CombineException(what + zone.name + " needs 0 to 99 points.");
        }
    }

    /** The token naming the converted scenario in the lobby: "UM" and six characters derived from its name. */
    static String token(String scenarioName) {
        String digits = Long.toString(Integer.toUnsignedLong(scenarioName.toUpperCase().hashCode()), 36).toUpperCase();
        return "UM" + (digits + "000000").substring(0, 6);
    }

    /** Validates a mod name the way Combine does, and refuses names WARNO's scripts can't handle. */
    public static void validateModName(String name) {
        MergePlanner.validateOutputName(name);
    }

    /** Builds the converted scenarios and installs them in the mod (created with WARNO's tools if it doesn't exist). */
    public List<String> convert(Request request, Consumer<String> log, Consumer<Integer> progress, Cancel token) {
        if (request.jobs.isEmpty()) throw new CombineException("Choose at least one scenario to convert.");
        validateModName(request.modName);
        Path warno = request.paths.warnoRoot;
        Path tools = warno.resolve("Tools");
        for (String tool : new String[]{"DataPacker.exe", "AssetCooker.exe", "DataPacking/datapack_local_scenarios.ndf"})
            if (!Files.isRegularFile(tools.resolve(tool))) throw new CombineException("WARNO's map tools are missing (" + tools.resolve(tool) + "). Verify the game files in Steam.");
        Path saved = request.paths.savedModsRoot.getParent();
        Path scenarioSources = saved.resolve("Scenario");
        Path datapacks = saved.resolve("Datapacks").resolve("Scenarios");
        List<String> names = new ArrayList<>();
        List<Scenario> vanilla = scan(warno, token);
        for (Job job : request.jobs) {
            validate(job.scenario, job.settings, vanilla);
            String converted = job.settings.scenarioName;
            if (names.stream().anyMatch(n -> n.equalsIgnoreCase(converted))) throw new CombineException("Two chosen scenarios would both become " + converted + ".");
            Path folder = scenarioSources.resolve(converted);
            if (Files.exists(folder) && !Files.isRegularFile(folder.resolve(MARKER)))
                throw new CombineException("Saved Games\\EugenSystems\\WARNO\\Scenario\\" + converted + " already exists and wasn't made by this tool. Move it away first.");
            names.add(converted);
        }
        List<Path> others = new ArrayList<>();
        for (Path folder : FileOps.directories(scenarioSources))
            if (!Files.isRegularFile(folder.resolve(MARKER)) && names.stream().noneMatch(n -> n.equalsIgnoreCase(folder.getFileName().toString()))) others.add(folder);
        if (!others.isEmpty())
            log.accept("  Note: WARNO also generates your own scenarios in Saved Games\\EugenSystems\\WARNO\\Scenario ("
                + others.stream().map(p -> p.getFileName().toString()).reduce((a, b) -> a + ", " + b).orElse("") + "); only the converted ones go into the mod.");
        String python = PythonLocator.find(request.paths.modsRoot, log);

        try {
            // 1. Scenario sources, as the map editor's File > Export > Scenario and the manual's MapPack.ndf leave them.
            progress.accept(5);
            log.accept("[STEP 1/5] Writing scenario sources to Saved Games\\EugenSystems\\WARNO\\Scenario...");
            TreeMap<String, TreeMap<String, byte[]>> expectedDetails = IgnoreCase.map();
            for (Job job : request.jobs) {
                token.check();
                Scenario scenario = job.scenario;
                Settings settings = job.settings;
                String converted = settings.scenarioName;
                TreeMap<String, byte[]> zones = details(scenario);
                for (String zoneFile : new String[]{"out/CommandZone.ndfbin", "out/LevelDesign.ndfbin"})
                    if (zones.containsKey(zoneFile)) zones.put(zoneFile, withPoints(zones.get(zoneFile), settings));
                if (!zones.containsKey("out/CommandZone.ndfbin") || !zones.containsKey("out/DeploymentZone.ndfbin"))
                    throw new CombineException(scenario.name + " has no command or deployment zones in WARNO's data.");
                expectedDetails.put(converted, zones);
                Path folder = scenarioSources.resolve(converted);
                FileOps.deleteTree(folder);
                for (Map.Entry<String, byte[]> file : zones.entrySet()) FileOps.write(FileOps.safeCombine(folder, file.getKey()), file.getValue());
                Files.createDirectories(folder.resolve("Scripting"));
                writeSources(folder, scenario, settings);
                log.accept("  " + scenario.name + " (" + scenario.mode.label + ") → " + converted + " (" + settings.mode.label + ", '" + settings.displayName
                    + "', " + settings.slots + " players" + (settings.pointsChanged() ? ", zone points changed" : "") + "), "
                    + zones.size() + " zone files from map " + scenario.map + ".");
            }

            // 2. WARNO's generation with local data compiles each Scenario.ndf and MapPack.ndf into the scenario's databases.
            progress.accept(15);
            log.accept("[STEP 2/5] Generating the scenarios with WARNO (LaunchGeneration)...");
            generate(warno, tools, log, token);
            int cook = processRunner.run(tools.resolve("AssetCooker.exe").toString(),
                Arrays.asList("-e", "ProgDir:/CoreCatalog.cat", "-g", "../Gen", "-l", "EugenSystems/WARNO"), tools, line -> log.accept("  [Cook] " + line), line -> log.accept("  [Cook] " + line), token);
            if (cook != 0) throw new CombineException("WARNO's AssetCooker failed with exit code " + cook + ".");

            // 3. The DataPacker turns sources and generated databases into the datapacks a mod ships.
            progress.accept(45);
            log.accept("[STEP 3/5] Packing the scenarios with WARNO's DataPacker (LaunchDatapacking)...");
            for (String name : names) for (Path old : packFiles(datapacks, name)) Files.deleteIfExists(old);
            int pack = processRunner.run(tools.resolve("DataPacker.exe").toString(),
                Arrays.asList("pack", "DataPacking/datapack_local_scenarios.ndf", "-gendir", "../Gen", "-local-data", "EugenSystems/WARNO"), tools,
                line -> log.accept("  [Pack] " + line), line -> log.accept("  [Pack] " + line), token);
            if (pack != 0) throw new CombineException("WARNO's DataPacker failed with exit code " + pack + ".");
            for (Job job : request.jobs) verifyDatapacks(datapacks, job, expectedDetails.get(job.settings.scenarioName), log);

            // 4. A second generation must find every converted scenario current, or the game would rebuild it at startup.
            progress.accept(55);
            log.accept("[STEP 4/5] Checking that WARNO reuses the generated scenarios on a second load...");
            List<String> rebuilt = generate(warno, tools, log, token);
            rebuilt.removeIf(line -> names.stream().noneMatch(n -> line.toLowerCase().contains("/scenarios/" + n.toLowerCase() + "/")));
            if (!rebuilt.isEmpty()) throw new CombineException("WARNO still treats these scenario databases as out of date: " + String.join(", ", rebuilt) + ".");
            log.accept("  WARNO loaded every converted scenario without rebuilding anything.");

            // 5. The mod: WARNO's CreateNewMod (when new) and GenerateMod, then the datapacks in its Scenarios folder.
            progress.accept(65);
            Path modSource = request.paths.modsRoot.resolve(request.modName);
            Path modRuntime = request.paths.savedModsRoot.resolve(request.modName);
            log.accept("[STEP 5/5] Installing the scenarios in the mod '" + request.modName + "'...");
            if (!Files.isDirectory(modSource)) {
                runSdk(python, request, "CreateNewMod.py", Collections.singletonList(request.modName), request.paths.modsRoot, log, token);
                if (!Files.isDirectory(modSource)) throw new CombineException("WARNO did not create the mod folder " + modSource + ".");
            }
            progress.accept(70);
            runSdk(python, request, "GenerateMod.py", Arrays.asList("WARNO", request.modName), modSource, log, token);
            if (!Files.isRegularFile(modRuntime.resolve("Config.ini"))) throw new CombineException("WARNO's GenerateMod did not write " + modRuntime.resolve("Config.ini") + ".");
            Path target = modRuntime.resolve("Scenarios");
            for (String name : names) {
                for (Path file : packFiles(datapacks, name)) {
                    Path destination = target.resolve(file.getFileName().toString());
                    FileOps.copyFileAtomic(file, destination, token);
                    if (!FileOps.sameContent(file, destination, token)) throw new CombineException("Copy verification failed for " + destination + ".");
                }
            }
            log.accept("  Installed " + names.size() + " scenario(s) in Saved Games\\EugenSystems\\WARNO\\mod\\" + request.modName + "\\Scenarios.");
            progress.accept(100);
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            // The sources and loose datapacks were only build inputs; left behind they would show up in local-data runs.
            for (String name : names) {
                FileOps.deleteTree(scenarioSources.resolve(name));
                try {
                    for (Path file : packFiles(datapacks, name)) Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            }
        }
    }

    static void writeSources(Path folder, Scenario scenario, Settings settings) {
        String converted = settings.scenarioName;
        String token = token(converted);
        StringBuilder environments = new StringBuilder();
        for (String environment : settings.environments) environments.append("        ").append(environment).append(",\n");
        String scenarioNdf = converted + " is Scenario\n(\n"
            + "    GUID = GUID:{" + UUID.nameUUIDFromBytes(("UltiMod map mode " + converted).getBytes(StandardCharsets.UTF_8)) + "}\n"
            + "    ScenarioDirectory = '" + converted + "'\n"
            + "    LevelBuildDirectory = '" + scenario.map + "'\n"
            + "    Category = 'Skirmish'\n)\n";
        String mapPack = "// Built from " + scenario.name + " (" + scenario.mode.label + ") by WARNO Mod Maker.\n"
            + "unnamed TemplateMultiMapInfo\n(\n"
            + "    ScenarioName = '" + converted + "'\n"
            + "    Description = '" + token + "'\n"
            + "    DefaultNbSlotsOpened = " + settings.slots + "\n"
            + "    GameMode = EDeploymentMode/Skirmish\n"
            + "    CombatRule = ECombatRule/" + settings.mode.label + "\n"
            + "    EnvironmentSettings = [\n" + environments + "    ]\n"
            + "    MapSize = '" + settings.mapSize + "'\n"
            + "    Density = '" + settings.density + "'\n"
            + "    HasRivers = " + settings.rivers + "\n)\n\n"
            + "unnamed TLocalisationDicoResource\n(\n"
            + "    DicoToken = LocalisationConstantes/dico_maps\n"
            + "    FileName = 'ScenariosData:/" + converted + "/SCENARIO_DEFINITION.csv'\n)\n";
        String csv = "\"TOKEN\";\"REFTEXT\"\n\"" + token + "\";\"" + settings.displayName.trim() + "\"\n";
        FileOps.write(folder.resolve("Scenario.ndf"), scenarioNdf.getBytes(StandardCharsets.UTF_8));
        FileOps.write(folder.resolve("MapPack.ndf"), mapPack.getBytes(StandardCharsets.UTF_8));
        FileOps.write(folder.resolve("SCENARIO_DEFINITION.csv"), csv.getBytes(StandardCharsets.UTF_8));
        FileOps.write(folder.resolve(MARKER), scenario.name.getBytes(StandardCharsets.UTF_8));
    }

    private static final Pattern GENERATING = Pattern.compile("Generating:\\s*'?(ZZ:/[^']+)'?", Pattern.CASE_INSENSITIVE);

    /** Runs WARNO's generation with local data and returns the databases it (re)built. */
    private List<String> generate(Path warno, Path tools, Consumer<String> log, Cancel token) {
        List<String> generated = new ArrayList<>();
        Consumer<String> sink = line -> {
            Matcher m = GENERATING.matcher(line);
            if (m.find()) generated.add(m.group(1));
            if (line.toLowerCase().contains("error")) log.accept("  [WARNO] " + line);
        };
        int exit = processRunner.run(warno.resolve("WARNO.exe").toString(),
            Arrays.asList("CommonData:Clusters/Bootstrap/ClusterBootstrapGeneration.ndf", "-headless", "-uselocaldata"), tools, sink, sink, token);
        if (exit != 0) throw new CombineException("WARNO's scenario generation failed with exit code " + exit + ". If WARNO showed an error, it names the file at fault.");
        log.accept("  WARNO generated " + generated.size() + " file(s).");
        return generated;
    }

    private static List<Path> packFiles(Path datapacks, String name) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(datapacks)) return files;
        try (Stream<Path> list = Files.list(datapacks)) {
            list.filter(p -> {
                String file = p.getFileName().toString();
                return file.regionMatches(true, 0, name + "_", 0, name.length() + 1) && file.toLowerCase().endsWith(".dat")
                    && file.substring(name.length() + 1, file.length() - 4).matches("(?i)Assets|Definition|Details|GameData|ScenariosData");
            }).forEach(files::add);
        }
        return files;
    }

    /** Reads the packed scenario back: declared as configured, and carrying exactly the zone files that were written. */
    static void verifyDatapacks(Path datapacks, Job job, TreeMap<String, byte[]> zones, Consumer<String> log) {
        Scenario scenario = job.scenario;
        Settings settings = job.settings;
        String converted = settings.scenarioName;
        Path definition = datapacks.resolve(converted + "_Definition.dat");
        Path details = datapacks.resolve(converted + "_Details.dat");
        if (!Files.isRegularFile(definition) || !Files.isRegularFile(details) || !Files.isRegularFile(datapacks.resolve(converted + "_Assets.dat")))
            throw new CombineException("The DataPacker did not produce the datapacks of " + converted + ".");
        Declaration packed = Declaration.read(definition);
        if (packed == null || !converted.equals(packed.name) || packed.mode != settings.mode || !scenario.map.equals(packed.map)
            || packed.slots != settings.slots || !packed.environments.equals(settings.environments)
            || !packed.mapSize.equals(settings.mapSize) || !packed.density.equals(settings.density) || packed.rivers != settings.rivers)
            throw new CombineException(converted + "'s packed declaration doesn't match its settings.");
        EdatArchive archive = new EdatArchive(details);
        int matched = 0;
        for (EdatArchive.Entry entry : archive.entries().values()) {
            byte[] expected = zones.get(entry.path.replace('\\', '/'));
            if (expected != null && Arrays.equals(expected, archive.read(entry))) matched++;
        }
        if (matched != zones.size()) throw new CombineException(converted + "'s packed zones differ from the ones written (" + matched + " of " + zones.size() + " match).");
        for (Zone z : zones(zones.get("out/CommandZone.ndfbin"))) {
            Integer wanted = settings.zonePoints.get(z.guid);
            if (wanted != null && wanted != z.points) throw new CombineException(converted + ": " + z.name + " has " + z.points + " points instead of " + wanted + ".");
        }
        log.accept("  Verified " + converted + ": " + packed.mode.label + " on " + packed.map + ", " + packed.slots + " players, " + matched + " zone files.");
    }

    private void runSdk(String python, Request request, String script, List<String> arguments, Path workingDirectory, Consumer<String> log, Cancel token) {
        Path path = request.paths.modsRoot.resolve("Utils").resolve("Scripts").resolve(script);
        if (!Files.isRegularFile(path)) throw new CombineException("WARNO's " + script + " tool is missing. Verify the game files in Steam.");
        List<String> args = new ArrayList<>();
        args.add(path.toString());
        args.addAll(arguments);
        int exit = processRunner.run(python, args, workingDirectory, line -> log.accept("  [SDK] " + line), line -> log.accept("  [SDK ERR] " + line), token);
        if (exit != 0) throw new CombineException("WARNO " + script + " failed with exit code " + exit + ".");
    }
}
