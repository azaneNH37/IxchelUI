package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Facet;

/** Paints an element's own content after its CSS box, using the pipeline's current opacity. */
public interface PaintFacet {
    Facet<PaintFacet> KEY = new Facet<>(new PaintFacet() {});
    default void paint(Element e, float y, DisplayList out, Context context) {}
    default boolean paintChildren() { return true; }

    interface Context {
        float textWidth(String text, float size);
        float lineHeight(float size);
        boolean caretVisible();
        int fade(int color);
        void rect(DisplayList out, float x, float y, float w, float h, int color);
        void text(DisplayList out, ComputedStyle style, float x, float y, float size, int color, String text);
    }
}
