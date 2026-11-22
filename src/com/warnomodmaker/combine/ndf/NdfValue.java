package com.warnomodmaker.combine.ndf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, structurally comparable value of a compiled database. Object references are resolved: exported objects
 * become {@link ExportRef} by name and every other object is inlined as an {@link Obj}.
 * <p>
 * {@link #equals} compares content; object identity (one compiled object) is by instance, so code that cares about
 * identity uses {@link java.util.IdentityHashMap}.
 */
public abstract class NdfValue {
    private int hash;

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NdfValue) || other.getClass() != getClass()) return false;
        NdfValue value = (NdfValue) other;
        return value.hashCode() == hashCode() && equalsCore(value);
    }

    @Override
    public final int hashCode() {
        if (hash == 0) {
            int computed = computeHash();
            hash = computed == 0 ? 1 : computed;
        }
        return hash;
    }

    protected abstract boolean equalsCore(NdfValue other);

    protected abstract int computeHash();

    /** A short human-readable form for reports. */
    public abstract String describe();

    public static boolean same(NdfValue left, NdfValue right) {
        return left == null ? right == null : left.equals(right);
    }

    public static Map.Entry<String, NdfValue> field(String name, NdfValue value) {
        return new java.util.AbstractMap.SimpleImmutableEntry<>(name, value);
    }

    public static Map.Entry<NdfValue, NdfValue> entry(NdfValue key, NdfValue value) {
        return new java.util.AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    /** Value stored verbatim: numbers, vectors, hashes, GUIDs, and length-prefixed blobs (0x08, 0x14). */
    public static final class Raw extends NdfValue {
        public final int type;
        public final byte[] data;

        public Raw(int type, byte[] data) {
            this.type = type;
            this.data = data;
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            Raw o = (Raw) other;
            return o.type == type && Arrays.equals(o.data, data);
        }

        @Override
        protected int computeHash() {
            return 31 * type + Arrays.hashCode(data);
        }

        @Override
        public String describe() {
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            switch (type) {
                case 0x00: return data[0] != 0 ? "True" : "False";
                case 0x02: return Integer.toString(buffer.getInt());
                case 0x03: return Long.toString(buffer.getInt() & 0xFFFFFFFFL);
                case 0x05: return formatFloat(buffer.getFloat());
                default: return String.format("0x%02X:%s", type, Hex.upper(data));
            }
        }

        private static String formatFloat(float value) {
            if (value == (long) value && Math.abs(value) < 1e6) return Long.toString((long) value);
            String text = String.format("%.6g", value);
            if (text.contains("e")) return text.replaceAll("\\.?0+e", "E");
            return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
        }
    }

    /** String-table value: 0x07 string literal or 0x1C file path. */
    public static final class Text extends NdfValue {
        public final int type;
        public final String text;

        public Text(int type, String text) {
            this.type = type;
            this.text = text;
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            Text o = (Text) other;
            return o.type == type && o.text.equals(text);
        }

        @Override
        protected int computeHash() {
            return 31 * type + text.hashCode();
        }

        @Override
        public String describe() {
            return "'" + text + "'";
        }
    }

    /** Null object reference. */
    public static final class Null extends NdfValue {
        public static final Null INSTANCE = new Null();

        private Null() {
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            return true;
        }

        @Override
        protected int computeHash() {
            return 6;
        }

        @Override
        public String describe() {
            return "nil";
        }
    }

    /** Reference to an object exported from another database (an IMPR entry such as $/GFX/Weapon/X). */
    public static final class Import extends NdfValue {
        public final String path;

        public Import(String path) {
            this.path = path;
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            return ((Import) other).path.equals(path);
        }

        @Override
        protected int computeHash() {
            return 31 + path.hashCode();
        }

        @Override
        public String describe() {
            return path;
        }
    }

    /** Reference to an object exported from the same database. */
    public static final class ExportRef extends NdfValue {
        public final String path;

        public ExportRef(String path) {
            this.path = path;
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            return ((ExportRef) other).path.equals(path);
        }

        @Override
        protected int computeHash() {
            return 62 + path.hashCode();
        }

        @Override
        public String describe() {
            return path;
        }
    }

    /** A compiled object. {@link #origin} is not part of equality. */
    public static final class Obj extends NdfValue {
        public final String className;
        public final List<Map.Entry<String, NdfValue>> fields;
        /**
         * The compiled object this instance was decompiled from, kept by the copies the merger makes of it, so a
         * written object carries its debug line. Null for objects with no compiled counterpart.
         */
        public final NdfOrigin origin;

        public Obj(String className, List<Map.Entry<String, NdfValue>> fields, NdfOrigin origin) {
            this.className = className;
            this.fields = fields;
            this.origin = origin;
        }

        public NdfValue get(String name) {
            for (Map.Entry<String, NdfValue> field : fields) {
                if (field.getKey().equals(name)) return field.getValue();
            }
            return null;
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            Obj o = (Obj) other;
            if (!o.className.equals(className) || o.fields.size() != fields.size()) return false;
            for (int i = 0; i < fields.size(); i++) {
                if (!o.fields.get(i).getKey().equals(fields.get(i).getKey())
                    || !o.fields.get(i).getValue().equals(fields.get(i).getValue())) return false;
            }
            return true;
        }

        @Override
        protected int computeHash() {
            int h = className.hashCode();
            for (Map.Entry<String, NdfValue> field : fields) {
                h = 31 * (31 * h + field.getKey().hashCode()) + field.getValue().hashCode();
            }
            return h;
        }

        @Override
        public String describe() {
            return className + "(…)";
        }
    }

    public static final class ListV extends NdfValue {
        public final List<NdfValue> items;

        public ListV(List<NdfValue> items) {
            this.items = items;
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            return ((ListV) other).items.equals(items);
        }

        @Override
        protected int computeHash() {
            int h = 3;
            for (NdfValue item : items) h = 31 * h + item.hashCode();
            return h;
        }

        @Override
        public String describe() {
            return "[" + items.size() + " items]";
        }
    }

    public static final class MapV extends NdfValue {
        public final List<Map.Entry<NdfValue, NdfValue>> entries;

        public MapV(List<Map.Entry<NdfValue, NdfValue>> entries) {
            this.entries = entries;
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            MapV o = (MapV) other;
            if (o.entries.size() != entries.size()) return false;
            for (int i = 0; i < entries.size(); i++) {
                if (!o.entries.get(i).getKey().equals(entries.get(i).getKey())
                    || !o.entries.get(i).getValue().equals(entries.get(i).getValue())) return false;
            }
            return true;
        }

        @Override
        protected int computeHash() {
            int h = 4;
            for (Map.Entry<NdfValue, NdfValue> entry : entries) {
                h = 31 * (31 * h + entry.getKey().hashCode()) + entry.getValue().hashCode();
            }
            return h;
        }

        @Override
        public String describe() {
            return "MAP[" + entries.size() + " entries]";
        }
    }

    public static final class Pair extends NdfValue {
        public final NdfValue first;
        public final NdfValue second;

        public Pair(NdfValue first, NdfValue second) {
            this.first = first;
            this.second = second;
        }

        @Override
        protected boolean equalsCore(NdfValue other) {
            Pair o = (Pair) other;
            return o.first.equals(first) && o.second.equals(second);
        }

        @Override
        protected int computeHash() {
            return Objects.hash(5, first.hashCode(), second.hashCode());
        }

        @Override
        public String describe() {
            return "(" + first.describe() + ", " + second.describe() + ")";
        }
    }
}
