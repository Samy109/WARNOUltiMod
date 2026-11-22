package com.warnomodmaker.combine.ndf;

import java.util.List;
import java.util.Map;

/**
 * A decompiled database. Object identity is by instance: one instance is one compiled object, however many places
 * reference it, and separate instances are separate objects even when their content is equal.
 */
public final class NdfDatabase {
    /** Key of an inherited property: (object class, property). */
    public static final class PropKey {
        public final String className;
        public final String property;

        public PropKey(String className, String property) {
            this.className = className;
            this.property = property;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PropKey && ((PropKey) o).className.equals(className) && ((PropKey) o).property.equals(property);
        }

        @Override
        public int hashCode() {
            return 31 * className.hashCode() + property.hashCode();
        }
    }

    /** A top-level object in TOPO order: exported by name, or anonymous (a shared private constant). */
    public static final class TopLevel {
        public final String exportPath;
        public final NdfValue.Obj value;

        public TopLevel(String exportPath, NdfValue.Obj value) {
            this.exportPath = exportPath;
            this.value = value;
        }

        public TopLevel withValue(NdfValue.Obj replacement) {
            return new TopLevel(exportPath, replacement);
        }
    }

    public final List<TopLevel> topLevel;
    /** Declaring class of inherited properties, keyed by (object class, property). */
    public final Map<PropKey, String> propertyOwners;

    public NdfDatabase(List<TopLevel> topLevel, Map<PropKey, String> propertyOwners) {
        this.topLevel = topLevel;
        this.propertyOwners = propertyOwners;
    }

    /** @param debugInfo the database's .dbg companion, or null. */
    public static NdfDatabase load(byte[] file, byte[] debugInfo) {
        return NdfReader.read(file, debugInfo);
    }

    public static NdfDatabase load(byte[] file) {
        return load(file, null);
    }

    public byte[] save() {
        return NdfWriter.write(this).database;
    }

    /** The compiled database and its .dbg companion, whose lines follow the written object order. */
    public NdfWriter.Output saveWithDebugInfo() {
        return NdfWriter.write(this);
    }
}
