package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.property.Keywords.AlignItems;
import com.azane.ixchel.core.css.property.Keywords.AlignSelf;
import com.azane.ixchel.core.css.property.Keywords.BackgroundBlendMode;
import com.azane.ixchel.core.css.property.Keywords.BackgroundRepeat;
import com.azane.ixchel.core.css.property.Keywords.BorderStyle;
import com.azane.ixchel.core.css.property.Keywords.Display;
import com.azane.ixchel.core.css.property.Keywords.FlexDirection;
import com.azane.ixchel.core.css.property.Keywords.FlexWrap;
import com.azane.ixchel.core.css.property.Keywords.FontStyle;
import com.azane.ixchel.core.css.property.Keywords.FontWeight;
import com.azane.ixchel.core.css.property.Keywords.JustifyContent;
import com.azane.ixchel.core.css.property.Keywords.Overflow;
import com.azane.ixchel.core.css.property.Keywords.Position;
import com.azane.ixchel.core.css.property.Keywords.TextAlign;
import com.azane.ixchel.core.css.property.Keywords.TextDecoration;
import com.azane.ixchel.core.css.property.Keywords.TextOverflow;
import com.azane.ixchel.core.css.property.Keywords.WhiteSpace;

import java.util.List;

import static com.azane.ixchel.core.css.property.Syntaxes.backgroundImage;
import static com.azane.ixchel.core.css.property.Syntaxes.backgroundSize;
import static com.azane.ixchel.core.css.property.Syntaxes.color;
import static com.azane.ixchel.core.css.property.Syntaxes.commaList;
import static com.azane.ixchel.core.css.property.Syntaxes.easing;
import static com.azane.ixchel.core.css.property.Syntaxes.keyword;
import static com.azane.ixchel.core.css.property.Syntaxes.length;
import static com.azane.ixchel.core.css.property.Syntaxes.number;
import static com.azane.ixchel.core.css.property.Syntaxes.scrollbarColor;
import static com.azane.ixchel.core.css.property.Syntaxes.textShadow;
import static com.azane.ixchel.core.css.property.Syntaxes.time;
import static com.azane.ixchel.core.css.property.Syntaxes.transform;
import static com.azane.ixchel.core.css.property.Syntaxes.transformOrigin;
import static com.azane.ixchel.core.css.property.Syntaxes.transitionProperty;

/**
 * The supported property set. Departures from CSS initial values: {@code display} is {@code flex}
 * and {@code flex-direction} is {@code column} (no block flow: a {@code block} holds only inline content,
 * see {@link Display}), {@code color} is white, and sizes
 * ({@code width}, {@code min-height}, ...) are border-box sizes, as with {@code box-sizing: border-box}.
 * Animatable properties are those whose values {@code anim} may interpolate; the
 * {@code transition-*} lists are cycled per property as in CSS.
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
    private static final Syntax<Length> BACKGROUND_OFFSET = length(true, false, true);
    private static final Syntax<Length> BORDER_WIDTH = length(false, false, false);
    private static final Length MEDIUM = Length.px(3);

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
    /** Drawn over the content, so no layout change; see {@code layout.Scrollbar}. */
    public static final PropertyDef<Keywords.ScrollbarWidth> SCROLLBAR_WIDTH =
            R.register("scrollbar-width", keyword(Keywords.ScrollbarWidth.class), Keywords.ScrollbarWidth.AUTO, false, false, false);
    /** Inherited, as in CSS. */
    public static final PropertyDef<ScrollbarColor> SCROLLBAR_COLOR =
            R.register("scrollbar-color", scrollbarColor(), ScrollbarColor.AUTO, true, false, false);
    /** A number; values above 1 act as 1. Percentages are not supported. */
    public static final PropertyDef<Float> OPACITY = R.register("opacity", number(false), 1f, false, false, true);
    /** Paint-time only: layout ignores it, painting and hit testing go through it. */
    public static final PropertyDef<Transform> TRANSFORM = R.register("transform", transform(), Transform.NONE, false, false, true);
    public static final PropertyDef<TransformOrigin> TRANSFORM_ORIGIN =
            R.register("transform-origin", transformOrigin(), TransformOrigin.CENTER, false, false, false);
    /** Used width is 0 while the side's style is {@code none}; the initial width is CSS {@code medium}, 3px. */
    public static final PropertyDef<Length> BORDER_TOP_WIDTH = R.register("border-top-width", BORDER_WIDTH, MEDIUM, false, true, true);
    public static final PropertyDef<Length> BORDER_RIGHT_WIDTH = R.register("border-right-width", BORDER_WIDTH, MEDIUM, false, true, true);
    public static final PropertyDef<Length> BORDER_BOTTOM_WIDTH = R.register("border-bottom-width", BORDER_WIDTH, MEDIUM, false, true, true);
    public static final PropertyDef<Length> BORDER_LEFT_WIDTH = R.register("border-left-width", BORDER_WIDTH, MEDIUM, false, true, true);
    public static final PropertyDef<BorderStyle> BORDER_TOP_STYLE = R.register("border-top-style", keyword(BorderStyle.class), BorderStyle.NONE, false, true, false);
    public static final PropertyDef<BorderStyle> BORDER_RIGHT_STYLE = R.register("border-right-style", keyword(BorderStyle.class), BorderStyle.NONE, false, true, false);
    public static final PropertyDef<BorderStyle> BORDER_BOTTOM_STYLE = R.register("border-bottom-style", keyword(BorderStyle.class), BorderStyle.NONE, false, true, false);
    public static final PropertyDef<BorderStyle> BORDER_LEFT_STYLE = R.register("border-left-style", keyword(BorderStyle.class), BorderStyle.NONE, false, true, false);
    /** No {@code currentcolor}: the initial value is white, which is {@code color}'s initial value. */
    public static final PropertyDef<Integer> BORDER_TOP_COLOR = R.register("border-top-color", color(), 0xFFFFFFFF, false, false, true);
    public static final PropertyDef<Integer> BORDER_RIGHT_COLOR = R.register("border-right-color", color(), 0xFFFFFFFF, false, false, true);
    public static final PropertyDef<Integer> BORDER_BOTTOM_COLOR = R.register("border-bottom-color", color(), 0xFFFFFFFF, false, false, true);
    public static final PropertyDef<Integer> BORDER_LEFT_COLOR = R.register("border-left-color", color(), 0xFFFFFFFF, false, false, true);
    public static final PropertyDef<Integer> BACKGROUND_COLOR = R.register("background-color", color(), 0x00000000, false, false, true);
    /** The image's resource location, or an empty string for {@code none}. */
    public static final PropertyDef<String> BACKGROUND_IMAGE = R.register("background-image", backgroundImage(), "", false, false, false);
    public static final PropertyDef<Length> BACKGROUND_POSITION_X =
            R.register("background-position-x", BACKGROUND_OFFSET, Length.percent(0), false, false, false);
    public static final PropertyDef<Length> BACKGROUND_POSITION_Y =
            R.register("background-position-y", BACKGROUND_OFFSET, Length.percent(0), false, false, false);
    public static final PropertyDef<BackgroundSize> BACKGROUND_SIZE =
            R.register("background-size", backgroundSize(), BackgroundSize.AUTO, false, false, false);
    public static final PropertyDef<BackgroundRepeat> BACKGROUND_REPEAT =
            R.register("background-repeat", keyword(BackgroundRepeat.class), BackgroundRepeat.REPEAT, false, false, false);
    public static final PropertyDef<BackgroundBlendMode> BACKGROUND_BLEND_MODE =
            R.register("background-blend-mode", keyword(BackgroundBlendMode.class), BackgroundBlendMode.NORMAL, false, false, false);
    public static final PropertyDef<Integer> COLOR = R.register("color", color(), 0xFFFFFFFF, true, false, true);
    /** The check mark of a checked checkbox and the filled track and thumb of a range input; no {@code auto}. */
    public static final PropertyDef<Integer> ACCENT_COLOR = R.register("accent-color", color(), 0xFF3C78D8, true, false, true);
    /** Aligns each line inside the box holding the text: a {@code block}, or a flex container's own text, whose box spans the container unless the flex layout shrinks it. */
    public static final PropertyDef<TextAlign> TEXT_ALIGN = R.register("text-align", keyword(TextAlign.class), TextAlign.START, true, false, false);
    public static final PropertyDef<WhiteSpace> WHITE_SPACE = R.register("white-space", keyword(WhiteSpace.class), WhiteSpace.NORMAL, true, true, false);
    public static final PropertyDef<TextOverflow> TEXT_OVERFLOW = R.register("text-overflow", keyword(TextOverflow.class), TextOverflow.CLIP, false, false, false);
    public static final PropertyDef<Length> FONT_SIZE = R.register("font-size", length(false, false, false), Length.px(9), true, true, true);
    /** Bold text is wider in the vanilla font, so the weight affects layout. */
    public static final PropertyDef<FontWeight> FONT_WEIGHT = R.register("font-weight", keyword(FontWeight.class), FontWeight.NORMAL, true, true, false);
    public static final PropertyDef<FontStyle> FONT_STYLE = R.register("font-style", keyword(FontStyle.class), FontStyle.NORMAL, true, false, false);
    public static final PropertyDef<TextDecoration> TEXT_DECORATION =
            R.register("text-decoration", keyword(TextDecoration.class), TextDecoration.NONE, false, false, false);
    /** One unblurred shadow under the text, in its own color; inherited and animatable as in CSS. */
    public static final PropertyDef<TextShadow> TEXT_SHADOW = R.register("text-shadow", textShadow(), TextShadow.NONE, true, false, true);
    public static final PropertyDef<Keywords.ClickSound> CLICK_SOUND =
            R.register("-ix-click-sound", keyword(Keywords.ClickSound.class), Keywords.ClickSound.AUTO, true, false, false);

    public static final PropertyDef<List<String>> TRANSITION_PROPERTY =
            R.register("transition-property", transitionProperty(R), List.of("all"), false, false, false);
    public static final PropertyDef<List<Float>> TRANSITION_DURATION =
            R.register("transition-duration", commaList(time(false)), List.of(0f), false, false, false);
    public static final PropertyDef<List<Easing>> TRANSITION_TIMING_FUNCTION =
            R.register("transition-timing-function", commaList(easing()), List.of(Easing.EASE), false, false, false);
    public static final PropertyDef<List<Float>> TRANSITION_DELAY =
            R.register("transition-delay", commaList(time(true)), List.of(0f), false, false, false);

    static {
        R.registerShorthand("margin", Shorthand.box(MARGIN), MARGIN_TOP, MARGIN_RIGHT, MARGIN_BOTTOM, MARGIN_LEFT);
        R.registerShorthand("padding", Shorthand.box(PADDING), PADDING_TOP, PADDING_RIGHT, PADDING_BOTTOM, PADDING_LEFT);
        R.registerShorthand("border-width", Shorthand.box(BORDER_WIDTH),
                BORDER_TOP_WIDTH, BORDER_RIGHT_WIDTH, BORDER_BOTTOM_WIDTH, BORDER_LEFT_WIDTH);
        R.registerShorthand("border-style", Shorthand.box(keyword(BorderStyle.class)),
                BORDER_TOP_STYLE, BORDER_RIGHT_STYLE, BORDER_BOTTOM_STYLE, BORDER_LEFT_STYLE);
        R.registerShorthand("border-color", Shorthand.box(color()),
                BORDER_TOP_COLOR, BORDER_RIGHT_COLOR, BORDER_BOTTOM_COLOR, BORDER_LEFT_COLOR);
        R.registerShorthand("border", border(4), BORDER_TOP_WIDTH, BORDER_RIGHT_WIDTH, BORDER_BOTTOM_WIDTH, BORDER_LEFT_WIDTH,
                BORDER_TOP_STYLE, BORDER_RIGHT_STYLE, BORDER_BOTTOM_STYLE, BORDER_LEFT_STYLE,
                BORDER_TOP_COLOR, BORDER_RIGHT_COLOR, BORDER_BOTTOM_COLOR, BORDER_LEFT_COLOR);
        R.registerShorthand("border-top", border(1), BORDER_TOP_WIDTH, BORDER_TOP_STYLE, BORDER_TOP_COLOR);
        R.registerShorthand("border-right", border(1), BORDER_RIGHT_WIDTH, BORDER_RIGHT_STYLE, BORDER_RIGHT_COLOR);
        R.registerShorthand("border-bottom", border(1), BORDER_BOTTOM_WIDTH, BORDER_BOTTOM_STYLE, BORDER_BOTTOM_COLOR);
        R.registerShorthand("border-left", border(1), BORDER_LEFT_WIDTH, BORDER_LEFT_STYLE, BORDER_LEFT_COLOR);
        R.registerShorthand("gap", Shorthand.pair(GAP), ROW_GAP, COLUMN_GAP);
        R.registerShorthand("background-position", Shorthand.position(BACKGROUND_OFFSET),
                BACKGROUND_POSITION_X, BACKGROUND_POSITION_Y);
        R.registerShorthand("transition", Shorthand.transition(R),
                TRANSITION_PROPERTY, TRANSITION_DURATION, TRANSITION_TIMING_FUNCTION, TRANSITION_DELAY);
        R.freeze();
    }

    private static Shorthand.Expander border(int sides) {
        return Shorthand.border(BORDER_WIDTH, keyword(BorderStyle.class), color(), MEDIUM, BorderStyle.NONE, 0xFFFFFFFF, sides);
    }
}
