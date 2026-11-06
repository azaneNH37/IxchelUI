package com.azane.ixchel.core.playtest;

/**
 * The loader's clock for a playtest run: it advances the run once per client tick, at the end of a
 * tick that follows a drawn frame, as a user acts between frames. Documents lay out when they are
 * drawn, and ticks catch up without frames after a world loads, so a step never sees a layout older
 * than its previous step's input. A recording run also needs every drawn frame, to capture it and to
 * move the pointer frame by frame.
 */
public interface Pump {
    /** Calls {@code tick} on the client thread from now on, at most once per tick, only after a frame was drawn since the last call. */
    void start(Runnable tick);

    /**
     * Calls {@code frame} on the client thread from now on, once per frame, when the frame is fully
     * drawn (world, HUD, screen and toasts) and not yet shown. For a recording run only.
     */
    void frames(Runnable frame);
}
