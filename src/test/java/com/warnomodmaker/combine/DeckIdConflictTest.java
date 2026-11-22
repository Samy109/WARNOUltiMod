package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.*;
import com.warnomodmaker.combine.ndf.NdfValue.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Two mods that add different units with the same deck ID must not produce a deck serializer WARNO rejects:
 * "Unit ID '16000' conflict in DeckSerializer".
 */
public final class DeckIdConflictTest {
    private static Text name(String n) { return new Text(7, n); }

    private static Raw id(int type, long n) {
        return new Raw(type, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) n).array());
    }

    private static NdfDatabase deck(Map<String, Long> units, Map<String, Long> divisions, int type) {
        List<Map.Entry<NdfValue, NdfValue>> u = new ArrayList<>();
        units.forEach((k, v) -> u.add(NdfValue.entry(name(k), id(type, v))));
        List<Map.Entry<NdfValue, NdfValue>> d = new ArrayList<>();
        divisions.forEach((k, v) -> d.add(NdfValue.entry(name(k), id(type, v))));
        var root = new Obj("TDeckSerializerEntries", List.of(
            NdfValue.field("DivisionIds", new MapV(d)), NdfValue.field("UnitIds", new MapV(u))), null);
        return new NdfDatabase(List.of(new NdfDatabase.TopLevel(null, root)), Map.of());
    }

    private static Map<String, Long> ids(NdfDatabase db, String field) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (var t : db.topLevel) {
            if (!t.value.className.equals("TDeckSerializerEntries")) continue;
            if (!(t.value.get(field) instanceof MapV map)) continue;
            for (var e : map.entries) {
                Raw raw = (Raw) e.getValue();
                int v = ByteBuffer.wrap(raw.data).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (out.put(((Text) e.getKey()).text, raw.type == 0x03 ? v & 0xFFFFFFFFL : v) != null)
                    throw new AssertionError("key listed twice");
            }
        }
        return out;
    }

    private static void assertUnique(Map<String, Long> ids) {
        if (new HashSet<>(ids.values()).size() != ids.size()) throw new AssertionError("Duplicate IDs remain: " + ids);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int type : new int[]{0x02, 0x03}) {
            var vanilla = deck(new LinkedHashMap<>(Map.of("Base", 12L)), new LinkedHashMap<>(Map.of("DivA", 4L)), type);
            var first = deck(new LinkedHashMap<>(Map.of("Base", 12L, "FirstTank", 16000L)),
                new LinkedHashMap<>(Map.of("DivA", 4L, "FirstDiv", 90L)), type);
            var second = deck(new LinkedHashMap<>(Map.of("Base", 12L, "SecondTank", 16000L, "SecondGun", 16001L)),
                new LinkedHashMap<>(Map.of("DivA", 4L, "SecondDiv", 90L)), type);

            var result = NdfMerger.merge(vanilla, List.of(new NdfMerger.Variant("first", first),
                new NdfMerger.Variant("second", second)), Cancel.NONE);
            var units = ids(result.database, "UnitIds");
            assertUnique(units);
            check(units.get("Base") == 12L, "vanilla ID moved: " + units);
            check(units.get("FirstTank") == 16000L, "the higher-priority mod keeps its ID: " + units);
            check(units.get("SecondTank") > 16001L, "the lower-priority duplicate is renumbered above every used ID: " + units);
            check(units.get("SecondGun") == 16001L, "non-conflicting IDs are left alone: " + units);
            assertUnique(ids(result.database, "DivisionIds"));
            check(ids(result.database, "DivisionIds").get("FirstDiv") == 90L, "division priority");
            check(result.conflicts.stream().anyMatch(c -> c.path.startsWith("DeckSerializer.UnitIds")
                && c.winner.equals("first") && c.overridden.contains("second")), "the renumbering is reported");

            // Reversed priority flips who keeps the ID.
            var flipped = NdfMerger.merge(vanilla, List.of(new NdfMerger.Variant("second", second),
                new NdfMerger.Variant("first", first)), Cancel.NONE).database;
            var flippedUnits = ids(flipped, "UnitIds");
            assertUnique(flippedUnits);
            check(flippedUnits.get("SecondTank") == 16000L, "priority decides: " + flippedUnits);

            // A vanilla entry always wins over a mod that reuses its ID.
            var stealer = deck(new LinkedHashMap<>(Map.of("Base", 12L, "Thief", 12L)), new LinkedHashMap<>(), type);
            var stolen = ids(NdfMerger.merge(vanilla, List.of(new NdfMerger.Variant("stealer", stealer)), Cancel.NONE).database, "UnitIds");
            assertUnique(stolen);
            check(stolen.get("Base") == 12L && stolen.get("Thief") != 12L, "vanilla keeps its ID: " + stolen);

            // No conflict, no change, no report.
            var clean = NdfMerger.merge(vanilla, List.of(new NdfMerger.Variant("first", first)), Cancel.NONE);
            check(ids(clean.database, "UnitIds").equals(Map.of("Base", 12L, "FirstTank", 16000L)), "untouched when unique");
            check(clean.conflicts.stream().noneMatch(c -> c.path.startsWith("DeckSerializer")), "no false report");
        }
        System.out.println("DeckIdConflictTest passed");
    }
}
