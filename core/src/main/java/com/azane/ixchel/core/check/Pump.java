package com.azane.ixchel.core.check;

/**
 * The loader's clock for a check run: it advances the run once per client tick, at the end of a
 * tick that follows a drawn frame, as a user acts between frames. Documents lay out when they are
 * drawn, and ticks catch up without frames after a world loads, so a step never sees a layout older
 * than its previous step's input.
 */
public interface Pump {
    /** Calls {@code frame} on the client thread from now on, at most once per tick, only after a frame was drawn since the last call. */
    void start(Runnable frame);
}
