package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Facet;

/** Paints an element's own content after its CSS box. */
public interface PaintFacet {
    Facet<PaintFacet> KEY = new Facet<>(new PaintFacet() {});
    default void paint(Element e, float y, DisplayList out, Context context) {}
    default boolean paintChildren() { return true; }

    interface Context {
        /** The advance of {@code text} on one line in the font size and family of {@code cs}. */
        float textWidth(String text, ComputedStyle cs);
        /** The used {@code line-height} of an element with style {@code cs}, in pixels. */
        float lineHeight(ComputedStyle cs);
        /** The height of the font's own lines at {@code size}, {@code line-height: normal}. */
        float fontHeight(float size);
        /** {@code e}'s content box as a shape, its border box at {@code y}: the border's corners reduced by the border and padding. */
        RoundRect contentShape(Element e, float y);
        boolean caretVisible();
        void rect(DisplayList out, float x, float y, float w, float h, int color);
        void text(DisplayList out, ComputedStyle style, float x, float y, float size, int color, String text);
    }
}
