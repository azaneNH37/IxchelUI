package com.azane.ixchel.core.css.property;

/** One shadow of {@code box-shadow}, lengths in GUI pixels. */
public record BoxShadow(float x, float y, float blur, float spread, int color, boolean inset) {
}
