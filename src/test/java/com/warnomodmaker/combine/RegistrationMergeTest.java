package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.*;
import com.warnomodmaker.combine.ndf.NdfValue.*;
import java.util.*;

public final class RegistrationMergeTest {
    private static Text text(String name) { return new Text(7, name); }
    private static NdfDatabase database(String... names) {
        var roots = new ArrayList<NdfDatabase.TopLevel>();
        for (String name : names) {
            var entry = new Obj("TTransportedInfantryEntry", List.of(NdfValue.field("Identifier",text(name))),null);
            var infantry = new Obj("TTransportedInfantryCatalogEntries",List.of(
                NdfValue.field("Catalog",text("catalog")), NdfValue.field("Entries",new ListV(List.of(entry)))),null);
            var textures = new Obj("TBUCKToolAdditionalTextureBank",List.of(
                NdfValue.field("Textures",new MapV(List.of(NdfValue.entry(text(name),text(name+".png")))))),null);
            roots.add(new NdfDatabase.TopLevel(null,infantry));
            roots.add(new NdfDatabase.TopLevel(null,textures));
            var squad = new Obj("TUnitMimeticRegistration",List.of(NdfValue.field("GfxProperties",text("global")),
                NdfValue.field("UnitMimetics",new MapV(List.of(NdfValue.entry(text(name),text("squad_"+name)))))),null);
            roots.add(new NdfDatabase.TopLevel(null,squad));
            var deck = new Obj("TDeckSerializerEntries",List.of(
                NdfValue.field("DivisionIds",new MapV(List.of(NdfValue.entry(text(name),text("division_"+name))))),
                NdfValue.field("UnitIds",new MapV(List.of(NdfValue.entry(text(name),text("unit_"+name)))))),null);
            roots.add(new NdfDatabase.TopLevel(null,deck));
        }
        return new NdfDatabase(roots,Map.of());
    }
    public static void main(String[] args) {
        var variants = List.of(new NdfMerger.Variant("first",database("existing","shared","first")),
                               new NdfMerger.Variant("second",database("existing","shared","second")));
        var db = NdfMerger.merge(database("existing"),variants,Cancel.NONE).database;
        if (db.topLevel.size()!=5) throw new AssertionError("Duplicate registration banks");
        for (var root : db.topLevel) {
            List<NdfValue> keys;
            if (root.value.get("Textures") instanceof MapV textures)
                keys = textures.entries.stream().map(Map.Entry::getKey).toList();
            else if (root.value.get("UnitMimetics") instanceof MapV squads)
                keys = squads.entries.stream().map(Map.Entry::getKey).toList();
            else if (root.value.className.equals("TDeckSerializerEntries"))
                keys = ((MapV)root.value.fields.getFirst().getValue()).entries.stream().map(Map.Entry::getKey).toList();
            else keys = ((ListV)root.value.get("Entries")).items.stream().map(item -> ((Obj)item).get("Identifier")).toList();
            if (keys.size()!=4 || !Set.copyOf(keys).equals(Set.of(text("existing"),text("shared"),text("first"),text("second"))))
                throw new AssertionError("Registration lost or repeated");
        }
    }
}
