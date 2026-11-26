package com.warnomodmaker.combine.ndf;

import io.airlift.compress.zstd.ZstdDecompressor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The unit pictures WARNO shows on unit cards. The game packs them into texture atlases: Data\PC\&lt;revision&gt;\ZZ_n.dat
 * holds PC/Atlas/UnitIcons-&lt;Side&gt;/TextureSmall.atlas, a compiled NDF list of {@code TTextureSmall} entries (the
 * icon's source path, its atlas page and UV rectangle), and the pages themselves as .tgv textures (A8B8G8R8 pixels in
 * one zstd-compressed mip). A unit names its picture with {@code ButtonTexture = 'Texture_Button_Unit_X'}, whose
 * source is GameData:/Assets/2D/Interface/Common/UnitsIcons/&lt;Side&gt;/X.png.
 */
public final class UnitPictures {
    private static final Pattern REVISION = Pattern.compile("\\d{1,9}");

    private static final class Icon {
        final EdatArchive archive;
        final EdatArchive.Entry page;
        final float minU, minV, maxU, maxV;

        Icon(EdatArchive archive, EdatArchive.Entry page, float minU, float minV, float maxU, float maxV) {
            this.archive = archive;
            this.page = page;
            this.minU = minU;
            this.minV = minV;
            this.maxU = maxU;
            this.maxV = maxV;
        }
    }

    private static final class Page {
        final int width, height;
        final byte[] pixels;

        Page(int width, int height, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    private final TreeMap<String, Icon> icons = IgnoreCase.map();
    // Decoded pages are 32-64 MB each; keep the two most recent.
    private final Map<String, Page> pages = new LinkedHashMap<String, Page>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Page> eldest) {
            return size() > 2;
        }
    };
    private final Map<String, BufferedImage> pictures = new LinkedHashMap<String, BufferedImage>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 400;
        }
    };

    private UnitPictures() {
    }

    /** Indexes the newest unit icon atlases of a WARNO installation; empty when none are found. */
    public static UnitPictures load(Path warnoRoot) {
        UnitPictures result = new UnitPictures();
        Path pc = warnoRoot.resolve("Data").resolve("PC");
        if (!Files.isDirectory(pc)) return result;
        // Revision folders oldest first; a nested folder (Data\PC\201602\210942) is the later revision.
        List<Path> folders = new ArrayList<>();
        for (File top : listDirs(pc.toFile())) {
            folders.add(top.toPath());
            for (File nested : listDirs(top)) folders.add(nested.toPath());
        }
        folders.removeIf(f -> !REVISION.matcher(f.getFileName().toString()).matches());
        folders.sort(Comparator.comparingLong((Path f) -> Long.parseLong(f.getFileName().toString())));
        // Each atlas family (UnitIcons-NATO, -PACT, ...) comes from the newest folder that has its index.
        TreeMap<String, Path> newestFolder = IgnoreCase.map();
        TreeMap<String, List<EdatArchive>> archives = new TreeMap<>();
        for (Path folder : folders) {
            List<EdatArchive> zz = new ArrayList<>();
            for (File file : listFiles(folder.toFile())) {
                if (!file.getName().matches("(?i)ZZ_\\d+\\.dat")) continue;
                try {
                    zz.add(new EdatArchive(file.toPath()));
                } catch (RuntimeException ignored) {
                }
            }
            archives.put(folder.toString(), zz);
            for (EdatArchive archive : zz) {
                for (String path : archive.entries().keySet()) {
                    if (path.regionMatches(true, 0, "PC/Atlas/UnitIcons-", 0, 19) && path.toLowerCase().endsWith("/texturesmall.atlas"))
                        newestFolder.put(path, folder);
                }
            }
        }
        for (Map.Entry<String, Path> family : newestFolder.entrySet()) {
            List<EdatArchive> zz = archives.get(family.getValue().toString());
            EdatArchive indexArchive = null;
            for (EdatArchive a : zz) if (a.entries().containsKey(family.getKey())) indexArchive = a;
            if (indexArchive == null) continue;
            try {
                NdfDatabase index = NdfDatabase.load(indexArchive.read(indexArchive.entries().get(family.getKey())));
                for (NdfDatabase.TopLevel top : index.topLevel) {
                    NdfValue.Obj o = top.value;
                    if (!o.className.equals("TTextureSmall")) continue;
                    String source = ((NdfValue.Text) o.get("TexturePartFileName")).text;
                    String container = ((NdfValue.Text) ((NdfValue.Obj) o.get("Container")).get("TextureFileName")).text;
                    String pagePath = container.replaceFirst("^ZZ:/", "").replaceFirst("(?i)\\.png$", ".tgv");
                    EdatArchive pageArchive = null;
                    for (EdatArchive a : zz) if (a.entries().containsKey(pagePath)) pageArchive = a;
                    if (pageArchive == null) continue;
                    float[] min = float2(o.get("MinUV")), max = float2(o.get("MaxUV"));
                    String name = source.substring(source.lastIndexOf('/') + 1).replaceFirst("(?i)\\.png$", "");
                    result.icons.put(name, new Icon(pageArchive, pageArchive.entries().get(pagePath), min[0], min[1], max[0], max[1]));
                }
            } catch (RuntimeException ignored) {
                // An index this reader doesn't understand only means fewer pictures.
            }
        }
        return result;
    }

    private static File[] listDirs(File folder) {
        File[] children = folder.listFiles(File::isDirectory);
        return children == null ? new File[0] : children;
    }

    private static File[] listFiles(File folder) {
        File[] children = folder.listFiles(File::isFile);
        return children == null ? new File[0] : children;
    }

    private static float[] float2(NdfValue value) {
        if (value == null) return new float[]{0, 0};
        String hex = value.describe();
        hex = hex.substring(hex.indexOf(':') + 1);
        ByteBuffer buffer = ByteBuffer.wrap(bytes(hex)).order(ByteOrder.LITTLE_ENDIAN);
        return new float[]{buffer.getFloat(0), buffer.getFloat(4)};
    }

    private static byte[] bytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) data[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        return data;
    }

    public int size() {
        return icons.size();
    }

    /** Every icon name the atlases hold, such as M1A1_Abrams_US. */
    public List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(icons.keySet()));
    }

    /**
     * The picture for a ButtonTexture token ('Texture_Button_Unit_M1A1_Abrams_US'), an icon name (M1A1_Abrams_US) or a
     * unit descriptor name (Descriptor_Unit_M1A1_Abrams_US); null when WARNO has none.
     */
    public synchronized BufferedImage picture(String key) {
        if (key == null) return null;
        String name = key.replace("'", "").replace("\"", "").trim()
            .replaceFirst("^(?i)Texture_Button_Unit_", "").replaceFirst("^(?i)Texture_Button_", "")
            .replaceFirst("^(?i)Descriptor_Unit_", "");
        Icon icon = icons.get(name);
        if (icon == null) return null;
        BufferedImage cached = pictures.get(name);
        if (cached != null) return cached;
        Page page = page(icon);
        int x0 = clamp(Math.round(icon.minU * page.width), page.width), y0 = clamp(Math.round(icon.minV * page.height), page.height);
        int x1 = clamp(Math.round(icon.maxU * page.width), page.width), y1 = clamp(Math.round(icon.maxV * page.height), page.height);
        if (x1 <= x0 || y1 <= y0) return null;
        BufferedImage image = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_ARGB);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int p = (y * page.width + x) * 4;
                int r = page.pixels[p] & 0xFF, g = page.pixels[p + 1] & 0xFF, b = page.pixels[p + 2] & 0xFF, a = page.pixels[p + 3] & 0xFF;
                image.setRGB(x - x0, y - y0, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        pictures.put(name, image);
        return image;
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private Page page(Icon icon) {
        String key = icon.page.path + "|" + icon.page.offset;
        Page page = pages.get(key);
        if (page == null) {
            page = decode(icon.archive.read(icon.page));
            pages.put(key, page);
        }
        return page;
    }

    /** A .tgv texture's first mip: u32 version, u32 flags, u32 width, u32 height, ..., mip offsets and sizes at 0x38. */
    static Page decode(byte[] tgv) {
        ByteBuffer header = ByteBuffer.wrap(tgv).order(ByteOrder.LITTLE_ENDIAN);
        int width = header.getInt(8), height = header.getInt(12);
        int mips = header.getShort(0x18) & 0xFFFF;
        int formatLength = header.getShort(0x1A) & 0xFFFF;
        String format = new String(tgv, 0x1C, formatLength, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!format.startsWith("A8B8G8R8")) throw new NdfFormatException("Unsupported texture format " + format + ".");
        int table = 0x38 + (mips > 1 ? 0 : 0);
        int offset = header.getInt(table);
        int size = header.getInt(table + 4 * mips);
        if (tgv[offset] != 'Z' || tgv[offset + 1] != 'S' || tgv[offset + 2] != 'T' || tgv[offset + 3] != 'D')
            throw new NdfFormatException("Texture mip is not zstd-compressed.");
        int raw = header.getInt(offset + 4);
        if (raw != width * height * 4) throw new NdfFormatException("Unexpected texture size.");
        byte[] pixels = new byte[raw];
        new ZstdDecompressor().decompress(tgv, offset + 8, size - 8, pixels, 0, raw);
        return new Page(width, height, pixels);
    }
}
