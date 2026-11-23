package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.CombineProgress;
import com.warnomodmaker.combine.Models.CombineRequest;
import com.warnomodmaker.combine.Models.CombineResult;
import com.warnomodmaker.combine.Models.CombinedModState;
import com.warnomodmaker.combine.Models.DecisionKind;
import com.warnomodmaker.combine.Models.MergeDecision;
import com.warnomodmaker.combine.Models.MergePreview;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.ModKind;
import com.warnomodmaker.combine.Models.SourceModFingerprint;
import com.warnomodmaker.combine.ndf.GenerationTag;
import com.warnomodmaker.combine.ndf.IgnoreCase;
import com.warnomodmaker.combine.ndf.NdfLayout;
import com.warnomodmaker.combine.ndf.VanillaDatabases;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/** Builds or rebuilds a combined mod: WARNO's SDK creates it, the plan fills it, and every file is verified. */
public final class CombineService {
    private final SourceDeltaAnalyzer deltaAnalyzer;
    private final ProcessRunner processRunner;

    public CombineService(SourceDeltaAnalyzer deltaAnalyzer, ProcessRunner processRunner) {
        this.deltaAnalyzer = deltaAnalyzer;
        this.processRunner = processRunner;
    }

    public CombineResult combine(CombineRequest request, Consumer<String> log, Consumer<CombineProgress> progress, Cancel token) {
        return execute(request, false, log, progress, token);
    }

    public CombineResult rebuild(CombineRequest request, Consumer<String> log, Consumer<CombineProgress> progress, Cancel token) {
        return execute(request, true, log, progress, token);
    }

    private interface Reporter {
        void report(int percent, String stage);
    }

    private CombineResult execute(CombineRequest request, boolean rebuild, Consumer<String> log, Consumer<CombineProgress> progress, Cancel token) {
        Reporter report = (percent, stage) -> {
            token.check();
            progress.accept(new CombineProgress(percent, stage));
        };
        MergePlanner.validateOutputName(request.outputName);
        MergePlanner.validateInputs(request.paths, request.mods, token);
        Path outputSource = request.paths.modsRoot.resolve(request.outputName);
        Path outputRuntime = request.paths.savedModsRoot.resolve(request.outputName);
        for (Path output : new Path[]{outputSource, outputRuntime}) {
            if (request.mods.stream().anyMatch(m -> FileOps.samePath(output, m.rootPath))) throw new CombineException("The output must not replace an input mod.");
        }
        verifyModDirectoryWritable(request.paths.modsRoot);
        // Resolve the SDK toolchain before anything is moved, so a missing Python never needs a rollback.
        for (String script : new String[]{"CreateNewMod.py", "GenerateMod.py"}) {
            if (!Files.isRegularFile(sdkScript(request, script))) throw new CombineException("WARNO's " + script + " tool is missing. Verify the game files in Steam.");
        }
        String python = PythonLocator.find(request.paths.modsRoot, log);
        CombineResult result;
        try (BuildRecovery.Lock ignored = BuildRecovery.acquireLock(request.paths, request.outputName)) {
            boolean exists = Files.isDirectory(outputSource) || Files.isDirectory(outputRuntime);
            if (rebuild && !exists) throw new CombineException("The existing combined mod is missing. Refresh mods.");
            if (!rebuild && exists) throw new CombineException("The output already exists. Refresh mods and rebuild it instead.");
            ModFingerprintService fingerprints = new ModFingerprintService();
            CombinationHealth health = new CombinationHealth();
            log.accept("[STEP 1/7] Starting " + (rebuild ? "rebuild" : "new merge") + " for '" + request.outputName + "'...");
            for (int i = 0; i < request.mods.size(); i++) {
                ModDescriptor m = request.mods.get(i);
                log.accept("  " + (i == 0 ? "Primary" : "Priority " + (i + 1)) + ": " + m.name + " (" + m.kindLabel() + ", " + m.modGenLabel() + ")");
            }
            report.report(0, "Checking inputs");
            // Every input is brought up to the installed WARNO first: editable mods with WARNO's UpdateMod, Workshop mods
            // made for an older revision by rebasing their databases (see ModRebaser).
            int installedModGen = MergePlanner.installedModGen(request.paths);
            for (ModDescriptor mod : request.mods) {
                if (mod.kind != ModKind.EDITABLE_SOURCE || MergePlanner.isOnCurrentBase(request.paths, mod, token)) continue;
                report.report(1, "Updating " + mod.name + " to the current WARNO");
                log.accept("  " + mod.name + " is based on an older WARNO; updating it with WARNO's UpdateMod...");
                new EditableModUpdater(processRunner).update(request.paths, mod, python, log, token);
            }
            report.report(2, "Bringing mods up to date");
            List<ModRebaser.RebasedMod> rebased = new ArrayList<>();
            List<ModDescriptor> current = MergePlanner.bringUpToDate(request.paths, request.mods, installedModGen, log, token, rebased);
            log.accept("[STEP 2/7] Computing SHA-256 baseline fingerprints for source mods and WARNO base.zip...");
            List<SourceModFingerprint> before = fingerprints.compute(request.mods, null, token);
            String gameHash = health.gameHash(request.paths, token);
            for (SourceModFingerprint f : before) log.accept("  " + f.name + " SHA-256: " + f.fingerprint.substring(0, 12) + "…");
            log.accept("  WARNO ModData\\base.zip SHA-256: " + gameHash.substring(0, 12) + "…");
            List<MergeDecision> sourcePlan = new MergePlanner(deltaAnalyzer).planSourceMerge(request.mods, token);
            BuildRecovery recovery = new BuildRecovery();
            BuildRecovery.Record record = recovery.begin(request.paths, request.outputName);
            try {
                recovery.moveOriginals(request.paths, record);
                if (record.sourceExisted || record.runtimeExisted) log.accept("  Safeguarded existing '" + request.outputName + "' folders to recovery backup.");
                report.report(5, "Creating local mod");
                log.accept("[STEP 3/7] Running WARNO SDK CreateNewMod.py to initialize '" + request.outputName + "'...");
                runSdk(python, request, "CreateNewMod.py", Collections.singletonList(request.outputName), request.paths.modsRoot, log, token);
                if (!Files.isDirectory(outputSource)) throw new CombineException("WARNO did not create the output directory.");
                if (!sourcePlan.isEmpty()) {
                    long deleted = sourcePlan.stream().filter(x -> x.kind == DecisionKind.DELETE).count();
                    log.accept(String.format("  Applying %,d editable source delta file(s) (%,d copied, %,d deleted)...", sourcePlan.size(), sourcePlan.size() - deleted, deleted));
                }
                for (MergeDecision decision : sourcePlan) {
                    token.check();
                    Path destination = FileOps.safeCombine(outputSource, decision.relativePath);
                    if (decision.kind == DecisionKind.DELETE) Files.deleteIfExists(destination);
                    else FileOps.copyFileAtomic(decision.sourcePath, destination, token);
                }
                // WARNO's generator rebuilds the updated mods' own dictionaries and resource packs from their sources.
                Set<String> generatedNames = new TreeSet<>(IgnoreCase.ORDER);
                for (ModRebaser.RebasedMod update : rebased) {
                    generatedNames.addAll(update.ownNames);
                    for (String name : update.ownNames) {
                        for (String folder : new String[]{"Localisation", "ResourcePacks"}) {
                            Path from = update.mod.rootPath.resolve("GameData").resolve(folder).resolve(name);
                            for (Path file : FileOps.files(from))
                                FileOps.copyFileAtomic(file, FileOps.safeCombine(outputSource, "GameData\\" + folder + "\\" + name + "\\" + FileOps.relative(from, file)), token);
                        }
                    }
                }
                if (!generatedNames.isEmpty())
                    log.accept("  WARNO will rebuild the dictionaries and resource packs of the updated mods: " + String.join(", ", generatedNames) + ".");
                report.report(10, "Generating with WARNO");
                log.accept("[STEP 4/7] Running WARNO SDK GenerateMod.py to build local mod identity and Config.ini baseline...");
                runSdk(python, request, "GenerateMod.py", Arrays.asList("WARNO", request.outputName), outputSource, log, token);
                CombineRequest currentRequest = request.withMods(current);
                IniDocument generatedConfig = loadGeneratedConfig(currentRequest, outputRuntime, installedModGen, log, token);
                MergePreview plan;
                if (request.mods.stream().anyMatch(m -> m.kind == ModKind.WORKSHOP_COMPILED)) {
                    List<MergePlanner.CompiledInput> inputs = compiledInputs(currentRequest, outputRuntime, generatedConfig);
                    VanillaDatabases vanilla = VanillaDatabases.open(request.paths.warnoRoot, generatedConfig.getInt("Properties", "ModGenVersion", -1));
                    log.accept(vanilla.available()
                        ? "  Using vanilla databases from " + vanilla.archivePath() + " as the delta baseline."
                        : "  WARNO's vanilla databases were not found (" + vanilla.searched() + "); databases several mods change can't be merged without them.");
                    report.report(20, "Delta merging databases");
                    log.accept("  Planning and delta merging compiled databases: " + inputs.stream().map(x -> x.name).collect(Collectors.joining(" > ")) + "...");
                    Path mergeDirectory = outputSource.resolve(".combine-merged-" + UUID.randomUUID().toString().replace("-", ""));
                    try {
                        // With editable input the output's generation is already an input.
                        MergePlanner.CompiledInput generated = request.mods.stream().anyMatch(m -> m.kind == ModKind.EDITABLE_SOURCE) ? null
                            : new MergePlanner.CompiledInput(request.outputName, outputRuntime, generatedConfig.getSection("Config"));
                        plan = MergePlanner.createCompiledPlan(request.outputName, request.mods, inputs, vanilla, mergeDirectory, token,
                            SourceBaseline.open(request.paths.modDataBaseZip()), false, generated, new ArrayList<>(generatedNames));
                        plan = MergePlanner.withRebaseReport(plan, rebased, MergePlanner.rebaseNotes(rebased, installedModGen));
                        for (String warning : plan.warnings) log.accept("  [NOTE] " + warning);
                        composeCompiledPayload(request, plan, inputs, generatedConfig, outputSource, outputRuntime, log, report, token);
                    } finally {
                        FileOps.deleteTree(mergeDirectory);
                    }
                } else {
                    plan = new MergePreview(request.outputName, request.mods, sourcePlan, Collections.emptyList(), false, null);
                    log.accept(String.format("[STEP 5/7] Verifying %,d merged source file(s) in '%s'...", plan.decisions.size(), outputSource));
                    for (MergeDecision decision : plan.decisions) {
                        token.check();
                        Path actual = FileOps.safeCombine(outputSource, decision.relativePath);
                        if (decision.kind == DecisionKind.DELETE) {
                            if (Files.exists(actual)) throw new CombineException("Deletion verification failed for " + decision.relativePath + ".");
                        } else if (!Files.isRegularFile(actual) || !FileOps.sha256(actual, token).equals(FileOps.sha256(decision.sourcePath, token))) {
                            throw new CombineException("Precedence verification failed for " + decision.relativePath + ".");
                        }
                    }
                    log.accept("[STEP 6/7] Verified generated Config.ini compatibility manifest in '" + outputRuntime + "'.");
                }
                if (!Files.isDirectory(outputSource.resolve("Gen")) || !Files.isDirectory(outputRuntime.resolve("Gen")))
                    throw new CombineException("WARNO generated incomplete output.");
                report.report(92, "Verifying inputs and output");
                log.accept("[STEP 7/7] Verifying output runtime SHA-256, input stability, and saving .warno-moderator.json tracking record...");
                String runtimeHash = health.runtimeHash(outputRuntime, null, token);
                List<SourceModFingerprint> after = fingerprints.compute(request.mods, null, token);
                CombinationHealth.verifyInputs(before, after);
                if (!health.gameHash(request.paths, token).equals(gameHash))
                    throw new CombineException("WARNO build data changed during the merge. Let Steam finish updating, then retry.");
                CombinedModState state = new CombinedModState(CombinedModState.CURRENT_SCHEMA_VERSION, request.outputName, after, runtimeHash, gameHash, null, null);
                token.check();
                new CombinedModStateStore().save(outputSource, state);
                log.accept("  Runtime SHA-256: " + runtimeHash.substring(0, 12) + "… (inputs and WARNO base.zip verified unchanged during build).");
                result = new CombineResult(outputSource, outputRuntime, plan, state);
                recovery.complete(request.paths, record, log);
            } catch (Throwable original) {
                log.accept("[ROLLBACK] Operation stopped; restoring previous outputs independently...");
                try {
                    recovery.restore(request.paths, record, log);
                } catch (RuntimeException restoration) {
                    CombineException failure = new CombineException(original.getMessage() + System.lineSeparator() + restoration.getMessage(), original);
                    failure.addSuppressed(restoration);
                    throw failure;
                }
                if (original instanceof RuntimeException) throw (RuntimeException) original;
                if (original instanceof Error) throw (Error) original;
                throw new UncheckedIOException((IOException) original);
            }
        }
        // Cancellation after the commit cannot turn a completed build into a rollback.
        ActivatedMods.activateCombined(request.paths, request.outputName, request.mods, log);
        log.accept("[OK] Verified " + result.plan.summary() + ". Combination completed successfully.");
        progress.accept(new CombineProgress(100, "Complete"));
        return result;
    }

    private static Path sdkScript(CombineRequest request, String scriptName) {
        return request.paths.modsRoot.resolve("Utils").resolve("Scripts").resolve(scriptName);
    }

    private void runSdk(String python, CombineRequest request, String scriptName, List<String> arguments, Path workingDirectory,
                        Consumer<String> log, Cancel token) {
        token.check();
        List<String> args = new ArrayList<>();
        args.add(sdkScript(request, scriptName).toString());
        args.addAll(arguments);
        int exit = processRunner.run(python, args, workingDirectory, line -> log.accept("  [SDK] " + line), line -> log.accept("  [SDK ERR] " + line), token);
        if (exit != 0) throw new CombineException("WARNO " + scriptName + " failed with exit code " + exit + ".");
    }

    private static void verifyModDirectoryWritable(Path root) {
        Path probe = root.resolve(".warno-moderator-write-" + UUID.randomUUID().toString().replace("-", "") + ".tmp");
        try {
            Files.newByteChannel(probe, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE).close();
        } catch (IOException | SecurityException e) {
            throw new CombineException("WARNO\\Mods is not writable. Check permissions or run the app as administrator.", e);
        }
    }

    private static IniDocument loadGeneratedConfig(CombineRequest request, Path runtime, int installedModGen, Consumer<String> log, Cancel token) {
        Path path = runtime.resolve("Config.ini");
        IniDocument config = Files.exists(path) ? IniDocument.load(path)
            : DeliveryManifest.recover(request, runtime, installedModGen, log, token);
        int version = config.getInt("Properties", "ModGenVersion", -1);
        if (version < 0) throw new CombineException("WARNO generated a manifest without a ModGen revision.");
        for (ModDescriptor input : request.mods) {
            if (input.kind == ModKind.WORKSHOP_COMPILED && (input.modGenVersion == null || input.modGenVersion != version))
                throw new CombineException(input.name + " uses ModGen " + input.modGenVersion + ", but installed WARNO requires " + version + ". Refresh the Workshop subscription.");
        }
        log.accept("  Verified installed WARNO ModGenVersion = " + version + " (matches selected inputs).");
        return config;
    }

    /** Compiled inputs in priority order; editable mods are represented once by WARNO's generation of them. */
    private static List<MergePlanner.CompiledInput> compiledInputs(CombineRequest request, Path runtime, IniDocument generatedConfig) {
        List<MergePlanner.CompiledInput> inputs = new ArrayList<>();
        List<String> editable = request.mods.stream().filter(m -> m.kind == ModKind.EDITABLE_SOURCE).map(m -> m.name).collect(Collectors.toList());
        for (ModDescriptor mod : request.mods) {
            if (mod.kind == ModKind.WORKSHOP_COMPILED) inputs.add(new MergePlanner.CompiledInput(mod.name, mod.rootPath, mod.configKeys));
            else if (mod.name.equals(editable.get(0)))
                inputs.add(new MergePlanner.CompiledInput(editable.size() == 1 ? mod.name : "Editable (" + String.join(", ", editable) + ")",
                    runtime, generatedConfig.getSection("Config")));
        }
        return inputs;
    }

    private void composeCompiledPayload(CombineRequest request, MergePreview plan, List<MergePlanner.CompiledInput> inputs,
                                               IniDocument generatedConfig, Path source, Path runtime, Consumer<String> log, Reporter report, Cancel token) {
        log.accept("[STEP 5/7] Composing compiled payload (" + plan.summary() + ")...");
        Path staging = source.resolve(".combine-staging-" + UUID.randomUUID().toString().replace("-", ""));
        int total = plan.decisions.size();
        String[] hashes = new String[total];
        // The plan is the only source of file winners. Stage and verify before replacing generated inputs.
        forEachFile(total, token, i -> {
            MergeDecision decision = plan.decisions.get(i);
            Path staged = FileOps.safeCombine(staging, decision.relativePath);
            String copied = FileOps.copyAndHash(decision.sourcePath, staged, token);
            hashes[i] = FileOps.sha256(staged, token);
            if (!hashes[i].equals(copied)) throw new CombineException("Precedence verification failed for " + decision.relativePath + ".");
        }, done -> report.report(40 + 25 * done / Math.max(1, total), String.format("Staging files · %,d of %,d", done, total)));
        log.accept(String.format("  Staged and SHA-256 verified all %,d compiled payload files.", total));

        int restamped = restampTags(plan, inputs, staging, source, hashes, token);
        log.accept(String.format("  Re-stamped %,d generation tag(s) with the combined mod's own source hashes, so WARNO loads its databases as built.", restamped));
        synthesizeConfig(request, plan, inputs, generatedConfig);
        for (Path root : new Path[]{source, runtime}) {
            token.check();
            FileOps.deleteTree(root.resolve("Gen"));
        }
        // Each file goes to both outputs and is checked against the hash taken while staging.
        forEachFile(total, token, i -> {
            MergeDecision decision = plan.decisions.get(i);
            Path staged = FileOps.safeCombine(staging, decision.relativePath);
            for (Path root : new Path[]{source, runtime}) {
                Path destination = FileOps.safeCombine(root, decision.relativePath);
                FileOps.copyFileAtomic(staged, destination, token);
                if (!FileOps.sha256(destination, token).equals(hashes[i]))
                    throw new CombineException("Deployment verification failed for " + decision.relativePath + ".");
            }
        }, done -> report.report(65 + 20 * done / Math.max(1, total), String.format("Installing files · %,d of %,d", done, total)));
        log.accept("  Deployed verified payload to Mods\\" + request.outputName + " and Saved Games\\...\\mod\\" + request.outputName + ".");
        if (plan.decisions.stream().anyMatch(d -> NdfLayout.isTransactionDatabase(d.relativePath))) {
            report.report(85, "Validating combined databases with WARNO");
            log.accept("  Loading the installed combination through WARNO's headless SDK...");
            List<Path> depictionFiles = List.of(source.resolve("Gen/NDF/GFX/DepictionResources.txn"),
                source.resolve("Gen/NDF/GFX/DepictionResources.dbg"), source.resolve("Gen/NDF/GFX/DepictionResources.txn.tag"));
            boolean rebuildDepictions = depictionsNeedRebuild(plan);
            if (rebuildDepictions) {
                // DepictionResources' ConfigHash covers the depiction cluster's ordered file list, which a source hash
                // restamp cannot recompute. Unless one mod supplies both unmerged, only WARNO can produce a matching database.
                log.accept("  The depiction cluster and DepictionResources don't come from one mod; WARNO will rebuild DepictionResources.");
                for (Path file : depictionFiles) {
                    try { Files.deleteIfExists(file); }
                    catch (IOException ex) { throw new UncheckedIOException(ex); }
                }
            }
            List<String> firstPass = new ArrayList<>();
            loadCombinedTransactions(request, source, line -> {
                log.accept(line);
                String database = regeneratedDatabase(line);
                if (database != null) firstPass.add(database);
            }, token);
            if (rebuildDepictions) {
                for (Path file : depictionFiles) {
                    if (!Files.isRegularFile(file) || file.toFile().length() == 0)
                        throw new CombineException("WARNO did not regenerate " + file.getFileName() + " for the combined depiction cluster.");
                }
            }
            if (!firstPass.isEmpty())
                log.accept(String.format("  WARNO rebuilt %,d database(s) from the combined sources: %s", firstPass.size(), String.join(", ", firstPass)));
            // The game refuses to start ("No writable component found") while any database reads as stale,
            // so a second load has to reuse every database exactly as it will ship.
            log.accept("  Checking that WARNO reuses every combined database on a second load...");
            List<String> secondPass = new ArrayList<>();
            loadCombinedTransactions(request, source, line -> {
                log.accept(line);
                String database = regeneratedDatabase(line);
                if (database != null) secondPass.add(database);
            }, token);
            if (!secondPass.isEmpty())
                throw new CombineException("WARNO still treats these combined databases as out of date, so the game would refuse to load the mod: "
                    + String.join(", ", secondPass) + ".");
            log.accept("  WARNO loaded every combined database without rebuilding anything.");
            for (Path generated : FileOps.files(source.resolve("Gen/NDF"))) {
                Path installed = runtime.resolve(source.relativize(generated));
                if (!Files.isRegularFile(installed) || !FileOps.sameContent(generated, installed, token)) {
                    FileOps.copyFileAtomic(generated, installed, token);
                    if (!FileOps.sameContent(generated, installed, token)) throw new CombineException("SDK deployment verification failed for " + installed);
                }
            }
        }
        report.report(85, "Writing compatibility manifest");
        Path configPath = runtime.resolve("Config.ini");
        DeliveryManifest.complete(generatedConfig, runtime, DeliveryManifest.revisions(request.paths.warnoRoot));
        generatedConfig.save(configPath);
        IniDocument saved = IniDocument.load(configPath);
        TreeMap<String, String> configSection = generatedConfig.getSection("Config");
        for (Map.Entry<String, String> entry : configSection.entrySet()) {
            if (!entry.getValue().equals(saved.get("Config", entry.getKey()))) throw new CombineException("Manifest verification failed for " + entry.getKey() + ".");
        }
        log.accept(String.format("[STEP 6/7] Wrote and verified Config.ini manifest (%,d NDF database fingerprints, DeckFormatVersion %s, Tags: %s).",
            configSection.size(), saved.get("Properties", "DeckFormatVersion"), saved.get("Properties", "TagList")));
        FileOps.deleteTree(staging);
    }

    private static final java.util.regex.Pattern GENERATING =
        java.util.regex.Pattern.compile("Generating:\\s*'(ZZ:/NDF/[^']+)'", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** The database a WARNO log line says it is rebuilding, or null. */
    static String regeneratedDatabase(String line) {
        var match = GENERATING.matcher(line);
        return match.find() ? match.group(1) : null;
    }

    /**
     * False only when neither file ships, or one mod supplies both the depiction cluster and DepictionResources unmerged:
     * the only case where the shipped DepictionResources tag still matches the combined cluster.
     */
    static boolean depictionsNeedRebuild(MergePreview plan) {
        MergeDecision cluster = null, resources = null;
        for (MergeDecision decision : plan.decisions) {
            if (decision.relativePath.equalsIgnoreCase("Gen\\NDF\\DepictionResource\\Cluster.txn")) cluster = decision;
            else if (decision.relativePath.equalsIgnoreCase("Gen\\NDF\\GFX\\DepictionResources.txn")) resources = decision;
        }
        if (cluster == null && resources == null) return false;
        if (cluster == null || resources == null) return true;
        if (cluster.kind == DecisionKind.MERGED || resources.kind == DecisionKind.MERGED) return true;
        return !cluster.winner.equals(resources.winner);
    }

    private void loadCombinedTransactions(CombineRequest request, Path source, Consumer<String> log, Cancel token) {
        int exit = processRunner.run(request.paths.warnoRoot.resolve("WARNO.exe").toString(),
            List.of("-headless", "-generatemod", request.outputName, "-rootdata", ".", "CommonData:Clusters/Bootstrap/ClusterBootstrapGeneration.ndf"),
            source, log, log, token);
        if (exit != 0) throw new CombineException("WARNO rejected the combined databases (exit code " + exit + ").");
    }

    /** Runs a file operation over many files on a few threads (disk-bound work), reporting progress as it goes. */
    private static void forEachFile(int count, Cancel token, IntConsumer work, IntConsumer progress) {
        AtomicInteger done = new AtomicInteger();
        int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 2));
        Parallel.forEach(count, threads, i -> {
            token.check();
            work.accept(i);
            int finished = done.incrementAndGet();
            if (finished % 64 == 0 || finished == count) progress.accept(finished);
        });
    }

    /**
     * Re-stamps every staged tag that lists source hashes (see {@link GenerationTag}) so it lists every source any
     * input's tag lists for that file, each with the hash of the source the combined mod contains: a source the plan
     * placed comes from staging, any other from the output's own sources (WARNO's vanilla base and the editable
     * inputs). Returns how many tags were re-stamped.
     */
    private static int restampTags(MergePreview plan, List<MergePlanner.CompiledInput> inputs, Path staging, Path source, String[] hashes, Cancel token) {
        TreeMap<String, Path> placed = IgnoreCase.map();
        for (MergeDecision d : plan.decisions) placed.put(d.relativePath, FileOps.safeCombine(staging, d.relativePath));
        // A source the combined mod doesn't contain is read from WARNO's own data, as the tags already record it.
        java.util.function.Function<String, Path> sourceFile = sourcePath -> {
            String relative = GenerationTag.modRelativePath(sourcePath);
            Path staged = placed.get(relative);
            if (staged != null) return staged;
            Path own = FileOps.safeCombine(source, relative);
            return Files.isRegularFile(own) ? own : null;
        };
        int restamped = 0;
        for (int i = 0; i < plan.decisions.size(); i++) {
            token.check();
            String relativePath = plan.decisions.get(i).relativePath;
            if (!relativePath.toLowerCase().endsWith(".tag")) continue;
            Path staged = FileOps.safeCombine(staging, relativePath);
            byte[] bytes = FileOps.read(staged);
            if (!GenerationTag.hasSourceHashes(bytes)) continue;
            // Every source any combined mod's tag lists for this file, the chosen tag's first.
            List<byte[]> inputTags = new ArrayList<>();
            for (MergePlanner.CompiledInput input : inputs) {
                Path tag = input.root.resolve(relativePath);
                if (!Files.isRegularFile(tag)) continue;
                byte[] data = FileOps.read(tag);
                if (GenerationTag.hasSourceHashes(data)) inputTags.add(data);
            }
            TreeSet<String> seen = IgnoreCase.set();
            List<String> sources = new ArrayList<>();
            for (String s : GenerationTag.sources(bytes)) if (seen.add(s)) sources.add(s);
            for (byte[] tag : inputTags) for (String s : GenerationTag.sources(tag)) if (seen.add(s)) sources.add(s);
            TreeMap<String, byte[]> recorded = IgnoreCase.map();
            for (byte[] tag : inputTags) for (Map.Entry<String, byte[]> e : GenerationTag.hashes(tag).entrySet()) recorded.putIfAbsent(e.getKey(), e.getValue());
            FileOps.write(staged, GenerationTag.restamp(bytes, sources, sourceFile, recorded));
            hashes[i] = FileOps.sha256(staged, token);
            restamped++;
        }
        return restamped;
    }

    private static void synthesizeConfig(CombineRequest request, MergePreview plan, List<MergePlanner.CompiledInput> inputs, IniDocument output) {
        int installed = output.getInt("Properties", "ModGenVersion", -1);
        // WARNO's fingerprint is the MD5 of the vanilla database a mod's database was built from, and the game
        // reports a mod as out of date when it differs from the installed one. Every input is brought up to the
        // installed revision before merging, so the output's fingerprints are the installed game's, never an input's.
        Map<String, String> vanilla = BuildRevision.vanillaFingerprints(request.paths.warnoRoot, installed);
        if (vanilla.isEmpty()) throw new CombineException("WARNO's databases for ModGen " + installed + " weren't found, so the combined manifest can't be written.");
        for (MergeDecision decision : plan.decisions) {
            if (!NdfLayout.isDatabase(decision.relativePath)) continue;
            String key = NdfLayout.manifestKey(decision.relativePath);
            String current = vanilla.get(key.toLowerCase());
            if (current != null) {
                output.set("Config", key, current);
                continue;
            }
            // WARNO's fingerprint identifies the database revision rather than its content, so a merged database takes
            // the entry of an input that ships it, preferring the decision's winner.
            List<String> winners = Arrays.asList(decision.winner.split(" \\+ "));
            List<MergePlanner.CompiledInput> providers = new ArrayList<>();
            for (MergePlanner.CompiledInput i : inputs) if (Files.isRegularFile(i.root.resolve(decision.relativePath))) providers.add(i);
            providers.sort((a, b) -> Boolean.compare(winners.contains(b.name), winners.contains(a.name)));
            String fingerprint = null;
            for (MergePlanner.CompiledInput p : providers) {
                String value = p.configKeys.get(key);
                if (value != null && !value.trim().isEmpty()) {
                    fingerprint = value;
                    break;
                }
            }
            if (fingerprint == null) {
                // WARNO never fingerprints per-mod-name databases (Localisation/<Mod>, ResourcePacks/<Mod>), so the inputs'
                // copies and the output's own have no manifest entry to carry over.
                if (isInputNamedDatabase(key)) continue;
                // Some mods (RadioWarszawa, for one) publish a manifest with no [Config] fingerprints at all; the output
                // then carries none for their databases either, as the mod itself does.
                if (!providers.isEmpty() && providers.stream().allMatch(p -> p.configKeys.isEmpty())) continue;
                throw new CombineException(decision.winner + "'s selected database " + decision.relativePath + " has no matching manifest fingerprint (" + key + "). Refresh or regenerate that input.");
            }
            output.set("Config", key, fingerprint);
        }
        output.set("Properties", "Name", request.outputName);
        Set<String> tags = new TreeSet<>(IgnoreCase.ORDER);
        for (ModDescriptor m : request.mods) tags.addAll(m.tags);
        output.set("Properties", "TagList", String.join(",", new LinkedHashSet<>(tags)));
        output.set("Properties", "ID", "0");
        int deck = output.getInt("Properties", "DeckFormatVersion", 0);
        for (ModDescriptor m : request.mods) deck = Math.max(deck, m.deckFormatVersion);
        output.set("Properties", "DeckFormatVersion", Integer.toString(deck));
    }

    private static boolean isInputNamedDatabase(String key) {
        int slash = key.indexOf('/');
        if (slash < 0) return false;
        String folder = key.substring(0, slash);
        // Mods ship extra per-mod databases such as Localisation/<Mod>_test, so any database in these folders is
        // accepted when unfingerprinted.
        return folder.equalsIgnoreCase("Localisation") || folder.equalsIgnoreCase("ResourcePacks");
    }
}
