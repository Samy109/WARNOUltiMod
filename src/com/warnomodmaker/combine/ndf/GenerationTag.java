package com.warnomodmaker.combine.ndf;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * A generated file's .tag (TNDFTagFile): the build configuration hash and, for every source file the file was built
 * from, the first 8 bytes of that source's MD5. When WARNO loads a mod it compares those hashes with the sources the
 * mod ships; any difference makes it treat the file as out of date and try to rebuild it, which a released mod can't
 * do ("No writable component found: 'ZZ:/NDF/Scenario/GFX/Skirmish.txn'"). A combined mod takes its sources from
 * several mods, so every tag is re-stamped with the hashes of the sources the combined mod actually contains.
 */
public final class GenerationTag {
    private static final String TAG_CLASS = "TNDFTagFile";
    private static final String HASHES_FIELD = "SourceFileHashes";
    private static final int HASH_TYPE = 0x13;

    private GenerationTag() {
    }

    /** True for a tag that lists source hashes (other .tag files, such as pack tags, are left as they are). */
    public static boolean hasSourceHashes(byte[] file) {
        if (!NdfContainer.isNdfBinary(file)) return false;
        NdfDatabase database = NdfDatabase.load(file);
        return database.topLevel.size() == 1 && database.topLevel.get(0).value.className.equals(TAG_CLASS)
            && database.topLevel.get(0).value.get(HASHES_FIELD) instanceof NdfValue.MapV;
    }

    /** The tag's source files, such as "GameData:/Gameplay/Skirmish/IASkirmishDescriptors.ndf". */
    public static List<String> sources(byte[] tag) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<NdfValue, NdfValue> e : hashMap(NdfDatabase.load(tag)).entries) {
            if (!(e.getKey() instanceof NdfValue.Text)) throw new NdfFormatException("A tag source is not a path.");
            result.add(((NdfValue.Text) e.getKey()).text);
        }
        return result;
    }

    /** Each source's hash as a tag records it, keyed by source path. */
    public static Map<String, byte[]> hashes(byte[] tag) {
        TreeMap<String, byte[]> result = IgnoreCase.map();
        for (Map.Entry<NdfValue, NdfValue> e : hashMap(NdfDatabase.load(tag)).entries) {
            if (e.getKey() instanceof NdfValue.Text && e.getValue() instanceof NdfValue.Raw)
                result.putIfAbsent(((NdfValue.Text) e.getKey()).text, ((NdfValue.Raw) e.getValue()).data);
        }
        return result;
    }

    /** The tag with each of its own sources' hash replaced by the hash of {@code sourceFile(source)}. */
    public static byte[] restamp(byte[] tag, Function<String, Path> sourceFile) {
        return restamp(tag, sources(tag), sourceFile, Collections.emptyMap());
    }

    /**
     * The tag listing exactly {@code sources} (in that order), each with the hash of {@code sourceFile(source)}. A
     * combined database depends on every source any combined mod's tag lists for it: a source WARNO finds but the tag
     * doesn't list makes it rebuild the database too.
     *
     * @param sourceFile the file a source is in the mod, or null when the mod doesn't contain it: WARNO then reads
     *                   its own copy, whose hash the tags record ({@code recorded}).
     */
    public static byte[] restamp(byte[] tag, List<String> sources, Function<String, Path> sourceFile, Map<String, byte[]> recorded) {
        NdfDatabase database = NdfDatabase.load(tag);
        NdfDatabase.TopLevel root = database.topLevel.get(0);
        NdfValue.MapV hashes = hashMap(database);
        if (hashes.entries.isEmpty() || !(hashes.entries.get(0).getKey() instanceof NdfValue.Text))
            throw new NdfFormatException("A generation tag without sources can't be extended.");
        int keyType = ((NdfValue.Text) hashes.entries.get(0).getKey()).type;
        TreeMap<String, byte[]> own = IgnoreCase.map();
        for (Map.Entry<NdfValue, NdfValue> e : hashes.entries)
            own.putIfAbsent(((NdfValue.Text) e.getKey()).text, ((NdfValue.Raw) e.getValue()).data);
        List<Map.Entry<NdfValue, NdfValue>> entries = new ArrayList<>(sources.size());
        for (String path : sources) {
            Path file = sourceFile.apply(path);
            byte[] hash;
            if (file != null) {
                hash = Arrays.copyOf(md5(file), 8);
            } else {
                hash = own.get(path);
                if (hash == null) hash = recorded.get(path);
                if (hash == null) throw new NdfFormatException("No hash is known for " + path + ", which the mod doesn't contain.");
            }
            entries.add(NdfValue.entry(new NdfValue.Text(keyType, path), new NdfValue.Raw(HASH_TYPE, hash)));
        }
        List<Map.Entry<String, NdfValue>> fields = new ArrayList<>();
        for (Map.Entry<String, NdfValue> f : root.value.fields)
            fields.add(f.getKey().equals(HASHES_FIELD) ? NdfValue.field(f.getKey(), new NdfValue.MapV(entries)) : f);
        NdfValue.Obj restamped = new NdfValue.Obj(root.value.className, fields, root.value.origin);
        return new NdfDatabase(Collections.singletonList(root.withValue(restamped)), database.propertyOwners).save();
    }

    /** "GameData:/A/B.ndf" → "GameData\A\B.ndf", the path of that source inside a mod folder. */
    public static String modRelativePath(String source) {
        int colon = source.indexOf(":/");
        if (colon <= 0) throw new NdfFormatException("Unexpected tag source path " + source + ".");
        return source.substring(0, colon) + "\\" + source.substring(colon + 2).replace('/', '\\');
    }

    private static NdfValue.MapV hashMap(NdfDatabase database) {
        if (database.topLevel.size() == 1 && database.topLevel.get(0).value.className.equals(TAG_CLASS)
            && database.topLevel.get(0).value.get(HASHES_FIELD) instanceof NdfValue.MapV)
            return (NdfValue.MapV) database.topLevel.get(0).value.get(HASHES_FIELD);
        throw new NdfFormatException("Not a generation tag with source hashes.");
    }

    private static byte[] md5(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[128 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
            return digest.digest();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
