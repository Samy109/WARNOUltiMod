package com.warnomodmaker.combine.ndf;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Since Tropic Storm WARNO refuses a pool (TPoolModel) whose ModelList lists a Required element after an optional
 * one: "'Required' pool element (DescriptorId ...) is placed after a non 'Required' pool element". A combined mod
 * ships every pool in that order, whether it was merged from several mods or taken whole from one: Required elements
 * first, each group keeping its order. An element without Required set is optional, as vanilla and every mod treat it.
 */
public final class NdfPoolOrder {
    private static final String POOL_CLASS = "TPoolModel";
    private static final String LIST_FIELD = "ModelList";

    private NdfPoolOrder() {
    }

    /** A settled database and the pools that were reordered (by export name, or the export they sit in). */
    public static final class Result {
        public final NdfDatabase database;
        public final List<String> reordered;

        Result(NdfDatabase database, List<String> reordered) {
            this.database = database;
            this.reordered = reordered;
        }
    }

    public static boolean isRequired(NdfValue element) {
        if (!(element instanceof NdfValue.Obj)) return false;
        NdfValue flag = ((NdfValue.Obj) element).get("Required");
        return flag instanceof NdfValue.Raw && ((NdfValue.Raw) flag).type == 0x00 && ((NdfValue.Raw) flag).data[0] != 0;
    }

    public static boolean breaksRule(NdfValue.Obj pool) {
        if (!pool.className.equals(POOL_CLASS) || !(pool.get(LIST_FIELD) instanceof NdfValue.ListV)) return false;
        boolean optionalSeen = false;
        for (NdfValue item : ((NdfValue.ListV) pool.get(LIST_FIELD)).items) {
            if (!isRequired(item)) optionalSeen = true;
            else if (optionalSeen) return true;
        }
        return false;
    }

    /** The database with every pool in WARNO's order; the same instance when every pool already is. */
    public static Result settle(NdfDatabase database) {
        Set<String> changed = new LinkedHashSet<>();
        String[] owner = {""};
        NdfRewrite rewrite = new NdfRewrite(null, null, result -> {
            if (!breaksRule(result)) return result;
            List<NdfValue> items = ((NdfValue.ListV) result.get(LIST_FIELD)).items;
            List<NdfValue> ordered = new ArrayList<>(items.size());
            for (NdfValue item : items) if (isRequired(item)) ordered.add(item);
            for (NdfValue item : items) if (!isRequired(item)) ordered.add(item);
            List<Map.Entry<String, NdfValue>> fields = new ArrayList<>(result.fields.size());
            for (Map.Entry<String, NdfValue> f : result.fields)
                fields.add(f.getKey().equals(LIST_FIELD) ? NdfValue.field(f.getKey(), new NdfValue.ListV(ordered)) : f);
            changed.add(owner[0]);
            return new NdfValue.Obj(result.className, fields, result.origin);
        });
        List<NdfDatabase.TopLevel> topLevel = new ArrayList<>(database.topLevel.size());
        for (NdfDatabase.TopLevel top : database.topLevel) {
            owner[0] = top.exportPath != null ? top.exportPath.substring(top.exportPath.lastIndexOf('/') + 1) : "an anonymous constant";
            topLevel.add(top.withValue(rewrite.object(top.value)));
        }
        List<String> reordered = new ArrayList<>(changed);
        return new Result(changed.isEmpty() ? database : new NdfDatabase(topLevel, database.propertyOwners), reordered);
    }
}
