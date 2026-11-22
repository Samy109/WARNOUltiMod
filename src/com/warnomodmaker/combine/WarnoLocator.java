package com.warnomodmaker.combine;

import com.warnomodmaker.combine.Models.WarnoPaths;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds WARNO in the Steam libraries, and the folders the combine engine works with. */
public final class WarnoLocator {
    private static final String[] REGISTRY_KEYS = {
        "HKCU\\Software\\Valve\\Steam",
        "HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam",
        "HKLM\\SOFTWARE\\Valve\\Steam"
    };
    private static final Pattern LIBRARY_PATH = Pattern.compile("\"path\"\\s+\"([^\"]+)\"");

    /** The WARNO installation found in the Steam libraries, or null. */
    public WarnoPaths locate() {
        for (Path steamRoot : steamRoots()) {
            for (Path library : libraries(steamRoot)) {
                Path warnoRoot = library.resolve("steamapps").resolve("common").resolve("WARNO");
                if (Files.isDirectory(warnoRoot)) return paths(warnoRoot, library);
            }
        }
        return null;
    }

    public WarnoPaths fromWarnoRoot(String warnoRoot) {
        Path fullRoot;
        try {
            fullRoot = Paths.get(warnoRoot.trim()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new CombineException("The selected folder is not a valid WARNO installation.");
        }
        if (!Files.isDirectory(fullRoot) || !Files.isRegularFile(fullRoot.resolve("WARNO.exe"))
            || !Files.isRegularFile(fullRoot.resolve("Mods").resolve("CreateNewMod.bat")))
            throw new CombineException("The selected folder is not a valid WARNO installation.");
        Path common = fullRoot.getParent() != null && fullRoot.getParent().getParent() != null ? fullRoot.getParent().getParent().getParent() : null;
        return paths(fullRoot, common != null ? common : fullRoot);
    }

    private static WarnoPaths paths(Path warnoRoot, Path library) {
        Path savedMods = Paths.get(System.getProperty("user.home"), "Saved Games", "EugenSystems", "WARNO", "mod");
        return new WarnoPaths(warnoRoot, warnoRoot.resolve("Mods"),
            library.resolve("steamapps").resolve("workshop").resolve("content").resolve("1611600"), savedMods);
    }

    private static Set<Path> steamRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        for (String key : REGISTRY_KEYS) {
            for (String valueName : new String[]{"SteamPath", "InstallPath"}) {
                String value = readRegistry(key, valueName);
                if (value == null) continue;
                Path path = Paths.get(value).toAbsolutePath().normalize();
                if (Files.isDirectory(path)) roots.add(path);
            }
        }
        // Steam's default install folders, for installs the registry doesn't list.
        for (String fallback : new String[]{"C:\\Program Files (x86)\\Steam", "C:\\Program Files\\Steam"}) {
            Path path = Paths.get(fallback);
            if (Files.isDirectory(path)) roots.add(path);
        }
        return roots;
    }

    /** A string value from the Windows registry through reg.exe, or null. */
    static String readRegistry(String key, String valueName) {
        try {
            Process process = new ProcessBuilder("reg", "query", key, "/v", valueName).redirectErrorStream(true).start();
            process.getOutputStream().close();
            byte[] output = readAll(process.getInputStream());
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;
            for (String line : new String(output, StandardCharsets.UTF_8).split("\r?\n")) {
                int type = line.indexOf("REG_SZ");
                if (line.trim().startsWith(valueName) && type >= 0) {
                    String value = line.substring(type + "REG_SZ".length()).trim();
                    return value.isEmpty() ? null : value;
                }
            }
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static List<Path> libraries(Path steamRoot) {
        List<Path> libraries = new ArrayList<>();
        libraries.add(steamRoot);
        Path vdf = steamRoot.resolve("steamapps").resolve("libraryfolders.vdf");
        if (!Files.isRegularFile(vdf)) return libraries;
        try {
            Matcher matcher = LIBRARY_PATH.matcher(new String(Files.readAllBytes(vdf), StandardCharsets.UTF_8));
            while (matcher.find()) {
                Path path = Paths.get(matcher.group(1).replace("\\\\", "\\"));
                if (Files.isDirectory(path)) libraries.add(path);
            }
        } catch (IOException ignored) {
            // A library list Steam is rewriting is read again on the next scan.
        }
        return libraries;
    }
}
