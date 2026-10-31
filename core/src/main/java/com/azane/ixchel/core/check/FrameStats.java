package com.azane.ixchel.core.check;

import java.util.Arrays;

/** Holds per-frame times to a budget: median and 95th percentile of the samples. */
public final class FrameStats {
    private FrameStats() {
    }

    /**
     * Records on {@code driver} the median and p95 of {@code nanos} (frame times, in nanoseconds);
     * passes when the p95 is at most {@code budgetUs} microseconds. No samples fail.
     */
    public static void report(Driver<?> driver, String what, long[] nanos, double budgetUs) {
        long[] t = nanos.clone();
        Arrays.sort(t);
        double median = t.length == 0 ? 0 : t[t.length / 2] / 1000.0;
        double p95 = t.length == 0 ? Double.MAX_VALUE : t[t.length * 95 / 100] / 1000.0;
        driver.report(String.format("frame time %s: median %.1f us, p95 %.1f us over %d frames (budget p95 %.0f us)",
                what, median, p95, t.length, budgetUs), p95 <= budgetUs);
    }
}
