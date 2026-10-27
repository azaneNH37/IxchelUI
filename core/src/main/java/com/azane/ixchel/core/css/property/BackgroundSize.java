package com.azane.ixchel.core.css.property;

/**
 * A computed {@code background-size}: each side is px, a percentage of the background positioning
 * area, or {@code auto} (taken from the image's natural size and aspect ratio).
 */
public record BackgroundSize(Length width, Length height) {
    public static final BackgroundSize AUTO = new BackgroundSize(Length.AUTO, Length.AUTO);
}
