package com.azane.ixchel.core.css.property;

/**
 * {@code grid-row-start} and the other placement longhands: {@code auto}; a line {@code number}
 * (negative counts from the end); the {@code number}th line called {@code name} (an area's name also
 * finds its {@code -start}/{@code -end} line); {@code span number}; or {@code span number name}.
 */
public record GridLine(Kind kind, int number, String name) {
    public enum Kind { AUTO, LINE, NAMED, SPAN, NAMED_SPAN }

    public static final GridLine AUTO = new GridLine(Kind.AUTO, 0, null);
}
