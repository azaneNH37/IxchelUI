package com.azane.ixchel.core.css.property;

/** Keyword-valued properties. Enum constant = CSS keyword upper-cased with {@code -} as {@code _}. */
public final class Keywords {
    private Keywords() {
    }

    public enum Display { FLEX, NONE }

    public enum Position { RELATIVE, ABSOLUTE }

    public enum FlexDirection { ROW, ROW_REVERSE, COLUMN, COLUMN_REVERSE }

    public enum FlexWrap { NOWRAP, WRAP, WRAP_REVERSE }

    /** {@code align-items}; {@code align-self} adds {@code auto} via {@link AlignSelf}. */
    public enum AlignItems { FLEX_START, FLEX_END, CENTER, BASELINE, STRETCH }

    public enum AlignSelf { AUTO, FLEX_START, FLEX_END, CENTER, BASELINE, STRETCH }

    public enum JustifyContent { FLEX_START, FLEX_END, CENTER, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }

    /** {@code hidden} clips to the padding box; there is no scrolling. */
    public enum Overflow { VISIBLE, HIDDEN }
}
