package com.warnomodmaker.combine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Finds a Python 3 interpreter for WARNO's SDK scripts. WARNO used to ship one in Mods\Utils\Python; newer builds
 * expect a system install instead (their .bat files call {@code python3} from PATH).
 */
public final class PythonLocator {
    public static final String MISSING_MESSAGE =
        "WARNO's mod tools need Python 3, which was not found on this PC. WARNO no longer bundles it. "
            + "Install Python 3 from python.org (tick \"Add python.exe to PATH\"), then try again.";

    private static volatile String found;

    private PythonLocator() {
    }

    /** The full path of a working Python 3 (3.6 or later) interpreter, or throws {@link CombineException}. */
    public static String find(Path modsRoot, Consumer<String> log) {
        String cached = found;
        if (cached != null && Files.isRegularFile(Paths.get(cached))) {
            log.accept("  Using Python at " + cached);
            return cached;
        }
        for (String[] candidate : candidates(modsRoot)) {
            String[] probe = probe(candidate[0], Arrays.copyOfRange(candidate, 1, candidate.length));
            if (probe == null) continue;
            log.accept("  Using Python " + probe[1] + " at " + probe[0]);
            found = probe[0];
            return probe[0];
        }
        throw new CombineException(MISSING_MESSAGE);
    }

    private static List<String[]> candidates(Path modsRoot) {
        List<String[]> list = new ArrayList<>();
        // Older WARNO builds bundled their own interpreter.
        list.add(new String[]{modsRoot.resolve("Utils").resolve("Python").resolve("python.exe").toString()});
        // The py launcher resolves the newest installed Python 3 even when it is not on PATH.
        Set<String> launchers = new LinkedHashSet<>(onPath("py.exe"));
        String windows = System.getenv("SystemRoot");
        launchers.add(Paths.get(windows != null ? windows : "C:\\Windows", "py.exe").toString());
        for (String launcher : launchers) list.add(new String[]{launcher, "-3"});
        // What WARNO's own .bat files run. The Windows Store alias stubs fail the probe and are skipped.
        for (String name : new String[]{"python3.exe", "python.exe"}) for (String path : onPath(name)) list.add(new String[]{path});
        // python.org installs made without "Add to PATH".
        for (String root : new String[]{
            System.getenv("LOCALAPPDATA") == null ? null : Paths.get(System.getenv("LOCALAPPDATA"), "Programs", "Python").toString(),
            System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")}) {
            if (root == null) continue;
            List<Path> dirs = new ArrayList<>(FileOps.directories(Paths.get(root), "Python3*"));
            dirs.sort((a, b) -> Integer.compare(versionOf(b), versionOf(a)));
            for (Path dir : dirs) list.add(new String[]{dir.resolve("python.exe").toString()});
        }
        return list;
    }

    /** {executable, "major.minor"} of a working interpreter, or null. */
    private static String[] probe(String executable, String[] arguments) {
        if (!Files.isRegularFile(Paths.get(executable))) return null;
        List<String> command = new ArrayList<>();
        command.add(executable);
        Collections.addAll(command, arguments);
        command.add("-c");
        command.add("import sys; print('%d.%d' % sys.version_info[:2]); print(sys.executable)");
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            byte[] output = readAll(process.getInputStream());
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;
            String[] lines = new String(output, StandardCharsets.UTF_8).trim().split("\\s*\r?\n\\s*");
            if (lines.length < 1 || !lines[0].matches("\\d+\\.\\d+")) return null;
            String[] version = lines[0].split("\\.");
            int major = Integer.parseInt(version[0]), minor = Integer.parseInt(version[1]);
            // GenerateMod.py uses f-strings.
            if (major < 3 || major == 3 && minor < 6) return null;
            // The launcher reports the interpreter it chose; run that directly so the SDK scripts see a plain python.exe.
            String chosen = lines.length > 1 && Files.isRegularFile(Paths.get(lines[1])) ? lines[1] : executable;
            return new String[]{chosen, lines[0]};
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static List<String> onPath(String fileName) {
        Set<String> result = new LinkedHashSet<>();
        String path = System.getenv("PATH");
        if (path == null) return new ArrayList<>();
        for (String dir : path.split(";")) {
            String trimmed = dir.trim().replace("\"", "");
            if (trimmed.isEmpty()) continue;
            try {
                Path candidate = Paths.get(trimmed, fileName);
                if (Files.isRegularFile(candidate)) result.add(candidate.toString());
            } catch (RuntimeException ignored) {
                // A malformed PATH entry is skipped.
            }
        }
        return new ArrayList<>(result);
    }

    // "Python314" -> 314, so 3.14 sorts above 3.9.
    private static int versionOf(Path directory) {
        String digits = directory.getFileName().toString().replaceAll("^\\D*(\\d*).*$", "$1");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
