package com.warnomodmaker.combine.ndf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Eugen "edat" archive (Data/PC/&lt;version&gt;/*.dat) with its prefix-trie dictionary. */
public final class EdatArchive {
    public static final class Entry {
        public final String path;
        public final long offset;
        public final long size;

        Entry(String path, long offset, long size) {
            this.path = path;
            this.offset = offset;
            this.size = size;
        }
    }

    private final Path path;
    private final Map<String, Entry> entries;

    public EdatArchive(Path path) {
        this.path = path;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer head = readFully(channel, 0, 0x28);
            if (head.get(0) != 'e' || head.get(1) != 'd' || head.get(2) != 'a' || head.get(3) != 't')
                throw new NdfFormatException(path + " is not an edat archive.");
            long dictOffset = head.getInt(8) & 0xFFFFFFFFL;
            int dictLength = head.getInt(12);
            long filesOffset = head.getLong(16);
            byte[] dictionary = new byte[dictLength];
            readFully(channel, dictOffset, dictLength).get(dictionary);
            TreeMap<String, Entry> found = IgnoreCase.map();
            walk(dictionary, 0, dictLength, "", filesOffset, found);
            entries = Collections.unmodifiableMap(found);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    // Record: u32 headerLength (0 = file), u32 span to next sibling (0 = last), then a directory name
    // or a file's u64 offset, u64 size, 16-byte checksum and name. Names are prefixes of their children.
    private static void walk(byte[] d, int p, int end, String prefix, long filesOffset, Map<String, Entry> entries) {
        ByteBuffer buffer = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        while (p < end) {
            int group = buffer.getInt(p);
            int span = buffer.getInt(p + 4);
            int next = span != 0 ? p + span : end;
            if (group == 0) {
                long offset = buffer.getLong(p + 8);
                long size = buffer.getLong(p + 16);
                String full = prefix + cString(d, p + 40);
                entries.put(full, new Entry(full, filesOffset + offset, size));
            } else {
                int nameEnd = indexOfZero(d, p + 8);
                String name = new String(d, p + 8, nameEnd - (p + 8), StandardCharsets.ISO_8859_1);
                walk(d, nameEnd + 1, next, prefix + name, filesOffset, entries);
            }
            p = next;
        }
    }

    private static int indexOfZero(byte[] d, int from) {
        for (int i = from; i < d.length; i++) if (d[i] == 0) return i;
        throw new NdfFormatException("Unterminated name in an edat dictionary.");
    }

    private static String cString(byte[] d, int p) {
        return new String(d, p, indexOfZero(d, p) - p, StandardCharsets.ISO_8859_1);
    }

    private static ByteBuffer readFully(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position + buffer.position());
            if (read < 0) throw new NdfFormatException(length + " bytes past the end of the archive were requested.");
        }
        buffer.flip();
        return buffer;
    }

    public byte[] read(Entry entry) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            byte[] data = new byte[(int) entry.size];
            readFully(channel, entry.offset, data.length).get(data);
            return data;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
