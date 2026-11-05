package com.azane.ixchel.core.text;

/**
 * Font metrics supplied by the host (the mc layer wraps the vanilla font). {@code size} is the CSS
 * font size in GUI pixels; {@code 9} is the vanilla font at scale 1. {@code font} is a font id
 * ({@code namespace:path}), {@code null} for the host's default font.
 */
public interface TextMeasurer {
    /** The advance of {@code text} on one line; bold glyphs may be wider. */
    float width(String text, float size, boolean bold, String font);

    /** Whether the host has the font {@code font} ({@code font-family} falls through ids it lacks). */
    boolean hasFont(String font);

    float lineHeight(float size);

    /** The distance from the top of a line to its baseline, at most {@link #lineHeight}. */
    float baseline(float size);
}
