package com.warnomodmaker.combine.ndf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles an {@link NdfDatabase} into a zstd-compressed database laid out exactly as WARNO's compiler lays it out:
 * an unchanged database is written back byte for byte (before compression). Each instance is one object.
 */
public final class NdfWriter {
    /** A compiled database and its .dbg companion. */
    public static final class Output {
        public final byte[] database;
        public final byte[] debugInfo;

        Output(byte[] database, byte[] debugInfo) {
            this.database = database;
            this.debugInfo = debugInfo;
        }
    }

    private final NdfDatabase db;
    private final List<NdfValue.Obj> objects = new ArrayList<>();
    // Each object's references (inline objects, exports and anonymous top-level objects) in field order.
    private final List<List<Integer>> references = new ArrayList<>();
    private final Map<NdfValue.Obj, Integer> index = new IdentityHashMap<>();
    private final Map<String, NdfValue.Obj> exports = new HashMap<>();
    private final Map<String, Integer> exportIndex = new LinkedHashMap<>();
    private final List<Integer> topo = new ArrayList<>();
    private final IndexedTable<String> classes = new IndexedTable<>(), strings = new IndexedTable<>(),
        tran = new IndexedTable<>(), imports = new IndexedTable<>();
    private final IndexedTable<PropRef> props = new IndexedTable<>();

    private static final class PropRef {
        final int cls;
        final String name;

        PropRef(int cls, String name) {
            this.cls = cls;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PropRef && ((PropRef) o).cls == cls && ((PropRef) o).name.equals(name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cls, name);
        }
    }

    private NdfWriter(NdfDatabase db) {
        this.db = db;
        Map<NdfValue.Obj, Boolean> topLevel = new IdentityHashMap<>();
        for (NdfDatabase.TopLevel top : db.topLevel) {
            if (topLevel.put(top.value, Boolean.TRUE) != null)
                throw new NdfFormatException((top.exportPath != null ? top.exportPath : "An anonymous object") + " is listed twice at the top level.");
            if (top.exportPath != null && exports.putIfAbsent(top.exportPath, top.value) != null)
                throw new NdfFormatException("Duplicate export " + top.exportPath + ".");
        }
    }

    public static Output write(NdfDatabase db) {
        NdfWriter writer = new NdfWriter(db);
        byte[] database = writer.compile();
        List<byte[]> lines = new ArrayList<>(writer.objects.size());
        for (NdfValue.Obj object : writer.objects) lines.add(object.origin != null ? object.origin.debugLine() : new byte[0]);
        return new Output(database, NdfDebugInfo.write(lines));
    }

    private byte[] compile() {
        assignIndices();
        LeBuffer body = new LeBuffer(1 << 16);
        List<Object[]> tables = new ArrayList<>();

        long start = NdfContainer.HEADER_SIZE + body.size();
        writeObjects(body); // Objects first: encoding them populates the class, property, string and import tables.
        start = table(tables, "OBJE", start, body);
        for (int i : topo) body.writeInt(i);
        start = table(tables, "TOPO", start, body);
        body.writeInt(0);
        body.writeInt(objects.size());
        start = table(tables, "CHNK", start, body);
        for (String name : classes.items) writeString(body, name);
        start = table(tables, "CLAS", start, body);
        for (PropRef prop : props.items) {
            writeString(body, prop.name);
            body.writeInt(prop.cls);
        }
        start = table(tables, "PROP", start, body);
        for (String text : strings.items) writeString(body, text);
        start = table(tables, "STRG", start, body);
        List<String[]> importPaths = new ArrayList<>();
        for (int i = 0; i < imports.items.size(); i++) importPaths.add(new String[]{imports.items.get(i), Integer.toString(i)});
        List<String[]> exportPaths = new ArrayList<>();
        for (Map.Entry<String, Integer> e : exportIndex.entrySet()) exportPaths.add(new String[]{e.getKey(), Integer.toString(e.getValue())});
        TreeNode importTree = buildTree(importPaths);
        TreeNode exportTree = buildTree(exportPaths);
        for (String segment : tran.items) writeString(body, segment);
        start = table(tables, "TRAN", start, body);
        writeTree(body, importTree);
        start = table(tables, "IMPR", start, body);
        writeTree(body, exportTree);
        table(tables, "EXPR", start, body);

        long toc = NdfContainer.HEADER_SIZE + body.size();
        body.writeBytes("TOC0".getBytes(StandardCharsets.US_ASCII));
        body.writeInt(tables.size());
        for (Object[] table : tables) {
            body.writeBytes(((String) table[0]).getBytes(StandardCharsets.US_ASCII));
            body.writeInt(0);
            body.writeLong((Long) table[1]);
            body.writeLong((Long) table[2]);
        }
        return NdfContainer.compress(body.array(), body.size(), toc);
    }

    private static long table(List<Object[]> tables, String name, long start, LeBuffer body) {
        long end = NdfContainer.HEADER_SIZE + body.size();
        tables.add(new Object[]{name, start, end - start});
        return end;
    }

    private static void writeString(LeBuffer w, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        w.writeInt(bytes.length);
        w.writeBytes(bytes);
    }

    /**
     * Numbers objects as WARNO's compiler does: breadth first from each top-level object in declaration order, so an
     * object's direct references follow it, then theirs. An object referenced again (including an export or an
     * anonymous top-level object reached before its own turn) keeps its first number.
     */
    private void assignIndices() {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (NdfDatabase.TopLevel top : db.topLevel) {
            resolve(top.value, queue);
            while (!queue.isEmpty()) {
                int current = queue.poll();
                List<Integer> refs = references.get(current);
                for (Map.Entry<String, NdfValue> field : objects.get(current).fields) collect(field.getValue(), refs, queue);
            }
        }
        for (NdfDatabase.TopLevel top : db.topLevel) {
            int i = index.get(top.value);
            topo.add(i);
            if (top.exportPath != null) exportIndex.put(top.exportPath, i);
        }
    }

    private void collect(NdfValue value, List<Integer> refs, ArrayDeque<Integer> queue) {
        if (value instanceof NdfValue.Obj) {
            refs.add(resolve((NdfValue.Obj) value, queue));
        } else if (value instanceof NdfValue.ExportRef) {
            String path = ((NdfValue.ExportRef) value).path;
            NdfValue.Obj target = exports.get(path);
            if (target == null) throw new NdfFormatException("Reference to missing export " + path + ".");
            refs.add(resolve(target, queue));
        } else if (value instanceof NdfValue.ListV) {
            for (NdfValue item : ((NdfValue.ListV) value).items) collect(item, refs, queue);
        } else if (value instanceof NdfValue.MapV) {
            for (Map.Entry<NdfValue, NdfValue> entry : ((NdfValue.MapV) value).entries) {
                collect(entry.getKey(), refs, queue);
                collect(entry.getValue(), refs, queue);
            }
        } else if (value instanceof NdfValue.Pair) {
            collect(((NdfValue.Pair) value).first, refs, queue);
            collect(((NdfValue.Pair) value).second, refs, queue);
        }
    }

    private int resolve(NdfValue.Obj value, ArrayDeque<Integer> queue) {
        Integer existing = index.get(value);
        if (existing != null) return existing;
        int i = objects.size();
        objects.add(value);
        references.add(new ArrayList<>());
        index.put(value, i);
        queue.add(i);
        return i;
    }

    private void writeObjects(LeBuffer w) {
        for (int i = 0; i < objects.size(); i++) {
            NdfValue.Obj value = objects.get(i);
            int cls = classes.get(value.className);
            w.writeInt(cls);
            int[] cursor = {0};
            for (Map.Entry<String, NdfValue> field : value.fields) {
                String declaring = db.propertyOwners.get(new NdfDatabase.PropKey(value.className, field.getKey()));
                int owner = declaring != null ? classes.get(declaring) : cls;
                w.writeInt(props.get(new PropRef(owner, field.getKey())));
                writeValue(w, field.getValue(), references.get(i), cursor);
            }
            w.writeInt(0xABABABAB);
        }
    }

    private void writeValue(LeBuffer w, NdfValue value, List<Integer> children, int[] cursor) {
        if (value instanceof NdfValue.Raw) {
            NdfValue.Raw raw = (NdfValue.Raw) value;
            w.writeInt(raw.type);
            if (raw.type == 0x08 || raw.type == 0x14) w.writeInt(raw.data.length);
            else if (NdfReader.fixedSize(raw.type) != raw.data.length)
                throw new NdfFormatException(String.format("Invalid payload for tag 0x%02X.", raw.type));
            w.writeBytes(raw.data);
        } else if (value instanceof NdfValue.Text) {
            NdfValue.Text text = (NdfValue.Text) value;
            w.writeInt(text.type);
            w.writeInt(strings.get(text.text));
        } else if (value instanceof NdfValue.Import) {
            w.writeInt(0x09);
            w.writeInt(0xAAAAAAAA);
            w.writeInt(imports.get(((NdfValue.Import) value).path));
        } else if (value instanceof NdfValue.ExportRef || value instanceof NdfValue.Obj) {
            int target = children.get(cursor[0]++);
            w.writeInt(0x09);
            w.writeInt(0xBBBBBBBB);
            w.writeInt(target);
            w.writeInt(classes.get(objects.get(target).className));
        } else if (value instanceof NdfValue.Null) {
            w.writeInt(0x09);
            w.writeInt(0xBBBBBBBB);
            w.writeInt(-1);
            w.writeInt(-1);
        } else if (value instanceof NdfValue.ListV) {
            List<NdfValue> items = ((NdfValue.ListV) value).items;
            w.writeInt(0x11);
            w.writeInt(items.size());
            for (NdfValue item : items) writeValue(w, item, children, cursor);
        } else if (value instanceof NdfValue.MapV) {
            List<Map.Entry<NdfValue, NdfValue>> entries = ((NdfValue.MapV) value).entries;
            w.writeInt(0x12);
            w.writeInt(entries.size());
            for (Map.Entry<NdfValue, NdfValue> entry : entries) {
                writeValue(w, entry.getKey(), children, cursor);
                writeValue(w, entry.getValue(), children, cursor);
            }
        } else if (value instanceof NdfValue.Pair) {
            w.writeInt(0x22);
            writeValue(w, ((NdfValue.Pair) value).first, children, cursor);
            writeValue(w, ((NdfValue.Pair) value).second, children, cursor);
        } else {
            throw new NdfFormatException("Cannot encode " + value.getClass().getSimpleName() + ".");
        }
    }

    private static final class TreeNode {
        final int segment;
        int target = -1;
        final List<TreeNode> children = new ArrayList<>();
        final Map<String, TreeNode> lookup = new HashMap<>();

        TreeNode(int segment) {
            this.segment = segment;
        }
    }

    private TreeNode buildTree(List<String[]> paths) {
        TreeNode root = null;
        String rootName = null;
        for (String[] entry : paths) {
            String[] segments = entry[0].split("/", -1);
            if (root == null) {
                rootName = segments[0];
                root = new TreeNode(tran.get(rootName));
            } else if (!segments[0].equals(rootName)) {
                throw new NdfFormatException("Path " + entry[0] + " has an unexpected root.");
            }
            TreeNode node = root;
            for (int i = 1; i < segments.length; i++) {
                TreeNode child = node.lookup.get(segments[i]);
                if (child == null) {
                    child = new TreeNode(tran.get(segments[i]));
                    node.lookup.put(segments[i], child);
                    node.children.add(child);
                }
                node = child;
            }
            node.target = Integer.parseInt(entry[1]);
        }
        return root;
    }

    private static void writeTree(LeBuffer w, TreeNode root) {
        if (root == null) return;
        LeBuffer buffer = new LeBuffer(1024);
        writeNode(buffer, root);
        w.writeBytes(Arrays.copyOf(buffer.array(), buffer.size()));
    }

    // Pre-order: header, child offset array, then each child subtree; offsets are back-patched. Children are in
    // TRAN order, as WARNO writes them.
    private static void writeNode(LeBuffer s, TreeNode node) {
        node.children.sort((a, b) -> Integer.compare(a.segment, b.segment));
        s.writeInt(node.segment);
        s.writeInt(node.target);
        s.writeInt(node.children.size());
        int offsets = s.size();
        for (int i = 0; i < node.children.size(); i++) s.writeInt(0);
        for (int i = 0; i < node.children.size(); i++) {
            int childStart = s.size();
            writeNode(s, node.children.get(i));
            s.putInt(offsets + 4 * i, childStart - offsets);
        }
    }

    private static final class IndexedTable<T> {
        private final Map<T, Integer> lookup = new HashMap<>();
        final List<T> items = new ArrayList<>();

        int get(T key) {
            Integer value = lookup.get(key);
            if (value != null) return value;
            lookup.put(key, items.size());
            items.add(key);
            return items.size() - 1;
        }
    }

    /** A growable little-endian byte buffer. */
    static final class LeBuffer {
        private byte[] data;
        private int size;

        LeBuffer(int capacity) {
            data = new byte[capacity];
        }

        private void ensure(int extra) {
            if (size + extra > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + extra));
        }

        void writeInt(int v) {
            ensure(4);
            putInt(size, v);
            size += 4;
        }

        void putInt(int at, int v) {
            data[at] = (byte) v;
            data[at + 1] = (byte) (v >>> 8);
            data[at + 2] = (byte) (v >>> 16);
            data[at + 3] = (byte) (v >>> 24);
        }

        void writeLong(long v) {
            writeInt((int) v);
            writeInt((int) (v >>> 32));
        }

        void writeBytes(byte[] bytes) {
            ensure(bytes.length);
            System.arraycopy(bytes, 0, data, size, bytes.length);
            size += bytes.length;
        }

        int size() {
            return size;
        }

        byte[] array() {
            return data;
        }
    }
}
