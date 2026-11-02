package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.css.property.BackgroundSize;
import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.property.ScrollbarColor;
import com.azane.ixchel.core.css.property.TextShadow;
import com.azane.ixchel.core.css.property.Transform;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.layout.Scrollbar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the laid-out tree into draw commands in document order (later commands paint on top): per
 * element the background color, the background image, the border, the content; with
 * {@code background-blend-mode: multiply} the color tints the image instead of painting under it. A
 * background image becomes one {@link DrawCommand.Image} per visible tile, already clipped to the
 * border box, so the host only draws texture regions. A border side is one {@link DrawCommand.Rect} ({@code solid}
 * only); the top and bottom sides cover the corners.
 *
 * <p>{@code opacity} multiplies the alpha of every color and image tint in the element's subtree, so
 * where two of its descendants overlap they blend with each other (a browser would flatten the group
 * first). Native elements ({@link DrawCommand.Custom}) are drawn by the host and do not fade. An
 * element with opacity 0 paints nothing but still takes pointer events, as in CSS.
 *
 * <p>A scroll container ({@code overflow} other than {@code visible}) clips its descendants to its
 * padding box and paints them {@link Element#scrollTop()} higher, then its {@link Scrollbar} over
 * them in {@code scrollbar-color}.
 *
 * <p>An {@code <input>} paints its value on one line, clipped to its content box and centered in it
 * vertically, scrolled sideways so the caret stays in view; empty, its {@code placeholder} at half
 * the alpha of {@code color}. Focused, the selection is a fixed translucent blue under the text and
 * the caret a 1px line of {@code color}, drawn while {@linkplain #setCaretVisible the blink} shows it.
 * A checked checkbox fills its content box with {@code accent-color}. A range draws a 2px track
 * across its content box, filled with {@code accent-color} up to the value, and a
 * {@value FormControl#THUMB_WIDTH}px thumb of the content box's height in {@code accent-color}. A
 * {@code <select>} draws its selected option's label like an input's value and a small down arrow of
 * {@code color} at the right of its content box; its options are not painted in place.
 *
 * <p>After the tree, each {@link TopLayer} entry in order, with no clip and full opacity: an open
 * dialog over a translucent black backdrop covering the viewport, the options of an open select.
 * Neither is painted where it sits in the tree.
 *
 * <p>{@code transform} wraps the element's commands in a {@link DrawCommand.PushTransform}; layout
 * and scroll offsets are not affected. Clips inside a transformed subtree are rectangles in its local
 * coordinates; under a rotation the host clips to their bounding box.
 */
public final class Painter implements PaintFacet.Context {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Bounds the commands one background may emit, so a tiny repeated tile cannot stall a frame. */
    private static final int MAX_TILES = 16384;
    private static final int BACKDROP_COLOR = 0x80000000;

    private final LayoutEngine layout;
    private final ImageMeasurer images;
    private final Diagnostics diag;
    private final Set<String> reported = new HashSet<>();
    /** Product of the opacities of the element being painted and its ancestors. */
    private float alpha = 1;
    private boolean caretVisible;

    /** @param diag receives paint-time errors (an image that cannot be measured), each reported once */
    public Painter(LayoutEngine layout, ImageMeasurer images, Diagnostics diag) {
        this.layout = layout;
        this.images = images;
        this.diag = diag;
    }

    /** Whether the focused input's caret is drawn: the visible half of its blink. */
    public boolean caretVisible() {
        return caretVisible;
    }

    public void setCaretVisible(boolean visible) {
        caretVisible = visible;
    }

    @Override
    public float textWidth(String text, float size) { return layout.textWidth(text, size); }

    @Override
    public float lineHeight(float size) { return layout.lineHeight(size); }

    /** Clears {@code out} and paints the children of the implicit root into it, then the top layer's entries. */
    public void paint(Element root, TopLayer top, DisplayList out) {
        out.clear();
        paintChildren(root, 0, out);
        List<Element> entries = top.entries();
        for (int i = 0; i < entries.size(); i++) {
            Element entry = entries.get(i);
            alpha = 1;
            LayerFacet presentation = entry.type().facet(LayerFacet.KEY);
            if (presentation.modal()) {
                rect(out, 0, 0, root.box().width(), root.box().height(), BACKDROP_COLOR);
            }
            for (int j = 0; j < presentation.contentCount(entry); j++) {
                Element child = presentation.contentAt(entry, j);
                if (child != null) paintElement(child, 0, out);
            }
        }
        root.clearRepaint();
    }

    /** Clears {@code out} and paints {@code top}'s element at the pointer into it; empty when none is shown. */
    public void paintAtPointer(TopLayer top, DisplayList out) {
        out.clear();
        if (top.atPointer() != null) {
            alpha = 1;
            paintElement(top.atPointer(), 0, out);
        }
    }

    /** {@code dy}: how far scroll containers above move these children up. Colors are multiplied by {@link #alpha}. */
    private void paintChildren(Element e, float dy, DisplayList out) {
        // Indexed: the unmodifiable view allocates two objects per for-each, on every repaint.
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            Node c = children.get(i);
            if (c instanceof Element child) {
                if (!child.rendersInTopLayer()) {
                    paintElement(child, dy, out);
                }
            } else {
                paintText((TextNode) c, e.computed(), dy, out);
            }
        }
    }

    private void paintElement(Element e, float dy, DisplayList out) {
        ComputedStyle cs = e.computed();
        float opacity = cs.get(CssProperties.OPACITY);
        if (cs.get(CssProperties.DISPLAY) == Keywords.Display.NONE || opacity <= 0) {
            return;
        }
        float outer = alpha;
        alpha = outer * Math.min(1, opacity);
        LayoutBox b = e.box();
        Affine m = Transform.of(cs, b.x(), b.y() - dy, b.width(), b.height());
        if (m != null) {
            out.add(new DrawCommand.PushTransform(m));
        }
        paintBox(e, cs, dy, out);
        if (m != null) {
            out.add(DrawCommand.PopTransform.INSTANCE);
        }
        alpha = outer;
    }

    private void paintBox(Element e, ComputedStyle cs, float dy, DisplayList out) {
        LayoutBox b = e.box();
        float y = b.y() - dy;
        int bg = cs.get(CssProperties.BACKGROUND_COLOR);
        boolean multiply = cs.get(CssProperties.BACKGROUND_BLEND_MODE) == Keywords.BackgroundBlendMode.MULTIPLY
                && !cs.get(CssProperties.BACKGROUND_IMAGE).isEmpty();
        if (multiply) {
            paintBackgroundImage(e, cs, b, y, 0xFF000000 | bg, out);
        } else {
            rect(out, b.x(), y, b.width(), b.height(), bg);
            paintBackgroundImage(e, cs, b, y, OPAQUE_WHITE, out);
        }
        paintBorder(cs, b, y, out);
        PaintFacet content = e.type().facet(PaintFacet.KEY);
        content.paint(e, y, out, this);
        if (!content.paintChildren()) return;
        Keywords.Overflow overflow = cs.get(CssProperties.OVERFLOW);
        if (overflow == Keywords.Overflow.VISIBLE) {
            paintChildren(e, dy, out);
            return;
        }
        // CSS clips overflow to the padding box.
        float clipX = b.x() + b.borderLeft();
        float clipY = y + b.borderTop();
        out.add(new DrawCommand.PushClip(clipX, clipY, b.width() - b.borderLeft() - b.borderRight(),
                b.height() - b.borderTop() - b.borderBottom()));
        paintChildren(e, dy + e.scrollTop(), out);
        paintScrollbar(e, cs, dy, out);
        out.add(DrawCommand.PopClip.INSTANCE);
    }


    /** One line of a control's text, over the shadow of {@code cs}'s {@code text-shadow}. */
    public void text(DisplayList out, ComputedStyle cs, float x, float y, float size, int color, String text) {
        TextShadow shadow = cs.get(CssProperties.TEXT_SHADOW);
        int s = fade(shadow.color());
        if (s >>> 24 != 0) {
            out.add(new DrawCommand.Text(x + shadow.x(), y + shadow.y(), size, s, text, 0));
        }
        int c = fade(color);
        if (c >>> 24 != 0) {
            out.add(new DrawCommand.Text(x, y, size, c, text, 0));
        }
    }

    /** Each side a rectangle inside the border box; top and bottom span the corners. */
    private void paintBorder(ComputedStyle cs, LayoutBox b, float y, DisplayList out) {
        float t = b.borderTop();
        float r = b.borderRight();
        float bt = b.borderBottom();
        float l = b.borderLeft();
        if (t > 0) {
            rect(out, b.x(), y, b.width(), t, cs.get(CssProperties.BORDER_TOP_COLOR));
        }
        if (bt > 0) {
            rect(out, b.x(), y + b.height() - bt, b.width(), bt, cs.get(CssProperties.BORDER_BOTTOM_COLOR));
        }
        float middle = b.height() - t - bt;
        if (middle <= 0) {
            return;
        }
        if (l > 0) {
            rect(out, b.x(), y + t, l, middle, cs.get(CssProperties.BORDER_LEFT_COLOR));
        }
        if (r > 0) {
            rect(out, b.x() + b.width() - r, y + t, r, middle, cs.get(CssProperties.BORDER_RIGHT_COLOR));
        }
    }

    public void rect(DisplayList out, float x, float y, float w, float h, int color) {
        int c = fade(color);
        if (c >>> 24 != 0) {
            out.add(new DrawCommand.Rect(x, y, w, h, c));
        }
    }

    /** {@code color} with its alpha multiplied by the opacity of the element being painted and its ancestors. */
    public int fade(int color) {
        if (alpha >= 1) {
            return color;
        }
        return Math.round((color >>> 24) * alpha) << 24 | color & 0xFFFFFF;
    }

    /** {@code dy}: the scroll offset of the ancestors, as {@link #paintBox} takes it. */
    private void paintScrollbar(Element e, ComputedStyle cs, float dy, DisplayList out) {
        Scrollbar bar = Scrollbar.of(e);
        if (bar == null) {
            return;
        }
        ScrollbarColor colors = cs.get(CssProperties.SCROLLBAR_COLOR);
        if (bar.track()) {
            rect(out, bar.x(), bar.y() - dy, bar.width(), bar.height(), colors.track());
        }
        rect(out, bar.x(), bar.thumbY() - dy, bar.width(), bar.thumbHeight(), colors.thumb());
    }

    /**
     * CSS Backgrounds 3 with the initial {@code background-origin} and {@code background-clip}: the
     * image is sized and positioned in the padding box and tiles out to the border box edges.
     * {@code top} is the border box top after scrolling; {@code tint} multiplies the image's texels.
     */
    private void paintBackgroundImage(Element e, ComputedStyle cs, LayoutBox b, float top, int tint, DisplayList out) {
        String src = cs.get(CssProperties.BACKGROUND_IMAGE);
        float areaW = b.width() - b.borderLeft() - b.borderRight();
        float areaH = b.height() - b.borderTop() - b.borderBottom();
        if (src.isEmpty() || areaW <= 0 || areaH <= 0) {
            return;
        }
        BackgroundSize size = cs.get(CssProperties.BACKGROUND_SIZE);
        float w = side(size.width(), areaW);
        float h = side(size.height(), areaH);
        if (Float.isNaN(w) || Float.isNaN(h)) {
            ImageMeasurer.Size natural = images.naturalSize(src);
            if (natural == null) {
                report(e, PaintCode.BACKGROUND_IMAGE_UNSIZED, "cannot load background image '" + src + "' to size it (background-size: auto)");
                return;
            }
            if (natural.width() <= 0 || natural.height() <= 0) {
                return;
            }
            if (Float.isNaN(w) && Float.isNaN(h)) {
                w = natural.width();
                h = natural.height();
            } else if (Float.isNaN(w)) {
                w = h * natural.width() / natural.height();
            } else {
                h = w * natural.height() / natural.width();
            }
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        Keywords.BackgroundRepeat repeat = cs.get(CssProperties.BACKGROUND_REPEAT);
        boolean repeatX = repeat == Keywords.BackgroundRepeat.REPEAT || repeat == Keywords.BackgroundRepeat.REPEAT_X;
        boolean repeatY = repeat == Keywords.BackgroundRepeat.REPEAT || repeat == Keywords.BackgroundRepeat.REPEAT_Y;
        float left = b.x();
        float right = left + b.width();
        float bottom = top + b.height();
        float x0 = left + b.borderLeft() + offset(cs.get(CssProperties.BACKGROUND_POSITION_X), areaW - w);
        float y0 = top + b.borderTop() + offset(cs.get(CssProperties.BACKGROUND_POSITION_Y), areaH - h);
        int columns = 1;
        int rows = 1;
        if (repeatX) {
            x0 -= w * (float) Math.ceil((x0 - left) / w);
            columns = (int) Math.ceil((right - x0) / w);
        }
        if (repeatY) {
            y0 -= h * (float) Math.ceil((y0 - top) / h);
            rows = (int) Math.ceil((bottom - y0) / h);
        }
        if ((long) columns * rows > MAX_TILES) {
            report(e, PaintCode.BACKGROUND_TOO_MANY_TILES, "background image '" + src + "' would repeat " + (long) columns * rows
                    + " times (at most " + MAX_TILES + "); use a larger background-size");
            return;
        }
        for (int row = 0; row < rows; row++) {
            float ty = y0 + row * h;
            float cy0 = Math.max(ty, top);
            float cy1 = Math.min(ty + h, bottom);
            if (cy1 <= cy0) {
                continue;
            }
            for (int col = 0; col < columns; col++) {
                float tx = x0 + col * w;
                float cx0 = Math.max(tx, left);
                float cx1 = Math.min(tx + w, right);
                if (cx1 <= cx0) {
                    continue;
                }
                out.add(new DrawCommand.Image(cx0, cy0, cx1 - cx0, cy1 - cy0, src,
                        (cx0 - tx) / w, (cy0 - ty) / h, (cx1 - tx) / w, (cy1 - ty) / h, fade(tint)));
            }
        }
    }

    /** A {@code background-size} side in pixels; NaN for {@code auto}. */
    private static float side(Length l, float area) {
        return switch (l.unit()) {
            case PX -> l.value();
            case PERCENT -> area * l.value() / 100;
            case AUTO -> Float.NaN;
        };
    }

    /** A {@code background-position} side: a percentage aligns that point of the image with that point of the box. */
    private static float offset(Length l, float free) {
        return l.unit() == Length.Unit.PERCENT ? free * l.value() / 100 : l.value();
    }

    private void report(Element e, PaintCode code, String message) {
        if (reported.add(e.pos() + ": " + message)) {
            diag.report(code, e.pos(), message);
        }
    }

    /**
     * Draws a text node's fragments as layout placed them, in its parent's color unless a styled span
     * has its own; bold and italic come from the parent, decorations from every element up to and
     * including the box holding the paragraph, and a span adds its own.
     */
    private void paintText(TextNode t, ComputedStyle parent, float dy, DisplayList out) {
        List<LayoutEngine.TextFragment> fragments = layout.fragments(t);
        if (fragments.isEmpty()) {
            return;
        }
        int flags = 0;
        if (parent.get(CssProperties.FONT_WEIGHT) == Keywords.FontWeight.BOLD) {
            flags |= StyledText.BOLD;
        }
        if (parent.get(CssProperties.FONT_STYLE) == Keywords.FontStyle.ITALIC) {
            flags |= StyledText.ITALIC;
        }
        for (Element a = t.parent(); a != null; a = a.parent()) {
            flags |= switch (a.computed().get(CssProperties.TEXT_DECORATION)) {
                case NONE -> 0;
                case UNDERLINE -> StyledText.UNDERLINE;
                case LINE_THROUGH -> StyledText.STRIKETHROUGH;
            };
            if (a.box().fragments() == null) {
                break; // the box holding the paragraph; inline elements have line fragments
            }
        }
        int base = parent.get(CssProperties.COLOR);
        TextShadow shadow = parent.get(CssProperties.TEXT_SHADOW);
        int shadowColor = fade(shadow.color());
        StyledText styled = t.styled();
        for (int i = 0; i < fragments.size(); i++) {
            LayoutEngine.TextFragment f = fragments.get(i);
            int color = base;
            int own = flags;
            if (f.span() >= 0) {
                StyledText.Span s = styled.spans().get(f.span());
                if (s.color() != 0) {
                    color = s.color();
                }
                own |= s.flags();
            }
            color = fade(color);
            if (shadowColor >>> 24 != 0) {
                out.add(new DrawCommand.Text(f.x() + shadow.x(), f.y() - dy + shadow.y(), f.size(), shadowColor, f.text(), own));
            }
            if (color >>> 24 != 0) {
                out.add(new DrawCommand.Text(f.x(), f.y() - dy, f.size(), color, f.text(), own));
            }
        }
    }
}
