package com.azane.ixchel.core.css.property;

import java.util.List;

/**
 * A computed CSS {@code <image>}: {@code none}, {@code url()}, a gradient ({@code linear-gradient()},
 * {@code radial-gradient()}, {@code conic-gradient()} and the repeating linear and radial ones) or
 * {@code paint()}.
 */
public sealed interface CssImage permits CssImage.None, CssImage.Url, CssImage.Gradient, CssImage.Paint {
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
     * A color stop: its position is {@code null} where it was omitted. On a linear or radial gradient
     * it is a length or a percentage of the gradient line or ray; on a conic one a percentage of the
     * full turn (an angle is stored as its percentage).
     */
    record Stop(int color, Length position) {
    }

    /** A gradient image. {@code repeating}: the stops repeat along the line, ray or turn beyond the first and last. */
    sealed interface Gradient extends CssImage permits LinearGradient, RadialGradient, ConicGradient {
        List<Stop> stops();

        boolean repeating();
    }

    /**
     * {@code angle} in degrees, 0 pointing up and growing clockwise, or NaN when the gradient points to
     * a corner: {@code cornerX} -1 left or 1 right, {@code cornerY} -1 top or 1 bottom, whose angle
     * depends on the box's aspect ratio.
     */
    record LinearGradient(float angle, int cornerX, int cornerY, List<Stop> stops, boolean repeating) implements Gradient {
    }

    /** How far a radial gradient's ending shape reaches: to a side or corner of the box, or as given. */
    enum Extent { CLOSEST_SIDE, FARTHEST_SIDE, CLOSEST_CORNER, FARTHEST_CORNER, EXPLICIT }

    /**
     * Centered at {@code (centerX, centerY)}, percentages of the box; the ending shape a {@code circle}
     * or an ellipse of {@code extent}. {@link Extent#EXPLICIT} gives the radii in {@code radiusX} and
     * {@code radiusY} (equal lengths for a circle; percentages of the box's width and height for an
     * ellipse); otherwise they are {@code null}.
     */
    record RadialGradient(boolean circle, Extent extent, Length radiusX, Length radiusY, Length centerX, Length centerY,
                          List<Stop> stops, boolean repeating) implements Gradient {
    }

    /** Starting {@code from} degrees clockwise from up, around {@code (centerX, centerY)}, percentages of the box. */
    record ConicGradient(float from, Length centerX, Length centerY, List<Stop> stops) implements Gradient {
        @Override
        public boolean repeating() {
            return false;
        }
    }

    /** {@code paint(effect)}: the document's box effect {@code effect}, filling the border box. */
    record Paint(String effect) implements CssImage {
    }
}
