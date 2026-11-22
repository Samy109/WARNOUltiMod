package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.IgnoreCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Vanilla's source files from WARNO's ModData\base.zip. A compiled mod that ships one of these files byte for byte has
 * not changed it, however many other mods did.
 */
public final class SourceBaseline {
    // Hashing every vanilla source takes seconds; it is done once per version of base.zip, not once per preview.
    private static final Map<String, SourceBaseline> CACHE = new ConcurrentHashMap<>();

    String countrySource = "";
    private final TreeMap<String, String> hashes = IgnoreCase.map();

    private SourceBaseline(Path zipPath) throws IOException {
        try (ZipFile archive = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String path = SourceDeltaAnalyzer.normalize(entry.getName());
                if (!SourceDeltaAnalyzer.isSourceTree(path)) continue;
                if (path.equalsIgnoreCase(CountrySources.PATH)) {
                    try (InputStream in = archive.getInputStream(entry)) { countrySource = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1); }
                }
                try (InputStream in = archive.getInputStream(entry)) {
                    hashes.put(path, SourceDeltaAnalyzer.hash(in, Cancel.NONE));
                }
            }
        }
    }

    /** Hashes vanilla's source files from WARNO's ModData\base.zip, which every install has. */
    public static SourceBaseline open(Path zipPath) {
        File file = zipPath.toFile();
        String key = IgnoreCase.upper(FileOps.full(zipPath)) + "|" + file.length() + "|" + file.lastModified();
        SourceBaseline cached = CACHE.get(key);
        if (cached != null) return cached;
        try {
            SourceBaseline baseline = new SourceBaseline(zipPath);
            CACHE.put(key, baseline);
            return baseline;
        } catch (IOException | RuntimeException e) {
            throw new CombineException("WARNO's vanilla mod sources (" + zipPath + ") can't be read: " + e.getMessage() + " Verify WARNO's files in Steam.", e);
        }
    }

    /** True when the installed WARNO's vanilla sources have this file. */
    public boolean contains(String relativePath) {
        return hashes.containsKey(SourceDeltaAnalyzer.normalize(relativePath));
    }

    /** True when vanilla ships this file and {@code file} is identical to it. */
    public boolean isVanilla(String relativePath, Path file, Cancel token) {
        String hash = hashes.get(SourceDeltaAnalyzer.normalize(relativePath));
        return hash != null && FileOps.sha256(file, token).equals(hash);
    }
}
