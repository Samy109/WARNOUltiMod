package com.warnomodmaker.combine.ndf;

import java.util.List;

/** Identity of one compiled object: its number in one decompiled database. Compared by reference. */
public final class NdfOrigin {
    private static final byte[] EMPTY = new byte[0];

    /** The .dbg line of each object of one decompiled database by number, or null without a .dbg. */
    private final List<byte[]> debugLines;
    private final int index;

    NdfOrigin(List<byte[]> debugLines, int index) {
        this.debugLines = debugLines;
        this.index = index;
    }

    /** The object's .dbg line; empty when its database came without debug info. */
    public byte[] debugLine() {
        return debugLines == null ? EMPTY : debugLines.get(index);
    }
}
