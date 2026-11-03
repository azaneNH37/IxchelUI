package com.azane.ixchel.core.css.property;

import java.util.List;

/** A computed CSS {@code <image>}: {@code none}, {@code url()}, {@code linear-gradient()} or {@code paint()}. */
public sealed interface CssImage permits CssImage.None, CssImage.Url, CssImage.LinearGradient, CssImage.Paint {
    None NONE = new None();

    record None() implements CssImage {
    }

    /**
     * A resource location such as {@code modid:textures/gui/icon.png}, or a sprite of the host's
     * texture atlas after {@link Syntaxes#SPRITE}.
     */
    record Url(String src) implements CssImage {
    }

    /**
     * {@code angle} in degrees, 0 pointing up and growing clockwise, or NaN when the gradient points to
     * a corner: {@code cornerX} -1 left or 1 right, {@code cornerY} -1 top or 1 bottom, whose angle
     * depends on the box's aspect ratio. A stop's position is {@code null} where it was omitted.
     */
    record LinearGradient(float angle, int cornerX, int cornerY, List<Stop> stops) implements CssImage {
        public record Stop(int color, Length position) {
        }
    }

    /** {@code paint(effect)}: the document's box effect {@code effect}, filling the border box. */
    record Paint(String effect) implements CssImage {
    }
}
