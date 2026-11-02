package com.azane.ixchel.core.css.property;

/**
 * {@code line-height}: {@code normal} (the font's own), a factor of the font size, which inherits as
 * a factor, or a length. A percentage or {@code em} is of the element's font size and computes to
 * pixels, which inherit as they are.
 */
public record LineHeight(Kind kind, float factor, Length length) {
    public enum Kind { NORMAL, FACTOR, LENGTH }

    public static final LineHeight NORMAL = new LineHeight(Kind.NORMAL, 0, null);

    public static LineHeight factor(float factor) {
        return new LineHeight(Kind.FACTOR, factor, null);
    }

    public static LineHeight of(Length length) {
        return new LineHeight(Kind.LENGTH, 0, length);
    }

    /** In pixels for a font of {@code size} whose own line height is {@code normal}. */
    public float resolve(float size, float normal) {
        return switch (kind) {
            case NORMAL -> normal;
            case FACTOR -> factor * size;
            case LENGTH -> length.resolve(size);
        };
    }
}
