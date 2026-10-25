package com.azane.ixchel.core.css.property;

/** A computed length: GUI pixels, a percentage of the containing block, or {@code auto}. */
public record Length(float value, Unit unit) {
    public enum Unit { PX, PERCENT, AUTO }

    public static final Length AUTO = new Length(0, Unit.AUTO);
    public static final Length ZERO = new Length(0, Unit.PX);

    public static Length px(float value) {
        return new Length(value, Unit.PX);
    }

    public static Length percent(float value) {
        return new Length(value, Unit.PERCENT);
    }
}
