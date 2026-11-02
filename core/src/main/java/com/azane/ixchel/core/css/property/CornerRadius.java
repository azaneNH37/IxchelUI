package com.azane.ixchel.core.css.property;

/** One {@code border-*-radius}: the horizontal then the vertical radius of the corner's ellipse. */
public record CornerRadius(Length x, Length y) {
    public static final CornerRadius ZERO = new CornerRadius(Length.ZERO, Length.ZERO);
}
