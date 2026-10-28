package com.azane.ixchel.sample.check;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Arrays;

/**
 * CPU time per frame of one HUD layer (overlay Pre to Post) or of the open screen (screen render Pre
 * to Post), submission included, GPU not; for checks that hold a document to a frame budget. Client
 * thread only.
 */
public final class FrameTimer {
    private static ResourceLocation overlay;
    private static boolean screen;
    private static long start;
    private static long[] samples = new long[0];
    private static int count;
    private static boolean listening;

    private FrameTimer() {
    }

    /** Starts timing the HUD layer {@code id}, dropping earlier samples; keeps at most {@code frames}. */
    public static void overlay(ResourceLocation id, int frames) {
        begin(frames);
        overlay = id;
    }

    /** Starts timing whatever screen is open, dropping earlier samples; keeps at most {@code frames}. */
    public static void screen(int frames) {
        begin(frames);
        screen = true;
    }

    /** Stops timing; the frames timed since the start, in nanoseconds, sorted. */
    static long[] stop() {
        overlay = null;
        screen = false;
        long[] out = Arrays.copyOf(samples, count);
        Arrays.sort(out);
        return out;
    }

    /** Stops timing and reports median and p95; fails when the p95 is over {@code budgetUs}. */
    public static void report(Script s, String what, double budgetUs) {
        long[] t = stop();
        double median = t.length == 0 ? 0 : t[t.length / 2] / 1000.0;
        double p95 = t.length == 0 ? Double.MAX_VALUE : t[t.length * 95 / 100] / 1000.0;
        s.report(String.format("frame time %s: median %.1f us, p95 %.1f us over %d frames (budget p95 %.0f us)",
                what, median, p95, t.length, budgetUs), p95 <= budgetUs);
    }

    private static void begin(int frames) {
        if (!listening) {
            listening = true;
            // Lowest / highest priority so the timed span excludes other listeners of the same event.
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderGuiOverlayEvent.Pre e) -> {
                if (e.getOverlay().id().equals(overlay)) {
                    start = System.nanoTime();
                }
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (RenderGuiOverlayEvent.Post e) -> {
                if (e.getOverlay().id().equals(overlay)) {
                    record();
                }
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Pre e) -> {
                if (screen) {
                    start = System.nanoTime();
                }
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.Render.Post e) -> {
                if (screen) {
                    record();
                }
            });
        }
        overlay = null;
        screen = false;
        samples = new long[frames];
        count = 0;
    }

    private static void record() {
        if (count < samples.length) {
            samples[count++] = System.nanoTime() - start;
        }
    }
}
