package com.azane.ixchel.core.css.property;

/**
 * A {@code cubic-bezier()} easing function from (0, 0) to (1, 1); the keywords are the CSS
 * presets. {@code x1} and {@code x2} lie in {@code [0, 1]}, so the curve is a function of x.
 */
public record Easing(float x1, float y1, float x2, float y2) {
    public static final Easing LINEAR = new Easing(0, 0, 1, 1);
    public static final Easing EASE = new Easing(0.25f, 0.1f, 0.25f, 1);
    public static final Easing EASE_IN = new Easing(0.42f, 0, 1, 1);
    public static final Easing EASE_OUT = new Easing(0, 0, 0.58f, 1);
    public static final Easing EASE_IN_OUT = new Easing(0.42f, 0, 0.58f, 1);

    /** The output progress for input progress {@code x} in {@code [0, 1]}. */
    public float apply(float x) {
        if (x <= 0 || x >= 1 || this.equals(LINEAR)) {
            return x <= 0 ? 0 : x >= 1 ? 1 : x;
        }
        // Newton's method on x(t), falling back to bisection where the slope is flat.
        double t = x;
        for (int i = 0; i < 8; i++) {
            double err = bezier(t, x1, x2) - x;
            if (Math.abs(err) < 1e-6) {
                return (float) bezier(t, y1, y2);
            }
            double slope = slope(t, x1, x2);
            if (Math.abs(slope) < 1e-6) {
                break;
            }
            t -= err / slope;
        }
        double lo = 0;
        double hi = 1;
        t = x;
        for (int i = 0; i < 32; i++) {
            double v = bezier(t, x1, x2);
            if (Math.abs(v - x) < 1e-6) {
                break;
            }
            if (v < x) {
                lo = t;
            } else {
                hi = t;
            }
            t = (lo + hi) / 2;
        }
        return (float) bezier(t, y1, y2);
    }

    /** One coordinate of the curve with end points 0 and 1. */
    private static double bezier(double t, double p1, double p2) {
        double u = 1 - t;
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
    }

    private static double slope(double t, double p1, double p2) {
        double u = 1 - t;
        return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2);
    }
}
