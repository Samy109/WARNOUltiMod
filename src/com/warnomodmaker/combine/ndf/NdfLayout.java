package com.warnomodmaker.combine.ndf;

/**
 * Where compiled databases live in a mod's Gen\NDF folder. WARNO's November 2026 update kept ModGen 210942 but
 * renamed every database from {@code <Db>.ndfbin} to {@code <Db>.txn} (same EUG0/CNDF container), and moved the
 * per-name ones into folders: {@code Localisation\<Name>\DicoDef.txn} and
 * {@code ResourcePacks\<Name>\ResourcePackDefinition.txn}. Mods published before it still ship the old names.
 */
public final class NdfLayout {
    private static final String ROOT = "Gen\\NDF\\";
    private static final String LEGACY = ".ndfbin";
    private static final String TRANSACTION = ".txn";
    private static final String[][] NAMED_FOLDERS = {{"Localisation", "DicoDef"}, {"ResourcePacks", "ResourcePackDefinition"}};

    private NdfLayout() {
    }

    private static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean endsWithIgnoreCase(String text, String suffix) {
        return text.length() >= suffix.length() && text.regionMatches(true, text.length() - suffix.length(), suffix, 0, suffix.length());
    }

    public static boolean isLegacyDatabase(String relativePath) {
        return startsWithIgnoreCase(relativePath, ROOT) && endsWithIgnoreCase(relativePath, LEGACY);
    }

    public static boolean isTransactionDatabase(String relativePath) {
        return startsWithIgnoreCase(relativePath, ROOT) && endsWithIgnoreCase(relativePath, TRANSACTION);
    }

    public static boolean isDatabase(String relativePath) {
        return isLegacyDatabase(relativePath) || isTransactionDatabase(relativePath);
    }

    /** Gen\NDF\Localisation\X.ndfbin → Gen\NDF\Localisation\X\DicoDef.txn; Gen\NDF\GFX\Sound.ndfbin → Gen\NDF\GFX\Sound.txn. */
    public static String toTransactionPath(String legacyPath) {
        String stem = legacyPath.substring(0, legacyPath.length() - LEGACY.length());
        String[] parts = stem.substring(ROOT.length()).split("\\\\", -1);
        if (parts.length == 2) {
            for (String[] named : NAMED_FOLDERS) {
                if (parts[0].equalsIgnoreCase(named[0])) return stem + "\\" + named[1] + TRANSACTION;
            }
        }
        return stem + TRANSACTION;
    }

    /** The inverse of {@link #toTransactionPath}. */
    public static String toLegacyPath(String transactionPath) {
        String stem = transactionPath.substring(0, transactionPath.length() - TRANSACTION.length());
        String[] parts = stem.substring(ROOT.length()).split("\\\\", -1);
        if (parts.length == 3) {
            for (String[] named : NAMED_FOLDERS) {
                if (parts[0].equalsIgnoreCase(named[0]) && parts[2].equalsIgnoreCase(named[1]))
                    return ROOT + parts[0] + "\\" + parts[1] + LEGACY;
            }
        }
        return stem + LEGACY;
    }

    /** The .dbg listing a .txn database's objects: Gen\NDF\GFX\Unit.txn → Gen\NDF\GFX\Unit.dbg. */
    public static String debugInfoPath(String transactionPath) {
        return transactionPath.substring(0, transactionPath.length() - TRANSACTION.length()) + ".dbg";
    }

    /** The Config.ini [Config] key of a database in either layout, such as GFX/Pawn or Localisation/X. */
    public static String manifestKey(String relativePath) {
        String legacy = isTransactionDatabase(relativePath) ? toLegacyPath(relativePath) : relativePath;
        return legacy.substring(ROOT.length(), legacy.length() - LEGACY.length()).replace('\\', '/');
    }
}
