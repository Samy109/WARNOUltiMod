package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.CombineProgress;
import com.warnomodmaker.combine.Models.CombinedModState;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.ModKind;
import com.warnomodmaker.combine.Models.SourceModFingerprint;
import com.warnomodmaker.combine.Models.WarnoPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Whether a combined mod's output still matches what was built. */
public final class CombinationHealth {
    public String runtimeHash(Path root, Consumer<CombineProgress> progress, Cancel token) {
        ModDescriptor descriptor = new ModDescriptor("Combined output", root, ModKind.WORKSHOP_COMPILED, null, null, 0,
            Collections.emptyList(), Collections.emptyMap());
        return new ModFingerprintService().compute(Collections.singletonList(descriptor), progress, token, "Checking combined output").get(0).fingerprint;
    }

    public String gameHash(WarnoPaths paths, Cancel token) {
        return FileOps.sha256Cached(paths.modDataBaseZip(), token);
    }

    public String check(WarnoPaths paths, CombinedModState state, Consumer<CombineProgress> progress, Cancel token) {
        Path runtime = paths.savedModsRoot.resolve(state.outputName);
        if (!Files.isRegularFile(runtime.resolve("Config.ini")) || !Files.isDirectory(runtime.resolve("Gen"))
            || !Files.isDirectory(paths.modsRoot.resolve(state.outputName).resolve("Gen")))
            return "Combined output is missing or incomplete. Rebuild required.";
        if (state.runtimeFingerprint == null || state.gameFingerprint == null) return "Rebuild once to verify and track the existing output.";
        if (!state.gameFingerprint.equalsIgnoreCase(gameHash(paths, token))) return "WARNO build data changed. Rebuild required.";
        if (!state.runtimeFingerprint.equalsIgnoreCase(runtimeHash(runtime, progress, token))) return "Combined output changed. Rebuild required.";
        return "Combined output matches the last merge. You can rebuild again.";
    }

    public static void verifyInputs(List<SourceModFingerprint> before, List<SourceModFingerprint> after) {
        boolean same = before.size() == after.size();
        for (int i = 0; same && i < before.size(); i++) same = CombinedModStateStore.fingerprintMatches(before.get(i), after.get(i));
        if (!same) throw new CombineException("Source mods changed during the merge. Let Steam finish downloading, then retry.");
    }
}
