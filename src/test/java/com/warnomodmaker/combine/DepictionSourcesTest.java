package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.DecisionKind;
import com.warnomodmaker.combine.Models.MergeDecision;
import com.warnomodmaker.combine.ndf.IgnoreCase;
import com.warnomodmaker.combine.ndf.NdfDatabase;
import com.warnomodmaker.combine.ndf.NdfValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Regression for case-aliases and exports shared between two paths in a transaction. */
public class DepictionSourcesTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("depiction-source-test");
        try {
            var primary = IgnoreCase.<Path>map();
            var other = IgnoreCase.<Path>map();
            String jet = "GameData\\Jet.ndf";
            String rocket = "GameData\\Rocket.ndf";
            primary.put(jet, write(temp, "a.ndf", mesh("JetA"), mesh("Shared")));
            other.put(jet, write(temp, "b.ndf", mesh("JetB")));
            other.put(rocket, write(temp, "c.ndf", mesh("Shared"), mesh("Rocket")));
            check(temp, List.of(primary, other), jet, rocket, true);
            check(temp, List.of(other, primary), jet, rocket, false);
        } finally {
            FileOps.deleteTree(temp);
        }
    }

    private static void check(Path temp, List<TreeMap<String, Path>> inputs, String jet,
                              String rocket, boolean primaryWins) throws Exception {
        var files = new NdfValue.ListV(List.of(path("Jet"), path("jet"), path("Rocket")));
        var transaction = new NdfValue.Obj("TNDFTransaction", List.of(
            NdfValue.field("NameSpace", new NdfValue.Text(7, "GFX/DepictionResources")),
            NdfValue.field("Files", files)), null);
        var sources = new DepictionSources();
        var cluster = sources.collect(new NdfDatabase(List.of(new NdfDatabase.TopLevel("test", transaction)), Map.of()));
        var kept = (NdfValue.ListV) cluster.topLevel.getFirst().value.get("Files");
        if (kept.items.size() != 2) throw new AssertionError("Case alias loaded twice");
        List<MergeDecision> plan = new ArrayList<>();
        for (String name : List.of(jet, rocket)) {
            Path winner = inputs.stream().map(m -> m.get(name)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            plan.add(new MergeDecision(name, DecisionKind.SINGLE, "winner", "", winner));
        }
        sources.resolve(plan, inputs, temp.resolve("resolved"), new Cancel());
        String a = Files.readString(plan.get(0).sourcePath);
        String b = Files.readString(plan.get(1).sourcePath);
        if (!a.contains("JetA") || !a.contains("JetB") || !b.contains("Rocket") ||
            a.contains("Shared") != primaryWins || b.contains("Shared") == primaryWins)
            throw new AssertionError("Mesh export lost or assigned to the wrong mod");
    }

    private static NdfValue.Text path(String name) {
        return new NdfValue.Text(7, "GameData:/" + name + ".ndf");
    }

    private static String mesh(String name) {
        return "export " + name + " is TResourceMesh ( Mesh = 'GameData:/" + name + ".fbx' )";
    }

    private static Path write(Path root, String name, String... lines) throws Exception {
        return Files.write(root.resolve(name), List.of(lines));
    }
}
