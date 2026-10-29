package com.azane.ixchel.core.css.property;

/** A computed {@code transform-origin}: px or a percentage of the border box, from its top-left. */
public record TransformOrigin(Length x, Length y) {
    public static final TransformOrigin CENTER = new TransformOrigin(Length.percent(50), Length.percent(50));
}
