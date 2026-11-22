package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.*;
import com.warnomodmaker.combine.ndf.NdfValue.*;
import java.nio.file.*;
import java.util.*;

/** Family numbers are local to BaseWeapon. Normalize each contributor before comparing database values. */
final class DamageFamilies {
    static final String BASE = "Gen\\NDF\\GFX\\BaseWeapon.txn";
    static final String DIR = "GameData\\Generated\\Gameplay\\Gfx\\";
    private record Slot(String name, int level) {}
    private static final class Axis {
        final List<String> names = new ArrayList<>();
        final Map<String,Integer> counts = new LinkedHashMap<>();
        final List<Slot> slots = new ArrayList<>();
        final Map<Slot,Integer> offsets = new HashMap<>();

        void include(String name, int count) {
            if (!counts.containsKey(name)) names.add(name);
            counts.merge(name, count, Math::max);
        }
        void index() {
            for (String name : names) for (int level = 1; level <= counts.get(name); level++) {
                Slot slot = new Slot(name, level);
                offsets.put(slot, slots.size());
                slots.add(slot);
            }
        }
    }
    private static final class Schema {
        final Axis damage = new Axis(), armor = new Axis();
        ListV matrix;
        Schema() {}
        Schema(byte[] bytes) {
            Map<String,Obj> objects = new HashMap<>();
            for (var entry : NdfDatabase.load(bytes).topLevel) objects.put(entry.value.className, entry.value);
            Obj container = Objects.requireNonNull(objects.get("TGameplayDamageResistanceContainer"), "Missing damage table");
            readAxis(damage, objects.get("TDamageFamilyList"), container.get("DamageFamilyCounts"));
            readAxis(armor, objects.get("TResistanceFamilyList"), container.get("ResistanceFamilyCounts"));
            matrix = (ListV)container.get("Values");
            if (matrix.items.size() != damage.slots.size() || matrix.items.stream().anyMatch(row -> ((ListV)row).items.size() != armor.slots.size()))
                throw new CombineException("The damage matrix dimensions disagree with its family counts.");
        }
        private static void readAxis(Axis axis, Obj list, NdfValue counts) {
            List<NdfValue> names = ((ListV)list.get("Values")).items;
            for (NdfValue item : ((ListV)counts).items) {
                Pair pair = (Pair)item;
                int id = number(pair.first);
                if (id != axis.names.size()) throw new CombineException("Unordered damage or resistance family counts.");
                axis.include(((Text)names.get(id)).text, number(pair.second));
            }
            axis.index();
        }
        NdfValue cell(Slot d, Slot a) {
            Integer row = damage.offsets.get(d), col = armor.offsets.get(a);
            return row == null || col == null ? null : ((ListV)matrix.items.get(row)).items.get(col);
        }
    }
    private final Schema merged = new Schema();
    private final Schema vanilla;
    private final Map<String,Schema> schemas = new LinkedHashMap<>();
    private final Map<String,String> sources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static DamageFamilies open(List<MergePlanner.CompiledInput> inputs, VanillaDatabases db) {
        if (inputs.stream().noneMatch(input -> Files.isRegularFile(input.root.resolve(BASE)))) return null;
        byte[] base = db.tryRead(BASE);
        if (base == null) return null;
        DamageFamilies result = new DamageFamilies(base, inputs);
        return result.sources.isEmpty() ? null : result;
    }
    DamageFamilies(byte[] base, List<MergePlanner.CompiledInput> inputs) {
        vanilla = new Schema(base);
        for (var input : inputs) {
            Path file = input.root.resolve(BASE);
            schemas.put(input.name, Files.isRegularFile(file) ? new Schema(FileOps.read(file)) : vanilla);
        }
        List<Schema> all = new ArrayList<>();
        all.add(vanilla);
        all.addAll(schemas.values());
        if (schemas.values().stream().allMatch(s -> s.damage.counts.equals(vanilla.damage.counts) && s.armor.counts.equals(vanilla.armor.counts)
                && s.damage.names.equals(vanilla.damage.names) && s.armor.names.equals(vanilla.armor.names))) return;
        for (Schema schema : all) {
            schema.damage.counts.forEach(merged.damage::include);
            schema.armor.counts.forEach(merged.armor::include);
        }
        merged.damage.index();
        merged.armor.index();
        List<NdfValue> rows = new ArrayList<>();
        Raw neutral = new Raw(0x05, new byte[]{0,0,(byte)128,63});
        for (Slot d : merged.damage.slots) {
            List<NdfValue> row = new ArrayList<>();
            for (Slot a : merged.armor.slots) {
                NdfValue original = vanilla.cell(d,a), winner = original;
                for (Schema schema : schemas.values()) {
                    NdfValue cell = schema.cell(d,a);
                    if (cell != null && !cell.equals(original)) { winner = cell; break; }
                }
                row.add(winner == null ? neutral : winner);
            }
            rows.add(new ListV(row));
        }
        merged.matrix = new ListV(rows);
        makeSources();
    }
    String source(String path) { return sources.get(path); }
    boolean applies(String path, List<MergePlanner.CompiledInput> inputs) {
        if (path.equalsIgnoreCase(BASE)) return true;
        if (!NdfLayout.isTransactionDatabase(path)) return false;
        return inputs.stream().anyMatch(input -> {
            Schema s = schemas.getOrDefault(input.name, vanilla);
            return !prefix(s.damage, merged.damage) || !prefix(s.armor, merged.armor);
        });
    }
    private static boolean prefix(Axis a, Axis b) { return b.names.subList(0,a.names.size()).equals(a.names); }
    private static int number(NdfValue value) {
        Raw raw = (Raw)value;
        if (raw.data.length == 0 || raw.data.length > 4) throw new CombineException("Invalid family index encoding.");
        int n = 0;
        for (int i = raw.data.length-1; i >= 0; i--) n = (n << 8) | Byte.toUnsignedInt(raw.data[i]);
        return n;
    }
    private static Raw integer(Raw template, int n) {
        byte[] bytes = new byte[template.data.length];
        for (int i = 0; i < bytes.length; i++) { bytes[i] = (byte)n; n >>>= 8; }
        if (n != 0) throw new CombineException("Family index exceeds the field size.");
        return new Raw(template.type, bytes);
    }
    private static NdfValue translate(NdfValue v, Axis from, Axis to) {
        if (!(v instanceof Raw raw)) return v;
        int id = number(raw);
        if (id == -1 || (raw.data.length == 1 && id == 255)) return v;
        if (id < 0 || id >= from.names.size()) throw new CombineException("Unknown family index " + id + " in a database.");
        int replacement = to.names.indexOf(from.names.get(id));
        return replacement == id ? v : integer(raw,replacement);
    }
    private static NdfValue indices(NdfValue v, Axis src, Axis dst) {
        if (v instanceof ListV list) return new ListV(list.items.stream().map(x -> indices(x,src,dst)).toList());
        if (v instanceof Pair pair) return new Pair(indices(pair.first,src,dst),pair.second);
        if (v instanceof MapV map) return new MapV(map.entries.stream().map(e -> NdfValue.entry(indices(e.getKey(),src,dst),e.getValue())).toList());
        return translate(v,src,dst);
    }
    NdfDatabase normalize(String mod, NdfDatabase db) {
        Schema s = schemas.getOrDefault(mod, vanilla);
        return new NdfRewrite(null,null,o -> {
            List<Map.Entry<String,NdfValue>> fields = new ArrayList<>();
            boolean changed = false;
            for (var field : o.fields) {
                String key = field.getKey();
                NdfValue v = field.getValue(), out = v;
                if (o.className.equals("TDamageFamilyList") || o.className.equals("TResistanceFamilyList")) {
                    if (key.equals("Values")) {
                        Axis axis = o.className.equals("TDamageFamilyList") ? merged.damage : merged.armor;
                        out = new ListV(axis.names.stream().<NdfValue>map(n -> new Text(7,n)).toList());
                    }
                } else if (o.className.equals("TGameplayDamageResistanceContainer")) {
                    if (key.equals("Values")) out = merged.matrix;
                    if (key.equals("DamageFamilyCounts") || key.equals("ResistanceFamilyCounts")) {
                        Axis axis = key.equals("DamageFamilyCounts") ? merged.damage : merged.armor;
                        Pair p = (Pair)((ListV)v).items.getFirst();
                        List<NdfValue> counts = new ArrayList<>();
                        for (String n : axis.names) counts.add(new Pair(integer((Raw)p.first,counts.size()),integer((Raw)p.second,axis.counts.get(n))));
                        out = new ListV(counts);
                    }
                } else if (key.equals("Family") && o.className.equals("TDamageTypeRTTI")) out = translate(v,s.damage,merged.damage);
                else if (key.equals("Family") && o.className.equals("TResistanceTypeRTTI")) out = translate(v,s.armor,merged.armor);
                else out = switch(key) {
                    case "DamageFamilyType","SafeDamageType","AlwaysThreateningDamageFamily","AttackDamageTypeThreat","MacroActionArtilleryDamageTypeThreat","DamageTypeForPrincipalAmmo","DamageWithMenaceForDangerounessArmor","StressMultiplierPerDamageFamily","SuppressDamagePerFamily","DamageTypeToMimeticProjectile","PierceBonusForDamageFamilies","DamageTypeNameToFeedbackType","TextForDamageType","WeaponTypes" -> indices(v,s.damage,merged.damage);
                    case "ResistanceFamilyType","ResistanceFamiliesNeedPiercing","ResistanceToMimeticImpact","HidePlatingPanelForResistance" -> indices(v,s.armor,merged.armor);
                    case "DamageModifierPerFamilyAndResistance","BlindagesToIgnoreForDamageFamilies" -> v instanceof MapV m ? new MapV(m.entries.stream().map(e -> NdfValue.entry(indices(e.getKey(),s.damage,merged.damage),indices(e.getValue(),s.armor,merged.armor))).toList()) : v;
                    default -> v;
                };
                changed |= !v.equals(out);
                fields.add(NdfValue.field(key,out));
            }
            return changed ? new Obj(o.className,fields,o.origin) : o;
        }).apply(db);
    }
    private void makeSources() {
        StringBuilder ids = new StringBuilder(), lists = new StringBuilder();
        StringBuilder table = new StringBuilder("private DamageResistanceParams is TGameplayDamageResistanceContainer\n(\n");
        for (Axis axis : List.of(merged.armor,merged.damage)) {
            String type = axis == merged.armor ? "ResistanceFamily" : "DamageFamily";
            lists.append("Generated_").append(type).append("_Enum is T").append(type).append("List\n(\n    Values = [\n");
            table.append("    ").append(type).append("Counts = [\n");
            for (int i=0;i<axis.names.size();i++) {
                String name = axis.names.get(i);
                ids.append(name).append(" is ").append(i).append('\n');
                lists.append("        \"").append(name).append("\",\n");
                table.append("        (").append(name).append(", ").append(axis.counts.get(name)).append("),\n");
            }
            lists.append("    ]\n)\n");
            table.append("    ]\n");
        }
        table.append("    Values = [\n");
        for (NdfValue row : merged.matrix.items) {
            StringJoiner cells = new StringJoiner(", ","        [","],\n");
            for (NdfValue cell : ((ListV)row).items) {
                Raw raw = (Raw)cell;
                if (raw.type != 5) throw new CombineException("Unexpected damage multiplier encoding.");
                cells.add(Float.toString(Float.intBitsToFloat(number(raw))));
            }
            table.append(cells);
        }
        sources.put(DIR+"DamageResistance.ndf", table.append("    ]\n)\n").toString());
        sources.put(DIR+"DamageResistanceFamilyList.ndf", ids.toString());
        sources.put(DIR+"DamageResistanceFamilyListImpl.ndf", lists.toString());
    }
}
