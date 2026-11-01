package com.azane.ixchel.mc;

import com.azane.ixchel.core.playtest.PlaytestDriver;
import com.azane.ixchel.core.playtest.FrameBudget;
import net.minecraft.client.gui.screens.Screen;

import java.util.Arrays;

/**
 * CPU time per frame of one Ixchel host's {@code render}: a {@link UiHud}, or whichever
 * {@link UiScreen} or {@link UiContainerScreen} is open, submission included, GPU not; for playtests
 * that hold a document to a frame budget. Client thread only.
 *
 * <pre>{@code
 * s.run(() -> FrameTimer.screen(4096)).pause(100)
 *  .run(() -> FrameTimer.expect(s, "list standing still", 8000));
 * }</pre>
 */
public final class FrameTimer {
    /** The target while timing screens: any open Ixchel screen. */
    private static final Object SCREENS = new Object();

    private static Object target;
    private static long start;
    private static long[] samples = new long[0];
    private static int count;

    private FrameTimer() {
    }

    /** Starts timing {@code hud}, dropping earlier samples; keeps at most {@code frames}. */
    public static void hud(UiHud hud, int frames) {
        begin(hud, frames);
    }

    /** Starts timing whatever Ixchel screen is open, dropping earlier samples; keeps at most {@code frames}. */
    public static void screen(int frames) {
        begin(SCREENS, frames);
    }

    /** Stops timing and asserts the frames timed since the start against {@code budgetUs} ({@link FrameBudget}). */
    public static void expect(PlaytestDriver<?> driver, String what, double budgetUs) {
        target = null;
        FrameBudget.expect(driver, what, Arrays.copyOf(samples, count), budgetUs);
    }

    private static void begin(Object timed, int frames) {
        target = timed;
        samples = new long[frames];
        count = 0;
    }

    /** At the start of {@code host}'s render. */
    static void start(Object host) {
        if (timed(host)) {
            start = System.nanoTime();
        }
    }

    /** At the end of {@code host}'s render. */
    static void stop(Object host) {
        if (timed(host) && count < samples.length) {
            samples[count++] = System.nanoTime() - start;
        }
    }

    private static boolean timed(Object host) {
        return target != null && (target == host || target == SCREENS && host instanceof Screen);
    }
}
