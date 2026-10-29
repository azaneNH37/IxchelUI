package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.BackgroundSize;
import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextField;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.text.TextShaper;

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
 * padding box and paints them {@link Element#scrollTop()} higher. {@code overflow: scroll} always draws
 * a 4px scrollbar track along the padding box's right edge with a thumb on it; {@code auto}
 * draws only the thumb, and only while the content overflows. The scrollbar is drawn over the
 * content (no space is reserved for it) in fixed colors and cannot be dragged.
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
 */
public final class Painter {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Bounds the commands one background may emit, so a tiny repeated tile cannot stall a frame. */
    private static final int MAX_TILES = 16384;
    private static final float SCROLLBAR_WIDTH = 4;
    private static final float MIN_THUMB = 8;
    private static final int TRACK_COLOR = 0x80000000;
    private static final int THUMB_COLOR = 0xFFA0A0A0;
    private static final int SELECTION_COLOR = 0x803399FF;
    private static final int TRACK_COLOR_RANGE = 0xFF505050;
    private static final int BACKDROP_COLOR = 0x80000000;

    private final LayoutEngine layout;
    private final TagRegistry tags;
    private final ImageMeasurer images;
    private final Diagnostics diag;
    private final Set<String> reported = new HashSet<>();
    /** Product of the opacities of the element being painted and its ancestors. */
    private float alpha = 1;
    private boolean caretVisible;

    /** @param diag receives paint-time errors (an image that cannot be measured), each reported once */
    public Painter(LayoutEngine layout, TagRegistry tags, ImageMeasurer images, Diagnostics diag) {
        this.layout = layout;
        this.tags = tags;
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

    /** Clears {@code out} and paints the children of the implicit root into it, then the top layer. */
    public void paint(Element root, TopLayer top, DisplayList out) {
        out.clear();
        paintChildren(root, 0, out);
        List<Element> entries = top.entries();
        for (int i = 0; i < entries.size(); i++) {
            Element entry = entries.get(i);
            alpha = 1;
            if (entry.tag().equals("dialog")) {
                rect(out, 0, 0, root.box().width(), root.box().height(), BACKDROP_COLOR);
                paintElement(entry, 0, out);
            } else {
                List<Node> options = entry.children();
                for (int j = 0; j < options.size(); j++) {
                    if (options.get(j) instanceof Element o) {
                        paintElement(o, 0, out);
                    }
                }
            }
        }
        root.clearRepaint();
    }

    /** {@code dy}: how far scroll containers above move these children up. Colors are multiplied by {@link #alpha}. */
    private void paintChildren(Element e, float dy, DisplayList out) {
        // Indexed: the unmodifiable view allocates two objects per for-each, on every repaint.
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            Node c = children.get(i);
            if (c instanceof Element child) {
                if (!child.tag().equals("dialog")) {
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
        paintBox(e, cs, dy, out);
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
        FormControl control = e.control();
        if (control != null) {
            switch (control.kind()) {
                case TEXT, NUMBER -> paintInput(e, cs, b, y, out);
                case CHECKBOX -> paintCheckbox(e, cs, b, y, out);
                case RANGE -> paintRange(control, cs, b, y, out);
                case SELECT -> {
                    paintSelect(control, cs, b, y, out);
                    return;
                }
            }
        }
        if (e.tag().equals("img")) {
            out.add(new DrawCommand.Image(b.x(), y, b.width(), b.height(), e.attribute("src"), 0, 0, 1, 1, fade(OPAQUE_WHITE)));
        } else if (e.payload() != null && tags.payloadAttribute(e.tag()) != null) {
            out.add(new DrawCommand.Custom(e.tag(), e.payload(), b.x(), y, b.width(), b.height()));
        }
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
        paintScrollbar(e, overflow, clipY, out);
        out.add(DrawCommand.PopClip.INSTANCE);
    }

    private void paintInput(Element e, ComputedStyle cs, LayoutBox b, float y, DisplayList out) {
        float cx = b.x() + b.borderLeft() + b.paddingLeft();
        float cy = y + b.borderTop() + b.paddingTop();
        float cw = b.width() - b.borderLeft() - b.paddingLeft() - b.paddingRight() - b.borderRight();
        float ch = b.height() - b.borderTop() - b.paddingTop() - b.paddingBottom() - b.borderBottom();
        if (cw <= 0 || ch <= 0) {
            return;
        }
        TextField f = e.textField();
        String v = f.value();
        float size = cs.get(CssProperties.FONT_SIZE).value();
        float lh = layout.lineHeight(size);
        int color = cs.get(CssProperties.COLOR);
        boolean focused = e.hasState(Element.FOCUS);
        float caretX = layout.textWidth(v.substring(0, f.caret()), size);
        // Room for the 1px caret after the last character; no empty space past the end while scrolled.
        float scroll = Math.min(f.scrollX(), Math.max(0, layout.textWidth(v, size) + 1 - cw));
        if (caretX + 1 - scroll > cw) {
            scroll = caretX + 1 - cw;
        }
        scroll = Math.round(Math.max(0, Math.min(scroll, caretX)));
        f.setScrollX(scroll);
        float tx = cx - scroll;
        float ty = cy + Math.round((ch - lh) / 2);
        out.add(new DrawCommand.PushClip(cx, cy, cw, ch));
        String placeholder = e.attribute("placeholder");
        if (!v.isEmpty()) {
            if (focused && f.hasSelection()) {
                float x0 = Math.round(tx + layout.textWidth(v.substring(0, f.selectionStart()), size));
                float x1 = Math.round(tx + layout.textWidth(v.substring(0, f.selectionEnd()), size));
                rect(out, x0, ty, x1 - x0, lh, SELECTION_COLOR);
            }
            text(out, tx, ty, size, color, v);
        } else if (placeholder != null && !placeholder.isEmpty()) {
            text(out, tx, ty, size, Math.round((color >>> 24) * 0.5f) << 24 | color & 0xFFFFFF, placeholder);
        }
        if (focused && caretVisible) {
            rect(out, Math.round(tx + caretX), ty, 1, lh, color);
        }
        out.add(DrawCommand.PopClip.INSTANCE);
    }

    private void paintCheckbox(Element e, ComputedStyle cs, LayoutBox b, float y, DisplayList out) {
        if (e.control().checked()) {
            rect(out, b.x() + b.borderLeft() + b.paddingLeft(), y + b.borderTop() + b.paddingTop(),
                    contentWidth(b), contentHeight(b), cs.get(CssProperties.ACCENT_COLOR));
        }
    }

    private void paintRange(FormControl c, ComputedStyle cs, LayoutBox b, float y, DisplayList out) {
        float cx = b.x() + b.borderLeft() + b.paddingLeft();
        float cy = y + b.borderTop() + b.paddingTop();
        float cw = contentWidth(b);
        float ch = contentHeight(b);
        if (cw <= FormControl.THUMB_WIDTH || ch <= 0) {
            return;
        }
        int accent = cs.get(CssProperties.ACCENT_COLOR);
        float trackY = cy + Math.round((ch - 2) / 2);
        float thumbX = cx + Math.round(c.fraction() * (cw - FormControl.THUMB_WIDTH));
        rect(out, cx, trackY, cw, 2, TRACK_COLOR_RANGE);
        rect(out, cx, trackY, thumbX - cx, 2, accent);
        rect(out, thumbX, cy, FormControl.THUMB_WIDTH, ch, accent);
    }

    private void paintSelect(FormControl c, ComputedStyle cs, LayoutBox b, float y, DisplayList out) {
        float cx = b.x() + b.borderLeft() + b.paddingLeft();
        float cy = y + b.borderTop() + b.paddingTop();
        float cw = contentWidth(b);
        float ch = contentHeight(b);
        if (cw <= 0 || ch <= 0) {
            return;
        }
        float size = cs.get(CssProperties.FONT_SIZE).value();
        float lh = layout.lineHeight(size);
        int color = cs.get(CssProperties.COLOR);
        float ty = cy + Math.round((ch - lh) / 2);
        float labelWidth = Math.max(0, cw - LayoutEngine.SELECT_ARROW_SPACE);
        out.add(new DrawCommand.PushClip(cx, cy, labelWidth, ch));
        text(out, cx, ty, size, color, c.label());
        out.add(DrawCommand.PopClip.INSTANCE);
        // A 5px-wide down arrow: rows 5, 3, 1 wide, centered in the reserved space.
        float ax = cx + cw - LayoutEngine.SELECT_ARROW_SPACE + 3;
        float ay = cy + Math.round((ch - 3) / 2);
        for (int row = 0; row < 3; row++) {
            rect(out, ax + row, ay + row, 5 - 2 * row, 1, color);
        }
    }

    private static float contentWidth(LayoutBox b) {
        return b.width() - b.borderLeft() - b.paddingLeft() - b.paddingRight() - b.borderRight();
    }

    private static float contentHeight(LayoutBox b) {
        return b.height() - b.borderTop() - b.paddingTop() - b.paddingBottom() - b.borderBottom();
    }

    private void text(DisplayList out, float x, float y, float size, int color, String text) {
        int c = fade(color);
        if (c >>> 24 != 0) {
            out.add(new DrawCommand.Text(x, y, size, c, text));
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

    private void rect(DisplayList out, float x, float y, float w, float h, int color) {
        int c = fade(color);
        if (c >>> 24 != 0) {
            out.add(new DrawCommand.Rect(x, y, w, h, c));
        }
    }

    /** {@code color} with its alpha multiplied by the opacity of the element being painted and its ancestors. */
    private int fade(int color) {
        if (alpha >= 1) {
            return color;
        }
        return Math.round((color >>> 24) * alpha) << 24 | color & 0xFFFFFF;
    }

    /** The scrollbar runs along the right edge of the padding box; {@code y} is its top after scrolling. */
    private void paintScrollbar(Element e, Keywords.Overflow overflow, float y, DisplayList out) {
        float max = e.maxScrollTop();
        boolean track = overflow == Keywords.Overflow.SCROLL;
        if (!track && (overflow != Keywords.Overflow.AUTO || max <= 0)) {
            return;
        }
        LayoutBox b = e.box();
        float h = b.height() - b.borderTop() - b.borderBottom();
        float x = b.x() + b.width() - b.borderRight() - SCROLLBAR_WIDTH;
        if (track) {
            rect(out, x, y, SCROLLBAR_WIDTH, h, TRACK_COLOR);
        }
        float thumb = Math.min(h, Math.max(MIN_THUMB, Math.round(h * h / (h + max))));
        float offset = max <= 0 ? 0 : Math.round((h - thumb) * e.scrollTop() / max);
        rect(out, x, y + offset, SCROLLBAR_WIDTH, thumb, THUMB_COLOR);
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
                report(e, "cannot load background image '" + src + "' to size it (background-size: auto)");
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
            report(e, "background image '" + src + "' would repeat " + (long) columns * rows
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

    private void report(Element e, String message) {
        if (reported.add(e.pos() + ": " + message)) {
            diag.error(e.pos(), message);
        }
    }

    private void paintText(TextNode t, ComputedStyle parent, float dy, DisplayList out) {
        float size = parent.get(CssProperties.FONT_SIZE).value();
        int color = fade(parent.get(CssProperties.COLOR));
        if (color >>> 24 == 0) {
            return;
        }
        float lh = layout.lineHeight(size);
        List<TextShaper.Line> lines = layout.lines(t);
        LayoutBox b = t.box();
        float align = switch (parent.get(CssProperties.TEXT_ALIGN)) {
            case START, LEFT -> 0;
            case CENTER -> 0.5f;
            case END, RIGHT -> 1;
        };
        // text-overflow is the text's element's own: with a clip, lines past its padding box are cut.
        Element owner = t.parent();
        float limit = Float.POSITIVE_INFINITY;
        if (parent.get(CssProperties.TEXT_OVERFLOW) == Keywords.TextOverflow.ELLIPSIS
                && parent.get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE) {
            limit = owner.box().x() + owner.box().width() - owner.box().borderRight() - b.x();
        }
        for (int i = 0; i < lines.size(); i++) {
            TextShaper.Line line = lines.get(i);
            String text = line.text();
            float x = b.x();
            if (line.width() > limit) {
                text = layout.ellipsize(text, size, limit);
            } else {
                // Whole pixels: a glyph at a fractional position is resampled and blurs. A line wider
                // than the box starts at its start edge, as in CSS.
                x += Math.max(0, Math.round((b.width() - line.width()) * align));
            }
            out.add(new DrawCommand.Text(x, b.y() - dy + i * lh, size, color, text));
        }
    }
}
