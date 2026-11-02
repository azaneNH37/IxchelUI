package com.azane.ixchel.core.css.property;

import java.util.List;

/**
 * {@code grid-template-rows}/{@code -columns} and {@code grid-auto-rows}/{@code -columns}: track
 * entries in order, and the named lines of the explicit grid. Empty for {@code none} (and, for the
 * auto properties, {@code auto}).
 */
public record GridTracks(List<Entry> entries, List<LineName> names) {
    public static final GridTracks NONE = new GridTracks(List.of(), List.of());

    /** {@link Entry#count()} of {@code repeat(auto-fill, …)} and {@code repeat(auto-fit, …)}. */
    public static final int AUTO_FILL = -1;
    public static final int AUTO_FIT = -2;

    /** {@code sizes} repeated {@code count} times; a plain track is one size once. */
    public record Entry(int count, List<Size> sizes) {
    }

    /** {@code name} labels line {@code line}, counted from 1 at the start of the explicit grid. */
    public record LineName(String name, int line) {
    }

    /**
     * A track sizing function: a length or percentage ({@code length}), {@code fr} ({@code fr}),
     * {@code auto}, {@code min-content}, {@code max-content}, {@code fit-content(length)} or
     * {@code minmax(min, max)}.
     */
    public record Size(Kind kind, Length length, float fr, Size min, Size max) {
        public enum Kind { LENGTH, FR, AUTO, MIN_CONTENT, MAX_CONTENT, FIT_CONTENT, MINMAX }

        public static final Size AUTO = new Size(Kind.AUTO, null, 0, null, null);
        public static final Size MIN_CONTENT = new Size(Kind.MIN_CONTENT, null, 0, null, null);
        public static final Size MAX_CONTENT = new Size(Kind.MAX_CONTENT, null, 0, null, null);
    }
}
