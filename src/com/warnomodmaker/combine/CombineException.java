package com.warnomodmaker.combine;

/** A combine, check or rebuild that can't go ahead; the message is shown to the user as it is. */
public final class CombineException extends RuntimeException {
    public CombineException(String message) {
        super(message);
    }

    public CombineException(String message, Throwable cause) {
        super(message, cause);
    }
}
