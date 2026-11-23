package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.NdfLayout;
import com.warnomodmaker.combine.ndf.VanillaDatabases;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which WARNO revision a compiled mod's databases were really built against.
 *
 * <p>A mod's Config.ini [Config] section lists, for each database it ships, the MD5 of the vanilla database it was
 * generated from. WARNO compares these with the installed game's databases: a mismatch is what it reports as an
 * out-of-date or incompatible mod. A mod's declared ModGenVersion can say the current revision while its
 * fingerprints still name an older one (a manifest edited by hand, or published without regenerating), so the
 * fingerprints, not the declaration, decide which vanilla build its changes are measured against.</p>
 */
public final class BuildRevision {
    /** Per database archive: manifest key to the MD5 of that revision's vanilla database. */
    private static final Map<Path, Map<String, String>> FINGERPRINTS = new ConcurrentHashMap<>();

    private BuildRevision() {
    }

    /** The vanilla fingerprints of one installed revision (manifest key to MD5), or an empty map when it isn't on disk. */
    public static Map<String, String> vanillaFingerprints(Path warnoRoot, int revision) {
        VanillaDatabases vanilla = VanillaDatabases.open(warnoRoot, revision);
        if (!vanilla.available()) return Collections.emptyMap();
        return FINGERPRINTS.computeIfAbsent(vanilla.archivePath().toAbsolutePath().normalize(), key -> {
            Map<String, String> result = new ConcurrentHashMap<>();
            for (String path : vanilla.databasePaths()) {
                if (!NdfLayout.isDatabase(path)) continue;
                byte[] data = vanilla.tryRead(path);
                if (data != null) result.put(NdfLayout.manifestKey(path).toLowerCase(), md5(data));
            }
            return result;
        });
    }

    /** Every revision on disk with full game data, newest first. */
    public static List<Integer> installedRevisions(Path warnoRoot) {
        TreeSet<Integer> revisions = new TreeSet<>(Collections.reverseOrder());
        Path pc = warnoRoot.resolve("Data").resolve("PC");
        if (!Files.isDirectory(pc)) return new ArrayList<>();
        for (Path top : FileOps.directories(pc)) {
            List<Path> candidates = new ArrayList<>(FileOps.directories(top));
            candidates.add(0, top);
            for (Path folder : candidates) {
                String name = folder.getFileName().toString();
                if (name.matches("\\d{1,9}") && VanillaDatabases.open(warnoRoot, Integer.parseInt(name)).available())
                    revisions.add(Integer.parseInt(name));
            }
        }
        return new ArrayList<>(revisions);
    }

    /**
     * The newest installed revision whose vanilla databases match every fingerprint the mod lists, or null when none
     * does. Fingerprints of databases a revision doesn't have are not evidence either way.
     */
    public static Integer matching(Path warnoRoot, Map<String, String> configKeys) {
        if (configKeys == null || configKeys.isEmpty()) return null;
        for (int revision : installedRevisions(warnoRoot)) {
            Map<String, String> vanilla = vanillaFingerprints(warnoRoot, revision);
            boolean compared = false, all = true;
            for (Map.Entry<String, String> entry : configKeys.entrySet()) {
                String expected = vanilla.get(entry.getKey().toLowerCase());
                if (expected == null) continue;
                compared = true;
                if (!expected.equalsIgnoreCase(entry.getValue().trim())) {
                    all = false;
                    break;
                }
            }
            if (compared && all) return revision;
        }
        return null;
    }

    /** The databases whose fingerprint differs from the installed revision's vanilla database. */
    public static List<String> staleKeys(Path warnoRoot, int installed, Map<String, String> configKeys) {
        List<String> stale = new ArrayList<>();
        Map<String, String> vanilla = vanillaFingerprints(warnoRoot, installed);
        for (Map.Entry<String, String> entry : configKeys.entrySet()) {
            String expected = vanilla.get(entry.getKey().toLowerCase());
            if (expected != null && !expected.equalsIgnoreCase(entry.getValue().trim())) stale.add(entry.getKey());
        }
        return stale;
    }

    static String md5(byte[] data) {
        try {
            StringBuilder text = new StringBuilder(32);
            for (byte b : MessageDigest.getInstance("MD5").digest(data)) text.append(String.format("%02x", b));
            return text.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
