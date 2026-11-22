package com.warnomodmaker.combine.ndf;

import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdDecompressor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** EUG0/CNDF container: a 0x28-byte header followed by an LZ4 (1) or zstd (2) compressed body. */
public final class NdfContainer {
    public static final int HEADER_SIZE = 0x28;
    private static final int LZ4 = 1, ZSTD = 2;
    private static final byte[] EUG0 = "EUG0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CNDF = "CNDF".getBytes(StandardCharsets.US_ASCII);

    private NdfContainer() {
    }

    /** The logical file (header then decompressed body) and where its table of contents starts. */
    public static final class Decompressed {
        public final byte[] data;
        public final long tocOffset;

        Decompressed(byte[] data, long tocOffset) {
            this.data = data;
            this.tocOffset = tocOffset;
        }
    }

    public static boolean isNdfBinary(byte[] data) {
        return isNdfBinary(data, data.length);
    }

    public static boolean isNdfBinary(byte[] data, int length) {
        return length >= 0x2C && startsWith(data, 0, EUG0) && startsWith(data, 8, CNDF);
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) if (data[offset + i] != prefix[i]) return false;
        return true;
    }

    public static Decompressed decompress(byte[] file) {
        if (!isNdfBinary(file)) throw new NdfFormatException("Not an EUG0/CNDF database.");
        ByteBuffer header = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        int flags = header.getInt(12);
        long tocOffset = header.getLong(16);
        int bodySize = header.getInt(0x28);
        byte[] data = new byte[HEADER_SIZE + bodySize];
        System.arraycopy(file, 0, data, 0, HEADER_SIZE);
        int written;
        if (flags == LZ4) {
            written = Lz4.decode(file, 0x2C, file.length - 0x2C, data, HEADER_SIZE);
        } else if (flags == ZSTD) {
            written = zstdDecompress(file, 0x2C, file.length - 0x2C, data, HEADER_SIZE, bodySize);
        } else {
            throw new NdfFormatException("Unsupported NDF compression " + flags + ".");
        }
        if (written != bodySize) throw new NdfFormatException("NDF body size mismatch.");
        return new Decompressed(data, tocOffset);
    }

    /** Wraps a logical body (everything after the header) as a zstd-compressed container. */
    public static byte[] compress(byte[] body, int bodyLength, long tocOffset) {
        byte[] packed = zstdCompress(body, bodyLength);
        byte[] file = new byte[0x2C + packed.length];
        ByteBuffer out = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        out.put(0, EUG0[0]).put(1, EUG0[1]).put(2, EUG0[2]).put(3, EUG0[3]);
        out.putInt(4, 1);
        for (int i = 0; i < 4; i++) out.put(8 + i, CNDF[i]);
        out.putInt(12, ZSTD);
        out.putLong(16, tocOffset);
        out.putLong(24, HEADER_SIZE);
        out.putLong(32, (long) HEADER_SIZE + bodyLength);
        out.putInt(0x28, bodyLength);
        System.arraycopy(packed, 0, file, 0x2C, packed.length);
        return file;
    }

    static int zstdDecompress(byte[] input, int offset, int length, byte[] output, int outputOffset, int maxLength) {
        try {
            return new ZstdDecompressor().decompress(input, offset, length, output, outputOffset, maxLength);
        } catch (RuntimeException e) {
            throw new NdfFormatException("Corrupt zstd data: " + e.getMessage());
        }
    }

    static byte[] zstdCompress(byte[] input, int length) {
        ZstdCompressor compressor = new ZstdCompressor();
        byte[] output = new byte[compressor.maxCompressedLength(length)];
        int size = compressor.compress(input, 0, length, output, 0, output.length);
        return Arrays.copyOf(output, size);
    }

    /** First index of {@code needle} in {@code data}, or -1. */
    public static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    static final class Lz4 {
        private Lz4() {
        }

        static int decode(byte[] src, int srcOffset, int srcLength, byte[] dst, int dstOffset) {
            int i = srcOffset, end = srcOffset + srcLength, o = dstOffset;
            while (i < end) {
                int token = src[i++] & 0xFF;
                int literals = token >>> 4;
                if (literals == 15) {
                    int b;
                    do { b = src[i++] & 0xFF; literals += b; } while (b == 255);
                }
                System.arraycopy(src, i, dst, o, literals);
                i += literals;
                o += literals;
                if (i >= end) break;
                int offset = (src[i] & 0xFF) | ((src[i + 1] & 0xFF) << 8);
                i += 2;
                int length = token & 15;
                if (length == 15) {
                    int b;
                    do { b = src[i++] & 0xFF; length += b; } while (b == 255);
                }
                length += 4;
                if (offset == 0 || offset > o - dstOffset) throw new NdfFormatException("Corrupt LZ4 block.");
                for (int k = 0; k < length; k++, o++) dst[o] = dst[o - offset];
            }
            return o - dstOffset;
        }
    }
}
