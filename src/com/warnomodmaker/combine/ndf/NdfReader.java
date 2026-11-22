package com.warnomodmaker.combine.ndf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Decompiles a compiled database (.txn, or .ndfbin before the November 2026 update) into an {@link NdfDatabase}. */
final class NdfReader {
    private static final int OBJECT_END = 0xABABABAB, IMPORT_REF = 0xAAAAAAAA, OBJECT_REF = 0xBBBBBBBB;

    /** Payload sizes of fixed-width value tags (verified against every Workshop database); -1 when not fixed. */
    static int fixedSize(int type) {
        switch (type) {
            case 0x00: case 0x01: return 1;
            case 0x18: case 0x19: return 2;
            case 0x02: case 0x03: case 0x05: case 0x0D: return 4;
            case 0x06: case 0x13: case 0x1D: case 0x1F: case 0x21: return 8;
            case 0x0B: case 0x0F: return 12;
            case 0x0C: case 0x10: case 0x1A: case 0x25: return 16;
            default: return -1;
        }
    }

    private static final class Table {
        final int offset, size;

        Table(int offset, int size) {
            this.offset = offset;
            this.size = size;
        }
    }

    // Raw values keep object references as indices so sharing can be resolved after all objects are read.
    private static final class RawRef {
        final int index;

        RawRef(int index) {
            this.index = index;
        }
    }

    private static final class RawList {
        final List<Object> items;

        RawList(List<Object> items) {
            this.items = items;
        }
    }

    private static final class RawMap {
        final List<Object[]> entries;

        RawMap(List<Object[]> entries) {
            this.entries = entries;
        }
    }

    private static final class RawPair {
        final Object first, second;

        RawPair(Object first, Object second) {
            this.first = first;
            this.second = second;
        }
    }

    private static final class RawObject {
        final int cls;
        final int[] props;
        final Object[] values;

        RawObject(int cls, int[] props, Object[] values) {
            this.cls = cls;
            this.props = props;
            this.values = values;
        }
    }

    private final byte[] d;
    private final ByteBuffer buffer;
    private final String[] classes, strings, tran;
    private final String[] propNames;
    private final int[] propClasses;
    private final List<RawObject> raw = new ArrayList<>();
    private final Map<Integer, String> exports, imports;
    private final int[] topo;
    private final NdfValue.Obj[] built;
    private final boolean[] building;
    private final Map<NdfDatabase.PropKey, String> owners = new HashMap<>();
    private final List<byte[]> debugLines;
    private int p;

    private NdfReader(byte[] file, byte[] debugInfo) {
        NdfContainer.Decompressed decompressed = NdfContainer.decompress(file);
        d = decompressed.data;
        buffer = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, Table> tables = readToc((int) decompressed.tocOffset);
        classes = readStrings(tables.get("CLAS"));
        strings = readStrings(tables.get("STRG"));
        tran = readStrings(tables.get("TRAN"));
        Table props = tables.get("PROP");
        List<String> names = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        for (int q = props.offset, end = props.offset + props.size; q < end; ) {
            int length = u32(q);
            names.add(latin1(q + 4, length));
            owners.add(u32(q + 4 + length));
            q += 8 + length;
        }
        propNames = names.toArray(new String[0]);
        propClasses = owners.stream().mapToInt(Integer::intValue).toArray();
        exports = readTree(tables.get("EXPR"));
        imports = readTree(tables.get("IMPR"));
        Table topoTable = tables.get("TOPO");
        topo = new int[topoTable.size / 4];
        for (int i = 0; i < topo.length; i++) topo[i] = u32(topoTable.offset + 4 * i);
        readObjects(tables.get("OBJE"));
        built = new NdfValue.Obj[raw.size()];
        building = new boolean[raw.size()];
        debugLines = debugInfo == null ? null : NdfDebugInfo.read(debugInfo);
        if (debugLines != null && debugLines.size() != raw.size())
            throw new NdfFormatException(String.format("The debug info lists %,d objects but the database has %,d.", debugLines.size(), raw.size()));
    }

    static NdfDatabase read(byte[] file, byte[] debugInfo) {
        return new NdfReader(file, debugInfo).build();
    }

    private int u32(int at) {
        return buffer.getInt(at);
    }

    private String latin1(int at, int length) {
        return new String(d, at, length, StandardCharsets.ISO_8859_1);
    }

    private Map<String, Table> readToc(int at) {
        if (!latin1(at, 4).equals("TOC0")) throw new NdfFormatException("Missing TOC0.");
        int count = u32(at + 4);
        Map<String, Table> result = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int e = at + 8 + 24 * i;
            result.put(latin1(e, 4), new Table((int) buffer.getLong(e + 8), (int) buffer.getLong(e + 16)));
        }
        for (String name : new String[]{"OBJE", "TOPO", "CHNK", "CLAS", "PROP", "STRG", "TRAN", "IMPR", "EXPR"}) {
            if (!result.containsKey(name)) throw new NdfFormatException("Missing " + name + " table.");
        }
        return result;
    }

    private String[] readStrings(Table table) {
        List<String> list = new ArrayList<>();
        for (int q = table.offset, end = table.offset + table.size; q < end; ) {
            int length = u32(q);
            list.add(latin1(q + 4, length));
            q += 4 + length;
        }
        return list.toArray(new String[0]);
    }

    // Node: u32 TRAN index, u32 target (0xFFFFFFFF = none), u32 child count, then child offsets measured from
    // the start of the offset array.
    private Map<Integer, String> readTree(Table table) {
        Map<Integer, String> result = new HashMap<>();
        if (table.size == 0) return result;
        walk(table.offset, "", result);
        return result;
    }

    private void walk(int at, String prefix, Map<Integer, String> result) {
        String path = prefix.isEmpty() ? tran[u32(at)] : prefix + "/" + tran[u32(at)];
        int target = u32(at + 4);
        if (target != -1) result.put(target, path);
        int children = u32(at + 8);
        for (int i = 0; i < children; i++) walk(at + 12 + u32(at + 12 + 4 * i), path, result);
    }

    private void readObjects(Table table) {
        p = table.offset;
        int end = table.offset + table.size;
        List<Integer> props = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        while (p < end) {
            int cls = u32(p);
            p += 4;
            props.clear();
            values.clear();
            while (true) {
                int prop = u32(p);
                p += 4;
                if (prop == OBJECT_END) break;
                props.add(prop);
                values.add(readValue());
            }
            raw.add(new RawObject(cls, props.stream().mapToInt(Integer::intValue).toArray(), values.toArray()));
        }
        if (p != end) throw new NdfFormatException("Object table overran its bounds.");
    }

    private Object readValue() {
        int type = u32(p);
        p += 4;
        int size = fixedSize(type);
        if (size >= 0) {
            byte[] data = new byte[size];
            System.arraycopy(d, p, data, 0, size);
            p += size;
            return new NdfValue.Raw(type, data);
        }
        switch (type) {
            case 0x07:
            case 0x1C: {
                int index = u32(p);
                p += 4;
                return new NdfValue.Text(type, strings[index]);
            }
            case 0x08:
            case 0x14: {
                int length = u32(p);
                byte[] bytes = new byte[length];
                System.arraycopy(d, p + 4, bytes, 0, length);
                p += 4 + length;
                return new NdfValue.Raw(type, bytes);
            }
            case 0x09: {
                int kind = u32(p);
                if (kind == IMPORT_REF) {
                    int importIndex = u32(p + 4);
                    p += 8;
                    String importPath = imports.get(importIndex);
                    if (importPath == null) throw new NdfFormatException("Unnamed import " + importIndex + ".");
                    return new NdfValue.Import(importPath);
                }
                if (kind == OBJECT_REF) {
                    int target = u32(p + 4);
                    p += 12;
                    return target == -1 ? NdfValue.Null.INSTANCE : new RawRef(target);
                }
                throw new NdfFormatException(String.format("Unknown reference kind 0x%08X.", kind));
            }
            case 0x11: {
                int count = u32(p);
                p += 4;
                List<Object> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) items.add(readValue());
                return new RawList(items);
            }
            case 0x12: {
                int count = u32(p);
                p += 4;
                List<Object[]> map = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    Object key = readValue();
                    map.add(new Object[]{key, readValue()});
                }
                return new RawMap(map);
            }
            case 0x22: {
                Object first = readValue();
                return new RawPair(first, readValue());
            }
            default:
                throw new NdfFormatException(String.format("Unknown NDF value tag 0x%02X at offset %d.", type, p - 4));
        }
    }

    private NdfDatabase build() {
        List<NdfDatabase.TopLevel> topLevel = new ArrayList<>(topo.length);
        for (int index : topo) topLevel.add(new NdfDatabase.TopLevel(exports.get(index), buildObject(index)));
        for (int i = 0; i < built.length; i++) {
            if (built[i] == null) throw new NdfFormatException("Object " + i + " is not reachable from the top level.");
        }
        return new NdfDatabase(topLevel, owners);
    }

    private NdfValue.Obj buildObject(int index) {
        if (built[index] != null) return built[index];
        if (building[index]) throw new NdfFormatException("Cyclic object references are not supported.");
        building[index] = true;
        RawObject object = raw.get(index);
        List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(object.props.length);
        for (int i = 0; i < object.props.length; i++) {
            String name = propNames[object.props[i]];
            int owner = propClasses[object.props[i]];
            if (owner != object.cls) owners.put(new NdfDatabase.PropKey(classes[object.cls], name), classes[owner]);
            fields.add(NdfValue.field(name, convert(object.values[i])));
        }
        building[index] = false;
        return built[index] = new NdfValue.Obj(classes[object.cls], fields, new NdfOrigin(debugLines, index));
    }

    private NdfValue convert(Object value) {
        if (value instanceof NdfValue) return (NdfValue) value;
        if (value instanceof RawRef) {
            int index = ((RawRef) value).index;
            String path = exports.get(index);
            return path != null ? new NdfValue.ExportRef(path) : buildObject(index);
        }
        if (value instanceof RawList) {
            List<Object> items = ((RawList) value).items;
            List<NdfValue> converted = new ArrayList<>(items.size());
            for (Object item : items) converted.add(convert(item));
            return new NdfValue.ListV(converted);
        }
        if (value instanceof RawMap) {
            List<Object[]> entries = ((RawMap) value).entries;
            List<Map.Entry<NdfValue, NdfValue>> converted = new ArrayList<>(entries.size());
            for (Object[] entry : entries) converted.add(NdfValue.entry(convert(entry[0]), convert(entry[1])));
            return new NdfValue.MapV(converted);
        }
        if (value instanceof RawPair) {
            RawPair pair = (RawPair) value;
            return new NdfValue.Pair(convert(pair.first), convert(pair.second));
        }
        throw new IllegalStateException();
    }
}
