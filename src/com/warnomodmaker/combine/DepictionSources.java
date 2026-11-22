package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.DecisionKind;
import com.warnomodmaker.combine.Models.MergeDecision;
import com.warnomodmaker.combine.ndf.IgnoreCase;
import com.warnomodmaker.combine.ndf.NdfDatabase;
import com.warnomodmaker.combine.ndf.NdfRewrite;
import com.warnomodmaker.combine.ndf.NdfValue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reconciles the source files loaded together by the merged depiction-resource transaction. */
final class DepictionSources {
    private final Set<String> loaded = IgnoreCase.set();
    private final Set<String> aliases = IgnoreCase.set();
    private static final Pattern MESH = Pattern.compile(
        "export\\s+(\\w+)\\s+is\\s+TResourceMesh\\s*\\(\\s*Mesh\\s*=\\s*(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*')\\s*\\)");

    NdfDatabase collect(NdfDatabase cluster) {
        return new NdfRewrite(null, null, obj -> {
            if (!obj.className.equals("TNDFTransaction") ||
                !(obj.get("NameSpace") instanceof NdfValue.Text ns) ||
                !ns.text.equalsIgnoreCase("GFX/DepictionResources") ||
                !(obj.get("Files") instanceof NdfValue.ListV list)) return obj;
            Set<String> seen = IgnoreCase.set();
            List<NdfValue> unique = new ArrayList<>();
            for (NdfValue item : list.items) {
                if (item instanceof NdfValue.Text path && path.text.regionMatches(true, 0, "GameData:/", 0, 10)) {
                    String relative = path.text.replace(":/", "/").replace('/', '\\');
                    loaded.add(relative);
                    if (!seen.add(relative)) {
                        aliases.add(relative);
                        continue;
                    }
                }
                unique.add(item);
            }
            if (unique.size() == list.items.size()) return obj;
            var fields = new ArrayList<>(obj.fields);
            fields.replaceAll(f -> f.getKey().equals("Files") ? NdfValue.field("Files", new NdfValue.ListV(unique)) : f);
            return new NdfValue.Obj(obj.className, fields, obj.origin);
        }).apply(cluster);
    }

    // Only rewrite files consisting entirely of self-contained mesh exports. Other NDF may
    // contain local references or comments and must retain its normal file-level precedence.
    private static Map<String, String> meshes(Path file) {
        String text = new String(FileOps.read(file), StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
        Matcher matcher = MESH.matcher(text);
        Map<String, String> result = new LinkedHashMap<>();
        int end = 0;
        while (matcher.find()) {
            if (!text.substring(end, matcher.start()).isBlank()) return Map.of();
            if (result.put(matcher.group(1), matcher.group()) != null) return Map.of();
            end = matcher.end();
        }
        return text.substring(end).isBlank() ? result : Map.of();
    }

    private record Definition(int rank, String path, String text) {}

    void resolve(List<MergeDecision> decisions, List<TreeMap<String, Path>> files, Path output, Cancel token) {
        if (loaded.isEmpty()) return;
        Map<String, Definition> owners = new HashMap<>();
        Map<Integer, Map<String, Definition>> byFile = new LinkedHashMap<>();
        Map<Integer, Set<String>> originalNames = new HashMap<>();
        for (int i = 0; i < decisions.size(); i++) {
            MergeDecision decision = decisions.get(i);
            if (!loaded.contains(decision.relativePath) || decision.sourcePath == null) continue;
            token.check();
            Map<String, String> original = meshes(decision.sourcePath);
            if (original.isEmpty()) continue;
            Map<String, Definition> definitions = new LinkedHashMap<>();
            originalNames.put(i, original.keySet());
            for (int rank = 0; rank < files.size(); rank++) {
                Path provider = files.get(rank).get(decision.relativePath);
                if (provider == null) continue;
                boolean winner = provider.equals(decision.sourcePath);
                if (!winner && !aliases.contains(decision.relativePath)) continue;
                var declarations = winner ? original : meshes(provider);
                for (var entry : declarations.entrySet()) {
                    if (!winner && original.containsKey(entry.getKey())) continue;
                    var def = new Definition(rank, decision.relativePath, entry.getValue());
                    definitions.putIfAbsent(entry.getKey(), def);
                    owners.merge(entry.getKey(), def, (a, b) -> a.rank <= b.rank ? a : b);
                }
            }
            byFile.put(i, definitions);
        }
        for (var file : byFile.entrySet()) {
            int index = file.getKey();
            MergeDecision decision = decisions.get(index);
            Map<String, Definition> kept = file.getValue();
            kept.entrySet().removeIf(e -> !owners.get(e.getKey()).path.equals(decision.relativePath));
            if (kept.keySet().equals(originalNames.get(index))) continue;
            Path dest = output == null ? null : FileOps.safeCombine(output, decision.relativePath);
            if (dest != null) {
                StringBuilder text = new StringBuilder("// Mesh exports resolved in combined mod priority order.\n");
                for (var def : kept.values()) text.append(def.text).append("\n\n");
                FileOps.write(dest, text.toString().getBytes(StandardCharsets.UTF_8));
            }
            decisions.set(index, new MergeDecision(decision.relativePath, DecisionKind.MERGED, decision.winner,
                "Reconciled mesh exports across depiction files by mod priority.", dest, false, true));
        }
    }
}
