package com.warnomodmaker.combine;

import com.warnomodmaker.combine.ndf.Hex;
import com.warnomodmaker.combine.ndf.IgnoreCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** File helpers shared by the combine engine. */
public final class FileOps {
    private static final int BUFFER = 512 * 1024;
    // Hashes of large files that rarely change (WARNO's base.zip, editable mods' base.zip), by path, size and time.
    private static final Map<String, String> HASH_CACHE = new ConcurrentHashMap<>();

    private FileOps() {
    }

    /** {@code root\relativePath}, refusing any path that escapes {@code root}. */
    public static Path safeCombine(Path root, String relativePath) {
        Path fullRoot = root.toAbsolutePath().normalize();
        Path combined = fullRoot.resolve(relativePath).normalize();
        if (!combined.startsWith(fullRoot) || combined.equals(fullRoot))
            throw new CombineException("Unsafe path escaped the output directory: " + relativePath);
        return combined;
    }

    /** Full, normalised path text without a trailing separator, for comparisons. */
    public static String full(Path path) {
        String text = path.toAbsolutePath().normalize().toString();
        while (text.length() > 3 && (text.endsWith("\\") || text.endsWith("/"))) text = text.substring(0, text.length() - 1);
        return text;
    }

    public static boolean samePath(Path left, Path right) {
        return left != null && right != null && full(left).equalsIgnoreCase(full(right));
    }

    /** {@code root}-relative path with backslashes, as the plan keys files. */
    public static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('/', '\\');
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(Path path, Cancel token) {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER];
            int n;
            while ((n = in.read(buffer)) > 0) {
                token.check();
                digest.update(buffer, 0, n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        token.check();
        return Hex.upper(digest.digest());
    }

    /** SHA-256 of a large, stable file, computed once per version of it. */
    public static String sha256Cached(Path path, Cancel token) {
        File file = path.toFile();
        String key = IgnoreCase.upper(full(path)) + "|" + file.length() + "|" + file.lastModified();
        String cached = HASH_CACHE.get(key);
        if (cached != null) return cached;
        String hash = sha256(path, token);
        HASH_CACHE.put(key, hash);
        return hash;
    }

    public static boolean sameContent(Path left, Path right, Cancel token) {
        try {
            return Files.size(left) == Files.size(right) && sha256(left, token).equals(sha256(right, token));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copies through a temporary file, so the destination is never half written. */
    public static void copyFileAtomic(Path source, Path destination, Cancel token) {
        copyAndHash(source, destination, token);
    }

    /** {@link #copyFileAtomic} that also returns the SHA-256 of the bytes copied, saving a second read of the source. */
    public static String copyAndHash(Path source, Path destination, Cancel token) {
        token.check();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".warno-combiner-" + UUID.randomUUID().toString().replace("-", "") + ".tmp");
        MessageDigest digest = sha256();
        try {
            Files.createDirectories(destination.getParent());
            try (InputStream in = Files.newInputStream(source);
                 OutputStream out = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    token.check();
                    out.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                }
            }
            token.check();
            move(temporary, destination);
            return Hex.upper(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A leftover temporary file is harmless.
            }
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void write(Path file, byte[] data) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copy(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every file below {@code root}, recursively; empty when it doesn't exist. */
    public static List<Path> files(Path root) {
        if (!Files.isDirectory(root)) return Collections.emptyList();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> result = new ArrayList<>();
            walk.filter(Files::isRegularFile).forEach(result::add);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The immediate subfolders of {@code root} whose names match the glob; empty when it doesn't exist. */
    public static List<Path> directories(Path root, String glob) {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(root)) return result;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, glob)) {
            for (Path p : stream) if (Files.isDirectory(p)) result.add(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        result.sort((a, b) -> IgnoreCase.ORDER.compare(a.getFileName().toString(), b.getFileName().toString()));
        return result;
    }

    public static List<Path> directories(Path root) {
        return directories(root, "*");
    }

    /** Deletes a folder and everything in it, clearing read-only flags first. */
    public static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    File f = file.toFile();
                    if (!f.canWrite()) f.setWritable(true);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Characters Windows doesn't allow in a file name. */
    public static boolean hasInvalidFileNameChars(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || "\"<>|:*?\\/".indexOf(c) >= 0) return true;
        }
        return false;
    }
}
