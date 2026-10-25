package com.azane.ixchel.core.text;

/**
 * Font metrics supplied by the host (the mc layer wraps the vanilla font). {@code size} is the CSS
 * font size in GUI pixels; {@code 9} is the vanilla font at scale 1.
 */
public interface TextMeasurer {
    float width(String text, float size);

    float lineHeight(float size);
}
