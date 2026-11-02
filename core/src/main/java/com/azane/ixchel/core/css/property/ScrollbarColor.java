package com.azane.ixchel.core.css.property;

/** A computed {@code scrollbar-color}, ARGB; {@code auto} is {@link #AUTO}'s gray thumb on a dark track. */
public record ScrollbarColor(int thumb, int track) {
    public static final ScrollbarColor AUTO = new ScrollbarColor(0xFFA0A0A0, 0x80000000);
}
