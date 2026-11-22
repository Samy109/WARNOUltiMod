package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.WarnoPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Keeps a rebuild safe: the previous output is moved aside under a journal before anything is written, and restored
 * if the build fails, is cancelled or the app is closed. The journal format is shared with WARNO UltiAI MODerator.
 */
public final class BuildRecovery {
    private static final String PREFIX = ".warno-moderator-recovery-";

    public static final class Record {
        public final String id, outputName;
        public final boolean sourceExisted, runtimeExisted, committed;

        public Record(String id, String outputName, boolean sourceExisted, boolean runtimeExisted, boolean committed) {
            this.id = id;
            this.outputName = outputName;
            this.sourceExisted = sourceExisted;
            this.runtimeExisted = runtimeExisted;
            this.committed = committed;
        }
    }

    /** An exclusive lock on one output's build, held while it runs (in this or another window, or MODerator). */
    public static final class Lock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        Lock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() {
            try {
                lock.release();
            } catch (IOException ignored) {
                // Closing the channel releases it anyway.
            }
            try {
                channel.close();
            } catch (IOException ignored) {
                // The lock file is deleted on close.
            }
        }
    }

    public static Lock acquireLock(WarnoPaths paths, String name) {
        MergePlanner.validateOutputName(name);
        Path file = paths.modsRoot.resolve(".warno-moderator-operation-" + name + ".lock");
        FileChannel channel = null;
        try {
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("locked");
            return new Lock(channel, lock);
        } catch (IOException | OverlappingFileLockException e) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // Already failing.
                }
            }
            throw new CombineException("Another operation is using '" + name + "'. Close it or wait for it to finish.", e);
        }
    }

    /** A build holds its output's lock while it runs. */
    private static boolean isRunning(WarnoPaths paths, String name) {
        try (Lock ignored = acquireLock(paths, name)) {
            return false;
        } catch (CombineException e) {
            return true;
        }
    }

    /** Interrupted builds waiting for recovery; a build that is still running is not one. */
    public List<Record> find(WarnoPaths paths) {
        List<Record> result = new ArrayList<>();
        for (Record record : readRecords(paths)) if (!isRunning(paths, record.outputName)) result.add(record);
        return result;
    }

    private static List<Record> readRecords(WarnoPaths paths) {
        List<Record> records = new ArrayList<>();
        if (!Files.isDirectory(paths.modsRoot)) return records;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(paths.modsRoot, PREFIX + "*.json")) {
            for (Path file : files) {
                try {
                    Object parsed = Json.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                    if (!(parsed instanceof Map)) continue;
                    Map<?, ?> json = (Map<?, ?>) parsed;
                    Record record = new Record(Json.str(json, "Id"), Json.str(json, "OutputName"), Json.bool(json, "SourceExisted"),
                        Json.bool(json, "RuntimeExisted"), Json.bool(json, "Committed"));
                    if (record.id == null || !record.id.matches("[0-9a-fA-F]{32}") || record.outputName == null || record.outputName.trim().isEmpty()) continue;
                    MergePlanner.validateOutputName(record.outputName);
                    if (file.getFileName().toString().equals(PREFIX + record.id + ".json")) records.add(record);
                } catch (IOException | RuntimeException ignored) {
                    // A record that can't be read isn't one of ours.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return records;
    }

    Record begin(WarnoPaths paths, String name) {
        // The caller holds this output's lock, so every record for it is a leftover of an interrupted build.
        if (readRecords(paths).stream().anyMatch(r -> r.outputName.equalsIgnoreCase(name)))
            throw new CombineException("An interrupted rebuild of '" + name + "' requires recovery. Refresh mods to recover it first.");
        Record record = new Record(UUID.randomUUID().toString().replace("-", ""), name,
            Files.isDirectory(paths.modsRoot.resolve(name)), Files.isDirectory(paths.savedModsRoot.resolve(name)), false);
        save(paths, record);
        return record;
    }

    static Path backup(Path root, Record record) {
        return FileOps.safeCombine(root, record.outputName + ".warno-moderator-backup-" + record.id);
    }

    void moveOriginals(WarnoPaths paths, Record record) {
        try {
            if (record.sourceExisted) Files.move(paths.modsRoot.resolve(record.outputName), backup(paths.modsRoot, record));
            if (record.runtimeExisted) Files.move(paths.savedModsRoot.resolve(record.outputName), backup(paths.savedModsRoot, record));
        } catch (IOException e) {
            throw new CombineException("The existing '" + record.outputName + "' couldn't be moved aside for the rebuild: " + e.getMessage()
                + ". Close WARNO and any program using its files, then retry.", e);
        }
    }

    public void recover(WarnoPaths paths, Record record, Consumer<String> log) {
        validate(record);
        try (Lock ignored = acquireLock(paths, record.outputName)) {
            if (record.committed) cleanup(paths, record, log);
            else restore(paths, record, log);
        }
    }

    void restore(WarnoPaths paths, Record record, Consumer<String> log) {
        List<String> failures = new ArrayList<>();
        restoreOne(paths.modsRoot, record.sourceExisted, record, log, failures);
        restoreOne(paths.savedModsRoot, record.runtimeExisted, record, log, failures);
        if (!failures.isEmpty())
            throw new CombineException("Automatic recovery is incomplete. Close WARNO and programs using these files, then refresh mods to retry. "
                + String.join(" ", failures));
        try {
            Files.deleteIfExists(journal(paths, record));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.accept("Previous outputs restored.");
    }

    private static void restoreOne(Path root, boolean existed, Record record, Consumer<String> log, List<String> failures) {
        Path output = root.resolve(record.outputName);
        Path backup = backup(root, record);
        try {
            // A missing backup means this side was never moved or was already restored.
            if (existed && !Files.isDirectory(backup)) return;
            if (Files.isDirectory(output)) {
                // A half-built output is never usable; it is removed so nothing stale is left behind.
                FileOps.deleteTree(output);
                log.accept("Deleted the incomplete output at " + output + ".");
            }
            if (Files.isDirectory(backup)) Files.move(backup, output);
        } catch (IOException | UncheckedIOException e) {
            String message = "Restore '" + output + "' from '" + backup + "': " + e.getMessage();
            log.accept(message);
            failures.add(message);
        }
    }

    void complete(WarnoPaths paths, Record record, Consumer<String> log) {
        // Persist the commit before cleanup; a restart must never restore a partially deleted backup.
        Record committed = new Record(record.id, record.outputName, record.sourceExisted, record.runtimeExisted, true);
        save(paths, committed);
        cleanup(paths, committed, log);
    }

    private static void cleanup(WarnoPaths paths, Record record, Consumer<String> log) {
        boolean pending = false;
        for (Path root : new Path[]{paths.modsRoot, paths.savedModsRoot}) {
            Path backup = backup(root, record);
            try {
                FileOps.deleteTree(backup);
            } catch (UncheckedIOException e) {
                pending = true;
                log.accept("Build succeeded. Backup cleanup pending at '" + backup + "': " + e.getMessage());
            }
        }
        if (!pending) {
            try {
                Files.deleteIfExists(journal(paths, record));
            } catch (IOException e) {
                log.accept("Build succeeded. Recovery record cleanup pending: " + e.getMessage());
            }
        }
    }

    private static void validate(Record record) {
        if (record.id == null || !record.id.matches("[0-9a-fA-F]{32}")) throw new CombineException("Invalid recovery record.");
        MergePlanner.validateOutputName(record.outputName);
    }

    private static Path journal(WarnoPaths paths, Record record) {
        return paths.modsRoot.resolve(PREFIX + record.id + ".json");
    }

    private static void save(WarnoPaths paths, Record record) {
        validate(record);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("Id", record.id);
        json.put("OutputName", record.outputName);
        json.put("SourceExisted", record.sourceExisted);
        json.put("RuntimeExisted", record.runtimeExisted);
        json.put("Committed", record.committed);
        Path file = journal(paths, record);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(java.nio.ByteBuffer.wrap(Json.write(json).getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Nothing to clean up.
            }
        }
    }
}
