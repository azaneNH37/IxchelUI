package com.azane.ixchel.core.playtest;

/**
 * The host's recorder in a recording run, which a {@link PlaytestDriver} opens and closes takes on
 * ({@link PlaytestDriver#take}). While a take is open the host captures every drawn frame as a video
 * frame and steps the game's time by exactly one video frame per frame, however long the frame took;
 * outside takes the game's time runs with the wall clock. Client thread only.
 */
public interface Recording {
    /** The game's time in nanoseconds: stepped one video frame per drawn frame inside a take. */
    long nanos();

    /** The window's height in GUI pixels, which pointer moves are paced against. */
    float viewportHeight();

    /**
     * Opens the take {@code name}: the frames drawn from now on are its video.
     *
     * @param region the part of the window it shows, {@code {x, y, width, height}} in GUI pixels, or
     *               {@code null} for the whole window
     * @param gif    whether a GIF is made from the video as well
     * @throws IllegalStateException when a take is open, or its video cannot be started
     */
    void beginTake(String name, float[] region, boolean gif);

    /**
     * Closes the open take and finishes its files; does nothing when none is open.
     *
     * @throws IllegalStateException when the take's frames could not be captured or encoded
     */
    void endTake();

    /**
     * A press of mouse {@code button} at {@code (x, y)} in GUI pixels that the driver gave the
     * document directly, past the {@link InputPort}, so that the take still shows it.
     */
    void pressed(double x, double y, int button);
}
