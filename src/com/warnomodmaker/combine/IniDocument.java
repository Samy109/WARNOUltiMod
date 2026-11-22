package com.warnomodmaker.combine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.warnomodmaker.combine.ndf.IgnoreCase;

/** A mod's Config.ini: case-insensitive sections and keys, kept in file order. */
public final class IniDocument {
    // Keyed by upper-cased name; each entry keeps the name as first written.
    private final Map<String, Section> sections = new LinkedHashMap<>();

    private static final class Section {
        final String name;
        final Map<String, String[]> values = new LinkedHashMap<>();

        Section(String name) {
            this.name = name;
        }
    }

    public static IniDocument load(Path path) {
        try {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);
            return parse(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static IniDocument parse(String text) {
        IniDocument document = new IniDocument();
        String section = "";
        for (String rawLine : text.split("\r\n|\n|\r", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;
            if (line.startsWith("[")) {
                int closing = line.indexOf(']');
                if (closing > 1) {
                    String trailing = line.substring(closing + 1).trim();
                    if (trailing.isEmpty() || trailing.startsWith(";") || trailing.startsWith("#")) {
                        section = line.substring(1, closing).trim();
                        document.section(section);
                        continue;
                    }
                }
            }
            int equals = line.indexOf('=');
            if (equals <= 0) continue;
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1);
            int semicolon = value.indexOf(';');
            if (semicolon >= 0) value = value.substring(0, semicolon);
            document.set(section, key, value.trim());
        }
        return document;
    }

    private Section section(String name) {
        return sections.computeIfAbsent(IgnoreCase.upper(name), k -> new Section(name));
    }

    public String get(String section, String key) {
        Section values = sections.get(IgnoreCase.upper(section));
        if (values == null) return null;
        String[] entry = values.values.get(IgnoreCase.upper(key));
        return entry == null ? null : entry[1];
    }

    public int getInt(String section, String key, int fallback) {
        String value = get(section, key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A copy of one section, case-insensitive, in file order. */
    public TreeMap<String, String> getSection(String section) {
        TreeMap<String, String> result = IgnoreCase.map();
        Section values = sections.get(IgnoreCase.upper(section));
        if (values != null) for (String[] entry : values.values.values()) result.put(entry[0], entry[1]);
        return result;
    }

    /** The keys of one section in file order. */
    public Map<String, String> orderedSection(String section) {
        Map<String, String> result = new LinkedHashMap<>();
        Section values = sections.get(IgnoreCase.upper(section));
        if (values != null) for (String[] entry : values.values.values()) result.put(entry[0], entry[1]);
        return result;
    }

    public void set(String section, String key, String value) {
        Map<String, String[]> values = section(section).values;
        String[] existing = values.get(IgnoreCase.upper(key));
        if (existing != null) existing[1] = value;
        else values.put(IgnoreCase.upper(key), new String[]{key, value});
    }

    public void save(Path path) {
        StringBuilder builder = new StringBuilder();
        for (Section section : sections.values()) {
            if (!section.name.isEmpty()) builder.append('[').append(section.name).append("]\r\n");
            for (String[] entry : section.values.values()) builder.append(entry[0]).append(" = ").append(entry[1]).append("\r\n");
            builder.append("\r\n");
        }
        try {
            Files.write(path, builder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
