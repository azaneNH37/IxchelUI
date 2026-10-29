package com.azane.ixchel.core.css.property;

/**
 * A 2D affine map in CSS {@code matrix(a, b, c, d, e, f)} order: {@code x' = a x + c y + e},
 * {@code y' = b x + d y + f}.
 */
public record Affine(float a, float b, float c, float d, float e, float f) {
    public static final Affine IDENTITY = new Affine(1, 0, 0, 1, 0, 0);

    public static Affine translate(float x, float y) {
        return new Affine(1, 0, 0, 1, x, y);
    }

    /** This map after {@code m}: {@code (this ∘ m)(p) = this(m(p))}, CSS's left-to-right function list order. */
    public Affine then(Affine m) {
        return new Affine(a * m.a + c * m.b, b * m.a + d * m.b,
                a * m.c + c * m.d, b * m.c + d * m.d,
                a * m.e + c * m.f + e, b * m.e + d * m.f + f);
    }

    /** The inverse, or {@code null} when the map is singular (a zero scale), which no point reaches. */
    public Affine invert() {
        float det = a * d - b * c;
        if (det == 0 || !Float.isFinite(det)) {
            return null;
        }
        return new Affine(d / det, -b / det, -c / det, a / det,
                (c * f - d * e) / det, (b * e - a * f) / det);
    }

    public float x(float x, float y) {
        return a * x + c * y + e;
    }

    public float y(float x, float y) {
        return b * x + d * y + f;
    }
}
