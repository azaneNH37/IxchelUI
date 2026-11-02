package com.azane.ixchel.core.css.property;

import java.util.List;

/**
 * A computed {@code transform}: the transform functions in source order, each normalized to one of
 * three kinds. Percentages in a translation refer to the element's border box, so the matrix is built
 * at paint time by {@link #matrix}.
 */
public record Transform(List<Fn> functions) {
    public static final Transform NONE = new Transform(List.of());

    public enum Kind { TRANSLATE, SCALE, ROTATE }

    /**
     * {@code TRANSLATE}: {@code x}, {@code y} lengths (px or %); {@code SCALE}: factors in
     * {@code x.value()}, {@code y.value()}; {@code ROTATE}: clockwise degrees in {@code x.value()}.
     */
    public record Fn(Kind kind, Length x, Length y) {
        /** The function that changes nothing, the partner of this one when the other side is {@code none}. */
        Fn identity() {
            return switch (kind) {
                case TRANSLATE -> new Fn(kind, Length.ZERO, Length.ZERO);
                case SCALE -> new Fn(kind, Length.px(1), Length.px(1));
                case ROTATE -> new Fn(kind, Length.ZERO, Length.ZERO);
            };
        }
    }

    public boolean isNone() {
        return functions.isEmpty();
    }

    /**
     * The matrix for a border box of {@code width × height} whose transform origin is {@code (ox, oy)}
     * in the coordinates the box is laid out in; it maps those coordinates.
     */
    public Affine matrix(float width, float height, float ox, float oy) {
        Affine m = Affine.translate(ox, oy);
        for (Fn fn : functions) {
            m = m.then(switch (fn.kind()) {
                case TRANSLATE -> Affine.translate(resolve(fn.x(), width), resolve(fn.y(), height));
                case SCALE -> new Affine(fn.x().value(), 0, 0, fn.y().value(), 0, 0);
                case ROTATE -> {
                    double r = Math.toRadians(fn.x().value());
                    float cos = (float) Math.cos(r);
                    float sin = (float) Math.sin(r);
                    yield new Affine(cos, sin, -sin, cos, 0, 0);
                }
            });
        }
        return m.then(Affine.translate(-ox, -oy));
    }

    /** {@code l} in px against {@code base} for a percentage. */
    public static float resolve(Length l, float base) {
        return l.resolve(base);
    }

    /**
     * The {@code transform} of the element styled {@code cs} whose border box is at {@code (x, y)},
     * {@code width × height}, about its {@code transform-origin}; {@code null} for {@code none}.
     */
    public static Affine of(ComputedStyle cs, float x, float y, float width, float height) {
        Transform t = cs.get(CssProperties.TRANSFORM);
        if (t.isNone()) {
            return null;
        }
        TransformOrigin o = cs.get(CssProperties.TRANSFORM_ORIGIN);
        return t.matrix(width, height, x + resolve(o.x(), width), y + resolve(o.y(), height));
    }
}
