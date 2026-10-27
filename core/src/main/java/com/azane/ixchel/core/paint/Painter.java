package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.BackgroundSize;
import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.text.TextShaper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the laid-out tree into draw commands in document order (later commands paint on top). A
 * background image becomes one {@link DrawCommand.Image} per visible tile, already clipped to the
 * border box, so the host only draws texture regions.
 *
 * <p>A scroll container ({@code overflow} other than {@code visible}) clips its descendants to its box
 * and paints them {@link Element#scrollTop()} higher. {@code overflow: scroll} always draws a
 * 4px scrollbar track along its right edge with a thumb on it; {@code auto}
 * draws only the thumb, and only while the content overflows. The scrollbar is drawn over the
 * content (no space is reserved for it) in fixed colors and cannot be dragged.
 */
public final class Painter {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Bounds the commands one background may emit, so a tiny repeated tile cannot stall a frame. */
    private static final int MAX_TILES = 16384;
    private static final float SCROLLBAR_WIDTH = 4;
    private static final float MIN_THUMB = 8;
    private static final int TRACK_COLOR = 0x80000000;
    private static final int THUMB_COLOR = 0xFFA0A0A0;

    private final LayoutEngine layout;
    private final TagRegistry tags;
    private final ImageMeasurer images;
    private final Diagnostics diag;
    private final Set<String> reported = new HashSet<>();

    /** @param diag receives paint-time errors (an image that cannot be measured), each reported once */
    public Painter(LayoutEngine layout, TagRegistry tags, ImageMeasurer images, Diagnostics diag) {
        this.layout = layout;
        this.tags = tags;
        this.images = images;
        this.diag = diag;
    }

    /** Clears {@code out} and paints the children of the implicit root into it. */
    public void paint(Element root, DisplayList out) {
        out.clear();
        paintChildren(root, 0, out);
        root.clearRepaint();
    }

    /** {@code dy}: how far scroll containers above move these children up. */
    private void paintChildren(Element e, float dy, DisplayList out) {
        // Indexed: the unmodifiable view allocates two objects per for-each, on every repaint.
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            Node c = children.get(i);
            if (c instanceof Element child) {
                paintElement(child, dy, out);
            } else {
                paintText((TextNode) c, e.computed(), dy, out);
            }
        }
    }

    private void paintElement(Element e, float dy, DisplayList out) {
        ComputedStyle cs = e.computed();
        if (cs.get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
            return;
        }
        LayoutBox b = e.box();
        float y = b.y() - dy;
        int bg = cs.get(CssProperties.BACKGROUND_COLOR);
        if (bg >>> 24 != 0) {
            out.add(new DrawCommand.Rect(b.x(), y, b.width(), b.height(), bg));
        }
        paintBackgroundImage(e, cs, b, y, out);
        if (e.tag().equals("img")) {
            out.add(new DrawCommand.Image(b.x(), y, b.width(), b.height(), e.attribute("src"), 0, 0, 1, 1, OPAQUE_WHITE));
        } else if (e.payload() != null && tags.payloadAttribute(e.tag()) != null) {
            out.add(new DrawCommand.Custom(e.tag(), e.payload(), b.x(), y, b.width(), b.height()));
        }
        Keywords.Overflow overflow = cs.get(CssProperties.OVERFLOW);
        if (overflow == Keywords.Overflow.VISIBLE) {
            paintChildren(e, dy, out);
            return;
        }
        out.add(new DrawCommand.PushClip(b.x(), y, b.width(), b.height()));
        paintChildren(e, dy + e.scrollTop(), out);
        paintScrollbar(e, overflow, y, out);
        out.add(DrawCommand.PopClip.INSTANCE);
    }

    private static void paintScrollbar(Element e, Keywords.Overflow overflow, float y, DisplayList out) {
        float max = e.maxScrollTop();
        boolean track = overflow == Keywords.Overflow.SCROLL;
        if (!track && (overflow != Keywords.Overflow.AUTO || max <= 0)) {
            return;
        }
        LayoutBox b = e.box();
        float h = b.height();
        float x = b.x() + b.width() - SCROLLBAR_WIDTH;
        if (track) {
            out.add(new DrawCommand.Rect(x, y, SCROLLBAR_WIDTH, h, TRACK_COLOR));
        }
        float thumb = Math.min(h, Math.max(MIN_THUMB, Math.round(h * h / (h + max))));
        float offset = max <= 0 ? 0 : Math.round((h - thumb) * e.scrollTop() / max);
        out.add(new DrawCommand.Rect(x, y + offset, SCROLLBAR_WIDTH, thumb, THUMB_COLOR));
    }

    /**
     * CSS Backgrounds 3 with the border box as positioning and painting area (there are no borders).
     * {@code top} is the border box top after scrolling.
     */
    private void paintBackgroundImage(Element e, ComputedStyle cs, LayoutBox b, float top, DisplayList out) {
        String src = cs.get(CssProperties.BACKGROUND_IMAGE);
        float areaW = b.width();
        float areaH = b.height();
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
        float right = left + areaW;
        float bottom = top + areaH;
        float x0 = left + offset(cs.get(CssProperties.BACKGROUND_POSITION_X), areaW - w);
        float y0 = top + offset(cs.get(CssProperties.BACKGROUND_POSITION_Y), areaH - h);
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
                        (cx0 - tx) / w, (cy0 - ty) / h, (cx1 - tx) / w, (cy1 - ty) / h, OPAQUE_WHITE));
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
        int color = parent.get(CssProperties.COLOR);
        float lh = layout.lineHeight(size);
        List<TextShaper.Line> lines = layout.lines(t);
        LayoutBox b = t.box();
        for (int i = 0; i < lines.size(); i++) {
            out.add(new DrawCommand.Text(b.x(), b.y() - dy + i * lh, size, color, lines.get(i).text()));
        }
    }
}
