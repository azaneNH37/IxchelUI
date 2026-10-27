package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.text.TextShaper;

import java.util.List;

/**
 * Turns the laid-out tree into draw commands in document order (later commands paint on top).
 *
 * <p>A scroll container ({@code overflow} other than {@code visible}) clips its descendants to its box
 * and paints them {@link Element#scrollTop()} higher. {@code overflow: scroll} always draws a
 * 4px scrollbar track along its right edge with a thumb on it; {@code auto}
 * draws only the thumb, and only while the content overflows. The scrollbar is drawn over the
 * content (no space is reserved for it) in fixed colors and cannot be dragged.
 */
public final class Painter {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final float SCROLLBAR_WIDTH = 4;
    private static final float MIN_THUMB = 8;
    private static final int TRACK_COLOR = 0x80000000;
    private static final int THUMB_COLOR = 0xFFA0A0A0;

    private final LayoutEngine layout;
    private final TagRegistry tags;

    public Painter(LayoutEngine layout, TagRegistry tags) {
        this.layout = layout;
        this.tags = tags;
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
        if (e.tag().equals("img")) {
            out.add(new DrawCommand.Image(b.x(), y, b.width(), b.height(), e.attribute("src"), OPAQUE_WHITE));
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
