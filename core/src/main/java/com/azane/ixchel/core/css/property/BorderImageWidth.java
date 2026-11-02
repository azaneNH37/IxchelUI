package com.azane.ixchel.core.css.property;

/** {@code border-image-width}: top, right, bottom, left. */
public record BorderImageWidth(Side top, Side right, Side bottom, Side left) {
    /**
     * A length (a percentage is of the border image area's width or height; {@code auto} is the slice's
     * own size in image pixels), or, when {@code length} is {@code null}, {@code number} times the border width.
     */
    public record Side(Length length, float number) {
    }

    private static final Side ONE = new Side(null, 1);
    public static final BorderImageWidth INITIAL = new BorderImageWidth(ONE, ONE, ONE, ONE);
}
