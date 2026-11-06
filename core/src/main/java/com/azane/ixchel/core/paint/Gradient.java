package com.azane.ixchel.core.paint;

/**
 * A color ramp over the plane: {@code colors[i]} sits at ramp position {@code offsets[i]}, offsets
 * ascending; before the first and after the last the end colors extend. Where the plane is on the ramp
 * depends on the {@link Kind}:
 * <ul>
 *   <li>{@link Kind#LINEAR}: 0 at {@code (x0, y0)}, 1 at {@code (x1, y1)}, constant across that line;</li>
 *   <li>{@link Kind#RADIAL}: 0 at the center {@code (x0, y0)}, 1 on the ellipse of radii {@code x1}
 *   across and {@code y1} down;</li>
 *   <li>{@link Kind#CONIC}: 0 to 1 clockwise around the center {@code (x0, y0)}, starting {@code x1}
 *   radians clockwise from up; {@code y1} is unused.</li>
 * </ul>
 * Between stops the color changes linearly in non-premultiplied ARGB, as vertex colors do, so the
 * painter inserts stops wherever premultiplied interpolation would look different. Arrays are not
 * copied: treat them as immutable.
 */
public record Gradient(Kind kind, float x0, float y0, float x1, float y1, float[] offsets, int[] colors) {
    public enum Kind { LINEAR, RADIAL, CONIC }

    private static final double TURN = 2 * Math.PI;

    /** A linear ramp from {@code (x0, y0)} to {@code (x1, y1)}. */
    public static Gradient linear(float x0, float y0, float x1, float y1, float[] offsets, int[] colors) {
        return new Gradient(Kind.LINEAR, x0, y0, x1, y1, offsets, colors);
    }

    /** Where the ramp is at {@code (x, y)}, unclamped; a conic one is in {@code [0, 1)}. */
    public float at(float x, float y) {
        switch (kind) {
            case LINEAR -> {
                float dx = x1 - x0;
                float dy = y1 - y0;
                float len2 = dx * dx + dy * dy;
                return len2 == 0 ? 0 : ((x - x0) * dx + (y - y0) * dy) / len2;
            }
            case RADIAL -> {
                float u = (x - x0) / x1;
                float v = (y - y0) / y1;
                return (float) Math.sqrt(u * u + v * v);
            }
            default -> {
                // Clockwise from up: atan2 of x against -y.
                double a = Math.atan2(x - x0, y0 - y) - x1;
                double t = a / TURN;
                return (float) (t - Math.floor(t));
            }
        }
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
        int lo = 0;
        int hi = n - 1;
        // The first offset not below t, by bisection: a repeating ramp has many stops.
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (offsets[mid] < t) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        int i = lo;
        float span = offsets[i] - offsets[i - 1];
        float p = span <= 0 ? 1 : (t - offsets[i - 1]) / span;
        return lerp(colors[i - 1], colors[i], p);
    }

    /**
     * The color at {@code t} held to the span from stop {@code a} to stop {@code a + 1}; before the
     * first stop ({@code a < 0}) and after the last the end colors extend. A shape cut at the stops
     * colors each piece by its own span, so a vertex on a hard stop takes its piece's side of it.
     */
    public int color(float t, int a) {
        int n = offsets.length;
        if (a < 0) {
            return colors[0];
        }
        if (a >= n - 1) {
            return colors[n - 1];
        }
        float span = offsets[a + 1] - offsets[a];
        float p = span <= 0 ? 1 : Math.max(0, Math.min(1, (t - offsets[a]) / span));
        return lerp(colors[a], colors[a + 1], p);
    }

    /** The last stop at or before {@code t}, {@code -1} when {@code t} is before the first. */
    public int span(float t) {
        int lo = 0;
        int hi = offsets.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (offsets[mid] <= t) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo - 1;
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
