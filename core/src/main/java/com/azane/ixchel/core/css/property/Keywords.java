package com.azane.ixchel.core.css.property;

/** Keyword-valued properties. Enum constant = CSS keyword upper-cased with {@code -} as {@code _}. */
public final class Keywords {
    private Keywords() {
    }

    /**
     * {@code display}. A flex item treats {@code inline} and {@code inline-block} as {@code block}, as CSS
     * blockifies flex items; inside an inline formatting context an {@code inline-block} is an atomic box
     * whose own content is laid out as a {@code block}'s, at its max-content width. {@code block}
     * holds an inline formatting context: its children must be text, {@code inline} or
     * {@code inline-block} elements, or {@code none}. {@code flex} is the initial value, so an element
     * without a {@code display} is a flex container; {@code <span>}, {@code <a>}, {@code <img>} and native
     * elements default to {@code inline}. {@code <img>} and drawn elements are replaced: their own
     * children, if any, are laid out as in a flex container whatever their {@code display}. {@code grid}
     * is a grid container; like a flex container it blockifies its items.
     */
    public enum Display { FLEX, NONE, BLOCK, INLINE, INLINE_BLOCK, GRID }

    public enum Position { RELATIVE, ABSOLUTE }

    public enum FlexDirection { ROW, ROW_REVERSE, COLUMN, COLUMN_REVERSE }

    public enum FlexWrap { NOWRAP, WRAP, WRAP_REVERSE }

    /** {@code align-items} and {@code justify-items}; {@code align-self}/{@code justify-self} add {@code auto} via {@link AlignSelf}. */
    public enum AlignItems { START, END, FLEX_START, FLEX_END, CENTER, BASELINE, STRETCH }

    public enum AlignSelf { AUTO, START, END, FLEX_START, FLEX_END, CENTER, BASELINE, STRETCH }

    public enum JustifyContent { START, END, FLEX_START, FLEX_END, CENTER, STRETCH, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }

    /** {@code align-content}: {@code normal} stretches flex lines and leaves grid tracks at the start. */
    public enum AlignContent { NORMAL, START, END, FLEX_START, FLEX_END, CENTER, STRETCH, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }

    public enum GridAutoFlow { ROW, COLUMN, ROW_DENSE, COLUMN_DENSE }

    /**
     * Every value but {@code visible} makes a scroll container: it clips its descendants to its padding
     * box, its automatic minimum size as a flex
     * item is 0, and its content can be moved by a vertical scroll offset ({@link
     * com.azane.ixchel.core.dom.Element#scrollTop()}). The wheel scrolls only {@code auto} and
     * {@code scroll}; {@code hidden} scrolls programmatically. {@code scroll} always draws a scrollbar
     * track and thumb, {@code auto} draws a thumb only while the content overflows. Content that
     * overflows horizontally is clipped but cannot be scrolled.
     */
    public enum Overflow { VISIBLE, HIDDEN, SCROLL, AUTO }

    /** {@code background-repeat}, one keyword per layer for both axes; {@code space} and {@code round} are not supported. */
    public enum BackgroundRepeat { REPEAT, REPEAT_X, REPEAT_Y, NO_REPEAT }

    /**
     * {@code background-clip}, one per layer: the box a layer (the bottom one's for the color) is
     * painted in, or {@code text}, all layers and the color only where the element's and its
     * descendants' text is drawn.
     */
    public enum BackgroundClip { BORDER_BOX, PADDING_BOX, CONTENT_BOX, TEXT }

    /** {@code mask-mode}, one per layer: both take the alpha of an image or gradient; {@code luminance} is not supported. */
    public enum MaskMode { MATCH_SOURCE, ALPHA }

    /** {@code mask-composite}, one per layer: only {@code add}, mask layers painted over each other. */
    public enum MaskComposite { ADD }

    /**
     * {@code background-blend-mode}, one keyword per layer. Only the bottom layer may {@code multiply}:
     * it is then an image tinted with the background color (the image's own alpha is kept) and the
     * color is not painted under it, so where the image is transparent the parent shows through; CSS
     * would show the color there. Other modes are not supported.
     */
    public enum BackgroundBlendMode { NORMAL, MULTIPLY }

    /** {@code mix-blend-mode}: the modes fixed-function blending composites; see {@code EffectSyntaxes.mixBlendMode}. */
    public enum MixBlendMode { NORMAL, MULTIPLY, SCREEN, PLUS_LIGHTER, DARKEN, LIGHTEN }

    /** {@code border-image-repeat}, per axis; {@code space} is not supported. */
    public enum ImageTiling { STRETCH, REPEAT, ROUND }

    /**
     * {@code image-rendering}: how images are scaled. {@code auto} keeps the texture's own filtering,
     * which for Minecraft's GUI textures is nearest-neighbour; {@code smooth} filters linearly;
     * {@code pixelated} and {@code crisp-edges} take the nearest texel.
     */
    public enum ImageRendering { AUTO, SMOOTH, PIXELATED, CRISP_EDGES }

    /**
     * {@code corner-shape} per corner (CSS Borders 4), inside the corner's {@code border-radius} box:
     * {@code round} the ellipse, {@code bevel} a straight cut, {@code notch} the whole box cut away,
     * {@code scoop} a concave quarter ellipse, {@code square} no curve. {@code superellipse()} is not supported.
     */
    public enum CornerShape { ROUND, BEVEL, NOTCH, SCOOP, SQUARE }

    /**
     * {@code shape-rendering}, inherited (the SVG property): {@code crisp-edges} bakes the masks of
     * rounded and shaped corners and of {@code box-shadow} at one texel per GUI pixel without
     * antialiasing, so curves step and shadows fade cell by cell.
     */
    public enum ShapeRendering { AUTO, CRISP_EDGES }

    /**
     * {@code content}, read only on {@code ::before} and {@code ::after}: {@link #EMPTY} is the empty
     * string {@code ""}, which generates the box; {@code normal} computes to {@code none} there.
     * Text is not supported: a pseudo-element is decoration, not a place for words that bypass translation.
     */
    public enum Content { NORMAL, NONE, EMPTY }

    /**
     * {@code border-style} and {@code outline-style}; {@code none} makes the used width 0, as in CSS.
     * Other styles are not supported.
     */
    public enum BorderStyle { NONE, SOLID }

    /** {@code text-align}; text runs left to right, so {@code start} is {@code left} and {@code end} is {@code right}. */
    public enum TextAlign { START, END, LEFT, RIGHT, CENTER }

    /** {@code white-space}; whitespace is always collapsed, so only wrapping differs. */
    public enum WhiteSpace { NORMAL, NOWRAP }

    /**
     * {@code text-overflow} on a box holding text (a {@code block}, or a flex container for its own text
     * children): with {@code overflow} other than {@code visible}, a line that runs past the box's padding
     * box ends in an ellipsis.
     */
    public enum TextOverflow { CLIP, ELLIPSIS }

    /** {@code font-weight}; numeric weights are not supported. */
    public enum FontWeight { NORMAL, BOLD }

    /** {@code font-style}; {@code oblique} is not supported. */
    public enum FontStyle { NORMAL, ITALIC }

    /**
     * {@code text-transform}, inherited: case mapping (root locale) of the text laid out in the document,
     * translations and bound {@code Component}s included, before it is measured. A form control's own
     * text (an input's value and placeholder, a select's label) is not transformed.
     */
    public enum TextTransform { NONE, UPPERCASE, LOWERCASE }

    /**
     * {@code text-decoration}, one line keyword; color, style and thickness are not supported. As in
     * CSS it is not inherited but drawn through the text of the element's inline descendants.
     */
    public enum TextDecoration { NONE, UNDERLINE, LINE_THROUGH }

    /** {@code scrollbar-width}: a 4px or 2px bar, or none (the element still scrolls by wheel and code). */
    public enum ScrollbarWidth { AUTO, THIN, NONE }

    /**
     * {@code -ix-click-sound}, inherited: whether a control plays the host's click sound
     * ({@link com.azane.ixchel.core.event.ClickSound}). {@code none} silences it, e.g. for a button whose
     * action plays its own sound.
     */
    public enum ClickSound { AUTO, NONE }

    /** {@code animation-direction}: which way each iteration runs. */
    public enum AnimationDirection { NORMAL, REVERSE, ALTERNATE, ALTERNATE_REVERSE }

    /** {@code animation-fill-mode}: whether the first keyframe applies during the delay and the last after the end. */
    public enum AnimationFillMode { NONE, FORWARDS, BACKWARDS, BOTH }

    /** {@code animation-play-state}: a paused animation holds its current time. */
    public enum AnimationPlayState { RUNNING, PAUSED }
}
