package com.warnomodmaker.combine.ndf;

import java.util.Comparator;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Ordinal, case-insensitive ordering of paths and names, as Windows (and WARNO's own tools) compare them: characters
 * are compared upper-cased, by code point. Unlike {@link String#CASE_INSENSITIVE_ORDER}, which compares lower-cased
 * characters, this puts '_' after letters, so file orders (and the fingerprints hashed over them) match the ones
 * WARNO UltiAI MODerator recorded.
 */
public final class IgnoreCase {
    public static final Comparator<String> ORDER = (a, b) -> {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            char x = Character.toUpperCase(a.charAt(i));
            char y = Character.toUpperCase(b.charAt(i));
            if (x != y) return x - y;
        }
        return a.length() - b.length();
    };

    private IgnoreCase() {
    }

    public static <V> TreeMap<String, V> map() {
        return new TreeMap<>(ORDER);
    }

    public static TreeSet<String> set() {
        return new TreeSet<>(ORDER);
    }

    public static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }
}
