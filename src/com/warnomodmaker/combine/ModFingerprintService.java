package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.CombineProgress;
import com.warnomodmaker.combine.Models.ModDescriptor;
import com.warnomodmaker.combine.Models.SourceModFingerprint;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One SHA-256 per mod over every file's relative path, size and content, in a fixed order. The format is shared with
 * WARNO UltiAI MODerator, so combined mods it recorded are recognised as up to date.
 */
public final class ModFingerprintService {

    public List<SourceModFingerprint> compute(List<ModDescriptor> mods, Consumer<CombineProgress> progress, Cancel token) {
        return compute(mods, progress, token, "Checking source mods");
    }

    public List<SourceModFingerprint> compute(List<ModDescriptor> mods, Consumer<CombineProgress> progress, Cancel token, String stage) {
        Consumer<CombineProgress> report = progress != null ? progress : p -> { };
        report.accept(new CombineProgress(0, stage));
        List<List<Path>> filesByMod = new ArrayList<>();
        int total = 0;
        for (ModDescriptor mod : mods) {
            List<Path> files = enumerate(mod.rootPath);
            token.check();
            filesByMod.add(files);
            total += files.size();
        }
        int totalFiles = total;
        AtomicInteger completed = new AtomicInteger();
        List<SourceModFingerprint> results = Parallel.map(mods.size(), Math.max(1, mods.size()), i -> {
            ModDescriptor mod = mods.get(i);
            MessageDigest hash = FileOps.sha256();
            byte[] buffer = new byte[512 * 1024];
            for (Path file : filesByMod.get(i)) {
                token.check();
                String relative = IgnoreCase.upper(mod.rootPath.relativize(file).toString().replace('\\', '/'));
                try {
                    hash.update((relative + "\0" + Files.size(file) + "\0").getBytes(StandardCharsets.UTF_8));
                    try (InputStream in = Files.newInputStream(file)) {
                        int n;
                        while ((n = in.read(buffer)) > 0) {
                            token.check();
                            hash.update(buffer, 0, n);
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                hash.update((byte) 0);
                int done = completed.incrementAndGet();
                if (done == totalFiles || done % 25 == 0)
                    report.accept(new CombineProgress(totalFiles == 0 ? 100 : (int) Math.round(done * 100.0 / totalFiles), stage));
            }
            return new SourceModFingerprint(mod.name, FileOps.full(mod.rootPath), Hex.upper(hash.digest()));
        });
        report.accept(new CombineProgress(100, stage));
        return results;
    }

    private static List<Path> enumerate(Path root) {
        if (!Files.isDirectory(root)) throw new CombineException("Source mod folder is missing: " + root);
        List<Path> files = new ArrayList<>();
        for (Path file : FileOps.files(root)) {
            if (!file.getFileName().toString().equalsIgnoreCase(CombinedModStateStore.FILE_NAME)) files.add(file);
        }
        files.sort((a, b) -> IgnoreCase.ORDER.compare(root.relativize(a).toString(), root.relativize(b).toString()));
        return files;
    }
}
