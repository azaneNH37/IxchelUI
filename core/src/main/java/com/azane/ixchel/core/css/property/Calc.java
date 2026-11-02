package com.azane.ixchel.core.css.property;

import java.util.List;

/**
 * A computed {@code calc()}, {@code min()}, {@code max()} or {@code clamp()} of pixels and percentages,
 * or an interpolation between lengths that is not linear in both. Pixels and percentages that only add
 * and scale are kept as one {@link Linear}; the other nodes appear only around a percentage that a
 * minimum or maximum needs the basis to settle.
 */
public sealed interface Calc {
    /** Pixels against {@code basis}, the length a percentage is of. */
    float resolve(float basis);

    /** {@code px + percent% of the basis}. */
    record Linear(float px, float percent) implements Calc {
        @Override
        public float resolve(float basis) {
            return px + basis * percent / 100;
        }
    }

    record Sum(Calc a, Calc b) implements Calc {
        @Override
        public float resolve(float basis) {
            return a.resolve(basis) + b.resolve(basis);
        }
    }

    record Scale(Calc of, float factor) implements Calc {
        @Override
        public float resolve(float basis) {
            return of.resolve(basis) * factor;
        }
    }

    record Min(List<Calc> of) implements Calc {
        @Override
        public float resolve(float basis) {
            float v = Float.POSITIVE_INFINITY;
            for (Calc c : of) {
                v = Math.min(v, c.resolve(basis));
            }
            return v;
        }
    }

    record Max(List<Calc> of) implements Calc {
        @Override
        public float resolve(float basis) {
            float v = Float.NEGATIVE_INFINITY;
            for (Calc c : of) {
                v = Math.max(v, c.resolve(basis));
            }
            return v;
        }
    }

    /** {@code clamp(min, value, max)}: the minimum wins over the maximum. */
    record Clamp(Calc min, Calc value, Calc max) implements Calc {
        @Override
        public float resolve(float basis) {
            return Math.max(min.resolve(basis), Math.min(value.resolve(basis), max.resolve(basis)));
        }
    }

    /** The value {@code p} of the way from {@code from} to {@code to}, CSS's calc-mix of two lengths. */
    record Mix(Calc from, Calc to, float p) implements Calc {
        @Override
        public float resolve(float basis) {
            float a = from.resolve(basis);
            return a + (to.resolve(basis) - a) * p;
        }
    }

    /** {@code of}, clamped at 0, for properties that take no negative lengths. */
    record NonNegative(Calc of) implements Calc {
        @Override
        public float resolve(float basis) {
            return Math.max(0, of.resolve(basis));
        }
    }

    /** {@code c} clamped at 0 where it can be negative. */
    static Calc nonNegative(Calc c) {
        if (c instanceof Linear l && l.px >= 0 && l.percent >= 0 || c instanceof NonNegative) {
            return c;
        }
        if (c instanceof Linear l && l.percent == 0) {
            return new Linear(0, 0);
        }
        return new NonNegative(c);
    }
}
