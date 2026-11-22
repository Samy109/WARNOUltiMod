package com.warnomodmaker.combine.ndf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Rebuilds a database or value with some objects or leaf values replaced, keeping every untouched instance (and so
 * the database's object identity) as it is. A replacement function returning null leaves the value alone.
 * <p>
 * Used by the merger, the pool-order rule and the mod rebaser, which each used to carry their own copy of this walk.
 */
public final class NdfRewrite {
    private final Function<NdfValue.Obj, NdfValue.Obj> replaceObject;
    private final Function<NdfValue, NdfValue> replaceLeaf;
    private final Function<NdfValue.Obj, NdfValue.Obj> afterObject;
    private final Map<NdfValue.Obj, NdfValue.Obj> done = new IdentityHashMap<>();

    public NdfRewrite(Function<NdfValue.Obj, NdfValue.Obj> replaceObject, Function<NdfValue, NdfValue> replaceLeaf) {
        this(replaceObject, replaceLeaf, null);
    }

    /** @param afterObject applied to each object once its fields are rewritten; may return the object itself. */
    public NdfRewrite(Function<NdfValue.Obj, NdfValue.Obj> replaceObject, Function<NdfValue, NdfValue> replaceLeaf,
                      Function<NdfValue.Obj, NdfValue.Obj> afterObject) {
        this.replaceObject = replaceObject;
        this.replaceLeaf = replaceLeaf;
        this.afterObject = afterObject;
    }

    public NdfDatabase apply(NdfDatabase database) {
        List<NdfDatabase.TopLevel> top = new ArrayList<>(database.topLevel.size());
        for (NdfDatabase.TopLevel t : database.topLevel) top.add(t.withValue(object(t.value)));
        return new NdfDatabase(top, database.propertyOwners);
    }

    /** True when {@link #apply} changed any top-level object. */
    public static boolean changed(NdfDatabase before, NdfDatabase after) {
        for (int i = 0; i < before.topLevel.size(); i++) {
            if (before.topLevel.get(i).value != after.topLevel.get(i).value) return true;
        }
        return false;
    }

    public NdfValue.Obj object(NdfValue.Obj value) {
        NdfValue.Obj cached = done.get(value);
        if (cached != null) return cached;
        if (replaceObject != null) {
            NdfValue.Obj replacement = replaceObject.apply(value);
            if (replacement != null && replacement != value) {
                NdfValue.Obj result = object(replacement);
                done.put(value, result);
                return result;
            }
        }
        done.put(value, value); // guards cycles
        List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(value.fields.size());
        boolean changed = false;
        for (Map.Entry<String, NdfValue> f : value.fields) {
            NdfValue field = value(f.getValue());
            changed |= field != f.getValue();
            fields.add(field == f.getValue() ? f : NdfValue.field(f.getKey(), field));
        }
        NdfValue.Obj result = changed ? new NdfValue.Obj(value.className, fields, value.origin) : value;
        if (afterObject != null) result = afterObject.apply(result);
        done.put(value, result);
        return result;
    }

    public NdfValue value(NdfValue value) {
        if (value instanceof NdfValue.Obj) return object((NdfValue.Obj) value);
        if (value instanceof NdfValue.ListV) {
            List<NdfValue> items = ((NdfValue.ListV) value).items;
            List<NdfValue> result = new ArrayList<>(items.size());
            boolean changed = false;
            for (NdfValue item : items) {
                NdfValue rewritten = value(item);
                changed |= rewritten != item;
                result.add(rewritten);
            }
            return changed ? new NdfValue.ListV(result) : value;
        }
        if (value instanceof NdfValue.MapV) {
            List<Map.Entry<NdfValue, NdfValue>> entries = ((NdfValue.MapV) value).entries;
            List<Map.Entry<NdfValue, NdfValue>> result = new ArrayList<>(entries.size());
            boolean changed = false;
            for (Map.Entry<NdfValue, NdfValue> entry : entries) {
                NdfValue key = value(entry.getKey());
                NdfValue v = value(entry.getValue());
                changed |= key != entry.getKey() || v != entry.getValue();
                result.add(NdfValue.entry(key, v));
            }
            return changed ? new NdfValue.MapV(result) : value;
        }
        if (value instanceof NdfValue.Pair) {
            NdfValue.Pair pair = (NdfValue.Pair) value;
            NdfValue first = value(pair.first);
            NdfValue second = value(pair.second);
            return first != pair.first || second != pair.second ? new NdfValue.Pair(first, second) : value;
        }
        if (replaceLeaf != null) {
            NdfValue replacement = replaceLeaf.apply(value);
            if (replacement != null) return replacement;
        }
        return value;
    }

    /** Every import and export reference path in a database, each once. */
    public static Set<String> referencePaths(NdfDatabase database) {
        Set<String> paths = new LinkedHashSet<>();
        Map<NdfValue.Obj, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<NdfValue> stack = new ArrayDeque<>();
        for (NdfDatabase.TopLevel t : database.topLevel) stack.push(t.value);
        while (!stack.isEmpty()) {
            NdfValue current = stack.pop();
            if (current instanceof NdfValue.Import) paths.add(((NdfValue.Import) current).path);
            else if (current instanceof NdfValue.ExportRef) paths.add(((NdfValue.ExportRef) current).path);
            else pushChildren(current, stack, seen);
        }
        return paths;
    }

    /** Pushes the children of a composite value; objects are visited once (by instance). */
    static void pushChildren(NdfValue current, ArrayDeque<NdfValue> stack, Map<NdfValue.Obj, Boolean> seen) {
        if (current instanceof NdfValue.Obj) {
            if (seen.put((NdfValue.Obj) current, Boolean.TRUE) == null)
                for (Map.Entry<String, NdfValue> f : ((NdfValue.Obj) current).fields) stack.push(f.getValue());
        } else if (current instanceof NdfValue.ListV) {
            for (NdfValue item : ((NdfValue.ListV) current).items) stack.push(item);
        } else if (current instanceof NdfValue.MapV) {
            for (Map.Entry<NdfValue, NdfValue> e : ((NdfValue.MapV) current).entries) {
                stack.push(e.getKey());
                stack.push(e.getValue());
            }
        } else if (current instanceof NdfValue.Pair) {
            stack.push(((NdfValue.Pair) current).first);
            stack.push(((NdfValue.Pair) current).second);
        }
    }
}
