package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.*;
import com.warnomodmaker.combine.ndf.NdfValue.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public class DamageFamiliesTest {
    private static Raw integer(int i) { return new Raw(2, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array()); }
    private static ListV names(List<String> names) { return new ListV(names.stream().<NdfValue>map(n -> new Text(7,n)).toList()); }
    private static ListV counts(int size) {
        List<NdfValue> pairs = new ArrayList<>();
        for (int i=0; i<size; i++) pairs.add(new Pair(integer(i),integer(1)));
        return new ListV(pairs);
    }
    private static NdfDatabase schema(List<String> damage, List<String> armor, float[][] table) {
        List<NdfValue> matrix = new ArrayList<>();
        for (float[] row : table) {
            List<NdfValue> values = new ArrayList<>();
            for (float value : row) values.add(new Raw(5,integer(Float.floatToIntBits(value)).data));
            matrix.add(new ListV(values));
        }
        return new NdfDatabase(List.of(
            root("TDamageFamilyList",List.of(NdfValue.field("Values",names(damage)))),
            root("TResistanceFamilyList",List.of(NdfValue.field("Values",names(armor)))),
            root("TGameplayDamageResistanceContainer",List.of(NdfValue.field("DamageFamilyCounts",counts(damage.size())),
                NdfValue.field("ResistanceFamilyCounts",counts(armor.size())),NdfValue.field("Values",new ListV(matrix))))),Map.of());
    }
    private static NdfDatabase.TopLevel root(String type,List<Map.Entry<String,NdfValue>> fields) {
        return new NdfDatabase.TopLevel(null,new Obj(type,fields,null));
    }
    private static MergePlanner.CompiledInput input(Path temp,String name,NdfDatabase db) throws Exception {
        Path root = temp.resolve(name);
        Path file = root.resolve(DamageFamilies.BASE);
        Files.createDirectories(file.getParent());
        Files.write(file,db.save());
        return new MergePlanner.CompiledInput(name,root,Map.of());
    }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("damage-family-test");
        try {
            var base = schema(List.of("D"),List.of("A"),new float[][]{{2}});
            var first = schema(List.of("D","missile"),List.of("A"),new float[][]{{3},{0}});
            var second = schema(List.of("D"),List.of("ERA","A"),new float[][]{{5,7}});
            var families = new DamageFamilies(base.save(),List.of(input(temp,"first",first),input(temp,"second",second)));
            var merged = families.normalize("second",second);
            var matrix = (ListV)merged.topLevel.getLast().value.get("Values");
            List<List<String>> rows = matrix.items.stream().map(r -> ((ListV)r).items.stream().map(NdfValue::describe).toList()).toList();
            if (!rows.equals(List.of(List.of("3","5"),List.of("0","1")))) throw new AssertionError(rows);
            var armor = new NdfDatabase(List.of(root("TResistanceTypeRTTI",List.of(NdfValue.field("Family",integer(0))))),Map.of());
            if (!families.normalize("second",armor).topLevel.getFirst().value.get("Family").equals(integer(1)))
                throw new AssertionError("ERA armor did not follow its named family");
        } finally { FileOps.deleteTree(temp); }
    }
}
