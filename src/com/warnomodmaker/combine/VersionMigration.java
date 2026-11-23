package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.NdfDatabase;
import com.warnomodmaker.combine.ndf.NdfLayout;
import com.warnomodmaker.combine.ndf.NdfValue;
import com.warnomodmaker.combine.ndf.VanillaDatabases;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carries objects written for an older WARNO revision over to the installed one's class layout, the way Eugen's own
 * update reshaped its data. The rules are learned from the two revisions' vanilla databases, so any pair of revisions
 * WARNO keeps on disk can be migrated:
 *
 * <ul>
 *   <li>a class renamed (vanilla's object at the same place has another class now);</li>
 *   <li>a property renamed on the same object;</li>
 *   <li>a property moved up from an inline child (the 2026 update moved Name, Caliber and four more from each mounted
 *       weapon's Ammunition onto the mounted weapon), or down from the parent onto the child;</li>
 *   <li>a property every current object of a class sets, with no old property to carry it from: it gets the value
 *       every vanilla object of that class has, when they all agree.</li>
 * </ul>
 *
 * <p>A rule is accepted only when vanilla's own objects confirm it: matched by their place in the data (export path
 * and field path), at least nine in ten of those holding both values agree. Properties WARNO removed are then dropped
 * by {@link RemovedProperties}.</p>
 */
final class VersionMigration {
    private static final Map<String, VersionMigration> CACHE = new ConcurrentHashMap<>();
    private static final double AGREEMENT = 0.9;

    private enum Kind { UP, DOWN, RENAME }

    private record Rule(Kind kind, String targetClass, String property, String field, String sourceClass, String sourceProperty) {}

    private final Map<String, String> classRenames = new HashMap<>();
    private final Map<String, List<Rule>> rules = new HashMap<>();
    private final Map<String, Map<String, NdfValue>> defaults = new HashMap<>();
    private final Census current;
    private final Map<Rule, Map<NdfValue, NdfValue>> byChildDescriptor = new ConcurrentHashMap<>();

    /**
     * For a moved-up rule: current vanilla's value of the property on objects whose child (the rule's field) is a
     * given descriptor, by that descriptor's id. Ids vanilla gives different values are left out.
     */
    private Map<NdfValue, NdfValue> byChildDescriptor(Rule rule) {
        return byChildDescriptor.computeIfAbsent(rule, r -> {
            Map<NdfValue, NdfValue> values = new HashMap<>();
            Set<NdfValue> ambiguous = new HashSet<>();
            for (NdfValue.Obj obj : current.byPath.values()) {
                if (!obj.className.equals(r.targetClass)) continue;
                if (!(obj.get(r.field) instanceof NdfValue.Obj child)) continue;
                NdfValue id = child.get("DescriptorId"), value = obj.get(r.property);
                if (id == null || value == null || ambiguous.contains(id)) continue;
                NdfValue seen = values.putIfAbsent(id, value);
                if (seen != null && !NdfValue.same(seen, value)) {
                    values.remove(id);
                    ambiguous.add(id);
                }
            }
            return values;
        });
    }
    private final Map<String, Set<String>> required = new HashMap<>();

    static VersionMigration between(VanillaDatabases oldVanilla, VanillaDatabases newVanilla) {
        String key = oldVanilla.archivePath().toAbsolutePath().normalize() + "|" + newVanilla.archivePath().toAbsolutePath().normalize();
        return CACHE.computeIfAbsent(key, k -> new VersionMigration(oldVanilla, newVanilla));
    }

    private VersionMigration(VanillaDatabases oldVanilla, VanillaDatabases newVanilla) {
        Census before = Census.of(oldVanilla), after = Census.of(newVanilla);
        current = after;

        // Classes whose objects changed class at the same place.
        Map<String, Map<String, Integer>> renamedTo = new HashMap<>();
        for (Map.Entry<String, NdfValue.Obj> e : after.byPath.entrySet()) {
            NdfValue.Obj old = before.byPath.get(e.getKey());
            if (old == null || old.className.equals(e.getValue().className)) continue;
            if (after.count.containsKey(old.className) || before.count.containsKey(e.getValue().className)) continue;
            renamedTo.computeIfAbsent(old.className, c -> new HashMap<>()).merge(e.getValue().className, 1, Integer::sum);
        }
        for (Map.Entry<String, Map<String, Integer>> e : renamedTo.entrySet()) {
            Map.Entry<String, Integer> best = e.getValue().entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            int total = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
            if (best.getValue() >= AGREEMENT * total) classRenames.put(e.getKey(), best.getKey());
        }

        // Properties current objects set that the same class's objects never set before.
        for (Map.Entry<String, Map<String, Integer>> cls : after.props.entrySet()) {
            String target = cls.getKey();
            Set<String> oldProps = before.props.containsKey(target) ? before.props.get(target).keySet() : oldPropsOfRenamed(before, target);
            if (oldProps == null) continue; // a class the old revision didn't have: nothing old to carry over
            for (String property : cls.getValue().keySet()) {
                if (oldProps.contains(property)) continue;
                Rule rule = learn(before, after, target, property);
                if (rule != null) {
                    rules.computeIfAbsent(target, c -> new ArrayList<>()).add(rule);
                    continue;
                }
                if (cls.getValue().get(property).intValue() == after.count.get(target).intValue()) {
                    required.computeIfAbsent(target, c -> new HashSet<>()).add(property);
                    NdfValue common = after.sharedValue(target, property);
                    if (common != null) defaults.computeIfAbsent(target, c -> new HashMap<>()).put(property, common);
                }
            }
        }
    }

    private Set<String> oldPropsOfRenamed(Census before, String target) {
        for (Map.Entry<String, String> e : classRenames.entrySet())
            if (e.getValue().equals(target) && before.props.containsKey(e.getKey())) return before.props.get(e.getKey()).keySet();
        return null;
    }

    /** The rule vanilla's matched objects confirm for a property the class gained, or null. */
    private Rule learn(Census before, Census after, String target, String property) {
        List<Rule> candidates = new ArrayList<>();
        for (Map.Entry<String, NdfValue.Obj> e : after.byPath.entrySet()) {
            if (!e.getValue().className.equals(target)) continue;
            NdfValue.Obj old = before.byPath.get(e.getKey());
            if (old == null) continue;
            for (Map.Entry<String, NdfValue> field : old.fields) {
                if (field.getValue() instanceof NdfValue.Obj child && child.get(property) != null && !after.sets(child.className, property, classRenames))
                    candidates.add(new Rule(Kind.UP, target, property, field.getKey(), child.className, property));
                if (!field.getKey().equals(property) && !after.sets(target, field.getKey(), classRenames) && !(field.getValue() instanceof NdfValue.Obj))
                    candidates.add(new Rule(Kind.RENAME, target, property, null, target, field.getKey()));
            }
            int dot = e.getKey().lastIndexOf('.');
            if (dot > 0) {
                NdfValue.Obj oldParent = before.byPath.get(e.getKey().substring(0, dot));
                String field = e.getKey().substring(dot + 1);
                if (oldParent != null && oldParent.get(property) != null && !field.contains("[") && !field.contains("{")
                    && !after.sets(oldParent.className, property, classRenames))
                    candidates.add(new Rule(Kind.DOWN, target, property, field, oldParent.className, property));
            }
            if (candidates.size() > 64) break;
        }
        Rule best = null;
        int bestAgree = 0;
        for (Rule rule : new LinkedHashMap<Rule, Boolean>() {{ for (Rule r : candidates) put(r, Boolean.TRUE); }}.keySet()) {
            int agree = 0, disagree = 0;
            for (Map.Entry<String, NdfValue.Obj> e : after.byPath.entrySet()) {
                if (!e.getValue().className.equals(target) || e.getValue().get(property) == null) continue;
                NdfValue.Obj old = before.byPath.get(e.getKey());
                NdfValue source = old == null ? null : source(rule, old, e.getKey(), before);
                if (source == null) continue;
                if (NdfValue.same(source, e.getValue().get(property))) agree++; else disagree++;
            }
            if (agree > bestAgree && agree >= AGREEMENT * (agree + disagree)) {
                best = rule;
                bestAgree = agree;
            }
        }
        return best;
    }

    private static NdfValue source(Rule rule, NdfValue.Obj old, String path, Census before) {
        switch (rule.kind) {
            case UP:
                return old.get(rule.field) instanceof NdfValue.Obj child && child.className.equals(rule.sourceClass) ? child.get(rule.sourceProperty) : null;
            case RENAME:
                return old.get(rule.sourceProperty);
            default:
                int dot = path.lastIndexOf('.');
                if (dot < 0 || !path.substring(dot + 1).equals(rule.field)) return null;
                NdfValue.Obj parent = before.byPath.get(path.substring(0, dot));
                return parent != null && parent.className.equals(rule.sourceClass) ? parent.get(rule.sourceProperty) : null;
        }
    }

    boolean isEmpty() {
        return classRenames.isEmpty() && rules.isEmpty() && required.isEmpty();
    }

    /** The database in the current revision's layout; what was done is added to {@code done}. */
    NdfDatabase apply(NdfDatabase database, Set<String> done) {
        Set<String> exports = new HashSet<>();
        for (NdfDatabase.TopLevel t : database.topLevel) if (t.exportPath != null) exports.add(t.exportPath);
        List<NdfDatabase.TopLevel> top = new ArrayList<>(database.topLevel.size());
        boolean changed = false;
        for (NdfDatabase.TopLevel t : database.topLevel) {
            NdfValue.Obj value = (NdfValue.Obj) migrate(t.value, null, null, done, exports);
            changed |= value != t.value;
            top.add(value == t.value ? t : t.withValue(value));
        }
        return changed ? new NdfDatabase(top, database.propertyOwners) : database;
    }

    private NdfValue migrate(NdfValue value, NdfValue.Obj parent, String fieldInParent, Set<String> done, Set<String> exports) {
        if (value instanceof NdfValue.Obj obj) {
            String className = classRenames.getOrDefault(obj.className, obj.className);
            List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(obj.fields.size() + 4);
            boolean changed = !className.equals(obj.className);
            if (changed) done.add("class " + obj.className + " → " + className);
            for (Map.Entry<String, NdfValue> field : obj.fields) {
                NdfValue inner = migrate(field.getValue(), obj, field.getKey(), done, exports);
                changed |= inner != field.getValue();
                fields.add(inner == field.getValue() ? field : new AbstractMap.SimpleImmutableEntry<>(field.getKey(), inner));
            }
            for (Rule rule : rules.getOrDefault(className, List.of())) {
                NdfValue carried = carried(rule, obj, parent, fieldInParent);
                if (carried == null) continue;
                set(fields, rule.property, carried);
                changed = true;
                done.add(describe(rule));
            }
            Map<String, NdfValue> fill = defaults.getOrDefault(className, Map.of());
            for (String property : required.getOrDefault(className, Set.of())) {
                if (has(fields, property)) continue;
                NdfValue value2 = fill.get(property) == null ? null : localize(fill.get(property), exports);
                if (value2 == null) continue;
                fields.add(new AbstractMap.SimpleImmutableEntry<>(property, value2));
                changed = true;
                done.add(className + "." + property + " (new, given vanilla's value " + value2.describe() + ")");
            }
            return changed ? new NdfValue.Obj(className, fields, obj.origin) : obj;
        }
        if (value instanceof NdfValue.ListV list) {
            List<NdfValue> items = new ArrayList<>(list.items.size());
            boolean changed = false;
            for (NdfValue item : list.items) {
                NdfValue inner = migrate(item, null, null, done, exports);
                changed |= inner != item;
                items.add(inner);
            }
            return changed ? new NdfValue.ListV(items) : list;
        }
        if (value instanceof NdfValue.MapV map) {
            List<Map.Entry<NdfValue, NdfValue>> entries = new ArrayList<>(map.entries.size());
            boolean changed = false;
            for (Map.Entry<NdfValue, NdfValue> entry : map.entries) {
                NdfValue key = migrate(entry.getKey(), null, null, done, exports), inner = migrate(entry.getValue(), null, null, done, exports);
                changed |= key != entry.getKey() || inner != entry.getValue();
                entries.add(key == entry.getKey() && inner == entry.getValue() ? entry : new AbstractMap.SimpleImmutableEntry<>(key, inner));
            }
            return changed ? new NdfValue.MapV(entries) : map;
        }
        if (value instanceof NdfValue.Pair pair) {
            NdfValue first = migrate(pair.first, null, null, done, exports), second = migrate(pair.second, null, null, done, exports);
            return first != pair.first || second != pair.second ? new NdfValue.Pair(first, second) : pair;
        }
        return value;
    }

    /**
     * The value a rule carries onto the object from where the old layout kept it, or null. A value still in the old
     * place is one the mod set (vanilla's own was removed there by the update), so it wins over the merged one.
     */
    private NdfValue carried(Rule rule, NdfValue.Obj obj, NdfValue.Obj parent, String fieldInParent) {
        switch (rule.kind) {
            case UP: {
                if (!(obj.get(rule.field) instanceof NdfValue.Obj child) || !classMatches(child.className, rule.sourceClass)) return null;
                NdfValue own = child.get(rule.sourceProperty);
                if (own != null) return own;
                // The child is a shared descriptor (vanilla ammunition) the merge has already given its current
                // layout; the moved value belongs to that descriptor, so it is the one current vanilla carries for it.
                if (obj.get(rule.property) != null) return null;
                NdfValue id = child.get("DescriptorId");
                return id == null ? null : byChildDescriptor(rule).get(id);
            }
            case RENAME:
                return obj.get(rule.sourceProperty);
            default:
                return parent != null && rule.field.equals(fieldInParent) && classMatches(parent.className, rule.sourceClass)
                    ? parent.get(rule.sourceProperty) : null;
        }
    }

    /**
     * A vanilla value as it must be written in this database: a reference to an object this database exports is a
     * local export reference, and any other reference an import.
     */
    private static NdfValue localize(NdfValue value, Set<String> exports) {
        if (value instanceof NdfValue.ExportRef ref) return exports.contains(ref.path) ? ref : new NdfValue.Import(ref.path);
        if (value instanceof NdfValue.Import ref) return exports.contains(ref.path) ? new NdfValue.ExportRef(ref.path) : ref;
        if (value instanceof NdfValue.Obj obj) {
            List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(obj.fields.size());
            for (Map.Entry<String, NdfValue> f : obj.fields) fields.add(new AbstractMap.SimpleImmutableEntry<>(f.getKey(), localize(f.getValue(), exports)));
            return new NdfValue.Obj(obj.className, fields, obj.origin);
        }
        if (value instanceof NdfValue.ListV list) {
            List<NdfValue> items = new ArrayList<>(list.items.size());
            for (NdfValue item : list.items) items.add(localize(item, exports));
            return new NdfValue.ListV(items);
        }
        if (value instanceof NdfValue.MapV map) {
            List<Map.Entry<NdfValue, NdfValue>> entries = new ArrayList<>(map.entries.size());
            for (Map.Entry<NdfValue, NdfValue> e : map.entries)
                entries.add(new AbstractMap.SimpleImmutableEntry<>(localize(e.getKey(), exports), localize(e.getValue(), exports)));
            return new NdfValue.MapV(entries);
        }
        if (value instanceof NdfValue.Pair pair) return new NdfValue.Pair(localize(pair.first, exports), localize(pair.second, exports));
        return value;
    }

    private static boolean classMatches(String actual, String expected) {
        return actual.equals(expected);
    }

    private static boolean has(List<Map.Entry<String, NdfValue>> fields, String name) {
        for (Map.Entry<String, NdfValue> f : fields) if (f.getKey().equals(name)) return true;
        return false;
    }

    private static void set(List<Map.Entry<String, NdfValue>> fields, String name, NdfValue value) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).getKey().equals(name)) {
                fields.set(i, new AbstractMap.SimpleImmutableEntry<>(name, value));
                return;
            }
        }
        fields.add(new AbstractMap.SimpleImmutableEntry<>(name, value));
    }

    private static String describe(Rule rule) {
        switch (rule.kind) {
            case UP: return rule.targetClass + "." + rule.property + " (moved up from " + rule.field + ")";
            case DOWN: return rule.targetClass + "." + rule.property + " (moved down from " + rule.sourceClass + ")";
            default: return rule.targetClass + "." + rule.property + " (renamed from " + rule.sourceProperty + ")";
        }
    }

    static String summary(Set<String> done) {
        List<String> shown = new ArrayList<>(new TreeSet<>(done));
        return String.join(", ", shown.subList(0, Math.min(8, shown.size()))) + (shown.size() > 8 ? ", ..." : "");
    }

    /** Every object of a revision by its place in the data, with how often each class sets each property. */
    private static final class Census {
        private static final Map<String, Census> CENSUSES = new ConcurrentHashMap<>();

        final Map<String, NdfValue.Obj> byPath = new HashMap<>();
        final Map<String, Integer> count = new HashMap<>();
        final Map<String, Map<String, Integer>> props = new HashMap<>();
        // Per class, the value every object gives each property, while they all agree.
        final Map<String, Map<String, NdfValue>> shared = new HashMap<>();
        final Set<String> mixed = new HashSet<>();

        static Census of(VanillaDatabases vanilla) {
            return CENSUSES.computeIfAbsent(vanilla.archivePath().toAbsolutePath().normalize().toString(), k -> {
                Census census = new Census();
                for (String path : vanilla.databasePaths()) {
                    if (!NdfLayout.isDatabase(path)) continue;
                    byte[] data = vanilla.tryRead(path);
                    if (data == null) continue;
                    for (NdfDatabase.TopLevel t : NdfDatabase.load(data).topLevel)
                        census.walk(t.value, t.exportPath);
                }
                return census;
            });
        }

        private void walk(NdfValue value, String path) {
            if (value instanceof NdfValue.Obj obj) {
                if (path != null) byPath.put(path, obj);
                count.merge(obj.className, 1, Integer::sum);
                Map<String, Integer> p = props.computeIfAbsent(obj.className, c -> new HashMap<>());
                Map<String, NdfValue> values = shared.computeIfAbsent(obj.className, c -> new HashMap<>());
                for (Map.Entry<String, NdfValue> field : obj.fields) {
                    p.merge(field.getKey(), 1, Integer::sum);
                    String key = obj.className + "." + field.getKey();
                    if (!mixed.contains(key)) {
                        NdfValue seen = values.putIfAbsent(field.getKey(), field.getValue());
                        if (seen != null && !NdfValue.same(seen, field.getValue())) {
                            mixed.add(key);
                            values.remove(field.getKey());
                        }
                    }
                    walk(field.getValue(), path == null ? null : path + "." + field.getKey());
                }
            } else if (value instanceof NdfValue.ListV list) {
                int i = 0;
                for (NdfValue item : list.items) walk(item, path == null ? null : path + "[" + i++ + "]");
            } else if (value instanceof NdfValue.MapV map) {
                for (Map.Entry<NdfValue, NdfValue> entry : map.entries) {
                    walk(entry.getKey(), null);
                    walk(entry.getValue(), path == null ? null : path + "{" + entry.getKey().describe() + "}");
                }
            } else if (value instanceof NdfValue.Pair pair) {
                walk(pair.first, null);
                walk(pair.second, null);
            }
        }

        /** True when objects of the class, or of the class it was renamed to, set the property. */
        boolean sets(String className, String property, Map<String, String> renames) {
            String current = renames.getOrDefault(className, className);
            Map<String, Integer> p = props.get(current);
            return p != null && p.containsKey(property);
        }

        /** The value every object of the class sets the property to (anonymous objects included), or null when they differ. */
        NdfValue sharedValue(String className, String property) {
            Map<String, NdfValue> values = shared.get(className);
            return values == null ? null : values.get(property);
        }
    }
}
