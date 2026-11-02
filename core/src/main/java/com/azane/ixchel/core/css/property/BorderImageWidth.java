package com.azane.ixchel.core.css.property;

/** {@code border-image-width}: top, right, bottom, left. */
public record BorderImageWidth(Side top, Side right, Side bottom, Side left) {
    /**
     * A length in GUI pixels, a percentage of the border image area's width or height, a multiple
     * of the border width ({@code NUMBER}), or {@code auto}: the slice's own size in image pixels.
     */
    public record Side(float value, Kind kind) {
        public enum Kind { PX, PERCENT, NUMBER, AUTO }
    }

    private static final Side ONE = new Side(1, Side.Kind.NUMBER);
    public static final BorderImageWidth INITIAL = new BorderImageWidth(ONE, ONE, ONE, ONE);
}
