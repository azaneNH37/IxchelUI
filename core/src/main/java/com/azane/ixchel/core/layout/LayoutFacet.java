package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Facet;
import com.azane.ixchel.core.text.TextShaper;

/** Intrinsic box behavior; CSS layout and the taffy tree remain owned by LayoutEngine. */
public interface LayoutFacet {
    Facet<LayoutFacet> KEY = new Facet<>(new LayoutFacet() {});
    default boolean atomic() { return false; }
    default boolean measured() { return false; }
    default boolean layoutChildren() { return true; }
    default String measureKey(Element e) { return ""; }
    /** The intrinsic width for {@code key} in {@code font} ({@code null}: the default font). */
    default float width(String key, float fontSize, String font, TextShaper text) { return 0; }
}
