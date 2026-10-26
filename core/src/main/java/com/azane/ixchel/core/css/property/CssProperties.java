package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.property.Keywords.AlignItems;
import com.azane.ixchel.core.css.property.Keywords.AlignSelf;
import com.azane.ixchel.core.css.property.Keywords.Display;
import com.azane.ixchel.core.css.property.Keywords.FlexDirection;
import com.azane.ixchel.core.css.property.Keywords.FlexWrap;
import com.azane.ixchel.core.css.property.Keywords.JustifyContent;
import com.azane.ixchel.core.css.property.Keywords.Overflow;
import com.azane.ixchel.core.css.property.Keywords.Position;

import static com.azane.ixchel.core.css.property.Syntaxes.color;
import static com.azane.ixchel.core.css.property.Syntaxes.keyword;
import static com.azane.ixchel.core.css.property.Syntaxes.length;
import static com.azane.ixchel.core.css.property.Syntaxes.number;

/**
 * The supported property set. Departures from CSS initial values: {@code display} is {@code flex}
 * and {@code flex-direction} is {@code column} (no document flow), {@code color} is white.
 */
public final class CssProperties {
    private CssProperties() {
    }

    public static final PropertyRegistry REGISTRY = new PropertyRegistry();
    private static final PropertyRegistry R = REGISTRY;

    private static final Syntax<Length> SIZE = length(true, true, false);
    private static final Syntax<Length> MAX_SIZE = length(true, true, false);
    private static final Syntax<Length> INSET = length(true, true, true);
    private static final Syntax<Length> MARGIN = length(true, true, true);
    private static final Syntax<Length> PADDING = length(true, false, false);
    private static final Syntax<Length> GAP = length(true, false, false);

    public static final PropertyDef<Display> DISPLAY = R.register("display", keyword(Display.class), Display.FLEX, false, true, false);
    public static final PropertyDef<Position> POSITION = R.register("position", keyword(Position.class), Position.RELATIVE, false, true, false);
    public static final PropertyDef<Length> TOP = R.register("top", INSET, Length.AUTO, false, true, true);
    public static final PropertyDef<Length> RIGHT = R.register("right", INSET, Length.AUTO, false, true, true);
    public static final PropertyDef<Length> BOTTOM = R.register("bottom", INSET, Length.AUTO, false, true, true);
    public static final PropertyDef<Length> LEFT = R.register("left", INSET, Length.AUTO, false, true, true);

    public static final PropertyDef<Length> WIDTH = R.register("width", SIZE, Length.AUTO, false, true, true);
    public static final PropertyDef<Length> HEIGHT = R.register("height", SIZE, Length.AUTO, false, true, true);
    public static final PropertyDef<Length> MIN_WIDTH = R.register("min-width", SIZE, Length.AUTO, false, true, true);
    public static final PropertyDef<Length> MIN_HEIGHT = R.register("min-height", SIZE, Length.AUTO, false, true, true);
    public static final PropertyDef<Length> MAX_WIDTH = R.register("max-width", MAX_SIZE, Length.AUTO, false, true, true);
    public static final PropertyDef<Length> MAX_HEIGHT = R.register("max-height", MAX_SIZE, Length.AUTO, false, true, true);

    public static final PropertyDef<Length> MARGIN_TOP = R.register("margin-top", MARGIN, Length.ZERO, false, true, true);
    public static final PropertyDef<Length> MARGIN_RIGHT = R.register("margin-right", MARGIN, Length.ZERO, false, true, true);
    public static final PropertyDef<Length> MARGIN_BOTTOM = R.register("margin-bottom", MARGIN, Length.ZERO, false, true, true);
    public static final PropertyDef<Length> MARGIN_LEFT = R.register("margin-left", MARGIN, Length.ZERO, false, true, true);
    public static final PropertyDef<Length> PADDING_TOP = R.register("padding-top", PADDING, Length.ZERO, false, true, true);
    public static final PropertyDef<Length> PADDING_RIGHT = R.register("padding-right", PADDING, Length.ZERO, false, true, true);
    public static final PropertyDef<Length> PADDING_BOTTOM = R.register("padding-bottom", PADDING, Length.ZERO, false, true, true);
    public static final PropertyDef<Length> PADDING_LEFT = R.register("padding-left", PADDING, Length.ZERO, false, true, true);

    public static final PropertyDef<FlexDirection> FLEX_DIRECTION = R.register("flex-direction", keyword(FlexDirection.class), FlexDirection.COLUMN, false, true, false);
    public static final PropertyDef<FlexWrap> FLEX_WRAP = R.register("flex-wrap", keyword(FlexWrap.class), FlexWrap.NOWRAP, false, true, false);
    public static final PropertyDef<Float> FLEX_GROW = R.register("flex-grow", number(false), 0f, false, true, true);
    public static final PropertyDef<Float> FLEX_SHRINK = R.register("flex-shrink", number(false), 1f, false, true, true);
    public static final PropertyDef<Length> FLEX_BASIS = R.register("flex-basis", SIZE, Length.AUTO, false, true, true);
    public static final PropertyDef<AlignItems> ALIGN_ITEMS = R.register("align-items", keyword(AlignItems.class), AlignItems.STRETCH, false, true, false);
    public static final PropertyDef<AlignSelf> ALIGN_SELF = R.register("align-self", keyword(AlignSelf.class), AlignSelf.AUTO, false, true, false);
    public static final PropertyDef<JustifyContent> JUSTIFY_CONTENT = R.register("justify-content", keyword(JustifyContent.class), JustifyContent.FLEX_START, false, true, false);
    public static final PropertyDef<Length> ROW_GAP = R.register("row-gap", GAP, Length.ZERO, false, true, true);
    public static final PropertyDef<Length> COLUMN_GAP = R.register("column-gap", GAP, Length.ZERO, false, true, true);

    public static final PropertyDef<Overflow> OVERFLOW = R.register("overflow", keyword(Overflow.class), Overflow.VISIBLE, false, true, false);
    public static final PropertyDef<Integer> BACKGROUND_COLOR = R.register("background-color", color(), 0x00000000, false, false, true);
    public static final PropertyDef<Integer> COLOR = R.register("color", color(), 0xFFFFFFFF, true, false, true);
    public static final PropertyDef<Length> FONT_SIZE = R.register("font-size", length(false, false, false), Length.px(9), true, true, true);

    static {
        R.registerShorthand("margin", Shorthand.box(MARGIN), MARGIN_TOP, MARGIN_RIGHT, MARGIN_BOTTOM, MARGIN_LEFT);
        R.registerShorthand("padding", Shorthand.box(PADDING), PADDING_TOP, PADDING_RIGHT, PADDING_BOTTOM, PADDING_LEFT);
        R.registerShorthand("gap", Shorthand.pair(GAP), ROW_GAP, COLUMN_GAP);
        R.freeze();
    }
}
