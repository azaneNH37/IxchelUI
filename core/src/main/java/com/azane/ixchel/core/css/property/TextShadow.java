package com.azane.ixchel.core.css.property;

/**
 * A computed {@code text-shadow}: one unblurred copy of the text, offset by {@code (x, y)} GUI pixels
 * and drawn in {@code color} (ARGB) under it. {@code none} is a transparent copy at no offset, so a
 * shadow fades in and out of {@code none} as CSS interpolates it.
 */
public record TextShadow(float x, float y, int color) {
    public static final TextShadow NONE = new TextShadow(0, 0, 0);
}
