package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.WarnoPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Brings an editable mod based on an older WARNO up to the installed one with WARNO's own UpdateMod.py, which
 * three-way merges every source file (old base, new base, the mod) with its diff3. Where WARNO's update and the mod
 * changed the same lines, UpdateMod would stop for a manual merge; here the mod's lines are kept, as for compiled
 * mods, and the number of such places is reported. UpdateMod keeps its own GameData_backup and CommonData_backup.
 */
public final class EditableModUpdater {
    private static final String RESOLVED = "MODERATOR-RESOLVED ";
    private static final String DRIVER = String.join("\n",
        "import builtins, os, sys",
        "scripts = sys.argv[1]",
        "sys.path.insert(0, scripts)",
        "import diff3",
        "import UpdateMod",
        "resolved = [0, 0]",
        "_merge = diff3.merge",
        "def merge(new, old, mod):",
        "    result = _merge(new, old, mod)",
        "    if result['conflict'] == 0:",
        "        return result",
        "    body, state = [], None",
        "    for line in result['body']:",
        "        if line == '<<<<<<<\\n': state = 'new'; continue",
        "        if state and line == '|||||||\\n': state = 'old'; continue",
        "        if state and line == '=======\\n': state = 'mod'; continue",
        "        if state and line == '>>>>>>>\\n': state = None; continue",
        "        if state in (None, 'mod'): body.append(line)",
        "    resolved[0] += 1",
        "    resolved[1] += result['conflict']",
        "    return {'conflict': 0, 'body': body}",
        "diff3.merge = merge",
        "builtins.input = lambda *args: ''",
        "UpdateMod.UpdateMod()",
        "print('" + RESOLVED + "%d %d' % (resolved[0], resolved[1]))",
        "");

    private final ProcessRunner processRunner;

    public EditableModUpdater(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /** Updates the mod in place; returns how many files had changes both WARNO and the mod made. */
    public int update(WarnoPaths paths, ModDescriptor mod, String python, Consumer<String> log, Cancel token) {
        Path scripts = paths.modsRoot.resolve("Utils").resolve("Scripts");
        if (!Files.isRegularFile(scripts.resolve("UpdateMod.py")) || !Files.isRegularFile(scripts.resolve("diff3.py")))
            throw new CombineException("WARNO's UpdateMod.py tool is missing. Verify the game files in Steam.");
        int[] files = {0};
        Path driver;
        try {
            driver = Files.createTempFile("warno-moderator-updatemod-", ".py");
            Files.write(driver, DRIVER.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            // The count is read from the raw output; MODerator read it after the log prefix was added, so it was always 0.
            int exit = processRunner.run(python, Arrays.asList(driver.toString(), scripts.toString()), mod.rootPath, line -> {
                if (line.startsWith(RESOLVED)) files[0] = Integer.parseInt(line.split(" ")[1]);
                else log.accept("  [UpdateMod] " + line);
            }, line -> log.accept("  [UpdateMod ERR] " + line), token);
            if (exit != 0)
                throw new CombineException("Updating " + mod.name + " to the current WARNO failed (UpdateMod exited with " + exit + "); its sources were restored from GameData_backup.");
        } finally {
            try {
                Files.deleteIfExists(driver);
            } catch (IOException ignored) {
                // A temporary file.
            }
        }
        if (!MergePlanner.isOnCurrentBase(paths, mod, token))
            throw new CombineException("Updating " + mod.name + " to the current WARNO didn't finish; it is still on an older base.");
        log.accept("  " + mod.name + " is now based on the current WARNO" + (files[0] > 0
            ? String.format("; in %,d file(s) it and WARNO's update changed the same lines, and the mod's lines were kept.", files[0]) : "."));
        return files[0];
    }
}
