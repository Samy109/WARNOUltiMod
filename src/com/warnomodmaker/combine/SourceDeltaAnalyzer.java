package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.DeltaKind;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.ModKind;
import com.warnomodmaker.combine.Models.SourceDelta;
import com.warnomodmaker.combine.ndf.Hex;
import com.warnomodmaker.combine.ndf.IgnoreCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** What an editable mod changed against the base.zip it was made from. */
public final class SourceDeltaAnalyzer {
    static final String[] SOURCE_TREES = {"GameData", "CommonData"};

    public List<SourceDelta> analyze(ModDescriptor mod, Cancel token) {
        if (mod.kind != ModKind.EDITABLE_SOURCE) throw new IllegalArgumentException("Source deltas require an editable mod.");
        if (!Files.isRegularFile(mod.baseZipPath())) throw new CombineException(mod.name + " is missing base.zip.");
        TreeMap<String, Path> diskFiles = IgnoreCase.map();
        for (String tree : SOURCE_TREES) {
            for (Path file : FileOps.files(mod.rootPath.resolve(tree))) {
                token.check();
                diskFiles.put(normalize(FileOps.relative(mod.rootPath, file)), file);
            }
        }
        List<SourceDelta> deltas = new ArrayList<>();
        try (ZipFile archive = new ZipFile(mod.baseZipPath().toFile(), StandardCharsets.UTF_8)) {
            TreeMap<String, ZipEntry> entries = IgnoreCase.map();
            Enumeration<? extends ZipEntry> all = archive.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                if (!entry.isDirectory()) entries.putIfAbsent(normalize(entry.getName()), entry);
            }
            for (Map.Entry<String, Path> pair : diskFiles.entrySet()) {
                ZipEntry entry = entries.get(pair.getKey());
                if (entry == null) {
                    deltas.add(new SourceDelta(pair.getKey(), DeltaKind.ADDED, pair.getValue()));
                    continue;
                }
                token.check();
                if (Files.size(pair.getValue()) != entry.getSize() && entry.getSize() >= 0) {
                    deltas.add(new SourceDelta(pair.getKey(), DeltaKind.MODIFIED, pair.getValue()));
                    continue;
                }
                String disk = FileOps.sha256(pair.getValue(), token);
                String zipped;
                try (InputStream in = archive.getInputStream(entry)) {
                    zipped = hash(in, token);
                }
                if (!disk.equals(zipped)) deltas.add(new SourceDelta(pair.getKey(), DeltaKind.MODIFIED, pair.getValue()));
            }
            for (String entry : entries.keySet()) {
                if (isSourceTree(entry) && !diskFiles.containsKey(entry)) deltas.add(new SourceDelta(entry, DeltaKind.DELETED, null));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        deltas.sort((a, b) -> IgnoreCase.ORDER.compare(a.relativePath, b.relativePath));
        return deltas;
    }

    static boolean isSourceTree(String relativePath) {
        for (String tree : SOURCE_TREES) if (relativePath.regionMatches(true, 0, tree + "\\", 0, tree.length() + 1)) return true;
        return false;
    }

    static String hash(InputStream in, Cancel token) throws IOException {
        MessageDigest digest = FileOps.sha256();
        byte[] buffer = new byte[128 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            token.check();
            digest.update(buffer, 0, n);
        }
        return Hex.upper(digest.digest());
    }

    static String normalize(String path) {
        String result = path.replace('/', '\\');
        while (result.startsWith("\\")) result = result.substring(1);
        return result;
    }
}
