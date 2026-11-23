package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.NdfDatabase;
import com.warnomodmaker.combine.ndf.NdfLayout;
import com.warnomodmaker.combine.ndf.NdfValue;
import com.warnomodmaker.combine.ndf.VanillaDatabases;

import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Properties WARNO has removed from a class between two revisions. WARNO refuses to load a database whose object
 * sets a property its class no longer has ("Property '_ShortDatabaseName' in class 'TSoundDescriptor' not found"), and
 * an old mod's objects (its own, its changed values the update also touched, or ones restored from its revision) can
 * still carry them.
 *
 * <p>A compiled database only stores the properties an object sets, so the class definitions are read from use: a
 * property is removed when the old revision's databases set it on the class, and the current revision still has
 * objects of that class but none of them sets it.</p>
 */
final class RemovedProperties {
    private static final Map<Path, Map<String, Set<String>>> USES = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> removed = new HashMap<>();

    RemovedProperties(VanillaDatabases oldVanilla, VanillaDatabases newVanilla) {
        Map<String, Set<String>> before = uses(oldVanilla), after = uses(newVanilla);
        for (Map.Entry<String, Set<String>> entry : before.entrySet()) {
            Set<String> now = after.get(entry.getKey());
            if (now == null) continue;
            for (String property : entry.getValue()) {
                if (!now.contains(property)) removed.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(property);
            }
        }
    }

    boolean isEmpty() {
        return removed.isEmpty();
    }

    /** The database without properties WARNO removed; dropped ones are added to {@code dropped} as Class.Property. */
    NdfDatabase strip(NdfDatabase database, Set<String> dropped) {
        List<NdfDatabase.TopLevel> top = new ArrayList<>(database.topLevel.size());
        boolean changed = false;
        for (NdfDatabase.TopLevel t : database.topLevel) {
            NdfValue.Obj value = (NdfValue.Obj) strip(t.value, dropped);
            changed |= value != t.value;
            top.add(value == t.value ? t : t.withValue(value));
        }
        return changed ? new NdfDatabase(top, database.propertyOwners) : database;
    }

    private NdfValue strip(NdfValue value, Set<String> dropped) {
        if (value instanceof NdfValue.Obj) {
            NdfValue.Obj obj = (NdfValue.Obj) value;
            Set<String> gone = removed.get(obj.className);
            List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(obj.fields.size());
            boolean changed = false;
            for (Map.Entry<String, NdfValue> field : obj.fields) {
                if (gone != null && gone.contains(field.getKey())) {
                    dropped.add(obj.className + "." + field.getKey());
                    changed = true;
                    continue;
                }
                NdfValue inner = strip(field.getValue(), dropped);
                changed |= inner != field.getValue();
                fields.add(inner == field.getValue() ? field : new AbstractMap.SimpleImmutableEntry<>(field.getKey(), inner));
            }
            return changed ? new NdfValue.Obj(obj.className, fields, obj.origin) : obj;
        }
        if (value instanceof NdfValue.ListV) {
            NdfValue.ListV list = (NdfValue.ListV) value;
            List<NdfValue> items = new ArrayList<>(list.items.size());
            boolean changed = false;
            for (NdfValue item : list.items) {
                NdfValue inner = strip(item, dropped);
                changed |= inner != item;
                items.add(inner);
            }
            return changed ? new NdfValue.ListV(items) : list;
        }
        if (value instanceof NdfValue.MapV) {
            NdfValue.MapV map = (NdfValue.MapV) value;
            List<Map.Entry<NdfValue, NdfValue>> entries = new ArrayList<>(map.entries.size());
            boolean changed = false;
            for (Map.Entry<NdfValue, NdfValue> entry : map.entries) {
                NdfValue key = strip(entry.getKey(), dropped), inner = strip(entry.getValue(), dropped);
                changed |= key != entry.getKey() || inner != entry.getValue();
                entries.add(key == entry.getKey() && inner == entry.getValue() ? entry : new AbstractMap.SimpleImmutableEntry<>(key, inner));
            }
            return changed ? new NdfValue.MapV(entries) : map;
        }
        if (value instanceof NdfValue.Pair) {
            NdfValue.Pair pair = (NdfValue.Pair) value;
            NdfValue first = strip(pair.first, dropped), second = strip(pair.second, dropped);
            return first != pair.first || second != pair.second ? new NdfValue.Pair(first, second) : pair;
        }
        return value;
    }

    /** Class name to every property its objects set, across all of a revision's databases. */
    private static Map<String, Set<String>> uses(VanillaDatabases vanilla) {
        return USES.computeIfAbsent(vanilla.archivePath().toAbsolutePath().normalize(), key -> {
            Map<String, Set<String>> result = new HashMap<>();
            for (String path : vanilla.databasePaths()) {
                if (!NdfLayout.isDatabase(path)) continue;
                byte[] data = vanilla.tryRead(path);
                if (data == null) continue;
                for (NdfDatabase.TopLevel t : NdfDatabase.load(data).topLevel) collect(t.value, result);
            }
            return result;
        });
    }

    private static void collect(NdfValue value, Map<String, Set<String>> out) {
        if (value instanceof NdfValue.Obj) {
            NdfValue.Obj obj = (NdfValue.Obj) value;
            Set<String> fields = out.computeIfAbsent(obj.className, k -> new HashSet<>());
            for (Map.Entry<String, NdfValue> field : obj.fields) {
                fields.add(field.getKey());
                collect(field.getValue(), out);
            }
        } else if (value instanceof NdfValue.ListV) {
            for (NdfValue item : ((NdfValue.ListV) value).items) collect(item, out);
        } else if (value instanceof NdfValue.MapV) {
            for (Map.Entry<NdfValue, NdfValue> entry : ((NdfValue.MapV) value).entries) {
                collect(entry.getKey(), out);
                collect(entry.getValue(), out);
            }
        } else if (value instanceof NdfValue.Pair) {
            collect(((NdfValue.Pair) value).first, out);
            collect(((NdfValue.Pair) value).second, out);
        }
    }

    static String summary(Set<String> dropped) {
        List<String> shown = new ArrayList<>(new TreeSet<>(dropped));
        return String.join(", ", shown.subList(0, Math.min(8, shown.size()))) + (shown.size() > 8 ? ", ..." : "");
    }
}
