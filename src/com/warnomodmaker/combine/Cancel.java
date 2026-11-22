package com.warnomodmaker.combine;

import java.util.concurrent.CancellationException;

/** A cancellation flag shared between the window and a background operation. */
public final class Cancel {
    /** A token that is never cancelled. */
    public static final Cancel NONE = new Cancel();

    private volatile boolean cancelled;

    public void cancel() {
        if (this != NONE) cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Throws {@link CancellationException} once cancelled. */
    public void check() {
        if (cancelled) throw new CancellationException();
    }
}
