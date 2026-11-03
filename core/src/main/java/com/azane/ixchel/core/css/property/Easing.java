package com.azane.ixchel.core.css.property;

/** An easing function (CSS Easing Functions Level 1): {@code cubic-bezier()} or {@code steps()}. */
public sealed interface Easing {
    Easing LINEAR = new CubicBezier(0, 0, 1, 1);
    Easing EASE = new CubicBezier(0.25f, 0.1f, 0.25f, 1);
    Easing EASE_IN = new CubicBezier(0.42f, 0, 1, 1);
    Easing EASE_OUT = new CubicBezier(0, 0, 0.58f, 1);
    Easing EASE_IN_OUT = new CubicBezier(0.42f, 0, 0.58f, 1);
    Easing STEP_START = new Steps(1, Steps.Position.JUMP_START);
    Easing STEP_END = new Steps(1, Steps.Position.JUMP_END);

    /**
     * The output progress for input progress {@code x} in {@code [0, 1]}, taken outside the before
     * phase; a caller in the before phase uses 0 instead.
     */
    float apply(float x);

    /** A curve from (0, 0) to (1, 1); {@code x1} and {@code x2} lie in {@code [0, 1]}, so it is a function of x. */
    record CubicBezier(float x1, float y1, float x2, float y2) implements Easing {
        @Override
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

    /**
     * {@code steps(count, position)}: {@code count} equal intervals; {@code count} is at least 1, or
     * at least 2 for {@link Position#JUMP_NONE}.
     */
    record Steps(int count, Position position) implements Easing {
        public enum Position { JUMP_START, JUMP_END, JUMP_NONE, JUMP_BOTH }

        @Override
        public float apply(float x) {
            int step = (int) Math.floor(x * count);
            if (position == Position.JUMP_START || position == Position.JUMP_BOTH) {
                step++;
            }
            int jumps = switch (position) {
                case JUMP_NONE -> count - 1;
                case JUMP_BOTH -> count + 1;
                default -> count;
            };
            if (x >= 0 && step < 0) {
                step = 0;
            }
            if (x <= 1 && step > jumps) {
                step = jumps;
            }
            return (float) step / jumps;
        }
    }
}
