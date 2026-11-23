package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.NdfDatabase;
import com.warnomodmaker.combine.ndf.NdfRewrite;
import com.warnomodmaker.combine.ndf.NdfValue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * WARNO requires every occurrence of a descriptor GUID in a database to have the same content ("Duplicate descriptor
 * GUID"). Vanilla shares descriptors (a weapon's ammunition) between many objects; an object an old mod still needs
 * and that is restored from its old revision carries that revision's copy of a descriptor the current game has since
 * changed. Each GUID takes the content most of its occurrences have: the current game's, or the one a mod's merge
 * settled on everywhere it is used.
 */
final class DescriptorSettle {
    private DescriptorSettle() {
    }

    /** The database with one content per descriptor GUID; the GUIDs settled are added to {@code settled}. */
    static NdfDatabase settle(NdfDatabase database, Set<String> settled) {
        // A GUID must also be a single object: the compiled database stores a shared descriptor once and refers to it
        // from every use, and separate objects with the same GUID, even equal ones, are declared twice. Rewriting
        // objects (migration, removed properties) copies them, so every use is pointed back at one instance.
        for (int pass = 0; pass < 8; pass++) {
            Map<NdfValue, Map<NdfValue.Obj, Integer>> contents = new HashMap<>();
            Map<NdfValue, Set<NdfValue.Obj>> instances = new HashMap<>();
            for (NdfDatabase.TopLevel t : database.topLevel) collect(t.value, contents, instances);
            Map<NdfValue, NdfValue.Obj> canonical = new HashMap<>();
            for (Map.Entry<NdfValue, Map<NdfValue.Obj, Integer>> e : contents.entrySet()) {
                if (instances.get(e.getKey()).size() < 2) continue;
                NdfValue.Obj best = null;
                int count = -1;
                for (Map.Entry<NdfValue.Obj, Integer> c : e.getValue().entrySet()) {
                    if (c.getValue() > count) {
                        best = c.getKey();
                        count = c.getValue();
                    }
                }
                canonical.put(e.getKey(), best);
                if (e.getValue().size() > 1) settled.add(e.getKey().describe());
            }
            if (canonical.isEmpty()) return database;
            database = new NdfRewrite(obj -> {
                NdfValue id = obj.get("DescriptorId");
                if (!(id instanceof NdfValue.Raw)) return null;
                NdfValue.Obj target = canonical.get(id);
                return target != null && target != obj ? target : null;
            }, null).apply(database);
        }
        return database;
    }

    private static void collect(NdfValue value, Map<NdfValue, Map<NdfValue.Obj, Integer>> out, Map<NdfValue, Set<NdfValue.Obj>> instances) {
        if (value instanceof NdfValue.Obj obj) {
            if (obj.get("DescriptorId") instanceof NdfValue.Raw id) {
                out.computeIfAbsent(id, k -> new LinkedHashMap<>()).merge(obj, 1, Integer::sum);
                instances.computeIfAbsent(id, k -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(obj);
            }
            for (Map.Entry<String, NdfValue> field : obj.fields) collect(field.getValue(), out, instances);
        } else if (value instanceof NdfValue.ListV list) {
            for (NdfValue item : list.items) collect(item, out, instances);
        } else if (value instanceof NdfValue.MapV map) {
            for (Map.Entry<NdfValue, NdfValue> entry : map.entries) {
                collect(entry.getKey(), out, instances);
                collect(entry.getValue(), out, instances);
            }
        } else if (value instanceof NdfValue.Pair pair) {
            collect(pair.first, out, instances);
            collect(pair.second, out, instances);
        }
    }
}
