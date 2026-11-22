package com.warnomodmaker.combine.ndf;

/** Upper-case hexadecimal, as WARNO's tools and the combined-mod records use. */
public final class Hex {
    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    private Hex() {
    }

    public static String upper(byte[] data) {
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            out[2 * i] = DIGITS[(data[i] >> 4) & 0xF];
            out[2 * i + 1] = DIGITS[data[i] & 0xF];
        }
        return new String(out);
    }

    public static boolean isHex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) return false;
        }
        return true;
    }
}
