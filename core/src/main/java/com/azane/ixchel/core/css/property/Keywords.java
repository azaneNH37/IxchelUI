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

    /**
     * Every value but {@code visible} makes a scroll container: it clips its descendants to its border
     * box (there are no borders, so that is the padding box), its automatic minimum size as a flex
     * item is 0, and its content can be moved by a vertical scroll offset ({@link
     * com.azane.ixchel.core.dom.Element#scrollTop()}). The wheel scrolls only {@code auto} and
     * {@code scroll}; {@code hidden} scrolls programmatically. {@code scroll} always draws a scrollbar
     * track and thumb, {@code auto} draws a thumb only while the content overflows. Content that
     * overflows horizontally is clipped but cannot be scrolled.
     */
    public enum Overflow { VISIBLE, HIDDEN, SCROLL, AUTO }

    /** {@code background-repeat}, one keyword for both axes; {@code space} and {@code round} are not supported. */
    public enum BackgroundRepeat { REPEAT, REPEAT_X, REPEAT_Y, NO_REPEAT }
}
