package com.warnomodmaker.combine.ndf;

/**
 * WARNO's localisation tokens (NDF type 0x1D): up to ten characters packed six bits each, first character in the
 * highest bits of a little-endian 64-bit value. Character 0 pads short tokens.
 */
public final class LocalisationToken {
    private static final String CHARACTERS = "\0" + "0123456789" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "_" + "abcdefghijklmnopqrstuvwxyz";

    private LocalisationToken() {
    }

    /** The token text of a raw value as {@link NdfValue#describe()} prints it ("0x1D:2A8EAB2D00000000"). */
    public static String decode(NdfValue value) {
        String hex = value.describe();
        int colon = hex.indexOf(':');
        if (!hex.regionMatches(true, 0, "0x1D", 0, 4) || colon < 0 || hex.length() - colon - 1 != 16)
            throw new NdfFormatException("Not a localisation token: " + hex);
        hex = hex.substring(colon + 1);
        long packed = 0;
        for (int i = 7; i >= 0; i--) packed = (packed << 8) | Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            int c = (int) ((packed >>> (54 - 6 * i)) & 63);
            if (c != 0) text.append(CHARACTERS.charAt(c));
        }
        return text.toString();
    }

    /** True when the text can be a token: one to ten characters from the token alphabet. */
    public static boolean valid(String text) {
        if (text == null || text.isEmpty() || text.length() > 10) return false;
        for (char c : text.toCharArray()) if (c == 0 || CHARACTERS.indexOf(c) < 0) return false;
        return true;
    }
}
