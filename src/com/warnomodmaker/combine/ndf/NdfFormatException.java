package com.warnomodmaker.combine.ndf;

/** A compiled database (or one of its companions) that can't be read, written or merged. */
public final class NdfFormatException extends RuntimeException {
    public NdfFormatException(String message) {
        super(message);
    }
}
