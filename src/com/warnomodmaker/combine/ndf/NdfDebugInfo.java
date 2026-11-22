package com.warnomodmaker.combine.ndf;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A database's .dbg companion: a u32 length then a zstd frame of text with one line per object, in object order
 * ("name;file;line;column", or an empty line for an object without debug info).
 */
public final class NdfDebugInfo {
    private NdfDebugInfo() {
    }

    public static List<byte[]> read(byte[] file) {
        if (file.length < 4) throw new NdfFormatException("Truncated debug info.");
        int size = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN).getInt(0);
        byte[] text = new byte[size];
        if (NdfContainer.zstdDecompress(file, 4, file.length - 4, text, 0, size) != size)
            throw new NdfFormatException("Debug info size mismatch.");
        List<byte[]> lines = new ArrayList<>();
        for (int start = 0, i = 0; i < text.length; i++) {
            if (text[i] == '\n') {
                lines.add(Arrays.copyOfRange(text, start, i));
                start = i + 1;
            }
        }
        return lines;
    }

    public static byte[] write(Iterable<byte[]> lines) {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        for (byte[] line : lines) {
            text.write(line, 0, line.length);
            text.write('\n');
        }
        byte[] raw = text.toByteArray();
        byte[] packed = NdfContainer.zstdCompress(raw, raw.length);
        byte[] file = new byte[4 + packed.length];
        ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN).putInt(0, raw.length);
        System.arraycopy(packed, 0, file, 4, packed.length);
        return file;
    }
}
