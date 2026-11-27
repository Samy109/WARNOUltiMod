package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.ModKind;
import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.ndf.IgnoreCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Lists the installed mods: editable SDK mods under WARNO\Mods and the Steam Workshop items. */
public final class ModScanner {
    private static final Set<String> RESERVED = IgnoreCase.set();

    static {
        RESERVED.addAll(Arrays.asList("ModData", "Utils", "ExampleAssets"));
    }

    public List<ModDescriptor> scan(WarnoPaths paths, Cancel token) {
        List<ModDescriptor> mods = new ArrayList<>();
        scanEditable(paths, mods, token);
        scanWorkshop(paths, mods, token);
        mods.sort((a, b) -> IgnoreCase.ORDER.compare(a.name, b.name));
        return mods;
    }

    private static void scanEditable(WarnoPaths paths, List<ModDescriptor> mods, Cancel token) {
        for (Path directory : FileOps.directories(paths.modsRoot)) {
            token.check();
            String name = directory.getFileName().toString();
            String lower = name.toLowerCase();
            if (RESERVED.contains(name)
                || lower.contains(".warno-moderator-backup-")
                || lower.contains(".warno-moderator-incomplete-")
                || !Files.isRegularFile(directory.resolve("base.zip"))
                || !Files.isDirectory(directory.resolve("GameData"))
                || !Files.isDirectory(directory.resolve("CommonData")))
                continue;
            mods.add(descriptor(name, directory, ModKind.EDITABLE_SOURCE, null, bestConfig(paths, name, directory)));
        }
    }

    private static void scanWorkshop(WarnoPaths paths, List<ModDescriptor> mods, Cancel token) {
        for (Path directory : FileOps.directories(paths.workshopRoot)) {
            token.check();
            Path configPath = directory.resolve("Config.ini");
            if (!Files.isRegularFile(configPath)) continue;
            try {
                IniDocument config = IniDocument.load(configPath);
                String name = config.get("Properties", "Name");
                if (name == null) name = directory.getFileName().toString();
                mods.add(descriptor(name, directory, ModKind.WORKSHOP_COMPILED, directory.getFileName().toString(), config));
            } catch (UncheckedIOException e) {
                // Ignore a Workshop item that Steam is currently replacing.
            }
        }
    }

    private static IniDocument bestConfig(WarnoPaths paths, String name, Path root) {
        for (Path path : new Path[]{paths.savedModsRoot.resolve(name).resolve("Config.ini"), root.resolve("Config.ini")}) {
            if (Files.isRegularFile(path)) return IniDocument.load(path);
        }
        return null;
    }

    private static ModDescriptor descriptor(String name, Path root, ModKind kind, String workshopId, IniDocument config) {
        List<String> tags = new ArrayList<>();
        if (config != null && config.get("Properties", "TagList") != null) {
            for (String tag : config.get("Properties", "TagList").split(",")) if (!tag.trim().isEmpty()) tags.add(tag.trim());
        }
        int modGen = config != null ? config.getInt("Properties", "ModGenVersion", -1) : -1;
        return new ModDescriptor(name, root, kind, workshopId, modGen >= 0 ? Integer.valueOf(modGen) : readGenVersion(root),
            config != null ? config.getInt("Properties", "DeckFormatVersion", 0) : 0, tags,
            config != null ? config.getSection("Config") : IgnoreCase.map());
    }

    private static Integer readGenVersion(Path root) {
        Path file = root.resolve("Gen").resolve("Version.ndf");
        if (!Files.isRegularFile(file)) return null;
        try {
            String digits = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replaceAll("[^0-9]", "");
            return digits.isEmpty() || digits.length() > 9 ? null : Integer.valueOf(digits);
        } catch (IOException e) {
            return null;
        }
    }
}
