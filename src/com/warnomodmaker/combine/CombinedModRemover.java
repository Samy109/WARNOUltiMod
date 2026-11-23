package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.IgnoreCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Deletes a combined mod everywhere it lives: Mods\&lt;Name&gt;, Saved Games\...\mod\&lt;Name&gt;, and the
 * "&lt;Name&gt;.warno-moderator-incomplete-*" and "-backup-*" folders interrupted builds leave next to them. Only
 * outputs this app (or WARNO UltiAI MODerator) created carry the tracking record, so an input mod never is.
 */
public final class CombinedModRemover {
    private static final String[] LEFTOVER_MARKERS = {".warno-moderator-incomplete-", ".warno-moderator-backup-"};

    /** What deleting a combined mod removes: its two output folders and the leftovers of earlier builds. */
    public static final class Targets {
        public final String outputName;
        public final List<Path> folders;

        public Targets(String outputName, List<Path> folders) {
            this.outputName = outputName;
            this.folders = folders;
        }
    }

    public Targets find(WarnoPaths paths, String outputName) {
        MergePlanner.validateOutputName(outputName);
        Path source = paths.modsRoot.resolve(outputName);
        if (Files.isDirectory(source) && new CombinedModStateStore().tryLoad(source) == null)
            throw new CombineException(source + " wasn't created by the combiner, so it won't be deleted.");
        if (new BuildRecovery().find(paths).stream().anyMatch(r -> r.outputName.equalsIgnoreCase(outputName)))
            throw new CombineException("An interrupted build of '" + outputName + "' still needs recovery. Refresh mods to recover it first.");
        List<Path> folders = new ArrayList<>();
        for (Path root : new Path[]{paths.modsRoot, paths.savedModsRoot}) {
            if (!Files.isDirectory(root)) continue;
            Path output = root.resolve(outputName);
            if (Files.isDirectory(output)) folders.add(output);
            for (Path dir : FileOps.directories(root)) {
                String name = dir.getFileName().toString();
                for (String marker : LEFTOVER_MARKERS) {
                    if (name.regionMatches(true, 0, outputName + marker, 0, outputName.length() + marker.length())) {
                        folders.add(dir);
                        break;
                    }
                }
            }
        }
        if (folders.isEmpty()) throw new CombineException("Nothing named '" + outputName + "' was found to delete.");
        return new Targets(outputName, folders);
    }

    /** Leftovers of interrupted builds of any combined mod, except those a pending recovery still needs. */
    public List<Targets> findLeftovers(WarnoPaths paths) {
        Set<String> pending = IgnoreCase.set();
        for (BuildRecovery.Record r : new BuildRecovery().find(paths)) pending.add(r.outputName);
        Map<String, List<Path>> found = new LinkedHashMap<>();
        Map<String, String> names = IgnoreCase.map();
        for (Path root : new Path[]{paths.modsRoot, paths.savedModsRoot}) {
            for (Path dir : FileOps.directories(root, "*.warno-moderator-*")) {
                String name = dir.getFileName().toString();
                int marker = -1;
                for (String m : LEFTOVER_MARKERS) marker = Math.max(marker, name.toLowerCase().indexOf(m));
                if (marker <= 0) continue;
                String output = name.substring(0, marker);
                if (pending.contains(output)) continue;
                String key = names.computeIfAbsent(output, k -> k);
                found.computeIfAbsent(key, k -> new ArrayList<>()).add(dir);
            }
        }
        List<Targets> result = new ArrayList<>();
        for (Map.Entry<String, List<Path>> e : found.entrySet()) result.add(new Targets(e.getKey(), e.getValue()));
        return result;
    }

    public void delete(WarnoPaths paths, Targets files, Consumer<String> log) {
        try (BuildRecovery.Lock ignored = BuildRecovery.acquireLock(paths, files.outputName)) {
            for (Path folder : files.folders) {
                if (!Files.isDirectory(folder)) continue;
                FileOps.deleteTree(folder);
                log.accept("Deleted " + folder);
            }
            if (!Files.isDirectory(paths.modsRoot.resolve(files.outputName)) && !Files.isDirectory(paths.savedModsRoot.resolve(files.outputName)))
                ActivatedMods.deactivate(paths, files.outputName, log);
        }
    }
}
