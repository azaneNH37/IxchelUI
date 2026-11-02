package com.azane.ixchel.core.css.property;

/**
 * {@code border-image-slice}: the inward offsets of the four slicing lines, top, right, bottom, left;
 * a {@code PX} length is a number of image pixels, a percentage is of the image's size. {@code fill}
 * keeps the middle part.
 */
public record BorderImageSlice(Length top, Length right, Length bottom, Length left, boolean fill) {
    public static final BorderImageSlice INITIAL =
            new BorderImageSlice(Length.percent(100), Length.percent(100), Length.percent(100), Length.percent(100), false);
}
