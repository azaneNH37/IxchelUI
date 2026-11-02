package com.azane.ixchel.core.style;

import com.azane.ixchel.core.dom.Facet;

/** User-agent declarations contributed by an element definition. */
public record StyleFacet(String declarations) {
    public static final Facet<StyleFacet> KEY = new Facet<>(new StyleFacet(""));
}
