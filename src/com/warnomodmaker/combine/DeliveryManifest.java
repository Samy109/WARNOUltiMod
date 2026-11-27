package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.CombineRequest;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.ndf.NdfLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Builds the delivery manifest from the databases the SDK actually delivered. */
final class DeliveryManifest {
    private DeliveryManifest() {}

    static Map<String, String> revisions(Path game) {
        Path file = game.resolve("ModRevisionList.ini");
        if (!Files.isRegularFile(file)) throw new CombineException("Missing WARNO revision list: " + file);
        var revisions = IniDocument.load(file).getSection("Config");
        if (revisions.isEmpty()) throw new CombineException("No database revisions in " + file);
        return revisions;
    }

    static String problem(IniDocument manifest, Map<String, String> revisions, Path runtime) {
        for (var entry : manifest.getSection("Config").entrySet()) {
            String current = revisions.get(entry.getKey());
            if (current == null || !current.equalsIgnoreCase(entry.getValue()))
                return "Database " + entry.getKey() + " declares " + entry.getValue() + ", but installed WARNO "
                    + (current == null ? "does not list it." : "requires " + current + ".");
        }
        for (Path file : FileOps.files(runtime.resolve("Gen/NDF"))) {
            String relative = FileOps.relative(runtime, file);
            if (!NdfLayout.isDatabase(relative)) continue;
            String key = NdfLayout.manifestKey(relative);
            if (revisions.containsKey(key) && manifest.get("Config", key) == null)
                return "The combined manifest is missing the revision for " + key + ".";
        }
        return null;
    }

    static void complete(IniDocument manifest, Path runtime, Map<String, String> revisions) {
        for (Path file : FileOps.files(runtime.resolve("Gen/NDF"))) {
            String relative = FileOps.relative(runtime, file);
            if (!NdfLayout.isDatabase(relative)) continue;
            String key = NdfLayout.manifestKey(relative);
            if (revisions.containsKey(key) && manifest.get("Config", key) == null)
                manifest.set("Config", key, revisions.get(key));
        }
        String problem = problem(manifest, revisions, runtime);
        if (problem != null) throw new CombineException(problem + " Check the source mod revision before rebuilding.");
    }

    static IniDocument recover(CombineRequest request, Path runtime, int version, Consumer<String> log, Cancel token) {
        Path source = request.paths.modsRoot.resolve(request.outputName);
        boolean dictionary = false, resources = false;
        for (Path file : FileOps.files(source.resolve("Gen"))) {
            token.check();
            String relative = FileOps.relative(source, file);
            Path installed = runtime.resolve(relative);
            if (!Files.isRegularFile(installed) || !FileOps.sameContent(file, installed, token))
                throw new CombineException("SDK delivery is incomplete at " + installed + ". Expected manifest: " + runtime.resolve("Config.ini"));
            if (!NdfLayout.isDatabase(relative) || file.toFile().length() == 0) continue;
            String key = NdfLayout.manifestKey(relative);
            dictionary |= key.equalsIgnoreCase("Localisation/" + request.outputName);
            resources |= key.equalsIgnoreCase("ResourcePacks/" + request.outputName);
        }
        if (!dictionary || !resources)
            throw new CombineException("SDK delivery is missing the generated databases for '" + request.outputName + "' at " + runtime + ".");
        IniDocument manifest = new IniDocument();
        manifest.set("Properties", "Name", request.outputName);
        manifest.set("Properties", "ID", CombineService.combinedModId(request.paths, request.outputName));
        manifest.set("Properties", "Version", "0");
        manifest.set("Properties", "PreviewImagePath", "");
        var tags = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        int deck = 0;
        for (ModDescriptor mod : request.mods) {
            tags.addAll(mod.tags);
            deck = Math.max(deck, mod.deckFormatVersion);
        }
        manifest.set("Properties", "TagList", String.join(",", tags));
        manifest.set("Properties", "DeckFormatVersion", Integer.toString(deck));
        manifest.set("Properties", "ModGenVersion", Integer.toString(version));
        complete(manifest, runtime, revisions(request.paths.warnoRoot));
        manifest.save(runtime.resolve("Config.ini"));
        log.accept("  Recovered missing Config.ini from verified SDK delivery and installed WARNO revisions.");
        return manifest;
    }
}
