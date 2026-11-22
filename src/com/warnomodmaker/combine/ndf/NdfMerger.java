package com.warnomodmaker.combine.ndf;

import com.warnomodmaker.combine.Cancel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Three-way, N-mod merge of compiled databases against the vanilla database. Each mod's delta is what it changed
 * relative to vanilla; deltas that touch different values combine, and where several mods change the same value
 * differently the highest-priority mod wins and a conflict is recorded.
 */
public final class NdfMerger {
    public static final class Variant {
        public final String modName;
        public final NdfDatabase database;

        public Variant(String modName, NdfDatabase database) {
            this.modName = modName;
            this.database = database;
        }
    }

    /** A value that several mods changed differently; the highest-priority mod's value was kept. */
    public static final class Conflict {
        public final String path;
        public final String winner;
        public final List<String> overridden;
        public final String winnerValue;

        public Conflict(String path, String winner, List<String> overridden, String winnerValue) {
            this.path = path;
            this.winner = winner;
            this.overridden = overridden;
            this.winnerValue = winnerValue;
        }

        public Conflict withPath(String newPath) {
            return new Conflict(newPath, winner, overridden, winnerValue);
        }
    }

    public static final class Result {
        public final NdfDatabase database;
        public final List<Conflict> conflicts;
        public final List<String> changedBy;
        public final int changedObjects;

        Result(NdfDatabase database, List<Conflict> conflicts, List<String> changedBy, int changedObjects) {
            this.database = database;
            this.conflicts = conflicts;
            this.changedBy = changedBy;
            this.changedObjects = changedObjects;
        }
    }

    /** One mod's value of something, or null when the mod doesn't have it. */
    public static final class Edit {
        final String mod;
        final NdfValue value;

        public Edit(String mod, NdfValue value) {
            this.mod = mod;
            this.value = value;
        }
    }

    /** The merged value of {@link #mergeOne} and the conflicts it resolved. */
    public static final class One {
        public final NdfValue value;
        public final List<Conflict> conflicts;

        One(NdfValue value, List<Conflict> conflicts) {
            this.value = value;
            this.conflicts = conflicts;
        }
    }

    private static final String DESCRIPTOR_ID_FIELD = "DescriptorId";
    private static final String[] IDENTITY_FIELDS = {"_ShortDatabaseName", "Name", DESCRIPTOR_ID_FIELD};

    private final List<Conflict> conflicts = new ArrayList<>();
    // Merging the same input objects again yields the same merged object, so an object several parents share stays one.
    private final Map<InstanceKey, NdfValue.Obj> mergedObjects = new HashMap<>();
    // Every input's instance of a private constant, mapped to the merged constant it became.
    private final Map<NdfValue.Obj, NdfValue.Obj> constants = new IdentityHashMap<>();
    // Merged constants vanilla has and some mod removed (or replaced): kept only while something still references them.
    private final Set<NdfValue.Obj> removedConstants = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> changedBy = new HashSet<>();
    private int changedObjects;

    private NdfMerger() {
    }

    /**
     * @param vanilla  the unmodified database, or null when WARNO ships none (e.g. Catalog.cat).
     * @param variants each mod's full database, highest priority first.
     */
    public static Result merge(NdfDatabase vanilla, List<Variant> variants, Cancel token) {
        if (variants.isEmpty()) throw new IllegalArgumentException("Nothing to merge.");
        NdfMerger merger = new NdfMerger();
        Object[] unified = merger.unifyDescriptors(vanilla, variants, token);
        vanilla = (NdfDatabase) unified[0];
        @SuppressWarnings("unchecked") List<Variant> unifiedVariants = (List<Variant>) unified[1];
        variants = unifiedVariants;
        List<NdfDatabase.TopLevel> merged = vanilla == null ? merger.mergeWithoutBase(variants, token) : merger.mergeTopLevel(vanilla, variants, token);
        List<NdfDatabase> sources = new ArrayList<>();
        if (vanilla != null) sources.add(vanilla);
        for (Variant v : variants) sources.add(v.database);
        Map<NdfDatabase.PropKey, String> owners = new HashMap<>();
        for (NdfDatabase db : sources) for (Map.Entry<NdfDatabase.PropKey, String> pair : db.propertyOwners.entrySet()) owners.putIfAbsent(pair.getKey(), pair.getValue());
        merged = merger.restoreMissingExports(merged, vanilla, variants);
        merged = merger.settleDescriptors(merged, vanilla, variants);
        merged = merger.settleConstants(merged, sources);
        merged = merger.settleDeckIds(merged, vanilla, variants);
        // WARNO's pool rule (see NdfPoolOrder): a merged list can put one mod's optional element ahead of another's Required one.
        NdfPoolOrder.Result settled = NdfPoolOrder.settle(new NdfDatabase(merged, owners));
        for (String pool : settled.reordered)
            merger.conflicts.add(new Conflict(pool, "WARNO's pool rule", Collections.emptyList(),
                "Required elements moved ahead of optional ones; WARNO won't load a pool that lists a Required element after an optional one"));
        List<String> changed = new ArrayList<>();
        for (Variant v : variants) if (merger.changedBy.contains(v.modName) && !changed.contains(v.modName)) changed.add(v.modName);
        return new Result(settled.database, merger.conflicts, changed, merger.changedObjects);
    }

    /**
     * Three-way merge of one value (an object WARNO moved to another database, say): the changes each variant made to
     * {@code baseValue}, highest priority first.
     */
    public static One mergeOne(String path, NdfValue baseValue, List<Edit> variants) {
        NdfMerger merger = new NdfMerger();
        NdfValue value = merger.mergeValue(path, baseValue, variants, new boolean[1], true);
        return new One(value, merger.conflicts);
    }

    // ───────────── Descriptors ─────────────

    /**
     * A DescriptorId names one object however many parents reference it, and WARNO refuses to load two objects with
     * the same id. Merging each use separately lets them diverge, so the content of every non-exported descriptor is
     * merged once here and written back into vanilla and each variant. The merge that follows then only decides
     * where each descriptor is used. Returns {vanilla, variants}.
     */
    private Object[] unifyDescriptors(NdfDatabase vanilla, List<Variant> variants, Cancel token) {
        // Slot 0 is vanilla; slot i + 1 is variants[i].
        Map<NdfValue, NdfValue.Obj[]> versions = new HashMap<>();
        List<NdfDatabase> databases = new ArrayList<>();
        databases.add(vanilla);
        for (Variant v : variants) databases.add(v.database);
        for (int i = 0; i < databases.size(); i++) {
            NdfDatabase database = databases.get(i);
            if (database == null) continue;
            for (NdfValue.Obj nested : nestedObjects(database.topLevel)) {
                NdfValue.Raw id = descriptorId(nested);
                if (id == null) continue;
                NdfValue.Obj[] slots = versions.computeIfAbsent(id, k -> new NdfValue.Obj[databases.size()]);
                if (slots[i] == null) slots[i] = nested;
            }
        }
        // Every database is rewritten, even when the descriptors agree: vanilla and each mod hold their own instance of
        // each descriptor, and the merged database must hold exactly one.
        Map<NdfValue, NdfValue.Obj> descriptors = new HashMap<>();
        Map<NdfValue.Obj, NdfValue.Obj> unified = new IdentityHashMap<>();

        class Unifier {
            NdfValue.Obj unify(NdfValue.Obj value) {
                NdfValue.Obj done = unified.get(value);
                if (done != null) return done;
                NdfValue.Raw id = descriptorId(value);
                NdfValue.Obj result = id != null ? mergeDescriptor(id) : unifyFields(value);
                unified.put(value, result);
                return result;
            }

            NdfValue.Obj unifyFields(NdfValue.Obj value) {
                List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(value.fields.size());
                for (Map.Entry<String, NdfValue> f : value.fields) fields.add(NdfValue.field(f.getKey(), unifyValue(f.getValue())));
                return new NdfValue.Obj(value.className, fields, value.origin);
            }

            NdfValue unifyValue(NdfValue value) {
                if (value instanceof NdfValue.Obj) return unify((NdfValue.Obj) value);
                if (value instanceof NdfValue.ListV) {
                    List<NdfValue> items = new ArrayList<>();
                    for (NdfValue item : ((NdfValue.ListV) value).items) items.add(unifyValue(item));
                    return new NdfValue.ListV(items);
                }
                if (value instanceof NdfValue.MapV) {
                    List<Map.Entry<NdfValue, NdfValue>> entries = new ArrayList<>();
                    for (Map.Entry<NdfValue, NdfValue> e : ((NdfValue.MapV) value).entries)
                        entries.add(NdfValue.entry(unifyValue(e.getKey()), unifyValue(e.getValue())));
                    return new NdfValue.MapV(firstEntries(entries));
                }
                if (value instanceof NdfValue.Pair)
                    return new NdfValue.Pair(unifyValue(((NdfValue.Pair) value).first), unifyValue(((NdfValue.Pair) value).second));
                return value;
            }

            NdfValue.Obj mergeDescriptor(NdfValue.Raw id) {
                NdfValue.Obj done = descriptors.get(id);
                if (done != null) return done;
                NdfValue.Obj[] raw = versions.get(id);
                NdfValue.Obj[] slots = new NdfValue.Obj[raw.length];
                for (int i = 0; i < raw.length; i++) slots[i] = raw[i] == null ? null : unifyFields(raw[i]);
                NdfValue.Obj baseValue = slots[0];
                NdfValue.Obj firstPresent = null;
                for (NdfValue.Obj slot : slots) if (slot != null) { firstPresent = slot; break; }
                String path = firstPresent.className + " " + describeDescriptor(id);
                List<Edit> edits = new ArrayList<>();
                for (int i = 0; i < variants.size(); i++) edits.add(new Edit(variants.get(i).modName, slots[i + 1] != null ? slots[i + 1] : baseValue));
                boolean[] changed = new boolean[1];
                NdfValue value = mergeValue(path, baseValue, edits, changed, false);
                if (changed[0]) changedObjects++;
                descriptors.put(id, (NdfValue.Obj) value);
                return (NdfValue.Obj) value;
            }

            NdfDatabase unifyDatabase(NdfDatabase database) {
                token.check();
                List<NdfDatabase.TopLevel> top = new ArrayList<>(database.topLevel.size());
                for (NdfDatabase.TopLevel t : database.topLevel)
                    top.add(new NdfDatabase.TopLevel(t.exportPath, t.exportPath == null ? unify(t.value) : unifyFields(t.value)));
                return new NdfDatabase(top, database.propertyOwners);
            }
        }

        Unifier unifier = new Unifier();
        NdfDatabase newVanilla = vanilla == null ? null : unifier.unifyDatabase(vanilla);
        List<Variant> newVariants = new ArrayList<>(variants.size());
        for (Variant v : variants) newVariants.add(new Variant(v.modName, unifier.unifyDatabase(v.database)));
        return new Object[]{newVanilla, newVariants};
    }

    /**
     * Compiles every nested descriptor once and keeps every export. Nested descriptors are already unique, so the only
     * collisions left are exports, such as units two mods cloned with the same id. References to exports go by path,
     * so the export that keeps vanilla's id (or else comes first) stays and the others get a new id.
     */
    private List<NdfDatabase.TopLevel> settleDescriptors(List<NdfDatabase.TopLevel> merged, NdfDatabase vanilla, List<Variant> variants) {
        Set<NdfValue> claimed = new HashSet<>();
        for (NdfValue.Obj nested : nestedObjects(merged)) {
            NdfValue.Raw id = descriptorId(nested);
            if (id != null) claimed.add(id);
        }
        Set<Map.Entry<String, NdfValue>> vanillaIds = new HashSet<>();
        if (vanilla != null) {
            for (NdfDatabase.TopLevel t : vanilla.topLevel) {
                if (t.exportPath != null && descriptorId(t.value) != null)
                    vanillaIds.add(new AbstractMap.SimpleImmutableEntry<>(t.exportPath, descriptorId(t.value)));
            }
        }
        List<NdfDatabase.TopLevel> exports = new ArrayList<>();
        for (NdfDatabase.TopLevel t : merged) if (t.exportPath != null && descriptorId(t.value) != null) exports.add(t);
        Map<Map.Entry<String,NdfValue>,Integer> priority = new HashMap<>();
        for (int i = 0; i < variants.size(); i++) {
            for (var entry : variants.get(i).database.topLevel) {
                var id = descriptorId(entry.value);
                if (entry.exportPath != null && id != null) priority.putIfAbsent(Map.entry(entry.exportPath,id),i);
            }
        }
        exports.sort(java.util.Comparator.<NdfDatabase.TopLevel>comparingInt(t -> keepsVanillaId(vanillaIds,t) ? 0 : 1)
            .thenComparingInt(t -> priority.getOrDefault(Map.entry(t.exportPath,descriptorId(t.value)),Integer.MAX_VALUE)));
        Map<String, NdfValue.Obj> reassigned = new HashMap<>();
        for (NdfDatabase.TopLevel top : exports) {
            NdfValue.Raw id = descriptorId(top.value);
            if (claimed.add(id)) continue;
            String path = top.exportPath;
            byte[] hash = sha256((path + "|" + Hex.upper(id.data)).getBytes(StandardCharsets.UTF_8));
            NdfValue.Raw replacement = new NdfValue.Raw(id.type, Arrays.copyOf(hash, id.data.length));
            claimed.add(replacement);
            List<Map.Entry<String, NdfValue>> fields = new ArrayList<>();
            for (Map.Entry<String, NdfValue> f : top.value.fields)
                fields.add(f.getKey().equals(DESCRIPTOR_ID_FIELD) ? NdfValue.field(f.getKey(), replacement) : f);
            reassigned.put(path, new NdfValue.Obj(top.value.className, fields, top.value.origin));
            String owner = "vanilla";
            for (Variant v : variants) {
                if (v.database.topLevel.stream().anyMatch(t -> path.equals(t.exportPath))) { owner = v.modName; break; }
            }
            conflicts.add(new Conflict(shortName(path), owner, Collections.emptyList(),
                "given the new descriptor " + describeDescriptor(replacement) + " because " + describeDescriptor(id) + " is already used"));
        }
        List<NdfDatabase.TopLevel> result = new ArrayList<>(merged.size());
        for (NdfDatabase.TopLevel t : merged) {
            NdfValue.Obj value = t.exportPath == null ? null : reassigned.get(t.exportPath);
            result.add(value != null ? new NdfDatabase.TopLevel(t.exportPath, value) : t);
        }
        return result;
    }

    private static boolean keepsVanillaId(Set<Map.Entry<String, NdfValue>> vanillaIds, NdfDatabase.TopLevel t) {
        return vanillaIds.contains(new AbstractMap.SimpleImmutableEntry<String, NdfValue>(t.exportPath, descriptorId(t.value)));
    }

    private static NdfValue.Raw descriptorId(NdfValue.Obj value) {
        NdfValue id = value.get(DESCRIPTOR_ID_FIELD);
        return id instanceof NdfValue.Raw ? (NdfValue.Raw) id : null;
    }

    private static String describeDescriptor(NdfValue.Raw id) {
        String hex = Hex.upper(id.data);
        if (hex.length() < 32) return "{" + hex + "}";
        return "{" + hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20) + "}";
    }

    /** Every distinct object (by content) below the exports, plus the anonymous top-level objects. */
    private static List<NdfValue.Obj> nestedObjects(List<NdfDatabase.TopLevel> topLevel) {
        Set<NdfValue> visited = new HashSet<>();
        ArrayDeque<NdfValue> stack = new ArrayDeque<>();
        List<NdfValue.Obj> result = new ArrayList<>();
        for (NdfDatabase.TopLevel top : topLevel) {
            if (top.exportPath == null) stack.push(top.value);
            else for (Map.Entry<String, NdfValue> field : top.value.fields) stack.push(field.getValue());
        }
        while (!stack.isEmpty()) {
            NdfValue current = stack.pop();
            if (current instanceof NdfValue.Obj) {
                if (!visited.add(current)) continue;
                result.add((NdfValue.Obj) current);
                for (Map.Entry<String, NdfValue> f : ((NdfValue.Obj) current).fields) stack.push(f.getValue());
            } else {
                NdfRewrite.pushChildren(current, stack, null);
            }
        }
        return result;
    }

    // ───────────── Top level ─────────────

    /**
     * A private constant (an anonymous top-level object) has no name to match it by, and WARNO compiles separate
     * constants with equal content, so a constant is the n-th one with its content: vanilla's second
     * "TSequenceCondition_Phase PhaseType=2" is each mod's second one.
     */
    private static final class ConstantKey {
        final NdfValue.Obj content;
        final int occurrence;

        ConstantKey(NdfValue.Obj content, int occurrence) {
            this.content = content;
            this.occurrence = occurrence;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ConstantKey && ((ConstantKey) o).occurrence == occurrence && ((ConstantKey) o).content.equals(content);
        }

        @Override
        public int hashCode() {
            return 31 * content.hashCode() + occurrence;
        }
    }

    /** Within a single provider, declaration order breaks ties between entries with the same key. */
    private static <K,V> List<Map.Entry<K,V>> firstEntries(List<Map.Entry<K,V>> entries) {
        Set<K> seen = new HashSet<>();
        return entries.stream().filter(entry -> seen.add(entry.getKey())).toList();
    }

    private record Registration(String type, String collection, List<Map.Entry<String,NdfValue>> scope) {}

    private static String registrationCollection(String type) {
        return switch (type) {
            case "TTransportedInfantryCatalogEntries" -> "Entries";
            case "TBUCKToolAdditionalTextureBank" -> "Textures";
            case "TUISpecificCountriesInfos" -> "CountriesInfos";
            case "TUnitMimeticRegistration" -> "UnitMimetics";
            case "TGhostMimeticRegistration" -> "GhostMimetics";
            case "TGhostBuildingMimeticRegistration" -> "GhostBuildingMimetics";
            case "TMimeticWorldHappeningRegistration" -> "Happenings";
            default -> null;
        };
    }

    private static boolean isRegistration(NdfDatabase.TopLevel root) {
        String type = root.value.className;
        return root.exportPath == null && (registrationCollection(type) != null || type.equals("TTowedUnitCatalogEntry") || type.equals("TDeckSerializerEntries"));
    }

    /** Each top-level entry's key (its export path, or its {@link ConstantKey}) with its value, in order. */
    private static List<Map.Entry<Object, NdfValue.Obj>> keys(List<NdfDatabase.TopLevel> topLevel) {
        Map<NdfValue.Obj, Integer> occurrences = new HashMap<>();
        List<Map.Entry<Object, NdfValue.Obj>> result = new ArrayList<>(topLevel.size());
        Map<Registration,List<NdfValue>> entries = new LinkedHashMap<>();
        Map<Registration,List<Map.Entry<NdfValue,NdfValue>>> maps = new LinkedHashMap<>();
        Map<Registration,NdfValue.Obj> catalogs = new LinkedHashMap<>();
        for (NdfDatabase.TopLevel t : topLevel) {
            if (t.exportPath != null) {
                result.add(new AbstractMap.SimpleImmutableEntry<>(t.exportPath, t.value));
                continue;
            }
            if (t.value.className.equals("TDeckSerializerEntries")) {
                // The engine registers each map independently, across every anonymous declaration.
                for (var field : t.value.fields) {
                    if (!(field.getValue() instanceof NdfValue.MapV map))
                        throw new NdfFormatException("Unexpected deck serializer field " + field.getKey());
                    Registration key = new Registration(t.value.className, field.getKey(), List.of());
                    catalogs.putIfAbsent(key, new NdfValue.Obj(t.value.className,List.of(field),t.value.origin));
                    maps.computeIfAbsent(key,k -> new ArrayList<>()).addAll(map.entries);
                }
                continue;
            }
            String collection = registrationCollection(t.value.className);
            if (collection != null && t.value.get(collection) != null) {
                var scope = t.value.fields.stream().filter(f -> !f.getKey().equals(collection)).toList();
                Registration key = new Registration(t.value.className, collection, scope);
                catalogs.putIfAbsent(key, t.value);
                if (t.value.get(collection) instanceof NdfValue.ListV list)
                    entries.computeIfAbsent(key, k -> new ArrayList<>()).addAll(list.items);
                else if (t.value.get(collection) instanceof NdfValue.MapV map)
                    maps.computeIfAbsent(key, k -> new ArrayList<>()).addAll(map.entries);
                else throw new NdfFormatException("Unexpected registration data in " + t.value.className);
                continue;
            }
            if (t.value.className.equals("TTowedUnitCatalogEntry") && t.value.get("Identifier") != null) {
                var identity = t.value.fields.stream().filter(f -> f.getKey().equals("Catalog") || f.getKey().equals("Identifier")).toList();
                result.add(Map.entry(new Registration(t.value.className, "", identity), t.value));
                continue;
            }
            int n = occurrences.getOrDefault(t.value, 0);
            occurrences.put(t.value, n + 1);
            result.add(new AbstractMap.SimpleImmutableEntry<>(new ConstantKey(t.value, n), t.value));
        }
        catalogs.forEach((key,root) -> {
            var fields = new ArrayList<>(root.fields);
            NdfValue combined = maps.containsKey(key) ? new NdfValue.MapV(firstEntries(maps.get(key))) : new NdfValue.ListV(infantryEntries(entries.get(key)));
            fields.replaceAll(f -> f.getKey().equals(key.collection()) ? NdfValue.field(key.collection(), combined) : f);
            result.add(Map.entry(key,new NdfValue.Obj(root.className,fields,root.origin)));
        });
        return firstEntries(result);
    }

    private static List<NdfValue> infantryEntries(List<NdfValue> entries) {
        Set<NdfValue> seen = new HashSet<>();
        return entries.stream().filter(entry -> !(entry instanceof NdfValue.Obj obj)
            || obj.get("Identifier") == null || seen.add(obj.get("Identifier"))).toList();
    }


    // ───────────── Deck serializer IDs ─────────────

    private static final List<String> DECK_ID_FIELDS = List.of("DivisionIds", "UnitIds");

    /**
     * WARNO refuses to load a deck serializer where two different units (or divisions) share one numeric ID:
     * "Unit ID '16000' conflict in DeckSerializer". Mods that each add units pick IDs independently, and the merge
     * unites their entries by key, so the combined mod can list one ID twice. The ID stays with the entry that
     * vanilla has, otherwise with the highest-priority mod, and every other entry gets the next free ID.
     * (Deck codes of a renumbered unit change; a crash on load is worse.)
     */
    private List<NdfDatabase.TopLevel> settleDeckIds(List<NdfDatabase.TopLevel> merged, NdfDatabase vanilla, List<Variant> variants) {
        // Rank 0 is vanilla, rank n is the n-th variant: who first declared each exact key and ID pair.
        Map<String, List<Map<NdfValue, NdfValue>>> declared = new HashMap<>();
        List<NdfDatabase> sources = new ArrayList<>();
        sources.add(vanilla);
        for (Variant v : variants) sources.add(v.database);
        for (String field : DECK_ID_FIELDS) {
            List<Map<NdfValue, NdfValue>> ranks = new ArrayList<>();
            for (NdfDatabase db : sources) {
                Map<NdfValue, NdfValue> pairs = new HashMap<>();
                if (db != null) for (NdfDatabase.TopLevel t : db.topLevel) {
                    if (t.exportPath != null || !t.value.className.equals("TDeckSerializerEntries")) continue;
                    if (t.value.get(field) instanceof NdfValue.MapV map) for (var e : map.entries) pairs.putIfAbsent(e.getKey(), e.getValue());
                }
                ranks.add(pairs);
            }
            declared.put(field, ranks);
        }

        List<NdfDatabase.TopLevel> result = new ArrayList<>(merged);
        for (String field : DECK_ID_FIELDS) {
            // Every entry of this field across the deck serializer roots, with where it lives.
            record Slot(int root, int index, NdfValue key, NdfValue id, int rank) {}
            List<Slot> slots = new ArrayList<>();
            for (int r = 0; r < result.size(); r++) {
                NdfDatabase.TopLevel t = result.get(r);
                if (t.exportPath != null || !t.value.className.equals("TDeckSerializerEntries")) continue;
                if (!(t.value.get(field) instanceof NdfValue.MapV map)) continue;
                for (int i = 0; i < map.entries.size(); i++) {
                    NdfValue key = map.entries.get(i).getKey();
                    NdfValue id = map.entries.get(i).getValue();
                    int rank = Integer.MAX_VALUE;
                    List<Map<NdfValue, NdfValue>> ranks = declared.get(field);
                    for (int k = 0; k < ranks.size(); k++) {
                        if (id.equals(ranks.get(k).get(key))) { rank = k; break; }
                    }
                    slots.add(new Slot(r, i, key, id, rank));
                }
            }
            Map<NdfValue, List<Slot>> byId = new LinkedHashMap<>();
            long highest = 0;
            for (Slot slot : slots) {
                byId.computeIfAbsent(slot.id(), k -> new ArrayList<>()).add(slot);
                Long n = idNumber(slot.id());
                if (n != null) highest = Math.max(highest, n);
            }
            Map<Long, Slot> renumbered = new HashMap<>();
            Map<Slot, NdfValue> replacement = new HashMap<>();
            long next = highest;
            for (Map.Entry<NdfValue, List<Slot>> group : byId.entrySet()) {
                List<Slot> sharing = group.getValue();
                if (sharing.size() < 2 || idNumber(group.getKey()) == null) continue;
                Slot keeper = sharing.get(0);
                for (Slot slot : sharing) if (slot.rank() < keeper.rank()) keeper = slot;
                List<String> losers = new ArrayList<>();
                StringBuilder detail = new StringBuilder();
                for (Slot slot : sharing) {
                    if (slot == keeper) continue;
                    next++;
                    replacement.put(slot, withIdNumber(group.getKey(), next));
                    String owner = slot.rank() == Integer.MAX_VALUE ? "the merge" : rankName(slot.rank(), variants);
                    if (!losers.contains(owner)) losers.add(owner);
                    if (detail.length() > 0) detail.append("; ");
                    detail.append(slot.key().describe()).append(" -> ").append(next);
                }
                conflicts.add(new Conflict("DeckSerializer." + field + " ID " + group.getKey().describe(),
                    rankName(keeper.rank(), variants), losers,
                    "kept for " + keeper.key().describe() + "; renumbered " + detail));
            }
            if (replacement.isEmpty()) continue;
            Map<Integer, Map<Integer, NdfValue>> byRoot = new HashMap<>();
            for (var e : replacement.entrySet()) byRoot.computeIfAbsent(e.getKey().root(), k -> new HashMap<>()).put(e.getKey().index(), e.getValue());
            for (var rootEdits : byRoot.entrySet()) {
                NdfDatabase.TopLevel t = result.get(rootEdits.getKey());
                NdfValue.MapV map = (NdfValue.MapV) t.value.get(field);
                List<Map.Entry<NdfValue, NdfValue>> entries = new ArrayList<>(map.entries);
                for (var edit : rootEdits.getValue().entrySet())
                    entries.set(edit.getKey(), NdfValue.entry(entries.get(edit.getKey()).getKey(), edit.getValue()));
                NdfValue.MapV fixed = new NdfValue.MapV(entries);
                List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(t.value.fields);
                fields.replaceAll(f -> f.getKey().equals(field) ? NdfValue.field(field, fixed) : f);
                result.set(rootEdits.getKey(), new NdfDatabase.TopLevel(t.exportPath,
                    new NdfValue.Obj(t.value.className, fields, t.value.origin)));
            }
        }
        return result;
    }

    private static String rankName(int rank, List<Variant> variants) {
        return rank == 0 ? "WARNO" : rank == Integer.MAX_VALUE ? "the merge" : variants.get(rank - 1).modName;
    }

    /** The number inside a 32-bit integer value (0x02 signed, 0x03 unsigned), or null for anything else. */
    private static Long idNumber(NdfValue value) {
        if (!(value instanceof NdfValue.Raw raw) || raw.data.length != 4 || (raw.type != 0x02 && raw.type != 0x03)) return null;
        int v = java.nio.ByteBuffer.wrap(raw.data).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
        return raw.type == 0x03 ? v & 0xFFFFFFFFL : (long) v;
    }

    private static NdfValue withIdNumber(NdfValue like, long number) {
        NdfValue.Raw raw = (NdfValue.Raw) like;
        byte[] data = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt((int) number).array();
        return new NdfValue.Raw(raw.type, data);
    }

    private static <K, V> Map<K, V> toMap(List<Map.Entry<K, V>> entries) {
        Map<K, V> map = new HashMap<>(entries.size() * 2);
        for (Map.Entry<K, V> e : entries) {
            if (map.put(e.getKey(), e.getValue()) != null)
                throw new NdfFormatException("The database lists " + describeKey(e.getKey()) + " twice.");
        }
        return map;
    }

    private static <K, V> List<Object> keyOrder(List<Map.Entry<K, V>> entries) {
        List<Object> order = new ArrayList<>(entries.size());
        for (Map.Entry<K, V> e : entries) order.add(e.getKey());
        return order;
    }

    private List<NdfDatabase.TopLevel> mergeTopLevel(NdfDatabase vanilla, List<Variant> variants, Cancel token) {
        List<Map.Entry<Object, NdfValue.Obj>> baseKeys = keys(vanilla.topLevel);
        List<List<Map.Entry<Object, NdfValue.Obj>>> variantKeys = new ArrayList<>();
        for (Variant v : variants) variantKeys.add(keys(v.database.topLevel));
        Map<Object, NdfValue.Obj> baseByKey = toMap(baseKeys);
        List<Map<Object, NdfValue.Obj>> variantMaps = new ArrayList<>();
        List<List<Object>> variantOrders = new ArrayList<>();
        for (List<Map.Entry<Object, NdfValue.Obj>> k : variantKeys) {
            variantMaps.add(toMap(k));
            variantOrders.add(keyOrder(k));
        }
        List<Object> order = mergeOrder(keyOrder(baseKeys), variantOrders);
        List<NdfDatabase.TopLevel> result = new ArrayList<>(order.size());
        Map<ConstantKey, NdfValue.Obj> merged = new HashMap<>();
        for (Object key : order) {
            token.check();
            if (key instanceof ConstantKey) {
                // Equal content wherever it comes from; the highest-priority mod's instance (and its debug line) is kept.
                NdfValue.Obj constant = null;
                for (Map<Object, NdfValue.Obj> m : variantMaps) {
                    constant = m.get(key);
                    if (constant != null) break;
                }
                if (constant == null) constant = baseByKey.get(key);
                merged.put((ConstantKey) key, constant);
                if (baseByKey.containsKey(key) && variantMaps.stream().anyMatch(m -> !m.containsKey(key))) removedConstants.add(constant);
                result.add(new NdfDatabase.TopLevel(null, constant));
                continue;
            }
            String path = key instanceof Registration registration ? registration.type() : (String) key;
            List<Edit> edits = new ArrayList<>(variants.size());
            for (int i = 0; i < variants.size(); i++) edits.add(new Edit(variants.get(i).modName, variantMaps.get(i).get(key)));
            boolean[] changed = new boolean[1];
            NdfValue value = mergeValue(shortName(path), baseByKey.get(key), edits, changed, true);
            if (changed[0]) changedObjects++;
            if (value instanceof NdfValue.Obj) result.add(new NdfDatabase.TopLevel(key instanceof Registration ? null : path, (NdfValue.Obj) value));
            else if (value != null) throw new NdfFormatException("Export " + path + " merged to a non-object value.");
        }
        List<List<Map.Entry<Object, NdfValue.Obj>>> all = new ArrayList<>();
        all.add(baseKeys);
        all.addAll(variantKeys);
        for (List<Map.Entry<Object, NdfValue.Obj>> keys : all) {
            for (Map.Entry<Object, NdfValue.Obj> e : keys)
                if (e.getKey() instanceof ConstantKey) constants.put(e.getValue(), merged.get(e.getKey()));
        }
        return result;
    }

    // Without vanilla (asset catalogs), anonymous roots are paired by position and every variant is treated as an
    // addition; differing values resolve by priority.
    private List<NdfDatabase.TopLevel> mergeWithoutBase(List<Variant> variants, Cancel token) {
        if (variants.stream().anyMatch(v -> v.database.topLevel.stream().anyMatch(NdfMerger::isRegistration))) {
            var registrations = new ArrayList<Variant>();
            var others = new ArrayList<Variant>();
            for (var variant : variants) {
                var registered = new ArrayList<NdfDatabase.TopLevel>();
                var remaining = new ArrayList<NdfDatabase.TopLevel>();
                for (var root : variant.database.topLevel) (isRegistration(root) ? registered : remaining).add(root);
                registrations.add(new Variant(variant.modName, new NdfDatabase(registered, variant.database.propertyOwners)));
                others.add(new Variant(variant.modName, new NdfDatabase(remaining, variant.database.propertyOwners)));
            }
            var result = mergeWithoutBase(others, token);
            result.addAll(mergeTopLevel(new NdfDatabase(List.of(),Map.of()), registrations, token));
            return result;
        }
        List<NdfDatabase.TopLevel> first = variants.get(0).database.topLevel;
        int count = first.size();
        for (Variant v : variants) {
            if (v.database.topLevel.size() != count) throw new NdfFormatException("Databases without a vanilla baseline have different top-level layouts.");
            for (int i = 0; i < count; i++) {
                if (!Objects.equals(v.database.topLevel.get(i).exportPath, first.get(i).exportPath))
                    throw new NdfFormatException("Databases without a vanilla baseline have different top-level layouts.");
            }
        }
        List<NdfDatabase.TopLevel> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            token.check();
            String path = first.get(i).exportPath;
            List<Edit> edits = new ArrayList<>(variants.size());
            for (Variant v : variants) edits.add(new Edit(v.modName, v.database.topLevel.get(i).value));
            boolean[] changed = new boolean[1];
            NdfValue.Obj value = (NdfValue.Obj) mergeValue(path == null ? "#" + i : shortName(path), null, edits, changed, false);
            if (changed[0]) changedObjects++;
            result.add(new NdfDatabase.TopLevel(path, value));
            // A private constant here is the one at the same position in every mod.
            if (path == null) for (Variant v : variants) constants.put(v.database.topLevel.get(i).value, value);
        }
        return result;
    }

    private static String shortName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // ───────────── Values ─────────────

    /** Merges one value. Edits are (mod, value) pairs, highest priority first; a null value means absent. */
    private NdfValue mergeValue(String path, NdfValue baseValue, List<Edit> variants, boolean[] changed, boolean exported) {
        List<Edit> edits = new ArrayList<>();
        for (Edit v : variants) if (!NdfValue.same(v.value, baseValue)) edits.add(v);
        changed[0] = !edits.isEmpty();
        if (edits.isEmpty()) return baseValue;
        for (Edit edit : edits) changedBy.add(edit.mod);
        boolean allSame = true;
        for (Edit e : edits) if (!NdfValue.same(e.value, edits.get(0).value)) { allSame = false; break; }
        if (allSame) return edits.get(0).value;

        List<NdfValue> candidates = new ArrayList<>();
        boolean allPresent = true;
        for (Edit e : edits) {
            if (e.value == null) allPresent = false;
            else candidates.add(e.value);
        }
        if (baseValue != null) candidates.add(baseValue);
        if (allPresent) {
            NdfValue first = candidates.get(0);
            if (candidates.stream().allMatch(v -> v instanceof NdfValue.Obj && first instanceof NdfValue.Obj
                && ((NdfValue.Obj) v).className.equals(((NdfValue.Obj) first).className)
                && (exported || NdfValue.same(descriptorId((NdfValue.Obj) v), descriptorId((NdfValue.Obj) first)))))
                return mergeObject(path, baseValue instanceof NdfValue.Obj ? (NdfValue.Obj) baseValue : null, variants);
            if (candidates.stream().allMatch(v -> v instanceof NdfValue.ListV)) {
                NdfValue.ListV list = tryMergeList(path, baseValue instanceof NdfValue.ListV ? (NdfValue.ListV) baseValue : null, variants);
                if (list != null) return list;
            }
            if (candidates.stream().allMatch(v -> v instanceof NdfValue.MapV))
                return mergeMap(path, baseValue instanceof NdfValue.MapV ? (NdfValue.MapV) baseValue : null, variants);
            if (candidates.stream().allMatch(v -> v instanceof NdfValue.Pair))
                return mergePair(path, baseValue instanceof NdfValue.Pair ? (NdfValue.Pair) baseValue : null, variants);
        }

        Edit winner = edits.get(0);
        LinkedHashSet<String> overridden = new LinkedHashSet<>();
        for (int i = 1; i < edits.size(); i++) if (!NdfValue.same(edits.get(i).value, winner.value)) overridden.add(edits.get(i).mod);
        conflicts.add(new Conflict(path, winner.mod, new ArrayList<>(overridden), winner.value != null ? winner.value.describe() : "removed"));
        return winner.value;
    }

    private NdfValue.Obj mergeObject(String path, NdfValue.Obj baseValue, List<Edit> variants) {
        NdfValue[] items = new NdfValue[variants.size() + 1];
        items[0] = baseValue;
        for (int i = 0; i < variants.size(); i++) items[i + 1] = variants.get(i).value;
        InstanceKey key = new InstanceKey(items);
        NdfValue.Obj done = mergedObjects.get(key);
        if (done != null) return done;
        NdfValue.Obj built = buildMergedObject(path, baseValue, variants);
        mergedObjects.put(key, built);
        return built;
    }

    private NdfValue.Obj buildMergedObject(String path, NdfValue.Obj baseValue, List<Edit> variants) {
        NdfValue.Obj firstVariant = null;
        for (Edit v : variants) if (v.value instanceof NdfValue.Obj) { firstVariant = (NdfValue.Obj) v.value; break; }
        String className = firstVariant.className;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (baseValue != null) for (Map.Entry<String, NdfValue> f : baseValue.fields) names.add(f.getKey());
        for (Edit v : variants)
            if (v.value instanceof NdfValue.Obj) for (Map.Entry<String, NdfValue> f : ((NdfValue.Obj) v.value).fields) names.add(f.getKey());
        List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(names.size());
        for (String name : names) {
            NdfValue baseField = baseValue != null ? baseValue.get(name) : null;
            List<Edit> edits = new ArrayList<>(variants.size());
            for (Edit v : variants) edits.add(new Edit(v.mod, v.value instanceof NdfValue.Obj ? ((NdfValue.Obj) v.value).get(name) : baseField));
            NdfValue value = mergeValue(path + " › " + name, baseField, edits, new boolean[1], false);
            if (value != null) fields.add(NdfValue.field(name, value));
        }
        // The merged object takes the place of vanilla's (or else the first mod's), so it keeps that identity.
        NdfOrigin origin = (baseValue != null ? baseValue : firstVariant).origin;
        return new NdfValue.Obj(className, fields, origin);
    }

    private NdfValue.Pair mergePair(String path, NdfValue.Pair baseValue, List<Edit> variants) {
        NdfValue baseFirst = baseValue != null ? baseValue.first : null;
        NdfValue baseSecond = baseValue != null ? baseValue.second : null;
        List<Edit> firsts = new ArrayList<>(), seconds = new ArrayList<>();
        for (Edit v : variants) {
            NdfValue.Pair pair = (NdfValue.Pair) v.value;
            firsts.add(new Edit(v.mod, pair != null ? pair.first : baseFirst));
            seconds.add(new Edit(v.mod, pair != null ? pair.second : baseSecond));
        }
        NdfValue first = mergeValue(path + ".1", baseFirst, firsts, new boolean[1], false);
        NdfValue second = mergeValue(path + ".2", baseSecond, seconds, new boolean[1], false);
        if (first == null || second == null) throw new NdfFormatException(path + " lost a pair element.");
        return new NdfValue.Pair(first, second);
    }

    private NdfValue.MapV mergeMap(String path, NdfValue.MapV baseValue, List<Edit> variants) {
        Function<NdfValue.MapV, List<Map.Entry<Object, NdfValue>>> keyed = map -> {
            List<Map.Entry<Object, NdfValue>> list = new ArrayList<>(map.entries.size());
            for (Map.Entry<NdfValue, NdfValue> e : map.entries) list.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()));
            return list;
        };
        List<KeyedEdit> edits = new ArrayList<>();
        for (Edit v : variants) edits.add(new KeyedEdit(v.mod, v.value instanceof NdfValue.MapV ? keyed.apply((NdfValue.MapV) v.value) : null));
        List<Map.Entry<Object, NdfValue>> entries = mergeKeyed(path, baseValue == null ? null : keyed.apply(baseValue), edits);
        List<Map.Entry<NdfValue, NdfValue>> result = new ArrayList<>(entries.size());
        for (Map.Entry<Object, NdfValue> e : entries) result.add(NdfValue.entry((NdfValue) e.getKey(), e.getValue()));
        return new NdfValue.MapV(result);
    }

    private NdfValue.ListV tryMergeList(String path, NdfValue.ListV baseValue, List<Edit> variants) {
        List<NdfValue.ListV> lists = new ArrayList<>();
        if (baseValue != null) lists.add(baseValue);
        for (Edit v : variants) if (v.value instanceof NdfValue.ListV) lists.add((NdfValue.ListV) v.value);
        boolean allKeyed = true;
        for (NdfValue.ListV l : lists) if (keyList(l) == null) { allKeyed = false; break; }
        if (allKeyed) {
            List<KeyedEdit> edits = new ArrayList<>();
            for (Edit v : variants) edits.add(new KeyedEdit(v.mod, v.value instanceof NdfValue.ListV ? keyList((NdfValue.ListV) v.value) : null));
            List<Map.Entry<Object, NdfValue>> entries = mergeKeyed(path, baseValue == null ? null : keyList(baseValue), edits);
            List<NdfValue> items = new ArrayList<>(entries.size());
            for (Map.Entry<Object, NdfValue> e : entries) items.add(e.getValue());
            return new NdfValue.ListV(items);
        }
        // Same-length lists merge element by element; without a base (asset catalogs) each element is an addition.
        int size = lists.get(0).items.size();
        if (lists.stream().allMatch(l -> l.items.size() == size)) {
            List<NdfValue> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int index = i;
                List<Edit> edits = new ArrayList<>();
                for (Edit v : variants) edits.add(new Edit(v.mod, v.value instanceof NdfValue.ListV ? ((NdfValue.ListV) v.value).items.get(index) : null));
                NdfValue item = mergeValue(path + "[" + i + "]", baseValue != null ? baseValue.items.get(i) : null, edits, new boolean[1], false);
                if (item == null) throw new NdfFormatException(path + "[" + i + "] was removed.");
                items.add(item);
            }
            return new NdfValue.ListV(items);
        }
        return null;
    }

    /** Identity of a list element by class and name-like field. */
    private static final class ClassKey {
        final String className;
        final NdfValue id;

        ClassKey(String className, NdfValue id) {
            this.className = className;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ClassKey && ((ClassKey) o).className.equals(className) && ((ClassKey) o).id.equals(id);
        }

        @Override
        public int hashCode() {
            return 31 * className.hashCode() + id.hashCode();
        }

        @Override
        public String toString() {
            return className + " " + id.describe();
        }
    }

    /** Keys list elements by identity (class plus name-like field, or the value itself); null if ambiguous. */
    private static List<Map.Entry<Object, NdfValue>> keyList(NdfValue.ListV list) {
        Set<Object> keys = new HashSet<>();
        List<Map.Entry<Object, NdfValue>> result = new ArrayList<>(list.items.size());
        for (NdfValue item : list.items) {
            Object key = null;
            if (item instanceof NdfValue.Obj) {
                NdfValue.Obj o = (NdfValue.Obj) item;
                NdfValue id = null;
                for (String field : IDENTITY_FIELDS) {
                    id = o.get(field);
                    if (id != null) break;
                }
                if (o.className.equals("TTransportedInfantryEntry")) id = o.get("Identifier");
                key = id != null ? new ClassKey(o.className, id) : o.className;
            } else if (item instanceof NdfValue.Text || item instanceof NdfValue.Import || item instanceof NdfValue.ExportRef || item instanceof NdfValue.Raw) {
                key = item;
            }
            if (key == null || !keys.add(key)) return null;
            result.add(new AbstractMap.SimpleImmutableEntry<>(key, item));
        }
        return result;
    }

    private static String describeKey(Object key) {
        if (key instanceof NdfValue) return ((NdfValue) key).describe();
        if (key instanceof ConstantKey) return "a " + ((ConstantKey) key).content.className + " constant";
        return String.valueOf(key);
    }

    private static final class KeyedEdit {
        final String mod;
        final List<Map.Entry<Object, NdfValue>> entries;

        KeyedEdit(String mod, List<Map.Entry<Object, NdfValue>> entries) {
            this.mod = mod;
            this.entries = entries;
        }
    }

    private List<Map.Entry<Object, NdfValue>> mergeKeyed(String path, List<Map.Entry<Object, NdfValue>> baseEntries, List<KeyedEdit> variants) {
        Map<Object, NdfValue> baseMap = baseEntries == null ? Collections.emptyMap() : toMap(baseEntries);
        List<Map<Object, NdfValue>> maps = new ArrayList<>();
        for (KeyedEdit v : variants) maps.add(v.entries == null ? null : toMap(v.entries));
        List<Object> baseOrder = baseEntries == null ? new ArrayList<>() : keyOrder(baseEntries);
        List<List<Object>> changedOrders = new ArrayList<>();
        for (KeyedEdit v : variants) {
            if (v.entries == null) continue;
            List<Object> order = keyOrder(v.entries);
            if (baseEntries == null || !order.equals(baseOrder)) changedOrders.add(order);
        }
        List<Object> order = mergeOrder(baseOrder, changedOrders);
        List<Map.Entry<Object, NdfValue>> result = new ArrayList<>(order.size());
        for (Object key : order) {
            NdfValue baseValue = baseMap.get(key);
            List<Edit> edits = new ArrayList<>(variants.size());
            for (int i = 0; i < variants.size(); i++) edits.add(new Edit(variants.get(i).mod, maps.get(i) != null ? maps.get(i).get(key) : baseValue));
            NdfValue value = mergeValue(path + "[" + describeKey(key) + "]", baseValue, edits, new boolean[1], false);
            if (value != null) result.add(new AbstractMap.SimpleImmutableEntry<>(key, value));
        }
        return result;
    }

    /**
     * Combines key orders: the highest-priority order that differs from the base is the skeleton, and keys only other
     * sequences contain are inserted after their nearest preceding key.
     */
    private static List<Object> mergeOrder(List<Object> baseOrder, List<List<Object>> others) {
        List<List<Object>> sequences = new ArrayList<>();
        for (List<Object> o : others) if (!o.equals(baseOrder)) sequences.add(o);
        sequences.add(baseOrder);
        List<Object> result = new ArrayList<>(sequences.get(0));
        Set<Object> positions = new HashSet<>(result);
        List<List<Object>> rest = new ArrayList<>(sequences.subList(1, sequences.size()));
        rest.addAll(others);
        for (List<Object> sequence : rest) {
            // Most sequences add nothing; only build a position index when one does.
            Map<Object, Integer> index = null;
            for (int i = 0; i < sequence.size(); i++) {
                Object key = sequence.get(i);
                if (positions.contains(key)) continue;
                if (index == null) index = indexOf(result);
                int insertAt = 0;
                for (int j = i - 1; j >= 0; j--) {
                    Integer anchor = index.get(sequence.get(j));
                    if (anchor != null) { insertAt = anchor + 1; break; }
                }
                result.add(insertAt, key);
                positions.add(key);
                index = indexOf(result);
            }
        }
        return result;
    }

    private static Map<Object, Integer> indexOf(List<Object> list) {
        Map<Object, Integer> index = new HashMap<>(list.size() * 2);
        for (int i = list.size() - 1; i >= 0; i--) index.put(list.get(i), i);
        return index;
    }

    // ───────────── Clean-up ─────────────

    /** Re-adds any export a merged value references but a mod deleted, so references stay valid. */
    private List<NdfDatabase.TopLevel> restoreMissingExports(List<NdfDatabase.TopLevel> merged, NdfDatabase vanilla, List<Variant> variants) {
        Set<String> present = new HashSet<>();
        for (NdfDatabase.TopLevel t : merged) if (t.exportPath != null) present.add(t.exportPath);
        List<String> names = new ArrayList<>();
        List<Map<String, NdfValue.Obj>> sources = new ArrayList<>();
        for (Variant v : variants) {
            names.add(v.modName);
            sources.add(exportsOf(v.database));
        }
        names.add("vanilla");
        sources.add(vanilla == null ? null : exportsOf(vanilla));
        ArrayDeque<NdfDatabase.TopLevel> pending = new ArrayDeque<>(merged);
        Map<NdfValue.Obj, Boolean> visited = new IdentityHashMap<>();
        while (!pending.isEmpty()) {
            NdfDatabase.TopLevel top = pending.poll();
            for (String path : exportRefs(top.value, visited)) {
                if (present.contains(path)) continue;
                int sourceIndex = -1;
                for (int i = 0; i < sources.size(); i++) if (sources.get(i) != null && sources.get(i).containsKey(path)) { sourceIndex = i; break; }
                if (sourceIndex < 0) throw new NdfFormatException("Merged database references " + path + ", which no input defines.");
                NdfDatabase.TopLevel restored = new NdfDatabase.TopLevel(path, sources.get(sourceIndex).get(path));
                List<String> removedBy = new ArrayList<>();
                for (Variant v : variants) if (v.database.topLevel.stream().noneMatch(t -> path.equals(t.exportPath))) removedBy.add(v.modName);
                conflicts.add(new Conflict(shortName(path), names.get(sourceIndex), removedBy, "kept because other merged content still references it"));
                merged.add(restored);
                present.add(path);
                pending.add(restored);
            }
        }
        return merged;
    }

    private static Map<String, NdfValue.Obj> exportsOf(NdfDatabase db) {
        Map<String, NdfValue.Obj> exports = new HashMap<>();
        for (NdfDatabase.TopLevel t : db.topLevel) if (t.exportPath != null) exports.putIfAbsent(t.exportPath, t.value);
        return exports;
    }

    private static List<String> exportRefs(NdfValue value, Map<NdfValue.Obj, Boolean> visited) {
        List<String> refs = new ArrayList<>();
        ArrayDeque<NdfValue> stack = new ArrayDeque<>();
        stack.push(value);
        while (!stack.isEmpty()) {
            NdfValue current = stack.pop();
            if (current instanceof NdfValue.ExportRef) refs.add(((NdfValue.ExportRef) current).path);
            else NdfRewrite.pushChildren(current, stack, visited);
        }
        return refs;
    }

    /**
     * Merged values still point at vanilla's or a mod's own instance of a private constant; each such reference is
     * pointed at the merged constant it became (see {@link ConstantKey}). A constant some mod removed, or that the
     * inputs used, is dropped once nothing in the merged database references it; one WARNO itself keeps unreferenced
     * stays.
     */
    private List<NdfDatabase.TopLevel> settleConstants(List<NdfDatabase.TopLevel> merged, List<NdfDatabase> sources) {
        Set<NdfValue.Obj> used = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NdfDatabase source : sources) {
            for (NdfValue.Obj referenced : referencedObjects(source.topLevel)) {
                NdfValue.Obj constant = constants.get(referenced);
                if (constant != null) used.add(constant);
            }
        }
        NdfRewrite rewrite = new NdfRewrite(value -> {
            NdfValue.Obj constant = constants.get(value);
            return constant != null && constant != value ? constant : null;
        }, null);
        List<NdfValue.Obj> originals = new ArrayList<>(merged.size());
        List<NdfDatabase.TopLevel> settled = new ArrayList<>(merged.size());
        for (NdfDatabase.TopLevel t : merged) {
            originals.add(t.value);
            settled.add(t.withValue(rewrite.object(t.value)));
        }
        Set<NdfValue.Obj> referencedNow = referencedObjects(settled);
        List<NdfDatabase.TopLevel> result = new ArrayList<>(settled.size());
        for (int i = 0; i < settled.size(); i++) {
            NdfDatabase.TopLevel top = settled.get(i);
            NdfValue.Obj original = originals.get(i);
            if (top.exportPath != null || referencedNow.contains(top.value) || (!used.contains(original) && !removedConstants.contains(original)))
                result.add(top);
        }
        return result;
    }

    /** Every object some field references (as opposed to objects that are only top-level roots). */
    private static Set<NdfValue.Obj> referencedObjects(List<NdfDatabase.TopLevel> topLevel) {
        Map<NdfValue.Obj, Boolean> visited = new IdentityHashMap<>();
        ArrayDeque<NdfValue> stack = new ArrayDeque<>();
        for (NdfDatabase.TopLevel top : topLevel) {
            if (visited.put(top.value, Boolean.TRUE) == null)
                for (Map.Entry<String, NdfValue> field : top.value.fields) stack.push(field.getValue());
        }
        Set<NdfValue.Obj> referenced = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!stack.isEmpty()) {
            NdfValue current = stack.pop();
            if (current instanceof NdfValue.Obj) referenced.add((NdfValue.Obj) current);
            NdfRewrite.pushChildren(current, stack, visited);
        }
        return referenced;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A tuple of instances compared by reference. */
    private static final class InstanceKey {
        private final NdfValue[] items;
        private final int hash;

        InstanceKey(NdfValue[] items) {
            this.items = items;
            int h = 1;
            for (NdfValue item : items) h = 31 * h + System.identityHashCode(item);
            hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof InstanceKey) || ((InstanceKey) o).items.length != items.length) return false;
            for (int i = 0; i < items.length; i++) if (((InstanceKey) o).items[i] != items[i]) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
