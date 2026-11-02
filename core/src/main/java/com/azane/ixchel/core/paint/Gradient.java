package com.azane.ixchel.core.paint;

/**
 * A linear color ramp from {@code (x0, y0)} to {@code (x1, y1)}: {@code colors[i]} sits at fraction
 * {@code offsets[i]} of the way, offsets ascending; before the first and after the last the end colors
 * extend. Between stops the color changes linearly in non-premultiplied ARGB, as vertex colors do, so
 * the painter inserts stops wherever premultiplied interpolation would look different. Arrays are not
 * copied: treat them as immutable.
 */
public record Gradient(float x0, float y0, float x1, float y1, float[] offsets, int[] colors) {

    /** Where the ramp is at {@code (x, y)}: 0 at the start point, 1 at the end point, unclamped. */
    public float at(float x, float y) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        return len2 == 0 ? 0 : ((x - x0) * dx + (y - y0) * dy) / len2;
    }

    /** The color at ramp position {@code t}. */
    public int color(float t) {
        int n = offsets.length;
        if (t <= offsets[0]) {
            return colors[0];
        }
        if (t >= offsets[n - 1]) {
            return colors[n - 1];
        }
        int i = 1;
        while (offsets[i] < t) {
            i++;
        }
        float span = offsets[i] - offsets[i - 1];
        float p = span <= 0 ? 1 : (t - offsets[i - 1]) / span;
        return lerp(colors[i - 1], colors[i], p);
    }

    static int lerp(int a, int b, float p) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            float ca = a >>> shift & 0xFF;
            float cb = b >>> shift & 0xFF;
            out |= Math.round(ca + (cb - ca) * p) << shift;
        }
        return out;
    }
}
