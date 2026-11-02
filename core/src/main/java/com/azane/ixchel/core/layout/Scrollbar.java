package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;

/**
 * The vertical scrollbar of a scroll container, where paint draws it and the pointer drags it: a track
 * along the right edge of the padding box, over the content (no space is reserved for it), with a
 * thumb as long as the visible share of the content (at least {@value #MIN_THUMB}px) placed by
 * {@link Element#scrollTop()}. Coordinates are those the container's box is laid out in, before any
 * ancestor's scroll offset.
 *
 * @param track whether the track is drawn: {@code overflow: scroll}; {@code auto} draws the thumb only
 */
public record Scrollbar(float x, float y, float width, float height, float thumbY, float thumbHeight, boolean track) {
    /** {@code scrollbar-width: auto}. */
    public static final float AUTO_WIDTH = 4;
    /** {@code scrollbar-width: thin}. */
    public static final float THIN_WIDTH = 2;
    public static final float MIN_THUMB = 8;

    /**
     * The scrollbar {@code e} shows, or {@code null}: {@code overflow: scroll} always shows one,
     * {@code auto} while the content overflows, and {@code scrollbar-width: none} never does.
     */
    public static Scrollbar of(Element e) {
        ComputedStyle cs = e.computed();
        if (cs == null) {
            return null;
        }
        Keywords.Overflow overflow = cs.get(CssProperties.OVERFLOW);
        float max = e.maxScrollTop();
        boolean track = overflow == Keywords.Overflow.SCROLL;
        Keywords.ScrollbarWidth size = cs.get(CssProperties.SCROLLBAR_WIDTH);
        if (size == Keywords.ScrollbarWidth.NONE || !track && (overflow != Keywords.Overflow.AUTO || max <= 0)) {
            return null;
        }
        float width = size == Keywords.ScrollbarWidth.THIN ? THIN_WIDTH : AUTO_WIDTH;
        LayoutBox b = e.box();
        float h = b.height() - b.borderTop() - b.borderBottom();
        float thumb = Math.min(h, Math.max(MIN_THUMB, Math.round(h * h / (h + max))));
        float offset = max <= 0 ? 0 : Math.round((h - thumb) * e.scrollTop() / max);
        float y = b.y() + b.borderTop();
        return new Scrollbar(b.x() + b.width() - b.borderRight() - width, y, width, h, y + offset, thumb, track);
    }

    public boolean contains(float px, float py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /** The scroll offset that puts the thumb's top at {@code top}, for a container scrolling {@code max}. */
    public float scrollTopFor(float top, float max) {
        float room = height - thumbHeight;
        return room <= 0 ? 0 : (top - y) / room * max;
    }
}
