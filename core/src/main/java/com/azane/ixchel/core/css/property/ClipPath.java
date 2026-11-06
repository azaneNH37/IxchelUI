package com.azane.ixchel.core.css.property;

import java.util.List;

/**
 * A computed {@code clip-path}: {@code none} or a basic shape ({@code inset()}, {@code circle()},
 * {@code ellipse()}, {@code polygon()}) in the element's border box. Percentages are of the border
 * box: horizontal ones of its width, vertical ones of its height, a circle's radius of
 * {@code sqrt(width² + height²) / sqrt(2)}.
 */
public sealed interface ClipPath permits ClipPath.None, ClipPath.Inset, ClipPath.Circle, ClipPath.Ellipse, ClipPath.Polygon {
    None NONE = new None();
    /** The most vertices a {@code polygon()} may have: the host tests a pixel against each edge. */
    int MAX_POINTS = 64;

    record None() implements ClipPath {
    }

    /** The border box moved in by the four sides, its corners rounded by radii resolved against the border box. */
    record Inset(Length top, Length right, Length bottom, Length left,
                 CornerRadius topLeft, CornerRadius topRight, CornerRadius bottomRight, CornerRadius bottomLeft) implements ClipPath {
    }

    /** A {@code <shape-radius>}: a length or percentage, or with {@code length} {@code null} the {@code closest-side} or {@code farthest-side} keyword. */
    record Radius(Length length, boolean farthest) {
        public static final Radius CLOSEST_SIDE = new Radius(null, false);
        public static final Radius FARTHEST_SIDE = new Radius(null, true);
    }

    record Circle(Radius radius, Length centerX, Length centerY) implements ClipPath {
    }

    record Ellipse(Radius radiusX, Radius radiusY, Length centerX, Length centerY) implements ClipPath {
    }

    /**
     * {@code points}: x then y of each vertex, in order; {@code evenOdd} the {@code evenodd} fill rule,
     * otherwise {@code nonzero}.
     */
    record Polygon(boolean evenOdd, List<Length> points) implements ClipPath {
    }
}
