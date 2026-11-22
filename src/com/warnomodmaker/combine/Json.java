package com.warnomodmaker.combine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer for the combine engine's records and reports. Objects read as
 * {@code LinkedHashMap<String, Object>}, arrays as {@code List<Object>}, numbers as {@code Long} or {@code Double}.
 */
public final class Json {
    private final String text;
    private int p;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json json = new Json(text.startsWith("﻿") ? text.substring(1) : text);
        json.skip();
        Object value = json.value();
        json.skip();
        if (json.p != json.text.length()) throw json.error("Unexpected text after the value");
        return value;
    }

    public static final class JsonException extends RuntimeException {
        JsonException(String message) {
            super(message);
        }
    }

    private JsonException error(String message) {
        return new JsonException(message + " at offset " + p + ".");
    }

    private void skip() {
        while (p < text.length() && Character.isWhitespace(text.charAt(p))) p++;
    }

    private Object value() {
        if (p >= text.length()) throw error("Unexpected end");
        char c = text.charAt(p);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default:
                if (c == '-' || Character.isDigit(c)) return number();
                throw error("Unexpected '" + c + "'");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, p)) throw error("Expected " + word);
        p += word.length();
        return value;
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        p++;
        skip();
        if (peek('}')) return map;
        while (true) {
            skip();
            if (p >= text.length() || text.charAt(p) != '"') throw error("Expected a property name");
            String key = string();
            skip();
            expect(':');
            skip();
            map.put(key, value());
            skip();
            if (peek('}')) return map;
            expect(',');
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        p++;
        skip();
        if (peek(']')) return list;
        while (true) {
            skip();
            list.add(value());
            skip();
            if (peek(']')) return list;
            expect(',');
        }
    }

    private boolean peek(char c) {
        if (p < text.length() && text.charAt(p) == c) {
            p++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!peek(c)) throw error("Expected '" + c + "'");
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        p++;
        while (true) {
            if (p >= text.length()) throw error("Unterminated string");
            char c = text.charAt(p++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = text.charAt(p++);
            switch (e) {
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u': out.append((char) Integer.parseInt(text.substring(p, p + 4), 16)); p += 4; break;
                default: out.append(e);
            }
        }
    }

    private Object number() {
        int start = p;
        while (p < text.length() && "+-0123456789.eE".indexOf(text.charAt(p)) >= 0) p++;
        String n = text.substring(start, p);
        try {
            return n.contains(".") || n.contains("e") || n.contains("E") ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n);
        } catch (NumberFormatException e) {
            throw error("Bad number " + n);
        }
    }

    // ───────────── Writing ─────────────

    /** Indented JSON for maps, lists, strings, numbers, booleans and null. Null map values are left out. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, "");
        return out.toString();
    }

    private static void write(StringBuilder out, Object value, String indent) {
        if (value == null) out.append("null");
        else if (value instanceof String) quote(out, (String) value);
        else if (value instanceof Number || value instanceof Boolean) out.append(value);
        else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            List<Map.Entry<?, ?>> entries = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) if (e.getValue() != null) entries.add(e);
            if (entries.isEmpty()) {
                out.append("{}");
                return;
            }
            String inner = indent + "  ";
            out.append("{\n");
            for (int i = 0; i < entries.size(); i++) {
                out.append(inner);
                quote(out, String.valueOf(entries.get(i).getKey()));
                out.append(": ");
                write(out, entries.get(i).getValue(), inner);
                out.append(i < entries.size() - 1 ? ",\n" : "\n");
            }
            out.append(indent).append('}');
        } else if (value instanceof Iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : (Iterable<?>) value) items.add(item);
            if (items.isEmpty()) {
                out.append("[]");
                return;
            }
            String inner = indent + "  ";
            out.append("[\n");
            for (int i = 0; i < items.size(); i++) {
                out.append(inner);
                write(out, items.get(i), inner);
                out.append(i < items.size() - 1 ? ",\n" : "\n");
            }
            out.append(indent).append(']');
        } else {
            quote(out, value.toString());
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    // ───────────── Reading helpers ─────────────

    public static String str(Map<?, ?> map, String key) {
        Object value = get(map, key);
        return value instanceof String ? (String) value : null;
    }

    public static Integer integer(Map<?, ?> map, String key) {
        Object value = get(map, key);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    public static boolean bool(Map<?, ?> map, String key) {
        return Boolean.TRUE.equals(get(map, key));
    }

    /** A property by name, matching case-insensitively when there is no exact match. */
    public static Object get(Map<?, ?> map, String key) {
        if (map.containsKey(key)) return map.get(key);
        for (Map.Entry<?, ?> e : map.entrySet()) if (String.valueOf(e.getKey()).equalsIgnoreCase(key)) return e.getValue();
        return null;
    }
}
