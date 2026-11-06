package com.azane.ixchel.core.playtest;

import java.util.Random;

/**
 * One pointer move of a take, in GUI pixels: how long it lasts and where the pointer is on the way.
 * The duration follows Fitts's law, {@code 0.2 + 0.1 log2(d / w + 1)} seconds for a target {@code w}
 * wide at distance {@code d}, both judged on a {@value #REFERENCE_HEIGHT}-line window so that a move
 * looks the same at any window size and GUI scale, held to {@value #MAX_SECONDS} s at most and varied
 * by ±8%. The path is a cubic Bézier bowing slightly to one side, as a hand turning at the
 * wrist moves; the pointer goes along it on a minimum-jerk (smootherstep) profile of slightly
 * front-loaded time, speeding up, slowing down and landing on the target without overshoot.
 */
final class CursorPath {
    static final float REFERENCE_HEIGHT = 1080;
    static final double MAX_SECONDS = 0.9;
    /** Below this, in reference pixels, a target counts as this wide: a pointer is not that precise. */
    private static final float MIN_TARGET = 12;

    /** Start, two control points, end: x and y each. */
    private final float[] points;
    private final double seconds;

    private CursorPath(float[] points, double seconds) {
        this.points = points;
        this.seconds = seconds;
    }

    /**
     * @param targetSize     the target's smaller side; small targets take longer to land on
     * @param viewportHeight the window's height in GUI pixels
     * @param slowdown       multiplies the duration, above 1 for a move that drags something
     */
    static CursorPath plan(float fromX, float fromY, float toX, float toY, float targetSize, float viewportHeight,
                           float slowdown, Random random) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        // Bows to one side more often than the other, by 4-10% of the distance, as one hand does.
        float bow = (random.nextFloat() < 0.7f ? 1 : -1) * (0.04f + random.nextFloat() * 0.06f);
        float nx = -dy * bow;
        float ny = dx * bow;
        float[] points = {
                fromX, fromY,
                fromX + dx * 0.3f + nx, fromY + dy * 0.3f + ny,
                fromX + dx * 0.7f + nx * 0.6f, fromY + dy * 0.7f + ny * 0.6f,
                toX, toY};
        double distance = Math.hypot(dx, dy);
        double seconds = distance < 0.5 ? 0 : fitts(distance, targetSize, viewportHeight) * (0.92 + random.nextFloat() * 0.16);
        return new CursorPath(points, seconds * slowdown);
    }

    /** Fitts's law on the reference window, held to what a person's longest reach takes. */
    static double fitts(double distance, float targetSize, float viewportHeight) {
        double scale = REFERENCE_HEIGHT / Math.max(1, viewportHeight);
        double d = distance * scale;
        double w = Math.max(MIN_TARGET, targetSize * scale);
        return Math.min(MAX_SECONDS, 0.2 + 0.1 * Math.log(d / w + 1) / Math.log(2));
    }

    double seconds() {
        return seconds;
    }

    /**
     * Writes where the pointer is {@code t} seconds into the move into {@code out}; whether the move
     * is over, {@code out} then being the target.
     */
    boolean at(double t, float[] out) {
        double u = seconds <= 0 ? 1 : Math.min(1, Math.max(0, t / seconds));
        // Peak speed a little before the midpoint, as in a reach.
        double e = Math.pow(u, 0.92);
        double s = e * e * e * (10 - 15 * e + 6 * e * e);
        double a = 1 - s;
        double b0 = a * a * a;
        double b1 = 3 * a * a * s;
        double b2 = 3 * a * s * s;
        double b3 = s * s * s;
        out[0] = (float) (b0 * points[0] + b1 * points[2] + b2 * points[4] + b3 * points[6]);
        out[1] = (float) (b0 * points[1] + b1 * points[3] + b2 * points[5] + b3 * points[7]);
        return u >= 1;
    }
}
