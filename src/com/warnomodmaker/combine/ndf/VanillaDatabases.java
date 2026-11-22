package com.warnomodmaker.combine.ndf;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Vanilla compiled databases for one WARNO build: Data/PC/&lt;ModGen&gt;/Glad.dat (.ndfbin), or since the November
 * 2026 update Data/PC/&lt;earlier build&gt;/&lt;ModGen&gt;/Transactions.dat (.txn, see {@link NdfLayout}).
 */
public final class VanillaDatabases {
    // An archive's dictionary is parsed once per file version, however often the build asks for it.
    private static final Map<String, EdatArchive> ARCHIVES = new ConcurrentHashMap<>();

    private final EdatArchive archive;
    private final Path archivePath;
    private final String searched;
    private final boolean transactionLayout;

    private VanillaDatabases(EdatArchive archive, Path archivePath, String searched, boolean transactionLayout) {
        this.archive = archive;
        this.archivePath = archivePath;
        this.searched = searched;
        this.transactionLayout = transactionLayout;
    }

    public Path archivePath() {
        return archivePath;
    }

    /** Where the archive was looked for and what was found there, for diagnostics when it is missing. */
    public String searched() {
        return searched;
    }

    /** The installed build stores databases as .txn transactions. */
    public boolean transactionLayout() {
        return transactionLayout;
    }

    public boolean available() {
        return archive != null;
    }

    private static boolean hasArchive(Path folder) {
        return Files.isRegularFile(folder.resolve("Transactions.dat")) || Files.isRegularFile(folder.resolve("Glad.dat"));
    }

    private static List<Path> directories(Path folder) {
        File[] children = folder.toFile().listFiles(File::isDirectory);
        if (children == null) return Collections.emptyList();
        List<Path> result = new ArrayList<>();
        for (File child : children) result.add(child.toPath());
        return result;
    }

    private static EdatArchive archive(Path path) {
        File file = path.toFile();
        String key = file.getAbsolutePath().toUpperCase() + "|" + file.length() + "|" + file.lastModified();
        return ARCHIVES.computeIfAbsent(key, k -> new EdatArchive(path));
    }

    /**
     * Opens the vanilla databases of one ModGen revision: Data\PC\&lt;ModGen&gt;\Transactions.dat since the November
     * 2026 update, Data\PC\&lt;ModGen&gt;\Glad.dat before it. Nothing else is accepted, because another build's
     * databases would make every merge wrong.
     */
    public static VanillaDatabases open(Path warnoRoot, Integer modGenVersion) {
        if (modGenVersion == null || modGenVersion < 0) return new VanillaDatabases(null, null, "no ModGenVersion to look up", false);
        Path pc = warnoRoot.resolve("Data").resolve("PC");
        // A revision's folder is Data\PC\<ModGen>, or nested in the revision before it (Data\PC\201602\210942).
        Path folder = pc.resolve(modGenVersion.toString());
        if (!hasArchive(folder) && Files.isDirectory(pc)) {
            for (Path dir : directories(pc)) {
                Path nested = dir.resolve(modGenVersion.toString());
                if (hasArchive(nested)) {
                    folder = nested;
                    break;
                }
            }
        }
        Path transactions = folder.resolve("Transactions.dat");
        if (Files.isRegularFile(transactions)) return new VanillaDatabases(archive(transactions), transactions, null, true);
        Path glad = folder.resolve("Glad.dat");
        if (Files.isRegularFile(glad)) return new VanillaDatabases(archive(glad), glad, null, false);
        return new VanillaDatabases(null, null, "neither " + transactions + " nor " + glad + " exists", false);
    }

    /**
     * The installed WARNO's ModGen revision: the highest-numbered revision folder in Data\PC that holds a full
     * database archive (Transactions.dat, or Glad.dat before November 2026). Revision numbers only grow, and Steam
     * keeps older revisions' folders, top-level (Data\PC\352) or inside the revision before them
     * (Data\PC\602\942); folders without an archive hold only a patch and don't count.
     */
    public static int installedModGen(Path warnoRoot) {
        Path pc = warnoRoot.resolve("Data").resolve("PC");
        if (!Files.isDirectory(pc))
            throw new NdfFormatException("WARNO's data folder " + pc + " doesn't exist. Choose the WARNO installation folder.");
        int best = -1;
        for (Path top : directories(pc)) {
            List<Path> candidates = new ArrayList<>(directories(top));
            candidates.add(0, top);
            for (Path folder : candidates) {
                String name = folder.getFileName().toString();
                if (name.matches("\\d{1,9}") && hasArchive(folder)) best = Math.max(best, Integer.parseInt(name));
            }
        }
        if (best < 0) throw new NdfFormatException("No WARNO revision with game data was found in " + pc + ". Verify WARNO's files in Steam.");
        return best;
    }

    /** Every database the archive holds, as mod-relative paths (Gen\NDF\GFX\Unit.txn). */
    public List<String> databasePaths() {
        if (archive == null) return Collections.emptyList();
        String extension = transactionLayout ? ".txn" : ".ndfbin";
        return archive.entries().keySet().stream()
            .filter(k -> k.toLowerCase().endsWith(extension))
            .map(k -> "Gen\\" + k.replace('/', '\\'))
            .collect(Collectors.toList());
    }

    /** The vanilla bytes for a mod-relative path such as Gen\NDF\GFX\Unit.txn, or null. */
    public byte[] tryRead(String relativePath) {
        if (archive == null) return null;
        // The archive uses the installed build's layout; a database in the other layout maps onto it.
        if (transactionLayout && NdfLayout.isLegacyDatabase(relativePath)) relativePath = NdfLayout.toTransactionPath(relativePath);
        else if (!transactionLayout && NdfLayout.isTransactionDatabase(relativePath)) relativePath = NdfLayout.toLegacyPath(relativePath);
        String normalized = relativePath.replace('\\', '/');
        if (normalized.regionMatches(true, 0, "Gen/", 0, 4)) normalized = normalized.substring(4);
        EdatArchive.Entry entry = archive.entries().get(normalized);
        return entry == null ? null : archive.read(entry);
    }
}
