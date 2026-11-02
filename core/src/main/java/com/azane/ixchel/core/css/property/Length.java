package com.azane.ixchel.core.css.property;

/**
 * A computed length: GUI pixels, a percentage of a basis (the containing block, the box, the image),
 * {@code auto}, or a {@link Calc} mixing pixels and percentages. Relative units are pixels by the time
 * a value is computed. {@code value} is meaningful for {@link Unit#PX} and {@link Unit#PERCENT} only.
 */
public record Length(float value, Unit unit, Calc calc) {
    public enum Unit { PX, PERCENT, AUTO, CALC }

    public static final Length AUTO = new Length(0, Unit.AUTO, null);
    public static final Length ZERO = new Length(0, Unit.PX, null);

    public static Length px(float value) {
        return new Length(value, Unit.PX, null);
    }

    public static Length percent(float value) {
        return new Length(value, Unit.PERCENT, null);
    }

    /** {@code c} in its simplest form: pixels or a percentage when it is one of those. */
    public static Length of(Calc c) {
        if (c instanceof Calc.Linear l) {
            if (l.percent() == 0) {
                return px(l.px());
            }
            if (l.px() == 0) {
                return percent(l.percent());
            }
        }
        return new Length(0, Unit.CALC, c);
    }

    public boolean isAuto() {
        return unit == Unit.AUTO;
    }

    /** Pixels against {@code basis}, the length a percentage is of; 0 for {@code auto}. */
    public float resolve(float basis) {
        return switch (unit) {
            case PX -> value;
            case PERCENT -> basis * value / 100;
            case CALC -> calc.resolve(basis);
            case AUTO -> 0;
        };
    }

    /** This length as a calculation; {@code auto} has none. */
    Calc asCalc() {
        return switch (unit) {
            case PX -> new Calc.Linear(value, 0);
            case PERCENT -> new Calc.Linear(0, value);
            case CALC -> calc;
            case AUTO -> throw new IllegalStateException("auto is not a calculation");
        };
    }

    /**
     * The value {@code p} of the way from {@code a} to {@code b}, by pixel and percentage component;
     * a non-linear side mixes as a whole. {@code null} when either is {@code auto}. With
     * {@code nonNegative} the result is never below 0.
     */
    static Length mix(Length a, Length b, float p, boolean nonNegative) {
        if (a.isAuto() || b.isAuto()) {
            return null;
        }
        if (a.unit == b.unit && a.unit != Unit.CALC) {
            float v = a.value + (b.value - a.value) * p;
            return new Length(nonNegative ? Math.max(0, v) : v, a.unit, null);
        }
        Calc ca = a.asCalc();
        Calc cb = b.asCalc();
        Calc out;
        if (ca instanceof Calc.Linear la && cb instanceof Calc.Linear lb) {
            out = new Calc.Linear(la.px() + (lb.px() - la.px()) * p, la.percent() + (lb.percent() - la.percent()) * p);
        } else {
            out = new Calc.Mix(ca, cb, p);
        }
        if (nonNegative) {
            out = out instanceof Calc.Linear l && l.percent() == 0 ? new Calc.Linear(Math.max(0, l.px()), 0) : Calc.nonNegative(out);
        }
        return of(out);
    }
}
